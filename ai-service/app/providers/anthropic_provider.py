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
            kwargs["thinking"] = {"type": "adaptive"}

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
