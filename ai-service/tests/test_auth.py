"""
Tests for inter-service authentication.

Java sends X-Service-Token on every request (AiClientConfig.java).
Python verifies it against vault secret("service.token").
"""
import pytest

from tests.conftest import TEST_SERVICE_TOKEN, TEST_USER_ID

pytestmark = pytest.mark.asyncio


async def test_missing_service_token_returns_401(client, mock_provider):
    resp = await client.post(
        "/ai/analyze",
        json={"question": "What is recursion?", "answer": "It calls itself."},
        # No X-Service-Token header
        headers={"X-User-Id": str(TEST_USER_ID)},
    )
    assert resp.status_code == 422  # FastAPI 422 for missing required header


async def test_wrong_service_token_returns_401(client, bad_token_headers, mock_provider):
    resp = await client.post(
        "/ai/analyze",
        json={"question": "What is recursion?", "answer": "It calls itself."},
        headers=bad_token_headers,
    )
    assert resp.status_code == 401
    assert "Invalid" in resp.json()["detail"]


async def test_correct_service_token_passes(client, auth_headers, mock_provider_with_json):
    resp = await client.post(
        "/ai/analyze",
        json={"question": "What is recursion?", "answer": "It calls itself."},
        headers=auth_headers,
    )
    assert resp.status_code == 200


async def test_missing_user_id_falls_back_to_default(client, mock_provider_with_json):
    """X-User-Id is optional; Python falls back to settings.default_user_id."""
    resp = await client.post(
        "/ai/analyze",
        json={"question": "Q", "answer": "A"},
        headers={"X-Service-Token": TEST_SERVICE_TOKEN},  # no X-User-Id
    )
    assert resp.status_code == 200


async def test_invalid_user_id_returns_400(client, mock_provider_with_json):
    resp = await client.post(
        "/ai/analyze",
        json={"question": "Q", "answer": "A"},
        headers={"X-Service-Token": TEST_SERVICE_TOKEN, "X-User-Id": "not-a-number"},
    )
    assert resp.status_code == 400


async def test_health_endpoint_requires_no_auth(client):
    resp = await client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"
