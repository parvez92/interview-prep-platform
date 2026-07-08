import json
import logging
from pathlib import Path

from fastapi import APIRouter
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel, ConfigDict

from app.cache import cache_get, cache_put, make_cache_key
from app.deps import DB, ModelName, OllamaUrl, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider, wants_thinking
from app.routers.parse import _extract_json
from app.usage import log_usage

router = APIRouter(prefix="/ai", tags=["depth"])
log = logging.getLogger(__name__)
_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))

_EXEMPLAR_DIR = Path(__file__).parent.parent / "exemplars"
_FALLBACK_CATEGORY = "domain"


def load_exemplar(category: str) -> dict:
    path = _EXEMPLAR_DIR / f"{(category or '').strip().lower()}.json"
    if not path.exists():
        path = _EXEMPLAR_DIR / f"{_FALLBACK_CATEGORY}.json"
    return json.loads(path.read_text())


class DepthRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")
    model: str | None = None  # injected by Java BudgetGuard
    topics: list[dict]        # [{slug,title,scope,split_hint,category}] — same category per batch
    seniority: str = "mid"
    target_role: str = "Software Engineer"
    regenerate: bool = False  # bypass cache — used for per-topic validation retries


@router.post("/deep-dive")
async def deep_dive(
    body: DepthRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
    ollama_url: OllamaUrl = None,
):
    if not body.topics:
        return {"result": {"topics": []}, "meta": {"model": "none", "cached": False, "tokens": 0, "cost": 0.0}}

    provider = get_provider(provider_name, body.model or model_name, ollama_url)
    category = (body.topics[0].get("category") or _FALLBACK_CATEGORY).lower()
    cache_key = make_cache_key("depth", {
        "user_id": user_id,
        "slugs": sorted(t.get("slug", "") for t in body.topics),
        "v": 1,
    })

    if not body.regenerate:
        cached = await cache_get(pool, user_id, cache_key)
        if cached:
            return {"result": cached["content"], "meta": {"model": cached["model"], "cached": True, "tokens": 0, "cost": 0.0}}

    exemplar = load_exemplar(category)
    system = _env.get_template("depth.md").render(
        topics=body.topics,
        seniority=body.seniority,
        target_role=body.target_role,
        category=category,
        exemplar_json=json.dumps(exemplar["topic"], separators=(",", ":")),
    )

    result = await provider.complete(
        messages=[{"role": "user", "content": "Write the deep-dive cards for the units above."}],
        system=system,
        max_tokens=32768,
        use_thinking=wants_thinking(provider_name),
    )
    cost = await log_usage(pool, user_id, "depth", result.model,
                           result.input_tokens, result.output_tokens, provider_name or "anthropic")

    parsed = _extract_json(result.content, label="depth") or {}
    units = parsed.get("units") if isinstance(parsed.get("units"), list) else []

    # flatten: strip _scratch, attach parent slug to every card
    topics_out: list[dict] = []
    for unit in units:
        if not isinstance(unit, dict):
            continue
        parent = unit.get("parent") or ""
        for card in unit.get("cards") or []:
            if isinstance(card, dict) and card.get("title"):
                card.pop("_scratch", None)
                card["parent"] = parent
                topics_out.append(card)

    content = {"topics": topics_out}
    if topics_out:
        await cache_put(pool, user_id, cache_key, "depth", content, result.model)
    else:
        log.warning("depth parse produced no cards: model=%s raw_len=%d preview=%r",
                    result.model, len(result.content), result.content[:300])

    return {
        "result": content,
        "meta": {"model": result.model, "cached": False,
                 "tokens": result.input_tokens + result.output_tokens, "cost": cost},
    }
