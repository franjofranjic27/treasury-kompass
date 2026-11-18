"""DTO of `GET /health`."""

from __future__ import annotations

from typing import Literal

from app.models.common import CamelModel


class Health(CamelModel):
    status: Literal["ok", "degraded"]
    database: Literal["ok", "error"]
    version: str
