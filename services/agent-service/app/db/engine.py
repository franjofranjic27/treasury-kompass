"""Async database engine (SQLAlchemy 2.1, psycopg 3) and idempotent schema setup."""

from __future__ import annotations

import logging
from importlib.resources import files

from psycopg import AsyncConnection
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine

logger = logging.getLogger(__name__)

# Serialises concurrent starts (e.g. scaled replicas) while the DDL runs.
_SCHEMA_LOCK_KEY = 74_201_118


def create_engine(database_url: str) -> AsyncEngine:
    return create_async_engine(
        database_url,
        pool_pre_ping=True,
        pool_size=5,
        max_overflow=5,
        connect_args={"options": "-c search_path=rag,public"},
    )


def _schema_script() -> str:
    return files("app.db").joinpath("schema.sql").read_text(encoding="utf-8")


async def apply_schema(engine: AsyncEngine) -> None:
    """Run `schema.sql` in one transaction. The script is multi-statement, so it goes through the driver directly."""
    script = _schema_script()
    async with engine.begin() as connection:
        await connection.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": _SCHEMA_LOCK_KEY})
        raw = await connection.get_raw_connection()
        driver_connection = raw.driver_connection
        if not isinstance(driver_connection, AsyncConnection):
            msg = f"Erwartet psycopg AsyncConnection, erhalten {type(driver_connection).__name__}"
            raise TypeError(msg)
        await driver_connection.execute(script.encode("utf-8"))
    logger.info("Schema rag angewendet")


async def database_is_reachable(engine: AsyncEngine) -> bool:
    try:
        async with engine.connect() as connection:
            await connection.execute(text("SELECT 1"))
    except (SQLAlchemyError, OSError):
        logger.warning("Datenbank nicht erreichbar", exc_info=True)
        return False
    return True
