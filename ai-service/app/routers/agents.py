import logging

from fastapi import APIRouter
from pydantic import BaseModel

from app.agents.coach import run_coach
from app.agents.debrief import run_debrief
from app.agents.jobfit import run_jobfit
from app.agents.prep_pack import run_prep_pack
from app.deps import DB, ModelName, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.usage import log_usage

router = APIRouter(prefix="/ai/agents", tags=["agents"])
log = logging.getLogger(__name__)


def _meta(run, cost: float) -> dict:
    return {
        "model": run.model,
        "cached": False,
        "tokens": run.tokens,
        "cost": cost,
        "steps": run.steps,
        "elapsed_ms": run.elapsed_ms,
        "status": run.status,
    }


class JobFitRequest(BaseModel):
    job_description: str
    company: str
    role: str
    profile: dict
    job_id: str | None = None


class DebriefRequest(BaseModel):
    interview: dict
    qa_pairs: list[dict]
    interview_id: str | None = None


class CoachRequest(BaseModel):
    review_flags: list[dict]
    mock_history: list[dict] = []
    seniority: str = "mid"
    strong_areas: list[str] = []
    target_role: str = "Software Engineer"


class PrepPackRequest(BaseModel):
    job_description: str
    company: str
    role: str
    profile: dict
    job_id: str | None = None


@router.post("/jobfit")
async def jobfit(
    body: JobFitRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    provider = get_provider(provider_name, model_name)
    run = await run_jobfit(
        user_id=user_id,
        job_description=body.job_description,
        company=body.company,
        role=body.role,
        profile=body.profile,
        provider=provider,
        pool=pool,
        job_id=body.job_id,
    )
    cost = await log_usage(pool, user_id, "jobfit", run.model, run.input_tokens, run.output_tokens)
    return {"result": run.output, "meta": _meta(run, cost)}


@router.post("/debrief")
async def debrief(
    body: DebriefRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    provider = get_provider(provider_name, model_name)
    run = await run_debrief(
        user_id=user_id,
        interview=body.interview,
        qa_pairs=body.qa_pairs,
        provider=provider,
        pool=pool,
        interview_id=body.interview_id,
    )
    cost = await log_usage(pool, user_id, "debrief", run.model, run.input_tokens, run.output_tokens)
    return {"result": run.output, "meta": _meta(run, cost)}


@router.post("/coach")
async def coach(
    body: CoachRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    provider = get_provider(provider_name, model_name)
    run = await run_coach(
        user_id=user_id,
        review_flags=body.review_flags,
        mock_history=body.mock_history,
        seniority=body.seniority,
        strong_areas=body.strong_areas,
        target_role=body.target_role,
        provider=provider,
        pool=pool,
    )
    cost = await log_usage(pool, user_id, "coach", run.model, run.input_tokens, run.output_tokens)
    return {"result": run.output, "meta": _meta(run, cost)}


@router.post("/prep-pack")
async def prep_pack(
    body: PrepPackRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
    provider_name: ProviderName = None,
    model_name: ModelName = None,
):
    provider = get_provider(provider_name, model_name)
    run = await run_prep_pack(
        user_id=user_id,
        job_description=body.job_description,
        company=body.company,
        role=body.role,
        profile=body.profile,
        provider=provider,
        pool=pool,
        job_id=body.job_id,
    )
    cost = await log_usage(pool, user_id, "prep_pack", run.model, run.input_tokens, run.output_tokens)
    return {"result": run.output, "meta": _meta(run, cost)}
