import hashlib
import json
import logging
import re
from pathlib import Path

from fastapi import APIRouter, HTTPException
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel

from app.cache import cache_get, cache_put, make_cache_key
from app.deps import DB, ModelName, OllamaUrl, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.rag.embeddings import embed_and_store
from app.usage import log_usage

router = APIRouter(prefix="/ai", tags=["parse"])
log = logging.getLogger(__name__)
_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))


def _extract_json(content: str, label: str = "json") -> dict | None:
    """
    Robustly extract a JSON object from LLM output.
    Handles: markdown fences, leading prose, truncated JSON.
    """
    if not content:
        return None

    # Strip <think>...</think> blocks that Qwen3 sometimes injects even with think=false
    text = re.sub(r"<think>.*?</think>", "", content, flags=re.DOTALL).strip()

    # Strip markdown code fences
    text = re.sub(r"^```(?:json)?\s*", "", text, flags=re.MULTILINE)
    text = re.sub(r"\s*```$", "", text, flags=re.MULTILINE).strip()

    # Find first { — skip any leading prose
    start = text.find("{")
    if start == -1:
        return None
    text = text[start:]

    # Sanitize: replace raw control characters (newlines/tabs) inside JSON strings
    # LLMs sometimes write unescaped \n or \t inside string values, which breaks json.loads
    def _sanitize(s: str) -> str:
        result = []
        in_str = False
        esc = False
        for ch in s:
            if esc:
                esc = False
                result.append(ch)
                continue
            if ch == "\\" and in_str:
                esc = True
                result.append(ch)
                continue
            if ch == '"':
                in_str = not in_str
                result.append(ch)
                continue
            if in_str and ch == '\n':
                result.append('\\n')
                continue
            if in_str and ch == '\r':
                result.append('\\r')
                continue
            if in_str and ch == '\t':
                result.append('\\t')
                continue
            result.append(ch)
        return "".join(result)

    # Direct parse (happy path)
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        log.debug("%s direct-parse failed at pos %d: %s | around: %r",
                  label, e.pos, e.msg, text[max(0, e.pos-40):e.pos+40])

    # Retry after sanitizing control characters in string values
    try:
        return json.loads(_sanitize(text))
    except json.JSONDecodeError as e:
        log.debug("%s sanitized-parse failed at pos %d: %s | around: %r",
                  label, e.pos, e.msg, _sanitize(text)[max(0, e.pos-40):e.pos+40])

    # Repair: walk the string tracking depth; stop at first balanced close
    depth = 0
    in_str = False
    escape = False
    last_balanced = 0
    for i, ch in enumerate(text):
        if escape:
            escape = False
            continue
        if ch == "\\" and in_str:
            escape = True
            continue
        if ch == '"':
            in_str = not in_str
            continue
        if in_str:
            continue
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                last_balanced = i + 1
                break

    if last_balanced:
        try:
            return json.loads(text[:last_balanced])
        except json.JSONDecodeError:
            pass
        try:
            return json.loads(_sanitize(text[:last_balanced]))
        except json.JSONDecodeError as e:
            log.warning("%s balanced-slice parse failed at pos %d: %s | around: %r",
                        label, e.pos, e.msg, _sanitize(text[:last_balanced])[max(0, e.pos-40):e.pos+40])

    # Last resort: truncation repair.
    # A cut-off generation usually ends mid-value (dangling '"key":', half-written
    # string, trailing comma) — just appending closers can't fix those. Cut back to
    # the end of the last COMPLETE value (last close bracket outside a string),
    # dropping the incomplete tail, then close whatever is still open, in order.
    last_close = -1
    in_str2, escape2 = False, False
    for i, ch in enumerate(text):
        if escape2:
            escape2 = False
            continue
        if ch == "\\" and in_str2:
            escape2 = True
            continue
        if ch == '"':
            in_str2 = not in_str2
            continue
        if in_str2:
            continue
        if ch in "}]":
            last_close = i

    base = text[:last_close + 1] if last_close != -1 else text.rstrip().rstrip(",")

    stack: list[str] = []
    in_str2, escape2 = False, False
    for ch in base:
        if escape2:
            escape2 = False
            continue
        if ch == "\\" and in_str2:
            escape2 = True
            continue
        if ch == '"':
            in_str2 = not in_str2
            continue
        if in_str2:
            continue
        if ch == "{":
            stack.append("}")
        elif ch == "[":
            stack.append("]")
        elif ch in "}]" and stack:
            stack.pop()

    stack_suffix = ('"' if in_str2 else "") + "".join(reversed(stack))
    if stack_suffix or last_close != len(text) - 1:
        log.warning("%s JSON was truncated — dropped %d tail chars, appending %r",
                    label, len(text) - len(base), stack_suffix)
        for candidate in (base + stack_suffix, _sanitize(base) + stack_suffix):
            try:
                return json.loads(candidate)
            except json.JSONDecodeError:
                pass

    return None


class ParseResumeRequest(BaseModel):
    raw_text: str


class GeneratePlanRequest(BaseModel):
    profile: dict | None = None
    profileJson: str | None = None
    targets: dict = {}
    additionalContext: str | None = None
    regenerate: bool = False  # skip the cache and produce a fresh plan

    def resolved_profile(self) -> dict:
        if self.profile:
            return self.profile
        if self.profileJson:
            import json as _json
            try:
                return _json.loads(self.profileJson)
            except Exception:
                pass
        return {}


