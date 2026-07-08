"""
Standalone MCP server entry point for Claude Desktop.

Claude Desktop spawns this as a subprocess and communicates via stdio.
Vault + DB are initialised on startup; then FastMCP takes over the stdio loop.

Usage (Claude Desktop config):
  {
    "mcpServers": {
      "prep-loop": {
        "command": "python",
        "args": ["/absolute/path/to/ai-service/mcp_server.py"],
        "env": {
          "VAULT_ADDR": "http://localhost:8200",
          "VAULT_TOKEN": "root",
          "CORE_URL":   "http://localhost:8080"
        }
      }
    }
  }

Requires docker-compose services to be running: postgres, vault, vault-init, core.
The ai FastAPI container does NOT need to be running for this path.
"""

import asyncio
import logging

logging.basicConfig(level=logging.WARNING)
log = logging.getLogger(__name__)


async def _init() -> None:
    from app.vault import load_secrets, secret
    from app.db import init_db

    log.info("Loading Vault secrets...")
    load_secrets()

    log.info("Opening DB pool...")
    await init_db(db_password=secret("db.password"))

    log.info("prep-loop MCP server ready")


asyncio.run(_init())

from app.mcp.server import mcp  # noqa: E402  (import after async init)

mcp.run()  # blocks; communicates via stdio with Claude Desktop
