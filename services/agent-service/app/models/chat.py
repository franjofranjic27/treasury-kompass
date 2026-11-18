"""Chat request and SSE event payloads (docs/api/agent-service.openapi.yaml)."""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Literal
from uuid import UUID

from pydantic import Field, JsonValue, field_validator

from app.models.common import CamelModel, StrictCamelModel
from app.models.questions import AnswerValue
from app.models.treasury import ProposalOutcome

Screen = Literal["START", "QUESTIONNAIRE", "DASHBOARD", "ADVISORY"]
ToolName = Literal[
    "search_documents",
    "get_client_overview",
    "get_questionnaire",
    "get_assessment",
    "get_audit_trail",
    "get_rule_catalog",
    "simulate_assessment",
    "propose_answer",
]
MAX_MESSAGE_LENGTH = 4000


class ChatContext(StrictCamelModel):
    screen: Screen
    question_key: str | None = Field(default=None, max_length=64, pattern=r"^[a-z]+(\.[a-zA-Z]+)+$")
    currency: Literal["EUR", "USD"] | None = None


class ChatRequest(StrictCamelModel):
    client_id: UUID
    conversation_id: UUID | None = None
    message: str = Field(min_length=1, max_length=MAX_MESSAGE_LENGTH)
    context: ChatContext

    @field_validator("message")
    @classmethod
    def _not_blank(cls, value: str) -> str:
        if not value.strip():
            msg = "Nachricht darf nicht leer sein"
            raise ValueError(msg)
        return value


class SseEventName(StrEnum):
    META = "meta"
    TOKEN = "token"  # noqa: S105 - SSE event name, not a credential
    TOOL_CALL = "tool_call"
    CITATION = "citation"
    PROPOSAL = "proposal"
    TOOL_RESULT = "tool_result"
    DONE = "done"
    ERROR = "error"


class FinishReason(StrEnum):
    END_TURN = "end_turn"
    MAX_TOKENS = "max_tokens"
    REFUSAL = "refusal"
    LIMIT_REACHED = "limit_reached"


class ChatErrorCode(StrEnum):
    LLM_UNAVAILABLE = "llm-unavailable"
    LLM_REFUSAL = "llm-refusal"
    UPSTREAM_UNAVAILABLE = "upstream-unavailable"
    TIMEOUT = "timeout"
    INTERNAL_ERROR = "internal-error"


class MetaEvent(CamelModel):
    conversation_id: UUID
    turn_id: UUID
    seq: int
    model: str
    created_at: datetime


class TokenEvent(CamelModel):
    turn_id: UUID
    delta: str


class ToolCallEvent(CamelModel):
    turn_id: UUID
    tool_call_id: str
    name: ToolName
    label: str
    arguments: dict[str, JsonValue]


class ToolResultEvent(CamelModel):
    turn_id: UUID
    tool_call_id: str
    name: ToolName
    status: Literal["ok", "error"]
    summary: str
    duration_ms: int


class CitationEvent(CamelModel):
    turn_id: UUID
    source_id: str
    document_id: UUID
    document_name: str
    page: int
    excerpt: str
    score: float


class ProposalEvent(CamelModel):
    turn_id: UUID
    proposal_id: UUID | None
    question_key: str
    question_label: str
    value: AnswerValue
    value_label: str | None
    outcome: ProposalOutcome
    reason: str | None


class Usage(CamelModel):
    input_tokens: int = 0
    output_tokens: int = 0
    cache_read_input_tokens: int | None = None


class DoneEvent(CamelModel):
    turn_id: UUID
    conversation_id: UUID
    finish_reason: FinishReason
    citations_used: tuple[str, ...]
    proposals_created: int
    usage: Usage


class ErrorEvent(CamelModel):
    turn_id: UUID | None
    code: ChatErrorCode
    message: str
    retryable: bool


SsePayload = (
    MetaEvent | TokenEvent | ToolCallEvent | ToolResultEvent | CitationEvent | ProposalEvent | DoneEvent | ErrorEvent
)
