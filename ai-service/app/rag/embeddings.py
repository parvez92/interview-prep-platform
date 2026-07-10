import logging

import psycopg_pool

from app.providers.base import LLMProvider
from app.rag.chunker import chunk_text

log = logging.getLogger(__name__)

_BATCH_SIZE = 32


async def embed_and_store(
    pool: psycopg_pool.AsyncConnectionPool,
    provider: LLMProvider,
    user_id: int,
    text: str,
) -> int:
    chunks = chunk_text(text)
    if not chunks:
        return 0

    texts = [c.text for c in chunks]
    batches = [texts[i: i + _BATCH_SIZE] for i in range(0, len(texts), _BATCH_SIZE)]

    async with pool.connection() as conn:
        await conn.execute("DELETE FROM resume_chunk WHERE user_id = %s", (user_id,))

        stored = 0
        for batch_texts in batches:
            result = await provider.embed(batch_texts)
            for text_chunk, embedding in zip(batch_texts, result.embeddings):
                await conn.execute(
                    "INSERT INTO resume_chunk (user_id, text, embedding) VALUES (%s, %s, %s)",
                    (user_id, text_chunk, embedding),
                )
                stored += 1

    log.info("embed_and_store user=%d chunks=%d", user_id, stored)
    return stored


async def embed_weak_answer(
    pool: psycopg_pool.AsyncConnectionPool,
    provider: LLMProvider,
    user_id: int,
    question_id: int,
    topic_id: int | None,
    text: str,
) -> int:
    """Upsert one weak-answer chunk keyed by question_id (re-rating replaces it)."""
    result = await provider.embed([text])
    if not result.embeddings:
        return 0

    async with pool.connection() as conn:
        await conn.execute(
            "DELETE FROM weak_answer_chunk WHERE user_id = %s AND question_id = %s",
            (user_id, question_id),
        )
        await conn.execute(
            "INSERT INTO weak_answer_chunk (user_id, question_id, topic_id, text, embedding)"
            " VALUES (%s, %s, %s, %s, %s)",
            (user_id, question_id, topic_id, text, result.embeddings[0]),
        )
    log.info("embed_weak_answer user=%d question=%d", user_id, question_id)
    return 1


async def delete_weak_answer(
    pool: psycopg_pool.AsyncConnectionPool,
    user_id: int,
    question_id: int,
) -> int:
    async with pool.connection() as conn:
        cur = await conn.execute(
            "DELETE FROM weak_answer_chunk WHERE user_id = %s AND question_id = %s",
            (user_id, question_id),
        )
        return cur.rowcount


async def embed_topic_cards(
    pool: psycopg_pool.AsyncConnectionPool,
    provider: LLMProvider,
    user_id: int,
    cards: list[dict],
) -> int:
    """
    Upsert one chunk per generated deep-dive card: cards = [{slug, title, text}].
    Keyed by (user_id, slug) so regeneration replaces the old card.
    """
    cards = [c for c in cards if c.get("slug") and c.get("text")]
    if not cards:
        return 0

    result = await provider.embed([f"{c['title']}\n{c['text']}" for c in cards])
    stored = 0
    async with pool.connection() as conn:
        for card, embedding in zip(cards, result.embeddings):
            await conn.execute(
                "DELETE FROM topic_chunk WHERE user_id = %s AND slug = %s",
                (user_id, card["slug"]),
            )
            await conn.execute(
                "INSERT INTO topic_chunk (user_id, slug, title, text, embedding)"
                " VALUES (%s, %s, %s, %s, %s)",
                (user_id, card["slug"], card["title"], card["text"], embedding),
            )
            stored += 1
    log.info("embed_topic_cards user=%d cards=%d", user_id, stored)
    return stored
