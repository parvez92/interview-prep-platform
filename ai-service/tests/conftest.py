"""
Shared fixtures for ai-service integration tests.

Isolation strategy:
  - Vault:  patched at module level; load_secrets() is mocked in lifespan
  - LLM:    MockLLMProvider injected via provider registry patch
  - DB:     real Postgres via testcontainers (pgvector image)
  - Java:   httpx calls mocked with respx per-test
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import AsyncIterator
from unittest.mock import AsyncMock, patch

import psycopg
import pytest
import pytest_asyncio
from asgi_lifespan import LifespanManager
from httpx import ASGITransport, AsyncClient
from testcontainers.postgres import PostgresContainer

from app.providers.base import (
    CompletionResult,
    EmbedResult,
    LLMProvider,
    ToolCallResult,
    ToolUse,
)

# ---------------------------------------------------------------------------
# Test constants
# ---------------------------------------------------------------------------

TEST_SERVICE_TOKEN = "test-service-token-abc123"
TEST_USER_ID = 1

TEST_VAULT_SECRETS: dict[str, str] = {
    "db.password": "test",
    "service.token": TEST_SERVICE_TOKEN,
    "anthropic.api_key": "sk-test-anthropic",
    "voyage.api_key": "pa-test-voyage",
    "openai.api_key": "sk-test-openai",
    "aws.access_key_id": "AKIATEST",
    "aws.secret_access_key": "test-secret",
    "aws.region": "us-east-1",
}


# ---------------------------------------------------------------------------
# Mock LLM provider — fixture responses, no live API calls
# ---------------------------------------------------------------------------

class MockLLMProvider(LLMProvider):
    def __init__(
        self,
        complete_text: str = '{"answer": "mock answer"}',
        tool_calls: list[ToolUse] | None = None,
    ) -> None:
        self._complete_text = complete_text
        self._tool_calls = tool_calls or []

    @property
    def model_id(self) -> str:
        return "mock-model"

    async def complete(self, messages, system=None, max_tokens=4096, use_thinking=False):
        return CompletionResult(
            content=self._complete_text,
            input_tokens=10,
            output_tokens=20,
            model="mock-model",
            stop_reason="end_turn",
        )

    async def tool_call(self, messages, tools, system=None, max_tokens=4096):
        raw = [{"type": "text", "text": self._complete_text}]
        for tc in self._tool_calls:
            raw.append({"type": "tool_use", "id": tc.id, "name": tc.name, "input": tc.input})
        return ToolCallResult(
            content=self._complete_text,
            tool_calls=self._tool_calls,
            raw_content=raw,
            input_tokens=10,
            output_tokens=20,
            model="mock-model",
            stop_reason="tool_use" if self._tool_calls else "end_turn",
        )

    async def embed(self, texts: list[str]) -> EmbedResult:
        return EmbedResult(
            embeddings=[[0.0] * 1024 for _ in texts],
            model="mock-embed",
            total_tokens=len(texts) * 5,
        )


# ---------------------------------------------------------------------------
# Postgres container (session-scoped — one container for all tests)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def postgres():
    with PostgresContainer(
        image="pgvector/pgvector:pg16",
        username="test",
        password="test",
        dbname="testdb",
    ) as pg:
        yield pg


# ---------------------------------------------------------------------------
# Patch vault + config BEFORE the app lifespan runs
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session", autouse=True)
def patch_vault_and_config(postgres):
    """
    1. Inject test secrets into vault._secrets so secret() calls work.
    2. Redirect app.config.settings DB fields to the test container.
    """
    import app.vault as vault_module
    vault_module._secrets = dict(TEST_VAULT_SECRETS)

    from app.config import settings
    settings.db_host = postgres.get_container_host_ip()
    settings.db_port = int(postgres.get_exposed_port(5432))
    settings.db_name = "testdb"
    settings.db_user = "test"
    settings.default_user_id = TEST_USER_ID
    yield


# ---------------------------------------------------------------------------
# DB schema setup (sync, runs once per session)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session", autouse=True)
def create_schema(postgres, patch_vault_and_config):
    sql = (Path(__file__).parent / "db_setup.sql").read_text()
    conninfo = (
        f"host={postgres.get_container_host_ip()} "
        f"port={postgres.get_exposed_port(5432)} "
        f"dbname=testdb user=test password=test"
    )
    with psycopg.connect(conninfo, autocommit=True) as conn:
        conn.execute(sql)


# ---------------------------------------------------------------------------
# DB pool (async, initialized once and reused)
# ---------------------------------------------------------------------------

@pytest_asyncio.fixture(scope="session")
async def db_pool(create_schema):
    from app.db import close_db, get_pool, init_db
    await init_db(db_password="test")
    yield get_pool()
    await close_db()


# ---------------------------------------------------------------------------
# ASGI test client
# ---------------------------------------------------------------------------

@pytest_asyncio.fixture(scope="session")
async def client(db_pool) -> AsyncIterator[AsyncClient]:
    """
    Wraps the FastAPI app in an ASGI test client.
    Lifespan load_secrets() and init_db() are patched to no-ops
    (vault and DB are already set up by session fixtures).
    """
    with (
        patch("app.vault.load_secrets", return_value=TEST_VAULT_SECRETS),
        patch("app.db.init_db", new_callable=AsyncMock),
        patch("app.db.close_db", new_callable=AsyncMock),
    ):
        from app.main import app
        async with LifespanManager(app) as manager:
            async with AsyncClient(
                transport=ASGITransport(app=manager.app),
                base_url="http://testserver",
            ) as c:
                yield c


# ---------------------------------------------------------------------------
# Common request header helpers
# ---------------------------------------------------------------------------

@pytest.fixture()
def auth_headers() -> dict[str, str]:
    return {
        "X-Service-Token": TEST_SERVICE_TOKEN,
        "X-User-Id": str(TEST_USER_ID),
    }


@pytest.fixture()
def bad_token_headers() -> dict[str, str]:
    return {
        "X-Service-Token": "wrong-token",
        "X-User-Id": str(TEST_USER_ID),
    }


# ---------------------------------------------------------------------------
# Mock provider — patches the provider registry per test
# ---------------------------------------------------------------------------

@pytest.fixture()
def mock_provider(monkeypatch) -> MockLLMProvider:
    provider = MockLLMProvider()
    monkeypatch.setattr("app.providers.registry.get_provider", lambda *a, **kw: provider)
    return provider


@pytest.fixture()
def mock_provider_with_json(monkeypatch) -> MockLLMProvider:
    """Provider that returns valid JSON (useful for parse/analyze/score)."""
    provider = MockLLMProvider(complete_text='{"rating": 4, "strengths": ["clear"], "weaknesses": [], "ideal_points": [], "flag_for_review": false, "summary": "Good."}')
    monkeypatch.setattr("app.providers.registry.get_provider", lambda *a, **kw: provider)
    return provider