class EmbedResumeRequest(BaseModel):
    raw_text: str


@router.post("/parse-resume")
async def parse_resume(
    body: ParseResumeRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
    ollama_url: OllamaUrl = None,
):
    log.info("parse_resume: provider=%s model=%s ollama_url=%s", provider_name, model_name, ollama_url)
    provider = get_provider(provider_name, model_name, ollama_url)
    # sha256, not builtin hash(): str hash is salted per process, so the cache would never hit across restarts
    text_hash = hashlib.sha256(body.raw_text.encode()).hexdigest()
    cache_key = make_cache_key("parse", {"user_id": user_id, "text_hash": text_hash})

    cached = await cache_get(pool, user_id, cache_key)
    if cached:
        return {"result": cached["content"], "meta": {"model": cached["model"], "cached": True, "tokens": 0, "cost": 0.0}}

    system = _env.get_template("parse.md").render(raw_text=body.raw_text)
    result = await provider.complete(
        messages=[{"role": "user", "content": "Parse the resume and return JSON."}],
        system=system,
        max_tokens=2048,
    )

    if not result.content:
        raise RuntimeError("empty content: The model returned no output. It may have run out of tokens during internal reasoning.")

    parsed = _extract_json(result.content, label="resume")
    if not parsed or not isinstance(parsed, dict):
        log.warning("parse_failed: model=%s raw_len=%d content_preview=%r",
                    result.model, len(result.content), result.content[:200])
        raise RuntimeError("parse_failed: The model returned text that couldn't be read as a resume profile.")

    # Treat a bare {"raw": "..."} fallback as failure — don't cache it
    _is_valid = any(k in parsed for k in ("skills", "seniority", "experiences", "totalYears", "gaps"))
    cost = await log_usage(pool, user_id, "parse", result.model, result.input_tokens, result.output_tokens, provider_name or "anthropic")
    if _is_valid:
        await cache_put(pool, user_id, cache_key, "parse", parsed, result.model)

    return {
        "result": parsed,
        "meta": {"model": result.model, "cached": False, "tokens": result.input_tokens + result.output_tokens, "cost": cost},
    }


@router.post("/generate-plan")
async def generate_plan(
    body: GeneratePlanRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
    ollama_url: OllamaUrl = None,
):
    profile = body.resolved_profile()
    if not profile:
        raise HTTPException(
            status_code=422,
            detail="empty_profile: generate-plan called without a resume profile — the plan would ignore the candidate's skills. Confirm the profile before generating.",
        )

    provider = get_provider(provider_name, model_name, ollama_url)
    # Profile and additional context must be in the key: same targets with an edited
    # profile or new instructions is a different plan, not a cache hit.
    cache_key = make_cache_key("plan", {
        "user_id": user_id,
        "targets": sorted(body.targets.items()),
        "profile": profile,
        "additional_context": body.additionalContext or "",
        # bump when plan.md changes materially — cache entries never expire
        "v": 2,
    })

    if not body.regenerate:
        cached = await cache_get(pool, user_id, cache_key)
        if cached:
            return {"result": cached["content"], "meta": {"model": cached["model"], "cached": True, "tokens": 0, "cost": 0.0}}

    system = _env.get_template("plan.md").render(
        profile=profile,
        targets=body.targets,
        additional_context=body.additionalContext or "",
    )
    user_msg = "Generate the study plan JSON."
    if body.additionalContext:
        user_msg += f"\n\nAdditional instructions from the candidate: {body.additionalContext}"
    plan = None
    result = None
    total_tokens = 0
    total_cost = 0.0
    for attempt in (1, 2):
        # 32768 triggers num_predict=-1 in OllamaProvider (unlimited); gives Anthropic enough room too.
        result = await provider.complete(
            messages=[{"role": "user", "content": user_msg}],
            system=system,
            max_tokens=32768,
            use_thinking=False,
        )
        # meter every attempt — a failed parse still consumed tokens
        total_cost += await log_usage(pool, user_id, "plan", result.model,
                                      result.input_tokens, result.output_tokens, provider_name or "anthropic")
        total_tokens += result.input_tokens + result.output_tokens

        plan = _extract_json(result.content, label="plan")
        if plan and isinstance(plan, dict):
            break
        log.warning("plan parse_failed (attempt %d): model=%s stop=%s raw_len=%d | head=%r | tail=%r",
                    attempt, result.model, result.stop_reason, len(result.content),
                    result.content[:300], result.content[-300:])

    if not plan or not isinstance(plan, dict):
        raise RuntimeError("parse_failed: The AI returned a plan that couldn't be read as JSON. Please try again.")

    await cache_put(pool, user_id, cache_key, "plan", plan, result.model)

    return {
        "result": plan,
        "meta": {"model": result.model, "cached": False, "tokens": total_tokens, "cost": total_cost},
    }


@router.post("/embed-resume")
async def embed_resume(
    body: EmbedResumeRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    # Embeddings use voyageai directly — bypass desktop mode by falling back
    # to the default non-desktop provider if the user has desktop selected.
    effective = None if (provider_name or "").lower() == "desktop" else provider_name
    provider = get_provider(effective, model_name)
    stored = await embed_and_store(pool, provider, user_id, body.raw_text)
    return {"result": {"chunks_stored": stored}, "meta": {"model": provider.model_id, "cached": False, "tokens": 0, "cost": 0.0}}
