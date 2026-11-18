"""One chat turn: checks before the stream, the agent run as its own task, persistence of the turn.

The run is decoupled from the SSE generator through a queue. When the browser disconnects, the generator
cancels the run task, which then stores the turn as CANCELLED; a turn timeout or model failure ends with an
`error` event and a FAILED turn. Only COMPLETED turns are replayed to the model later.
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Final
from uuid import UUID, uuid4
from weakref import WeakValueDictionary

import anthropic
from langchain_core.exceptions import ModelError
from langchain_core.messages import AIMessage, BaseMessage, HumanMessage

from app.agent.graph import ChatAgent
from app.agent.history import ConversationRecord, ConversationRepository, TurnRecord, TurnStatus, conversation_title
from app.agent.prompts import chat_user_message
from app.agent.sources import cited_source_ids, unknown_source_ids
from app.agent.sse import TurnStreamMapper, new_turn_messages
from app.agent.tools import TurnContext
from app.background import BackgroundTasks
from app.clients.treasury import TreasuryClient, TreasuryError, TreasuryNotFoundError, TreasuryUnavailableError
from app.models.chat import (
    ChatErrorCode,
    ChatRequest,
    DoneEvent,
    ErrorEvent,
    FinishReason,
    MetaEvent,
    SsePayload,
    Usage,
)
from app.models.conversations import TurnCitation
from app.models.treasury import ClientInfo, ProposalOutcome
from app.problems import ProblemCode, ProblemError

logger = logging.getLogger(__name__)

STREAM_MODES: Final = ["messages", "updates", "custom", "values"]

_ERROR_MESSAGES: Final[dict[ChatErrorCode, str]] = {
    ChatErrorCode.LLM_UNAVAILABLE: "Das Sprachmodell ist zurzeit nicht erreichbar. Bitte später erneut versuchen.",
    ChatErrorCode.LLM_REFUSAL: "Das Sprachmodell hat die Anfrage abgelehnt. Bitte die Frage anders formulieren.",
    ChatErrorCode.UPSTREAM_UNAVAILABLE: "Der treasury-service ist zurzeit nicht erreichbar.",
    ChatErrorCode.TIMEOUT: "Die Antwort hat zu lange gedauert und wurde abgebrochen. Bitte erneut versuchen.",
    ChatErrorCode.INTERNAL_ERROR: "Unerwarteter Fehler. Bitte erneut versuchen.",
}
_RETRYABLE: Final[frozenset[ChatErrorCode]] = frozenset(
    {ChatErrorCode.LLM_UNAVAILABLE, ChatErrorCode.UPSTREAM_UNAVAILABLE, ChatErrorCode.TIMEOUT}
)


@dataclass(frozen=True)
class PreparedTurn:
    request: ChatRequest
    client: ClientInfo
    conversation: ConversationRecord


@dataclass(frozen=True)
class TurnSettings:
    model_name: str
    max_proposals: int
    timeout_seconds: float


class _StreamEnd:
    """Queue sentinel: the run task is finished."""


_END: Final = _StreamEnd()


@dataclass
class _Outcome:
    status: TurnStatus
    terminal: SsePayload


class ChatTurnService:
    def __init__(
        self,
        *,
        agent: ChatAgent,
        treasury: TreasuryClient,
        conversations: ConversationRepository,
        tasks: BackgroundTasks,
        settings: TurnSettings,
    ) -> None:
        self._agent = agent
        self._treasury = treasury
        self._conversations = conversations
        self._tasks = tasks
        self._settings = settings
        # Turns of one conversation run one after another, so each sees the complete history.
        self._locks: WeakValueDictionary[UUID, asyncio.Lock] = WeakValueDictionary()

    async def prepare(self, request: ChatRequest) -> PreparedTurn:
        """Everything that may still fail with an HTTP problem, before the SSE stream starts."""
        client = await self._client(request.client_id)
        if request.conversation_id is None:
            record = await self._conversations.create(client_id=client.id, title=conversation_title(request.message))
        else:
            existing = await self._conversations.get(request.conversation_id)
            if existing is None or existing.client_id != client.id:
                raise ProblemError(ProblemCode.NOT_FOUND, "Gespräch nicht gefunden.")
            record = existing
        return PreparedTurn(request=request, client=client, conversation=record)

    async def stream(self, prepared: PreparedTurn) -> AsyncIterator[SsePayload]:
        queue: asyncio.Queue[SsePayload | _StreamEnd] = asyncio.Queue()
        run = self._tasks.spawn(self._run(prepared, queue), name=f"turn-{prepared.conversation.id}")
        try:
            while True:
                item = await queue.get()
                if isinstance(item, _StreamEnd):
                    return
                yield item
        finally:
            if not run.done():
                run.cancel()

    async def _client(self, client_id: UUID) -> ClientInfo:
        try:
            return await self._treasury.get_client_info(client_id)
        except TreasuryNotFoundError as error:
            raise ProblemError(ProblemCode.NOT_FOUND, "Kunde nicht gefunden.") from error
        except TreasuryError as error:
            raise ProblemError(ProblemCode.UPSTREAM_UNAVAILABLE, "treasury-service nicht erreichbar.") from error

    def _lock(self, conversation_id: UUID) -> asyncio.Lock:
        lock = self._locks.get(conversation_id)
        if lock is None:
            lock = asyncio.Lock()
            self._locks[conversation_id] = lock
        return lock

    async def _run(self, prepared: PreparedTurn, queue: asyncio.Queue[SsePayload | _StreamEnd]) -> None:
        try:
            async with self._lock(prepared.conversation.id):
                await self._run_locked(prepared, queue)
        finally:
            queue.put_nowait(_END)

    async def _run_locked(self, prepared: PreparedTurn, queue: asyncio.Queue[SsePayload | _StreamEnd]) -> None:
        request = prepared.request
        conversation_id = prepared.conversation.id
        seq = await self._conversations.next_seq(conversation_id)
        history = await self._conversations.load_history(conversation_id)
        turn_id = uuid4()
        created_at = datetime.now(UTC)
        queue.put_nowait(
            MetaEvent(
                conversation_id=conversation_id,
                turn_id=turn_id,
                seq=seq,
                model=self._settings.model_name,
                created_at=created_at,
            )
        )
        context = TurnContext(
            client_id=prepared.client.id,
            conversation_id=conversation_id,
            turn_id=turn_id,
            advisor_message=request.message,
            max_proposals=self._settings.max_proposals,
        )
        human = HumanMessage(
            content=chat_user_message(
                client_name=prepared.client.name,
                screen=request.context.screen,
                question_key=request.context.question_key,
                currency=request.context.currency,
                message=request.message,
            )
        )
        input_messages = [*history, human]
        mapper = TurnStreamMapper(turn_id=turn_id, limit_node=self._agent.limit_node)
        try:
            outcome = await self._execute(input_messages, context, mapper, queue, conversation_id)
        except asyncio.CancelledError:
            await self._persist(prepared, seq, turn_id, created_at, "CANCELLED", input_messages, mapper, context)
            raise
        queue.put_nowait(outcome.terminal)
        await self._persist(prepared, seq, turn_id, created_at, outcome.status, input_messages, mapper, context)

    async def _execute(
        self,
        input_messages: Sequence[BaseMessage],
        context: TurnContext,
        mapper: TurnStreamMapper,
        queue: asyncio.Queue[SsePayload | _StreamEnd],
        conversation_id: UUID,
    ) -> _Outcome:
        try:
            async with asyncio.timeout(self._settings.timeout_seconds):
                async for part in self._agent.graph.astream(
                    {"messages": list(input_messages)},
                    context=context,
                    stream_mode=STREAM_MODES,
                    version="v2",
                ):
                    for event in mapper.map(part):
                        queue.put_nowait(event)
        except TimeoutError:
            logger.warning("Chat-Turn %s: Zeitüberschreitung", context.turn_id)
            return self._failure(context.turn_id, ChatErrorCode.TIMEOUT)
        except (ModelError, anthropic.APIError):
            logger.warning("Chat-Turn %s: Sprachmodell nicht verfügbar", context.turn_id, exc_info=True)
            return self._failure(context.turn_id, ChatErrorCode.LLM_UNAVAILABLE)
        except TreasuryUnavailableError:
            return self._failure(context.turn_id, ChatErrorCode.UPSTREAM_UNAVAILABLE)
        except Exception:
            # Task boundary: the stream must always end with exactly one terminal event.
            logger.exception("Chat-Turn %s: unerwarteter Fehler", context.turn_id)
            return self._failure(context.turn_id, ChatErrorCode.INTERNAL_ERROR)
        return self._completion(input_messages, context, mapper, conversation_id)

    def _completion(
        self,
        input_messages: Sequence[BaseMessage],
        context: TurnContext,
        mapper: TurnStreamMapper,
        conversation_id: UUID,
    ) -> _Outcome:
        produced = new_turn_messages(mapper.final_messages, len(input_messages))
        finish_reason = _finish_reason(produced, limit_reached=mapper.limit_reached)
        if finish_reason is FinishReason.REFUSAL:
            # A refused exchange must not enter the history replayed to the model.
            logger.warning("Chat-Turn %s: Modell hat abgelehnt", context.turn_id)
            return self._failure(context.turn_id, ChatErrorCode.LLM_REFUSAL)
        known = context.sources.source_ids
        unknown = unknown_source_ids(mapper.text, known)
        if unknown:
            logger.warning("Chat-Turn %s: Marker ohne Fundstelle %s", context.turn_id, unknown)
        done = DoneEvent(
            turn_id=context.turn_id,
            conversation_id=conversation_id,
            finish_reason=finish_reason,
            citations_used=tuple(cited_source_ids(mapper.text, known)),
            proposals_created=sum(1 for p in mapper.proposals if p.outcome is not ProposalOutcome.REJECTED),
            usage=_usage(produced),
        )
        return _Outcome(status="COMPLETED", terminal=done)

    def _failure(self, turn_id: UUID, code: ChatErrorCode) -> _Outcome:
        return _Outcome(
            status="FAILED",
            terminal=ErrorEvent(
                turn_id=turn_id, code=code, message=_ERROR_MESSAGES[code], retryable=code in _RETRYABLE
            ),
        )

    async def _persist(
        self,
        prepared: PreparedTurn,
        seq: int,
        turn_id: UUID,
        created_at: datetime,
        status: TurnStatus,
        input_messages: Sequence[BaseMessage],
        mapper: TurnStreamMapper,
        context: TurnContext,
    ) -> None:
        produced = new_turn_messages(mapper.final_messages, len(input_messages))
        human = input_messages[-1]
        cited = set(cited_source_ids(mapper.text, context.sources.source_ids))
        record = TurnRecord(
            id=turn_id,
            conversation_id=prepared.conversation.id,
            seq=seq,
            status=status,
            user_message=prepared.request.message,
            assistant_message=mapper.text or None,
            context=prepared.request.context,
            # The turn's own user message (with context block) first, then everything the agent produced.
            lc_messages=[human, *produced],
            citations=[
                TurnCitation(
                    source_id=source.source_id,
                    document_id=source.document_id,
                    document_name=source.document_name,
                    page=source.page,
                    excerpt=source.excerpt,
                    used=source.source_id in cited,
                )
                for source in context.sources.sources
            ],
            tool_events=mapper.tool_events,
            proposals=mapper.proposals,
            usage=_usage(produced),
            created_at=created_at,
            completed_at=datetime.now(UTC),
        )
        try:
            await self._conversations.append_turn(record)
        except Exception:
            # The stream already ended for the advisor; losing the record must at least be visible in the log.
            logger.exception("Chat-Turn %s (%s) konnte nicht gespeichert werden", turn_id, status)


def _finish_reason(produced: Sequence[BaseMessage], *, limit_reached: bool) -> FinishReason:
    if limit_reached:
        return FinishReason.LIMIT_REACHED
    model_messages = [message for message in produced if isinstance(message, AIMessage)]
    stop_reason = model_messages[-1].response_metadata.get("stop_reason") if model_messages else None
    if stop_reason == "refusal":
        return FinishReason.REFUSAL
    if stop_reason == "max_tokens":
        return FinishReason.MAX_TOKENS
    return FinishReason.END_TURN


def _usage(produced: Sequence[BaseMessage]) -> Usage:
    input_tokens = output_tokens = cache_read = 0
    for message in produced:
        if isinstance(message, AIMessage) and message.usage_metadata:
            input_tokens += message.usage_metadata.get("input_tokens", 0)
            output_tokens += message.usage_metadata.get("output_tokens", 0)
            details = message.usage_metadata.get("input_token_details") or {}
            cache_read += details.get("cache_read") or 0
    return Usage(input_tokens=input_tokens, output_tokens=output_tokens, cache_read_input_tokens=cache_read)
