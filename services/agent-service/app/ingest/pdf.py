"""Page-wise text extraction with pypdf (layout mode keeps table columns apart)."""

from __future__ import annotations

import re
from io import BytesIO

from pypdf import PdfReader
from pypdf.errors import PyPdfError

from app.rag.store import PageText

# A page counts as having a text layer from this many letters or digits on.
MIN_ALNUM_CHARS_FOR_TEXT_LAYER = 40

_WIDE_GAP = re.compile(r"[ \t]{3,}")
_MANY_BLANK_LINES = re.compile(r"\n{3,}")
# pypdf raises these besides its own errors on malformed, untrusted input.
_PARSER_ERRORS = (PyPdfError, ValueError, KeyError, TypeError, IndexError, AttributeError, RecursionError)


class PdfParseError(Exception):
    """The bytes are not a readable PDF."""


class NoTextLayerError(Exception):
    """The PDF has no usable text layer (e.g. a scan without OCR)."""


def extract_pages(data: bytes) -> list[PageText]:
    """Return one cleaned text per page (1-based page numbers). Raises if there is no usable text at all."""
    try:
        reader = PdfReader(BytesIO(data))
        if reader.is_encrypted and not reader.decrypt(""):
            raise PdfParseError("PDF ist passwortgeschützt")
        pages = [
            PageText(page=number, text=clean_page_text(page.extract_text(extraction_mode="layout")))
            for number, page in enumerate(reader.pages, start=1)
        ]
    except _PARSER_ERRORS as error:
        raise PdfParseError(f"PDF nicht lesbar: {type(error).__name__}") from error
    if not pages:
        raise PdfParseError("PDF enthält keine Seiten")
    if not any(_alnum_count(page.text) >= MIN_ALNUM_CHARS_FOR_TEXT_LAYER for page in pages):
        raise NoTextLayerError("Kein Textlayer gefunden (Scan?)")
    return pages


def clean_page_text(raw: str) -> str:
    """Trim layout padding while keeping a visible gap between table columns."""
    text = raw.replace("\x00", "").replace("\r\n", "\n").replace("\r", "\n")
    lines = [_WIDE_GAP.sub("  ", line).rstrip() for line in text.split("\n")]
    return _MANY_BLANK_LINES.sub("\n\n", "\n".join(lines)).strip()


def _alnum_count(text: str) -> int:
    return sum(1 for char in text if char.isalnum())
