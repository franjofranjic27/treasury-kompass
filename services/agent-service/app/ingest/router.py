"""Internal endpoints for the treasury-service: start an ingest, purge a deleted document."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Request, Response, status

from app.ingest.runner import IngestRunner
from app.models.ingest import IngestAccepted, IngestRequest
from app.security import require_service_key

router = APIRouter(prefix="/internal", tags=["Internal"], dependencies=[Depends(require_service_key)])


def _runner(request: Request) -> IngestRunner:
    runner: IngestRunner = request.app.state.services.ingest_runner
    return runner


@router.post(
    "/ingest",
    status_code=status.HTTP_202_ACCEPTED,
    response_model=IngestAccepted,
    response_model_by_alias=True,
    operation_id="startIngest",
)
async def start_ingest(
    body: IngestRequest,
    runner: Annotated[IngestRunner, Depends(_runner)],
) -> IngestAccepted:
    return await runner.submit(body)


@router.delete(
    "/documents/{document_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    operation_id="purgeDocument",
)
async def purge_document(
    document_id: UUID,
    runner: Annotated[IngestRunner, Depends(_runner)],
) -> Response:
    await runner.purge(document_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
