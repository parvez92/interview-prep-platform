"""
Feedback-loop RAG: weak-answer embed/delete endpoints, weak-answer retrieval
into the guide prompt, cache-busting on new review flags, and depth-pass
topic-card embedding + related-topic context.
"""
import json

import pytest
import pytest_asyncio

from app.providers.base import EmbedResult

from .conftest import MockLLMProvider, _install_provider


class RecordingProvider(MockLLMProvider):
    """
    Deterministic non-zero embeddings (all texts embed to the same unit vector,
    so cosine distance is 0 and everything passes the retrieval cutoff) and a
    record of every system prompt sent to complete().
    """

    def __init__(self, complete_text: str = '{"concept": "c", "points": ["p"], "angle": "a"}'):
        super().__init__(complete_text)
        self.systems: list[str] = []

    async def complete(self, messages, system=None, max_tokens=4096, use_thinking=False):
        self.systems.append(system or "")
        return await super().complete(messages, system, max_tokens)

    async def embed(self, texts: list[str]) -> EmbedResult:
        vec = [1.0] + [0.0] * 1023
        return EmbedResult(embeddings=[vec for _ in texts], model="mock-embed", total_tokens=0)


@pytest.fixture()
def recording_provider(monkeypatch):
    provider = RecordingProvider()
    _install_provider(monkeypatch, provider)
    yield provider
    from app.providers import registry
    registry._cache.clear()


@pytest_asyncio.fixture()
async def clean_rag_tables(db_pool):
    """All mock embeddings are identical vectors, so retrieval ties are arbitrary —
    prompt-content assertions need to start from an empty store."""
    async with db_pool.connection() as conn:
        await conn.execute("DELETE FROM weak_answer_chunk")
        await conn.execute("DELETE FROM topic_chunk")


async def _count(db_pool, table: str, where: str, params: tuple) -> int:
    async with db_pool.connection() as conn:
        cur = await conn.execute(f"SELECT count(*) FROM {table} WHERE {where}", params)
        return (await cur.fetchone())[0]


# ---------------------------------------------------------------------------
# embed / delete endpoints
# ---------------------------------------------------------------------------

