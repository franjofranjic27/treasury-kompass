"""Parse a `text/event-stream` body into events."""

from __future__ import annotations

import json
from dataclasses import dataclass


@dataclass(frozen=True)
class SseEvent:
    event: str
    id: str
    data: dict[str, object]
    data_lines: int


def parse_sse(body: str) -> list[SseEvent]:
    events: list[SseEvent] = []
    for block in body.replace("\r\n", "\n").split("\n\n"):
        fields: dict[str, str] = {}
        data_lines: list[str] = []
        for line in block.split("\n"):
            if not line or line.startswith(":"):
                continue
            name, _, value = line.partition(": ")
            if name == "data":
                data_lines.append(value)
            else:
                fields[name] = value
        if "event" in fields:
            events.append(
                SseEvent(
                    event=fields["event"],
                    id=fields.get("id", ""),
                    data=json.loads("\n".join(data_lines)),
                    data_lines=len(data_lines),
                )
            )
    return events


def names(events: list[SseEvent]) -> list[str]:
    return [event.event for event in events]
