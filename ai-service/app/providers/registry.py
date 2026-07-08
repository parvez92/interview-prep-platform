import logging

from app.config import settings
from app.providers.base import LLMProvider
from app.vault import secret

log = logging.getLogger(__name__)

_cache: dict[str, LLMProvider] = {}


def _key(provider: str, model: str) -> str:
    return f"{provider}:{model}"


def wants_thinking(provider_name: str | None) -> bool:
    """
    Extended thinking for the quality-critical passes (plan, depth).
    API models benefit; local models slow to a crawl and drift off strict JSON.
    """
    return (provider_name or settings.default_provider).lower() not in ("ollama", "desktop")


def get_provider(
    provider_name: str | None = None,
    model_name: str | None = None,
    ollama_url: str | None = None,
) -> LLMProvider:
    provider = (provider_name or settings.default_provider).lower()
    # Ollama uses its own model default — never let a cloud model name bleed in
    model = model_name if provider == "ollama" else (model_name or settings.default_model)
    # Ollama URL varies per-user so we can't cache by provider:model alone
    k = _key(provider, str(model)) if provider != "ollama" else _key(provider, f"{model}:{ollama_url}")

    if k in _cache:
        return _cache[k]

    instance = _build_provider(provider, model, ollama_url)
    _cache[k] = instance
    return instance


def _build_provider(provider: str, model: str, ollama_url: str | None = None) -> LLMProvider:
    if provider == "desktop":
        raise RuntimeError(
            "Provider 'desktop' should be intercepted by DesktopModeMiddleware before reaching "
            "the provider registry. Ensure the middleware is registered in app/main.py."
        )

    if provider == "anthropic":
        from app.providers.anthropic_provider import AnthropicProvider
        return AnthropicProvider(
            api_key=secret("anthropic.api_key"),
            voyage_key=secret("voyage.api_key"),
            model=model,
        )

    if provider == "openai":
        from app.providers.openai_provider import OpenAIProvider
        return OpenAIProvider(api_key=secret("openai.api_key"), model=model)

    if provider == "bedrock":
        from app.providers.bedrock_provider import BedrockProvider
        return BedrockProvider(
            access_key=secret("aws.access_key_id"),
            secret_key=secret("aws.secret_access_key"),
            region=secret("aws.region"),
            model=model,
        )

    if provider == "gemini":
        from app.providers.gemini_provider import GeminiProvider
        return GeminiProvider(api_key=secret("gemini.api_key"), model=model)

    if provider == "ollama":
        from app.providers.ollama_provider import OllamaProvider
        # Translate localhost → host.docker.internal: inside Docker "localhost" is the container itself
        _base = (ollama_url or "http://host.docker.internal:11434").replace(
            "://localhost:", "://host.docker.internal:"
        )
        return OllamaProvider(base_url=_base, model=model)

    raise ValueError(f"Unknown provider: {provider!r}. Choose anthropic|openai|bedrock|gemini|ollama.")
