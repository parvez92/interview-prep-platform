import json
import logging
import time
from dataclasses import dataclass

import psycopg_pool

from app.agents.tools import ToolSpec
from app.config import settings
from app.providers.base import LLMProvider

log = logging.getLogger(__name__)


@dataclass
class AgentRunResult:
    output: str
    steps: int
    tokens: int
    elapsed_ms: int
    status: str
    model: str
    input_tokens: int
    output_tokens: int


async def run_agent(
    name: str,
    user_id: int,
    messages: list[dict],
    system: str,
    tools: list[ToolSpec],
    provider: LLMProvider,
    pool: psycopg_pool.AsyncConnectionPool,
    max_steps: int | None = None,
    deadline_s: float | None = None,
    max_tokens: int | None = None,
    trigger_ref: str | None = None,
) -> AgentRunResult:
    _max_steps = max_steps or settings.agent_max_steps
    _deadline = deadline_s or settings.agent_deadline_seconds
    _max_tokens = max_tokens or settings.agent_max_tokens

    start = time.monotonic()
    steps = 0
    total_in = 0
    total_out = 0
    conversation = list(messages)
    tool_map = {t.name: t.fn for t in tools}
    tool_defs = [t.schema for t in tools]
    output = ""
    status = "ok"
    last_model = provider.model_id

    try:
        while steps < _max_steps:
            if time.monotonic() - start >= _deadline:
                status = "timeout"
                log.warning("agent=%s timeout after %d steps", name, steps)
                break

            remaining = _max_tokens - total_in - total_out
            result = await provider.tool_call(
                messages=conversation,
                tools=tool_defs,
                system=system,
                max_tokens=min(4096, max(256, remaining)),
            )
            total_in += result.input_tokens
            total_out += result.output_tokens
            last_model = result.model
            steps += 1

            conversation.append({"role": "assistant", "content": result.raw_content})

            if result.stop_reason in ("end_turn", "stop"):
                output = result.content
                status = "ok"
                break

            if result.stop_reason == "tool_use":
                tool_results = []
                for tc in result.tool_calls:
                    fn = tool_map.get(tc.name)
                    if fn is None:
                        tool_results.append({
                            "type": "tool_result",
                            "tool_use_id": tc.id,
                            "content": f"Error: unknown tool '{tc.name}'",
                            "is_error": True,
                        })
                        continue
                    try:
                        tr = await fn(**tc.input)
                        tool_results.append({
                            "type": "tool_result",
                            "tool_use_id": tc.id,
                            "content": json.dumps(tr) if not isinstance(tr, str) else tr,
                        })
                    except Exception as exc:
                        log.warning("tool %s failed: %s", tc.name, exc)
                        tool_results.append({
                            "type": "tool_result",
                            "tool_use_id": tc.id,
                            "content": f"Error: {exc}",
                            "is_error": True,
                        })
                conversation.append({"role": "user", "content": tool_results})
                continue

            status = "error"
            log.error("agent=%s unexpected stop_reason=%s", name, result.stop_reason)
            break
        else:
            status = "max_steps"
            log.warning("agent=%s reached max_steps=%d", name, _max_steps)

    except Exception as exc:
        status = "error"
        output = str(exc)
        log.exception("agent=%s error", name)

    elapsed_ms = int((time.monotonic() - start) * 1000)
    total_tokens = total_in + total_out

    async with pool.connection() as conn:
        await conn.execute(
            """
            INSERT INTO agent_run (user_id, agent, trigger_ref, steps_used, tokens_used, status, result_summary)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            """,
            (user_id, name, trigger_ref, steps, total_tokens, status, (output or "")[:500]),
        )

    return AgentRunResult(
        output=output,
        steps=steps,
        tokens=total_tokens,
        elapsed_ms=elapsed_ms,
        status=status,
        model=last_model,
        input_tokens=total_in,
        output_tokens=total_out,
    )
