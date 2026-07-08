from fastapi import Depends, Header, HTTPException
from typing import Annotated

import psycopg_pool

from app.config import settings
from app.db import get_pool
from app.vault import secret


async def verify_service_token(x_service_token: Annotated[str, Header()]) -> None:
    """Java sends X-Service-Token on every request (see AiClientConfig.java)."""
    if x_service_token != secret("service.token"):
        raise HTTPException(status_code=401, detail="Invalid service token")


async def get_user_id(x_user_id: Annotated[str | None, Header()] = None) -> int:
    """
    Java sends X-User-Id with every request (after AiClient fix).
    Falls back to settings.default_user_id for standalone MCP mode.
    """
    if x_user_id is None:
        return settings.default_user_id
    try:
        return int(x_user_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="X-User-Id must be an integer")


async def get_provider_name(x_provider: Annotated[str | None, Header()] = None) -> str | None:
    return x_provider


async def get_model_name(x_model: Annotated[str | None, Header()] = None) -> str | None:
    return x_model


async def get_ollama_url(x_ollama_url: Annotated[str | None, Header()] = None) -> str | None:
    return x_ollama_url


async def db_pool() -> psycopg_pool.AsyncConnectionPool:
    return get_pool()


ServiceAuth = Annotated[None, Depends(verify_service_token)]
UserId = Annotated[int, Depends(get_user_id)]
ProviderName = Annotated[str | None, Depends(get_provider_name)]
ModelName = Annotated[str | None, Depends(get_model_name)]
OllamaUrl = Annotated[str | None, Depends(get_ollama_url)]
DB = Annotated[psycopg_pool.AsyncConnectionPool, Depends(db_pool)]
