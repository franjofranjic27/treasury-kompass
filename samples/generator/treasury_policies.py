"""Treasury-Policies der beiden fiktiven Demo-Kunden.

Die Werte passen zu den Beraterantworten im Prototyp (CLIENTS.*.answers): Ziel der Absicherung,
maximale Absicherungsquote (80 % bzw. 90 %), Anlagehorizont, tragbarer Wertrückgang und zugelassene
Instrumente. Die Fragen unter `goals.*` und `risk.*` sind nicht extrahierbar; die Policies dienen dem
Berater und dem Chat als Beleg (z.B. Testfall 3 in docs/specs/agent-testcases.md).
"""

from __future__ import annotations

from reportlab.lib import colors

from .layout import (
    Block,
    Bullets,
    DocumentSpec,
    Grid,
    Heading,
    Page,
    Space,
    Text,
    TextStyle,
    Theme,
    require_on_page,
)

SECTION = "Treasury-Policy"


def _title_block(company: str, version: str, approval: str) -> tuple[Block, ...]:
    return (
        Text(company, TextStyle.SUBTITLE),
        Heading("Treasury-Policy"),
        Text(f"Version {version} · {approval}", TextStyle.LEAD),
    )


def _signature(place: str, date: str, signatories: tuple[str, str]) -> tuple[Block, ...]:
    return (
        Space(4),
        Grid(("Für den Verwaltungsrat", ""), (signatories,), (85, 85), numeric_from=2),
        Text(f"{place}, {date}", TextStyle.SMALL),
    )


# --- Muster Maschinenbau AG -----------------------------------------------------------------


