import logging

import psycopg_pool

from app.providers.base import LLMProvider

log = logging.getLogger(__name__)

# Cosine distance above this is "not actually about this topic" — retrieval is
# nearest-neighbour, so without a cutoff every query returns *something*.
_MAX_DISTANCE = 0.5


async def _embed_query(provider: LLMProvider, query: str) -> list | None:
    result = await provider.embed([query])
    return result.embeddings[0] if result.embeddings else None


async def retrieve(
    pool: psycopg_pool.AsyncConnectionPool,
    provider: LLMProvider,
    user_id: int,
    query: str,
    top_k: int = 5,
) -> list[str]:
    """Resume chunks nearest the query (no cutoff — the resume is always relevant context)."""
    query_vec = await _embed_query(provider, query)
    if query_vec is None:
        return []

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


async def retrieve_weak_answers(
    pool: psycopg_pool.AsyncConnectionPool,
    provider: LLMProvider,
    user_id: int,
    query: str,
    top_k: int = 3,
    max_distance: float = _MAX_DISTANCE,
) -> list[str]:
    """Interview questions the user answered poorly that are semantically near the query."""
    query_vec = await _embed_query(provider, query)
    if query_vec is None:
        return []

    async with pool.connection() as conn:
        cur = await conn.execute(
            """
            SELECT text
            FROM weak_answer_chunk
            WHERE user_id = %s AND embedding <=> %s::vector < %s
            ORDER BY embedding <=> %s::vector
            LIMIT %s
            """,
            (user_id, query_vec, max_distance, query_vec, top_k),
        )
        rows = await cur.fetchall()

    return [row[0] for row in rows]


async def retrieve_related_topics(
    pool: psycopg_pool.AsyncConnectionPool,
    provider: LLMProvider,
    user_id: int,
    query: str,
    exclude_parents: list[str],
    top_k: int = 4,
    max_distance: float = _MAX_DISTANCE,
) -> list[dict]:
    """
    Already-generated deep-dive cards near the query, excluding cards that belong
    to the coarse topics being generated right now (slug format: "<parent>::<card>").
    Returns [{title, text}].
    """
    query_vec = await _embed_query(provider, query)
    if query_vec is None:
        return []

    async with pool.connection() as conn:
        cur = await conn.execute(
            """
            SELECT title, text
            FROM topic_chunk
            WHERE user_id = %s
              AND split_part(slug, '::', 1) != ALL(%s)
              AND embedding <=> %s::vector < %s
            ORDER BY embedding <=> %s::vector
            LIMIT %s
            """,
            (user_id, exclude_parents, query_vec, max_distance, query_vec, top_k),
        )
        rows = await cur.fetchall()

    return [{"title": row[0], "text": row[1]} for row in rows]
