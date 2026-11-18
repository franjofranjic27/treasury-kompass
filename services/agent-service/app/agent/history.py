"""Conversations and turns (ADR-11). Turns are inserted once and never changed; only COMPLETED turns
go back to the model, with their LangChain messages (thinking blocks included) exactly as stored."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Literal
from uuid import UUID, uuid4

from langchain_core.messages import BaseMessage, messages_from_dict, messages_to_dict
from pydantic import TypeAdapter
from pydantic_core import to_jsonable_python
from sqlalchemy import delete, func, insert, select, update
from sqlalchemy.ext.asyncio import AsyncEngine

from app.db.tables import conversation, turn
from app.models.chat import ChatContext, ProposalEvent, Usage
from app.models.conversations import Conversation, ConversationDetail, ToolEvent, Turn, TurnCitation

TurnStatus = Literal["COMPLETED", "FAILED", "CANCELLED"]
TITLE_MAX_CHARS = 80

_CITATIONS = TypeAdapter(tuple[TurnCitation, ...])
_TOOL_EVENTS = TypeAdapter(tuple[ToolEvent, ...])
_PROPOSALS = TypeAdapter(tuple[ProposalEvent, ...])


@dataclass(frozen=True)
class ConversationRecord:
    id: UUID
    client_id: UUID
    title: str


@dataclass(frozen=True)
class TurnRecord:
    id: UUID
    conversation_id: UUID
    seq: int
    status: TurnStatus
    user_message: str
    assistant_message: str | None
    context: ChatContext
    lc_messages: Sequence[BaseMessage]
    citations: Sequence[TurnCitation]
    tool_events: Sequence[ToolEvent]
    proposals: Sequence[ProposalEvent]
    usage: Usage | None
    created_at: datetime
    completed_at: datetime


def conversation_title(message: str) -> str:
    collapsed = " ".join(message.split())
    return collapsed if len(collapsed) <= TITLE_MAX_CHARS else collapsed[: TITLE_MAX_CHARS - 1] + "…"


class ConversationRepository:
    def __init__(self, engine: AsyncEngine) -> None:
        self._engine = engine

    async def create(self, *, client_id: UUID, title: str) -> ConversationRecord:
        now = datetime.now(UTC)
        record = ConversationRecord(id=uuid4(), client_id=client_id, title=title)
        async with self._engine.begin() as connection:
            await connection.execute(
                insert(conversation).values(
                    id=record.id, client_id=client_id, title=title, created_at=now, updated_at=now
                )
            )
        return record

    async def get(self, conversation_id: UUID) -> ConversationRecord | None:
        query = select(conversation.c.id, conversation.c.client_id, conversation.c.title).where(
            conversation.c.id == conversation_id
        )
        async with self._engine.connect() as connection:
            row = (await connection.execute(query)).first()
        return ConversationRecord(id=row.id, client_id=row.client_id, title=row.title) if row else None

    async def next_seq(self, conversation_id: UUID) -> int:
        query = select(func.coalesce(func.max(turn.c.seq), 0)).where(turn.c.conversation_id == conversation_id)
        async with self._engine.connect() as connection:
            current = (await connection.execute(query)).scalar_one()
        return int(current) + 1

    async def load_history(self, conversation_id: UUID) -> list[BaseMessage]:
        query = (
            select(turn.c.lc_messages)
            .where(turn.c.conversation_id == conversation_id, turn.c.status == "COMPLETED")
            .order_by(turn.c.seq)
        )
        async with self._engine.connect() as connection:
            rows = (await connection.execute(query)).all()
        return [message for row in rows for message in messages_from_dict(row.lc_messages)]

    async def append_turn(self, record: TurnRecord) -> None:
        async with self._engine.begin() as connection:
            await connection.execute(
                insert(turn).values(
                    id=record.id,
                    conversation_id=record.conversation_id,
                    seq=record.seq,
                    status=record.status,
                    user_message=record.user_message,
                    assistant_message=record.assistant_message,
                    context=record.context.to_json_dict(),
                    lc_messages=to_jsonable_python(messages_to_dict(list(record.lc_messages))),
                    citations=[citation.to_json_dict() for citation in record.citations],
                    tool_events=[event.to_json_dict() for event in record.tool_events],
                    proposals=[proposal.to_json_dict() for proposal in record.proposals],
                    usage=record.usage.to_json_dict() if record.usage else None,
                    created_at=record.created_at,
                    completed_at=record.completed_at,
                )
            )
            await connection.execute(
                update(conversation)
                .where(conversation.c.id == record.conversation_id)
                .values(updated_at=record.completed_at)
            )

    async def list_for_client(self, client_id: UUID) -> list[Conversation]:
        turn_count = (
            select(func.count()).where(turn.c.conversation_id == conversation.c.id).scalar_subquery().label("turns")
        )
        query = (
            select(conversation, turn_count)
            .where(conversation.c.client_id == client_id)
            .order_by(conversation.c.updated_at.desc())
        )
        async with self._engine.connect() as connection:
            rows = (await connection.execute(query)).all()
        return [
            Conversation(
                id=row.id,
                client_id=row.client_id,
                title=row.title,
                created_at=row.created_at,
                updated_at=row.updated_at,
                turn_count=row.turns,
            )
            for row in rows
        ]

    async def detail(self, conversation_id: UUID) -> ConversationDetail | None:
        async with self._engine.connect() as connection:
            head = (await connection.execute(select(conversation).where(conversation.c.id == conversation_id))).first()
            if head is None:
                return None
            rows = (
                await connection.execute(
                    select(turn).where(turn.c.conversation_id == conversation_id).order_by(turn.c.seq)
                )
            ).all()
        turns = tuple(
            Turn(
                id=row.id,
                seq=row.seq,
                status=row.status,
                user_message=row.user_message,
                assistant_message=row.assistant_message,
                context=ChatContext.model_validate(row.context) if row.context else None,
                citations=_CITATIONS.validate_python(row.citations),
                tool_events=_TOOL_EVENTS.validate_python(row.tool_events),
                proposals=_PROPOSALS.validate_python(row.proposals),
                created_at=row.created_at,
                completed_at=row.completed_at,
            )
            for row in rows
        )
        return ConversationDetail(
            id=head.id,
            client_id=head.client_id,
            title=head.title,
            created_at=head.created_at,
            updated_at=head.updated_at,
            turn_count=len(turns),
            turns=turns,
        )

    async def delete(self, conversation_id: UUID) -> bool:
        async with self._engine.begin() as connection:
            result = await connection.execute(delete(conversation).where(conversation.c.id == conversation_id))
        return result.rowcount > 0
