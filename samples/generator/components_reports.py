"""Geschäftsbericht Beispiel Components AG: Lagebericht (S. 32–37) und Segmentbericht (S. 38–43).

Fundstellen laut Prototyp: S. 34 «Group Treasury mit 3 Vollzeitstellen», S. 41 Umsatz und Einkauf
nach Währungen (EUR 85.4 / 33.0, USD 33.0 / 12.0).
"""

from __future__ import annotations

from decimal import Decimal as D

from . import components_data as data
from .layout import Bullets, Grid, Heading, Page, Text, TextStyle
from .numbers import growth, mio, percent, ratio, shares, signed_mio

SEGMENTS = tuple(data.SEGMENT_REVENUE)


def _expense_ratio(label: str, year: int) -> str:
    return percent(ratio(data.OPERATING_EXPENSES[label][year], data.REVENUE[year]))


def business_development() -> Page:
    return Page(
        section="Lagebericht",
        blocks=(
            Heading("Lagebericht 2025"),
            Heading("Geschäftsverlauf", 2),
            Text(f"Der Nettoerlös der Gruppe stieg um {percent(growth(*data.REVENUE))}. Der starke Franken "
                 "verringerte den Nettoerlös in Franken um rund 2.6 Prozentpunkte; in Lokalwährungen betrug das "
                 "Wachstum 7.2 %. Am stärksten wuchs der Geschäftsbereich Sensorik und Service, getragen vom "
                 "Ersatzteil- und Servicegeschäft in Nordamerika."),
            Heading("Bestellungs- und Auftragslage", 2),
            Text("Der Auftragseingang stieg um 8.8 % und lag leicht über dem Nettoerlös (Book-to-Bill 1.02). Per "
                 "31. Dezember 2025 betrug der Auftragsbestand CHF 48.3 Mio. (Vorjahr CHF 46.1 Mio.). Er deckt "
                 "im Durchschnitt knapp vier Monate Produktion; in der Medizintechnik reicht er bis in den "
                 "Sommer 2026."),
            Heading("Anzahl Vollzeitstellen", 2),
            Text(f"Im Jahresdurchschnitt beschäftigte die Gruppe rund 600 Vollzeitstellen; per Jahresende waren "
                 f"es {data.EMPLOYEES_FTE[0]} (Vorjahr {data.EMPLOYEES_FTE[1]}). Die Aufteilung nach Ländern "
                 "steht auf Seite 24."),
            Heading("Ausserordentliche Ereignisse", 2),
            Text("Im Geschäftsjahr 2025 sind keine ausserordentlichen Ereignisse eingetreten. Der Wechsel der "
                 "Strombeschaffung im Werk Süddeutschland auf einen langfristigen Liefervertrag mit Strom aus "
                 "erneuerbaren Quellen hat die Energiekosten stabilisiert."),
        ),
    )


def financial_position() -> Page:
    equity_ratio = (ratio(data.TOTAL_EQUITY[0], data.TOTAL_ASSETS[0]), ratio(data.TOTAL_EQUITY[1], data.TOTAL_ASSETS[1]))
    return Page(
        section="Lagebericht",
        blocks=(
            Heading("Ertrags-, Vermögens- und Finanzlage", 2),
            Grid(
                ("Quote in % des Nettoerlöses", "2025", "2024"),
                (
                    ("Materialaufwand", _expense_ratio("Materialaufwand", 0), _expense_ratio("Materialaufwand", 1)),
                    ("Personalaufwand", _expense_ratio("Personalaufwand", 0), _expense_ratio("Personalaufwand", 1)),
                    ("Übriger Betriebsaufwand", _expense_ratio("Übriger Betriebsaufwand", 0),
                     _expense_ratio("Übriger Betriebsaufwand", 1)),
                    ("EBIT-Marge", percent(ratio(data.EBIT[0], data.REVENUE[0])),
                     percent(ratio(data.EBIT[1], data.REVENUE[1]))),
                ),
                (110, 30, 30),
            ),
            Text("Die Materialquote sank dank tieferer Preise für Stahl und Aluminium und eines höheren Anteils "
                 "an Serviceleistungen. Die Personalquote ging trotz Lohnerhöhungen von durchschnittlich 1.8 % in "
                 "der Schweiz zurück, weil das Wachstum vor allem im Werk Tschechien stattfand."),
            Text(f"Die Eigenkapitalquote stieg auf {percent(equity_ratio[0])} (Vorjahr {percent(equity_ratio[1])}). "
                 "Die Finanzverbindlichkeiten wurden planmässig amortisiert. Die flüssigen Mittel übersteigen "
                 "die Finanzverbindlichkeiten; die Gruppe ist netto schuldenfrei. Bilanz, Erfolgsrechnung und "
                 "Geldflussrechnung stehen auf den Seiten 51 bis 53."),
        ),
    )


