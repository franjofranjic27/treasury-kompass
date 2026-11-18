"""Jahresrechnung 2025 der fiktiven Muster Maschinenbau AG (24 Seiten, Beträge in CHF).

Fundstellen wie im Prototyp (reference/prototype/treasury-kompass.html, CLIENTS.kmu):
Bilanz S. 4, Erfolgsrechnung S. 5, Anhang Ziff. 3 S. 9 (EUR-Flüsse), Ziff. 5 S. 11 (SARON-Kredit),
Ziff. 7 S. 13 (Devisentermingeschäfte). Die Kennzahlen aus docs/regelwerk.md Abschnitt 9 stehen
als absolute Beträge nur auf diesen Seiten; Lagebericht und Ergänzungen nennen Veränderungen in %.
USD-Abflüsse fehlen bewusst: Sie stammen im Demo-Fall aus der Kreditorenliste des Kunden.
"""

from __future__ import annotations

from reportlab.lib import colors

from .layout import (
    Bullets,
    DocumentSpec,
    Grid,
    Heading,
    Page,
    Row,
    RowKind,
    Space,
    Statement,
    Text,
    TextStyle,
    Theme,
    require_on_page,
)
from .numbers import Pair, chf, growth, percent, ratio, require_equal, require_millions, shares, total

FILENAME = "Jahresrechnung_2025_Muster_Maschinenbau.pdf"
COMPANY = "Muster Maschinenbau AG"
PLACE = "Musterwil"
APPROVAL_DATE = "12. März 2026"

# --- Bilanz per 31.12. (2025, 2024) -------------------------------------------------------

CURRENT_ASSETS: dict[str, Pair] = {
    "Flüssige Mittel": (6_400_380, 5_512_640),
    "Forderungen aus Lieferungen und Leistungen": (3_915_270, 3_688_410),
    "Übrige kurzfristige Forderungen": (241_060, 198_730),
    "Vorräte und nicht fakturierte Dienstleistungen": (3_482_915, 3_610_220),
    "Aktive Rechnungsabgrenzungen": (126_540, 104_880),
}
FIXED_ASSETS: dict[str, Pair] = {
    "Finanzanlagen": (85_000, 85_000),
    "Mobile Sachanlagen": (2_164_045, 2_232_780),
    "Immobile Sachanlagen": (2_600_000, 2_700_000),
    "Immaterielle Werte": (185_000, 210_000),
}
SHORT_TERM_LIABILITIES: dict[str, Pair] = {
    "Verbindlichkeiten aus Lieferungen und Leistungen": (2_318_460, 2_187_350),
    "Anzahlungen von Kunden": (1_164_900, 1_021_600),
    "Übrige kurzfristige Verbindlichkeiten": (412_730, 396_210),
    "Passive Rechnungsabgrenzungen und kurzfristige Rückstellungen": (612_340, 588_460),
}
LONG_TERM_LIABILITIES: dict[str, Pair] = {
    "Langfristige verzinsliche Verbindlichkeiten": (6_000_000, 6_000_000),
    "Rückstellungen": (791_560, 808_560),
}
RETAINED_EARNINGS_CARRIED: Pair = (340_480, 747_000)
EQUITY_RESERVES: dict[str, Pair] = {
    "Aktienkapital": (1_000_000, 1_000_000),
    "Gesetzliche Gewinnreserve": (500_000, 500_000),
    "Freiwillige Gewinnreserven": (4_200_000, 3_600_000),
}

# --- Erfolgsrechnung (2025, 2024), Aufwände positiv erfasst ----------------------------------

REVENUE: Pair = (28_400_540, 27_105_310)
OPERATING_EXPENSES: dict[str, Pair] = {
    "Materialaufwand": (11_642_880, 11_210_540),
    "Personalaufwand": (10_318_450, 9_987_330),
    "Übriger betrieblicher Aufwand": (2_951_720, 2_884_150),
    "Abschreibungen und Wertberichtigungen auf Positionen des Anlagevermögens": (1_187_140, 1_142_600),
}
FINANCIAL_EXPENSE: Pair = (104_720, 112_380)
FINANCIAL_INCOME: Pair = (18_350, 9_640)
TAXES: Pair = (354_240, 284_470)

# --- Anhang ----------------------------------------------------------------------------------

SARON_LOAN = 6_000_000
EUR_INVOICED: Pair = (18_700_420, 17_934_100)
EUR_PURCHASED: Pair = (4_499_860, 4_286_500)
EUR_AVERAGE_RATE = ("0.9400", "0.9520")
SALES_BY_MARKET: dict[str, Pair] = {
    "Schweiz": (9_412_110, 9_188_450),
    "Deutschland": (9_986_230, 9_702_540),
    "Österreich": (3_204_870, 3_096_110),
    "Italien": (2_662_030, 2_549_800),
    "Übrige Länder Euroraum": (1_725_265, 1_724_813),
    "Übrige Länder": (1_410_035, 843_597),
}
EURO_MARKETS = ("Deutschland", "Österreich", "Italien", "Übrige Länder Euroraum")
FX_FORWARDS_EUR: Pair = (2_000_000, 1_500_000)
DIVIDEND = 1_300_000
ALLOCATION_TO_RESERVES = 500_000

# --- abgeleitete Werte und Prüfungen ---------------------------------------------------------

TOTAL_CURRENT_ASSETS = total(CURRENT_ASSETS.values())
TOTAL_FIXED_ASSETS = total(FIXED_ASSETS.values())
TOTAL_ASSETS = total([TOTAL_CURRENT_ASSETS, TOTAL_FIXED_ASSETS])
TOTAL_SHORT_TERM = total(SHORT_TERM_LIABILITIES.values())
TOTAL_LONG_TERM = total(LONG_TERM_LIABILITIES.values())
TOTAL_LIABILITIES = total([TOTAL_SHORT_TERM, TOTAL_LONG_TERM])
TOTAL_OPERATING_EXPENSES = total(OPERATING_EXPENSES.values())
EBIT: Pair = (REVENUE[0] - TOTAL_OPERATING_EXPENSES[0], REVENUE[1] - TOTAL_OPERATING_EXPENSES[1])
EBT: Pair = (
    EBIT[0] - FINANCIAL_EXPENSE[0] + FINANCIAL_INCOME[0],
    EBIT[1] - FINANCIAL_EXPENSE[1] + FINANCIAL_INCOME[1],
)
NET_INCOME: Pair = (EBT[0] - TAXES[0], EBT[1] - TAXES[1])
BALANCE_SHEET_PROFIT: Pair = (
    RETAINED_EARNINGS_CARRIED[0] + NET_INCOME[0],
    RETAINED_EARNINGS_CARRIED[1] + NET_INCOME[1],
)
TOTAL_EQUITY = total([*EQUITY_RESERVES.values(), BALANCE_SHEET_PROFIT])
CASH = CURRENT_ASSETS["Flüssige Mittel"]


