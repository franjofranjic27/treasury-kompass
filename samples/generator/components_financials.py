"""Geschäftsbericht Beispiel Components AG: Finanzbericht, Konzernrechnung, Anhang und
Revisionsbericht (S. 44–71).

Fundstellen laut Prototyp: Konzernbilanz S. 51, Konzernerfolgsrechnung S. 52, Anhang Ziff. 9
(Derivate, hier S. 61) und Ziff. 14 S. 66 (Konsortialkredit SARON).
"""

from __future__ import annotations

from . import components_data as data
from .layout import Bullets, Grid, Heading, Page, Row, RowKind, Space, Statement, Text, TextStyle
from .numbers import Pair, growth, mio, percent, ratio

STATEMENT_WIDTHS = (98, 14, 29, 29)
NOTE_TABLE_WIDTHS = (110, 30, 30)


def _item(label: str, pair: Pair, note: str = "", kind: RowKind = RowKind.ITEM) -> Row:
    return Row(label, (note, mio(pair[0]), mio(pair[1])), kind)


def _negative(pair: Pair) -> Pair:
    return -pair[0], -pair[1]


def _pair_row(label: str, pair: Pair) -> tuple[str, str, str]:
    return label, mio(pair[0]), mio(pair[1])


def finance_divider() -> Page:
    return Page(
        section="Finanzbericht",
        blocks=(
            Heading("Finanzbericht 2025"),
            Text("Der Finanzbericht enthält den Finanzkommentar, die Kennzahlen im Fünfjahresvergleich, die "
                 "Konzernrechnung nach Swiss GAAP FER mit Anhang sowie den Bericht der Revisionsstelle.",
                 TextStyle.LEAD),
            Grid(
                ("Inhalt", "Seite"),
                (
                    ("Finanzkommentar: Ertragslage", "45"),
                    ("Finanzkommentar: Bilanz und Finanzierung", "46"),
                    ("Finanzkommentar: Geldfluss und Investitionen", "47"),
                    ("Finanzkommentar: Währungseinflüsse und Absicherung", "48"),
                    ("Kennzahlen im Fünfjahresvergleich", "49"),
                    ("Konzernrechnung 2025", "50"),
                    ("Anhang zur Konzernrechnung", "55"),
                    ("Bericht der Revisionsstelle zur Konzernrechnung", "70"),
                ),
                (140, 30),
            ),
        ),
    )


def commentary_results() -> Page:
    return Page(
        section="Finanzbericht",
        blocks=(
            Heading("Finanzkommentar: Ertragslage", 2),
            Text(f"Der Nettoerlös wuchs um {percent(growth(*data.REVENUE))}, in Lokalwährungen um 7.2 %. Der "
                 "Währungseffekt von minus 2.6 Prozentpunkten stammt vor allem aus dem schwächeren US-Dollar."),
            Text(f"Das Betriebsergebnis (EBIT) stieg um {percent(growth(*data.EBIT))}, die EBIT-Marge auf "
                 f"{percent(ratio(data.EBIT[0], data.REVENUE[0]))}. Die Bruttomarge nach Materialaufwand verbesserte "
                 "sich um 0.3 Prozentpunkte. Die Absicherungsgeschäfte milderten den Währungseffekt auf das "
                 "Ergebnis; ohne Absicherung wäre die EBIT-Marge rund 0.6 Prozentpunkte tiefer ausgefallen."),
            Text("Das Finanzergebnis verbesserte sich dank tieferer Zinsen und höherer Zinserträge auf den "
                 "flüssigen Mitteln. Die Steuerquote betrug 19.5 % (Vorjahr 19.0 %)."),
            Text(f"Der Konzerngewinn stieg um {percent(growth(*data.NET_INCOME))}."),
        ),
    )


def commentary_balance_sheet() -> Page:
    return Page(
        section="Finanzbericht",
        blocks=(
            Heading("Finanzkommentar: Bilanz und Finanzierung", 2),
            Text(f"Die Bilanzsumme nahm um {percent(growth(*data.TOTAL_ASSETS))} zu, vor allem wegen höherer "
                 "flüssiger Mittel. Die Vorräte gingen trotz höherem Umsatz zurück; die Lagerreichweite sank von "
                 "88 auf 82 Tage. Die Forderungen stiegen unterproportional zum Umsatz."),
            Text(f"Die Eigenkapitalquote erreichte {percent(ratio(data.TOTAL_EQUITY[0], data.TOTAL_ASSETS[0]))}. "
                 "Die Finanzverbindlichkeiten sanken durch die vertragliche Amortisation des Konsortialkredits. "
                 "Die Gruppe ist netto schuldenfrei; die Financial Covenants des Kreditvertrags sind mit grosser "
                 "Reserve eingehalten (Anhang Ziff. 14)."),
            Text("Für die überschüssige Liquidität hat das Treasury-Komitee eine gestaffelte Anlage in "
                 "Festgeldern, Geldmarktfonds und Obligationen in CHF beschlossen. Die Mindestliquidität gemäss "
                 "Treasury-Policy bleibt jederzeit verfügbar."),
        ),
    )


