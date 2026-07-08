import hashlib
import json
import logging
from datetime import datetime, timezone, timedelta

import psycopg_pool

log = logging.getLogger(__name__)

DEFAULT_TTL_HOURS = 168  # 7 days


def make_cache_key(kind: str, payload: dict) -> str:
    raw = json.dumps({"kind": kind, **payload}, sort_keys=True)
    return hashlib.sha256(raw.encode()).hexdigest()


async def cache_get(
    pool: psycopg_pool.AsyncConnectionPool,
    user_id: int,
    cache_key: str,
) -> dict | None:
    async with pool.connection() as conn:
        cur = await conn.execute(
            """
            SELECT response_json, model FROM ai_cache
            WHERE cache_key = %s AND user_id = %s
            """,
            (cache_key, user_id),
        )
        row = await cur.fetchone()
    if row is None:
        return None
    try:
        return {"content": json.loads(row[0]), "model": row[1]}
    except json.JSONDecodeError:
        return {"content": row[0], "model": row[1]}


async def cache_put(
    pool: psycopg_pool.AsyncConnectionPool,
    user_id: int,
    cache_key: str,
    kind: str,
    content: str | dict,
    model: str,
) -> None:
    content_str = json.dumps(content) if isinstance(content, dict) else content
    async with pool.connection() as conn:
        await conn.execute(
            """
            INSERT INTO ai_cache (user_id, cache_key, kind, response_json, model)
            VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT (cache_key) DO UPDATE
                SET response_json = EXCLUDED.response_json, model = EXCLUDED.model, created_at = now()
            """,
            (user_id, cache_key, kind, content_str, model),
        )
    log.debug("cache_put kind=%s key=%s", kind, cache_key[:16])
