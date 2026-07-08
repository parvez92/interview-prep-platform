import logging

from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict

from app.agents.mock_agent import start_mock_session
from app.deps import DB, ModelName, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.routers.parse import _extract_json
from app.usage import log_usage

router = APIRouter(prefix="/ai", tags=["mock"])
log = logging.getLogger(__name__)


class MockRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")
    model: str | None = None  # injected by Java BudgetGuard
    target_role: str = "Software Engineer"
    topic: str = "general"
    difficulty: str = "medium"
    interview_type: str = "technical"
    seniority: str = "mid"
    strengths: list[str] = []
    history: list[dict] = []


@router.post("/mock")
async def mock_interview(
    body: MockRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    provider = get_provider(provider_name, body.model or model_name)

    resp = await start_mock_session(
        user_id=user_id,
        target_role=body.target_role,
        topic=body.topic,
        difficulty=body.difficulty,
        interview_type=body.interview_type,
        seniority=body.seniority,
        strengths=body.strengths,
        provider=provider,
        pool=pool,
        history=body.history or None,
    )

    cost = await log_usage(pool, user_id, "mock", resp["model"], resp["input_tokens"], resp["output_tokens"])

    # mock.md asks for {"reply","done"[,"score","feedback"]} every turn;
    # fall back to treating raw text as the reply if the model ignores that.
    parsed = _extract_json(resp["reply"], label="mock") or {}
    result: dict = {
        "reply": parsed.get("reply") or resp["reply"],
        "done": bool(parsed.get("done")),
    }
    if result["done"]:
        score = parsed.get("score")
        result["score"] = int(score) if isinstance(score, (int, float)) else None
        result["feedback"] = parsed.get("feedback") or ""

    return {
        "result": result,
        "meta": {
            "model": resp["model"],
            "cached": False,
            "tokens": resp["input_tokens"] + resp["output_tokens"],
            "cost": cost,
        },
    }
