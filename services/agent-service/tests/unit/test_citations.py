from __future__ import annotations

import pytest

from app.ingest.citations import excerpt_found_on_page, match_key

PAGE = """Bilanz per 31.12.2025 (in TCHF)
Total Aktiven  19 204  18 950
Eigenkapital  7 912  7 433
Offene Devisen-
termingeschäfte über EUR 2.0 Mio. per 31.12.2025 – siehe Anhang Ziff. 7
Die «Reserven» betragen CHF 1’200’000."""


@pytest.mark.parametrize(
    "excerpt",
    [
        "Total Aktiven 19 204",
        "total aktiven     19 204",
        "Eigenkapital\n7 912",
        "Devisentermingeschäfte über EUR 2.0 Mio.",
        "per 31.12.2025 - siehe Anhang Ziff. 7",
        "Die \"Reserven\" betragen CHF 1'200'000.",
        "Total Aktiven 19 204 … Eigenkapital 7 912",
        "Total Aktiven 19 204 [...] Eigenkapital",
    ],
)
def test_excerpt_on_page_is_verified(excerpt: str) -> None:
    assert excerpt_found_on_page(excerpt, PAGE)


@pytest.mark.parametrize(
    "excerpt",
    [
        "Total Aktiven 21 000",
        "Eigenkapital 7 912 … Total Aktiven 19 204",  # fragments out of order
        "19 2",  # too short to prove anything
        "",
        "Umsatzerlös 28 410",
    ],
)
def test_excerpt_not_on_page_is_rejected(excerpt: str) -> None:
    assert not excerpt_found_on_page(excerpt, PAGE)


def test_match_key_normalises_typography_and_whitespace() -> None:
    assert match_key("Netto–Erlös «A»  1’0") == match_key('netto-erlös "a" 1\'0')
