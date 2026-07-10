import asyncio
import logging
from functools import partial

import voyageai
from anthropic import AsyncAnthropic

from app.providers.base import (
    CompletionResult,
    EmbedResult,
    LLMProvider,
    ToolCallResult,
    ToolUse,
)

log = logging.getLogger(__name__)

# Adaptive thinking is only accepted on these model families — sending it to
# anything else (Haiku 4.5, Sonnet/Opus 4.5 and older) is a 400.
_ADAPTIVE_THINKING_MODELS = (
    "claude-opus-4-6", "claude-opus-4-7", "claude-opus-4-8",
    "claude-sonnet-4-6", "claude-sonnet-5",
    "claude-fable-5", "claude-mythos-5",
)


def _supports_adaptive_thinking(model: str) -> bool:
    return model.startswith(_ADAPTIVE_THINKING_MODELS)


class AnthropicProvider(LLMProvider):
    def __init__(self, api_key: str, voyage_key: str, model: str = "claude-opus-4-8") -> None:
        self._model = model
        self._client = AsyncAnthropic(api_key=api_key)
        self._voyage = voyageai.Client(api_key=voyage_key)

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
        kwargs: dict = dict(model=self._model, max_tokens=max_tokens, messages=messages)
        if system:
            kwargs["system"] = system
        if use_thinking:
            if _supports_adaptive_thinking(self._model):
                kwargs["thinking"] = {"type": "adaptive"}
            else:
                log.info("thinking requested but %s does not support adaptive — running without", self._model)

        async with self._client.messages.stream(**kwargs) as stream:
            msg = await stream.get_final_message()

        text = next((b.text for b in msg.content if b.type == "text"), "")
        return CompletionResult(
            content=text,
            input_tokens=msg.usage.input_tokens,
            output_tokens=msg.usage.output_tokens,
            model=msg.model,
            stop_reason=msg.stop_reason or "end_turn",
        )

    async def tool_call(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str | None = None,
        max_tokens: int = 4096,
    ) -> ToolCallResult:
        kwargs: dict = dict(
            model=self._model,
            max_tokens=max_tokens,
            messages=messages,
            tools=tools,
            thinking={"type": "adaptive"},
        )
        if system:
            kwargs["system"] = system

        msg = await self._client.messages.create(**kwargs)

        raw_content: list[dict] = []
        tool_uses: list[ToolUse] = []
        text = ""

        for block in msg.content:
            if block.type == "text":
                text = block.text
                raw_content.append({"type": "text", "text": block.text})
            elif block.type == "tool_use":
                tool_uses.append(ToolUse(id=block.id, name=block.name, input=block.input))
                raw_content.append({"type": "tool_use", "id": block.id, "name": block.name, "input": block.input})
            elif block.type == "thinking":
                raw_content.append({"type": "thinking", "thinking": block.thinking})

        return ToolCallResult(
            content=text,
            tool_calls=tool_uses,
            raw_content=raw_content,
            input_tokens=msg.usage.input_tokens,
            output_tokens=msg.usage.output_tokens,
            model=msg.model,
            stop_reason=msg.stop_reason or "end_turn",
        )

    async def embed(self, texts: list[str]) -> EmbedResult:
        fn = partial(self._voyage.embed, texts, model="voyage-2", input_type="document")
        result = await asyncio.to_thread(fn)
        return EmbedResult(embeddings=result.embeddings, model="voyage-2", total_tokens=result.total_tokens)
