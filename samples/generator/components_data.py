"""Zahlen der fiktiven Beispiel Components AG (Konzern, Mio. CHF, Geschäftsjahr 2025 und Vorjahr).

Zielwerte aus docs/regelwerk.md Abschnitt 9 und dem Prototyp (CLIENTS.mid): Umsatz 146.0,
Bilanzsumme 112.0, Eigenkapital 46.0, EBIT 12.8, flüssige Mittel 28.0, Betriebsaufwand 133.2,
Bankkredit SARON 25.0, EUR 85.4 / 33.0, USD 33.0 / 12.0.
"""

from __future__ import annotations

from decimal import Decimal as D

from .numbers import Pair, ratio, require_equal, total

COMPANY = "Beispiel Components AG"
PLACE = "Beispielfeld"
ADDRESS = "Werkstrasse 5, 9998 Beispielfeld"
APPROVAL_DATE = "5. März 2026"

# --- Konzernbilanz per 31.12. (2025, 2024) ---------------------------------------------------

CURRENT_ASSETS: dict[str, Pair] = {
    "Flüssige Mittel": (D("28.0"), D("22.4")),
    "Forderungen aus Lieferungen und Leistungen": (D("21.6"), D("20.9")),
    "Übrige kurzfristige Forderungen": (D("2.1"), D("2.4")),
    "Vorräte": (D("24.3"), D("25.1")),
    "Aktive Rechnungsabgrenzungen": (D("1.4"), D("1.2")),
}
FIXED_ASSETS: dict[str, Pair] = {
    "Sachanlagen": (D("31.2"), D("31.9")),
    "Finanzanlagen": (D("1.1"), D("1.0")),
    "Immaterielle Werte": (D("2.3"), D("2.6")),
}
SHORT_TERM_LIABILITIES: dict[str, Pair] = {
    "Verbindlichkeiten aus Lieferungen und Leistungen": (D("14.8"), D("14.1")),
    "Kurzfristige Finanzverbindlichkeiten": (D("5.0"), D("5.0")),
    "Übrige kurzfristige Verbindlichkeiten": (D("5.9"), D("5.6")),
    "Kurzfristige Rückstellungen": (D("2.6"), D("2.4")),
    "Passive Rechnungsabgrenzungen": (D("8.1"), D("7.6")),
}
LONG_TERM_LIABILITIES: dict[str, Pair] = {
    "Langfristige Finanzverbindlichkeiten": (D("20.0"), D("25.0")),
    "Langfristige Rückstellungen": (D("4.1"), D("3.9")),
    "Übrige langfristige Verbindlichkeiten": (D("5.5"), D("4.3")),
}
EQUITY: dict[str, Pair] = {
    "Aktienkapital": (D("5.0"), D("5.0")),
    "Kapitalreserven": (D("3.2"), D("3.2")),
    "Gewinnreserven": (D("40.4"), D("33.5")),
    "Kumulierte Umrechnungsdifferenzen": (D("-2.6"), D("-2.1")),
}

# --- Konzernerfolgsrechnung (2025, 2024), Aufwände positiv ------------------------------------

REVENUE: Pair = (D("146.0"), D("139.6"))
OPERATING_EXPENSES: dict[str, Pair] = {
    "Materialaufwand": (D("56.9"), D("54.8")),
    "Personalaufwand": (D("52.7"), D("50.9")),
    "Übriger Betriebsaufwand": (D("16.4"), D("15.8")),
    "Abschreibungen auf Sachanlagen": (D("6.5"), D("6.3")),
    "Abschreibungen auf immateriellen Werten": (D("0.7"), D("0.7")),
}
FINANCIAL_INCOME: Pair = (D("0.2"), D("0.1"))
FINANCIAL_EXPENSE: Pair = (D("0.7"), D("0.7"))
INCOME_TAXES: Pair = (D("2.4"), D("2.0"))

# --- Konzerngeldflussrechnung (2025, 2024) ----------------------------------------------------

