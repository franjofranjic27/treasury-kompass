"""HTTP client for the treasury-service: the only module that talks to it (architecture.md 4.2, 10.3)."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable, Mapping
from dataclasses import dataclass
from http import HTTPStatus
from uuid import UUID

import httpx
from pydantic import SecretStr, TypeAdapter, ValidationError

from app.models.treasury import (
    ClientInfo,
    DocumentContent,
    DocumentStatusUpdate,
    JsonDocument,
    ProposalBatch,
    ProposalBatchResult,
    QuestionnaireInfo,
    SimulationRequest,
)
from app.security import SERVICE_KEY_HEADER

logger = logging.getLogger(__name__)

_RETRYABLE_STATUS = frozenset(
    {
        HTTPStatus.TOO_MANY_REQUESTS,
        HTTPStatus.BAD_GATEWAY,
        HTTPStatus.SERVICE_UNAVAILABLE,
        HTTPStatus.GATEWAY_TIMEOUT,
    }
)
_JSON_DOCUMENT = TypeAdapter(JsonDocument)
REQUEST_TIMEOUT_SECONDS = 10.0


class TreasuryError(Exception):
    """Base class for all failures talking to the treasury-service."""


class TreasuryUnavailableError(TreasuryError):
    """Network error or 5xx after all retries."""


class TreasuryNotFoundError(TreasuryError):
    """404 from the treasury-service (client, document, ...)."""


@dataclass(eq=False)
class TreasuryRequestError(TreasuryError):
    """Any other non-success answer, with the problem slug if the body had one."""

    status: int
    code: str | None
    detail: str | None

    def __post_init__(self) -> None:
        super().__init__(f"treasury-service antwortete {self.status} ({self.code or 'ohne Problem-Code'})")


class StaleIngestError(TreasuryRequestError):
    """409 `stale-ingest`: a newer ingest owns the document, this one must stop silently."""


@dataclass(frozen=True)
class RetryPolicy:
    """Delays between attempts; `(1, 2, 4)` means one attempt plus three retries."""

    delays: tuple[float, ...] = (1.0, 2.0, 4.0)


Sleep = Callable[[float], Awaitable[None]]


def create_http_client(base_url: str, transport: httpx.AsyncBaseTransport | None = None) -> httpx.AsyncClient:
    return httpx.AsyncClient(base_url=base_url, timeout=httpx.Timeout(REQUEST_TIMEOUT_SECONDS), transport=transport)


class TreasuryClient:
    """Typed access to the treasury endpoints this service uses. Read endpoints return their JSON unchanged."""

    def __init__(
        self,
        http: httpx.AsyncClient,
        service_key: SecretStr,
        retry_policy: RetryPolicy | None = None,
        sleep: Sleep = asyncio.sleep,
    ) -> None:
        self._http = http
        self._service_key = service_key
        self._retry_policy = retry_policy or RetryPolicy()
        self._sleep = sleep

    async def get_client(self, client_id: UUID) -> JsonDocument:
        return await self._get_json(f"/api/clients/{client_id}")

    async def get_client_info(self, client_id: UUID) -> ClientInfo:
        return ClientInfo.model_validate(await self.get_client(client_id))

    async def get_questionnaire(self, client_id: UUID) -> JsonDocument:
        return await self._get_json(f"/api/clients/{client_id}/questionnaire")

    async def get_questionnaire_info(self, client_id: UUID) -> QuestionnaireInfo:
        return QuestionnaireInfo.model_validate(await self.get_questionnaire(client_id))

    async def get_assessment(self, client_id: UUID) -> JsonDocument:
        return await self._get_json(f"/api/clients/{client_id}/assessment")

    async def get_audit_events(self, client_id: UUID, limit: int) -> JsonDocument:
        return await self._get_json(f"/api/clients/{client_id}/audit-events", params={"limit": limit})

    async def get_rules(self) -> JsonDocument:
        return await self._get_json("/api/rules")

    async def simulate_assessment(self, client_id: UUID, request: SimulationRequest) -> JsonDocument:
        response = await self._send(
            "POST",
            f"/internal/clients/{client_id}/assessment/simulate",
            json=request.to_json_dict(),
            internal=True,
        )
        return _JSON_DOCUMENT.validate_python(response.json())

    async def get_document_content(self, document_id: UUID) -> DocumentContent:
        response = await self._send("GET", f"/internal/documents/{document_id}/content", internal=True)
        client_header = response.headers.get("X-Client-Id")
        return DocumentContent(
            data=response.content,
            client_id=UUID(client_header) if client_header else None,
            sha256=response.headers.get("X-Document-Sha256"),
        )

    async def update_document_status(self, document_id: UUID, update: DocumentStatusUpdate) -> None:
        await self._send(
            "POST",
            f"/internal/documents/{document_id}/status",
            json=update.to_json_dict(),
            internal=True,
        )

    async def create_answer_proposals(self, client_id: UUID, batch: ProposalBatch) -> ProposalBatchResult:
        response = await self._send(
            "POST",
            f"/internal/clients/{client_id}/answer-proposals",
            json=batch.to_json_dict(),
            internal=True,
        )
        try:
            return ProposalBatchResult.model_validate(response.json())
        except ValidationError as error:
            raise TreasuryRequestError(response.status_code, None, "Antwort entspricht nicht dem Vertrag") from error

    async def _get_json(self, path: str, params: Mapping[str, int | str] | None = None) -> JsonDocument:
        response = await self._send("GET", path, params=params)
        return _JSON_DOCUMENT.validate_python(response.json())

    async def _send(
        self,
        method: str,
        path: str,
        *,
        json: object | None = None,
        params: Mapping[str, int | str] | None = None,
        internal: bool = False,
    ) -> httpx.Response:
        headers = {SERVICE_KEY_HEADER: self._service_key.get_secret_value()} if internal else {}
        delays = (0.0, *self._retry_policy.delays)
        last_failure: str = "kein Versuch"
        for attempt, delay in enumerate(delays, start=1):
            if delay:
                await self._sleep(delay)
            try:
                response = await self._http.request(method, path, json=json, params=params, headers=headers)
            except httpx.TransportError as error:
                last_failure = type(error).__name__
                logger.warning("treasury %s %s Versuch %d fehlgeschlagen: %s", method, path, attempt, last_failure)
                continue
            if response.status_code in _RETRYABLE_STATUS:
                last_failure = f"HTTP {response.status_code}"
                logger.warning("treasury %s %s Versuch %d: %s", method, path, attempt, last_failure)
                continue
            _raise_for_status(response)
            return response
        msg = f"treasury-service nicht erreichbar ({method} {path}: {last_failure})"
        raise TreasuryUnavailableError(msg)


def _problem_field(response: httpx.Response, name: str) -> str | None:
    if "json" not in response.headers.get("content-type", ""):
        return None
    try:
        body = response.json()
    except ValueError:
        return None
    value = body.get(name) if isinstance(body, dict) else None
    return value if isinstance(value, str) else None


def _raise_for_status(response: httpx.Response) -> None:
    if response.is_success:
        return
    if response.status_code == HTTPStatus.NOT_FOUND:
        raise TreasuryNotFoundError(f"{response.request.method} {response.request.url.path}: 404")
    code = _problem_field(response, "code")
    detail = _problem_field(response, "detail")
    if response.status_code == HTTPStatus.CONFLICT and code == "stale-ingest":
        raise StaleIngestError(response.status_code, code, detail)
    raise TreasuryRequestError(response.status_code, code, detail)
