"""
MCP server for prep-loop.

Two deployment modes:
  1. Embedded  — mounted at /mcp inside the FastAPI app (SSE transport).
  2. Standalone — run via mcp_server.py; Claude Desktop spawns it as a
                  stdio subprocess and it uses settings.default_user_id.

Tools fall into two groups:
  - Action tools  (flag, schedule) — write-side via Java REST
  - Context tools (search, get_*)  — read-side via Java REST + pgvector
"""

import json
import logging

from mcp.server.fastmcp import FastMCP

from app.agents.tools import (
    flag_for_review,
    get_interview_log,
    get_progress,
    schedule_review,
    search_topics,
)
from app.config import settings

log = logging.getLogger(__name__)

mcp = FastMCP(
    "prep-loop",
    instructions=(
        "You are an expert interview coach with access to the candidate's "
        "full study plan, progress, resume, and interview history. "
        "Use the context tools to retrieve information before answering. "
        "Always ground your responses in the candidate's actual data."
    ),
)

# ---------------------------------------------------------------------------
# Context tools — Claude Desktop calls these to pull information before answering
# ---------------------------------------------------------------------------

@mcp.tool()
async def get_study_progress(user_id: int = settings.default_user_id) -> str:
    """
    Return the candidate's complete study progress: phases, topics, confidence
    scores, and weak areas. Call this first in any coaching or planning query.
    """
    data = await get_progress(user_id)
    return json.dumps(data, indent=2)


@mcp.tool()
async def search_study_topics(
    query: str,
    limit: int = 8,
    user_id: int = settings.default_user_id,
) -> str:
    """
    Full-text search over study topics. Returns matching topics with their
    category, priority, confidence, and any linked notes/resources.
    Use this to find relevant topics before answering a technical question.
    """
    data = await search_topics(user_id, query, limit)
    return json.dumps(data, indent=2)


@mcp.tool()
async def get_interview_history(
    interview_id: int | None = None,
    user_id: int = settings.default_user_id,
) -> str:
    """
    Return recent interview history with questions asked and the candidate's
    self-ratings. Use this for debrief or coaching queries.
    Pass interview_id to get a specific interview; omit for the last 10.
    """
    data = await get_interview_log(user_id, interview_id)
    return json.dumps(data, indent=2)


@mcp.tool()
async def search_resume(
    query: str,
    top_k: int = 6,
    user_id: int = settings.default_user_id,
) -> str:
    """
    Semantic search over the candidate's resume using pgvector.
    Returns the most relevant excerpts from their background.
    Use this when the question relates to their experience or skills.
    """
    try:
        from app.db import get_pool
        from app.providers.registry import get_provider
        from app.rag.retriever import retrieve

        pool = get_pool()
        provider = get_provider()
        chunks = await retrieve(pool, provider, user_id, query, top_k=top_k)
        return json.dumps({"chunks": chunks}, indent=2)
    except Exception as exc:
        log.warning("search_resume failed (DB may not be available in this MCP mode): %s", exc)
        return json.dumps({"error": str(exc), "chunks": []})


@mcp.tool()
async def get_today_review(user_id: int = settings.default_user_id) -> str:
    """
    Return topics flagged for review today — weak areas from past mock
    interviews and self-assessments. Use this for daily coaching sessions.
    """
    try:
        import httpx
        from app.config import settings as cfg
        from app.vault import secret

        async with httpx.AsyncClient(base_url=cfg.core_url, timeout=10.0) as client:
            resp = await client.get(
                f"/api/review/today?userId={user_id}",
                headers={"Authorization": f"Bearer {secret('service.token')}"},
            )
            resp.raise_for_status()
            return json.dumps(resp.json(), indent=2)
    except Exception as exc:
        return json.dumps({"error": str(exc)})


@mcp.tool()
async def get_plan_overview(user_id: int = settings.default_user_id) -> str:
    """
    Return the candidate's full study plan structure: phases, weeks, and
    per-topic goals. Use this for planning or scheduling queries.
    """
    try:
        import httpx
        from app.config import settings as cfg
        from app.vault import secret

        async with httpx.AsyncClient(base_url=cfg.core_url, timeout=10.0) as client:
            resp = await client.get(
                f"/api/phases?userId={user_id}",
                headers={"Authorization": f"Bearer {secret('service.token')}"},
            )
            resp.raise_for_status()
            return json.dumps(resp.json(), indent=2)
    except Exception as exc:
        return json.dumps({"error": str(exc)})


# ---------------------------------------------------------------------------
# Action tools — write-side, Claude Desktop uses these to update state
# ---------------------------------------------------------------------------

@mcp.tool()
async def flag_topic_for_review(
    topic_id: int,
    reason: str,
    user_id: int = settings.default_user_id,
) -> str:
    """
    Flag a topic for review because the candidate struggled with it.
    The feedback loop will lower confidence and surface it in daily review.
    """
    data = await flag_for_review(user_id, topic_id, reason)
    return json.dumps(data, indent=2)


@mcp.tool()
async def schedule_topic_review(
    topic_id: int,
    due_date: str,
    user_id: int = settings.default_user_id,
) -> str:
    """
    Schedule a topic for focused review on a specific date (ISO 8601, e.g. 2026-06-25).
    Use this after identifying gaps in a debrief or coaching session.
    """
    data = await schedule_review(user_id, topic_id, due_date)
    return json.dumps(data, indent=2)
