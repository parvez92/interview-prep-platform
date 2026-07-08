from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    vault_addr: str = "http://vault:8200"
    vault_token: str = "root"
    vault_secret_path: str = "ai-service"

    db_host: str = "postgres"
    db_port: int = 5432
    db_name: str = "preploop"
    db_user: str = "prep"

    core_url: str = "http://core:8080"

    default_provider: str = "anthropic"
    default_model: str = "claude-opus-4-8"
    default_embed_model: str = "voyage-2"

    agent_max_steps: int = 4
    agent_deadline_seconds: float = 30.0
    agent_max_tokens: int = 40_000

    budget_warn_usd: float = 5.0
    budget_hard_usd: float = 20.0
    cheap_model: str = "claude-haiku-4-5-20251001"

    # For standalone MCP server (Claude Desktop): single-user default
    default_user_id: int = 1


settings = Settings()
