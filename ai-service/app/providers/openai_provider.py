import logging

from openai import AsyncOpenAI

from app.providers.base import (
    CompletionResult,
    EmbedResult,
    LLMProvider,
    ToolCallResult,
    ToolUse,
)

log = logging.getLogger(__name__)


def _to_openai_tools(tools: list[dict]) -> list[dict]:
    return [
        {
            "type": "function",
            "function": {
                "name": t["name"],
                "description": t.get("description", ""),
                "parameters": t.get("input_schema", {}),
            },
        }
        for t in tools
    ]


class OpenAIProvider(LLMProvider):
    def __init__(self, api_key: str, model: str = "gpt-4o") -> None:
        self._model = model
        self._client = AsyncOpenAI(api_key=api_key)

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

        resp = await self._client.chat.completions.create(
            model=self._model,
            messages=msgs,
            max_tokens=max_tokens,
        )
        choice = resp.choices[0]
        return CompletionResult(
            content=choice.message.content or "",
            input_tokens=resp.usage.prompt_tokens,
            output_tokens=resp.usage.completion_tokens,
            model=resp.model,
            stop_reason=choice.finish_reason or "stop",
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

        resp = await self._client.chat.completions.create(
            model=self._model,
            messages=msgs,
            tools=_to_openai_tools(tools),
            max_tokens=max_tokens,
        )
        choice = resp.choices[0]
        msg = choice.message

        tool_uses: list[ToolUse] = []
        raw_content: list[dict] = []

        if msg.content:
            raw_content.append({"type": "text", "text": msg.content})

        if msg.tool_calls:
            import json
            for tc in msg.tool_calls:
                try:
                    inp = json.loads(tc.function.arguments)
                except Exception:
                    inp = {}
                tool_uses.append(ToolUse(id=tc.id, name=tc.function.name, input=inp))
                raw_content.append({"type": "tool_use", "id": tc.id, "name": tc.function.name, "input": inp})

        return ToolCallResult(
            content=msg.content or "",
            tool_calls=tool_uses,
            raw_content=raw_content,
            input_tokens=resp.usage.prompt_tokens,
            output_tokens=resp.usage.completion_tokens,
            model=resp.model,
            stop_reason=choice.finish_reason or "stop",
        )

    async def embed(self, texts: list[str]) -> EmbedResult:
        resp = await self._client.embeddings.create(
            model="text-embedding-3-small",
            input=texts,
            dimensions=1024,
        )
        embeddings = [item.embedding for item in sorted(resp.data, key=lambda x: x.index)]
        return EmbedResult(embeddings=embeddings, model="text-embedding-3-small", total_tokens=resp.usage.total_tokens)
