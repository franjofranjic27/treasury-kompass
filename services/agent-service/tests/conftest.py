"""Shared fixtures. Integration tests start one pgvector container per session (Docker required)."""

from __future__ import annotations

from collections.abc import AsyncIterator, Iterator

import pytest

from tests.support.harness import Harness, open_harness

PGVECTOR_IMAGE = "pgvector/pgvector:0.8.7-pg17-trixie"

# Settings read these; a developer's shell must not leak real keys or overrides into the tests.
_SETTINGS_ENV = (
    "ANTHROPIC_API_KEY",
    "ANTHROPIC_MODEL",
    "ANTHROPIC_EFFORT",
    "ANTHROPIC_EXTRACTION_EFFORT",
    "ANTHROPIC_FALLBACKS",
    "VOYAGE_API_KEY",
    "VOYAGE_MODEL",
    "AGENT_DATABASE_URL",
    "AGENT_TREASURY_BASE_URL",
    "AGENT_INTERNAL_API_KEY",
    "INTERNAL_API_KEY",
    "AGENT_EXTRACTION_FULL_DOC_MAX_PAGES",
    "AGENT_RAG_TOP_K",
    "AGENT_CHAT_MAX_MODEL_CALLS",
    "AGENT_CHAT_MAX_PROPOSALS",
    "AGENT_CHAT_TURN_TIMEOUT_SECONDS",
    "LOG_LEVEL",
)


@pytest.fixture(autouse=True)
def _isolated_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in _SETTINGS_ENV:
        monkeypatch.delenv(name, raising=False)


@pytest.fixture(scope="session")
def database_url() -> Iterator[str]:
    from testcontainers.community.postgres import PostgresContainer

    container = PostgresContainer(PGVECTOR_IMAGE, driver="psycopg")
    try:
        container.start()
    except Exception as error:  # Docker missing or not running
        pytest.skip(f"Docker/testcontainers nicht verfügbar: {error}")
    try:
        yield container.get_connection_url()
    finally:
        container.stop()


@pytest.fixture
async def harness(database_url: str) -> AsyncIterator[Harness]:
    async with open_harness(database_url) as opened:
        yield opened


def pytest_collection_modifyitems(items: list[pytest.Item]) -> None:
    for item in items:
        if "integration" in item.path.parts:
            item.add_marker(pytest.mark.integration)
