"""Maps LangGraph stream parts of one turn to the SSE events of the contract and collects what the turn stores."""

from __future__ import annotations

import json
import logging
import time
from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from typing import Final, cast
from uuid import UUID

from langchain_core.messages import AIMessage, AIMessageChunk, BaseMessage, ToolMessage
from pydantic import JsonValue, TypeAdapter
from sse_starlette import ServerSentEvent

from app.agent.graph import MODEL_NODE
from app.agent.tools import TOOL_LABELS, ToolSummary
from app.models.chat import (
    CitationEvent,
    DoneEvent,
    ErrorEvent,
    MetaEvent,
    ProposalEvent,
    SseEventName,
    SsePayload,
    TokenEvent,
    ToolCallEvent,
    ToolName,
    ToolResultEvent,
)
from app.models.conversations import ToolEvent

logger = logging.getLogger(__name__)

MAX_ARGUMENT_CHARS = 500
MAX_ERROR_SUMMARY_CHARS = 200
LIMIT_NOTICE = (
    "Ich habe die maximale Anzahl Arbeitsschritte für diese Anfrage erreicht. "
    "Bitte die Frage enger stellen oder aufteilen."
)

_EVENT_NAMES: Final[dict[type[SsePayload], SseEventName]] = {
    MetaEvent: SseEventName.META,
    TokenEvent: SseEventName.TOKEN,
    ToolCallEvent: SseEventName.TOOL_CALL,
    CitationEvent: SseEventName.CITATION,
    ProposalEvent: SseEventName.PROPOSAL,
    ToolResultEvent: SseEventName.TOOL_RESULT,
    DoneEvent: SseEventName.DONE,
    ErrorEvent: SseEventName.ERROR,
}
_JSON_VALUE = TypeAdapter(JsonValue)


def to_server_sent_event(payload: SsePayload, sequence: int) -> ServerSentEvent:
    """`event: <name>`, `id: <n>` and exactly one `data:` line of compact JSON."""
    return ServerSentEvent(
        event=_EVENT_NAMES[type(payload)].value,
        id=str(sequence),
        data=payload.model_dump_json(by_alias=True),
    )


@dataclass
class _PendingCall:
    name: ToolName
    started: float


