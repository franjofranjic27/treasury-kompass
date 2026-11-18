"""Geschäftsbericht 2025 der fiktiven Beispiel Components AG (86 Seiten, Konzern in Mio. CHF).

Fundstellen wie im Prototyp (reference/prototype/treasury-kompass.html, CLIENTS.mid):
S. 34 Group Treasury, S. 41 Segmentbericht Währungen, S. 51 Konzernbilanz,
S. 52 Konzernerfolgsrechnung, S. 61 Anhang Ziff. 9 (Derivate), S. 66 Anhang Ziff. 14 (SARON).
"""

from __future__ import annotations

from reportlab.lib import colors

from . import (
    components_company,
    components_data,
    components_financials,
    components_reports,
    components_sustainability,
)
from .layout import DocumentSpec, PageOverflowError, Theme, require_on_page

FILENAME = "Geschaeftsbericht_2025_Beispiel_Components.pdf"
PAGE_COUNT = 86


def build() -> DocumentSpec:
    components_data.check_figures()
    pages = (
        *components_company.pages(),
        *components_reports.pages(),
        *components_financials.pages(),
        *components_sustainability.pages(),
    )
    if len(pages) != PAGE_COUNT:
        raise PageOverflowError(f"{FILENAME}: {len(pages)} Seiten statt {PAGE_COUNT} wie im Prototyp")
    spec = DocumentSpec(
        filename=FILENAME,
        title=f"Geschäftsbericht 2025 – {components_data.COMPANY} (fiktives Beispiel)",
        subject="Fiktives Beispiel – Hackathon Treasury Kompass: Geschäftsbericht eines Mid-Caps",
        running_header=f"{components_data.COMPANY} · Geschäftsbericht 2025",
        theme=Theme(accent=colors.HexColor("#0F5C5C")),
        pages=pages,
    )
    _check_fact_pages(spec)
    return spec


def _check_fact_pages(spec: DocumentSpec) -> None:
    """Fundstellen laut Prototyp und samples/README.md."""
    require_on_page(spec, 34, "Finanzmanagement und Treasury-Organisation", "Group Treasury mit 3 Vollzeitstellen")
    require_on_page(spec, 41, "Umsatz und Einkauf nach Währungen", "EUR (Mio. EUR) 85.4 33.0 +52.4",
                    "USD (Mio. USD) 33.0 12.0 +21.0")
    require_on_page(spec, 51, "Konzernbilanz per 31. Dezember 2025", "Flüssige Mittel 28.0",
                    "Total Aktiven 112.0", "Total Eigenkapital 46.0")
    require_on_page(spec, 52, "Konzernerfolgsrechnung 2025", "Nettoerlöse aus Lieferungen und Leistungen 146.0",
                    "Total Betriebsaufwand -133.2", "Betriebsergebnis (EBIT) 12.8")
    require_on_page(spec, 61, "9. Derivative Finanzinstrumente", "Devisentermingeschäfte 38.0",
                    "Devisenoptionen (Zero-Cost-Collars) 12.0")
    require_on_page(spec, 66, "14. Finanzverbindlichkeiten", "Konsortialkredit, variabel verzinst (Basis SARON) 25.0")
