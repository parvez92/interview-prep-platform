from pathlib import Path

import psycopg_pool
from jinja2 import Environment, FileSystemLoader

from app.agents.loop import AgentRunResult, run_agent
from app.agents.tools import make_core_tools, make_vector_search_tool
from app.providers.base import LLMProvider

_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))

SYSTEM = """You are a job-fit analyst. Given a job description and the candidate's profile,
assess fit, identify gaps, and create a targeted prep plan. Use the available tools to
check the candidate's current study progress and search their resume. Be concise and actionable."""


async def run_jobfit(
    user_id: int,
    job_description: str,
    company: str,
    role: str,
    profile: dict,
    provider: LLMProvider,
    pool: psycopg_pool.AsyncConnectionPool,
    job_id: str | None = None,
) -> AgentRunResult:
    tools = make_core_tools(user_id) + [make_vector_search_tool(pool, user_id)]

    template = _env.get_template("prep_pack.md")
    user_msg = template.render(
        job_description=job_description,
        company=company,
        role=role,
        profile=profile,
        progress_summary={},
    )

    return await run_agent(
        name="jobfit",
        user_id=user_id,
        messages=[{"role": "user", "content": user_msg}],
        system=SYSTEM,
        tools=tools,
        provider=provider,
        pool=pool,
        max_steps=6,
        trigger_ref=job_id,
    )
