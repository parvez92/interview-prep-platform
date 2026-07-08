import logging

import hvac

from app.config import settings

log = logging.getLogger(__name__)

_secrets: dict[str, str] = {}


def load_secrets() -> dict[str, str]:
    global _secrets
    client = hvac.Client(url=settings.vault_addr, token=settings.vault_token)
    try:
        data = client.secrets.kv.v2.read_secret_version(
            path=settings.vault_secret_path,
            mount_point="secret",
        )
        _secrets = data["data"]["data"]
        log.info("Vault secrets loaded for path=%s", settings.vault_secret_path)
    except Exception:
        log.exception("Failed to load secrets from Vault — service cannot start")
        raise
    return _secrets


def get_secrets() -> dict[str, str]:
    return _secrets


def secret(key: str) -> str:
    val = _secrets.get(key)
    if not val:
        raise RuntimeError(f"Secret '{key}' not found in Vault — ensure vault-init seeded it")
    return val
