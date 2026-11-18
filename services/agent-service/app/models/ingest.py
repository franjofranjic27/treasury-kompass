"""DTOs of `/internal/ingest` (docs/api/agent-service.openapi.yaml)."""

from __future__ import annotations

from enum import StrEnum
from uuid import UUID

from pydantic import Field

from app.models.common import CamelModel, StrictCamelModel


class IngestRequest(StrictCamelModel):
    ingest_id: UUID
    document_id: UUID
    client_id: UUID
    filename: str = Field(min_length=1, max_length=255)
    sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    size_bytes: int = Field(ge=0)
    page_count: int | None = Field(default=None, ge=0)


class IngestAcceptance(StrEnum):
    ACCEPTED = "ACCEPTED"
    ALREADY_RUNNING = "ALREADY_RUNNING"
    ALREADY_DONE = "ALREADY_DONE"


class IngestAccepted(CamelModel):
    ingest_id: UUID
    status: IngestAcceptance
