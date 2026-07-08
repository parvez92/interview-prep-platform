import json
import logging
from pathlib import Path

from fastapi import APIRouter
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel

from app.cache import cache_get, cache_put, make_cache_key
from app.deps import DB, ModelName, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.usage import log_usage

router = APIRouter(prefix="/ai", tags=["score"])
log = logging.getLogger(__name__)
_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))

_SCORE_SYSTEM = """You are an interview readiness evaluator. Given a candidate's study topics
with confidence scores and recent interview performance, compute a readiness score.

Return JSON:
{
  "overall_score": <0-100>,
  "by_category": {"dsa": <0-100>, "system_design": <0-100>, "behavioral": <0-100>},
  "ready_to_interview": <boolean>,
  "top_risks": [<string>, ...],
  "recommended_focus": [<string>, ...]
}
"""


class ScoreRequest(BaseModel):
    topics: list[dict]
    interview_history: list[dict] = []
    target_role: str = "Software Engineer"


@router.post("/score")
async def readiness_score(
    body: ScoreRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    provider = get_provider(provider_name, model_name)
    cache_key = make_cache_key("score", {"user_id": user_id, "role": body.target_role, "topics_count": len(body.topics)})

    cached = await cache_get(pool, user_id, cache_key)
    if cached:
        return {"result": cached["content"], "meta": {"model": cached["model"], "cached": True, "tokens": 0, "cost": 0.0}}

    payload = json.dumps({
        "topics": body.topics,
        "interview_history": body.interview_history,
        "target_role": body.target_role,
    }, indent=2)

    result = await provider.complete(
        messages=[{"role": "user", "content": f"Compute readiness score for:\n{payload}"}],
        system=_SCORE_SYSTEM,
        max_tokens=1024,
    )

    try:
        score_data = json.loads(result.content)
    except json.JSONDecodeError:
        import re
        m = re.search(r"\{.*\}", result.content, re.DOTALL)
        score_data = json.loads(m.group()) if m else {"raw": result.content}

    cost = await log_usage(pool, user_id, "score", result.model, result.input_tokens, result.output_tokens)
    await cache_put(pool, user_id, cache_key, "score", score_data, result.model)

    return {
        "result": score_data,
        "meta": {"model": result.model, "cached": False, "tokens": result.input_tokens + result.output_tokens, "cost": cost},
    }
