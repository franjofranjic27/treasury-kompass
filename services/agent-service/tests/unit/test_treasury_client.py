from __future__ import annotations

from uuid import uuid4

import httpx
import pytest
import respx
from pydantic import SecretStr

from app.clients.treasury import (
    RetryPolicy,
    StaleIngestError,
    TreasuryClient,
    TreasuryNotFoundError,
    TreasuryRequestError,
    TreasuryUnavailableError,
)
from app.models.treasury import DocumentStatus, DocumentStatusUpdate
from tests.support.harness import INTERNAL_KEY, TREASURY_URL


class _Sleeps:
    def __init__(self) -> None:
        self.delays: list[float] = []

    async def __call__(self, delay: float) -> None:
        self.delays.append(delay)


def _client(router: respx.Router, sleeps: _Sleeps | None = None) -> TreasuryClient:
    http = httpx.AsyncClient(base_url=TREASURY_URL, transport=httpx.MockTransport(router.async_handler))
    return TreasuryClient(http, SecretStr(INTERNAL_KEY), RetryPolicy(), sleep=sleeps or _Sleeps())


async def test_retries_with_backoff_then_succeeds() -> None:
    router = respx.Router(base_url=TREASURY_URL)
    client_id = uuid4()
    router.get(f"/api/clients/{client_id}/assessment").mock(
        side_effect=[httpx.Response(503), httpx.ConnectError("weg"), httpx.Response(200, json={"version": 1})]
    )
    sleeps = _Sleeps()

    assert await _client(router, sleeps).get_assessment(client_id) == {"version": 1}
    assert sleeps.delays == [1.0, 2.0]


async def test_gives_up_after_one_attempt_plus_three_retries() -> None:
    router = respx.Router(base_url=TREASURY_URL)
    route = router.get("/api/rules").mock(return_value=httpx.Response(503))
    sleeps = _Sleeps()

    with pytest.raises(TreasuryUnavailableError):
        await _client(router, sleeps).get_rules()
    assert route.call_count == 4
    assert sleeps.delays == [1.0, 2.0, 4.0]


async def test_client_errors_are_not_retried() -> None:
    router = respx.Router(base_url=TREASURY_URL)
    client_id = uuid4()
    route = router.get(f"/api/clients/{client_id}").mock(return_value=httpx.Response(404))

    with pytest.raises(TreasuryNotFoundError):
        await _client(router).get_client(client_id)
    assert route.call_count == 1


async def test_stale_ingest_is_its_own_error() -> None:
    router = respx.Router(base_url=TREASURY_URL)
    document_id = uuid4()
    router.post(f"/internal/documents/{document_id}/status").mock(
        return_value=httpx.Response(
            409,
            json={"type": "urn:treasury-kompass:problem:stale-ingest", "code": "stale-ingest", "status": 409},
            headers={"content-type": "application/problem+json"},
        )
    )
    update = DocumentStatusUpdate(ingest_id=uuid4(), status=DocumentStatus.INDEXED, chunk_count=3)

    with pytest.raises(StaleIngestError):
        await _client(router).update_document_status(document_id, update)


async def test_other_problems_carry_the_code() -> None:
    router = respx.Router(base_url=TREASURY_URL)
    document_id = uuid4()
    router.post(f"/internal/documents/{document_id}/status").mock(
        return_value=httpx.Response(409, json={"code": "invalid-status-transition", "detail": "nein"})
    )
    update = DocumentStatusUpdate(ingest_id=uuid4(), status=DocumentStatus.EXTRACTED)

    with pytest.raises(TreasuryRequestError) as error:
        await _client(router).update_document_status(document_id, update)
    assert error.value.code == "invalid-status-transition"
    assert not isinstance(error.value, StaleIngestError)


async def test_service_key_only_on_internal_calls() -> None:
    router = respx.Router(base_url=TREASURY_URL)
    client_id, document_id = uuid4(), uuid4()
    public = router.get(f"/api/clients/{client_id}").mock(return_value=httpx.Response(200, json={}))
    internal = router.get(f"/internal/documents/{document_id}/content").mock(
        return_value=httpx.Response(
            200, content=b"%PDF-1.7", headers={"X-Client-Id": str(client_id), "X-Document-Sha256": "abc"}
        )
    )
    client = _client(router)

    await client.get_client(client_id)
    content = await client.get_document_content(document_id)

    assert "X-Service-Key" not in public.calls.last.request.headers
    assert internal.calls.last.request.headers["X-Service-Key"] == INTERNAL_KEY
    assert content.data == b"%PDF-1.7"
    assert content.client_id == client_id
    assert content.sha256 == "abc"


async def test_status_update_payload_is_camel_case() -> None:
    router = respx.Router(base_url=TREASURY_URL)
    document_id, ingest_id = uuid4(), uuid4()
    route = router.post(f"/internal/documents/{document_id}/status").mock(return_value=httpx.Response(204))

    await _client(router).update_document_status(
        document_id, DocumentStatusUpdate(ingest_id=ingest_id, status=DocumentStatus.TEXT_EXTRACTED, page_count=24)
    )

    body = route.calls.last.request.read()
    assert body == (
        b'{"ingestId":"' + str(ingest_id).encode() + b'","status":"TEXT_EXTRACTED","pageCount":24,'
        b'"chunkCount":null,"errorCode":null,"errorMessage":null}'
    )
