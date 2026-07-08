import json
import logging
from pathlib import Path

import psycopg_pool
from jinja2 import Environment, FileSystemLoader

from app.providers.base import LLMProvider

_env = Environment(loader=FileSystemLoader(Path(__file__).parent.parent / "prompts"))
log = logging.getLogger(__name__)


async def start_mock_session(
    user_id: int,
    target_role: str,
    topic: str,
    difficulty: str,
    interview_type: str,
    seniority: str,
    strengths: list[str],
    provider: LLMProvider,
    pool: psycopg_pool.AsyncConnectionPool,
    history: list[dict] | None = None,
) -> dict:
    system = _env.get_template("mock.md").render(
        target_role=target_role,
        topic=topic,
        difficulty=difficulty,
        interview_type=interview_type,
        seniority=seniority,
        strengths=strengths,
    )

    messages = history or []

    result = await provider.complete(
        messages=messages if messages else [{"role": "user", "content": "Begin the interview."}],
        system=system,
        max_tokens=1024,
    )

    return {
        "reply": result.content,
        "input_tokens": result.input_tokens,
        "output_tokens": result.output_tokens,
        "model": result.model,
    }
