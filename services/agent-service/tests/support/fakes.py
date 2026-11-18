"""Fakes for the external AI services. No network, no API keys."""

from __future__ import annotations

import hashlib
import json
import math
import re
from collections.abc import AsyncIterator, Callable, Iterator, Sequence
from typing import Any
from uuid import uuid4

from langchain_core.callbacks import AsyncCallbackManagerForLLMRun, CallbackManagerForLLMRun
from langchain_core.language_models import BaseChatModel, LanguageModelInput
from langchain_core.messages import AIMessage, AIMessageChunk, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatGenerationChunk, ChatResult
from langchain_core.runnables import Runnable, RunnableLambda
from langchain_core.utils.function_calling import convert_to_openai_tool
from pydantic import Field

from app.db.tables import EMBEDDING_DIMENSION
from app.rag.embeddings import EmbeddingError

_WORD = re.compile(r"\w+", re.UNICODE)


class HashingEmbedder:
    """Deterministic bag-of-words vectors: cosine similarity follows word overlap, good enough for retrieval tests."""

    model_name = "test-hashing-1024"

    def __init__(self, *, fail: bool = False) -> None:
        self.fail = fail
        self.document_batches: list[list[str]] = []
        self.query_batches: list[list[str]] = []

    async def embed_documents(self, texts: Sequence[str]) -> list[list[float]]:
        self._raise_if_failing()
        self.document_batches.append(list(texts))
        return [embed_text(text) for text in texts]

    async def embed_queries(self, texts: Sequence[str]) -> list[list[float]]:
        self._raise_if_failing()
        self.query_batches.append(list(texts))
        return [embed_text(text) for text in texts]

    def _raise_if_failing(self) -> None:
        if self.fail:
            raise EmbeddingError("Voyage AI: simulierter Ausfall")


def embed_text(text: str) -> list[float]:
    vector = [0.0] * EMBEDDING_DIMENSION
    vector[0] = 0.01  # never a zero vector (cosine distance would be undefined)
    for word in _WORD.findall(text.casefold()):
        bucket = int(hashlib.sha256(word.encode("utf-8")).hexdigest()[:8], 16) % (EMBEDDING_DIMENSION - 1) + 1
        vector[bucket] += 1.0
    norm = math.sqrt(sum(value * value for value in vector))
    return [value / norm for value in vector]


Step = AIMessage | Exception | Callable[[list[BaseMessage]], AIMessage]


def ai(
    text: str = "",
    *,
    tool_calls: Sequence[tuple[str, dict[str, object]]] = (),
    content: list[dict[str, object]] | None = None,
    stop_reason: str | None = None,
) -> AIMessage:
    """Build a scripted model answer the way ChatAnthropic would return it."""
    calls = [{"name": name, "args": args, "id": f"toolu_{uuid4().hex[:12]}"} for name, args in tool_calls]
    reason = stop_reason or ("tool_use" if calls else "end_turn")
    return AIMessage(
        content=content if content is not None else text,
        tool_calls=calls,
        id=f"msg_{uuid4().hex[:12]}",
        response_metadata={"stop_reason": reason, "model_provider": "anthropic"},
        usage_metadata={
            "input_tokens": 100,
            "output_tokens": 20,
            "total_tokens": 120,
            "input_token_details": {"cache_read": 80},
        },
    )