def commentary_cash_flow() -> Page:
    free_cash_flow = (data.OPERATING_CASH_FLOW[0] + data.INVESTING_CASH_FLOW[0],
                      data.OPERATING_CASH_FLOW[1] + data.INVESTING_CASH_FLOW[1])
    return Page(
        section="Finanzbericht",
        blocks=(
            Heading("Finanzkommentar: Geldfluss und Investitionen", 2),
            Text(f"Der Geldfluss aus Geschäftstätigkeit stieg um {percent(growth(*data.OPERATING_CASH_FLOW))}. "
                 "Neben dem höheren Gewinn trug der Abbau der Vorräte bei."),
            Text("Die Investitionen lagen unter dem Vorjahr, weil die Erweiterung im Werk Tschechien 2024 begonnen "
                 "hatte. Investiert wurde in die zweite Montagelinie, Schleifmaschinen und die "
                 "Servicewerkstatt in den USA."),
            Grid(
                ("in Mio. CHF", "2025", "2024"),
                (
                    _pair_row("Geldfluss aus Geschäftstätigkeit", data.OPERATING_CASH_FLOW),
                    _pair_row("Geldfluss aus Investitionstätigkeit", data.INVESTING_CASH_FLOW),
                    _pair_row("Free Cashflow", free_cash_flow),
                ),
                NOTE_TABLE_WIDTHS,
                total_last=True,
            ),
            Text("Aus dem Free Cashflow wurden die Amortisation des Konsortialkredits und die Dividende bezahlt."),
        ),
    )


def commentary_currencies() -> Page:
    return Page(
        section="Finanzbericht",
        blocks=(
            Heading("Finanzkommentar: Währungseinflüsse und Absicherung", 2),
            Text("Der Franken war im Jahresdurchschnitt gegenüber dem Euro um 1.3 % und gegenüber dem US-Dollar "
                 "um 9.1 % stärker als im Vorjahr. Weil der Anteil des Nettoerlöses in Fremdwährung deutlich höher "
                 "ist als der Anteil der Kosten, wirkt ein stärkerer Franken direkt auf die Marge."),
            Text("Group Treasury sichert die budgetierten Nettopositionen in EUR und USD in vier Quartalstranchen "
                 "ab. Die Quote ist für die nächsten drei Monate am höchsten und nimmt für spätere Tranchen ab. "
                 "Ein Teil der Absicherung erfolgt mit Zero-Cost-Collars, damit bei einem schwächeren Franken ein "
                 "Teil der Kurschancen erhalten bleibt."),
            Text("Per Jahresende waren rund drei Viertel der Nettopositionen der nächsten zwölf Monate "
                 "abgesichert. Kontraktvolumen und Wiederbeschaffungswerte stehen in Anhang Ziff. 9, die "
                 "Währungsflüsse im Segmentbericht auf Seite 41."),
        ),
    )


def five_year_overview() -> Page:
    return Page(
        section="Finanzbericht",
        blocks=(
            Heading("Kennzahlen im Fünfjahresvergleich", 2),
            Grid(
                ("Kennzahl", "2021", "2022", "2023", "2024", "2025"),
                (
                    ("Veränderung Nettoerlös", "+9.4 %", "+11.8 %", "+2.9 %", "+3.8 %",
                     f"+{percent(growth(*data.REVENUE))}"),
                    ("EBIT-Marge", "7.1 %", "8.4 %", "7.6 %", percent(ratio(data.EBIT[1], data.REVENUE[1])),
                     percent(ratio(data.EBIT[0], data.REVENUE[0]))),
                    ("Eigenkapitalquote", "31.5 %", "33.2 %", "34.9 %",
                     percent(ratio(data.TOTAL_EQUITY[1], data.TOTAL_ASSETS[1])),
                     percent(ratio(data.TOTAL_EQUITY[0], data.TOTAL_ASSETS[0]))),
                    ("Geldfluss aus Geschäftstätigkeit in % des Nettoerlöses", "10.8 %", "11.6 %", "10.1 %",
                     percent(ratio(data.OPERATING_CASH_FLOW[1], data.REVENUE[1])),
                     percent(ratio(data.OPERATING_CASH_FLOW[0], data.REVENUE[0]))),
                    ("Investitionen in % des Nettoerlöses", "6.0 %", "5.7 %", "6.4 %",
                     percent(ratio(-data.INVESTING_CASH_FLOW[1], data.REVENUE[1])),
                     percent(ratio(-data.INVESTING_CASH_FLOW[0], data.REVENUE[0]))),
                    ("Vollzeitstellen per 31.12.", "541", "566", "579", str(data.EMPLOYEES_FTE[1]),
                     str(data.EMPLOYEES_FTE[0])),
                    ("Dividende je Aktie in CHF", "4.00", "5.00", "5.00", "6.00", "7.00 (Antrag)"),
                ),
                (62, 21, 21, 21, 21, 24),
            ),
        ),
    )


def consolidated_divider() -> Page:
    return Page(
        section="Konzernrechnung",
        blocks=(
            Heading("Konzernrechnung 2025"),
            Text("Die Konzernrechnung der Beispiel Components AG ist nach den Fachempfehlungen zur "
                 "Rechnungslegung (Swiss GAAP FER) erstellt. Beträge in Mio. CHF.", TextStyle.LEAD),
            Grid(
                ("Inhalt", "Seite"),
                (
                    ("Konzernbilanz", "51"),
                    ("Konzernerfolgsrechnung", "52"),
                    ("Konzerngeldflussrechnung", "53"),
                    ("Konzerneigenkapitalnachweis", "54"),
                    ("Anhang zur Konzernrechnung", "55"),
                ),
                (140, 30),
            ),
        ),
    )


