from pathlib import Path

import psycopg_pool
from jinja2 import Environment, FileSystemLoader

from app.agents.loop import AgentRunResult, run_agent
from app.agents.tools import make_core_tools, make_vector_search_tool
from app.providers.base import LLMProvider

_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))

SYSTEM = """You are a personalized interview coach. Use the candidate's performance data
to identify weak areas and provide targeted coaching. Use tools to get the latest
progress data and flag topics that need review."""


async def run_coach(
    user_id: int,
    review_flags: list[dict],
    mock_history: list[dict],
    seniority: str,
    strong_areas: list[str],
    target_role: str,
    provider: LLMProvider,
    pool: psycopg_pool.AsyncConnectionPool,
) -> AgentRunResult:
    tools = make_core_tools(user_id) + [make_vector_search_tool(pool, user_id)]

    template = _env.get_template("coach.md")
    user_msg = template.render(
        review_flags=review_flags,
        mock_history=mock_history,
        seniority=seniority,
        strong_areas=strong_areas,
        target_role=target_role,
    )

    return await run_agent(
        name="coach",
        user_id=user_id,
        messages=[{"role": "user", "content": user_msg}],
        system=SYSTEM,
        tools=tools,
        provider=provider,
        pool=pool,
        max_steps=4,
    )
