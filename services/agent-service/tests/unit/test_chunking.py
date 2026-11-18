from __future__ import annotations

from itertools import pairwise

from app.ingest.chunking import chunk_pages
from app.rag.store import PageText


def _long_page(page: int, sentences: int) -> PageText:
    text = " ".join(
        f"Satz {index} auf Seite {page} über Devisentermingeschäfte und Liquidität." for index in range(sentences)
    )
    return PageText(page=page, text=text)


def test_chunks_never_span_pages() -> None:
    pages = [_long_page(1, 60), _long_page(2, 5), _long_page(3, 80)]

    chunks = chunk_pages(pages, chunk_size=400, chunk_overlap=50)

    by_page = {page.page: page.text for page in pages}
    assert {chunk.page for chunk in chunks} == {1, 2, 3}
    for chunk in chunks:
        assert chunk.content in by_page[chunk.page]
        assert f"Seite {chunk.page} " in chunk.content
        assert len(chunk.content) <= 400


def test_chunk_index_runs_across_the_document_in_page_order() -> None:
    pages = [_long_page(2, 30), _long_page(1, 30)]

    chunks = chunk_pages(pages, chunk_size=300, chunk_overlap=30)

    assert [chunk.chunk_index for chunk in chunks] == list(range(len(chunks)))
    assert [chunk.page for chunk in chunks] == sorted(chunk.page for chunk in chunks)


def test_blank_pages_produce_no_chunks() -> None:
    chunks = chunk_pages([PageText(page=1, text="   \n  "), PageText(page=2, text="Bilanz per 31.12.2025")])

    assert [(chunk.page, chunk.content) for chunk in chunks] == [(2, "Bilanz per 31.12.2025")]


def test_long_pages_overlap_between_chunks() -> None:
    chunks = chunk_pages([_long_page(1, 40)], chunk_size=300, chunk_overlap=80)

    assert len(chunks) > 2
    overlapping = sum(1 for first, second in pairwise(chunks) if first.content[-20:] in second.content)
    assert overlapping > 0