OPERATING_CASH_FLOW_ITEMS: dict[str, Pair] = {
    "Abschreibungen auf Sachanlagen": (D("6.5"), D("6.3")),
    "Abschreibungen auf immateriellen Werten": (D("0.7"), D("0.7")),
    "Veränderung Rückstellungen": (D("0.4"), D("0.2")),
    "Veränderung Forderungen aus Lieferungen und Leistungen": (D("-0.7"), D("-1.1")),
    "Veränderung Vorräte": (D("0.8"), D("-0.9")),
    "Veränderung übrige Forderungen und aktive Rechnungsabgrenzungen": (D("0.1"), D("-0.2")),
    "Veränderung Verbindlichkeiten aus Lieferungen und Leistungen": (D("0.7"), D("1.6")),
    "Veränderung übrige Verbindlichkeiten und passive Rechnungsabgrenzungen": (D("2.0"), D("2.1")),
}
INVESTING_ITEMS: dict[str, Pair] = {
    "Investitionen in Sachanlagen": (D("-5.8"), D("-6.4")),
    "Investitionen in immaterielle Werte": (D("-0.4"), D("-0.6")),
    "Investitionen in Finanzanlagen": (D("-0.1"), D("-0.1")),
}
FINANCING_ITEMS: dict[str, Pair] = {
    "Rückzahlung Finanzverbindlichkeiten": (D("-5.0"), D("-5.0")),
    "Dividende an die Aktionäre": (D("-3.0"), D("-2.5")),
}
TRANSLATION_EFFECT_ON_CASH: Pair = (D("-0.5"), D("-0.2"))
CASH_AT_START: Pair = (D("22.4"), D("20.0"))

# --- Segmente, Regionen, Währungen ------------------------------------------------------------

SEGMENT_REVENUE: dict[str, Pair] = {
    "Antriebskomponenten": (D("68.6"), D("66.1")),
    "Präzisionsteile": (D("49.6"), D("47.2")),
    "Sensorik und Service": (D("27.8"), D("26.3")),
}
SEGMENT_EBIT: dict[str, Pair] = {
    "Antriebskomponenten": (D("6.6"), D("5.9")),
    "Präzisionsteile": (D("4.1"), D("3.6")),
    "Sensorik und Service": (D("3.0"), D("2.5")),
    "Konzernfunktionen und Eliminationen": (D("-0.9"), D("-0.9")),
}
REGION_SHARES: dict[str, tuple[str, str]] = {
    "Schweiz": ("18.4", "19.0"),
    "Deutschland": ("31.2", "31.5"),
    "Übriges Europa": ("27.0", "26.8"),
    "Nordamerika": ("19.1", "18.5"),
    "Asien und übrige Länder": ("4.3", "4.2"),
}

# Umsatz und Einkauf in Originalwährung (Mio.), Durchschnittskurse für die Anteile.
FX_REVENUE: dict[str, Pair] = {"EUR": (D("85.4"), D("82.0")), "USD": (D("33.0"), D("30.6"))}
FX_PURCHASES: dict[str, Pair] = {"EUR": (D("33.0"), D("31.8")), "USD": (D("12.0"), D("11.1"))}
AVERAGE_RATES: dict[str, Pair] = {"EUR": (D("0.94"), D("0.952")), "USD": (D("0.80"), D("0.88"))}
YEAR_END_RATES: dict[str, Pair] = {"EUR": (D("0.9300"), D("0.9400")), "USD": (D("0.7900"), D("0.9000")),
                                   "CZK": (D("0.0372"), D("0.0374"))}

FX_FORWARDS: Pair = (D("38.0"), D("34.5"))
FX_COLLARS: Pair = (D("12.0"), D("9.0"))
SARON_LOAN: Pair = (D("25.0"), D("30.0"))
UNUSED_CREDIT_LINE = D("15.0")
EMPLOYEES_FTE = (610, 592)
EMPLOYEES_BY_COUNTRY: dict[str, tuple[int, int]] = {
    "Schweiz": (352, 345), "Deutschland": (118, 116), "Tschechien": (104, 98), "USA": (36, 33),
}

# --- abgeleitete Werte -----------------------------------------------------------------------

