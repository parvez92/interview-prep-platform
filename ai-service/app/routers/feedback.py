"""
Feedback-loop RAG endpoints. Called by backend-core (FeedbackLoopService) when the
interview/mock rating rule fires — these only maintain the weak_answer_chunk vector
store; the flag/confidence rule itself stays in Java.
"""
import logging

from fastapi import APIRouter
from pydantic import BaseModel

from app.deps import DB, ModelName, ProviderName, ServiceAuth, UserId
from app.providers.registry import get_provider
from app.rag.embeddings import delete_weak_answer, embed_weak_answer

router = APIRouter(prefix="/ai", tags=["feedback"])
log = logging.getLogger(__name__)


class EmbedWeakAnswerRequest(BaseModel):
    question_id: int
    topic_id: int | None = None
    text: str


class DeleteWeakAnswerRequest(BaseModel):
    question_id: int


@router.post("/embed-weak-answer")
async def embed_weak_answer_endpoint(
    body: EmbedWeakAnswerRequest,
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
    stored = await embed_weak_answer(pool, provider, user_id, body.question_id, body.topic_id, body.text)
    return {"result": {"chunks_stored": stored}, "meta": {"model": provider.model_id, "cached": False, "tokens": 0, "cost": 0.0}}


@router.post("/delete-weak-answer")
async def delete_weak_answer_endpoint(
    body: DeleteWeakAnswerRequest,
    _auth: ServiceAuth,
    user_id: UserId,
    pool: DB,
):
    deleted = await delete_weak_answer(pool, user_id, body.question_id)
    return {"result": {"chunks_deleted": deleted}, "meta": {"model": "none", "cached": False, "tokens": 0, "cost": 0.0}}
