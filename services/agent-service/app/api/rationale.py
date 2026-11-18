"""`POST /agent/clients/{clientId}/rationale-draft`."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Body, Depends

from app.api.dependencies import get_services
from app.models.rationale import RationaleDraft, RationaleDraftRequest
from app.services import Services

router = APIRouter(prefix="/agent", tags=["Rationale"])


@router.post(
    "/clients/{client_id}/rationale-draft",
    response_model=RationaleDraft,
    response_model_by_alias=True,
    operation_id="draftRationale",
)
async def draft_rationale(
    client_id: UUID,
    services: Annotated[Services, Depends(get_services)],
    body: Annotated[RationaleDraftRequest | None, Body()] = None,
) -> RationaleDraft:
    audience = body.audience if body is not None else "CLIENT"
    return await services.rationale.draft(client_id, audience)
