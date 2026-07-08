import logging

import psycopg_pool
from pgvector.psycopg import register_vector_async

from app.config import settings

log = logging.getLogger(__name__)

_pool: psycopg_pool.AsyncConnectionPool | None = None


async def _configure_conn(conn) -> None:
    await register_vector_async(conn)


async def init_db(db_password: str) -> None:
    global _pool
    conninfo = (
        f"host={settings.db_host} port={settings.db_port} "
        f"dbname={settings.db_name} user={settings.db_user} "
        f"password={db_password}"
    )
    _pool = psycopg_pool.AsyncConnectionPool(
        conninfo,
        min_size=2,
        max_size=10,
        open=False,
        configure=_configure_conn,
    )
    await _pool.open()
    log.info("DB pool opened: %s:%d/%s", settings.db_host, settings.db_port, settings.db_name)


async def close_db() -> None:
    if _pool:
        await _pool.close()


def get_pool() -> psycopg_pool.AsyncConnectionPool:
    if _pool is None:
        raise RuntimeError("DB pool not initialised — call init_db() first")
    return _pool