def treasury_organisation() -> Page:
    return Page(
        section="Lagebericht",
        blocks=(
            Heading("Finanzmanagement und Treasury-Organisation", 2),
            Text("Das Group Treasury mit 3 Vollzeitstellen ist am Hauptsitz in Beispielfeld angesiedelt und "
                 "rapportiert an den CFO. Es besteht aus der Leiterin Group Treasury, die vor ihrem Eintritt "
                 "zwölf Jahre im Devisen- und Geldhandel einer Bank tätig war, einem Treasury-Spezialisten für "
                 "Währungs- und Zinsrisiken und einer Cash-Managerin. Group Treasury führt den Cash-Pool der "
                 "Schweizer und deutschen Gesellschaften und schliesst alle Finanzgeschäfte der Gruppe ab."),
            Heading("Treasury-Policy und Treasury-Komitee", 3),
            Text("Grundsätze, Kompetenzen und Limiten regelt die vom Verwaltungsrat genehmigte Treasury-Policy, "
                 "zuletzt überarbeitet im Oktober 2025. Das Treasury-Komitee (CEO, CFO, Leiterin Group Treasury) "
                 "tagt monatlich, legt die Absicherungsquoten innerhalb der Policy fest und überwacht "
                 "Gegenparteien und Liquidität."),
            Heading("Währungsrisiken", 3),
            Text("Der grössere Teil des Nettoerlöses fällt in EUR und USD an, der grössere Teil der Kosten in CHF. "
                 "Group Treasury sichert die budgetierten Nettopositionen in EUR und USD rollierend über zwölf "
                 "Monate in vier Quartalstranchen ab, mit abnehmender Quote für spätere Tranchen. Eingesetzt "
                 "werden Devisentermingeschäfte und Zero-Cost-Collars; die offenen Kontrakte stehen in Anhang "
                 "Ziff. 9."),
            Heading("Liquidität und Finanzierung", 3),
            Text("Die Gruppe finanziert sich über Eigenkapital und einen variabel verzinsten Konsortialkredit "
                 "(Anhang Ziff. 14). Eine Betriebskreditlimite steht als Reserve zur Verfügung. Überschüssige "
                 "Liquidität legt Group Treasury in Festgeldern, Geldmarktfonds und Obligationen in CHF mit "
                 "Laufzeiten bis drei Jahre an."),
            Heading("Zinsrisiken", 3),
            Text("Der Konsortialkredit ist auf Basis SARON verzinst. Ein Zinsswap zur teilweisen Absicherung "
                 "wird 2026 geprüft."),
        ),
    )


def risk_assessment() -> Page:
    return Page(
        section="Lagebericht",
        blocks=(
            Heading("Durchführung einer Risikobeurteilung", 2),
            Text("Der Verwaltungsrat beurteilt die Risiken der Gruppe jährlich auf Antrag der Geschäftsleitung. "
                 "Grundlage ist ein Risikoinventar mit 24 Einzelrisiken, die nach Eintrittswahrscheinlichkeit und "
                 "Auswirkung auf den EBIT bewertet werden. Für jedes wesentliche Risiko sind eine verantwortliche "
                 "Person und Massnahmen festgelegt. Die letzte Beurteilung hat der Verwaltungsrat im November "
                 "2025 verabschiedet."),
            Heading("Wesentliche Risiken", 3),
            Bullets((
                "Konjunktur: Ein Einbruch der Investitionsgüternachfrage im Euroraum würde vor allem den "
                "Geschäftsbereich Antriebskomponenten treffen.",
                "Währungen: Ein stärkerer Franken gegenüber EUR und USD senkt Erlöse und Margen, weil die Kosten "
                "überwiegend in Franken anfallen.",
                "Lieferkette: Engpässe bei Lagern und Elektronik können Lieferungen verzögern.",
                "Informationssicherheit: Ein Cyberangriff könnte Produktion und Auftragsabwicklung unterbrechen.",
                "Fachkräfte: Der Mangel an Polymechanikerinnen und Polymechanikern begrenzt das Wachstum am "
                "Hauptsitz.",
            )),
            Text("Die Risikomatrix auf der folgenden Seite fasst Bewertung und Massnahmen zusammen."),
        ),
    )


