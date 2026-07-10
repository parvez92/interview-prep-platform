"""
Depth-pass repair contract (fix-brief §1).

When Java's covered-split validator finds that the depth pass narrowed a coarse unit —
dropped scope terms instead of splitting — it re-calls /ai/deep-dive with `must_cover`.
That call must:
  - reach the model as an explicit repair instruction naming the dropped terms
  - bypass the cache entry written by the plain (non-repair) call
"""
import json

import pytest

from tests.conftest import TEST_SERVICE_TOKEN, MockLLMProvider, _install_provider

pytestmark = pytest.mark.asyncio

# Own user: ai_cache and topic_chunk are user-scoped, and the mock provider embeds every
# card to the zero vector — reusing TEST_USER_ID would let these rows outrank the fixtures
# other tests retrieve.
DEPTH_USER_ID = 9001


def _headers() -> dict:
    return {"X-Service-Token": TEST_SERVICE_TOKEN, "X-User-Id": str(DEPTH_USER_ID)}


def _card(title: str) -> dict:
    return {
        "title": title,
        "concept": "A mechanism worth two sentences of explanation about how it actually works.",
        "points": ["acks=all", "min.insync.replicas=2", "ThreadPoolExecutor", "isolation.level=read_committed"],
        "angle": "Why does this fail under load?",
        "est_minutes": 40,
    }


class RecordingProvider(MockLLMProvider):
    """Captures the system prompt + user turn so we can assert on what the model saw."""

    def __init__(self, text: str) -> None:
        super().__init__(complete_text=text)
        self.system: str | None = None
        self.messages: list | None = None

    async def complete(self, messages, system=None, max_tokens=4096, use_thinking=False):
        self.system = system
        self.messages = messages
        return await super().complete(messages, system, max_tokens, use_thinking)


@pytest.fixture()
def repair_provider(monkeypatch):
    payload = json.dumps({"units": [{"parent": "kafka-internals", "cards": [_card("CompletableFuture composition")]}]})
    provider = RecordingProvider(payload)
    _install_provider(monkeypatch, provider)
    yield provider
    from app.providers import registry
    registry._cache.clear()


_TOPIC = {
    "slug": "kafka-internals",
    "title": "Kafka internals",
    "scope": "partitions, ISR, exactly-once",
    "split_hint": "likely",
    "category": "domain",
}


async def test_must_cover_reaches_the_model_as_a_repair_instruction(client, repair_provider):
    resp = await client.post(
        "/ai/deep-dive",
        json={"topics": [_TOPIC], "regenerate": True, "must_cover": ["CompletableFuture", "locks"]},
        headers=_headers(),
    )
    assert resp.status_code == 200, resp.text

    system = repair_provider.system
    assert "REPAIR pass" in system
    assert "CompletableFuture, locks" in system
    assert "2 card(s) total" in system

    user_turn = repair_provider.messages[0]["content"]
    assert "CompletableFuture, locks" in user_turn

    cards = resp.json()["result"]["topics"]
    assert [c["parent"] for c in cards] == ["kafka-internals"]


async def test_plain_call_carries_no_repair_block(client, repair_provider):
    resp = await client.post(
        "/ai/deep-dive",
        json={"topics": [_TOPIC], "regenerate": True},
        headers=_headers(),
    )
    assert resp.status_code == 200, resp.text
    assert "REPAIR pass" not in repair_provider.system
    # the scope-as-contract rule is always stated, repair or not
    assert "CONTRACT" in repair_provider.system


async def test_must_cover_is_part_of_the_cache_key(client, repair_provider):
    """A repair result must never be served to a plain call, or the dropped scope returns."""
    # own slug: the cache is keyed on slugs and is shared across tests in this session
    plain = {"topics": [{**_TOPIC, "slug": "cache-key-probe"}]}
    first = await client.post("/ai/deep-dive", json=plain, headers=_headers())
    assert first.json()["meta"]["cached"] is False

    cached = await client.post("/ai/deep-dive", json=plain, headers=_headers())
    assert cached.json()["meta"]["cached"] is True

    repair = await client.post(
        "/ai/deep-dive",
        json={**plain, "must_cover": ["locks"]},
        headers=_headers(),
    )
    assert repair.json()["meta"]["cached"] is False