def balance_sheet() -> Page:
    notes = {"Forderungen aus Lieferungen und Leistungen": "6", "Vorräte": "7", "Sachanlagen": "8",
             "Immaterielle Werte": "8", "Kurzfristige Finanzverbindlichkeiten": "14",
             "Übrige kurzfristige Verbindlichkeiten": "13", "Kurzfristige Rückstellungen": "10",
             "Passive Rechnungsabgrenzungen": "13", "Langfristige Finanzverbindlichkeiten": "14",
             "Langfristige Rückstellungen": "10", "Übrige langfristige Verbindlichkeiten": "11"}
    rows: list[Row] = [Row("Aktiven", kind=RowKind.HEADING)]
    rows += [_item(label, pair, notes.get(label, "")) for label, pair in data.CURRENT_ASSETS.items()]
    rows.append(_item("Total Umlaufvermögen", data.TOTAL_CURRENT_ASSETS, kind=RowKind.SUBTOTAL))
    rows += [_item(label, pair, notes.get(label, "")) for label, pair in data.FIXED_ASSETS.items()]
    rows.append(_item("Total Anlagevermögen", data.TOTAL_FIXED_ASSETS, kind=RowKind.SUBTOTAL))
    rows.append(_item("Total Aktiven", data.TOTAL_ASSETS, kind=RowKind.TOTAL))
    rows.append(Row("Passiven", kind=RowKind.HEADING))
    rows += [_item(label, pair, notes.get(label, "")) for label, pair in data.SHORT_TERM_LIABILITIES.items()]
    rows.append(_item("Total kurzfristiges Fremdkapital", data.TOTAL_SHORT_TERM, kind=RowKind.SUBTOTAL))
    rows += [_item(label, pair, notes.get(label, "")) for label, pair in data.LONG_TERM_LIABILITIES.items()]
    rows.append(_item("Total langfristiges Fremdkapital", data.TOTAL_LONG_TERM, kind=RowKind.SUBTOTAL))
    rows.append(_item("Total Fremdkapital", data.TOTAL_LIABILITIES, kind=RowKind.SUBTOTAL))
    rows += [_item(label, pair) for label, pair in data.EQUITY.items()]
    rows.append(_item("Total Eigenkapital", data.TOTAL_EQUITY, kind=RowKind.SUBTOTAL))
    rows.append(_item("Total Passiven", data.TOTAL_ASSETS, kind=RowKind.TOTAL))
    return Page(
        section="Konzernrechnung",
        blocks=(
            Heading("Konzernbilanz per 31. Dezember 2025"),
            Statement(("in Mio. CHF", "Anhang", "31.12.2025", "31.12.2024"), tuple(rows), STATEMENT_WIDTHS),
        ),
    )


def income_statement() -> Page:
    notes = {"Personalaufwand": "11", "Abschreibungen auf Sachanlagen": "8",
             "Abschreibungen auf immateriellen Werten": "8"}
    rows = [
        _item("Nettoerlöse aus Lieferungen und Leistungen", data.REVENUE),
        *[_item(label, _negative(pair), notes.get(label, "")) for label, pair in data.OPERATING_EXPENSES.items()],
        _item("Total Betriebsaufwand", _negative(data.TOTAL_OPERATING_EXPENSES), kind=RowKind.SUBTOTAL),
        _item("Betriebsergebnis (EBIT)", data.EBIT, kind=RowKind.SUBTOTAL),
        _item("Finanzertrag", data.FINANCIAL_INCOME),
        _item("Finanzaufwand", _negative(data.FINANCIAL_EXPENSE), "14"),
        _item("Ordentliches Ergebnis vor Steuern", data.EBT, kind=RowKind.SUBTOTAL),
        _item("Ertragssteuern", _negative(data.INCOME_TAXES), "12"),
        _item("Konzerngewinn", data.NET_INCOME, kind=RowKind.TOTAL),
    ]
    return Page(
        section="Konzernrechnung",
        blocks=(
            Heading("Konzernerfolgsrechnung 2025"),
            Statement(("in Mio. CHF", "Anhang", "2025", "2024"), tuple(rows), STATEMENT_WIDTHS),
            Text("Gesamtkostenverfahren. Aufwände mit negativem Vorzeichen. Der Konzerngewinn ist vollständig den "
                 "Aktionären der Beispiel Components AG zuzurechnen.", TextStyle.SMALL),
        ),
    )


def cash_flow_statement() -> Page:
    rows = [
        _item("Konzerngewinn", data.NET_INCOME),
        *[_item(label, pair) for label, pair in data.OPERATING_CASH_FLOW_ITEMS.items()],
        _item("Geldfluss aus Geschäftstätigkeit", data.OPERATING_CASH_FLOW, kind=RowKind.SUBTOTAL),
        *[_item(label, pair) for label, pair in data.INVESTING_ITEMS.items()],
        _item("Geldfluss aus Investitionstätigkeit", data.INVESTING_CASH_FLOW, kind=RowKind.SUBTOTAL),
        *[_item(label, pair) for label, pair in data.FINANCING_ITEMS.items()],
        _item("Geldfluss aus Finanzierungstätigkeit", data.FINANCING_CASH_FLOW, kind=RowKind.SUBTOTAL),
        _item("Umrechnungsdifferenzen auf flüssigen Mitteln", data.TRANSLATION_EFFECT_ON_CASH),
        _item("Veränderung der flüssigen Mittel", data.CASH_CHANGE, kind=RowKind.TOTAL),
    ]
    return Page(
        section="Konzernrechnung",
        blocks=(
            Heading("Konzerngeldflussrechnung 2025"),
            Statement(("in Mio. CHF", "", "2025", "2024"), tuple(rows), STATEMENT_WIDTHS),
            Text("Indirekte Methode. Der Fonds umfasst die flüssigen Mittel gemäss Konzernbilanz (Seite 51).",
                 TextStyle.SMALL),
        ),
    )


