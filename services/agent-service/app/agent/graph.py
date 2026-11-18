"""The chat agent: LangChain `create_agent` with fixed tools, a static system prompt and middleware (ADR-12)."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from langchain.agents import create_agent
from langchain.agents.middleware import ModelCallLimitMiddleware
from langchain_anthropic.middleware import AnthropicPromptCachingMiddleware
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import SystemMessage
from langchain_core.tools import BaseTool
from langgraph.graph.state import CompiledStateGraph

from app.agent.prompts import chat_system_prompt
from app.agent.tools import TurnContext

MODEL_NODE = "model"


@dataclass(frozen=True)
class ChatAgent:
    graph: CompiledStateGraph
    tool_names: tuple[str, ...]
    # Node that ends the run with an artificial message once the model call limit is hit.
    limit_node: str


def build_chat_agent(
    model: BaseChatModel,
    tools: Sequence[BaseTool],
    *,
    max_model_calls: int,
    max_proposals: int,
) -> ChatAgent:
    call_limit = ModelCallLimitMiddleware(run_limit=max_model_calls, exit_behavior="end")
    graph = create_agent(
        model,
        list(tools),
        system_prompt=SystemMessage(content=chat_system_prompt(max_proposals)),
        middleware=[
            # Breakpoints on system prompt and tools plus automatic caching of the growing history.
            AnthropicPromptCachingMiddleware(unsupported_model_behavior="ignore"),
            call_limit,
        ],
        context_schema=TurnContext,
    )
    return ChatAgent(
        graph=graph,
        tool_names=tuple(tool.name for tool in tools),
        limit_node=f"{call_limit.name}.before_model",
    )
