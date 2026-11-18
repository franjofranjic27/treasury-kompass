"""Ingest jobs (`rag.ingest_job`): idempotency per `ingestId` and the stage of a running ingest."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from enum import StrEnum
from uuid import UUID

from sqlalchemy import select, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.ext.asyncio import AsyncEngine

from app.db.tables import ingest_job


class JobStatus(StrEnum):
    RUNNING = "RUNNING"
    DONE = "DONE"
    FAILED = "FAILED"


class IngestStage(StrEnum):
    FETCH = "FETCH"
    PARSE = "PARSE"
    EMBED = "EMBED"
    EXTRACT = "EXTRACT"
    PROPOSE = "PROPOSE"
    DONE = "DONE"


@dataclass(frozen=True)
class IngestJob:
    ingest_id: UUID
    document_id: UUID
    client_id: UUID
    status: JobStatus
    stage: IngestStage


class IngestJobRepository:
    def __init__(self, engine: AsyncEngine) -> None:
        self._engine = engine

    async def try_create(self, *, ingest_id: UUID, document_id: UUID, client_id: UUID) -> bool:
        """Insert a RUNNING job; False if this `ingestId` is already known."""
        statement = (
            insert(ingest_job)
            .values(
                ingest_id=ingest_id,
                document_id=document_id,
                client_id=client_id,
                status=JobStatus.RUNNING.value,
                stage=IngestStage.FETCH.value,
                started_at=datetime.now(UTC),
            )
            .on_conflict_do_nothing(index_elements=[ingest_job.c.ingest_id])
            .returning(ingest_job.c.ingest_id)
        )
        async with self._engine.begin() as connection:
            created = (await connection.execute(statement)).scalar_one_or_none()
        return created is not None

    async def get(self, ingest_id: UUID) -> IngestJob | None:
        async with self._engine.connect() as connection:
            row = (await connection.execute(select(ingest_job).where(ingest_job.c.ingest_id == ingest_id))).first()
        if row is None:
            return None
        return IngestJob(
            ingest_id=row.ingest_id,
            document_id=row.document_id,
            client_id=row.client_id,
            status=JobStatus(row.status),
            stage=IngestStage(row.stage),
        )

    async def set_stage(self, ingest_id: UUID, stage: IngestStage) -> None:
        async with self._engine.begin() as connection:
            await connection.execute(
                update(ingest_job).where(ingest_job.c.ingest_id == ingest_id).values(stage=stage.value)
            )

    async def finish(
        self,
        ingest_id: UUID,
        status: JobStatus,
        *,
        error_code: str | None = None,
        error_message: str | None = None,
    ) -> None:
        values: dict[str, object] = {
            "status": status.value,
            "error_code": error_code,
            "error_message": error_message[:500] if error_message else None,
            "finished_at": datetime.now(UTC),
        }
        if status is JobStatus.DONE:
            values["stage"] = IngestStage.DONE.value
        async with self._engine.begin() as connection:
            await connection.execute(update(ingest_job).where(ingest_job.c.ingest_id == ingest_id).values(**values))

    async def fail_interrupted(self, error_code: str, error_message: str) -> list[IngestJob]:
        """Mark jobs left RUNNING by a previous process as FAILED and return them."""
        statement = (
            update(ingest_job)
            .where(ingest_job.c.status == JobStatus.RUNNING.value)
            .values(
                status=JobStatus.FAILED.value,
                error_code=error_code,
                error_message=error_message,
                finished_at=datetime.now(UTC),
            )
            .returning(ingest_job)
        )
        async with self._engine.begin() as connection:
            rows = (await connection.execute(statement)).all()
        return [
            IngestJob(
                ingest_id=row.ingest_id,
                document_id=row.document_id,
                client_id=row.client_id,
                status=JobStatus.FAILED,
                stage=IngestStage(row.stage),
            )
            for row in rows
        ]