async def test_embed_weak_answer_stores_row(client, recording_provider, db_pool, auth_headers):
    resp = await client.post(
        "/ai/embed-weak-answer",
        json={"question_id": 101, "topic_id": 7, "text": "Explain Kafka ISR shrink"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert resp.json()["result"]["chunks_stored"] == 1
    assert await _count(db_pool, "weak_answer_chunk", "question_id = %s", (101,)) == 1


async def test_embed_weak_answer_upserts_by_question_id(client, recording_provider, db_pool, auth_headers):
    for text in ("first version", "second version"):
        await client.post(
            "/ai/embed-weak-answer",
            json={"question_id": 102, "text": text},
            headers=auth_headers,
        )
    assert await _count(db_pool, "weak_answer_chunk", "question_id = %s", (102,)) == 1
    async with db_pool.connection() as conn:
        cur = await conn.execute("SELECT text FROM weak_answer_chunk WHERE question_id = %s", (102,))
        assert (await cur.fetchone())[0] == "second version"


async def test_delete_weak_answer(client, recording_provider, db_pool, auth_headers):
    await client.post(
        "/ai/embed-weak-answer",
        json={"question_id": 103, "text": "gone soon"},
        headers=auth_headers,
    )
    resp = await client.post("/ai/delete-weak-answer", json={"question_id": 103}, headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["result"]["chunks_deleted"] == 1
    assert await _count(db_pool, "weak_answer_chunk", "question_id = %s", (103,)) == 0


async def test_embed_endpoints_bypass_desktop_mode(client, recording_provider, auth_headers):
    resp = await client.post(
        "/ai/embed-weak-answer",
        json={"question_id": 104, "text": "desktop user weak answer"},
        headers={**auth_headers, "X-Provider": "desktop"},
    )
    assert resp.status_code == 200
    assert resp.json().get("mode") != "desktop"


# ---------------------------------------------------------------------------
# guide integration
# ---------------------------------------------------------------------------

async def test_guide_overview_includes_weak_answers_in_prompt(client, recording_provider, auth_headers, clean_rag_tables):
    await client.post(
        "/ai/embed-weak-answer",
        json={"question_id": 110, "text": "Answered poorly (self-rated 1/5 at Acme): What is ISR shrink?"},
        headers=auth_headers,
    )
    resp = await client.post(
        "/ai/guide",
        json={"tab": "overview", "topic_title": "Kafka replication internals xq1"},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert "What is ISR shrink?" in recording_provider.systems[-1]


async def test_guide_new_review_flag_busts_cache(client, recording_provider, auth_headers):
    body = {"tab": "overview", "topic_title": "Consistent hashing xq2"}

    first = await client.post("/ai/guide", json=body, headers=auth_headers)
    assert first.json()["meta"]["cached"] is False

    second = await client.post("/ai/guide", json=body, headers=auth_headers)
    assert second.json()["meta"]["cached"] is True

    flagged = await client.post(
        "/ai/guide",
        json={**body, "review_reasons": ["self-rated 1/5 at Acme"]},
        headers=auth_headers,
    )
    assert flagged.json()["meta"]["cached"] is False


# ---------------------------------------------------------------------------
# depth integration
# ---------------------------------------------------------------------------

_DEPTH_JSON = json.dumps({
    "units": [{
        "parent": "kafka-internals",
        "_scratch": ["raw"],
        "cards": [{
            "title": "Kafka Replication Mechanics",
            "concept": "Leader-follower replication with ISR tracking.",
            "points": ["replica.lag.time.max.ms controls ISR eviction"],
            "angle": "How does Kafka avoid data loss on leader failure?",
            "est_minutes": 60,
        }],
    }],
})


@pytest.fixture()
def depth_provider(monkeypatch):
    provider = RecordingProvider(complete_text=_DEPTH_JSON)
    _install_provider(monkeypatch, provider)
    yield provider
    from app.providers import registry
    registry._cache.clear()


async def test_depth_embeds_generated_cards(client, depth_provider, db_pool, auth_headers, clean_rag_tables):
    resp = await client.post(
        "/ai/deep-dive",
        json={"topics": [{"slug": "kafka-internals", "title": "Kafka internals",
                          "scope": "replication", "split_hint": "none", "category": "system_design"}]},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert await _count(db_pool, "topic_chunk", "slug LIKE %s", ("kafka-internals::%",)) == 1


async def test_depth_prompt_includes_related_topics_from_earlier_batches(client, depth_provider, auth_headers):
    # first batch seeds topic_chunk (or was seeded by the previous test); second
    # batch has a different parent slug, so the first card qualifies as "related"
    await client.post(
        "/ai/deep-dive",
        json={"topics": [{"slug": "kafka-internals", "title": "Kafka internals",
                          "scope": "replication", "split_hint": "none", "category": "system_design"}]},
        headers=auth_headers,
    )
    resp = await client.post(
        "/ai/deep-dive",
        json={"topics": [{"slug": "stream-processing", "title": "Stream processing",
                          "scope": "exactly-once", "split_hint": "none", "category": "system_design"}]},
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert "Kafka Replication Mechanics" in depth_provider.systems[-1]
    assert "Already covered elsewhere" in depth_provider.systems[-1]


async def test_depth_own_batch_is_excluded_from_related(client, depth_provider, auth_headers):
    # regenerating the same parent must not present its own card as "already covered"
    await client.post(
        "/ai/deep-dive",
        json={"topics": [{"slug": "kafka-internals", "title": "Kafka internals",
                          "scope": "replication", "split_hint": "none", "category": "system_design"}],
              "regenerate": True},
        headers=auth_headers,
    )
    system = depth_provider.systems[-1]
    related_section = system.split("Already covered elsewhere")[-1] if "Already covered elsewhere" in system else ""
    assert "kafka-internals::" not in related_section
