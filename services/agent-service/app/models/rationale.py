"""DTOs of `POST /agent/clients/{clientId}/rationale-draft`."""

from __future__ import annotations

from datetime import datetime
from typing import Literal
from uuid import UUID

from app.models.common import CamelModel, StrictCamelModel

Audience = Literal["CLIENT", "ADVISOR"]


class RationaleDraftRequest(StrictCamelModel):
    audience: Audience = "CLIENT"


class RationaleDraft(CamelModel):
    client_id: UUID
    assessment_version: int
    text: str
    model: str
    generated_at: datetime
