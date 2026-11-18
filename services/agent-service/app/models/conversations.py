"""Conversation DTOs (`/agent/clients/{clientId}/conversations`, `/agent/conversations/{id}`)."""

from __future__ import annotations

from datetime import datetime
from typing import Literal
from uuid import UUID

from app.models.chat import ChatContext, ProposalEvent, ToolName
from app.models.common import CamelModel

TurnStatus = Literal["COMPLETED", "FAILED", "CANCELLED"]


class Conversation(CamelModel):
    id: UUID
    client_id: UUID
    title: str
    created_at: datetime
    updated_at: datetime
    turn_count: int


class TurnCitation(CamelModel):
    source_id: str
    document_id: UUID
    document_name: str
    page: int
    excerpt: str
    used: bool


class ToolEvent(CamelModel):
    tool_call_id: str
    name: ToolName
    label: str
    status: Literal["ok", "error"]
    summary: str


class Turn(CamelModel):
    id: UUID
    seq: int
    status: TurnStatus
    user_message: str
    assistant_message: str | None
    context: ChatContext | None
    citations: tuple[TurnCitation, ...]
    tool_events: tuple[ToolEvent, ...]
    proposals: tuple[ProposalEvent, ...]
    created_at: datetime
    completed_at: datetime | None


class ConversationDetail(Conversation):
    turns: tuple[Turn, ...]