def equity_statement() -> Page:
    return Page(
        section="Konzernrechnung",
        blocks=(
            Heading("Konzerneigenkapitalnachweis"),
            Grid(
                ("in Mio. CHF", "Aktienkapital", "Kapitalreserven", "Gewinnreserven", "Umrechnung", "Total"),
                (
                    ("Stand 1. Januar 2024", "5.0", "3.2", "27.5", "-1.8", "33.9"),
                    ("Dividende", "", "", "-2.5", "", "-2.5"),
                    ("Konzerngewinn 2024", "", "", "8.5", "", "8.5"),
                    ("Umrechnungsdifferenzen", "", "", "", "-0.3", "-0.3"),
                    ("Stand 31. Dezember 2024", "5.0", "3.2", "33.5", "-2.1", "39.6"),
                    ("Dividende", "", "", "-3.0", "", "-3.0"),
                    ("Konzerngewinn 2025", "", "", mio(data.NET_INCOME[0]), "", mio(data.NET_INCOME[0])),
                    ("Umrechnungsdifferenzen", "", "", "", "-0.5", "-0.5"),
                    ("Stand 31. Dezember 2025", "5.0", "3.2", mio(data.EQUITY["Gewinnreserven"][0]), "-2.6",
                     mio(data.TOTAL_EQUITY[0])),
                ),
                (44, 24, 28, 28, 24, 22),
                total_last=True,
            ),
            Text("Das Aktienkapital besteht aus 500'000 Namenaktien zu CHF 10. Die Gesellschaft hält keine eigenen "
                 "Aktien. Goodwill aus Akquisitionen wird im Erwerbszeitpunkt mit den Gewinnreserven verrechnet "
                 "(Anhang Ziff. 8).", TextStyle.SMALL),
        ),
    )


def note_principles() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("Anhang zur Konzernrechnung 2025"),
            Heading("1. Grundsätze der Konzernrechnungslegung", 2),
            Text("Die Konzernrechnung wurde in Übereinstimmung mit den gesamten Fachempfehlungen zur "
                 "Rechnungslegung (Swiss GAAP FER) und dem schweizerischen Gesetz erstellt. Sie vermittelt ein den "
                 "tatsächlichen Verhältnissen entsprechendes Bild der Vermögens-, Finanz- und Ertragslage. Die "
                 "Konzernrechnung basiert auf historischen Anschaffungs- oder Herstellkosten, mit Ausnahme der "
                 "Wertschriften, die zu aktuellen Werten bewertet werden."),
            Heading("2. Konsolidierungsgrundsätze", 2),
            Text("Gesellschaften, an denen die Beispiel Components AG direkt oder indirekt mehr als 50 % der "
                 "Stimmrechte hält, werden vollkonsolidiert. Die Kapitalkonsolidierung erfolgt nach der "
                 "Erwerbsmethode. Konzerninterne Forderungen, Verbindlichkeiten, Aufwände, Erträge und "
                 "Zwischengewinne werden eliminiert. Abschlussstichtag aller Gesellschaften ist der 31. Dezember."),
        ),
    )


def note_scope() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("3. Konsolidierungskreis", 2),
            Grid(
                ("Gesellschaft", "Sitz", "Tätigkeit", "Kapital", "Anteil"),
                (
                    ("Beispiel Components AG", "Beispielfeld, CH", "Produktion, Holding", "CHF 5.0 Mio.", "Mutter"),
                    ("Beispiel Sensorik AG", "Beispieltal, CH", "Produktion", "CHF 0.5 Mio.", "100 %"),
                    ("Beispiel Components GmbH", "Deutschland", "Produktion, Vertrieb", "EUR 1.0 Mio.", "100 %"),
                    ("Beispiel Components s.r.o.", "Tschechien", "Produktion", "CZK 50 Mio.", "100 %"),
                    ("Beispiel Components Inc.", "USA", "Vertrieb, Service", "USD 1.0 Mio.", "100 %"),
                ),
                (50, 33, 37, 28, 22),
                numeric_from=3,
            ),
            Text("Der Konsolidierungskreis blieb gegenüber dem Vorjahr unverändert."),
        ),
    )


