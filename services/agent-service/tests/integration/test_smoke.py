from __future__ import annotations

import httpx

from tests.support.fakes import ai
from tests.support.harness import Harness
from tests.support.sse import names, parse_sse
from tests.support.treasury_data import MUSTER_ID, client_detail


async def test_health_reports_database(harness: Harness) -> None:
    response = await harness.client.get("/health")
    assert response.status_code == 200
    assert response.json()["database"] == "ok"


async def test_simple_turn_streams(harness: Harness) -> None:
    harness.treasury.get(f"/api/clients/{MUSTER_ID}").mock(
        return_value=httpx.Response(200, json=client_detail(MUSTER_ID, "Muster Maschinenbau AG"))
    )
    harness.chat_model.steps.append(ai("Grüezi, wie kann ich helfen?"))
    response = await harness.client.post(
        "/agent/chat",
        json={"clientId": str(MUSTER_ID), "message": "Hallo", "context": {"screen": "DASHBOARD"}},
    )
    print(response.headers)
    print(response.text)
    events = parse_sse(response.text)
    assert names(events)[0] == "meta"
    assert names(events)[-1] == "done"
