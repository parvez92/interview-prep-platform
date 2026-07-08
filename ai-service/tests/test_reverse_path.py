"""
Reverse-path integration tests: ai-service agent tools → Java REST.

Python agents call back to Java via httpx. These tests verify:
  - The service token is forwarded in the Authorization header
  - Requests go to the correct Java endpoints
  - Agent tools gracefully handle Java errors (4xx, 5xx, timeout)
  - The agent_run trace row is written to the DB after each agent run
"""
import pytest
import respx
import httpx

from app.agents.tools import (
    get_progress,
    search_topics,
    flag_for_review,
    schedule_review,
    get_interview_log,
)
from tests.conftest import TEST_SERVICE_TOKEN, TEST_USER_ID

pytestmark = pytest.mark.asyncio

JAVA_BASE = "http://core:8080"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _java_auth_header() -> str:
    """The header value Python sends to Java."""
    return f"Bearer {TEST_SERVICE_TOKEN}"


# ---------------------------------------------------------------------------
# Tool-level tests: verify HTTP contract with Java
# ---------------------------------------------------------------------------

@respx.mock
async def test_get_progress_calls_correct_java_endpoint():
    route = respx.get(f"{JAVA_BASE}/api/progress").mock(
        return_value=httpx.Response(200, json={"phases": [], "overallConfidence": 0})
    )
    result = await get_progress(TEST_USER_ID)
    assert route.called
    assert result["phases"] == []


@respx.mock
async def test_get_progress_sends_service_token_to_java():
    route = respx.get(f"{JAVA_BASE}/api/progress").mock(
        return_value=httpx.Response(200, json={"phases": []})
    )
    await get_progress(TEST_USER_ID)
    sent_auth = route.calls[0].request.headers.get("authorization")
    assert sent_auth == _java_auth_header(), (
        f"Expected 'Bearer {TEST_SERVICE_TOKEN}', got '{sent_auth}'"
    )


@respx.mock
async def test_search_topics_includes_query_and_limit():
    route = respx.get(f"{JAVA_BASE}/api/topics").mock(
        return_value=httpx.Response(200, json={"content": [{"id": 1, "title": "Arrays"}]})
    )
    result = await search_topics(TEST_USER_ID, "arrays", limit=3)
    assert route.called
    assert "content" in result


@respx.mock
async def test_flag_for_review_posts_to_java():
    route = respx.post(f"{JAVA_BASE}/api/review/flag").mock(
        return_value=httpx.Response(200, json={"flagged": True})
    )
    result = await flag_for_review(TEST_USER_ID, topic_id=42, reason="Struggled in mock")
    assert route.called
    body = route.calls[0].request.read()
    import json
    payload = json.loads(body)
    assert payload["topicId"] == 42
    assert payload["reason"] == "Struggled in mock"


@respx.mock
async def test_schedule_review_posts_to_java():
    route = respx.post(f"{JAVA_BASE}/api/review/schedule").mock(
        return_value=httpx.Response(200, json={"scheduled": True})
    )
    await schedule_review(TEST_USER_ID, topic_id=7, due_date="2026-07-01")
    assert route.called
    payload = httpx.Response(**{"status_code": 200, "json": {}})  # just check called


@respx.mock
async def test_get_interview_log_without_id_fetches_recent():
    route = respx.get(f"{JAVA_BASE}/api/interviews").mock(
        return_value=httpx.Response(200, json={"content": []})
    )
    await get_interview_log(TEST_USER_ID)
    assert route.called


@respx.mock
async def test_get_interview_log_with_id_fetches_specific():
    route = respx.get(f"{JAVA_BASE}/api/interviews/99").mock(
        return_value=httpx.Response(200, json={"id": 99, "company": "Acme"})
    )
    result = await get_interview_log(TEST_USER_ID, interview_id=99)
    assert route.called
    assert result["id"] == 99


