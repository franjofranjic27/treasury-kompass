from __future__ import annotations

import pytest

from app.ingest.pdf import NoTextLayerError, PdfParseError, clean_page_text, extract_pages
from tests.support.pdf import build_pdf


def test_extracts_text_per_page_with_umlauts() -> None:
    data = build_pdf(
        [
            ["Lagebericht der Muster Maschinenbau AG", "Das Geschäftsjahr verlief erfreulich für alle Märkte."],
            [],
            ["Bilanz per 31.12.2025 (in TCHF)", "Total Aktiven            19 204", "Flüssige Mittel   6 412"],
        ]
    )

    pages = extract_pages(data)

    assert [page.page for page in pages] == [1, 2, 3]
    assert "Geschäftsjahr" in pages[0].text
    assert pages[1].text == ""
    assert "Total Aktiven  19 204" in pages[2].text
    assert "Flüssige Mittel" in pages[2].text


def test_scan_without_text_layer_is_detected() -> None:
    with pytest.raises(NoTextLayerError):
        extract_pages(build_pdf([[], [], ["S. 3"]]))


def test_garbage_is_a_parse_error() -> None:
    with pytest.raises(PdfParseError):
        extract_pages(b"%PDF-1.7\nkein echtes PDF" + bytes(range(256)))


def test_clean_page_text_keeps_column_gap_and_drops_nul() -> None:
    raw = "Total Aktiven\x00          19 204      18 950   \r\n\n\n\n\nEigenkapital   7 912"

    assert clean_page_text(raw) == "Total Aktiven  19 204  18 950\n\nEigenkapital  7 912"
