import asyncio
import logging
from pathlib import Path

from fastapi import APIRouter
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel

from app.cache import cache_get, cache_put, make_cache_key
from app.deps import DB, ModelName, OllamaUrl, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.routers.parse import _extract_json
from app.usage import log_usage

router = APIRouter(prefix="/ai", tags=["seed"])
log = logging.getLogger(__name__)
_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))

# One call covering a 30-40 topic plan truncates even at 32k output tokens.
# Small batches keep each response well under the limit; failed/uncached batches
# retry independently because each batch has its own cache entry.
BATCH_SIZE = 6
MAX_CONCURRENT_BATCHES = 3


class SeedPlanRequest(BaseModel):
    topics: list[dict]       # [{slug, title, tag}]
    profile: dict = {}       # parsed resume profile for context
    target_role: str = ""    # e.g. "Senior Backend Engineer"


@router.post("/seed-plan")
async def seed_plan(
    body: SeedPlanRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
    ollama_url: OllamaUrl = None,
):
    if not body.topics:
        return {"result": {"topics": []}, "meta": {"model": "none", "cached": False, "tokens": 0, "cost": 0.0}}

    provider = get_provider(provider_name, model_name, ollama_url)
    batches = [body.topics[i:i + BATCH_SIZE] for i in range(0, len(body.topics), BATCH_SIZE)]
    sem = asyncio.Semaphore(MAX_CONCURRENT_BATCHES)

    async def seed_batch(batch: list[dict]) -> tuple[list, int, float, bool, str]:
        """Returns (topics, tokens, cost, cached, model) for one batch."""
        cache_key = make_cache_key("seed", {"user_id": user_id, "slugs": sorted(t["slug"] for t in batch), "v": 2})
        cached = await cache_get(pool, user_id, cache_key)
        if cached:
            return cached["content"].get("topics", []), 0, 0.0, True, cached["model"]

        system = _env.get_template("seed.md").render(
            topics=batch,
            profile=body.profile,
            target_role=body.target_role,
        )
        # 32768 → triggers num_predict=-1 in OllamaProvider (unlimited output)
        async with sem:
            result = await provider.complete(
                messages=[{"role": "user", "content": "Generate study materials for all topics listed above."}],
                system=system,
                max_tokens=32768,
                use_thinking=False,
            )

        cost = await log_usage(pool, user_id, "seed-plan", result.model,
                               result.input_tokens, result.output_tokens, provider_name or "anthropic")
        tokens = result.input_tokens + result.output_tokens

        content = _extract_json(result.content, label="seed")
        if not content or not isinstance(content.get("topics"), list):
            log.warning("seed batch parse_failed: model=%s slugs=%s raw_len=%d preview=%r",
                        result.model, [t["slug"] for t in batch], len(result.content), result.content[:300])
            return [], tokens, cost, False, result.model

        await cache_put(pool, user_id, cache_key, "seed", content, result.model)
        return content["topics"], tokens, cost, False, result.model

    results = await asyncio.gather(*(seed_batch(b) for b in batches))

    all_topics: list = []
    total_tokens = 0
    total_cost = 0.0
    all_cached = True
    model_used = "none"
    for topics, tokens, cost, was_cached, model in results:
        all_topics.extend(topics)
        total_tokens += tokens
        total_cost += cost
        all_cached = all_cached and was_cached
        model_used = model

    return {
        "result": {"topics": all_topics},
        "meta": {"model": model_used, "cached": all_cached, "tokens": total_tokens, "cost": total_cost},
    }
