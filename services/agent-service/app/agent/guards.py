"""Deterministic guards around the write tool; they hold even if the model follows injected instructions."""

from __future__ import annotations

from app.ingest.citations import match_key

MIN_REQUEST_QUOTE_KEY_LENGTH = 8


def advisor_explicitly_requested(advisor_request: str, advisor_message: str) -> bool:
    """The model must quote the advisor's current message verbatim.

    A proposal prompted by document content cannot pass: the quote would come from a tool
    result, not from what the advisor typed in this turn.
    """
    quote = match_key(advisor_request)
    return len(quote) >= MIN_REQUEST_QUOTE_KEY_LENGTH and quote in match_key(advisor_message)
