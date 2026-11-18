"""The request that actually leaves the SDK for `claude-opus-5-5`, captured with an in-memory transport."""

from __future__ import annotations

import json

import anthropic
import httpx2
import pytest
from langchain_core.messages import HumanMessage
from pydantic import BaseModel

from app.agent.llm import FALLBACK_BETA, build_chat_model, chat_purpose, extraction_purpose
from tests.support.harness import make_settings

_RESPONSE = {
    "id": "msg_1",
    "type": "message",
    "role": "assistant",
    "model": "claude-opus-5-5",
    "content": [{"type": "thinking", "thinking": "…", "signature": "sig"}, {"type": "text", "text": '{"value": 1}'}],
    "stop_reason": "end_turn",
    "stop_sequence": None,
    "usage": {"input_tokens": 10, "output_tokens": 5},
}


class _Answer(BaseModel):
    value: int


class _Capture:
    def __init__(self) -> None:
        self.requests: list[httpx2.Request] = []

    def __call__(self, request: httpx2.Request) -> httpx2.Response:
        self.requests.append(request)
        return httpx2.Response(200, json=_RESPONSE)

    @property
    def body(self) -> dict[str, object]:
        return json.loads(self.requests[-1].content)

    @property
    def headers(self) -> httpx2.Headers:
        return self.requests[-1].headers


def _wire(model: object, capture: _Capture) -> None:
    # Replace the cached SDK client so the real request building runs against an in-memory transport.
    client = anthropic.AsyncClient(
        api_key="test", http_client=httpx2.AsyncClient(transport=httpx2.MockTransport(capture))
    )
    model.__dict__["_async_client"] = client  # type: ignore[attr-defined]


async def test_fallback_and_effort_reach_the_wire() -> None:
    settings = make_settings()
    model = build_chat_model(settings, chat_purpose(settings))
    capture = _Capture()
    _wire(model, capture)

    await model.ainvoke([HumanMessage("Hallo")])

    body = capture.body
    assert capture.headers["anthropic-beta"] == FALLBACK_BETA
    assert body["fallbacks"] == "default"
    assert body["model"] == "claude-opus-5-5"
    assert body["output_config"] == {"effort": "medium"}
    for forbidden in ("temperature", "top_p", "top_k", "tool_choice", "extra_body"):
        assert forbidden not in body
    assert body["thinking"] != {"type": "disabled"}


async def test_fallback_can_be_switched_off() -> None:
    settings = make_settings(ANTHROPIC_FALLBACKS="off")
    model = build_chat_model(settings, chat_purpose(settings))
    capture = _Capture()
    _wire(model, capture)

    await model.ainvoke([HumanMessage("Hallo")])

    assert "anthropic-beta" not in capture.headers
    assert "fallbacks" not in capture.body


async def test_structured_output_uses_native_json_schema_without_forced_tool() -> None:
    settings = make_settings()
    model = build_chat_model(settings, extraction_purpose(settings))
    capture = _Capture()
    _wire(model, capture)

    result = await model.with_structured_output(_Answer, method="json_schema", include_raw=True).ainvoke(
        [HumanMessage("Zahl?")]
    )

    body = capture.body
    assert result["parsed"] == _Answer(value=1)
    assert body["output_config"]["effort"] == "high"
    assert body["output_config"]["format"]["type"] == "json_schema"
    assert "tools" not in body
    assert "tool_choice" not in body
    assert body["fallbacks"] == "default"


@pytest.mark.parametrize("purpose", [chat_purpose, extraction_purpose])
def test_max_tokens_and_timeout_are_explicit(purpose: object) -> None:
    settings = make_settings()
    model = build_chat_model(settings, purpose(settings))  # type: ignore[operator]

    assert model.max_tokens is not None
    assert model.max_tokens <= 32_000
    assert model.default_request_timeout == 600.0
    assert model.max_retries == 2
