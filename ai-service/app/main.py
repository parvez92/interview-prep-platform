import logging

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.config import settings
from app.db import close_db, init_db
from app.desktop_mode import DesktopModeMiddleware
from app.vault import load_secrets, secret

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


@asynccontextmanager
async def lifespan(app: FastAPI):
    secrets = load_secrets()
    await init_db(db_password=secret("db.password"))
    log.info("ai-service ready — provider=%s model=%s", settings.default_provider, settings.default_model)
    yield
    await close_db()


app = FastAPI(title="prep-loop AI Service", version="0.1.0", lifespan=lifespan)
app.add_middleware(DesktopModeMiddleware)


@app.exception_handler(Exception)
async def global_error(request: Request, exc: Exception):
    exc_type = type(exc).__name__
    exc_msg = str(exc)

    # Auth errors from any provider
    if "AuthenticationError" in exc_type or "authentication" in exc_type.lower() or (
        "401" in exc_msg and ("api" in exc_msg.lower() or "key" in exc_msg.lower() or "auth" in exc_msg.lower())
    ):
        log.error("Provider auth error %s %s: %s", request.method, request.url, exc_msg)
        return JSONResponse(
            status_code=401,
            content={"error": {"code": "PROVIDER_AUTH", "message": "AI provider API key is invalid or not configured. Set your key in Settings → AI Provider."}},
        )

    # Ollama model not found
    if "not found" in exc_msg.lower() and "ollama" in exc_msg.lower():
        log.error("Ollama model not found %s %s: %s", request.method, request.url, exc_msg)
        return JSONResponse(
            status_code=422,
            content={"error": {"code": "MODEL_NOT_FOUND", "message": exc_msg}},
        )

    # LLM returned empty or unparseable output
    if "empty content" in exc_msg.lower() or "parse_failed" in exc_msg.lower():
        log.error("LLM output error %s %s: %s", request.method, request.url, exc_msg)
        return JSONResponse(
            status_code=422,
            content={"error": {"code": "PARSE_FAILED", "message": "The AI model couldn't produce a valid response. Try again — if it keeps failing, the model may be too slow or the resume too long."}},
        )

    log.exception("Unhandled error %s %s", request.method, request.url)
    return JSONResponse(status_code=500, content={"error": {"code": "INTERNAL_ERROR", "message": "Something went wrong on the AI side. Please try again."}})


from app.routers import agents, analyze, depth, guide, mock, narrative, parse, score, seed  # noqa: E402
from app.routers import settings as settings_router  # noqa: E402

app.include_router(parse.router)
app.include_router(depth.router)
app.include_router(narrative.router)
app.include_router(guide.router)
app.include_router(seed.router)
app.include_router(score.router)
app.include_router(analyze.router)
app.include_router(mock.router)
app.include_router(agents.router)
app.include_router(settings_router.router)


from app.mcp.server import mcp  # noqa: E402

# SSE transport: Claude Desktop can also connect via http://localhost:8000/mcp/sse
try:
    _mcp_app = mcp.get_asgi_app()
except AttributeError:
    _mcp_app = mcp.sse_app()  # older FastMCP versions
app.mount("/mcp", _mcp_app)


@app.get("/health")
async def health():
    return {"status": "ok"}
