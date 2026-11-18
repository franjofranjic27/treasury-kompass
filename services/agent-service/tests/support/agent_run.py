"""Run one turn of the real agent graph without database or HTTP layer."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field
from uuid import UUID, uuid4

import httpx
import respx
from langchain_core.messages import HumanMessage
from pydantic import SecretStr

from app.agent.graph import ChatAgent, build_chat_agent
from app.agent.sse import TurnStreamMapper
from app.agent.tools import TurnContext, build_tools
from app.agent.turn import STREAM_MODES
from app.clients.treasury import RetryPolicy, TreasuryClient
from app.models.chat import SsePayload
from app.rag.search import SearchHit
from tests.support.fakes import ScriptedChatModel, Step
from tests.support.harness import INTERNAL_KEY, TREASURY_URL


@dataclass
class FakeSearch:
    hits: list[SearchHit] = field(default_factory=list)
    calls: list[dict[str, object]] = field(default_factory=list)

    async def search(self, *, client_id: UUID, query: str, k: int, document_id: UUID | None = None) -> list[SearchHit]:
        self.calls.append({"client_id": client_id, "query": query, "k": k, "document_id": document_id})
        return self.hits


@dataclass
class AgentRun:
    events: list[SsePayload]
    mapper: TurnStreamMapper
    model: ScriptedChatModel
    context: TurnContext
    agent: ChatAgent


async def run_turn(
    steps: Sequence[Step],
    *,
    advisor_message: str,
    router: respx.Router,
    search: FakeSearch | None = None,
    client_id: UUID | None = None,
    max_proposals: int = 5,
    max_model_calls: int = 8,
) -> AgentRun:
    model = ScriptedChatModel(steps=list(steps))
    http = httpx.AsyncClient(base_url=TREASURY_URL, transport=httpx.MockTransport(router.async_handler))
    treasury = TreasuryClient(http, SecretStr(INTERNAL_KEY), RetryPolicy(delays=(0.0, 0.0, 0.0)))
    agent = build_chat_agent(
        model,
        build_tools(treasury=treasury, search=search or FakeSearch(), top_k=6),  # type: ignore[arg-type]
        max_model_calls=max_model_calls,
        max_proposals=max_proposals,
    )
    context = TurnContext(
        client_id=client_id or uuid4(),
        conversation_id=uuid4(),
        turn_id=uuid4(),
        advisor_message=advisor_message,
        max_proposals=max_proposals,
    )
    mapper = TurnStreamMapper(turn_id=context.turn_id, limit_node=agent.limit_node)
    events: list[SsePayload] = []
    async for part in agent.graph.astream(
        {"messages": [HumanMessage(advisor_message)]}, context=context, stream_mode=STREAM_MODES, version="v2"
    ):
        events.extend(mapper.map(part))
    await http.aclose()
    return AgentRun(events=events, mapper=mapper, model=model, context=context, agent=agent)
