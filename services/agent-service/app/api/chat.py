"""`POST /agent/chat`: one turn as Server-Sent Events."""

from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends
from sse_starlette import EventSourceResponse, ServerSentEvent

from app.agent.sse import to_server_sent_event
from app.api.dependencies import get_services
from app.models.chat import ChatRequest, SsePayload
from app.services import Services

router = APIRouter(prefix="/agent", tags=["Chat"])

PING_INTERVAL_SECONDS = 15
SSE_HEADERS = {"Cache-Control": "no-cache", "X-Accel-Buffering": "no"}


async def _numbered(events: AsyncIterator[SsePayload]) -> AsyncIterator[ServerSentEvent]:
    sequence = 0
    async for payload in events:
        sequence += 1
        yield to_server_sent_event(payload, sequence)


@router.post("/chat", operation_id="chat", response_class=EventSourceResponse)
async def chat(body: ChatRequest, services: Annotated[Services, Depends(get_services)]) -> EventSourceResponse:
    # Client and conversation checks raise problem responses before the stream starts.
    prepared = await services.chat.prepare(body)
    return EventSourceResponse(
        _numbered(services.chat.stream(prepared)),
        headers=SSE_HEADERS,
        ping=PING_INTERVAL_SECONDS,
        ping_message_factory=lambda: ServerSentEvent(comment="ping"),
    )
