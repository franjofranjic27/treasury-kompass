from __future__ import annotations

import json
from uuid import uuid4

from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage, ToolMessage

from app.agent.sse import LIMIT_NOTICE, TurnStreamMapper, new_turn_messages, to_server_sent_event, truncate_argument
from app.agent.tools import ToolSummary
from app.models.chat import CitationEvent, DoneEvent, FinishReason, TokenEvent, ToolCallEvent, ToolResultEvent, Usage

TURN_ID = uuid4()
LIMIT_NODE = "ModelCallLimitMiddleware.before_model"


def _mapper() -> TurnStreamMapper:
    return TurnStreamMapper(turn_id=TURN_ID, limit_node=LIMIT_NODE)


def _chunk(text: str, message_id: str, node: str = "model") -> dict[str, object]:
    return {
        "type": "messages",
        "ns": (),
        "data": (AIMessageChunk(content=text, id=message_id), {"langgraph_node": node}),
    }


def test_tokens_come_only_from_the_model_node() -> None:
    mapper = _mapper()

    events = [
        *mapper.map(_chunk("Die Bilanz", "m1")),
        *mapper.map(_chunk("summe", "m1")),
        *mapper.map(
            {"type": "messages", "ns": (), "data": (ToolMessage("x", tool_call_id="t"), {"langgraph_node": "tools"})}
        ),
        *mapper.map(_chunk("ignoriert", "m9", node="SomeMiddleware.before_model")),
    ]

    assert [event.delta for event in events if isinstance(event, TokenEvent)] == ["Die Bilanz", "summe"]
    assert mapper.text == "Die Bilanzsumme"


def test_thinking_blocks_are_not_streamed() -> None:
    mapper = _mapper()
    chunk = AIMessageChunk(content=[{"type": "thinking", "thinking": "intern", "index": 0}], id="m1")

    assert mapper.map({"type": "messages", "ns": (), "data": (chunk, {"langgraph_node": "model"})}) == []


def test_text_of_a_new_model_call_is_separated() -> None:
    mapper = _mapper()
    mapper.map(_chunk("Ich suche.", "m1"))

    events = mapper.map(_chunk("Gefunden [S1].", "m2"))

    assert [event.delta for event in events if isinstance(event, TokenEvent)] == ["\n\n", "Gefunden [S1]."]


def test_tool_call_and_result_events() -> None:
    mapper = _mapper()
    call = AIMessage(
        content="",
        tool_calls=[
            {"name": "search_documents", "args": {"query": "Umsatz EUR " + "x" * 600}, "id": "toolu_1"},
            {"name": "approve_proposal", "args": {}, "id": "toolu_2"},
        ],
    )

    calls = mapper.map({"type": "updates", "ns": (), "data": {"model": {"messages": [call]}}})
    results = mapper.map(
        {
            "type": "updates",
            "ns": (),
            "data": {
                "tools": {
                    "messages": [
                        ToolMessage(
                            "…", tool_call_id="toolu_1", name="search_documents", artifact=ToolSummary("3 Fundstellen")
                        ),
                        ToolMessage(
                            "Error: approve_proposal is not a valid tool", tool_call_id="toolu_2", status="error"
                        ),
                    ]
                }
            },
        }
    )

    assert len(calls) == 1  # unknown tools are not part of the contract and are not shown
    [tool_call] = calls
    assert isinstance(tool_call, ToolCallEvent)
    assert (tool_call.name, tool_call.label) == ("search_documents", "Durchsuche Unterlagen")
    assert len(str(tool_call.arguments["query"])) == 500
    [result] = results
    assert isinstance(result, ToolResultEvent)
    assert (result.status, result.summary) == ("ok", "3 Fundstellen")
    assert result.duration_ms >= 0
    assert [event.summary for event in mapper.tool_events] == ["3 Fundstellen"]


def test_tool_error_uses_message_as_summary() -> None:
    mapper = _mapper()
    mapper.map(
        {
            "type": "updates",
            "ns": (),
            "data": {
                "model": {
                    "messages": [AIMessage(content="", tool_calls=[{"name": "get_assessment", "args": {}, "id": "t1"}])]
                }
            },
        }
    )

    [result] = mapper.map(
        {
            "type": "updates",
            "ns": (),
            "data": {
                "tools": {
                    "messages": [
                        ToolMessage(
                            "Der treasury-service ist zurzeit nicht erreichbar.", tool_call_id="t1", status="error"
                        )
                    ]
                }
            },
        }
    )

    assert isinstance(result, ToolResultEvent)
    assert result.status == "error"
    assert result.summary.startswith("Der treasury-service")


def test_custom_events_pass_through_and_are_collected() -> None:
    mapper = _mapper()
    citation = CitationEvent(
        turn_id=TURN_ID,
        source_id="S1",
        document_id=uuid4(),
        document_name="GB.pdf",
        page=41,
        excerpt="EUR 85.4",
        score=0.8,
    )

    assert mapper.map({"type": "custom", "ns": (), "data": citation}) == [citation]
    assert mapper.citations == [citation]


def test_call_limit_is_reported_in_german() -> None:
    mapper = _mapper()
    mapper.map(_chunk("Zwischenstand", "m1"))

    events = mapper.map(
        {
            "type": "updates",
            "ns": (),
            "data": {LIMIT_NODE: {"messages": [AIMessage("Model call limits exceeded")], "jump_to": "end"}},
        }
    )

    assert mapper.limit_reached
    assert isinstance(events[0], TokenEvent)
    assert events[0].delta == "\n\n" + LIMIT_NOTICE
    assert "exceeded" not in mapper.text


def test_final_messages_come_from_values() -> None:
    mapper = _mapper()
    history = [HumanMessage("alt"), AIMessage("alt")]
    produced = [AIMessage("neu")]

    mapper.map({"type": "values", "ns": (), "data": {"messages": [*history, HumanMessage("neu"), *produced]}})

    assert new_turn_messages(mapper.final_messages, input_count=3) == produced


def test_server_sent_event_has_name_id_and_one_compact_data_line() -> None:
    done = DoneEvent(
        turn_id=TURN_ID,
        conversation_id=uuid4(),
        finish_reason=FinishReason.END_TURN,
        citations_used=("S1",),
        proposals_created=0,
        usage=Usage(input_tokens=10, output_tokens=2, cache_read_input_tokens=None),
    )

    encoded = to_server_sent_event(done, 7).encode().decode()

    assert "event: done" in encoded
    assert "id: 7" in encoded
    [data_line] = [line for line in encoded.splitlines() if line.startswith("data: ")]
    payload = json.loads(data_line.removeprefix("data: "))
    assert payload["finishReason"] == "end_turn"
    assert payload["citationsUsed"] == ["S1"]
    assert payload["usage"] == {"inputTokens": 10, "outputTokens": 2, "cacheReadInputTokens": None}


def test_token_with_newlines_stays_on_one_data_line() -> None:
    encoded = to_server_sent_event(TokenEvent(turn_id=TURN_ID, delta="a\n\nb"), 1).encode().decode()

    assert len([line for line in encoded.splitlines() if line.startswith("data: ")]) == 1


def test_truncate_argument_keeps_small_structures() -> None:
    assert truncate_argument([{"currency": "EUR", "start_ratio": 80}]) == [{"currency": "EUR", "start_ratio": 80}]
    assert len(str(truncate_argument({"text": "y" * 900}))) == 500
