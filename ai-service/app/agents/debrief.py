from pathlib import Path

import psycopg_pool
from jinja2 import Environment, FileSystemLoader

from app.agents.loop import AgentRunResult, run_agent
from app.agents.tools import make_core_tools
from app.providers.base import LLMProvider

_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))

SYSTEM = """You are a career coach debriefing a candidate after a real interview.
Analyze what went well, what didn't, and update the study plan accordingly.
Use tools to flag weak topics for review and adjust scheduling."""


async def run_debrief(
    user_id: int,
    interview: dict,
    qa_pairs: list[dict],
    provider: LLMProvider,
    pool: psycopg_pool.AsyncConnectionPool,
    interview_id: str | None = None,
) -> AgentRunResult:
    tools = make_core_tools(user_id)

    template = _env.get_template("debrief.md")
    user_msg = template.render(
        interview=interview,
        qa_pairs=qa_pairs,
        progress_summary={},
    )

    return await run_agent(
        name="debrief",
        user_id=user_id,
        messages=[{"role": "user", "content": user_msg}],
        system=SYSTEM,
        tools=tools,
        provider=provider,
        pool=pool,
        max_steps=5,
        trigger_ref=interview_id,
    )