TOTAL_CURRENT_ASSETS = total(CURRENT_ASSETS.values())
TOTAL_FIXED_ASSETS = total(FIXED_ASSETS.values())
TOTAL_ASSETS = total([TOTAL_CURRENT_ASSETS, TOTAL_FIXED_ASSETS])
TOTAL_SHORT_TERM = total(SHORT_TERM_LIABILITIES.values())
TOTAL_LONG_TERM = total(LONG_TERM_LIABILITIES.values())
TOTAL_LIABILITIES = total([TOTAL_SHORT_TERM, TOTAL_LONG_TERM])
TOTAL_EQUITY = total(EQUITY.values())
TOTAL_OPERATING_EXPENSES = total(OPERATING_EXPENSES.values())
EBIT: Pair = (REVENUE[0] - TOTAL_OPERATING_EXPENSES[0], REVENUE[1] - TOTAL_OPERATING_EXPENSES[1])
EBT: Pair = (
    EBIT[0] + FINANCIAL_INCOME[0] - FINANCIAL_EXPENSE[0],
    EBIT[1] + FINANCIAL_INCOME[1] - FINANCIAL_EXPENSE[1],
)
NET_INCOME: Pair = (EBT[0] - INCOME_TAXES[0], EBT[1] - INCOME_TAXES[1])
OPERATING_CASH_FLOW = total([NET_INCOME, *OPERATING_CASH_FLOW_ITEMS.values()])
INVESTING_CASH_FLOW = total(INVESTING_ITEMS.values())
FINANCING_CASH_FLOW = total(FINANCING_ITEMS.values())
CASH_CHANGE = total([OPERATING_CASH_FLOW, INVESTING_CASH_FLOW, FINANCING_CASH_FLOW, TRANSLATION_EFFECT_ON_CASH])


def fx_in_chf(amounts: dict[str, Pair], year: int) -> dict[str, D]:
    return {ccy: amounts[ccy][year] * AVERAGE_RATES[ccy][year] for ccy in amounts}


def chf_residual(amounts: dict[str, Pair], whole: Pair, year: int) -> D:
    """Anteil in CHF = Total minus EUR und USD zum Durchschnittskurs, auf 0.1 gerundet."""
    return (whole[year] - sum(fx_in_chf(amounts, year).values())).quantize(D("0.1"))


def check_figures() -> None:
    for year in (0, 1):
        label = 2025 - year
        require_equal(f"Konzernbilanz {label}", TOTAL_ASSETS[year], TOTAL_LIABILITIES[year] + TOTAL_EQUITY[year])
        require_equal(f"Segmentumsatz {label}", sum(v[year] for v in SEGMENT_REVENUE.values()), REVENUE[year])
        require_equal(f"Segment-EBIT {label}", sum(v[year] for v in SEGMENT_EBIT.values()), EBIT[year])
        require_equal(f"Regionen {label}", sum(D(v[year]) for v in REGION_SHARES.values()), D("100.0"))
        require_equal(f"Mitarbeitende {label}", sum(v[year] for v in EMPLOYEES_BY_COUNTRY.values()),
                      EMPLOYEES_FTE[year])
        require_equal(f"Flüssige Mittel {label}", CASH_AT_START[year] + CASH_CHANGE[year],
                      CURRENT_ASSETS["Flüssige Mittel"][year])
    require_equal("Umsatzerlös", REVENUE[0], D("146.0"))
    require_equal("Bilanzsumme", TOTAL_ASSETS[0], D("112.0"))
    require_equal("Eigenkapital", TOTAL_EQUITY[0], D("46.0"))
    require_equal("EBIT", EBIT[0], D("12.8"))
    require_equal("Flüssige Mittel", CURRENT_ASSETS["Flüssige Mittel"][0], D("28.0"))
    require_equal("Betriebsaufwand", TOTAL_OPERATING_EXPENSES[0], D("133.2"))
    require_equal("Bankkredit SARON", SARON_LOAN[0], D("25.0"))
    require_equal("Finanzverbindlichkeiten", SARON_LOAN[0],
                  SHORT_TERM_LIABILITIES["Kurzfristige Finanzverbindlichkeiten"][0]
                  + LONG_TERM_LIABILITIES["Langfristige Finanzverbindlichkeiten"][0])
    require_equal("EUR netto", FX_REVENUE["EUR"][0] - FX_PURCHASES["EUR"][0], D("52.4"))
    require_equal("USD netto", FX_REVENUE["USD"][0] - FX_PURCHASES["USD"][0], D("21.0"))
    require_equal("Eigenkapitalquote", ratio(TOTAL_EQUITY[0], TOTAL_ASSETS[0]), D("41.1"))
    require_equal("Gewinnreserven", EQUITY["Gewinnreserven"][1] + NET_INCOME[0]
                  + FINANCING_ITEMS["Dividende an die Aktionäre"][0], EQUITY["Gewinnreserven"][0])
