"""RFC 9457 problem details (`application/problem+json`) for all HTTP errors of the service."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from enum import StrEnum
from http import HTTPStatus

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

logger = logging.getLogger(__name__)

PROBLEM_CONTENT_TYPE = "application/problem+json"
PROBLEM_TYPE_PREFIX = "urn:treasury-kompass:problem:"


class ProblemCode(StrEnum):
    """Problem slugs of the agent-service contract (`Problem.code`)."""

    VALIDATION_ERROR = "validation-error"
    UNAUTHORIZED = "unauthorized"
    NOT_FOUND = "not-found"
    ASSESSMENT_INCOMPLETE = "assessment-incomplete"
    UPSTREAM_UNAVAILABLE = "upstream-unavailable"
    LLM_UNAVAILABLE = "llm-unavailable"
    INTERNAL_ERROR = "internal-error"


_STATUS_BY_CODE: dict[ProblemCode, HTTPStatus] = {
    ProblemCode.VALIDATION_ERROR: HTTPStatus.BAD_REQUEST,
    ProblemCode.UNAUTHORIZED: HTTPStatus.UNAUTHORIZED,
    ProblemCode.NOT_FOUND: HTTPStatus.NOT_FOUND,
    ProblemCode.ASSESSMENT_INCOMPLETE: HTTPStatus.CONFLICT,
    ProblemCode.UPSTREAM_UNAVAILABLE: HTTPStatus.SERVICE_UNAVAILABLE,
    ProblemCode.LLM_UNAVAILABLE: HTTPStatus.SERVICE_UNAVAILABLE,
    ProblemCode.INTERNAL_ERROR: HTTPStatus.INTERNAL_SERVER_ERROR,
}

_TITLE_BY_CODE: dict[ProblemCode, str] = {
    ProblemCode.VALIDATION_ERROR: "Ungültige Eingabe",
    ProblemCode.UNAUTHORIZED: "Nicht autorisiert",
    ProblemCode.NOT_FOUND: "Nicht gefunden",
    ProblemCode.ASSESSMENT_INCOMPLETE: "Beurteilung unvollständig",
    ProblemCode.UPSTREAM_UNAVAILABLE: "Dienst nicht erreichbar",
    ProblemCode.LLM_UNAVAILABLE: "Sprachmodell nicht verfügbar",
    ProblemCode.INTERNAL_ERROR: "Interner Fehler",
}


@dataclass(frozen=True)
class FieldError:
    field: str
    message: str


@dataclass(eq=False)
class ProblemError(Exception):
    """Raise to answer a request with a problem document."""

    code: ProblemCode
    detail: str
    errors: tuple[FieldError, ...] = field(default=())

    def __post_init__(self) -> None:
        super().__init__(f"{self.code}: {self.detail}")

    @property
    def status(self) -> HTTPStatus:
        return _STATUS_BY_CODE[self.code]


def problem_response(
    code: ProblemCode,
    detail: str,
    *,
    errors: tuple[FieldError, ...] = (),
    instance: str | None = None,
) -> JSONResponse:
    status = _STATUS_BY_CODE[code]
    body: dict[str, object] = {
        "type": f"{PROBLEM_TYPE_PREFIX}{code.value}",
        "title": _TITLE_BY_CODE[code],
        "status": int(status),
        "detail": detail,
        "code": code.value,
    }
    if instance is not None:
        body["instance"] = instance
    if errors:
        body["errors"] = [{"field": error.field, "message": error.message} for error in errors]
    return JSONResponse(status_code=int(status), content=body, media_type=PROBLEM_CONTENT_TYPE)


def _field_path(location: tuple[int | str, ...]) -> str:
    parts = [str(part) for part in location if part not in ("body", "query", "path", "header")]
    return ".".join(parts) or "body"


def _expect[E: Exception](exc: Exception, expected: type[E]) -> E:
    if not isinstance(exc, expected):
        raise TypeError(f"Handler für {expected.__name__} erhielt {type(exc).__name__}") from exc
    return exc


async def _handle_problem(request: Request, exc: Exception) -> JSONResponse:
    exc = _expect(exc, ProblemError)
    return problem_response(exc.code, exc.detail, errors=exc.errors, instance=request.url.path)


async def _handle_validation(request: Request, exc: Exception) -> JSONResponse:
    exc = _expect(exc, RequestValidationError)
    errors = tuple(FieldError(field=_field_path(tuple(err["loc"])), message=err["msg"]) for err in exc.errors())
    return problem_response(
        ProblemCode.VALIDATION_ERROR,
        "Die Anfrage enthält ungültige Felder.",
        errors=errors,
        instance=request.url.path,
    )


async def _handle_http(request: Request, exc: Exception) -> JSONResponse:
    exc = _expect(exc, StarletteHTTPException)
    code = {
        HTTPStatus.NOT_FOUND: ProblemCode.NOT_FOUND,
        HTTPStatus.UNAUTHORIZED: ProblemCode.UNAUTHORIZED,
        HTTPStatus.METHOD_NOT_ALLOWED: ProblemCode.VALIDATION_ERROR,
    }.get(HTTPStatus(exc.status_code), ProblemCode.INTERNAL_ERROR)
    detail = exc.detail if isinstance(exc.detail, str) else HTTPStatus(exc.status_code).phrase
    response = problem_response(code, detail, instance=request.url.path)
    response.status_code = exc.status_code
    return response


async def _handle_unexpected(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("Unerwarteter Fehler bei %s %s", request.method, request.url.path)
    return problem_response(
        ProblemCode.INTERNAL_ERROR,
        "Unerwarteter Fehler. Details stehen im Log.",
        instance=request.url.path,
    )


def register_problem_handlers(app: FastAPI) -> None:
    app.add_exception_handler(ProblemError, _handle_problem)
    app.add_exception_handler(RequestValidationError, _handle_validation)
    app.add_exception_handler(StarletteHTTPException, _handle_http)
    app.add_exception_handler(Exception, _handle_unexpected)