def _check_figures() -> None:
    """Hält die Abschlüsse in sich stimmig und auf den Zielwerten aus docs/regelwerk.md."""
    for year in (0, 1):
        require_equal(f"Bilanz {2025 - year}", TOTAL_ASSETS[year], TOTAL_LIABILITIES[year] + TOTAL_EQUITY[year])
    require_millions("Umsatzerlös", REVENUE[0], "28.4")
    require_millions("Bilanzsumme", TOTAL_ASSETS[0], "19.2")
    require_millions("Eigenkapital", TOTAL_EQUITY[0], "7.9")
    require_millions("EBIT", EBIT[0], "2.3")
    require_millions("Flüssige Mittel", CASH[0], "6.4")
    require_millions("Betriebsaufwand", TOTAL_OPERATING_EXPENSES[0], "26.1")
    require_millions("Bankkredit SARON", SARON_LOAN, "6.0")
    require_millions("EUR-Zuflüsse", EUR_INVOICED[0], "18.7")
    require_millions("EUR-Abflüsse", EUR_PURCHASED[0], "4.5")
    require_equal("Umsatz nach Märkten", total(SALES_BY_MARKET.values()), REVENUE)
    for year, rate in ((0, 0.94), (1, 0.952)):
        euro_sales = sum(SALES_BY_MARKET[m][year] for m in EURO_MARKETS)
        require_equal(f"EUR-Umsatz in CHF {2025 - year}", euro_sales, round(EUR_INVOICED[year] * rate))
    require_equal("Gewinnvortrag 2025", RETAINED_EARNINGS_CARRIED[0],
                  BALANCE_SHEET_PROFIT[1] - DIVIDEND - (EQUITY_RESERVES["Freiwillige Gewinnreserven"][0]
                                                        - EQUITY_RESERVES["Freiwillige Gewinnreserven"][1]))
    require_equal("Geldfluss", sum(v for _, v in _cash_flow_rows()), CASH[0] - CASH[1])


def _delta(pair: Pair) -> int:
    return int(pair[0] - pair[1])


def _cash_flow_rows() -> list[tuple[str, int]]:
    depreciation = OPERATING_EXPENSES["Abschreibungen und Wertberichtigungen auf Positionen des Anlagevermögens"][0]
    return [
        ("Jahresgewinn", int(NET_INCOME[0])),
        ("Abschreibungen und Wertberichtigungen", int(depreciation)),
        ("Veränderung Rückstellungen", _delta(LONG_TERM_LIABILITIES["Rückstellungen"])),
        ("Veränderung Forderungen aus Lieferungen und Leistungen",
         -_delta(CURRENT_ASSETS["Forderungen aus Lieferungen und Leistungen"])),
        ("Veränderung übrige Forderungen und aktive Rechnungsabgrenzungen",
         -_delta(CURRENT_ASSETS["Übrige kurzfristige Forderungen"]) - _delta(CURRENT_ASSETS["Aktive Rechnungsabgrenzungen"])),
        ("Veränderung Vorräte", -_delta(CURRENT_ASSETS["Vorräte und nicht fakturierte Dienstleistungen"])),
        ("Veränderung Verbindlichkeiten aus Lieferungen und Leistungen",
         _delta(SHORT_TERM_LIABILITIES["Verbindlichkeiten aus Lieferungen und Leistungen"])),
        ("Veränderung Anzahlungen von Kunden", _delta(SHORT_TERM_LIABILITIES["Anzahlungen von Kunden"])),
        ("Veränderung übrige Verbindlichkeiten und passive Rechnungsabgrenzungen",
         _delta(SHORT_TERM_LIABILITIES["Übrige kurzfristige Verbindlichkeiten"])
         + _delta(SHORT_TERM_LIABILITIES["Passive Rechnungsabgrenzungen und kurzfristige Rückstellungen"])),
        ("Investitionen in Sachanlagen", -958_405),
        ("Investitionen in immaterielle Werte", -35_000),
        ("Dividendenausschüttung", -DIVIDEND),
    ]


def _pair(pair: Pair) -> tuple[str, str]:
    return chf(int(pair[0])), chf(int(pair[1]))


def _item(label: str, pair: Pair, note: str = "", kind: RowKind = RowKind.ITEM) -> Row:
    return Row(label, (note, *_pair(pair)), kind)


def _negative(pair: Pair) -> Pair:
    return -pair[0], -pair[1]


# --- Seiten ----------------------------------------------------------------------------------

STATEMENT_WIDTHS = (98, 14, 29, 29)


def _cover() -> Page:
    return Page(
        section="",
        cover=True,
        blocks=(
            Space(55),
            Text(COMPANY, TextStyle.SUBTITLE),
            Text("Jahresrechnung 2025", TextStyle.TITLE),
            Text("Lagebericht, Bilanz, Erfolgsrechnung, Geldflussrechnung und Anhang per 31. Dezember 2025",
                 TextStyle.LEAD),
            Space(8),
            Text(f"Industriestrasse 12, 9999 {PLACE}"),
            Space(70),
            Text("Fiktives Beispiel – Hackathon", TextStyle.SUBTITLE),
            Text("Firma, Personen, Adressen und Zahlen sind frei erfunden und dienen nur als Testdaten für "
                 "den Hackathon «Treasury Kompass». Ähnlichkeiten mit realen Unternehmen oder Personen sind "
                 "zufällig.", TextStyle.SMALL),
        ),
    )


def _contents() -> Page:
    return Page(
        section="Inhalt",
        blocks=(
            Heading("Inhalt"),
            Grid(
                ("Teil", "Seite"),
                (
                    ("Lagebericht 2025", "3"),
                    ("Bilanz per 31. Dezember 2025", "4"),
                    ("Erfolgsrechnung 2025", "5"),
                    ("Geldflussrechnung 2025", "6"),
                    ("Anhang zur Jahresrechnung", "7"),
                    ("Antrag über die Verwendung des Bilanzgewinns", "16"),
                    ("Bericht der Revisionsstelle", "17"),
                    ("Ergänzende Angaben der Geschäftsleitung (nicht geprüft)", "18"),
                ),
                (140, 30),
            ),
            Heading("Organe der Gesellschaft", 2),
            Grid(
                ("Organ", "Mitglieder"),
                (
                    ("Verwaltungsrat", "Hans Muster, Präsident; Dr. Claudia Beispiel, Mitglied; Marco Exempel, Mitglied"),
                    ("Geschäftsleitung", "Stefan Muster, Geschäftsführer; Petra Vorlage, Leiterin Technik; "
                                         "Martin Fiktiv, Leiter Finanzen und Administration; Andrea Modell, "
                                         "Leiterin Verkauf"),
                    ("Revisionsstelle", f"Exempla Treuhand AG, {PLACE} (eingeschränkte Revision)"),
                ),
                (40, 130),
                numeric_from=2,
            ),
            Heading("Gesellschaft", 2),
            Text(f"{COMPANY}, Industriestrasse 12, 9999 {PLACE}. Aktienkapital CHF 1'000'000, eingeteilt in "
                 "10'000 Namenaktien zu CHF 100, vollständig im Besitz der Gründerfamilie. Das Geschäftsjahr "
                 "entspricht dem Kalenderjahr."),
        ),
    )


