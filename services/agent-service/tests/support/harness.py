"""The real app (lifespan, routers, services) on pgvector, with fakes for Anthropic, Voyage and treasury-service."""

from __future__ import annotations

from collections.abc import AsyncIterator, Sequence
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from uuid import UUID

import httpx
import respx
from fastapi import FastAPI

from app.clients.treasury import RetryPolicy
from app.config import Settings
from app.ingest.chunking import chunk_pages
from app.main import create_app
from app.rag.store import EmbeddedChunk, PageText
from app.services import AiProviders, ServiceOptions, Services
from tests.support.fakes import HashingEmbedder, ScriptedChatModel, ScriptedStructuredModel

INTERNAL_KEY = "test-internal-key-0123456789abcdef-0123"
TREASURY_URL = "http://treasury.test"
SERVICE_HEADERS = {"X-Service-Key": INTERNAL_KEY}


def make_settings(**overrides: object) -> Settings:
    values: dict[str, object] = {
        "ANTHROPIC_API_KEY": "test-anthropic-key",
        "VOYAGE_API_KEY": "test-voyage-key",
        "AGENT_INTERNAL_API_KEY": INTERNAL_KEY,
        "AGENT_DATABASE_URL": "postgresql+psycopg://unused@localhost:1/unused",
        "AGENT_TREASURY_BASE_URL": TREASURY_URL,
        "LOG_LEVEL": "INFO",
    }
    values.update(overrides)
    return Settings(_env_file=None, **values)  # type: ignore[call-arg]


@dataclass
class Harness:
    app: FastAPI
    client: httpx.AsyncClient
    services: Services
    treasury: respx.Router
    embedder: HashingEmbedder
    chat_model: ScriptedChatModel
    extraction_model: ScriptedStructuredModel
    rationale_model: ScriptedChatModel
    indexed: list[UUID] = field(default_factory=list)

    async def index_document(self, *, client_id: UUID, document_id: UUID, name: str, pages: dict[int, str]) -> int:
        """Store pages and embedded chunks directly, as a finished ingest would."""
        page_texts = [PageText(page=number, text=text) for number, text in sorted(pages.items())]
        chunks = chunk_pages(page_texts)
        vectors = await self.embedder.embed_documents([chunk.content for chunk in chunks])
        await self.services.document_store.replace_pages(client_id=client_id, document_id=document_id, pages=page_texts)
        return await self.services.document_store.replace_chunks(
            client_id=client_id,
            document_id=document_id,
            document_name=name,
            embedding_model=self.embedder.model_name,
            chunks=[
                EmbeddedChunk(page=c.page, chunk_index=c.chunk_index, content=c.content, embedding=v)
                for c, v in zip(chunks, vectors, strict=True)
            ],
        )


@asynccontextmanager
async def open_harness(database_url: str, **settings_overrides: object) -> AsyncIterator[Harness]:
    embedder = HashingEmbedder()
    chat_model = ScriptedChatModel()
    extraction_model = ScriptedStructuredModel()
    rationale_model = ScriptedChatModel()
    treasury = respx.Router(base_url=TREASURY_URL, assert_all_called=False)
    settings = make_settings(AGENT_DATABASE_URL=database_url, **settings_overrides)
    app = create_app(
        settings,
        providers_factory=lambda _: AiProviders(
            embedder=embedder,
            chat_model=chat_model,
            extraction_model=extraction_model,
            rationale_model=rationale_model,
        ),
        options=ServiceOptions(
            retry_policy=RetryPolicy(delays=(0.0, 0.0, 0.0)),
            http_transport=httpx.MockTransport(treasury.async_handler),
        ),
    )
    async with (
        app.router.lifespan_context(app),
        httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://agent.test") as client,
    ):
        yield Harness(
            app=app,
            client=client,
            services=app.state.services,
            treasury=treasury,
            embedder=embedder,
            chat_model=chat_model,
            extraction_model=extraction_model,
            rationale_model=rationale_model,
        )


def requests_to(router: respx.Router, method: str, path_suffix: str) -> Sequence[httpx.Request]:
    return [
        call.request
        for call in router.calls
        if call.request.method == method and call.request.url.path.endswith(path_suffix)
    ]