class ScriptedChatModel(BaseChatModel):
    """Plays back scripted answers; records the messages and tool lists of every model call."""

    steps: list[Step] = Field(default_factory=list)
    calls: list[list[BaseMessage]] = Field(default_factory=list)
    bound_tools: list[list[str]] = Field(default_factory=list)
    bound_tool_choices: list[object] = Field(default_factory=list)
    chunk_size: int = 6

    @property
    def _llm_type(self) -> str:
        return "scripted"

    def bind_tools(
        self,
        tools: Sequence[Any],
        *,
        tool_choice: object = None,
        **kwargs: object,
    ) -> Runnable[LanguageModelInput, AIMessage]:
        self.bound_tools.append([convert_to_openai_tool(tool)["function"]["name"] for tool in tools])
        self.bound_tool_choices.append(tool_choice)
        return self  # type: ignore[return-value]

    def _next(self, messages: list[BaseMessage]) -> AIMessage:
        self.calls.append(list(messages))
        if not self.steps:
            raise AssertionError("ScriptedChatModel: kein weiterer Schritt im Skript")
        step = self.steps.pop(0)
        if isinstance(step, Exception):
            raise step
        if isinstance(step, AIMessage):
            return step
        return step(list(messages))

    def _generate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        return ChatResult(generations=[ChatGeneration(message=self._next(messages))])

    async def _agenerate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: AsyncCallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        return self._generate(messages, stop)

    def _stream(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> Iterator[ChatGenerationChunk]:
        yield from self._chunks(self._next(messages))

    async def _astream(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: AsyncCallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> AsyncIterator[ChatGenerationChunk]:
        for chunk in self._chunks(self._next(messages)):
            if run_manager is not None:
                await run_manager.on_llm_new_token(chunk.text, chunk=chunk)
            yield chunk

    def _chunks(self, message: AIMessage) -> Iterator[ChatGenerationChunk]:
        message_id = message.id or f"msg_{uuid4().hex[:12]}"
        if isinstance(message.content, str):
            text = message.content
            for start in range(0, len(text), self.chunk_size):
                yield ChatGenerationChunk(
                    message=AIMessageChunk(content=text[start : start + self.chunk_size], id=message_id)
                )
        else:
            yield ChatGenerationChunk(message=AIMessageChunk(content=list(message.content), id=message_id))
        yield ChatGenerationChunk(
            message=AIMessageChunk(
                content="" if isinstance(message.content, str) else [],
                id=message_id,
                tool_call_chunks=[
                    {"name": call["name"], "args": json.dumps(call["args"]), "id": call["id"], "index": index}
                    for index, call in enumerate(message.tool_calls)
                ],
                response_metadata=dict(message.response_metadata),
                usage_metadata=message.usage_metadata,
            )
        )


StructuredStep = dict[str, object] | Exception


class ScriptedStructuredModel(BaseChatModel):
    """Stands in for `with_structured_output(..., method="json_schema", include_raw=True)`."""

    results: list[StructuredStep] = Field(default_factory=list)
    calls: list[list[BaseMessage]] = Field(default_factory=list)
    structured_requests: list[dict[str, object]] = Field(default_factory=list)

    @property
    def _llm_type(self) -> str:
        return "scripted-structured"

    def _generate(
        self,
        messages: list[BaseMessage],
        stop: list[str] | None = None,
        run_manager: CallbackManagerForLLMRun | None = None,
        **kwargs: Any,
    ) -> ChatResult:
        raise AssertionError("Extraktion muss über with_structured_output laufen")

    def with_structured_output(  # type: ignore[override]
        self,
        schema: object,
        *,
        include_raw: bool = False,
        method: str = "function_calling",
        **kwargs: object,
    ) -> Runnable[LanguageModelInput, object]:
        self.structured_requests.append({"schema": schema, "include_raw": include_raw, "method": method})

        async def run(messages: list[BaseMessage]) -> object:
            self.calls.append(list(messages))
            if not self.results:
                raise AssertionError("ScriptedStructuredModel: kein Ergebnis im Skript")
            result = self.results.pop(0)
            if isinstance(result, Exception):
                raise result
            return result

        def run_sync(messages: list[BaseMessage]) -> object:
            raise AssertionError("nur asynchron")

        return RunnableLambda(run_sync, afunc=run)


def structured_result(parsed: object, *, stop_reason: str = "end_turn") -> dict[str, object]:
    raw = AIMessage(content="", response_metadata={"stop_reason": stop_reason})
    return {"raw": raw, "parsed": parsed, "parsing_error": None}