def _management_report() -> Page:
    revenue_growth = growth(*REVENUE)
    margin = ratio(EBIT[0], REVENUE[0])
    margin_prior = ratio(EBIT[1], REVENUE[1])
    euro_share = ratio(sum(SALES_BY_MARKET[m][0] for m in EURO_MARKETS), REVENUE[0], 0)
    return Page(
        section="Lagebericht",
        blocks=(
            Heading("Lagebericht 2025"),
            Heading("Geschäftsverlauf", 2),
            Text(f"Die {COMPANY} entwickelt und fertigt Spann- und Automationssysteme für die spanende "
                 f"Metallbearbeitung. Rund {euro_share} % des Nettoerlöses entfallen auf Kunden im Euroraum, vor "
                 f"allem in Deutschland, Österreich und Italien. Im Geschäftsjahr 2025 stieg der Nettoerlös "
                 f"gegenüber dem Vorjahr um {percent(revenue_growth)}. Wachstumstreiber waren Automationszellen "
                 f"für die Werkzeugmaschinenindustrie und das Ersatzteilgeschäft. Die EBIT-Marge verbesserte "
                 f"sich von {percent(margin_prior)} auf {percent(margin)}, weil höhere Stückzahlen die Fixkosten "
                 f"besser deckten und die Materialkosten unterproportional zunahmen."),
            Heading("Bestellungs- und Auftragslage", 2),
            Text("Der Auftragseingang lag 3.1 % über dem Vorjahr. Per 31. Dezember 2025 betrug der "
                 "Auftragsbestand CHF 11.2 Mio. (Vorjahr CHF 10.6 Mio.); er deckt die Produktion bis Ende des "
                 "zweiten Quartals 2026."),
            Heading("Mitarbeitende", 2),
            Text("Ende 2025 beschäftigte die Gesellschaft 140 Mitarbeitende (Vorjahr 134), davon 11 Lernende. "
                 "Die Bandbreite der Vollzeitstellen im Jahresdurchschnitt ist in Anhang Ziff. 1 angegeben."),
            Heading("Finanzführung", 2),
            Text("Die Finanzen führt der Leiter Finanzen und Administration. Er ist zugleich für Personal und "
                 "Informatik verantwortlich und wird von zwei Fachpersonen in der Buchhaltung unterstützt. Eine "
                 "eigene Treasury-Stelle besteht nicht. Devisentermingeschäfte schliesst die Gesellschaft "
                 "fallweise über ihre Hausbank ab, gestützt auf die vom Verwaltungsrat genehmigte "
                 "Treasury-Policy."),
            Heading("Durchführung einer Risikobeurteilung", 2),
            Text("Der Verwaltungsrat hat die Risikobeurteilung im Oktober 2025 aktualisiert. Wesentliche Risiken "
                 "sind die Konjunktur im Euroraum, der Wechselkurs EUR/CHF, die Verfügbarkeit von Fachkräften "
                 "sowie die Preise für Stahl und Elektronikkomponenten. Massnahmen und Verantwortlichkeiten sind "
                 "festgelegt; die Risikomatrix steht auf Seite 23."),
            Heading("Forschung und Entwicklung, ausserordentliche Ereignisse", 2),
            Text("Für Entwicklung wurden rund 3 % des Nettoerlöses aufgewendet, vor allem für eine neue "
                 "Generation von Nullpunkt-Spannsystemen. Ausserordentliche Ereignisse sind im Berichtsjahr "
                 "nicht eingetreten."),
            Heading("Zukunftsaussichten", 2),
            Text("Für 2026 erwartet die Geschäftsleitung einen Nettoerlös und Fremdwährungsflüsse in ähnlicher "
                 "Höhe wie 2025 sowie eine stabile EBIT-Marge. Die Unsicherheit über die Nachfrage aus der "
                 "deutschen Automobilzulieferindustrie bleibt hoch."),
            Space(3),
            Text(f"{PLACE}, {APPROVAL_DATE} · Für den Verwaltungsrat: Hans Muster, Präsident", TextStyle.SMALL),
        ),
    )


def _balance_sheet() -> Page:
    rows: list[Row] = [Row("Aktiven", kind=RowKind.HEADING)]
    notes = {"Forderungen aus Lieferungen und Leistungen": "2", "Vorräte und nicht fakturierte Dienstleistungen": "2",
             "Mobile Sachanlagen": "4", "Immobile Sachanlagen": "4", "Immaterielle Werte": "4",
             "Passive Rechnungsabgrenzungen und kurzfristige Rückstellungen": "8",
             "Langfristige verzinsliche Verbindlichkeiten": "5", "Rückstellungen": "8"}
    rows += [_item(label, pair, notes.get(label, "")) for label, pair in CURRENT_ASSETS.items()]
    rows.append(_item("Total Umlaufvermögen", TOTAL_CURRENT_ASSETS, kind=RowKind.SUBTOTAL))
    rows += [_item(label, pair, notes.get(label, "")) for label, pair in FIXED_ASSETS.items()]
    rows.append(_item("Total Anlagevermögen", TOTAL_FIXED_ASSETS, kind=RowKind.SUBTOTAL))
    rows.append(_item("Total Aktiven", TOTAL_ASSETS, kind=RowKind.TOTAL))
    rows.append(Row("Passiven", kind=RowKind.HEADING))
    rows += [_item(label, pair, notes.get(label, "")) for label, pair in SHORT_TERM_LIABILITIES.items()]
    rows.append(_item("Total kurzfristiges Fremdkapital", TOTAL_SHORT_TERM, kind=RowKind.SUBTOTAL))
    rows += [_item(label, pair, notes.get(label, "")) for label, pair in LONG_TERM_LIABILITIES.items()]
    rows.append(_item("Total langfristiges Fremdkapital", TOTAL_LONG_TERM, kind=RowKind.SUBTOTAL))
    rows.append(_item("Total Fremdkapital", TOTAL_LIABILITIES, kind=RowKind.SUBTOTAL))
    rows += [_item(label, pair) for label, pair in EQUITY_RESERVES.items()]
    rows.append(_item("Bilanzgewinn", BALANCE_SHEET_PROFIT))
    rows.append(_item("Gewinnvortrag", RETAINED_EARNINGS_CARRIED, kind=RowKind.SUBITEM))
    rows.append(_item("Jahresgewinn", NET_INCOME, kind=RowKind.SUBITEM))
    rows.append(_item("Total Eigenkapital", TOTAL_EQUITY, kind=RowKind.SUBTOTAL))
    rows.append(_item("Total Passiven", total([TOTAL_LIABILITIES, TOTAL_EQUITY]), kind=RowKind.TOTAL))
    return Page(
        section="Bilanz",
        blocks=(
            Heading("Bilanz per 31. Dezember 2025"),
            Statement(("in CHF", "Anhang", "31.12.2025", "31.12.2024"), tuple(rows), STATEMENT_WIDTHS),
        ),
    )


