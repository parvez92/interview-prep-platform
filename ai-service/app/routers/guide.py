import logging
from pathlib import Path

from fastapi import APIRouter
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel

from app.cache import cache_get, cache_put, make_cache_key
from app.deps import DB, ModelName, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.rag.retriever import retrieve
from app.routers.parse import _extract_json
from app.usage import log_usage

router = APIRouter(prefix="/ai", tags=["guide"])
log = logging.getLogger(__name__)
_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))

_TEMPLATE = {
    "overview":  "guide_overview.md",
    "resources": "guide_resources.md",
    "exercises": "guide_exercises.md",
    "questions": "guide_questions.md",
}
_JSON_TABS = {"overview", "resources", "exercises", "questions"}


class GuideRequest(BaseModel):
    tab: str = "overview"
    topic_title: str
    topic_category: str = ""
    seniority: str = "mid"
    confidence: int = 3
    skills: list[str] = []
    angle: str = ""  # plan hint or previously generated interview angle


@router.post("/guide")
async def guide(
    body: GuideRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    provider = get_provider(provider_name, model_name)
    # "v" bumps when the guide prompts change materially, so stale shallow content isn't served forever.
    # Overview depth is calibrated to confidence, so confidence is part of that key.
    key_payload = {"user_id": user_id, "tab": body.tab, "topic": body.topic_title, "v": 2}
    if body.tab == "overview":
        key_payload["confidence"] = body.confidence
    cache_key = make_cache_key("guide", key_payload)

    cached = await cache_get(pool, user_id, cache_key)
    if cached:
        return {"result": cached["content"], "meta": {"model": cached["model"], "cached": True, "tokens": 0, "cost": 0.0}}

    context_chunks: list = []
    if body.tab == "overview":
        context_chunks = await retrieve(pool, provider, user_id, body.topic_title, top_k=4)

    template_name = _TEMPLATE.get(body.tab, "guide_overview.md")
    system = _env.get_template(template_name).render(
        topic_title=body.topic_title,
        topic_category=body.topic_category,
        seniority=body.seniority,
        confidence=body.confidence,
        skills=body.skills,
        angle=body.angle,
        context_chunks=context_chunks,
    )

    # In-depth content (multi-paragraph overviews, 8-10 questions with scenarios) needs headroom
    max_tokens = 4096
    result = await provider.complete(
        messages=[{"role": "user", "content": f"Generate {body.tab} content for: {body.topic_title}"}],
        system=system,
        max_tokens=max_tokens,
    )

    if body.tab in _JSON_TABS:
        content = _extract_json(result.content, label=f"guide-{body.tab}") or {}
    else:
        content = result.content

    cost = await log_usage(pool, user_id, f"guide-{body.tab}", result.model, result.input_tokens, result.output_tokens)
    await cache_put(pool, user_id, cache_key, f"guide-{body.tab}", content, result.model)

    return {
        "result": content,
        "meta": {"model": result.model, "cached": False, "tokens": result.input_tokens + result.output_tokens, "cost": cost},
    }
