"""Composition root: builds every collaborator once per process and tears them down again."""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from dataclasses import dataclass

import httpx
from langchain_core.language_models import BaseChatModel
from sqlalchemy.ext.asyncio import AsyncEngine

from app.agent.graph import build_chat_agent
from app.agent.history import ConversationRepository
from app.agent.llm import build_chat_model, chat_purpose, extraction_purpose, rationale_purpose
from app.agent.rationale import RationaleService
from app.agent.tools import build_tools
from app.agent.turn import ChatTurnService, TurnSettings
from app.background import BackgroundTasks
from app.clients.treasury import RetryPolicy, TreasuryClient, create_http_client
from app.config import Settings
from app.db.engine import apply_schema, create_engine
from app.ingest.extraction import KeyFigureExtractor
from app.ingest.jobs import IngestJobRepository
from app.ingest.pipeline import IngestPipeline
from app.ingest.runner import IngestRunner
from app.rag.embeddings import Embedder, VoyageEmbedder
from app.rag.search import DocumentSearch
from app.rag.store import DocumentStore

logger = logging.getLogger(__name__)

SHUTDOWN_TIMEOUT_SECONDS = 10.0


@dataclass(frozen=True)
class AiProviders:
    """External AI dependencies; tests replace them with fakes."""

    embedder: Embedder
    chat_model: BaseChatModel
    extraction_model: BaseChatModel
    rationale_model: BaseChatModel


def default_providers(settings: Settings) -> AiProviders:
    return AiProviders(
        embedder=VoyageEmbedder(settings.voyage_api_key.get_secret_value(), settings.voyage_model),
        chat_model=build_chat_model(settings, chat_purpose(settings)),
        extraction_model=build_chat_model(settings, extraction_purpose(settings)),
        rationale_model=build_chat_model(settings, rationale_purpose(settings)),
    )


ProvidersFactory = Callable[[Settings], AiProviders]


@dataclass(frozen=True)
class ServiceOptions:
    """Seams for tests: HTTP transport towards the treasury-service and retry delays."""

    retry_policy: RetryPolicy | None = None
    http_transport: httpx.AsyncBaseTransport | None = None


@dataclass(frozen=True)
class Services:
    settings: Settings
    engine: AsyncEngine
    treasury: TreasuryClient
    document_store: DocumentStore
    search: DocumentSearch
    ingest_runner: IngestRunner
    chat: ChatTurnService
    conversations: ConversationRepository
    rationale: RationaleService
    tasks: BackgroundTasks


@asynccontextmanager
async def open_services(
    settings: Settings,
    providers: AiProviders,
    options: ServiceOptions | None = None,
) -> AsyncIterator[Services]:
    resolved = options or ServiceOptions()
    engine = create_engine(settings.database_url)
    http = create_http_client(settings.treasury_base_url, transport=resolved.http_transport)
    tasks = BackgroundTasks()
    try:
        await apply_schema(engine)
        services = _wire(settings, providers, engine, http, tasks, resolved.retry_policy)
        await services.ingest_runner.recover_interrupted()
        yield services
    finally:
        await tasks.shutdown(SHUTDOWN_TIMEOUT_SECONDS)
        await http.aclose()
        await engine.dispose()


def _wire(
    settings: Settings,
    providers: AiProviders,
    engine: AsyncEngine,
    http: httpx.AsyncClient,
    tasks: BackgroundTasks,
    retry_policy: RetryPolicy | None,
) -> Services:
    treasury = TreasuryClient(http, settings.internal_api_key, retry_policy)
    store = DocumentStore(engine)
    search = DocumentSearch(engine, providers.embedder)
    jobs = IngestJobRepository(engine)
    extractor = KeyFigureExtractor(providers.extraction_model, search, settings.extraction_full_doc_max_pages)
    pipeline = IngestPipeline(
        treasury=treasury, store=store, embedder=providers.embedder, extractor=extractor, jobs=jobs
    )
    conversations = ConversationRepository(engine)
    agent = build_chat_agent(
        providers.chat_model,
        build_tools(treasury=treasury, search=search, top_k=settings.rag_top_k),
        max_model_calls=settings.chat_max_model_calls,
        max_proposals=settings.chat_max_proposals,
    )
    return Services(
        settings=settings,
        engine=engine,
        treasury=treasury,
        document_store=store,
        search=search,
        ingest_runner=IngestRunner(pipeline=pipeline, jobs=jobs, store=store, treasury=treasury, tasks=tasks),
        chat=ChatTurnService(
            agent=agent,
            treasury=treasury,
            conversations=conversations,
            tasks=tasks,
            settings=TurnSettings(
                model_name=settings.anthropic_model,
                max_proposals=settings.chat_max_proposals,
                timeout_seconds=settings.chat_turn_timeout_seconds,
            ),
        ),
        conversations=conversations,
        rationale=RationaleService(
            model=providers.rationale_model, treasury=treasury, model_name=settings.anthropic_model
        ),
        tasks=tasks,
    )