def risk_matrix() -> Page:
    return Page(
        section="Lagebericht",
        blocks=(
            Heading("Risikomatrix", 2),
            Grid(
                ("Risiko", "Wahrscheinlichkeit", "Auswirkung", "Massnahmen"),
                (
                    ("Nachfrageeinbruch Euroraum", "mittel", "hoch",
                     "Diversifikation der Kundengruppen, flexible Arbeitszeitmodelle, variable Kostenstruktur"),
                    ("Wechselkurse EUR/CHF und USD/CHF", "hoch", "hoch",
                     "Rollierende Absicherung gemäss Treasury-Policy, natürliche Absicherung über Einkauf und "
                     "Produktion im Ausland"),
                    ("Lieferengpässe Lager und Elektronik", "mittel", "mittel",
                     "Zwei Lieferanten je kritischer Komponente, Sicherheitsbestände"),
                    ("Cyberangriff", "mittel", "hoch",
                     "Netzwerksegmentierung, Mehrfaktor-Authentifizierung, Notfallübungen"),
                    ("Fachkräftemangel", "hoch", "mittel", "Lehrlingsausbildung, Automation, Standort Tschechien"),
                    ("Zinsanstieg SARON", "tief", "tief", "Prüfung Zinsswap, tiefe Nettoverschuldung"),
                    ("Ausfall Gegenpartei Bank", "tief", "mittel",
                     "Mindestrating A-, Limiten je Bank gemäss Treasury-Policy"),
                    ("Produkthaftung Medizintechnik", "tief", "hoch", "ISO 13485, Rückverfolgbarkeit, Versicherung"),
                ),
                (44, 28, 24, 74),
                numeric_from=4,
            ),
        ),
    )


def research_and_outlook() -> Page:
    return Page(
        section="Lagebericht",
        blocks=(
            Heading("Forschung und Entwicklung", 2),
            Text("Der Aufwand für Forschung und Entwicklung betrug 4.1 % des Nettoerlöses. Schwerpunkte waren die "
                 "Linearachse LX-40, das Leichtbaugetriebe und die Zustandsüberwachung «Component Health» "
                 "(Seiten 19 und 20)."),
            Heading("Zukunftsaussichten", 2),
            Text("Für 2026 erwartet die Geschäftsleitung ein Wachstum des Nettoerlöses in Lokalwährungen von 3 bis "
                 "5 % und eine EBIT-Marge zwischen 8.5 und 9.5 %. Die Gruppe rechnet mit Umsatz und Einkauf in "
                 "EUR und USD in ähnlicher Grössenordnung wie 2025. Die Investitionen sollen bei rund 4 bis 5 % "
                 "des Nettoerlöses liegen, vor allem für die Endmontage in den USA und die Automation am "
                 "Hauptsitz."),
            Text("Risiken für den Ausblick sind eine längere Schwäche der Industrie im Euroraum, ein weiter "
                 "erstarkender Franken und Handelshemmnisse zwischen den USA und Europa."),
            Text(f"{data.PLACE}, {data.APPROVAL_DATE} · Der Verwaltungsrat", TextStyle.SMALL),
        ),
    )


def segment_principles() -> Page:
    return Page(
        section="Segmentbericht",
        blocks=(
            Heading("Segmentbericht 2025"),
            Heading("Grundsätze", 2),
            Text("Die Segmentberichterstattung folgt Swiss GAAP FER 31 und der internen Führungsstruktur. Die "
                 "Gruppe führt drei Segmente: Antriebskomponenten, Präzisionsteile sowie Sensorik und Service. "
                 "Konzernfunktionen (Finanzen, Group Treasury, Informatik, Personal) und Eliminationen sind "
                 "separat ausgewiesen."),
            Text("Nettoerlöse werden nach dem Sitz des Kunden den Regionen und nach der Fakturierungswährung den "
                 "Währungen zugeordnet. Leistungen zwischen den Segmenten werden zu Marktpreisen verrechnet und "
                 "sind eliminiert. Das Segmentergebnis entspricht dem Betriebsergebnis (EBIT) vor Umlage der "
                 "Konzernfunktionen."),
            Heading("Inhalt", 3),
            Grid(
                ("Thema", "Seite"),
                (
                    ("Segmente im Überblick", "39"),
                    ("Nettoerlöse nach Regionen", "40"),
                    ("Umsatz und Einkauf nach Währungen", "41"),
                    ("Investitionen und Mitarbeitende nach Segmenten", "42"),
                    ("Auftragseingang und Auftragsbestand", "43"),
                ),
                (140, 30),
            ),
        ),
    )


