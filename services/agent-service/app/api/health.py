"""`GET /health`: liveness and readiness (database) for Compose; not routed by the gateway."""

from __future__ import annotations

from importlib.metadata import PackageNotFoundError, version
from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from app.api.dependencies import get_services
from app.db.engine import database_is_reachable
from app.models.health import Health
from app.services import Services

router = APIRouter(tags=["Health"])


def service_version() -> str:
    try:
        return version("agent-service")
    except PackageNotFoundError:
        return "0.1.0"


@router.get("/health", response_model=Health, operation_id="health")
async def health(services: Annotated[Services, Depends(get_services)]) -> JSONResponse:
    reachable = await database_is_reachable(services.engine)
    body = Health(
        status="ok" if reachable else "degraded", database="ok" if reachable else "error", version=service_version()
    )
    return JSONResponse(status_code=200 if reachable else 503, content=body.to_json_dict())