def note_valuation() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("4. Bewertungsgrundsätze", 2),
            Bullets((
                "Flüssige Mittel: Kassenbestände, Post- und Bankguthaben sowie Festgelder mit einer Laufzeit von "
                "höchstens 90 Tagen, zum Nominalwert.",
                "Forderungen: zum Nominalwert abzüglich betriebswirtschaftlich notwendiger Wertberichtigungen.",
                "Vorräte: zu Anschaffungs- oder Herstellkosten, höchstens zum Netto-Marktwert; gleitender "
                "Durchschnitt, Wertberichtigung für Bestände mit geringem Umschlag.",
                "Sachanlagen: zu Anschaffungs- oder Herstellkosten abzüglich linearer Abschreibungen. "
                "Nutzungsdauern: Gebäude 30 bis 40 Jahre, Maschinen 8 bis 12 Jahre, Werkzeuge und Informatik 3 "
                "bis 5 Jahre.",
                "Immaterielle Werte: Software und Patente, linear über 3 bis 5 Jahre abgeschrieben.",
                "Rückstellungen: für Garantien, Restrukturierungen und Rechtsfälle, wenn ein Ereignis in der "
                "Vergangenheit eine Verpflichtung begründet und der Mittelabfluss zuverlässig schätzbar ist.",
                "Derivative Finanzinstrumente: siehe Ziff. 9.",
            )),
        ),
    )


def note_currency_translation() -> Page:
    rates = data.YEAR_END_RATES
    return Page(
        section="Anhang",
        blocks=(
            Heading("5. Fremdwährungsumrechnung", 2),
            Text("Die Konzernrechnung wird in Schweizer Franken dargestellt. Bilanzen der ausländischen "
                 "Gesellschaften werden zu Stichtagskursen, Erfolgs- und Geldflussrechnungen zu "
                 "Jahresdurchschnittskursen umgerechnet. Umrechnungsdifferenzen werden im Eigenkapital erfasst. "
                 "Transaktionen in Fremdwährung werden zum Kurs des Transaktionstags umgerechnet; Kursdifferenzen "
                 "sind im Finanzergebnis enthalten."),
            Grid(
                ("Kurse in CHF", "Stichtag 2025", "Stichtag 2024", "Durchschnitt 2025", "Durchschnitt 2024"),
                (
                    ("1 EUR", f"{rates['EUR'][0]}", f"{rates['EUR'][1]}", "0.9400", "0.9520"),
                    ("1 USD", f"{rates['USD'][0]}", f"{rates['USD'][1]}", "0.8000", "0.8800"),
                    ("1 CZK", f"{rates['CZK'][0]}", f"{rates['CZK'][1]}", "0.0375", "0.0378"),
                ),
                (50, 30, 30, 30, 30),
            ),
        ),
    )


def note_receivables_inventories() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("6. Forderungen aus Lieferungen und Leistungen", 2),
            Grid(
                ("in Mio. CHF", "31.12.2025", "31.12.2024"),
                (("Forderungen gegenüber Dritten", "22.3", "21.7"), ("Wertberichtigungen", "-0.7", "-0.8"),
                 ("Total", "21.6", "20.9")),
                NOTE_TABLE_WIDTHS,
                total_last=True,
            ),
            Heading("7. Vorräte", 2),
            Grid(
                ("in Mio. CHF", "31.12.2025", "31.12.2024"),
                (("Rohmaterial und Kaufteile", "9.8", "10.3"), ("Halbfabrikate und Ware in Arbeit", "8.4", "8.6"),
                 ("Fertigfabrikate und Ersatzteile", "7.6", "7.9"), ("Wertberichtigungen", "-1.5", "-1.7"),
                 ("Total", "24.3", "25.1")),
                NOTE_TABLE_WIDTHS,
                total_last=True,
            ),
        ),
    )


def note_fixed_assets() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("8. Sachanlagen und immaterielle Werte", 2),
            Grid(
                ("in Mio. CHF", "Grundstücke und Bauten", "Maschinen und Anlagen", "Übrige Sachanlagen",
                 "Immaterielle Werte"),
                (
                    ("Nettobuchwert 1. Januar 2025", "14.6", "14.8", "2.5", "2.6"),
                    ("Zugänge", "0.6", "4.4", "0.8", "0.4"),
                    ("Abschreibungen", "-0.8", "-4.8", "-0.9", "-0.7"),
                    ("Nettobuchwert 31. Dezember 2025", "14.4", "14.4", "2.4", "2.3"),
                ),
                (54, 29, 29, 29, 29),
                total_last=True,
            ),
            Text("Umrechnungsdifferenzen auf den Sachanlagen der ausländischen Gesellschaften waren 2025 "
                 "unwesentlich. Goodwill aus der Übernahme der Beispiel Sensorik AG im Jahr 2019 wurde mit den "
                 "Gewinnreserven verrechnet. Eine theoretische Aktivierung mit Abschreibung über fünf Jahre hätte "
                 "keine Auswirkung mehr auf das Ergebnis 2025, weil der Goodwill vollständig abgeschrieben wäre."),
        ),
    )


