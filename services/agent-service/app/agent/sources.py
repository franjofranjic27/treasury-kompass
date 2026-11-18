"""Source IDs for chat citations: unique per turn, sequential across all searches of the turn."""

from __future__ import annotations

import re
from collections.abc import Collection
from dataclasses import dataclass
from uuid import UUID

from app.rag.search import SearchHit

MAX_EXCERPT_CHARS = 800
_MARKER_GROUP = re.compile(r"\[\s*(S\d+(?:\s*[,;]\s*S\d+)*)\s*\]")
_SOURCE_ID = re.compile(r"S\d+")


@dataclass(frozen=True)
class Source:
    source_id: str
    chunk_id: UUID
    document_id: UUID
    document_name: str
    page: int
    excerpt: str
    score: float


class SourceRegistry:
    """Hands out S1, S2, … per distinct chunk. Not thread-safe; one registry per turn on one event loop."""

    def __init__(self) -> None:
        self._by_chunk: dict[UUID, Source] = {}

    def register(self, hit: SearchHit) -> tuple[Source, bool]:
        """Return the source for the hit and whether it is new in this turn."""
        existing = self._by_chunk.get(hit.chunk_id)
        if existing is not None:
            return existing, False
        source = Source(
            source_id=f"S{len(self._by_chunk) + 1}",
            chunk_id=hit.chunk_id,
            document_id=hit.document_id,
            document_name=hit.document_name,
            page=hit.page,
            excerpt=hit.content[:MAX_EXCERPT_CHARS],
            score=round(hit.score, 4),
        )
        self._by_chunk[hit.chunk_id] = source
        return source, True

    @property
    def sources(self) -> tuple[Source, ...]:
        return tuple(self._by_chunk.values())

    @property
    def source_ids(self) -> frozenset[str]:
        return frozenset(source.source_id for source in self._by_chunk.values())


def cited_source_ids(text: str, known_ids: Collection[str]) -> list[str]:
    """Markers like [S1] or [S1, S3] in order of first appearance; unknown IDs are dropped."""
    cited: list[str] = []
    for group in _MARKER_GROUP.finditer(text):
        for source_id in _SOURCE_ID.findall(group.group(1)):
            if source_id in known_ids and source_id not in cited:
                cited.append(source_id)
    return cited


def unknown_source_ids(text: str, known_ids: Collection[str]) -> list[str]:
    """Markers the model used without a matching search hit (logged as a warning, never shown as a source)."""
    found = {source_id for group in _MARKER_GROUP.finditer(text) for source_id in _SOURCE_ID.findall(group.group(1))}
    return sorted(found - set(known_ids))
