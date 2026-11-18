"""FastAPI application of the agent-service. Start: `uvicorn app.main:app --host 0.0.0.0 --port 8000`."""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from functools import cache

from fastapi import FastAPI

from app.api import chat, conversations, health, rationale
from app.api.health import service_version
from app.config import Settings
from app.ingest import router as ingest
from app.problems import register_problem_handlers
from app.services import ProvidersFactory, ServiceOptions, default_providers, open_services

LOG_FORMAT = "%(asctime)s %(levelname)s %(name)s %(message)s"


def configure_logging(level: str) -> None:
    # No force=True: keeps handlers an embedding runtime (uvicorn, pytest) has already installed.
    logging.basicConfig(level=level, format=LOG_FORMAT)
    logging.getLogger("app").setLevel(level)
    # HTTP client logs contain URLs only, but stay quiet unless debugging.
    for noisy in ("httpx", "httpcore", "anthropic"):
        logging.getLogger(noisy).setLevel(max(logging.WARNING, logging.getLevelName(level)))


def create_app(
    settings: Settings | None = None,
    *,
    providers_factory: ProvidersFactory = default_providers,
    options: ServiceOptions | None = None,
) -> FastAPI:
    resolved = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        configure_logging(resolved.log_level)
        _warn_about_missing_keys(resolved)
        async with open_services(resolved, providers_factory(resolved), options) as services:
            app.state.settings = resolved
            app.state.services = services
            yield

    app = FastAPI(
        title="Treasury Kompass – agent-service",
        version=service_version(),
        summary="Chat-Agent, Ingest mit Extraktion und Fundstellen, Begründungsentwurf.",
        lifespan=lifespan,
    )
    register_problem_handlers(app)
    app.include_router(chat.router)
    app.include_router(conversations.router)
    app.include_router(rationale.router)
    app.include_router(ingest.router)
    app.include_router(health.router)
    return app


def _warn_about_missing_keys(settings: Settings) -> None:
    # The service starts without keys (e.g. Compose with empty values); only the AI features fail then.
    logger = logging.getLogger("app.main")
    if not settings.anthropic_api_key.get_secret_value().strip():
        logger.warning("ANTHROPIC_API_KEY ist leer: Chat, Extraktion und Begründungsentwurf sind nicht verfügbar")
    if not settings.voyage_api_key.get_secret_value().strip():
        logger.warning("VOYAGE_API_KEY ist leer: Indexierung und Dokumentsuche sind nicht verfügbar")


@cache
def _default_app() -> FastAPI:
    return create_app()


def __getattr__(name: str) -> FastAPI:
    # `app` is created on first access so importing this module (tests, tooling) needs no environment.
    if name == "app":
        return _default_app()
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
