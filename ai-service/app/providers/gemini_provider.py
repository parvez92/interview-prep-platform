import logging

from google import genai
from google.genai import types

from app.providers.base import (
    CompletionResult,
    EmbedResult,
    LLMProvider,
    ToolCallResult,
    ToolUse,
)

log = logging.getLogger(__name__)

_DEFAULT_MODEL = "gemini-2.0-flash"
_EMBED_MODEL = "text-embedding-004"


class GeminiProvider(LLMProvider):
    def __init__(self, api_key: str, model: str = _DEFAULT_MODEL) -> None:
        self._model = model or _DEFAULT_MODEL
        self._client = genai.Client(api_key=api_key)

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
        contents = [
            types.Content(
                role=m["role"] if m["role"] != "assistant" else "model",
                parts=[types.Part.from_text(text=m["content"])],
            )
            for m in messages
        ]
        config = types.GenerateContentConfig(
            system_instruction=system,
            max_output_tokens=max_tokens,
        )
        resp = await self._client.aio.models.generate_content(
            model=self._model,
            contents=contents,
            config=config,
        )
        text = resp.text or ""
        usage = resp.usage_metadata
        return CompletionResult(
            content=text,
            input_tokens=usage.prompt_token_count if usage else 0,
            output_tokens=usage.candidates_token_count if usage else 0,
            model=self._model,
            stop_reason=str(resp.candidates[0].finish_reason) if resp.candidates else "stop",
        )

    async def tool_call(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str | None = None,
        max_tokens: int = 4096,
    ) -> ToolCallResult:
        gemini_tools = [
            types.Tool(
                function_declarations=[
                    types.FunctionDeclaration(
                        name=t["name"],
                        description=t.get("description", ""),
                        parameters=t.get("input_schema", {}),
                    )
                ]
            )
            for t in tools
        ]
        contents = [
            types.Content(
                role=m["role"] if m["role"] != "assistant" else "model",
                parts=[types.Part.from_text(text=m["content"])],
            )
            for m in messages
        ]
        config = types.GenerateContentConfig(
            system_instruction=system,
            tools=gemini_tools,
            max_output_tokens=max_tokens,
        )
        resp = await self._client.aio.models.generate_content(
            model=self._model,
            contents=contents,
            config=config,
        )
        tool_uses: list[ToolUse] = []
        raw_content: list[dict] = []
        text = ""

        if resp.candidates:
            for part in resp.candidates[0].content.parts:
                if part.text:
                    text = part.text
                    raw_content.append({"type": "text", "text": part.text})
                if part.function_call:
                    fc = part.function_call
                    inp = dict(fc.args) if fc.args else {}
                    tool_uses.append(ToolUse(id=fc.name, name=fc.name, input=inp))
                    raw_content.append({"type": "tool_use", "id": fc.name, "name": fc.name, "input": inp})

        usage = resp.usage_metadata
        return ToolCallResult(
            content=text,
            tool_calls=tool_uses,
            raw_content=raw_content,
            input_tokens=usage.prompt_token_count if usage else 0,
            output_tokens=usage.candidates_token_count if usage else 0,
            model=self._model,
            stop_reason="tool_use" if tool_uses else "stop",
        )

    async def embed(self, texts: list[str]) -> EmbedResult:
        embeddings = []
        total_tokens = 0
        for text in texts:
            resp = await self._client.aio.models.embed_content(
                model=_EMBED_MODEL,
                contents=text,
            )
            embeddings.append(resp.embeddings[0].values)
        return EmbedResult(
            embeddings=embeddings,
            model=_EMBED_MODEL,
            total_tokens=total_tokens,
        )
