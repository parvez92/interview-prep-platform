import logging
from pathlib import Path

from fastapi import APIRouter
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel, ConfigDict

from app.cache import cache_get, cache_put, make_cache_key
from app.deps import DB, ModelName, OllamaUrl, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.routers.parse import _extract_json
from app.usage import log_usage

router = APIRouter(prefix="/ai", tags=["narrative"])
log = logging.getLogger(__name__)
_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))


class NarrativeRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")
    model: str | None = None      # injected by Java BudgetGuard
    phases: list[dict]            # [{name, goal}]
    weeks: list[dict]             # [{week_number, title, phase}]
    highlights: list[str] = []
    target_role: str = "Software Engineer"


@router.post("/narrative")
async def narrative(
    body: NarrativeRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
    ollama_url: OllamaUrl = None,
):
    if not body.phases or not body.weeks:
        return {"result": {}, "meta": {"model": "none", "cached": False, "tokens": 0, "cost": 0.0}}

    provider = get_provider(provider_name, body.model or model_name, ollama_url)
    cache_key = make_cache_key("narrative", {
        "user_id": user_id,
        "weeks": [w.get("title", "") for w in body.weeks],
        "v": 1,
    })
    cached = await cache_get(pool, user_id, cache_key)
    if cached:
        return {"result": cached["content"], "meta": {"model": cached["model"], "cached": True, "tokens": 0, "cost": 0.0}}

    system = _env.get_template("narrative.md").render(
        phases=body.phases,
        weeks=body.weeks,
        highlights=body.highlights[:12],
        target_role=body.target_role,
    )
    result = await provider.complete(
        messages=[{"role": "user", "content": "Write the narrative JSON for the structure above."}],
        system=system,
        max_tokens=8192,
        use_thinking=False,
    )
    cost = await log_usage(pool, user_id, "narrative", result.model,
                           result.input_tokens, result.output_tokens, provider_name or "anthropic")

    content = _extract_json(result.content, label="narrative") or {}
    if content.get("weeks"):
        await cache_put(pool, user_id, cache_key, "narrative", content, result.model)

    return {
        "result": content,
        "meta": {"model": result.model, "cached": False,
                 "tokens": result.input_tokens + result.output_tokens, "cost": cost},
    }
