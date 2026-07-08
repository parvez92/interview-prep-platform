import json
import logging
import re
from pathlib import Path

from fastapi import APIRouter
from jinja2 import Environment, FileSystemLoader
from pydantic import BaseModel, ConfigDict

from app.cache import cache_get, cache_put, make_cache_key
from app.deps import DB, ModelName, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.usage import log_usage

router = APIRouter(prefix="/ai", tags=["analyze"])
log = logging.getLogger(__name__)
_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))


class AnalyzeRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")
    model: str | None = None  # injected by Java BudgetGuard
    question: str
    answer: str
    question_type: str = "technical"


@router.post("/analyze")
async def analyze_answer(
    body: AnalyzeRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    provider = get_provider(provider_name, body.model or model_name)
    cache_key = make_cache_key("analysis", {"user_id": user_id, "q": body.question, "a": body.answer[:100]})

    cached = await cache_get(pool, user_id, cache_key)
    if cached:
        return {"result": cached["content"], "meta": {"model": cached["model"], "cached": True, "tokens": 0, "cost": 0.0}}

    system = _env.get_template("analyze.md").render(
        question=body.question,
        answer=body.answer,
        question_type=body.question_type,
    )

    result = await provider.complete(
        messages=[{"role": "user", "content": "Evaluate the answer and return JSON."}],
        system=system,
        max_tokens=1024,
    )

    try:
        analysis = json.loads(result.content)
    except json.JSONDecodeError:
        m = re.search(r"\{.*\}", result.content, re.DOTALL)
        analysis = json.loads(m.group()) if m else {"raw": result.content}

    cost = await log_usage(pool, user_id, "analysis", result.model, result.input_tokens, result.output_tokens)
    await cache_put(pool, user_id, cache_key, "analysis", analysis, result.model)

    return {
        "result": analysis,
        "meta": {"model": result.model, "cached": False, "tokens": result.input_tokens + result.output_tokens, "cost": cost},
    }