def segment_overview() -> Page:
    rows = tuple(
        (name, mio(data.SEGMENT_REVENUE[name][0]), mio(data.SEGMENT_REVENUE[name][1]),
         mio(data.SEGMENT_EBIT[name][0]), mio(data.SEGMENT_EBIT[name][1]),
         percent(ratio(data.SEGMENT_EBIT[name][0], data.SEGMENT_REVENUE[name][0])))
        for name in SEGMENTS
    )
    corporate = data.SEGMENT_EBIT["Konzernfunktionen und Eliminationen"]
    return Page(
        section="Segmentbericht",
        blocks=(
            Heading("Segmente im Überblick", 2),
            Grid(
                ("in Mio. CHF", "Nettoerlöse 2025", "Nettoerlöse 2024", "EBIT 2025", "EBIT 2024", "EBIT-Marge 2025"),
                (*rows, ("Konzernfunktionen und Eliminationen", "–", "–", mio(corporate[0]), mio(corporate[1]), "–")),
                (50, 24, 24, 24, 24, 24),
            ),
            Text("Die Summe der Segmente entspricht dem Nettoerlös und dem Betriebsergebnis der "
                 "Konzernerfolgsrechnung auf Seite 52."),
            Text(f"Antriebskomponenten wuchsen um {percent(growth(*data.SEGMENT_REVENUE['Antriebskomponenten']))}, "
                 f"Präzisionsteile um {percent(growth(*data.SEGMENT_REVENUE['Präzisionsteile']))} und Sensorik und "
                 f"Service um {percent(growth(*data.SEGMENT_REVENUE['Sensorik und Service']))}. Alle Segmente "
                 "verbesserten ihre Marge."),
        ),
    )


def regions() -> Page:
    return Page(
        section="Segmentbericht",
        blocks=(
            Heading("Nettoerlöse nach Regionen", 2),
            Grid(
                ("Region (Sitz des Kunden)", "Anteil 2025", "Anteil 2024"),
                (*((region, f"{a} %", f"{b} %") for region, (a, b) in data.REGION_SHARES.items()),
                 ("Total", "100.0 %", "100.0 %")),
                (110, 30, 30),
                total_last=True,
            ),
            Text("Nordamerika gewann Anteile dank Service und Medizintechnik. Der Rückgang in Deutschland "
                 "betrifft vor allem die Industrieautomation. Kunden in der Schweiz werden in CHF fakturiert, "
                 "ebenso ein Teil der Kunden in Asien und im übrigen Europa; die Fakturierung nach Währungen steht "
                 "auf der folgenden Seite."),
        ),
    )


