from __future__ import annotations

from uuid import uuid4

from app.agent.sources import SourceRegistry, cited_source_ids, unknown_source_ids
from app.rag.search import SearchHit


def _hit(page: int, content: str = "Text") -> SearchHit:
    return SearchHit(
        chunk_id=uuid4(),
        document_id=uuid4(),
        document_name="Geschaeftsbericht_2025_Beispiel_Components.pdf",
        page=page,
        chunk_index=page,
        content=content,
        score=0.83456,
    )


def test_registry_numbers_sources_and_deduplicates_chunks() -> None:
    registry = SourceRegistry()
    first, second = _hit(41), _hit(52)

    s1, new1 = registry.register(first)
    s2, new2 = registry.register(second)
    again, new_again = registry.register(first)

    assert (s1.source_id, new1) == ("S1", True)
    assert (s2.source_id, new2) == ("S2", True)
    assert (again.source_id, new_again) == ("S1", False)
    assert registry.source_ids == {"S1", "S2"}
    assert s1.score == 0.8346


def test_excerpt_is_cut_to_800_characters() -> None:
    source, _ = SourceRegistry().register(_hit(4, "x" * 2000))

    assert len(source.excerpt) == 800


def test_cited_ids_in_order_of_first_appearance_and_only_known() -> None:
    text = "Zuflüsse EUR 85.4 Mio. [S2], Abflüsse EUR 33.0 Mio. [S1, S2]. Erfunden: [S9]. Nochmals [S1]."

    assert cited_source_ids(text, {"S1", "S2"}) == ["S2", "S1"]
    assert unknown_source_ids(text, {"S1", "S2"}) == ["S9"]


def test_no_markers_means_no_citations() -> None:
    assert cited_source_ids("Die Unterlagen enthalten dazu nichts.", {"S1"}) == []
