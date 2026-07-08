import asyncio
import json
import logging
from functools import partial

from app.providers.base import (
    CompletionResult,
    EmbedResult,
    LLMProvider,
    ToolCallResult,
    ToolUse,
)

log = logging.getLogger(__name__)

_BEDROCK_MODEL_MAP = {
    "claude-opus-4-8": "anthropic.claude-opus-4-8",
    "claude-opus-4-6": "anthropic.claude-opus-4-6",
    "claude-sonnet-4-6": "anthropic.claude-sonnet-4-6",
    "claude-haiku-4-5-20251001": "anthropic.claude-haiku-4-5-20251001",
}


class BedrockProvider(LLMProvider):
    def __init__(self, access_key: str, secret_key: str, region: str, model: str = "claude-opus-4-8") -> None:
        import boto3
        self._model = model
        self._bedrock_model = _BEDROCK_MODEL_MAP.get(model, model)
        self._client = boto3.client(
            "bedrock-runtime",
            region_name=region,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
        )

    @property
    def model_id(self) -> str:
        return self._model

    def _invoke_sync(self, body: dict) -> dict:
        resp = self._client.invoke_model(
            modelId=self._bedrock_model,
            body=json.dumps(body),
            contentType="application/json",
            accept="application/json",
        )
        return json.loads(resp["body"].read())

    async def complete(
        self,
        messages: list[dict],
        system: str | None = None,
        max_tokens: int = 4096,
        use_thinking: bool = False,
    ) -> CompletionResult:
        body: dict = {"anthropic_version": "bedrock-2023-05-31", "max_tokens": max_tokens, "messages": messages}
        if system:
            body["system"] = system

        result = await asyncio.to_thread(partial(self._invoke_sync, body))
        text = next((b["text"] for b in result.get("content", []) if b["type"] == "text"), "")
        usage = result.get("usage", {})
        return CompletionResult(
            content=text,
            input_tokens=usage.get("input_tokens", 0),
            output_tokens=usage.get("output_tokens", 0),
            model=self._bedrock_model,
            stop_reason=result.get("stop_reason", "end_turn"),
        )

    async def tool_call(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str | None = None,
        max_tokens: int = 4096,
    ) -> ToolCallResult:
        body: dict = {
            "anthropic_version": "bedrock-2023-05-31",
            "max_tokens": max_tokens,
            "messages": messages,
            "tools": tools,
        }
        if system:
            body["system"] = system

        result = await asyncio.to_thread(partial(self._invoke_sync, body))

        raw_content: list[dict] = []
        tool_uses: list[ToolUse] = []
        text = ""

        for block in result.get("content", []):
            if block["type"] == "text":
                text = block["text"]
                raw_content.append(block)
            elif block["type"] == "tool_use":
                tool_uses.append(ToolUse(id=block["id"], name=block["name"], input=block["input"]))
                raw_content.append(block)

        usage = result.get("usage", {})
        return ToolCallResult(
            content=text,
            tool_calls=tool_uses,
            raw_content=raw_content,
            input_tokens=usage.get("input_tokens", 0),
            output_tokens=usage.get("output_tokens", 0),
            model=self._bedrock_model,
            stop_reason=result.get("stop_reason", "end_turn"),
        )

    async def embed(self, texts: list[str]) -> EmbedResult:
        raise NotImplementedError("BedrockProvider does not support embeddings — configure a separate embed provider")