def build_muster() -> DocumentSpec:
    company = "Muster Maschinenbau AG"
    pages = (
        Page(
            section=SECTION,
            blocks=(
                *_title_block(company, "2.0", "genehmigt vom Verwaltungsrat am 24. Oktober 2024, gültig ab "
                                              "1. Januar 2025"),
                Heading("1. Zweck und Geltungsbereich", 2),
                Text("Diese Policy regelt die Bewirtschaftung der flüssigen Mittel, die Anlage überschüssiger "
                     "Liquidität und die Absicherung von Währungs- und Zinsrisiken der Muster Maschinenbau AG. Sie "
                     "gilt für alle Finanzgeschäfte der Gesellschaft."),
                Heading("2. Grundsätze", 2),
                Bullets((
                    "Sicherheit und Verfügbarkeit gehen vor Rendite.",
                    "Finanzgeschäfte dienen ausschliesslich der Absicherung des operativen Geschäfts. Spekulation "
                    "ist verboten.",
                    "Abgesichert werden nur bestehende oder budgetierte Grundgeschäfte, nie mehr als deren Betrag.",
                    "Es werden nur Instrumente eingesetzt, die die verantwortlichen Personen verstehen und "
                    "bewerten können.",
                )),
                Heading("3. Organisation und Kompetenzen", 2),
                Grid(
                    ("Stelle", "Aufgaben"),
                    (
                        ("Verwaltungsrat", "Genehmigt die Policy, überprüft sie jährlich, erhält quartalsweise "
                                           "einen Bericht"),
                        ("Geschäftsführer", "Genehmigt Absicherungen über EUR 1.0 Mio. je Geschäft"),
                        ("Leiter Finanzen und Administration", "Liquiditätsplanung, Anlagen und Absicherungen "
                                                               "innerhalb der Policy, Abschluss mit der Hausbank"),
                    ),
                    (55, 115),
                    numeric_from=2,
                ),
                Text("Eine eigene Treasury-Stelle besteht nicht. Die Aufgaben übernimmt der Leiter Finanzen und "
                     "Administration neben seinen übrigen Aufgaben."),
            ),
        ),
        Page(
            section=SECTION,
            blocks=(
                Heading("4. Liquidität und Anlagen", 2),
                Text("Die Gesellschaft hält jederzeit eine Mindestliquidität von 1.5 Monaten Betriebsaufwand gemäss "
                     "der letzten Jahresrechnung. Überschüssige Liquidität darf angelegt werden, muss aber innerhalb "
                     "eines Jahres verfügbar sein."),
                Grid(
                    ("Zulässige Anlage", "Bedingungen", "Höchstanteil"),
                    (
                        ("Festgeld und Callgeld in CHF", "Laufzeit bis 12 Monate, Banken mit Rating mindestens A",
                         "100 %"),
                        ("Geldmarktfonds in CHF", "täglich handelbar", "50 %"),
                        ("Obligationen in CHF", "Rating mindestens A-, Restlaufzeit bis 2 Jahre", "20 %"),
                    ),
                    (50, 90, 30),
                ),
                Text("Nicht zulässig sind Aktien, Anlagen in Fremdwährung und strukturierte Produkte. Ein "
                     "vorübergehender Wertrückgang der Anlagen von höchstens 2 % innert 12 Monaten wird toleriert."),
                Heading("5. Währungsmanagement", 2),
                Text("Ziel ist Budgetsicherheit: Der im Budget verwendete Plankurs EUR/CHF soll für den grösseren "
                     "Teil der erwarteten Zuflüsse gesichert sein. Grundgeschäft ist die budgetierte Nettoposition "
                     "in EUR (Fakturierung minus Einkauf) der nächsten 12 Monate."),
                Bullets((
                    "Die Absicherungsquote beträgt höchstens 80 % der budgetierten Nettoposition je Währung. Eine "
                    "Absicherung über 100 % der Nettoposition ist in jedem Fall verboten.",
                    "Die Absicherung erfolgt gestaffelt über bis zu vier Quartale; nähere Quartale werden höher "
                    "abgesichert als spätere.",
                    "Zulässige Instrumente: Devisentermingeschäfte und Devisenswaps zur Anpassung von Fälligkeiten. "
                    "Optionen, Zero-Cost-Collars und andere Derivate sind nicht zugelassen.",
                    "Positionen in anderen Währungen werden abgesichert, wenn sie CHF 0.5 Mio. übersteigen.",
                )),
            ),
        ),
        Page(
            section=SECTION,
            blocks=(
                Heading("6. Zinsmanagement", 2),
                Text("Bankkredite werden variabel auf Basis SARON aufgenommen. Eine Absicherung des Zinsrisikos "
                     "mit Zinsswaps bedarf der Zustimmung des Verwaltungsrates."),
                Heading("7. Gegenparteien", 2),
                Text("Finanzgeschäfte werden mit der Hausbank und höchstens einer weiteren Schweizer Bank mit "
                     "einem Rating von mindestens A abgeschlossen."),
                Heading("8. Berichterstattung", 2),
                Text("Der Leiter Finanzen und Administration berichtet dem Verwaltungsrat quartalsweise über "
                     "Liquidität, Anlagen, offene Absicherungen mit Absicherungsquote und Marktwerten sowie über "
                     "Abweichungen von der Policy."),
                Heading("9. Inkraftsetzung", 2),
                Text("Die Policy tritt am 1. Januar 2025 in Kraft und ersetzt die Version 1.3 vom März 2021."),
                *_signature("Musterwil", "24. Oktober 2024", ("Hans Muster, Präsident", "Dr. Claudia Beispiel, Mitglied")),
            ),
        ),
    )
    spec = DocumentSpec(
        filename="Treasury-Policy_Muster_Maschinenbau.pdf",
        title=f"Treasury-Policy – {company} (fiktives Beispiel)",
        subject="Fiktives Beispiel – Hackathon Treasury Kompass: Treasury-Policy eines Export-KMU",
        running_header=f"{company} · Treasury-Policy, Version 2.0",
        theme=Theme(accent=colors.HexColor("#1F3A5F")),
        pages=pages,
    )
    require_on_page(spec, 2, "höchstens 80 % der budgetierten Nettoposition", "Optionen, Zero-Cost-Collars",
                    "1.5 Monaten Betriebsaufwand", "höchstens 2 % innert 12 Monaten", "Budgetsicherheit")
    return spec