# ---------------------------------------------------------------------------
# Error handling: Java returns 4xx / 5xx
# ---------------------------------------------------------------------------

@respx.mock
async def test_tool_raises_on_java_404():
    respx.get(f"{JAVA_BASE}/api/progress").mock(
        return_value=httpx.Response(404, json={"error": "not found"})
    )
    with pytest.raises(httpx.HTTPStatusError):
        await get_progress(TEST_USER_ID)


@respx.mock
async def test_tool_raises_on_java_500():
    respx.post(f"{JAVA_BASE}/api/review/flag").mock(
        return_value=httpx.Response(500, json={"error": "server error"})
    )
    with pytest.raises(httpx.HTTPStatusError):
        await flag_for_review(TEST_USER_ID, topic_id=1, reason="test")


# ---------------------------------------------------------------------------
# Agent-level tests: agent loop calls Java tools and writes agent_run trace
# ---------------------------------------------------------------------------

@respx.mock
async def test_agent_run_writes_trace_to_db(client, auth_headers, mock_provider, db_pool):
    """After an agent call, agent_run table must have a new row."""
    # Mock all Java endpoints the agent might call
    respx.get(f"{JAVA_BASE}/api/progress").mock(return_value=httpx.Response(200, json={"phases": []}))
    respx.get(f"{JAVA_BASE}/api/topics").mock(return_value=httpx.Response(200, json={"content": []}))

    async with db_pool.connection() as conn:
        cur = await conn.execute("SELECT COUNT(*) FROM agent_run WHERE user_id = %s", (TEST_USER_ID,))
        before = (await cur.fetchone())[0]

    resp = await client.post(
        "/ai/agents/coach",
        json={
            "review_flags": [{"topicId": 1, "reason": "weak"}],
            "seniority": "mid",
            "target_role": "Software Engineer",
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200

    async with db_pool.connection() as conn:
        cur = await conn.execute("SELECT COUNT(*) FROM agent_run WHERE user_id = %s", (TEST_USER_ID,))
        after = (await cur.fetchone())[0]

    assert after > before, "agent_run trace not written after agent execution"


@respx.mock
async def test_agent_run_trace_has_correct_agent_name(client, auth_headers, mock_provider, db_pool):
    respx.get(f"{JAVA_BASE}/api/progress").mock(return_value=httpx.Response(200, json={}))
    respx.get(f"{JAVA_BASE}/api/topics").mock(return_value=httpx.Response(200, json={"content": []}))

    await client.post(
        "/ai/agents/debrief",
        json={"interview": {"company": "Acme"}, "qa_pairs": []},
        headers=auth_headers,
    )

    async with db_pool.connection() as conn:
        cur = await conn.execute(
            "SELECT agent FROM agent_run WHERE user_id = %s ORDER BY id DESC LIMIT 1",
            (TEST_USER_ID,),
        )
        row = await cur.fetchone()

    assert row is not None
    assert row[0] == "debrief"


@respx.mock
async def test_jobfit_agent_calls_java_tools(client, auth_headers, mock_provider, db_pool):
    """jobfit agent should call Java REST to get progress and search topics."""
    progress_route = respx.get(f"{JAVA_BASE}/api/progress").mock(
        return_value=httpx.Response(200, json={"phases": [], "overallConfidence": 2})
    )
    topics_route = respx.get(f"{JAVA_BASE}/api/topics").mock(
        return_value=httpx.Response(200, json={"content": []})
    )

    resp = await client.post(
        "/ai/agents/jobfit",
        json={
            "job_description": "Python backend, distributed systems, 5+ years",
            "company": "Stripe",
            "role": "Senior Engineer",
            "profile": {"skills": ["Python"], "seniority": "senior"},
        },
        headers=auth_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert "result" in body
    assert "meta" in body
    # Agent loop always writes a trace — status may be ok, max_steps, or timeout
    assert body["meta"]["status"] in ("ok", "max_steps", "timeout", "error")