def _income_statement() -> Page:
    rows = [
        _item("Nettoerlöse aus Lieferungen und Leistungen", REVENUE, "3"),
        *[_item(label, _negative(pair), "4" if label.startswith("Abschreibungen") else "")
          for label, pair in OPERATING_EXPENSES.items()],
        _item("Total Betriebsaufwand", _negative(TOTAL_OPERATING_EXPENSES), kind=RowKind.SUBTOTAL),
        _item("Betriebliches Ergebnis (EBIT)", EBIT, kind=RowKind.SUBTOTAL),
        _item("Finanzaufwand", _negative(FINANCIAL_EXPENSE), "5"),
        _item("Finanzertrag", FINANCIAL_INCOME),
        _item("Ergebnis vor Steuern", EBT, kind=RowKind.SUBTOTAL),
        _item("Direkte Steuern", _negative(TAXES)),
        _item("Jahresgewinn", NET_INCOME, kind=RowKind.TOTAL),
    ]
    return Page(
        section="Erfolgsrechnung",
        blocks=(
            Heading("Erfolgsrechnung 2025"),
            Statement(("in CHF", "Anhang", "2025", "2024"), tuple(rows), STATEMENT_WIDTHS),
            Text("Darstellung nach dem Gesamtkostenverfahren (Produktionserfolgsrechnung, Art. 959b Abs. 2 OR). "
                 "Aufwände sind mit negativem Vorzeichen dargestellt.", TextStyle.SMALL),
        ),
    )


def _cash_flow() -> Page:
    flows = _cash_flow_rows()
    operating = flows[:9]
    investing = flows[9:11]
    financing = flows[11:]
    operating_total = sum(v for _, v in operating)
    investing_total = sum(v for _, v in investing)
    financing_total = sum(v for _, v in financing)
    rows = [
        *[Row(label, (chf(v),)) for label, v in operating],
        Row("Geldfluss aus Geschäftstätigkeit", (chf(operating_total),), RowKind.SUBTOTAL),
        *[Row(label, (chf(v),)) for label, v in investing],
        Row("Geldfluss aus Investitionstätigkeit", (chf(investing_total),), RowKind.SUBTOTAL),
        *[Row(label, (chf(v),)) for label, v in financing],
        Row("Geldfluss aus Finanzierungstätigkeit", (chf(financing_total),), RowKind.SUBTOTAL),
        Row("Veränderung der flüssigen Mittel", (chf(operating_total + investing_total + financing_total),),
            RowKind.TOTAL),
    ]
    return Page(
        section="Geldflussrechnung",
        blocks=(
            Heading("Geldflussrechnung 2025"),
            Statement(("in CHF", "2025"), tuple(rows), (135, 35)),
            Text("Freiwillige Angabe nach der indirekten Methode. Der Bestand der flüssigen Mittel ist in der "
                 "Bilanz auf Seite 4 ausgewiesen.", TextStyle.SMALL),
        ),
    )


def _notes_general() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("Anhang zur Jahresrechnung 2025"),
            Heading("1. Allgemeine Angaben", 2),
            Text(f"Firma: {COMPANY}. Rechtsform: Aktiengesellschaft. Sitz: {PLACE}. Anzahl Vollzeitstellen im "
                 "Jahresdurchschnitt: über 50, aber nicht über 250 (Vorjahr: über 50, aber nicht über 250)."),
            Heading("2. Angaben über die in der Jahresrechnung angewandten Grundsätze", 2),
            Text("Die Jahresrechnung wurde gemäss den Bestimmungen des Schweizer Rechnungslegungsrechts "
                 "(32. Titel des Obligationenrechts) erstellt. Die wesentlichen angewandten Bewertungsgrundsätze, "
                 "die nicht vom Gesetz vorgeschrieben sind, sind nachfolgend beschrieben. Zur Sicherung des "
                 "dauernden Gedeihens des Unternehmens besteht die Möglichkeit, stille Reserven zu bilden und "
                 "aufzulösen."),
            Heading("2.1 Forderungen aus Lieferungen und Leistungen", 3),
            Text("Die Forderungen werden zum Nominalwert bilanziert. Für das allgemeine Kreditrisiko besteht eine "
                 "Pauschalwertberichtigung von 5 % auf inländischen und 10 % auf ausländischen Forderungen. "
                 "Exportforderungen ausserhalb des Euroraums sind teilweise kreditversichert."),
            Heading("2.2 Vorräte und nicht fakturierte Dienstleistungen", 3),
            Text("Rohmaterial und Kaufteile werden zu Anschaffungskosten, Halb- und Fertigfabrikate zu "
                 "Herstellkosten bewertet, höchstens zum Netto-Veräusserungswert. Auf dem Warenlager wird die "
                 "steuerlich zulässige Wertberichtigung vorgenommen. Angefangene Automationsprojekte werden nach "
                 "dem Fertigstellungsgrad zu Herstellkosten bilanziert; Anzahlungen von Kunden sind separat "
                 "unter den kurzfristigen Verbindlichkeiten ausgewiesen."),
        ),
    )


