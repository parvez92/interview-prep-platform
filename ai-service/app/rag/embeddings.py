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
