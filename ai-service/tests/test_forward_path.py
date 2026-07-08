"""
Forward-path integration tests: Java → ai-service.

Verifies the exact contract AiClient.java uses:
  - X-Service-Token header for auth
  - X-User-Id header for user scoping
  - "model" field injected into POST body by BudgetGuard
  - Extra unknown fields in body are silently ignored
  - Response shape: {result, meta: {model, cached, tokens, cost}}
  - Cache: second identical call returns cached=True, same result
  - Usage log: a row is written to usage_log after each LLM call
"""
import json
import pytest

from tests.conftest import TEST_SERVICE_TOKEN, TEST_USER_ID

pytestmark = pytest.mark.asyncio


def _headers(model: str = "claude-haiku-4-5-20251001") -> dict:
    """Simulate exactly what AiClient.java sends."""
    return {
        "X-Service-Token": TEST_SERVICE_TOKEN,
        "X-User-Id": str(TEST_USER_ID),
        # Java does NOT send X-Provider or X-Model — it injects model into body
    }


# ---------------------------------------------------------------------------
# Body format — Java injects "model" and sometimes extra fields
# ---------------------------------------------------------------------------

async def test_java_model_field_in_body_is_accepted(client, mock_provider_with_json):
    """Java's AiClient.post() injects 'model' into every POST body."""
    resp = await client.post(
        "/ai/analyze",
        json={
            "question": "What is BFS?",
            "answer": "Breadth-first search.",
            "model": "claude-haiku-4-5-20251001",  # injected by Java BudgetGuard
        },
        headers=_headers(),
    )
    assert resp.status_code == 200, resp.text


async def test_extra_fields_in_body_are_ignored(client, mock_provider_with_json):
    """Java may add other metadata fields; Pydantic must not reject them."""
    resp = await client.post(
        "/ai/analyze",
        json={
            "question": "Q",
            "answer": "A",
            "model": "claude-opus-4-8",
            "budgetWarning": True,          # Java adds this
            "_requestId": "abc-123",        # hypothetical future field
        },
        headers=_headers(),
    )
    assert resp.status_code == 200, resp.text


async def test_mock_body_accepted_with_java_fields(client, mock_provider):
    """POST /ai/mock — Java injects model + may send extra fields."""
    resp = await client.post(
        "/ai/mock",
        json={
            "target_role": "Software Engineer",
            "topic": "dynamic programming",
            "difficulty": "medium",
            "model": "claude-haiku-4-5-20251001",
        },
        headers=_headers(),
    )
    assert resp.status_code == 200, resp.text


# ---------------------------------------------------------------------------
# Response envelope shape
# ---------------------------------------------------------------------------

async def test_response_envelope_has_required_fields(client, mock_provider_with_json):
    resp = await client.post(
        "/ai/analyze",
        json={"question": "What is a hash map?", "answer": "Key-value store."},
        headers=_headers(),
    )
    body = resp.json()
    assert "result" in body, "missing 'result'"
    assert "meta" in body, "missing 'meta'"

    meta = body["meta"]
    assert "model" in meta
    assert "cached" in meta
    assert "tokens" in meta
    assert "cost" in meta
    assert isinstance(meta["cached"], bool)
    assert isinstance(meta["tokens"], int)
    assert isinstance(meta["cost"], float)


async def test_mock_response_envelope(client, mock_provider):
    resp = await client.post(
        "/ai/mock",
        json={"target_role": "SWE", "topic": "arrays", "model": "claude-haiku-4-5-20251001"},
        headers=_headers(),
    )
    body = resp.json()
    assert resp.status_code == 200
    assert "result" in body
    assert "meta" in body
    assert "reply" in body["result"]


# ---------------------------------------------------------------------------
# Cache: identical requests return cached=True on the second call
# ---------------------------------------------------------------------------

async def test_cache_miss_then_hit(client, mock_provider_with_json, db_pool):
    payload = {"question": "Explain quicksort unique_cache_key_123", "answer": "Pivot-based sort."}

    # Clear any existing cache entry
    async with db_pool.connection() as conn:
        await conn.execute("DELETE FROM ai_cache WHERE user_id = %s", (TEST_USER_ID,))

    first = await client.post("/ai/analyze", json=payload, headers=_headers())
    assert first.status_code == 200
    assert first.json()["meta"]["cached"] is False

    second = await client.post("/ai/analyze", json=payload, headers=_headers())
    assert second.status_code == 200
    assert second.json()["meta"]["cached"] is True
    assert second.json()["meta"]["tokens"] == 0
    assert second.json()["meta"]["cost"] == 0.0


async def test_cache_result_identical_to_original(client, mock_provider_with_json, db_pool):
    payload = {"question": "Explain DFS unique_cache_key_456", "answer": "Stack-based traversal."}

    async with db_pool.connection() as conn:
        await conn.execute("DELETE FROM ai_cache WHERE user_id = %s", (TEST_USER_ID,))

    first = await client.post("/ai/analyze", json=payload, headers=_headers())
    second = await client.post("/ai/analyze", json=payload, headers=_headers())

    assert first.json()["result"] == second.json()["result"]


# ---------------------------------------------------------------------------
# Usage log: written after every non-cached LLM call
# ---------------------------------------------------------------------------

async def test_usage_log_row_written_after_llm_call(client, mock_provider_with_json, db_pool):
    async with db_pool.connection() as conn:
        await conn.execute("DELETE FROM ai_cache WHERE user_id = %s", (TEST_USER_ID,))
        cur = await conn.execute(
            "SELECT COUNT(*) FROM usage_log WHERE user_id = %s", (TEST_USER_ID,)
        )
        before = (await cur.fetchone())[0]

    await client.post(
        "/ai/analyze",
        json={"question": "Unique usage log test question abc", "answer": "Answer."},
        headers=_headers(),
    )

    async with db_pool.connection() as conn:
        cur = await conn.execute(
            "SELECT COUNT(*) FROM usage_log WHERE user_id = %s", (TEST_USER_ID,)
        )
        after = (await cur.fetchone())[0]

    assert after > before, "usage_log row not written after LLM call"


async def test_usage_log_not_written_for_cache_hit(client, mock_provider_with_json, db_pool):
    payload = {"question": "Cache no usage log test xyz", "answer": "A."}

    async with db_pool.connection() as conn:
        await conn.execute("DELETE FROM ai_cache WHERE user_id = %s", (TEST_USER_ID,))

    # First call — writes usage log
    await client.post("/ai/analyze", json=payload, headers=_headers())

    async with db_pool.connection() as conn:
        cur = await conn.execute("SELECT COUNT(*) FROM usage_log WHERE user_id = %s", (TEST_USER_ID,))
        before_second = (await cur.fetchone())[0]

    # Second call — cache hit, no LLM, no usage log
    await client.post("/ai/analyze", json=payload, headers=_headers())

    async with db_pool.connection() as conn:
        cur = await conn.execute("SELECT COUNT(*) FROM usage_log WHERE user_id = %s", (TEST_USER_ID,))
        after_second = (await cur.fetchone())[0]

    assert after_second == before_second, "usage_log should not grow on cache hit"
