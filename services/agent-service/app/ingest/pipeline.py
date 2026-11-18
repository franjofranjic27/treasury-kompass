"""Ingest stages with status callbacks (architecture.md 9.1, 10): fetch, parse, index, extract, propose."""

from __future__ import annotations

import asyncio
import hashlib
import logging
from collections import Counter
from collections.abc import Sequence
from dataclasses import dataclass, field

from app.clients.treasury import StaleIngestError, TreasuryClient, TreasuryError, TreasuryNotFoundError
from app.ingest.chunking import chunk_pages
from app.ingest.extraction import (
    ExtractionDocument,
    ExtractionFailedError,
    ExtractionRefusedError,
    KeyFigureExtractor,
)
from app.ingest.jobs import IngestJobRepository, IngestStage, JobStatus
from app.ingest.pdf import NoTextLayerError, PdfParseError, extract_pages
from app.models.ingest import IngestRequest
from app.models.treasury import (
    DocumentErrorCode,
    DocumentStatus,
    DocumentStatusUpdate,
    ProposalBatch,
    ProposalInput,
    ProposalSource,
)
from app.rag.embeddings import Embedder, EmbeddingError
from app.rag.store import DocumentStore, EmbeddedChunk, PageText

logger = logging.getLogger(__name__)

STALE_INGEST_CODE = "STALE_INGEST"


@dataclass(eq=False)
class IngestStageError(Exception):
    """A stage failed; `message` is German and shown at the document."""

    error_code: DocumentErrorCode
    message: str

    def __post_init__(self) -> None:
        super().__init__(f"{self.error_code}: {self.message}")


@dataclass
class _Progress:
    chunk_count: int | None = None
    outcomes: Counter[str] = field(default_factory=Counter)