def note_derivatives() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("9. Derivative Finanzinstrumente", 2),
            Text("Die Gruppe sichert erwartete Zuflüsse in EUR und USD mit Devisentermingeschäften und "
                 "Devisenoptionen ab. Devisenoptionen werden ausschliesslich als Zero-Cost-Collars eingesetzt "
                 "(Kauf einer Put-Option und Verkauf einer Call-Option mit gleichem Volumen und gleicher "
                 "Laufzeit). Alle Geschäfte schliesst Group Treasury mit Banken mit einem Rating von mindestens "
                 "A- ab; die Laufzeiten betragen höchstens zwölf Monate."),
            Grid(
                ("in Mio. CHF", "Volumen 2025", "Volumen 2024", "Aktive WBW 2025", "Passive WBW 2025", "Zweck"),
                (
                    ("Devisentermingeschäfte", mio(data.FX_FORWARDS[0]), mio(data.FX_FORWARDS[1]), "0.6", "0.2",
                     "Absicherung"),
                    ("Devisenoptionen (Zero-Cost-Collars)", mio(data.FX_COLLARS[0]), mio(data.FX_COLLARS[1]), "0.1",
                     "0.1", "Absicherung"),
                    ("Zinsderivate", "–", "–", "–", "–", "–"),
                ),
                (62, 22, 22, 20, 20, 24),
            ),
            Grid(
                ("Kontraktvolumen nach Währung, in Mio. CHF", "Termingeschäfte", "Collars"),
                (("EUR", "26.0", "8.0"), ("USD", "12.0", "4.0")),
                NOTE_TABLE_WIDTHS,
            ),
            Text("Volumen: Kontraktvolumen. WBW: Wiederbeschaffungswert. Absicherungsgeschäfte für künftige Geldflüsse werden gemäss Swiss "
                 "GAAP FER 27 bis zur Erfassung des Grundgeschäfts nicht bilanziert, sondern hier offengelegt."),
        ),
    )


def note_provisions() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("10. Rückstellungen", 2),
            Grid(
                ("in Mio. CHF", "Garantien", "Latente Steuern", "Übrige", "Total"),
                (
                    ("Stand 1. Januar 2025", "2.6", "2.9", "0.8", "6.3"),
                    ("Bildung", "1.1", "0.3", "0.4", "1.8"),
                    ("Verwendung", "-0.8", "0.0", "-0.3", "-1.1"),
                    ("Auflösung", "-0.2", "0.0", "-0.1", "-0.3"),
                    ("Stand 31. Dezember 2025", "2.7", "3.2", "0.8", "6.7"),
                    ("davon kurzfristig", "1.8", "0.0", "0.8", "2.6"),
                ),
                (70, 25, 25, 25, 25),
            ),
            Text("Garantierückstellungen decken Gewährleistungen aus Lieferungen der letzten zwei Jahre. Latente "
                 "Steuern betreffen vor allem steuerlich zulässige Abschreibungen und Wertberichtigungen in den "
                 "Einzelabschlüssen."),
        ),
    )


def note_pensions() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("11. Personalvorsorge", 2),
            Text("Die Schweizer Gesellschaften sind einer rechtlich selbständigen Sammelstiftung angeschlossen "
                 "(Beitragsprimat). Die ausländischen Gesellschaften haben beitragsorientierte Pläne. Gemäss "
                 "Swiss GAAP FER 16 bestehen weder wirtschaftlicher Nutzen noch wirtschaftliche Verpflichtungen "
                 "aus Über- oder Unterdeckungen."),
            Grid(
                ("in Mio. CHF", "2025", "2024"),
                (("Vorsorgeaufwand im Personalaufwand", "3.9", "3.7"),
                 ("Deckungsgrad der Sammelstiftung per 31.12.", "112 %", "108 %"),
                 ("Verbindlichkeiten gegenüber Vorsorgeeinrichtungen", "0.4", "0.3")),
                NOTE_TABLE_WIDTHS,
            ),
            Text("Die übrigen langfristigen Verbindlichkeiten enthalten Verpflichtungen für Dienstaltersgeschenke, "
                 "Abgangsentschädigungen in Deutschland und Tschechien sowie die langfristigen Anteile der "
                 "Erfolgsbeteiligung."),
        ),
    )


def note_taxes() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("12. Ertragssteuern", 2),
            Grid(
                ("in Mio. CHF", "2025", "2024"),
                (("Laufende Ertragssteuern", "2.1", "1.8"), ("Latente Ertragssteuern", "0.3", "0.2"),
                 ("Total Ertragssteuern", mio(data.INCOME_TAXES[0]), mio(data.INCOME_TAXES[1]))),
                NOTE_TABLE_WIDTHS,
                total_last=True,
            ),
            Text("Der erwartete durchschnittliche Steuersatz beträgt 19.8 % (Vorjahr 19.6 %). Steuerliche "
                 "Verlustvorträge bestehen nicht. Die Gruppe fällt nicht in den Anwendungsbereich der globalen "
                 "Mindeststeuer, weil der Umsatz unter EUR 750 Mio. liegt."),
        ),
    )


def note_other_liabilities() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("13. Übrige Verbindlichkeiten und passive Rechnungsabgrenzungen", 2),
            Grid(
                ("in Mio. CHF", "31.12.2025", "31.12.2024"),
                (
                    ("Anzahlungen von Kunden", "2.8", "2.6"),
                    ("Sozialversicherungen und Vorsorgeeinrichtungen", "1.4", "1.3"),
                    ("Mehrwertsteuer und übrige Steuern", "1.7", "1.7"),
                    ("Total übrige kurzfristige Verbindlichkeiten", "5.9", "5.6"),
                ),
                NOTE_TABLE_WIDTHS,
                total_last=True,
            ),
            Grid(
                ("in Mio. CHF", "31.12.2025", "31.12.2024"),
                (
                    ("Ferien, Überzeit und Erfolgsbeteiligung", "4.6", "4.3"),
                    ("Ausstehende Rechnungen und übrige Abgrenzungen", "3.5", "3.3"),
                    ("Total passive Rechnungsabgrenzungen", "8.1", "7.6"),
                ),
                NOTE_TABLE_WIDTHS,
                total_last=True,
            ),
        ),
    )