@dataclass
class TurnStreamMapper:
    """Stateful per turn: tracks tool calls for durations and accumulates text, events and final messages."""

    turn_id: UUID
    limit_node: str
    text_parts: list[str] = field(default_factory=list)
    tool_events: list[ToolEvent] = field(default_factory=list)
    citations: list[CitationEvent] = field(default_factory=list)
    proposals: list[ProposalEvent] = field(default_factory=list)
    limit_reached: bool = False
    final_messages: list[BaseMessage] = field(default_factory=list)
    _pending: dict[str, _PendingCall] = field(default_factory=dict)
    _current_message_id: str | None = None

    @property
    def text(self) -> str:
        return "".join(self.text_parts)

    def map(self, part: Mapping[str, object]) -> list[SsePayload]:
        kind = part.get("type")
        data = part.get("data")
        if kind == "messages" and isinstance(data, tuple):
            return self._map_message_chunk(data)
        if kind == "updates" and isinstance(data, dict):
            return self._map_updates(data)
        if kind == "custom":
            return self._map_custom(data)
        if kind == "values" and isinstance(data, dict):
            messages = data.get("messages")
            if isinstance(messages, list):
                self.final_messages = messages
        return []

    def _map_message_chunk(self, data: tuple[object, ...]) -> list[SsePayload]:
        message, metadata = data
        if not isinstance(message, AIMessageChunk) or not isinstance(metadata, dict):
            return []
        if metadata.get("langgraph_node") != MODEL_NODE:
            return []
        delta = message.text
        if not delta:
            return []
        events: list[SsePayload] = []
        if message.id != self._current_message_id:
            self._current_message_id = message.id
            separator = self._separator()
            if separator:
                events.append(self._token(separator))
        events.append(self._token(delta))
        return events

    def _map_updates(self, data: dict[str, object]) -> list[SsePayload]:
        events: list[SsePayload] = []
        for node, update in data.items():
            if not isinstance(update, dict):
                continue
            for message in update.get("messages", []):
                if isinstance(message, AIMessage) and node == MODEL_NODE:
                    events.extend(self._tool_calls(message))
                elif isinstance(message, ToolMessage):
                    events.extend(self._tool_result(message))
                elif isinstance(message, AIMessage) and node == self.limit_node:
                    self.limit_reached = True
                    events.append(self._token(self._separator() + LIMIT_NOTICE))
        return events

    def _map_custom(self, data: object) -> list[SsePayload]:
        if isinstance(data, CitationEvent):
            self.citations.append(data)
            return [data]
        if isinstance(data, ProposalEvent):
            self.proposals.append(data)
            return [data]
        logger.warning("Unbekanntes Custom-Event im Stream: %s", type(data).__name__)
        return []

    def _tool_calls(self, message: AIMessage) -> list[SsePayload]:
        events: list[SsePayload] = []
        for call in message.tool_calls:
            name = call["name"]
            call_id = call.get("id") or ""
            if name not in TOOL_LABELS:
                # Not part of the contract (e.g. a hallucinated approve tool): nothing runs, nothing is shown.
                logger.warning("Modell rief unbekanntes Tool %s auf", name)
                continue
            tool_name = cast("ToolName", name)
            self._pending[call_id] = _PendingCall(name=tool_name, started=time.monotonic())
            events.append(
                ToolCallEvent(
                    turn_id=self.turn_id,
                    tool_call_id=call_id,
                    name=tool_name,
                    label=TOOL_LABELS[tool_name],
                    arguments={key: truncate_argument(value) for key, value in call["args"].items()},
                )
            )
        return events

    def _tool_result(self, message: ToolMessage) -> list[SsePayload]:
        pending = self._pending.pop(message.tool_call_id, None)
        if pending is None:
            return []
        status = "error" if message.status == "error" else "ok"
        summary = (
            message.artifact.summary
            if isinstance(message.artifact, ToolSummary)
            else _shorten(message.text, MAX_ERROR_SUMMARY_CHARS)
        )
        self.tool_events.append(
            ToolEvent(
                tool_call_id=message.tool_call_id,
                name=pending.name,
                label=TOOL_LABELS[pending.name],
                status=status,
                summary=summary,
            )
        )
        return [
            ToolResultEvent(
                turn_id=self.turn_id,
                tool_call_id=message.tool_call_id,
                name=pending.name,
                status=status,
                summary=summary,
                duration_ms=round((time.monotonic() - pending.started) * 1000),
            )
        ]

    def _token(self, delta: str) -> TokenEvent:
        self.text_parts.append(delta)
        return TokenEvent(turn_id=self.turn_id, delta=delta)

    def _separator(self) -> str:
        text = self.text
        if not text or text.endswith("\n\n"):
            return ""
        return "\n" if text.endswith("\n") else "\n\n"


def new_turn_messages(final_messages: Sequence[BaseMessage], input_count: int) -> list[BaseMessage]:
    """Messages produced in this turn; the graph state is the input history plus appended messages."""
    if len(final_messages) < input_count:
        return []
    return list(final_messages[input_count:])


def truncate_argument(value: object) -> JsonValue:
    if isinstance(value, str):
        return _shorten(value, MAX_ARGUMENT_CHARS)
    serialized = json.dumps(value, ensure_ascii=False, default=str)
    if len(serialized) > MAX_ARGUMENT_CHARS:
        return _shorten(serialized, MAX_ARGUMENT_CHARS)
    return _JSON_VALUE.validate_python(json.loads(serialized))


def _shorten(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[: limit - 1] + "…"
