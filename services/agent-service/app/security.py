"""Service-to-service authentication for `/internal/**` (header `X-Service-Key`)."""

from __future__ import annotations

import hmac
from typing import Annotated

from fastapi import Depends, Header, Request

from app.config import Settings
from app.problems import ProblemCode, ProblemError

SERVICE_KEY_HEADER = "X-Service-Key"


def _settings(request: Request) -> Settings:
    settings: Settings = request.app.state.settings
    return settings


def service_key_matches(provided: str | None, expected: str) -> bool:
    """Constant-time comparison; a missing header never matches."""
    if provided is None:
        return False
    return hmac.compare_digest(provided.encode("utf-8"), expected.encode("utf-8"))


async def require_service_key(
    settings: Annotated[Settings, Depends(_settings)],
    x_service_key: Annotated[str | None, Header(alias=SERVICE_KEY_HEADER)] = None,
) -> None:
    """FastAPI dependency for all `/internal/**` routes."""
    if not service_key_matches(x_service_key, settings.internal_api_key.get_secret_value()):
        raise ProblemError(ProblemCode.UNAUTHORIZED, f"Header {SERVICE_KEY_HEADER} fehlt oder ist ungültig.")
