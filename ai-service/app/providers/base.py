from abc import ABC, abstractmethod
from dataclasses import dataclass, field


@dataclass
class CompletionResult:
    content: str
    input_tokens: int
    output_tokens: int
    model: str
    stop_reason: str


@dataclass
class ToolUse:
    id: str
    name: str
    input: dict


@dataclass
class ToolCallResult:
    content: str
    tool_calls: list[ToolUse]
    raw_content: list[dict]
    input_tokens: int
    output_tokens: int
    model: str
    stop_reason: str


@dataclass
class EmbedResult:
    embeddings: list[list[float]]
    model: str
    total_tokens: int = 0


class LLMProvider(ABC):
    @abstractmethod
    async def complete(
        self,
        messages: list[dict],
        system: str | None = None,
        max_tokens: int = 4096,
        use_thinking: bool = False,
    ) -> CompletionResult: ...

    @abstractmethod
    async def tool_call(
        self,
        messages: list[dict],
        tools: list[dict],
        system: str | None = None,
        max_tokens: int = 4096,
    ) -> ToolCallResult: ...

    @abstractmethod
    async def embed(self, texts: list[str]) -> EmbedResult: ...

    @property
    @abstractmethod
    def model_id(self) -> str: ...
