import logging
from dataclasses import dataclass
from typing import Any, Callable

import httpx
import psycopg_pool

from app.config import settings
from app.vault import secret

log = logging.getLogger(__name__)


@dataclass
class ToolSpec:
    name: str
    description: str
    input_schema: dict
    fn: Callable

    @property
    def schema(self) -> dict:
        return {"name": self.name, "description": self.description, "input_schema": self.input_schema}


def _core_headers() -> dict[str, str]:
    return {"Authorization": f"Bearer {secret('service.token')}"}


async def _core_get(path: str) -> Any:
    async with httpx.AsyncClient(base_url=settings.core_url, timeout=10.0) as client:
        resp = await client.get(path, headers=_core_headers())
        resp.raise_for_status()
        return resp.json()


async def _core_post(path: str, body: dict) -> Any:
    async with httpx.AsyncClient(base_url=settings.core_url, timeout=10.0) as client:
        resp = await client.post(path, json=body, headers=_core_headers())
        resp.raise_for_status()
        return resp.json()


async def search_topics(user_id: int, query: str, limit: int = 5) -> dict:
    return await _core_get(f"/api/topics?search={query}&size={limit}&userId={user_id}")


async def get_progress(user_id: int) -> dict:
    return await _core_get(f"/api/progress?userId={user_id}")


async def flag_for_review(user_id: int, topic_id: int, reason: str) -> dict:
    return await _core_post("/api/review/flag", {"userId": user_id, "topicId": topic_id, "reason": reason})


async def schedule_review(user_id: int, topic_id: int, due_date: str) -> dict:
    return await _core_post("/api/review/schedule", {"userId": user_id, "topicId": topic_id, "dueDate": due_date})


async def get_interview_log(user_id: int, interview_id: int | None = None) -> dict:
    path = f"/api/interviews/{interview_id}" if interview_id else f"/api/interviews?userId={user_id}&size=10"
    return await _core_get(path)


def make_vector_search_tool(pool: psycopg_pool.AsyncConnectionPool, user_id: int):
    async def vector_search(query: str, top_k: int = 5) -> dict:
        from app.providers.registry import get_provider
        from app.rag.retriever import retrieve
        provider = get_provider()
        chunks = await retrieve(pool, provider, user_id, query, top_k=top_k)
        return {"chunks": chunks}

    return ToolSpec(
        name="vector_search",
        description="Search the candidate's resume and notes using semantic similarity",
        input_schema={
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Semantic search query"},
                "top_k": {"type": "integer", "description": "Number of results", "default": 5},
            },
            "required": ["query"],
        },
        fn=vector_search,
    )


def make_core_tools(user_id: int) -> list[ToolSpec]:
    return [
        ToolSpec(
            name="search_topics",
            description="Search study topics by keyword",
            input_schema={
                "type": "object",
                "properties": {
                    "query": {"type": "string"},
                    "limit": {"type": "integer", "default": 5},
                },
                "required": ["query"],
            },
            fn=lambda query, limit=5: search_topics(user_id, query, limit),
        ),
        ToolSpec(
            name="get_progress",
            description="Get the candidate's overall study progress and confidence scores",
            input_schema={"type": "object", "properties": {}},
            fn=lambda: get_progress(user_id),
        ),
        ToolSpec(
            name="flag_for_review",
            description="Flag a topic for review because the candidate struggled with it",
            input_schema={
                "type": "object",
                "properties": {
                    "topic_id": {"type": "integer"},
                    "reason": {"type": "string"},
                },
                "required": ["topic_id", "reason"],
            },
            fn=lambda topic_id, reason: flag_for_review(user_id, topic_id, reason),
        ),
        ToolSpec(
            name="schedule_review",
            description="Schedule a topic for review on a specific date (ISO 8601)",
            input_schema={
                "type": "object",
                "properties": {
                    "topic_id": {"type": "integer"},
                    "due_date": {"type": "string", "format": "date"},
                },
                "required": ["topic_id", "due_date"],
            },
            fn=lambda topic_id, due_date: schedule_review(user_id, topic_id, due_date),
        ),
        ToolSpec(
            name="get_interview_log",
            description="Get recent interview history with questions and self-ratings",
            input_schema={
                "type": "object",
                "properties": {"interview_id": {"type": "integer"}},
            },
            fn=lambda interview_id=None: get_interview_log(user_id, interview_id),
        ),
    ]