def _notes_principles() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("2. Angewandte Grundsätze (Fortsetzung)", 2),
            Heading("2.3 Sachanlagen und immaterielle Werte", 3),
            Text("Sachanlagen und immaterielle Werte werden zu Anschaffungskosten abzüglich linearer "
                 "Abschreibungen bilanziert. Nutzungsdauern: Gebäude 33 Jahre, Maschinen und Anlagen 5 bis "
                 "10 Jahre, Fahrzeuge 5 Jahre, Informatik 3 Jahre, Software 3 bis 5 Jahre."),
            Heading("2.4 Rückstellungen", 3),
            Text("Rückstellungen decken Garantieverpflichtungen aus Lieferungen der letzten 24 Monate sowie "
                 "Dienstaltersgeschenke. Die Garantierückstellung wird auf Basis der Garantiefälle der letzten "
                 "drei Jahre bemessen."),
            Heading("2.5 Fremdwährungen", 3),
            Text("Bilanzpositionen in Fremdwährung werden zum Stichtagskurs, Transaktionen zum Kurs des "
                 "Transaktionstags umgerechnet. Kursgewinne und -verluste sind im Finanzergebnis enthalten; "
                 "nicht realisierte Kursgewinne werden zurückgestellt."),
            Grid(
                ("Kurse EUR/CHF", "2025", "2024"),
                (("Stichtagskurs per 31.12.", "0.9300", "0.9400"),
                 ("Durchschnittskurs Geschäftsjahr", *EUR_AVERAGE_RATE)),
                (110, 30, 30),
            ),
            Heading("2.6 Derivative Finanzinstrumente", 3),
            Text("Devisentermingeschäfte dienen der Absicherung künftiger Zahlungsflüsse in EUR. Sie werden nicht "
                 "bilanziert; Kontraktwerte und Wiederbeschaffungswerte sind in Ziff. 7 offengelegt."),
            Heading("2.7 Umsatzrealisierung", 3),
            Text("Nettoerlöse werden mit dem Übergang von Nutzen und Gefahr erfasst, in der Regel bei Lieferung "
                 "beziehungsweise bei Abnahme der Anlage durch den Kunden. Erlösminderungen wie Skonti und Rabatte "
                 "sind abgezogen."),
        ),
    )


def _notes_markets() -> Page:
    market_shares = (shares([v[0] for v in SALES_BY_MARKET.values()]),
                     shares([v[1] for v in SALES_BY_MARKET.values()]))
    market_rows = tuple(
        (market, percent(market_shares[0][i]), percent(market_shares[1][i]))
        for i, market in enumerate(SALES_BY_MARKET)
    ) + (("Total", "100.0 %", "100.0 %"),)
    net_eur = (EUR_INVOICED[0] - EUR_PURCHASED[0], EUR_INVOICED[1] - EUR_PURCHASED[1])
    return Page(
        section="Anhang",
        blocks=(
            Heading("3. Umsatz nach Märkten und Fremdwährungen", 2),
            Text("Lieferungen in den Euroraum werden in EUR fakturiert, Lieferungen in die Schweiz und in übrige "
                 "Länder in CHF. Einkäufe in EUR betreffen vor allem Antriebe, Steuerungen und Halbzeuge von "
                 "Lieferanten in Deutschland und Italien."),
            Heading("Nettoerlöse nach Märkten (Anteil in %)", 3),
            Grid(("Markt", "2025", "2024"), market_rows, (110, 30, 30), total_last=True),
            Heading("Fakturierung und Einkauf in EUR (in EUR)", 3),
            Grid(
                ("Position", "2025", "2024"),
                (
                    ("Fakturierung in EUR (Zuflüsse)", *_pair(EUR_INVOICED)),
                    ("Einkauf in EUR (Abflüsse)", *_pair(EUR_PURCHASED)),
                    ("Nettozufluss in EUR", *_pair(net_eur)),
                ),
                (110, 30, 30),
                total_last=True,
            ),
            Text("Für das Geschäftsjahr 2026 erwartet die Geschäftsleitung Fakturierung und Einkauf in EUR in "
                 "ähnlicher Höhe wie 2025 (Lagebericht, Zukunftsaussichten). Die offenen Devisentermingeschäfte "
                 "sind in Ziff. 7 aufgeführt. Durchschnittskurs EUR/CHF siehe Ziff. 2.5."),
        ),
    )


def _notes_fixed_assets() -> Page:
    rows = (
        ("Mobile Sachanlagen", chf(2_232_780), chf(958_405), chf(-1_027_140), chf(2_164_045)),
        ("Immobile Sachanlagen", chf(2_700_000), chf(0), chf(-100_000), chf(2_600_000)),
        ("Immaterielle Werte", chf(210_000), chf(35_000), chf(-60_000), chf(185_000)),
        ("Total", chf(5_142_780), chf(993_405), chf(-1_187_140), chf(4_949_045)),
    )
    require_equal("Anlagespiegel", 5_142_780 + 993_405 - 1_187_140, 4_949_045)
    return Page(
        section="Anhang",
        blocks=(
            Heading("4. Sachanlagen und immaterielle Werte", 2),
            Text("Entwicklung der Buchwerte im Geschäftsjahr 2025 (in CHF):"),
            Grid(("Anlagekategorie", "Buchwert 1.1.", "Zugänge", "Abschreibungen", "Buchwert 31.12."),
                 rows, (58, 28, 28, 28, 28), total_last=True),
            Text("Die Zugänge betreffen im Wesentlichen ein 5-Achs-Bearbeitungszentrum, eine Koordinaten-"
                 "messmaschine und die Erweiterung des ERP-Systems. Die Betriebsliegenschaft in Musterwil steht "
                 "im Eigentum der Gesellschaft."),
            Grid(
                ("Brandversicherungswerte", "31.12.2025", "31.12.2024"),
                (("Mobile Sachanlagen", chf(9_800_000), chf(9_450_000)),
                 ("Gebäude", chf(7_400_000), chf(7_400_000))),
                (110, 30, 30),
            ),
        ),
    )


def _notes_bank_debt() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("5. Verbindlichkeiten gegenüber Banken", 2),
            Text("Die Gesellschaft verfügt über eine Kreditvereinbarung mit ihrer Hausbank. Die verzinslichen "
                 "Verbindlichkeiten setzen sich wie folgt zusammen:"),
            Grid(
                ("in CHF", "31.12.2025", "31.12.2024"),
                (
                    ("Festvorschuss mit Roll-over, variabel verzinst auf Basis SARON", chf(SARON_LOAN), chf(SARON_LOAN)),
                    ("Kontokorrentkredit (Limite CHF 2'000'000)", chf(0), chf(0)),
                    ("Total verzinsliche Verbindlichkeiten gegenüber Banken", chf(SARON_LOAN), chf(SARON_LOAN)),
                ),
                (110, 30, 30),
                total_last=True,
            ),
            Heading("Konditionen", 3),
            Bullets((
                "Zinssatz: Compounded SARON zuzüglich einer Marge von 1.40 %, Zinsperiode drei Monate, Zinsen "
                "nachschüssig.",
                "Laufzeit der Rahmenvereinbarung bis 30. September 2028; eine Amortisation ist nicht vereinbart.",
                "Financial Covenant: Eigenkapitalquote mindestens 30 %, per 31. Dezember 2025 eingehalten.",
                "Der Kontokorrentkredit war per Bilanzstichtag nicht beansprucht.",
            )),
            Heading("Zinsänderungsrisiko", 3),
            Text("Das Zinsänderungsrisiko ist nicht abgesichert. Ein Anstieg des SARON um 1 Prozentpunkt erhöht "
                 "den Zinsaufwand um rund CHF 60'000 pro Jahr. Sicherheiten für die Bankverbindlichkeiten sind in "
                 "Ziff. 6 aufgeführt."),
        ),
    )