# --- Beispiel Components AG -----------------------------------------------------------------


def build_components() -> DocumentSpec:
    company = "Beispiel Components AG"
    pages = (
        Page(
            section=SECTION,
            blocks=(
                *_title_block(company, "3.1", "genehmigt vom Verwaltungsrat am 23. Oktober 2025, gültig ab "
                                              "1. November 2025, gilt für alle Konzerngesellschaften"),
                Heading("1. Zweck", 2),
                Text("Die Group Treasury Policy legt fest, wie die Gruppe Liquidität sichert, Finanzierungen "
                     "aufnimmt, überschüssige Mittel anlegt und Währungs-, Zins- und Gegenparteirisiken steuert."),
                Heading("2. Grundsätze", 2),
                Bullets((
                    "Treasury ist zentral organisiert. Tochtergesellschaften schliessen Finanzgeschäfte nur über "
                    "Group Treasury ab.",
                    "Ziel der Währungsabsicherung ist es, die Margen zu schützen und Chancen teilweise offen zu "
                    "lassen.",
                    "Finanzgeschäfte ohne Grundgeschäft (Spekulation) sind verboten.",
                    "Sicherheit und Verfügbarkeit der Mittel gehen vor Rendite.",
                )),
                Heading("3. Organisation", 2),
                Grid(
                    ("Gremium oder Stelle", "Aufgaben"),
                    (
                        ("Verwaltungsrat", "Genehmigt die Policy und die Limiten"),
                        ("Ausschuss für Prüfung und Risiko", "Überwacht die Einhaltung, prüft die Policy jährlich"),
                        ("Treasury-Komitee (CEO, CFO, Leiterin Group Treasury)",
                         "Legt monatlich die Absicherungsquoten innerhalb der Policy fest"),
                        ("Group Treasury (3 Vollzeitstellen)", "Cash-Pool, Finanzierung, Anlagen, Abschluss und "
                                                               "Abwicklung aller Finanzgeschäfte, Reporting"),
                    ),
                    (65, 105),
                    numeric_from=2,
                ),
            ),
        ),
        Page(
            section=SECTION,
            blocks=(
                Heading("4. Kompetenzen", 2),
                Grid(
                    ("Geschäft", "Group Treasury", "CFO", "Treasury-Komitee"),
                    (
                        ("Devisentermingeschäfte und Collars je Geschäft", "bis CHF 5 Mio.", "bis CHF 20 Mio.",
                         "darüber"),
                        ("Anlagen je Gegenpartei", "bis CHF 5 Mio.", "bis CHF 10 Mio.", "darüber"),
                        ("Aufnahme von Finanzierungen", "–", "bis CHF 5 Mio.", "darüber, ab CHF 20 Mio. "
                                                                               "Verwaltungsrat"),
                    ),
                    (65, 35, 35, 35),
                ),
                Heading("5. Liquidität und Anlagen", 2),
                Text("Die Gruppe hält jederzeit eine Mindestliquidität von 1.5 Monaten konsolidiertem "
                     "Betriebsaufwand. Überschüssige Liquidität wird gestaffelt angelegt; der Anlagehorizont "
                     "beträgt höchstens drei Jahre."),
                Grid(
                    ("Zulässige Anlage", "Bedingungen", "Höchstanteil"),
                    (
                        ("Festgeld und Callgeld", "Banken mit Rating mindestens A-", "100 %"),
                        ("Geldmarktfonds in CHF", "täglich handelbar", "60 %"),
                        ("Obligationen in CHF", "Investment Grade, Rating mindestens A-, Restlaufzeit bis 3 Jahre",
                         "70 %"),
                    ),
                    (45, 95, 30),
                ),
                Text("Ein vorübergehender Wertrückgang der Anlagen von höchstens 5 % innert 12 Monaten wird "
                     "toleriert. Aktien, Hedgefonds, Kryptowährungen und strukturierte Produkte mit "
                     "Kapitalrisiko sind nicht zugelassen."),
            ),
        ),
        Page(
            section=SECTION,
            blocks=(
                Heading("6. Währungsrisiken", 2),
                Text("Grundgeschäft sind die budgetierten Nettopositionen in EUR und USD (Umsatz minus Einkauf) "
                     "der nächsten 12 Monate. Umrechnungsrisiken aus der Konsolidierung werden nicht abgesichert."),
                Bullets((
                    "Die Absicherung erfolgt rollierend in vier Quartalstranchen. Die Quote nimmt mit der Laufzeit "
                    "ab.",
                    "Die Absicherungsquote beträgt je Tranche höchstens 90 % der budgetierten Nettoposition. Eine "
                    "Absicherung über 100 % der Nettoposition ist verboten.",
                    "Zulässige Instrumente: Devisentermingeschäfte, Devisenswaps und Zero-Cost-Collars. Ein Collar "
                    "besteht aus einer gekauften und einer verkauften Option mit gleichem Volumen und gleicher "
                    "Laufzeit; Netto-Stillhalterpositionen sind verboten.",
                    "Collars dürfen höchstens 40 % des abgesicherten Volumens ausmachen.",
                    "Laufzeit höchstens 12 Monate, für Projekte mit Zustimmung des Treasury-Komitees bis 24 Monate.",
                )),
                Heading("7. Zinsrisiken", 2),
                Text("Finanzverbindlichkeiten dürfen mit Zinsswaps oder Zinsbegrenzungen (Caps) bis höchstens 75 % "
                     "des Bestands abgesichert werden. Der Entscheid liegt beim Treasury-Komitee."),
            ),
        ),
        Page(
            section=SECTION,
            blocks=(
                Heading("8. Gegenparteien", 2),
                Text("Finanzgeschäfte werden nur mit Banken mit einem Rating von mindestens A- abgeschlossen. Je "
                     "Bank dürfen höchstens 40 % der Anlagen und höchstens 50 % des Absicherungsvolumens liegen. "
                     "Mit allen Gegenparteien bestehen Rahmenverträge für Derivate."),
                Heading("9. Berichterstattung", 2),
                Bullets((
                    "Monatlich an das Treasury-Komitee: Liquidität, Anlagen, Absicherungsquoten je Währung und "
                    "Tranche, Marktwerte, Limitenauslastung.",
                    "Quartalsweise an den Ausschuss für Prüfung und Risiko: Einhaltung der Policy, Abweichungen und "
                    "Massnahmen.",
                )),
                Heading("10. Inkraftsetzung", 2),
                Text("Die Policy tritt am 1. November 2025 in Kraft und ersetzt die Version 3.0 vom Januar 2023. "
                     "Neu sind die rollierende Absicherung in vier Quartalstranchen und die höhere Obergrenze von "
                     "90 %."),
                *_signature("Beispielfeld", "23. Oktober 2025",
                            ("Dr. Anna Beispiel, Präsidentin", "Thomas Exempel, Vizepräsident")),
            ),
        ),
    )
    spec = DocumentSpec(
        filename="Treasury-Policy_Beispiel_Components.pdf",
        title=f"Group Treasury Policy – {company} (fiktives Beispiel)",
        subject="Fiktives Beispiel – Hackathon Treasury Kompass: Treasury-Policy eines Mid-Caps",
        running_header=f"{company} · Group Treasury Policy, Version 3.1",
        theme=Theme(accent=colors.HexColor("#0F5C5C")),
        pages=pages,
    )
    require_on_page(spec, 3, "höchstens 90 % der budgetierten Nettoposition", "Zero-Cost-Collars")
    require_on_page(spec, 2, "1.5 Monaten konsolidiertem Betriebsaufwand", "höchstens drei Jahre",
                    "höchstens 5 % innert 12 Monaten")
    require_on_page(spec, 1, "Margen zu schützen", "Group Treasury (3 Vollzeitstellen)")
    return spec