def note_financial_liabilities() -> Page:
    loan = data.SARON_LOAN
    short = data.SHORT_TERM_LIABILITIES["Kurzfristige Finanzverbindlichkeiten"]
    long = data.LONG_TERM_LIABILITIES["Langfristige Finanzverbindlichkeiten"]
    return Page(
        section="Anhang",
        blocks=(
            Heading("14. Finanzverbindlichkeiten", 2),
            Grid(
                ("in Mio. CHF", "31.12.2025", "31.12.2024"),
                (
                    _pair_row("Konsortialkredit, variabel verzinst (Basis SARON)", loan),
                    _pair_row("davon kurzfristig (Amortisation innert 12 Monaten)", short),
                    _pair_row("davon langfristig", long),
                ),
                NOTE_TABLE_WIDTHS,
            ),
            Grid(
                ("Fälligkeiten per 31.12.2025, in Mio. CHF", "Betrag"),
                (("innert 1 Jahr", mio(short[0])), ("1 bis 5 Jahre", mio(long[0])), ("über 5 Jahre", "0.0")),
                (140, 30),
            ),
            Bullets((
                "Der Konsortialkredit mit zwei Schweizer Banken läuft bis 30. Juni 2029 und wird mit CHF 5.0 Mio. "
                "pro Jahr amortisiert.",
                "Zinssatz: Compounded SARON zuzüglich einer Marge von 0.95 % bis 1.45 % je nach Verhältnis von "
                "Nettoverschuldung zu EBITDA; per 31. Dezember 2025 gilt die tiefste Stufe.",
                "Financial Covenants: Eigenkapitalquote mindestens 35 %, Nettoverschuldung zu EBITDA höchstens "
                "2.5; beide per Bilanzstichtag eingehalten.",
                f"Zusätzlich steht eine Betriebskreditlimite von CHF {mio(data.UNUSED_CREDIT_LINE)} Mio. zur "
                "Verfügung, die per Bilanzstichtag nicht beansprucht war.",
            )),
            Text("Zinsänderungsrisiko: Ein Anstieg des SARON um 1 Prozentpunkt erhöht den Zinsaufwand auf Basis "
                 "des Bestands per Bilanzstichtag um rund CHF 0.25 Mio. pro Jahr. Zinsderivate bestehen nicht."),
        ),
    )


def note_leasing_contingencies() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("15. Leasingverpflichtungen", 2),
            Grid(
                ("Nicht bilanzierte Leasing- und Mietverpflichtungen, in Mio. CHF", "31.12.2025", "31.12.2024"),
                (("Fällig innert 1 Jahr", "1.2", "1.1"), ("Fällig in 1 bis 5 Jahren", "3.4", "3.6"),
                 ("Fällig nach 5 Jahren", "0.9", "1.2")),
                NOTE_TABLE_WIDTHS,
            ),
            Heading("16. Eventualverpflichtungen und verpfändete Aktiven", 2),
            Text("Gegenüber Kunden bestehen Anzahlungs- und Erfüllungsgarantien von CHF 2.3 Mio. (Vorjahr CHF "
                 "2.1 Mio.). Zur Sicherung des Konsortialkredits sind die Aktien der Tochtergesellschaften "
                 "verpfändet. Weitere Aktiven sind nicht verpfändet. Rechtsfälle mit wesentlichem Risiko bestehen "
                 "nicht."),
        ),
    )


def note_related_parties_audit_fee() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("17. Transaktionen mit nahestehenden Personen", 2),
            Text("Die Beispiel Holding AG erhielt Dividenden entsprechend ihrem Anteil am Aktienkapital. Mit "
                 "Gesellschaften der Gründerfamilie bestehen Mietverträge für zwei Lagerhallen am Hauptsitz zu "
                 "marktüblichen Bedingungen; der Mietaufwand betrug CHF 0.3 Mio. (Vorjahr CHF 0.3 Mio.). Weitere "
                 "wesentliche Transaktionen mit nahestehenden Personen fanden nicht statt."),
            Heading("18. Honorar der Revisionsstelle", 2),
            Grid(
                ("in Mio. CHF", "2025", "2024"),
                (("Revisionsdienstleistungen", "0.21", "0.20"), ("Steuerberatung", "0.04", "0.05")),
                NOTE_TABLE_WIDTHS,
            ),
        ),
    )


def note_events_approval() -> Page:
    return Page(
        section="Anhang",
        blocks=(
            Heading("19. Ereignisse nach dem Bilanzstichtag", 2),
            Text("Nach dem Bilanzstichtag sind keine Ereignisse eingetreten, welche die Aussagekraft der "
                 "Konzernrechnung 2025 wesentlich beeinträchtigen."),
            Heading("20. Genehmigung der Konzernrechnung", 2),
            Text(f"Der Verwaltungsrat hat die Konzernrechnung am {data.APPROVAL_DATE} genehmigt. Sie unterliegt der "
                 "Genehmigung durch die Generalversammlung vom 8. Mai 2026."),
        ),
    )


