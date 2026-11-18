"""Citation check for extracted values: does the quoted excerpt really occur on the cited page?"""

from __future__ import annotations

import re
import unicodedata

MIN_EXCERPT_KEY_LENGTH = 6
_MIN_FRAGMENT_KEY_LENGTH = 3

_QUOTE_TRANSLATION = str.maketrans(
    {
        "‘": "'",
        "’": "'",
        "ʼ": "'",
        "´": "'",
        "`": "'",
        "′": "'",
        "«": '"',
        "»": '"',
        "„": '"',
        "“": '"',
        "”": '"',
        "‐": "-",
        "‑": "-",
        "‒": "-",
        "–": "-",
        "—": "-",
        "―": "-",
        "−": "-",
    }
)
_WHITESPACE = re.compile(r"\s+")
_LINE_BREAK_HYPHEN = re.compile(r"-[ \t]*\n\s*")
_ELLIPSIS = re.compile(r"…|\.{3,}|\[\.\.\.\]")


def match_key(text: str) -> str:
    """Comparison form: Unicode-normalised, case-folded, unified quotes and dashes, no whitespace.

    Whitespace is dropped entirely because layout extraction pads table columns
    differently from how a model quotes them ("Total Aktiven      19 204").
    """
    normalized = unicodedata.normalize("NFKC", text).translate(_QUOTE_TRANSLATION).casefold()
    return _WHITESPACE.sub("", normalized)


def excerpt_found_on_page(excerpt: str, page_text: str) -> bool:
    """True if every fragment of the excerpt (split at ellipses) occurs on the page, in order."""
    fragments = [match_key(part) for part in _ELLIPSIS.split(excerpt)]
    fragments = [fragment for fragment in fragments if len(fragment) >= _MIN_FRAGMENT_KEY_LENGTH]
    if sum(len(fragment) for fragment in fragments) < MIN_EXCERPT_KEY_LENGTH:
        return False
    candidates = (match_key(page_text), match_key(_LINE_BREAK_HYPHEN.sub("", page_text)))
    return any(_fragments_in_order(fragments, candidate) for candidate in candidates)


def _fragments_in_order(fragments: list[str], haystack: str) -> bool:
    position = 0
    for fragment in fragments:
        found = haystack.find(fragment, position)
        if found < 0:
            return False
        position = found + len(fragment)
    return True
