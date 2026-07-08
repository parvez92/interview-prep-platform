import json
import logging

import httpx

from app.providers.base import (
    CompletionResult,
    EmbedResult,
    LLMProvider,
    ToolCallResult,
    ToolUse,
)

log = logging.getLogger(__name__)

_DEFAULT_BASE = "http://host.docker.internal:11434"
_DEFAULT_MODEL = "llama3.2"
_EMBED_MODEL = "nomic-embed-text"


class OllamaProvider(LLMProvider):
    """Calls a local Ollama server via its OpenAI-compatible /v1 endpoint."""

    def __init__(self, base_url: str = _DEFAULT_BASE, model: str = _DEFAULT_MODEL) -> None:
        self._base = base_url.rstrip("/")
        self._model = model or _DEFAULT_MODEL

    @property
    def model_id(self) -> str:
        return self._model

    async def complete(
        self,
        messages: list[dict],
        system: str | None = None,
        max_tokens: int = 4096,
        use_thinking: bool = False,
    ) -> CompletionResult:
        msgs = []
        if system:
            msgs.append({"role": "system", "content": system})
        msgs.extend(messages)

        log.info("Ollama complete → model=%s base=%s thinking=%s", self._model, self._base, use_thinking)
        async with httpx.AsyncClient(timeout=600) as client:
            # Use the native /api/chat endpoint — it honours think=false (v0.30+).
            # The OpenAI-compat /v1/chat/completions ignores the think param.
            resp = await client.post(
                f"{self._base}/api/chat",
                json={
                    "model": self._model,
                    "messages": msgs,
                    "stream": False,
                    "think": use_thinking,
                    "options": {
                        # -1 = unlimited (generate until EOS). Use it when caller passes a
                        # sentinel value or a very large limit — avoids mid-JSON truncation.
                        "num_predict": -1 if max_tokens <= 0 or max_tokens >= 16384 else max_tokens,
                        # Ollama's default context (~4k) silently truncates generation once
                        # prompt + output fill it — num_predict=-1 does not protect against
                        # that. Long JSON plans need the headroom.
                        "num_ctx": 16384,
                    },
                },
            )
            if resp.status_code == 404:
                raise RuntimeError(
                    f"Ollama model '{self._model}' not found at {self._base}. "
                    f"Run: ollama pull {self._model}"
                )
            resp.raise_for_status()
            data = resp.json()

        msg = data.get("message", {})
        content = msg.get("content") or msg.get("thinking") or ""
        return CompletionResult(
            content=content,
            input_tokens=data.get("prompt_eval_count", 0),
            output_tokens=data.get("eval_count", 0),
            model=data.get("model", self._model),
            # "length" = truncated — callers use this to explain parse failures
            stop_reason=data.get("done_reason", "stop"),
        )

    async def tool_call(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str | None = None,
        max_tokens: int = 4096,
    ) -> ToolCallResult:
        msgs = []
        if system:
            msgs.append({"role": "system", "content": system})
        msgs.extend(messages)

        openai_tools = [
            {"type": "function", "function": {"name": t["name"], "description": t.get("description", ""), "parameters": t.get("input_schema", {})}}
            for t in tools
        ]

        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post(
                f"{self._base}/v1/chat/completions",
                json={"model": self._model, "messages": msgs, "tools": openai_tools, "max_tokens": max_tokens, "stream": False},
            )
            resp.raise_for_status()
            data = resp.json()

        choice = data["choices"][0]
        msg = choice["message"]
        tool_uses, raw_content = [], []

        if msg.get("content"):
            raw_content.append({"type": "text", "text": msg["content"]})

        for tc in msg.get("tool_calls") or []:
            try:
                inp = json.loads(tc["function"]["arguments"])
            except Exception:
                inp = {}
            name = tc["function"]["name"]
            tool_uses.append(ToolUse(id=tc.get("id", name), name=name, input=inp))
            raw_content.append({"type": "tool_use", "id": tc.get("id", name), "name": name, "input": inp})

        usage = data.get("usage", {})
        return ToolCallResult(
            content=msg.get("content") or "",
            tool_calls=tool_uses,
            raw_content=raw_content,
            input_tokens=usage.get("prompt_tokens", 0),
            output_tokens=usage.get("completion_tokens", 0),
            model=data.get("model", self._model),
            stop_reason="tool_use" if tool_uses else choice.get("finish_reason", "stop"),
        )

    async def embed(self, texts: list[str]) -> EmbedResult:
        embeddings = []
        async with httpx.AsyncClient(timeout=60) as client:
            for text in texts:
                resp = await client.post(
                    f"{self._base}/v1/embeddings",
                    json={"model": _EMBED_MODEL, "input": text},
                )
                resp.raise_for_status()
                embeddings.append(resp.json()["data"][0]["embedding"])
        return EmbedResult(embeddings=embeddings, model=_EMBED_MODEL)