def audit_report_one() -> Page:
    return Page(
        section="Bericht der Revisionsstelle",
        blocks=(
            Heading("Bericht der Revisionsstelle"),
            Text("an die Generalversammlung der Beispiel Components AG, Beispielfeld", TextStyle.LEAD),
            Heading("Bericht zur Prüfung der Konzernrechnung", 2),
            Heading("Prüfungsurteil", 3),
            Text("Wir haben die Konzernrechnung der Beispiel Components AG und ihrer Tochtergesellschaften – "
                 "bestehend aus der Konzernbilanz zum 31. Dezember 2025, der Konzernerfolgsrechnung, der "
                 "Konzerngeldflussrechnung und dem Konzerneigenkapitalnachweis für das dann endende Jahr sowie dem "
                 "Anhang – geprüft (Seiten 51 bis 69)."),
            Text("Nach unserer Beurteilung vermittelt die beigefügte Konzernrechnung in allen wesentlichen "
                 "Belangen ein den tatsächlichen Verhältnissen entsprechendes Bild der Vermögens- und Finanzlage "
                 "des Konzerns zum 31. Dezember 2025 sowie dessen Ertragslage und Geldflüsse für das dann endende "
                 "Jahr in Übereinstimmung mit Swiss GAAP FER und entspricht dem schweizerischen Gesetz."),
            Heading("Grundlage für das Prüfungsurteil", 3),
            Text("Wir haben unsere Abschlussprüfung in Übereinstimmung mit dem schweizerischen Gesetz und den "
                 "Schweizer Standards zur Abschlussprüfung durchgeführt. Wir sind von der Gruppe unabhängig in "
                 "Übereinstimmung mit den schweizerischen gesetzlichen Vorschriften und den Anforderungen des "
                 "Berufsstands und haben unsere sonstigen beruflichen Verhaltenspflichten erfüllt. Wir sind der "
                 "Auffassung, dass die erlangten Prüfungsnachweise ausreichend und angemessen sind, um als "
                 "Grundlage für unser Prüfungsurteil zu dienen."),
            Heading("Sonstige Informationen", 3),
            Text("Der Verwaltungsrat ist für die sonstigen Informationen verantwortlich. Diese umfassen die im "
                 "Geschäftsbericht enthaltenen Informationen, aber nicht die Konzernrechnung und unseren "
                 "Bericht dazu. Unser Prüfungsurteil erstreckt sich nicht auf die sonstigen Informationen."),
        ),
    )


def audit_report_two() -> Page:
    return Page(
        section="Bericht der Revisionsstelle",
        blocks=(
            Heading("Verantwortlichkeiten des Verwaltungsrates", 3),
            Text("Der Verwaltungsrat ist verantwortlich für die Aufstellung einer Konzernrechnung, die in "
                 "Übereinstimmung mit Swiss GAAP FER und den gesetzlichen Vorschriften ein den tatsächlichen "
                 "Verhältnissen entsprechendes Bild vermittelt, und für die internen Kontrollen, die er als "
                 "notwendig feststellt, um die Aufstellung einer Konzernrechnung zu ermöglichen, die frei von "
                 "wesentlichen falschen Darstellungen aufgrund von dolosen Handlungen oder Irrtümern ist."),
            Heading("Verantwortlichkeiten der Revisionsstelle", 3),
            Text("Unsere Ziele sind, hinreichende Sicherheit darüber zu erlangen, ob die Konzernrechnung als "
                 "Ganzes frei von wesentlichen falschen Darstellungen ist, und einen Bericht abzugeben, der unser "
                 "Prüfungsurteil beinhaltet. Hinreichende Sicherheit ist ein hohes Mass an Sicherheit, aber keine "
                 "Garantie dafür, dass eine Prüfung immer eine wesentliche falsche Darstellung aufdeckt, falls "
                 "eine solche vorliegt."),
            Heading("Bericht zu sonstigen gesetzlichen und anderen rechtlichen Anforderungen", 2),
            Text("In Übereinstimmung mit Art. 728a Abs. 1 Ziff. 3 OR und dem Schweizer Prüfungsstandard 890 "
                 "bestätigen wir, dass ein gemäss den Vorgaben des Verwaltungsrates ausgestaltetes internes "
                 "Kontrollsystem für die Aufstellung der Konzernrechnung existiert. Wir empfehlen, die vorliegende "
                 "Konzernrechnung zu genehmigen."),
            Space(4),
            Text(f"Fiktiva Wirtschaftsprüfung AG · Simon Platzhalter, zugelassener Revisionsexperte, leitender "
                 f"Revisor · {data.PLACE}, {data.APPROVAL_DATE}", TextStyle.SMALL),
        ),
    )


def pages() -> tuple[Page, ...]:
    return (
        finance_divider(), commentary_results(), commentary_balance_sheet(), commentary_cash_flow(),
        commentary_currencies(), five_year_overview(), consolidated_divider(), balance_sheet(), income_statement(),
        cash_flow_statement(), equity_statement(), note_principles(), note_scope(), note_valuation(),
        note_currency_translation(), note_receivables_inventories(), note_fixed_assets(), note_derivatives(),
        note_provisions(), note_pensions(), note_taxes(), note_other_liabilities(), note_financial_liabilities(),
        note_leasing_contingencies(), note_related_parties_audit_fee(), note_events_approval(), audit_report_one(),
        audit_report_two(),
    )
