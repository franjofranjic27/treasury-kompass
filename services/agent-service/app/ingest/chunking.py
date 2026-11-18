"""Page-faithful chunking: a chunk never spans two pages, so every hit has exactly one page to cite."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.rag.store import PageText

DEFAULT_CHUNK_SIZE = 1200
DEFAULT_CHUNK_OVERLAP = 150
_SEPARATORS = ["\n\n", "\n", ". ", " ", ""]


@dataclass(frozen=True)
class Chunk:
    page: int
    chunk_index: int
    content: str


def chunk_pages(
    pages: Sequence[PageText],
    *,
    chunk_size: int = DEFAULT_CHUNK_SIZE,
    chunk_overlap: int = DEFAULT_CHUNK_OVERLAP,
) -> list[Chunk]:
    """Split each page on its own; `chunk_index` runs across the whole document."""
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        separators=_SEPARATORS,
        strip_whitespace=True,
    )
    chunks: list[Chunk] = []
    for page in sorted(pages, key=lambda item: item.page):
        for content in splitter.split_text(page.text):
            if content.strip():
                chunks.append(Chunk(page=page.page, chunk_index=len(chunks), content=content))
    return chunks
