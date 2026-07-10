"""
Tests for the DesktopModeMiddleware.

When X-Provider: desktop is set, LLM endpoints return a desktop-mode
response without calling any LLM API. Embed-resume and /ai/settings
are exempt (data ops, not LLM calls).
"""
import pytest

from tests.conftest import TEST_SERVICE_TOKEN, TEST_USER_ID

pytestmark = pytest.mark.asyncio

DESKTOP_HEADERS = {
    "X-Service-Token": TEST_SERVICE_TOKEN,
    "X-User-Id": str(TEST_USER_ID),
    "X-Provider": "desktop",
}


async def test_desktop_intercepts_analyze(client):
    resp = await client.post(
        "/ai/analyze",
        json={"question": "What is O(n)?", "answer": "Linear time."},
        headers=DESKTOP_HEADERS,
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["mode"] == "desktop"
    assert body["result"] is None


async def test_desktop_intercepts_mock(client):
    resp = await client.post(
        "/ai/mock",
        json={"target_role": "SWE", "topic": "arrays"},
        headers=DESKTOP_HEADERS,
    )
    assert resp.status_code == 200
    assert resp.json()["mode"] == "desktop"


async def test_desktop_intercepts_agent_endpoints(client):
    for path in ["/ai/agents/jobfit", "/ai/agents/debrief", "/ai/agents/coach", "/ai/agents/prep-pack"]:
        resp = await client.post(path, json={}, headers=DESKTOP_HEADERS)
        assert resp.status_code == 200, f"{path} not intercepted"
        assert resp.json()["mode"] == "desktop", f"{path} wrong mode"


async def test_desktop_response_shape(client):
    resp = await client.post(
        "/ai/analyze",
        json={"question": "Q", "answer": "A"},
        headers=DESKTOP_HEADERS,
    )
    body = resp.json()
    assert "meta" in body
    assert body["meta"]["model"] == "desktop"
    assert body["meta"]["cost"] == 0.0
    assert body["mode"] == "desktop"
    assert isinstance(body["desktop_prompt"], str) and body["desktop_prompt"]
    assert body["result"] is None


async def test_desktop_does_not_intercept_settings(client):
    """GET /ai/settings must always be reachable regardless of provider."""
    resp = await client.get("/ai/settings", headers=DESKTOP_HEADERS)
    assert resp.status_code == 200
    assert "modes" in resp.json()


async def test_desktop_does_not_intercept_health(client):
    resp = await client.get("/health", headers=DESKTOP_HEADERS)
    assert resp.status_code == 200


async def test_desktop_does_not_intercept_embed(client, mock_provider):
    """embed-resume uses voyageai embeddings — must run even in desktop mode."""
    resp = await client.post(
        "/ai/embed-resume",
        json={"raw_text": "Python developer with 5 years experience."},
        headers=DESKTOP_HEADERS,
    )
    assert resp.status_code == 200
    body = resp.json()
    # Should NOT be a desktop-mode response — should be actual embedding result
    assert body.get("mode") != "desktop"
    assert "result" in body
    assert "chunks_stored" in body["result"]


async def test_api_mode_not_intercepted(client, mock_provider_with_json):
    """Without X-Provider: desktop, the LLM is called normally."""
    resp = await client.post(
        "/ai/analyze",
        json={"question": "Q", "answer": "A"},
        headers={
            "X-Service-Token": TEST_SERVICE_TOKEN,
            "X-User-Id": str(TEST_USER_ID),
            # No X-Provider header
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body.get("mode") != "desktop"
    assert body["result"] is not None