def _notes_collateral() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("6. Sicherheiten, Eventualverbindlichkeiten und Leasing", 2),
            Heading("Verpfändete Aktiven", 3),
            Text("Zur Sicherung der Bankverbindlichkeiten ist ein Inhaberschuldbrief über CHF 4'000'000 auf der "
                 "Betriebsliegenschaft verpfändet (Buchwert der Liegenschaft CHF 2'600'000)."),
            Heading("Eventualverbindlichkeiten", 3),
            Grid(
                ("in CHF", "31.12.2025", "31.12.2024"),
                (("Anzahlungs- und Erfüllungsgarantien zugunsten von Kunden", chf(845_000), chf(760_000)),),
                (110, 30, 30),
            ),
            Heading("Nicht bilanzierte Leasingverbindlichkeiten", 3),
            Grid(
                ("in CHF", "31.12.2025", "31.12.2024"),
                (("Fahrzeuge und Informatik, Restbetrag", chf(186_400), chf(214_900)),
                 ("davon fällig innert 12 Monaten", chf(78_300), chf(81_200))),
                (110, 30, 30),
            ),
        ),
    )


def _notes_derivatives() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("7. Derivative Finanzinstrumente", 2),
            Text("Die Gesellschaft sichert einen Teil der erwarteten EUR-Zuflüsse mit Devisentermingeschäften ab. "
                 "Die Geschäfte werden mit der Hausbank abgeschlossen und nicht bilanziert."),
            Grid(
                ("Instrument", "Zweck", "Kontraktwert 31.12.2025", "Kontraktwert 31.12.2024",
                 "Wiederbeschaffungswert 31.12.2025"),
                (("Devisentermingeschäfte (Verkauf EUR gegen CHF)", "Absicherung",
                  f"EUR {chf(FX_FORWARDS_EUR[0])}", f"EUR {chf(FX_FORWARDS_EUR[1])}", "CHF 14'300"),),
                (54, 24, 31, 31, 30),
                numeric_from=2,
            ),
            Text("Per 31. Dezember 2025 bestehen offene Devisentermingeschäfte über EUR 2.0 Mio. (Vorjahr EUR "
                 "1.5 Mio.) mit Fälligkeiten zwischen Januar und Juni 2026. Die Gesellschaft setzt "
                 "Devisentermingeschäfte seit 2019 regelmässig ein; die Abschlüsse tätigt der Leiter Finanzen "
                 "und Administration im Rahmen der Treasury-Policy. Weitere derivative Finanzinstrumente bestehen "
                 "nicht."),
        ),
    )


def _notes_provisions() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("8. Rückstellungen", 2),
            Grid(
                ("in CHF", "31.12.2025", "31.12.2024"),
                (
                    ("Garantierückstellungen", chf(512_360), chf(520_560)),
                    ("Dienstaltersgeschenke", chf(279_200), chf(288_000)),
                    ("Total langfristige Rückstellungen", chf(791_560), chf(808_560)),
                ),
                (110, 30, 30),
                total_last=True,
            ),
            Text("In den passiven Rechnungsabgrenzungen sind kurzfristige Rückstellungen für Ferien- und "
                 "Überzeitguthaben von CHF 214'800 (Vorjahr CHF 198'600) enthalten."),
            Heading("Auflösung stiller Reserven", 3),
            Text("Im Berichtsjahr wurden netto keine stillen Reserven aufgelöst."),
        ),
    )


def _notes_other() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("9. Weitere Angaben", 2),
            Grid(
                ("in CHF", "31.12.2025", "31.12.2024"),
                (("Verbindlichkeiten gegenüber Vorsorgeeinrichtungen", chf(96_240), chf(91_880)),),
                (110, 30, 30),
            ),
            Heading("Honorar der Revisionsstelle", 3),
            Text("Revisionsdienstleistungen CHF 38'500, andere Dienstleistungen (Steuerberatung) CHF 6'200."),
            Heading("Ereignisse nach dem Bilanzstichtag", 3),
            Text(f"Nach dem Bilanzstichtag und bis zur Verabschiedung der Jahresrechnung durch den Verwaltungsrat "
                 f"am {APPROVAL_DATE} sind keine wesentlichen Ereignisse eingetreten, welche die Aussagekraft der "
                 f"Jahresrechnung 2025 beeinträchtigen könnten."),
        ),
    )


def _appropriation() -> Page:
    remainder = int(BALANCE_SHEET_PROFIT[0]) - DIVIDEND - ALLOCATION_TO_RESERVES
    return Page(
        section="Gewinnverwendung",
        blocks=(
            Heading("Antrag über die Verwendung des Bilanzgewinns"),
            Text("Der Verwaltungsrat beantragt der Generalversammlung, den Bilanzgewinn wie folgt zu verwenden:"),
            Grid(
                ("in CHF", "2025"),
                (
                    ("Gewinnvortrag", chf(int(RETAINED_EARNINGS_CARRIED[0]))),
                    ("Jahresgewinn", chf(int(NET_INCOME[0]))),
                    ("Bilanzgewinn zur Verfügung der Generalversammlung", chf(int(BALANCE_SHEET_PROFIT[0]))),
                    ("Dividende von CHF 130 je Namenaktie (10'000 Aktien)", chf(-DIVIDEND)),
                    ("Zuweisung an die freiwilligen Gewinnreserven", chf(-ALLOCATION_TO_RESERVES)),
                    ("Vortrag auf neue Rechnung", chf(remainder)),
                ),
                (135, 35),
                total_last=True,
            ),
            Text(f"{PLACE}, {APPROVAL_DATE} · Für den Verwaltungsrat: Hans Muster, Präsident", TextStyle.SMALL),
        ),
    )


