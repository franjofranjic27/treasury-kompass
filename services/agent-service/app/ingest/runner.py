"""Starts ingests in the background: idempotent per `ingestId`, serialised per document."""

from __future__ import annotations

import asyncio
import logging
from uuid import UUID
from weakref import WeakValueDictionary

from app.background import BackgroundTasks
from app.clients.treasury import TreasuryClient, TreasuryError
from app.ingest.jobs import IngestJob, IngestJobRepository, JobStatus
from app.ingest.pipeline import IngestPipeline
from app.models.ingest import IngestAcceptance, IngestAccepted, IngestRequest
from app.models.treasury import DocumentErrorCode, DocumentStatus, DocumentStatusUpdate
from app.rag.store import DocumentStore

logger = logging.getLogger(__name__)

_INTERRUPTED_MESSAGE = "Auslesen durch Neustart des agent-service abgebrochen. Bitte erneut auslesen."


class IngestRunner:
    def __init__(
        self,
        *,
        pipeline: IngestPipeline,
        jobs: IngestJobRepository,
        store: DocumentStore,
        treasury: TreasuryClient,
        tasks: BackgroundTasks,
    ) -> None:
        self._pipeline = pipeline
        self._jobs = jobs
        self._store = store
        self._treasury = treasury
        self._tasks = tasks
        # A reprocess (new ingestId) waits for a still running ingest of the same document,
        # so pages and chunks are never written by two runs at once.
        self._document_locks: WeakValueDictionary[UUID, asyncio.Lock] = WeakValueDictionary()

    async def submit(self, request: IngestRequest) -> IngestAccepted:
        created = await self._jobs.try_create(
            ingest_id=request.ingest_id, document_id=request.document_id, client_id=request.client_id
        )
        if not created:
            job = await self._jobs.get(request.ingest_id)
            running = job is not None and job.status is JobStatus.RUNNING
            status = IngestAcceptance.ALREADY_RUNNING if running else IngestAcceptance.ALREADY_DONE
            return IngestAccepted(ingest_id=request.ingest_id, status=status)
        self._tasks.spawn(self._run_serialised(request), name=f"ingest-{request.ingest_id}")
        return IngestAccepted(ingest_id=request.ingest_id, status=IngestAcceptance.ACCEPTED)

    async def purge(self, document_id: UUID) -> None:
        """Remove pages and chunks now; if an ingest of the document still runs, purge again once it ends."""
        await self._store.purge_document(document_id)
        lock = self._document_locks.get(document_id)
        if lock is not None and lock.locked():
            self._tasks.spawn(self._purge_after(lock, document_id), name=f"purge-{document_id}")

    async def _purge_after(self, lock: asyncio.Lock, document_id: UUID) -> None:
        async with lock:
            await self._store.purge_document(document_id)

    async def recover_interrupted(self) -> None:
        """Jobs left RUNNING by a previous process can never finish: mark and report them as FAILED."""
        interrupted = await self._jobs.fail_interrupted(DocumentErrorCode.INTERNAL_ERROR.value, _INTERRUPTED_MESSAGE)
        for job in interrupted:
            self._tasks.spawn(self._report_interrupted(job), name=f"ingest-recover-{job.ingest_id}")

    async def _run_serialised(self, request: IngestRequest) -> None:
        lock = self._document_locks.get(request.document_id)
        if lock is None:
            lock = asyncio.Lock()
            self._document_locks[request.document_id] = lock
        async with lock:
            await self._pipeline.run(request)

    async def _report_interrupted(self, job: IngestJob) -> None:
        update = DocumentStatusUpdate(
            ingest_id=job.ingest_id,
            status=DocumentStatus.FAILED,
            error_code=DocumentErrorCode.INTERNAL_ERROR,
            error_message=_INTERRUPTED_MESSAGE,
        )
        try:
            await self._treasury.update_document_status(job.document_id, update)
        except TreasuryError:
            logger.warning("Abgebrochenen Ingest %s nicht gemeldet", job.ingest_id, exc_info=True)