def currencies() -> Page:
    chf_revenue = (data.chf_residual(data.FX_REVENUE, data.REVENUE, 0),
                   data.chf_residual(data.FX_REVENUE, data.REVENUE, 1))
    purchases_total = data.OPERATING_EXPENSES["Materialaufwand"]
    chf_purchases = (data.chf_residual(data.FX_PURCHASES, purchases_total, 0),
                     data.chf_residual(data.FX_PURCHASES, purchases_total, 1))

    def row(ccy: str) -> tuple[str, ...]:
        revenue, purchases = data.FX_REVENUE[ccy], data.FX_PURCHASES[ccy]
        return (f"{ccy} (Mio. {ccy})", mio(revenue[0]), mio(purchases[0]), signed_mio(revenue[0] - purchases[0]),
                mio(revenue[1]), mio(purchases[1]))

    def share_row(year: int) -> tuple[list[D], list[D]]:
        revenue = [*data.fx_in_chf(data.FX_REVENUE, year).values(), chf_revenue[year]]
        purchases = [*data.fx_in_chf(data.FX_PURCHASES, year).values(), chf_purchases[year]]
        return shares(revenue), shares(purchases)

    (rev_now, pur_now), (rev_prior, pur_prior) = share_row(0), share_row(1)
    labels = ("EUR", "USD", "CHF")
    return Page(
        section="Segmentbericht",
        blocks=(
            Heading("Umsatz und Einkauf nach Währungen", 2),
            Text("Nettoerlös und Einkauf (Material- und Wareneinkauf) der Gruppe nach Fakturierungswährung, in Mio. "
                 "Originalwährung. Die Beträge in EUR und USD sind die Grundlage für die Absicherung der "
                 "Fremdwährungsflüsse."),
            Grid(
                ("Währung", "Umsatz 2025", "Einkauf 2025", "Netto 2025", "Umsatz 2024", "Einkauf 2024"),
                (row("EUR"), row("USD"),
                 ("CHF (Mio. CHF)", mio(chf_revenue[0]), mio(chf_purchases[0]), "–", mio(chf_revenue[1]),
                  mio(chf_purchases[1]))),
                (45, 25, 25, 25, 25, 25),
            ),
            Heading("Anteile in %, umgerechnet zu Durchschnittskursen", 3),
            Grid(
                ("Währung", "Umsatz 2025", "Einkauf 2025", "Umsatz 2024", "Einkauf 2024"),
                tuple((label, percent(rev_now[i]), percent(pur_now[i]), percent(rev_prior[i]), percent(pur_prior[i]))
                      for i, label in enumerate(labels)),
                (70, 25, 25, 25, 25),
            ),
            Grid(
                ("Durchschnittskurse", "2025", "2024"),
                (("EUR/CHF", "0.9400", "0.9520"), ("USD/CHF", "0.8000", "0.8800")),
                (110, 30, 30),
            ),
            Text("Für 2026 rechnet die Gruppe mit Umsatz und Einkauf in EUR und USD in ähnlicher Grössenordnung. "
                 "Group Treasury sichert die budgetierten Nettopositionen gemäss Treasury-Policy rollierend über "
                 "zwölf Monate ab (Lagebericht, Seite 34; offene Kontrakte in Anhang Ziff. 9)."),
        ),
    )


def investments_and_employees() -> Page:
    return Page(
        section="Segmentbericht",
        blocks=(
            Heading("Investitionen und Mitarbeitende nach Segmenten", 2),
            Grid(
                ("Segment", "Investitionen 2025", "Investitionen 2024", "Vollzeitstellen 2025",
                 "Vollzeitstellen 2024"),
                (
                    ("Antriebskomponenten", "2.9", "3.3", "268", "262"),
                    ("Präzisionsteile", "2.1", "2.4", "214", "208"),
                    ("Sensorik und Service", "0.8", "0.9", "92", "88"),
                    ("Konzernfunktionen", "0.4", "0.4", "36", "34"),
                    ("Total", "6.2", "7.0", str(data.EMPLOYEES_FTE[0]), str(data.EMPLOYEES_FTE[1])),
                ),
                (54, 29, 29, 29, 29),
                total_last=True,
            ),
            Text("Investitionen in Sachanlagen und immaterielle Werte in Mio. CHF. Schwerpunkte 2025 waren die "
                 "zweite Montagelinie im Werk Tschechien, Schleifmaschinen am Hauptsitz und die "
                 "Servicewerkstatt in den USA."),
        ),
    )


def orders() -> Page:
    return Page(
        section="Segmentbericht",
        blocks=(
            Heading("Auftragseingang und Auftragsbestand", 2),
            Grid(
                ("in Mio. CHF", "Auftragseingang 2025", "Auftragsbestand 31.12.2025", "Auftragsbestand 31.12.2024"),
                (
                    ("Antriebskomponenten", "70.1", "22.6", "21.8"),
                    ("Präzisionsteile", "50.4", "18.9", "18.1"),
                    ("Sensorik und Service", "28.4", "6.8", "6.2"),
                    ("Total", "148.9", "48.3", "46.1"),
                ),
                (65, 35, 35, 35),
                total_last=True,
            ),
            Text("Der Auftragsbestand enthält verbindliche Bestellungen mit Liefertermin; Rahmenverträge ohne "
                 "Abruf sind nicht enthalten. Die Differenz zwischen Anfangsbestand, Auftragseingang und Umsatz "
                 "ergibt sich aus Währungseffekten und Stornierungen."),
        ),
    )


def pages() -> tuple[Page, ...]:
    return (
        business_development(), financial_position(), treasury_organisation(), risk_assessment(), risk_matrix(),
        research_and_outlook(), segment_principles(), segment_overview(), regions(), currencies(),
        investments_and_employees(), orders(),
    )
