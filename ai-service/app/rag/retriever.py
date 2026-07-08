import logging

import psycopg_pool

from app.providers.base import LLMProvider

log = logging.getLogger(__name__)


async def retrieve(
    pool: psycopg_pool.AsyncConnectionPool,
    provider: LLMProvider,
    user_id: int,
    query: str,
    top_k: int = 5,
) -> list[str]:
    result = await provider.embed([query])
    if not result.embeddings:
        return []

    query_vec = result.embeddings[0]

    async with pool.connection() as conn:
        cur = await conn.execute(
            """
            SELECT text
            FROM resume_chunk
            WHERE user_id = %s
            ORDER BY embedding <=> %s::vector
            LIMIT %s
            """,
            (user_id, query_vec, top_k),
        )
        rows = await cur.fetchall()

    return [row[0] for row in rows]