def _audit_report() -> Page:
    return Page(
        section="Bericht der Revisionsstelle",
        blocks=(
            Heading("Bericht der Revisionsstelle zur eingeschränkten Revision"),
            Text(f"an die Generalversammlung der {COMPANY}, {PLACE}", TextStyle.LEAD),
            Text(f"Als Revisionsstelle haben wir die Jahresrechnung (Bilanz, Erfolgsrechnung und Anhang) der "
                 f"{COMPANY} für das am 31. Dezember 2025 abgeschlossene Geschäftsjahr geprüft. Die freiwillige "
                 f"Geldflussrechnung und die ergänzenden Angaben der Geschäftsleitung waren nicht Gegenstand "
                 f"unserer Prüfung."),
            Text("Für die Jahresrechnung ist der Verwaltungsrat verantwortlich, während unsere Aufgabe darin "
                 "besteht, diese zu prüfen. Wir bestätigen, dass wir die gesetzlichen Anforderungen hinsichtlich "
                 "Zulassung und Unabhängigkeit erfüllen."),
            Text("Unsere Revision erfolgte nach dem Schweizer Standard zur Eingeschränkten Revision. Danach ist "
                 "diese Revision so zu planen und durchzuführen, dass wesentliche Fehlaussagen in der "
                 "Jahresrechnung erkannt werden. Eine eingeschränkte Revision umfasst hauptsächlich Befragungen "
                 "und analytische Prüfungshandlungen sowie den Umständen angemessene Detailprüfungen der beim "
                 "geprüften Unternehmen vorhandenen Unterlagen. Prüfungen der betrieblichen Abläufe und des "
                 "internen Kontrollsystems sowie Prüfungshandlungen zur Aufdeckung deliktischer Handlungen oder "
                 "anderer Gesetzesverstösse sind nicht Bestandteil dieser Revision."),
            Text("Bei unserer Revision sind wir nicht auf Sachverhalte gestossen, aus denen wir schliessen "
                 "müssten, dass die Jahresrechnung sowie der Antrag über die Verwendung des Bilanzgewinns nicht "
                 "Gesetz und Statuten entsprechen."),
            Space(4),
            Text(f"Exempla Treuhand AG · Lea Platzhalter, zugelassene Revisionsexpertin, leitende Revisorin · "
                 f"{PLACE}, {APPROVAL_DATE}", TextStyle.SMALL),
        ),
    )


def _supplement_intro() -> Page:
    return Page(
        section="Ergänzende Angaben",
        blocks=(
            Heading("Ergänzende Angaben der Geschäftsleitung"),
            Text("Die folgenden Seiten ergänzen die Jahresrechnung für Banken und Kreditgeber. Sie sind nicht "
                 "Bestandteil der geprüften Jahresrechnung.", TextStyle.LEAD),
            Grid(
                ("Inhalt", "Seite"),
                (
                    ("Kennzahlen im Mehrjahresvergleich", "19"),
                    ("Erläuterungen zur Ertragslage", "20"),
                    ("Investitionen und Unterhalt", "21"),
                    ("Finanzierung und Liquiditätsplanung", "22"),
                    ("Risikomatrix", "23"),
                    ("Kontakt und Hinweise", "24"),
                ),
                (140, 30),
            ),
        ),
    )


def _key_ratios() -> Page:
    equity_ratio = (ratio(TOTAL_EQUITY[0], TOTAL_ASSETS[0]), ratio(TOTAL_EQUITY[1], TOTAL_ASSETS[1]))
    margin = (ratio(EBIT[0], REVENUE[0]), ratio(EBIT[1], REVENUE[1]))
    quick = tuple(
        ratio(CASH[y] + CURRENT_ASSETS["Forderungen aus Lieferungen und Leistungen"][y]
              + CURRENT_ASSETS["Übrige kurzfristige Forderungen"][y], TOTAL_SHORT_TERM[y], 0)
        for y in (0, 1)
    )
    euro_share = tuple(
        ratio(sum(SALES_BY_MARKET[m][y] for m in EURO_MARKETS), REVENUE[y]) for y in (0, 1)
    )
    return Page(
        section="Ergänzende Angaben",
        blocks=(
            Heading("Kennzahlen im Mehrjahresvergleich"),
            Grid(
                ("Kennzahl", "2021", "2022", "2023", "2024", "2025"),
                (
                    ("Veränderung Nettoerlös", "+11.2 %", "+7.5 %", "+2.4 %", "+1.9 %",
                     f"+{percent(growth(*REVENUE))}"),
                    ("EBIT-Marge", "7.4 %", "8.3 %", "7.6 %", percent(margin[1]), percent(margin[0])),
                    ("Eigenkapitalquote", "36.2 %", "37.9 %", "39.0 %", percent(equity_ratio[1]),
                     percent(equity_ratio[0])),
                    ("Liquiditätsgrad II", "198 %", "207 %", "215 %", percent(quick[1], 0), percent(quick[0], 0)),
                    ("Anteil Nettoerlös Euroraum", "64.8 %", "64.1 %", "63.5 %", percent(euro_share[1]),
                     percent(euro_share[0])),
                    ("Investitionen in % des Nettoerlöses", "4.9 %", "6.2 %", "3.8 %", "3.1 %",
                     percent(ratio(993_405, REVENUE[0]))),
                    ("Mitarbeitende per 31.12.", "118", "126", "131", "134", "140"),
                ),
                (60, 22, 22, 22, 22, 22),
            ),
            Text("Eigenkapitalquote: Eigenkapital in % der Bilanzsumme. Liquiditätsgrad II: flüssige Mittel und "
                 "kurzfristige Forderungen in % des kurzfristigen Fremdkapitals.", TextStyle.SMALL),
        ),
    )


def _profitability() -> Page:
    def quota(label: str) -> tuple[str, str, str]:
        pair = OPERATING_EXPENSES[label]
        return label, percent(ratio(pair[0], REVENUE[0])), percent(ratio(pair[1], REVENUE[1]))

    return Page(
        section="Ergänzende Angaben",
        blocks=(
            Heading("Erläuterungen zur Ertragslage"),
            Text("Der Nettoerlös wuchs vor allem mit Automationszellen und im Ersatzteilgeschäft. Per 1. März 2025 "
                 "wurden die Listenpreise im Durchschnitt um 2.5 % erhöht. Gemessen am Nettoerlös entwickelten "
                 "sich die Aufwandpositionen wie folgt:"),
            Grid(("Aufwand in % des Nettoerlöses", "2025", "2024"),
                 tuple(quota(label) for label in OPERATING_EXPENSES), (110, 30, 30)),
            Heading("Nettoerlös nach Produktgruppen", 3),
            Grid(("Produktgruppe", "Anteil 2025", "Anteil 2024"),
                 (("Spannsysteme", "54 %", "57 %"), ("Automationszellen", "31 %", "28 %"),
                  ("Service und Ersatzteile", "15 %", "15 %")), (110, 30, 30)),
            Text("Die Materialquote sank dank Rahmenverträgen für Stahl und Antriebe. Die Personalquote ging trotz "
                 "sechs zusätzlicher Stellen in Montage und Service zurück."),
        ),
    )


