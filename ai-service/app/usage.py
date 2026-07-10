import logging

import psycopg_pool

log = logging.getLogger(__name__)

PRICING: dict[str, tuple[float, float]] = {
    "claude-opus-4-8": (5.0, 25.0),
    "claude-opus-4-7": (5.0, 25.0),
    "claude-opus-4-6": (5.0, 25.0),
    "claude-sonnet-5": (3.0, 15.0),
    "claude-sonnet-4-6": (3.0, 15.0),
    "claude-haiku-4-5": (1.0, 5.0),
    "claude-haiku-4-5-20251001": (1.0, 5.0),
    "claude-fable-5": (10.0, 50.0),
}


def compute_cost(model: str, input_tokens: int, output_tokens: int) -> float:
    in_rate, out_rate = PRICING.get(model, (5.0, 25.0))
    return (input_tokens * in_rate + output_tokens * out_rate) / 1_000_000


async def log_usage(
    pool: psycopg_pool.AsyncConnectionPool,
    user_id: int,
    source: str,
    model: str,
    tokens_in: int,
    tokens_out: int,
    provider: str = "anthropic",
) -> float:
    cost = compute_cost(model, tokens_in, tokens_out)
    async with pool.connection() as conn:
        await conn.execute(
            """
            INSERT INTO usage_log (user_id, feature, model, prompt_tokens, completion_tokens, cost_usd, provider)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            """,
            (user_id, source, model, tokens_in, tokens_out, cost, provider),
        )
    log.debug("usage feature=%s model=%s in=%d out=%d cost=%.6f", source, model, tokens_in, tokens_out, cost)
    return cost


async def get_spend_today(pool: psycopg_pool.AsyncConnectionPool, user_id: int) -> float:
    async with pool.connection() as conn:
        cur = await conn.execute(
            """
            SELECT COALESCE(SUM(cost_usd), 0)
            FROM usage_log
            WHERE user_id = %s AND created_at >= CURRENT_DATE
            """,
            (user_id,),
        )
        row = await cur.fetchone()
    return float(row[0]) if row else 0.0
