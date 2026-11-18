"""FastAPI dependencies that hand out the process-wide services."""

from __future__ import annotations

from fastapi import Request

from app.services import Services


def get_services(request: Request) -> Services:
    services: Services = request.app.state.services
    return services
