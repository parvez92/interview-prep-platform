from fastapi import APIRouter, Request

from app.config import settings
from app.desktop_mode import _MCP_TOOLS

router = APIRouter(prefix="/ai", tags=["settings"])


@router.get("/settings")
async def get_ai_settings(request: Request):
    """
    Returns available AI modes and connection details.
    The frontend settings page uses this to display mode options and MCP URLs.
    No auth required — purely informational.
    """
    base = str(request.base_url).rstrip("/")
    return {
        "current_defaults": {
            "provider": settings.default_provider,
            "model": settings.default_model,
        },
        "modes": {
            "api": {
                "provider_values": ["anthropic", "openai", "gemini", "bedrock"],
                "description": "Direct LLM API calls — fully integrated in-app, costs per token.",
                "requires_key": True,
            },
            "desktop": {
                "provider_value": "desktop",
                "description": (
                    "Claude Desktop handles AI via MCP — no per-token cost from this service, "
                    "uses your Claude Desktop subscription."
                ),
                "requires_key": False,
                "mcp": {
                    "sse_url": f"{base}/mcp/sse",
                    "stdio_entry": "python /path/to/ai-service/mcp_server.py",
                    "tools": _MCP_TOOLS,
                    "claude_desktop_config": {
                        "mcpServers": {
                            "prep-loop": {
                                "command": "python",
                                "args": ["<absolute-path>/ai-service/mcp_server.py"],
                                "env": {
                                    "VAULT_ADDR": "http://localhost:8200",
                                    "VAULT_TOKEN": "root",
                                    "CORE_URL": "http://localhost:8080",
                                },
                            }
                        }
                    },
                },
            },
        },
    }