class IngestPipeline:
    def __init__(
        self,
        *,
        treasury: TreasuryClient,
        store: DocumentStore,
        embedder: Embedder,
        extractor: KeyFigureExtractor,
        jobs: IngestJobRepository,
    ) -> None:
        self._treasury = treasury
        self._store = store
        self._embedder = embedder
        self._extractor = extractor
        self._jobs = jobs

    async def run(self, request: IngestRequest) -> None:
        """Run all stages. Never raises except on cancellation: every failure ends as FAILED callback."""
        progress = _Progress()
        logger.info("Ingest %s für Dokument %s gestartet", request.ingest_id, request.document_id)
        try:
            await self._run_stages(request, progress)
        except StaleIngestError:
            logger.info("Ingest %s ist veraltet (neuere ingestId), Abbruch ohne Meldung", request.ingest_id)
            await self._jobs.finish(
                request.ingest_id, JobStatus.FAILED, error_code=STALE_INGEST_CODE, error_message="Veraltete ingestId"
            )
        except IngestStageError as failure:
            await self._fail(request, failure, progress)
        except Exception:
            # Task boundary: an unexpected error must still end as FAILED at the document.
            logger.exception("Ingest %s: unerwarteter Fehler", request.ingest_id)
            failure = IngestStageError(DocumentErrorCode.INTERNAL_ERROR, "Unerwarteter Fehler beim Auslesen.")
            await self._fail(request, failure, progress)

    async def _run_stages(self, request: IngestRequest, progress: _Progress) -> None:
        data = await self._fetch(request)

        await self._jobs.set_stage(request.ingest_id, IngestStage.PARSE)
        pages = await self._parse(data)
        await self._store.replace_pages(client_id=request.client_id, document_id=request.document_id, pages=pages)
        await self._report(request, DocumentStatus.TEXT_EXTRACTED, page_count=len(pages))

        await self._jobs.set_stage(request.ingest_id, IngestStage.EMBED)
        progress.chunk_count = await self._index(request, pages)
        await self._report(request, DocumentStatus.INDEXED, chunk_count=progress.chunk_count)

        await self._jobs.set_stage(request.ingest_id, IngestStage.EXTRACT)
        proposals = await self._extract(request, pages)

        await self._jobs.set_stage(request.ingest_id, IngestStage.PROPOSE)
        await self._propose(request, proposals, progress)
        await self._report(request, DocumentStatus.EXTRACTED, chunk_count=progress.chunk_count)
        await self._jobs.finish(request.ingest_id, JobStatus.DONE)
        logger.info(
            "Ingest %s fertig: %d Seiten, %d Chunks, Vorschläge %s",
            request.ingest_id,
            len(pages),
            progress.chunk_count,
            dict(progress.outcomes),
        )

    async def _fetch(self, request: IngestRequest) -> bytes:
        try:
            content = await self._treasury.get_document_content(request.document_id)
        except TreasuryNotFoundError as error:
            raise IngestStageError(DocumentErrorCode.INTERNAL_ERROR, "Dokument nicht mehr vorhanden.") from error
        except TreasuryError as error:
            raise IngestStageError(DocumentErrorCode.INTERNAL_ERROR, "Dokumentinhalt nicht abrufbar.") from error
        if content.client_id is not None and content.client_id != request.client_id:
            # INV-6: never index a document under a client it does not belong to.
            raise IngestStageError(DocumentErrorCode.INTERNAL_ERROR, "Dokument gehört nicht zum angegebenen Kunden.")
        if hashlib.sha256(content.data).hexdigest() != request.sha256:
            raise IngestStageError(DocumentErrorCode.CHECKSUM_MISMATCH, "Prüfsumme (SHA-256) stimmt nicht überein.")
        return content.data

    async def _parse(self, data: bytes) -> list[PageText]:
        try:
            # pypdf is CPU-bound; a 150-page report must not block the event loop.
            return await asyncio.to_thread(extract_pages, data)
        except NoTextLayerError as error:
            raise IngestStageError(DocumentErrorCode.NO_TEXT_LAYER, "Kein Textlayer gefunden (Scan?).") from error
        except PdfParseError as error:
            raise IngestStageError(DocumentErrorCode.PDF_PARSE_ERROR, "PDF konnte nicht gelesen werden.") from error

    async def _index(self, request: IngestRequest, pages: Sequence[PageText]) -> int:
        chunks = chunk_pages(pages)
        try:
            vectors = await self._embedder.embed_documents([chunk.content for chunk in chunks])
        except EmbeddingError as error:
            raise IngestStageError(
                DocumentErrorCode.EMBEDDING_FAILED, "Embeddings nicht verfügbar (Voyage AI)."
            ) from error
        embedded = [
            EmbeddedChunk(page=chunk.page, chunk_index=chunk.chunk_index, content=chunk.content, embedding=vector)
            for chunk, vector in zip(chunks, vectors, strict=True)
        ]
        return await self._store.replace_chunks(
            client_id=request.client_id,
            document_id=request.document_id,
            document_name=request.filename,
            embedding_model=self._embedder.model_name,
            chunks=embedded,
        )

    async def _extract(self, request: IngestRequest, pages: Sequence[PageText]) -> list[ProposalInput]:
        document = ExtractionDocument(
            client_id=request.client_id,
            document_id=request.document_id,
            document_name=request.filename,
            pages=pages,
        )
        try:
            return await self._extractor.extract(document)
        except ExtractionRefusedError as error:
            raise IngestStageError(
                DocumentErrorCode.LLM_REFUSAL, "Das Sprachmodell hat das Auslesen abgelehnt."
            ) from error
        except (ExtractionFailedError, EmbeddingError) as error:
            raise IngestStageError(
                DocumentErrorCode.EXTRACTION_FAILED, "Kennzahlen konnten nicht ausgelesen werden."
            ) from error

    async def _propose(self, request: IngestRequest, proposals: Sequence[ProposalInput], progress: _Progress) -> None:
        if not proposals:
            logger.info("Ingest %s: keine belegten Werte gefunden", request.ingest_id)
            return
        batch = ProposalBatch(
            source=ProposalSource.DOCUMENT,
            document_id=request.document_id,
            ingest_id=request.ingest_id,
            proposals=tuple(proposals),
        )
        try:
            result = await self._treasury.create_answer_proposals(request.client_id, batch)
        except StaleIngestError:
            raise
        except TreasuryError as error:
            raise IngestStageError(
                DocumentErrorCode.INTERNAL_ERROR, "Vorschläge konnten nicht übermittelt werden."
            ) from error
        progress.outcomes.update(entry.outcome.value for entry in result.results)

    async def _report(
        self,
        request: IngestRequest,
        status: DocumentStatus,
        *,
        page_count: int | None = None,
        chunk_count: int | None = None,
    ) -> None:
        update = DocumentStatusUpdate(
            ingest_id=request.ingest_id, status=status, page_count=page_count, chunk_count=chunk_count
        )
        try:
            await self._treasury.update_document_status(request.document_id, update)
        except StaleIngestError:
            raise
        except TreasuryError as error:
            raise IngestStageError(DocumentErrorCode.INTERNAL_ERROR, f"Status {status} nicht gemeldet.") from error

    async def _fail(self, request: IngestRequest, failure: IngestStageError, progress: _Progress) -> None:
        logger.warning("Ingest %s fehlgeschlagen: %s", request.ingest_id, failure.error_code)
        await self._jobs.finish(
            request.ingest_id, JobStatus.FAILED, error_code=failure.error_code.value, error_message=failure.message
        )
        update = DocumentStatusUpdate(
            ingest_id=request.ingest_id,
            status=DocumentStatus.FAILED,
            chunk_count=progress.chunk_count,
            error_code=failure.error_code,
            error_message=failure.message,
        )
        try:
            await self._treasury.update_document_status(request.document_id, update)
        except StaleIngestError:
            logger.info("Ingest %s: FAILED nicht gemeldet, ingestId veraltet", request.ingest_id)
        except TreasuryError:
            logger.error("Ingest %s: FAILED konnte nicht gemeldet werden", request.ingest_id, exc_info=True)