def _investments() -> Page:
    return Page(
        section="Ergänzende Angaben",
        blocks=(
            Heading("Investitionen und Unterhalt"),
            Text("Im Geschäftsjahr 2025 investierte die Gesellschaft vor allem in die mechanische Fertigung:"),
            Bullets((
                "5-Achs-Bearbeitungszentrum für Grundkörper von Spannsystemen (Inbetriebnahme Mai 2025)",
                "Koordinatenmessmaschine für die Endkontrolle",
                "Erweiterung des ERP-Systems um Projektabrechnung und Serviceplanung",
            )),
            Text("Für 2026 sind Investitionen von rund CHF 1.2 Mio. budgetiert, darunter eine Automationszelle für "
                 "die eigene Fertigung. Die Investitionen werden aus dem Geldfluss aus Geschäftstätigkeit "
                 "finanziert. Grössere Unterhaltsarbeiten an der Liegenschaft sind erst ab 2028 geplant."),
        ),
    )


def _financing() -> Page:
    return Page(
        section="Ergänzende Angaben",
        blocks=(
            Heading("Finanzierung und Liquiditätsplanung"),
            Text("Die Gesellschaft finanziert sich über Eigenkapital, den Festvorschuss der Hausbank (Anhang "
                 "Ziff. 5) und eine nicht beanspruchte Kontokorrentlimite."),
            Text("Die Liquiditätsplanung wird monatlich für die nächsten zwölf Monate nachgeführt. Der "
                 "Liquiditätsbedarf ist im zweiten Quartal am höchsten, weil Automationsprojekte vorfinanziert "
                 "werden. Gemäss Treasury-Policy hält die Gesellschaft jederzeit eine Mindestliquidität von "
                 "1.5 Monaten Betriebsaufwand."),
            Text("Überschüssige Liquidität liegt heute vollständig auf Kontokorrent- und Callgeldkonten bei der "
                 "Hausbank. Die Geschäftsleitung prüft mit der Bank eine Anlage in Geldmarktfonds und "
                 "kurzlaufende Obligationen in CHF; die Mittel sollen innerhalb eines Jahres verfügbar bleiben."),
            Text("Die erwarteten Nettozuflüsse in EUR sichert die Gesellschaft teilweise mit "
                 "Devisentermingeschäften ab (Anhang Ziff. 3 und 7). Einkäufe in USD sind in der Jahresrechnung "
                 "nicht separat ausgewiesen."),
        ),
    )


def _risk_matrix() -> Page:
    return Page(
        section="Ergänzende Angaben",
        blocks=(
            Heading("Risikomatrix"),
            Text("Auszug aus der Risikobeurteilung des Verwaltungsrates vom Oktober 2025:"),
            Grid(
                ("Risiko", "Wahrscheinlichkeit", "Auswirkung", "Massnahmen"),
                (
                    ("Nachfrageeinbruch im Euroraum", "mittel", "hoch",
                     "Diversifikation der Kundenbranchen, flexible Arbeitszeitmodelle"),
                    ("Wechselkurs EUR/CHF", "hoch", "mittel",
                     "Teilabsicherung mit Devisentermingeschäften gemäss Treasury-Policy, Einkauf in EUR"),
                    ("Fachkräftemangel", "mittel", "mittel", "Lehrlingsausbildung, Kooperation mit einer Fachhochschule"),
                    ("Rohmaterial- und Elektronikpreise", "mittel", "mittel", "Rahmenverträge, Preisgleitklauseln"),
                    ("Zinsanstieg SARON", "tief", "tief", "Monitoring, Zinsabsicherung prüfen"),
                    ("Ausfall Informatik, Cyberangriff", "tief", "hoch", "Datensicherung, Notfallplan, Schulungen"),
                    ("Ausfall eines Grosskunden", "tief", "mittel", "Kreditlimiten, Kreditversicherung"),
                ),
                (46, 28, 24, 72),
                numeric_from=4,
            ),
        ),
    )


def _contact() -> Page:
    return Page(
        section="Ergänzende Angaben",
        blocks=(
            Heading("Kontakt und Hinweise"),
            Text(f"{COMPANY}, Industriestrasse 12, 9999 {PLACE}"),
            Text("Ansprechperson Finanzen: Martin Fiktiv, Leiter Finanzen und Administration"),
            Space(6),
            Heading("Hinweis", 2),
            Text("Dieses Dokument ist ein fiktives Beispiel für den Hackathon «Treasury Kompass». Firma, Personen, "
                 "Adressen und Zahlen sind frei erfunden. Ähnlichkeiten mit realen Unternehmen oder Personen sind "
                 "zufällig. Das Dokument enthält keine echten Kunden- oder Finanzdaten."),
        ),
    )


def build() -> DocumentSpec:
    _check_figures()
    pages = (
        _cover(), _contents(), _management_report(), _balance_sheet(), _income_statement(), _cash_flow(),
        _notes_general(), _notes_principles(), _notes_markets(), _notes_fixed_assets(), _notes_bank_debt(),
        _notes_collateral(), _notes_derivatives(), _notes_provisions(), _notes_other(), _appropriation(),
        _audit_report(), _supplement_intro(), _key_ratios(), _profitability(), _investments(), _financing(),
        _risk_matrix(), _contact(),
    )
    spec = DocumentSpec(
        filename=FILENAME,
        title=f"Jahresrechnung 2025 – {COMPANY} (fiktives Beispiel)",
        subject="Fiktives Beispiel – Hackathon Treasury Kompass: Jahresrechnung eines Export-KMU",
        running_header=f"{COMPANY} · Jahresrechnung 2025",
        theme=Theme(accent=colors.HexColor("#1F3A5F")),
        pages=pages,
    )
    _check_fact_pages(spec)
    return spec


def _check_fact_pages(spec: DocumentSpec) -> None:
    """Fundstellen laut Prototyp und samples/README.md."""
    require_on_page(spec, 4, "Bilanz per 31. Dezember 2025", "Total Aktiven 19'200'210",
                    "Total Eigenkapital 7'900'220", "Flüssige Mittel 6'400'380")
    require_on_page(spec, 5, "Erfolgsrechnung 2025", "Nettoerlöse aus Lieferungen und Leistungen 3 28'400'540",
                    "Total Betriebsaufwand -26'100'190", "Betriebliches Ergebnis (EBIT) 2'300'350")
    require_on_page(spec, 9, "3. Umsatz nach Märkten und Fremdwährungen", "Fakturierung in EUR (Zuflüsse) 18'700'420",
                    "Einkauf in EUR (Abflüsse) 4'499'860")
    require_on_page(spec, 11, "5. Verbindlichkeiten gegenüber Banken", "variabel verzinst auf Basis SARON 6'000'000")
    require_on_page(spec, 13, "7. Derivative Finanzinstrumente", "offene Devisentermingeschäfte über EUR 2.0 Mio.")
    require_on_page(spec, 3, "Eine eigene Treasury-Stelle besteht nicht.")
