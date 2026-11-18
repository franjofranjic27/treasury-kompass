"""Geschäftsbericht Beispiel Components AG, Unternehmensteil (S. 1–31).

Absolute Kennzahlen (Umsatz, Bilanz, EBIT …) stehen bewusst nur in der Konzernrechnung und im
Anhang; hier stehen Veränderungen und Quoten, damit die Fundstellen eindeutig bleiben.
"""

from __future__ import annotations

from decimal import Decimal as D

from . import components_data as data
from .layout import Bullets, Grid, Heading, KeyFigures, Page, Space, Text, TextStyle
from .numbers import growth, percent, ratio

COMPANY = data.COMPANY


def _revenue_growth_in_local_currencies() -> D:
    revenue_at_prior_rates = (
        sum(data.FX_REVENUE[c][0] * data.AVERAGE_RATES[c][1] for c in data.FX_REVENUE)
        + data.REVENUE[0] - sum(data.fx_in_chf(data.FX_REVENUE, 0).values())
    )
    return growth(revenue_at_prior_rates, data.REVENUE[1])


def _foreign_currency_share(year: int) -> D:
    return ratio(sum(data.fx_in_chf(data.FX_REVENUE, year).values()), data.REVENUE[year])


def _segment_growth(name: str) -> str:
    return percent(growth(*data.SEGMENT_REVENUE[name]))


def _segment_margin(name: str) -> str:
    return percent(ratio(data.SEGMENT_EBIT[name][0], data.SEGMENT_REVENUE[name][0]))


def cover() -> Page:
    return Page(
        section="",
        cover=True,
        blocks=(
            Space(55),
            Text(COMPANY, TextStyle.SUBTITLE),
            Text("Geschäftsbericht 2025", TextStyle.TITLE),
            Text("Unternehmensbericht, Lagebericht, Segmentbericht, Konzernrechnung nach Swiss GAAP FER und "
                 "Nachhaltigkeitsbericht", TextStyle.LEAD),
            Space(8),
            Text(data.ADDRESS),
            Space(65),
            Text("Fiktives Beispiel – Hackathon", TextStyle.SUBTITLE),
            Text("Firma, Personen, Adressen und Zahlen sind frei erfunden und dienen nur als Testdaten für den "
                 "Hackathon «Treasury Kompass». Ähnlichkeiten mit realen Unternehmen oder Personen sind zufällig.",
                 TextStyle.SMALL),
        ),
    )


def contents() -> Page:
    return Page(
        section="Inhalt",
        blocks=(
            Heading("Inhalt"),
            Grid(
                ("Teil", "Seite"),
                (
                    ("Kennzahlen 2025", "3"),
                    ("Brief an die Aktionärinnen und Aktionäre", "4"),
                    ("Das Jahr 2025 im Überblick", "6"),
                    ("Unternehmensporträt und Strategie «Fokus 2030»", "7"),
                    ("Märkte und Kunden", "11"),
                    ("Geschäftsbereiche", "13"),
                    ("Innovation, Produktion und Lieferkette", "19"),
                    ("Mitarbeitende", "24"),
                    ("Nachhaltigkeit im Überblick", "27"),
                    ("Corporate Governance", "29"),
                    ("Lagebericht", "32"),
                    ("Segmentbericht", "38"),
                    ("Finanzbericht", "44"),
                    ("Konzernrechnung 2025", "50"),
                    ("Anhang zur Konzernrechnung", "55"),
                    ("Bericht der Revisionsstelle", "70"),
                    ("Nachhaltigkeitsbericht", "72"),
                    ("Glossar, Adressen und Impressum", "84"),
                ),
                (140, 30),
            ),
            Text("Die statutarische Jahresrechnung der Beispiel Components AG (Einzelabschluss nach OR) wird "
                 "separat publiziert und ist auf Anfrage erhältlich.", TextStyle.SMALL),
        ),
    )


def key_figures() -> Page:
    growth_local = _revenue_growth_in_local_currencies()
    equity_ratio = (ratio(data.TOTAL_EQUITY[0], data.TOTAL_ASSETS[0]), ratio(data.TOTAL_EQUITY[1], data.TOTAL_ASSETS[1]))
    ebit_margin = (ratio(data.EBIT[0], data.REVENUE[0]), ratio(data.EBIT[1], data.REVENUE[1]))
    net_margin = (ratio(data.NET_INCOME[0], data.REVENUE[0]), ratio(data.NET_INCOME[1], data.REVENUE[1]))
    cash_margin = (ratio(data.OPERATING_CASH_FLOW[0], data.REVENUE[0]),
                   ratio(data.OPERATING_CASH_FLOW[1], data.REVENUE[1]))
    capex_ratio = (ratio(-data.INVESTING_CASH_FLOW[0], data.REVENUE[0]),
                   ratio(-data.INVESTING_CASH_FLOW[1], data.REVENUE[1]))
    return Page(
        section="Kennzahlen",
        blocks=(
            Heading("Kennzahlen 2025"),
            Text("Die Gruppe ist 2025 in allen drei Geschäftsbereichen gewachsen und hat die Profitabilität "
                 "verbessert. Absolute Werte enthält die Konzernrechnung ab Seite 51.", TextStyle.LEAD),
            KeyFigures((
                (f"+{percent(growth(*data.REVENUE))}", "Veränderung Nettoerlös", "Vorjahr +3.8 %"),
                (f"+{percent(growth_local)}", "Veränderung Nettoerlös in Lokalwährungen", "Vorjahr +5.1 %"),
                (percent(ebit_margin[0]), "EBIT-Marge", f"Vorjahr {percent(ebit_margin[1])}"),
                (percent(net_margin[0]), "Konzerngewinn in % des Nettoerlöses", f"Vorjahr {percent(net_margin[1])}"),
                (percent(equity_ratio[0]), "Eigenkapitalquote", f"Vorjahr {percent(equity_ratio[1])}"),
                (percent(cash_margin[0]), "Geldfluss aus Geschäftstätigkeit in % des Nettoerlöses",
                 f"Vorjahr {percent(cash_margin[1])}"),
                (percent(capex_ratio[0]), "Investitionen in % des Nettoerlöses", f"Vorjahr {percent(capex_ratio[1])}"),
                (percent(_foreign_currency_share(0)), "Anteil Nettoerlös in Fremdwährung",
                 f"Vorjahr {percent(_foreign_currency_share(1))}"),
                (str(data.EMPLOYEES_FTE[0]), "Mitarbeitende (Vollzeitstellen)", f"Vorjahr {data.EMPLOYEES_FTE[1]}"),
                ("1.02", "Book-to-Bill (Auftragseingang zu Nettoerlös)", "Vorjahr 0.98"),
                ("4.1 %", "Forschung und Entwicklung in % des Nettoerlöses", "Vorjahr 3.9 %"),
                ("-8 %", "Treibhausgasemissionen Scope 1 und 2", "gegenüber Vorjahr"),
            )),
        ),
    )


def letter_part_one() -> Page:
    return Page(
        section="Brief an die Aktionäre",
        blocks=(
            Heading("Brief an die Aktionärinnen und Aktionäre"),
            Text("Sehr geehrte Aktionärinnen und Aktionäre", TextStyle.LEAD),
            Text(f"2025 war für die {COMPANY} ein gutes Jahr in einem schwierigen Umfeld. Die Industrie in "
                 f"Europa investierte zurückhaltend, der Franken blieb stark und die Lieferketten waren zeitweise "
                 f"angespannt. Trotzdem steigerten wir den Nettoerlös um {percent(growth(*data.REVENUE))}, in "
                 f"Lokalwährungen um {percent(_revenue_growth_in_local_currencies())}. Alle drei Geschäftsbereiche "
                 f"trugen zum Wachstum bei, am stärksten Sensorik und Service."),
            Text(f"Die EBIT-Marge stieg auf {percent(ratio(data.EBIT[0], data.REVENUE[0]))}. Dazu beigetragen haben "
                 "die bessere Auslastung im Werk Tschechien, Preisanpassungen im Ersatzteilgeschäft und das "
                 "Effizienzprogramm in der Getriebefertigung. Die Absicherung der Fremdwährungsflüsse hat die "
                 "Margen gegen den starken Franken geschützt, ohne die Chancen ganz aus der Hand zu geben."),
            Text(f"Die Bilanz ist solide: Die Eigenkapitalquote erreichte "
                 f"{percent(ratio(data.TOTAL_EQUITY[0], data.TOTAL_ASSETS[0]))}, und die flüssigen Mittel "
                 "übersteigen erstmals seit 2019 die Finanzverbindlichkeiten. Wir haben den Konsortialkredit "
                 "planmässig amortisiert und gleichzeitig in Kapazität und Digitalisierung investiert."),
            Heading("Strategie «Fokus 2030» auf Kurs", 2),
            Text("Vor zwei Jahren haben wir uns vorgenommen, vom Teilelieferanten zum Systempartner für Bewegungs- "
                 "und Positionierlösungen zu werden. 2025 haben wir dafür wichtige Schritte gemacht: Die "
                 "Linearachse LX-40 mit integriertem Wegmesssystem ist im Markt, das Servicegeschäft in "
                 "Nordamerika wächst zweistellig, und die Zustandsüberwachung «Component Health» läuft bei den "
                 "ersten Kunden im Pilotbetrieb."),
        ),
    )


def letter_part_two() -> Page:
    return Page(
        section="Brief an die Aktionäre",
        blocks=(
            Heading("Dank an unsere Mitarbeitenden", 2),
            Text(f"Ende 2025 arbeiteten {data.EMPLOYEES_FTE[0]} Vollzeitstellen für unsere Gruppe, an fünf "
                 "Standorten in vier Ländern. Ihr Einsatz, ihre Erfahrung und ihre Ideen machen den Unterschied. "
                 "Wir danken allen Mitarbeitenden herzlich, ebenso unseren Kunden und Lieferanten für die "
                 "langjährige Partnerschaft."),
            Heading("Dividende", 2),
            Text("Der Verwaltungsrat beantragt der Generalversammlung eine Dividende von CHF 7.00 je Namenaktie "
                 "(Vorjahr CHF 6.00). Damit beteiligen wir Sie am guten Ergebnis und behalten genügend Mittel für "
                 "Investitionen und die weitere Entschuldung."),
            Heading("Ausblick", 2),
            Text("Für 2026 erwarten wir ein Wachstum in Lokalwährungen von 3 bis 5 % und eine EBIT-Marge zwischen "
                 "8.5 und 9.5 %. Die Nachfrage in der Medizintechnik und der Bahntechnik bleibt robust, in der "
                 "Industrieautomation rechnen wir mit einer Erholung ab dem zweiten Halbjahr. Die "
                 "Währungsrisiken sichern wir weiterhin gemäss unserer Treasury-Policy ab."),
            Text("Wir danken Ihnen für Ihr Vertrauen."),
            Space(4),
            Grid(
                ("Für den Verwaltungsrat", "Für die Geschäftsleitung"),
                (("Dr. Anna Beispiel, Präsidentin des Verwaltungsrates", "Peter Muster, Chief Executive Officer"),),
                (85, 85),
                numeric_from=2,
            ),
            Text(f"{data.PLACE}, im März 2026", TextStyle.SMALL),
        ),
    )


def year_overview() -> Page:
    return Page(
        section="Das Jahr 2025",
        blocks=(
            Heading("Das Jahr 2025 im Überblick"),
            Grid(
                ("Quartal", "Ereignis"),
                (
                    ("1. Quartal", "Markteinführung der Linearachse LX-40 an der Fachmesse für Automation in "
                                   "Stuttgart; Start des Effizienzprogramms «Getriebe 25» am Hauptsitz"),
                    ("1. Quartal", "Rahmenvertrag über fünf Jahre mit einem europäischen Hersteller von "
                                   "Medizinrobotern für Präzisionsteile"),
                    ("2. Quartal", "Inbetriebnahme der zweiten Montagelinie im Werk Tschechien; Photovoltaikanlage "
                                   "auf dem Dach des Werks Süddeutschland"),
                    ("2. Quartal", "Generalversammlung genehmigt eine Dividende von CHF 6.00 je Aktie"),
                    ("3. Quartal", "Pilotbetrieb der Zustandsüberwachung «Component Health» bei drei Kunden aus "
                                   "der Bahntechnik"),
                    ("3. Quartal", "Erweiterung der Servicewerkstatt in den USA um eine Prüfstrecke für Getriebe"),
                    ("4. Quartal", "Überarbeitung der Treasury-Policy durch den Verwaltungsrat; Einführung "
                                   "rollierender Absicherung über vier Quartalstranchen"),
                    ("4. Quartal", "Zertifizierung des Werks Tschechien nach ISO 14001; 38 Lernende in Ausbildung"),
                ),
                (30, 140),
                numeric_from=2,
            ),
        ),
    )


def portrait() -> Page:
    return Page(
        section="Unternehmen",
        blocks=(
            Heading("Unternehmensporträt"),
            Text(f"Die {COMPANY} entwickelt und fertigt Komponenten, die Maschinen präzise bewegen und "
                 "positionieren: Getriebe und Linearachsen, gedrehte und gefräste Präzisionsteile sowie Sensoren "
                 "für Weg, Winkel und Drehmoment. Kunden sind Hersteller von Industrieautomation, "
                 "Medizintechnik, Bahn- und Energietechnik.", TextStyle.LEAD),
            Heading("Geschichte", 2),
            Text("Das Unternehmen wurde 1958 als mechanische Werkstätte in Beispielfeld gegründet. In den 1980er "
                 "Jahren kamen Getriebe für Werkzeugmaschinen dazu, 2004 das Werk in Süddeutschland, 2012 das "
                 "Montagewerk in Tschechien und 2016 die Vertriebs- und Servicegesellschaft in den USA. 2019 "
                 "übernahm die Gruppe einen Hersteller von Drehmomentsensoren, die heutige Beispiel Sensorik AG."),
            Heading("Eigentümer", 2),
            Text("Die Gesellschaft ist nicht börsenkotiert. Die Mehrheit der Aktien hält die Gründerfamilie über "
                 "die Beispiel Holding AG. Verwaltungsrat, Geschäftsleitung und Mitarbeitende sind über ein "
                 "Beteiligungsprogramm am Kapital beteiligt (Corporate Governance, Seite 29)."),
            Heading("Gruppe auf einen Blick", 2),
            Bullets((
                "Drei Geschäftsbereiche: Antriebskomponenten, Präzisionsteile, Sensorik und Service",
                "Fünf Standorte in der Schweiz, in Deutschland, Tschechien und den USA",
                f"{data.EMPLOYEES_FTE[0]} Vollzeitstellen, davon 38 Lernende in der Schweiz und in Deutschland",
                f"Rund {ratio(sum(data.fx_in_chf(data.FX_REVENUE, 0).values()), data.REVENUE[0], 0)} % des "
                "Nettoerlöses in Fremdwährungen, vor allem EUR und USD",
            )),
        ),
    )


def strategy_starting_point() -> Page:
    return Page(
        section="Strategie",
        blocks=(
            Heading("Strategie «Fokus 2030»"),
            Heading("Ausgangslage", 2),
            Text("Unsere Kunden kaufen zunehmend ganze Baugruppen statt einzelner Teile. Sie erwarten kürzere "
                 "Lieferzeiten, dokumentierte Qualität bis zum Rohmaterial und Daten über den Zustand der "
                 "Komponenten im Betrieb. Gleichzeitig steigen die Anforderungen an Energieeffizienz und "
                 "CO2-Bilanz entlang der Lieferkette."),
            Text("Als mittelgrosser Hersteller mit Kosten überwiegend in Franken und Erlösen überwiegend in "
                 "Euro und US-Dollar müssen wir produktiver sein als die Konkurrenz im Euroraum. Unsere Stärken "
                 "sind die Präzision der Fertigung, die Nähe zu den Entwicklungsabteilungen der Kunden und die "
                 "Kombination von Mechanik und Sensorik unter einem Dach."),
            Heading("Was wir 2023 entschieden haben", 2),
            Bullets((
                "Wir konzentrieren uns auf Anwendungen, in denen Präzision und Zuverlässigkeit über den Preis "
                "entscheiden.",
                "Wir bauen das Service- und Ersatzteilgeschäft aus, weil es stabile Erträge bringt.",
                "Wir wachsen in Nordamerika organisch und mit Partnern vor Ort.",
                "Wir halten eine starke Bilanz, damit wir auch in schwächeren Jahren investieren können.",
            )),
        ),
    )


def strategy_directions() -> Page:
    return Page(
        section="Strategie",
        blocks=(
            Heading("Stossrichtungen", 2),
            Heading("1. Systempartner für Bewegung und Positionierung", 3),
            Text("Wir kombinieren Getriebe, Linearachsen und Sensoren zu einbaufertigen Modulen. Entwicklung "
                 "und Vertrieb arbeiten dafür in gemeinsamen Kundenteams."),
            Heading("2. Wachstum in Nordamerika", 3),
            Text("Die Vertriebs- und Servicegesellschaft in den USA erhält eine eigene Endmontage. Ziel ist ein "
                 "Anteil Nordamerikas am Nettoerlös von einem Viertel bis 2030."),
            Heading("3. Service und Zustandsüberwachung", 3),
            Text("Mit «Component Health» messen Sensoren Temperatur, Vibration und Drehmoment im Betrieb. Kunden "
                 "planen so Wartungen und vermeiden Stillstände; wir gewinnen Daten für die Weiterentwicklung."),
            Heading("4. Effiziente und klimafreundliche Produktion", 3),
            Text("Wir automatisieren die Fertigung weiter, senken den Energieverbrauch und beziehen bis 2030 an "
                 "allen Standorten Strom aus erneuerbaren Quellen."),
            Heading("5. Starke Bilanz", 3),
            Text("Die Eigenkapitalquote soll über 40 % bleiben. Währungs- und Zinsrisiken sichern wir nach einer "
                 "vom Verwaltungsrat genehmigten Treasury-Policy ab; überschüssige Liquidität legen wir "
                 "konservativ an."),
        ),
    )


def strategy_targets() -> Page:
    return Page(
        section="Strategie",
        blocks=(
            Heading("Ziele 2030 und Stand der Umsetzung", 2),
            Grid(
                ("Ziel bis 2030", "Stand 2025", "Beurteilung"),
                (
                    ("Organisches Wachstum durchschnittlich 5 % pro Jahr",
                     f"{percent(_revenue_growth_in_local_currencies())} in Lokalwährungen", "auf Kurs"),
                    ("EBIT-Marge mindestens 10 %", percent(ratio(data.EBIT[0], data.REVENUE[0])),
                     "Verbesserung, noch unter Ziel"),
                    ("Anteil Service und Ersatzteile 25 % des Nettoerlöses", "19 %", "auf Kurs"),
                    ("Anteil Nordamerika 25 % des Nettoerlöses", "19.1 %", "leicht hinter Plan"),
                    ("Eigenkapitalquote über 40 %", percent(ratio(data.TOTAL_EQUITY[0], data.TOTAL_ASSETS[0])),
                     "erreicht"),
                    ("Treibhausgasemissionen Scope 1 und 2 minus 50 % gegenüber 2020", "minus 31 %", "auf Kurs"),
                    ("Strom aus erneuerbaren Quellen an allen Standorten", "78 %", "auf Kurs"),
                ),
                (78, 50, 42),
                numeric_from=3,
            ),
            Text("Die Ziele wurden 2023 festgelegt und 2025 vom Verwaltungsrat bestätigt. Die Zwischenziele "
                 "fliessen in die variable Vergütung der Geschäftsleitung ein."),
        ),
    )


def market_environment() -> Page:
    return Page(
        section="Märkte und Kunden",
        blocks=(
            Heading("Märkte und Kunden"),
            Heading("Branchenumfeld 2025", 2),
            Text("Die Investitionsgüterindustrie im Euroraum blieb 2025 schwach. Der Auftragseingang der "
                 "deutschen Maschinenbauer lag real unter dem Vorjahr, die Automobilindustrie verschob Projekte. "
                 "In Nordamerika profitierten Automation und Medizintechnik von Investitionen in lokale "
                 "Produktion. Die Bahntechnik wuchs dank öffentlicher Infrastrukturprogramme in Europa."),
            Text("Der Franken war gegenüber dem Euro und dem US-Dollar im Jahresdurchschnitt stärker als im "
                 "Vorjahr. Das drückte den in Franken ausgewiesenen Nettoerlös um rund 2.6 Prozentpunkte. Die "
                 "Preise für Stahl und Aluminium gingen leicht zurück, Elektronikkomponenten waren wieder gut "
                 "verfügbar."),
            Heading("Wettbewerb", 2),
            Text("Unsere Wettbewerber sind spezialisierte Hersteller in Deutschland, Italien und Japan sowie die "
                 "Eigenfertigung grosser Kunden. Wir gewinnen Aufträge über Präzision, Lieferzuverlässigkeit und "
                 "technische Beratung, selten über den Preis."),
        ),
    )


def customer_groups() -> Page:
    return Page(
        section="Märkte und Kunden",
        blocks=(
            Heading("Kundengruppen", 2),
            Grid(
                ("Kundengruppe", "Entwicklung", "Anteil 2025", "Anteil 2024"),
                (
                    ("Industrieautomation", "Projektverschiebungen in Europa", "38 %", "40 %"),
                    ("Medizintechnik", "neuer Rahmenvertrag Medizinroboter", "21 %", "19 %"),
                    ("Mobilität und Bahntechnik", "Infrastrukturprogramme", "17 %", "16 %"),
                    ("Energie- und Umwelttechnik", "stabil", "14 %", "14 %"),
                    ("Übrige", "–", "10 %", "11 %"),
                ),
                (55, 65, 25, 25),
                numeric_from=2,
            ),
            Heading("Kundenstruktur", 2),
            Text("Die zehn grössten Kunden stehen für 34 % des Nettoerlöses (Vorjahr 36 %), kein Kunde für mehr "
                 "als 7 %. Mit den meisten Grosskunden bestehen mehrjährige Rahmenverträge mit Preisgleitklauseln "
                 "für Rohmaterial. Die Verträge sind in der Regel in der Währung des Kunden abgeschlossen, im "
                 "Euroraum in EUR, in Nordamerika in USD."),
            Text("Die Verteilung des Nettoerlöses nach Regionen und Währungen zeigt der Segmentbericht auf den "
                 "Seiten 40 und 41."),
        ),
    )


def _segment_pages(name: str, intro: str, products: tuple[str, ...], highlights: str, outlook: str,
                   customers: str) -> tuple[Page, Page]:
    first = Page(
        section="Geschäftsbereiche",
        blocks=(
            Heading(f"Geschäftsbereich {name}"),
            Text(intro, TextStyle.LEAD),
            Grid(
                ("Kennzahl", "2025", "Vorjahr"),
                (
                    ("Veränderung Nettoerlös", f"+{_segment_growth(name)}", "–"),
                    ("Segment-EBIT-Marge", _segment_margin(name),
                     percent(ratio(data.SEGMENT_EBIT[name][1], data.SEGMENT_REVENUE[name][1]))),
                    ("Anteil am Nettoerlös der Gruppe", percent(ratio(data.SEGMENT_REVENUE[name][0], data.REVENUE[0])),
                     percent(ratio(data.SEGMENT_REVENUE[name][1], data.REVENUE[1]))),
                ),
                (90, 40, 40),
            ),
            Heading("Geschäftsverlauf", 2),
            Text(highlights),
        ),
    )
    second = Page(
        section="Geschäftsbereiche",
        blocks=(
            Heading(f"{name}: Produkte und Kunden", 2),
            Bullets(products),
            Heading("Kunden", 3),
            Text(customers),
            Heading("Ausblick", 3),
            Text(outlook),
        ),
    )
    return first, second


def business_units() -> tuple[Page, ...]:
    drive = _segment_pages(
        "Antriebskomponenten",
        "Getriebe, Linearachsen und Antriebsmodule für Maschinen, die schnell und genau positionieren müssen.",
        (
            "Planeten- und Zykloidgetriebe mit geringem Verdrehspiel für Roboter und Werkzeugmaschinen",
            "Linearachsen mit Kugelgewindetrieb, neu LX-40 mit integriertem Wegmesssystem",
            "Kundenspezifische Antriebsmodule aus Getriebe, Motor und Sensorik",
        ),
        "Der Bereich wuchs trotz schwacher Nachfrage aus der europäischen Automation. Die Linearachse LX-40 "
        "gewann im ersten Jahr 40 Kunden. Das Effizienzprogramm «Getriebe 25» senkte die Durchlaufzeit in der "
        "Getriebefertigung um ein Fünftel und verbesserte die Marge.",
        "Für 2026 rechnen wir mit einem leichten Wachstum. Mehr Module für Nordamerika werden ab Mitte Jahr in "
        "der neuen Endmontage in den USA gefertigt.",
        "Hersteller von Industrierobotern, Werkzeugmaschinen und Verpackungsanlagen in Deutschland, Italien, "
        "der Schweiz und den USA. Die fünf grössten Kunden des Bereichs stehen für rund 40 % seines "
        "Nettoerlöses.",
    )
    precision = _segment_pages(
        "Präzisionsteile",
        "Gedrehte, gefräste und geschliffene Teile mit engen Toleranzen, vor allem für Medizintechnik und "
        "Automation.",
        (
            "Präzisionsteile aus Edelstahl, Titan und Aluminium in kleinen und mittleren Serien",
            "Baugruppen inklusive Reinigung, Prüfung und Rückverfolgbarkeit für die Medizintechnik",
            "Prototypen und Nullserien innerhalb von zehn Arbeitstagen",
        ),
        "Wachstumstreiber war die Medizintechnik: Ein europäischer Hersteller von Medizinrobotern hat uns als "
        "Hauptlieferanten für Gelenkbaugruppen gewählt. Die Auslastung im Werk Tschechien verbesserte sich "
        "deutlich, die Ausschussquote sank auf 0.8 %.",
        "Der Auftragsbestand deckt die Kapazität bis in den Sommer 2026. Wir investieren in zwei weitere "
        "Dreh-Fräszentren und in eine automatisierte Messzelle.",
        "Hersteller von Medizintechnik, Automation und Messtechnik, überwiegend im Euroraum. Für die "
        "Medizintechnik ist die Fertigung nach ISO 13485 zertifiziert.",
    )
    sensing = _segment_pages(
        "Sensorik und Service",
        "Sensoren für Weg, Winkel und Drehmoment sowie Service, Ersatzteile und Zustandsüberwachung.",
        (
            "Drehmomentsensoren für Prüfstände und kollaborative Roboter, neu TS-Mini",
            "Wegmesssysteme für Linearachsen und Hydraulikzylinder",
            "Reparatur, Revision und Ersatzteile für Getriebe aller Generationen",
            "«Component Health»: Zustandsüberwachung mit Sensorik und Datenauswertung",
        ),
        "Der Bereich wuchs am stärksten. Das Ersatzteilgeschäft profitierte von der grossen installierten "
        "Basis, die Servicewerkstatt in den USA arbeitete voll ausgelastet. Drei Kunden aus der Bahntechnik "
        "nutzen «Component Health» im Pilotbetrieb.",
        "2026 bringen wir «Component Health» in den Regelbetrieb und bauen den Service in Nordamerika weiter "
        "aus. Wir erwarten erneut überdurchschnittliches Wachstum.",
        "Betreiber und Hersteller in Bahntechnik, Energie und Industrie. Rund die Hälfte des Servicegeschäfts "
        "entfällt auf Kunden in Nordamerika.",
    )
    return (*drive, *precision, *sensing)


def innovation_organisation() -> Page:
    return Page(
        section="Innovation",
        blocks=(
            Heading("Innovation"),
            Heading("Organisation und Schwerpunkte", 2),
            Text("In Forschung und Entwicklung arbeiten 46 Personen an den Standorten Beispielfeld und "
                 "Süddeutschland. Der Aufwand für Forschung und Entwicklung betrug 4.1 % des Nettoerlöses "
                 "(Vorjahr 3.9 %). 2025 haben wir zwölf Patente angemeldet."),
            Bullets((
                "Mechatronik: Integration von Sensorik und Elektronik in Getriebe und Achsen",
                "Werkstoffe: Leichtbau mit Recyclingaluminium, verschleissarme Beschichtungen",
                "Software und Daten: Auswertung von Zustandsdaten, digitale Produktpässe",
                "Fertigungstechnologie: Hartfeinbearbeitung, automatisierte Messtechnik",
            )),
            Heading("Zusammenarbeit", 2),
            Text("Wir arbeiten mit zwei Fachhochschulen und einem Forschungsinstitut für Fertigungstechnik "
                 "zusammen. Zwei Projekte werden von der Schweizerischen Agentur für Innovationsförderung "
                 "mitfinanziert."),
        ),
    )


def innovation_projects() -> Page:
    return Page(
        section="Innovation",
        blocks=(
            Heading("Projekte 2025", 2),
            Grid(
                ("Projekt", "Ziel", "Stand"),
                (
                    ("Linearachse LX-40", "Achse mit integriertem Wegmesssystem, weniger Bauraum", "im Markt"),
                    ("Leichtbaugetriebe", "Gehäuse aus Recyclingaluminium, 30 % leichter", "Prototypen beim Kunden"),
                    ("TS-Mini", "Drehmomentsensor für kollaborative Roboter", "Serienstart Q2 2026"),
                    ("Component Health", "Zustandsüberwachung mit Datenplattform", "Pilotbetrieb"),
                    ("Digitaler Produktpass", "Material- und CO2-Daten je Baugruppe", "Konzept"),
                ),
                (45, 85, 40),
                numeric_from=3,
            ),
            Text("Neue Produkte, die in den letzten drei Jahren eingeführt wurden, trugen 2025 rund 18 % zum "
                 "Nettoerlös bei (Vorjahr 15 %)."),
        ),
    )


def production_sites() -> Page:
    return Page(
        section="Produktion",
        blocks=(
            Heading("Produktion und Standorte"),
            Grid(
                ("Standort", "Land", "Tätigkeit", "Vollzeitstellen"),
                (
                    ("Beispielfeld (Hauptsitz)", "Schweiz", "Getriebe, Präzisionsteile, Entwicklung, Konzernfunktionen",
                     "298"),
                    ("Beispieltal", "Schweiz", "Beispiel Sensorik AG: Sensoren, Elektronik", "54"),
                    ("Werk Süddeutschland", "Deutschland", "Linearachsen, Vertrieb Euroraum", "118"),
                    ("Montagewerk", "Tschechien", "Präzisionsteile, Baugruppenmontage", "104"),
                    ("Vertrieb und Service", "USA", "Vertrieb, Servicewerkstatt, Endmontage ab 2026", "36"),
                ),
                (45, 27, 73, 25),
                numeric_from=3,
            ),
            Text("Die Gruppe fertigt in der Schweiz vor allem anspruchsvolle Teile mit hoher Wertschöpfung, in "
                 "Tschechien arbeitsintensive Serien und Baugruppen. Der Anteil der Kosten in Franken ist "
                 "deutlich höher als der Anteil des Nettoerlöses in Franken; das daraus entstehende "
                 "Währungsrisiko steuert Group Treasury (Lagebericht, Seite 34)."),
        ),
    )


def quality() -> Page:
    return Page(
        section="Produktion",
        blocks=(
            Heading("Qualität und Operational Excellence", 2),
            Text("Alle Werke sind nach ISO 9001 zertifiziert, die Medizintechnikfertigung zusätzlich nach "
                 "ISO 13485 und das Werk Süddeutschland nach IATF 16949. Die Reklamationsquote sank auf "
                 "180 ppm (Vorjahr 240 ppm)."),
            Text("Mit dem Programm «Getriebe 25» haben wir die Getriebefertigung am Hauptsitz neu organisiert: "
                 "Fertigungsinseln statt Werkstattprinzip, automatisierte Beladung der Schleifmaschinen und "
                 "Messung direkt in der Zelle. Die Durchlaufzeit sank um 21 %, die Termintreue stieg auf 94 %."),
            Grid(
                ("Kennzahl", "2025", "2024"),
                (
                    ("Termintreue (Lieferung zum bestätigten Termin)", "94 %", "89 %"),
                    ("Reklamationsquote", "180 ppm", "240 ppm"),
                    ("Ausschussquote Präzisionsteile", "0.8 %", "1.1 %"),
                    ("Gesamtanlageneffektivität Schlüsselmaschinen", "71 %", "66 %"),
                ),
                (110, 30, 30),
            ),
        ),
    )


def procurement() -> Page:
    return Page(
        section="Lieferkette",
        blocks=(
            Heading("Einkauf und Lieferkette"),
            Text("Die Gruppe bezieht Rohmaterial (Stahl, Aluminium, Titan), Normteile, Lager, Motoren und "
                 "Elektronik von rund 600 aktiven Lieferanten. Rund 55 % des Material- und Wareneinkaufs werden "
                 "in EUR bezahlt, rund 17 % in USD, der Rest in CHF (Segmentbericht, Seite 41)."),
            Text("Für Stahl, Aluminium und Lager bestehen Rahmenverträge mit jeweils zwei Lieferanten. "
                 "Kritische Elektronikkomponenten halten wir für sechs Monate an Lager. Die Lieferanten werden "
                 "jährlich nach Qualität, Liefertreue, Preis und Nachhaltigkeit bewertet (Nachhaltigkeitsbericht, "
                 "Seite 81)."),
            Heading("Natürliche Absicherung", 2),
            Text("Einkäufe in EUR und USD verringern das Währungsrisiko aus den Verkäufen in diesen Währungen. "
                 "Wo es wirtschaftlich sinnvoll ist, verlagern wir Einkäufe in den Euroraum und in die USA. Die "
                 "verbleibende Nettoposition sichert Group Treasury ab."),
        ),
    )


def employees() -> Page:
    rows = tuple((country, str(fte[0]), str(fte[1])) for country, fte in data.EMPLOYEES_BY_COUNTRY.items())
    return Page(
        section="Mitarbeitende",
        blocks=(
            Heading("Mitarbeitende"),
            Text(f"Ende 2025 zählte die Gruppe {data.EMPLOYEES_FTE[0]} Vollzeitstellen (Vorjahr "
                 f"{data.EMPLOYEES_FTE[1]}). Neue Stellen entstanden vor allem im Werk Tschechien und im Service "
                 "in den USA.", TextStyle.LEAD),
            Grid(("Vollzeitstellen per 31.12.", "2025", "2024"),
                 (*rows, ("Total", str(data.EMPLOYEES_FTE[0]), str(data.EMPLOYEES_FTE[1]))),
                 (110, 30, 30), total_last=True),
            Grid(
                ("Kennzahl", "2025", "2024"),
                (
                    ("Frauenanteil gesamt", "24 %", "23 %"),
                    ("Frauenanteil Kader", "18 %", "16 %"),
                    ("Freiwillige Fluktuation", "7.8 %", "8.9 %"),
                    ("Durchschnittliche Betriebszugehörigkeit in Jahren", "11.2", "11.5"),
                ),
                (110, 30, 30),
            ),
        ),
    )


def training() -> Page:
    return Page(
        section="Mitarbeitende",
        blocks=(
            Heading("Aus- und Weiterbildung", 2),
            Text("Wir bilden 38 Lernende aus, in den Berufen Polymechanik, Konstruktion, Automatik, Logistik und "
                 "Kaufmann/Kauffrau sowie in Deutschland im dualen Studium. 2025 haben alle 11 Lernenden ihre "
                 "Abschlussprüfung bestanden, neun arbeiten weiter bei uns."),
            Text("Für Weiterbildung investierten wir durchschnittlich 3.4 Tage pro Vollzeitstelle. Schwerpunkte "
                 "waren Programmierung von Werkzeugmaschinen, Qualitätsmethoden und Führung. Mit dem Programm "
                 "«Meister von morgen» bereiten wir zwölf Fachkräfte auf Führungsaufgaben in der Produktion vor."),
            Heading("Vergütung und Beteiligung", 2),
            Text("Alle Mitarbeitenden in der Schweiz und in Deutschland erhalten eine Erfolgsbeteiligung, die an "
                 "EBIT-Marge und Kundenzufriedenheit gekoppelt ist. Seit 2021 können Mitarbeitende Aktien zu "
                 "Vorzugsbedingungen erwerben; 2025 nahmen 31 % der Berechtigten teil."),
        ),
    )


def safety() -> Page:
    return Page(
        section="Mitarbeitende",
        blocks=(
            Heading("Arbeitssicherheit und Gesundheit", 2),
            Text("Sicherheit hat in der Produktion Vorrang vor Termin und Kosten. Jeder Standort hat eine "
                 "Sicherheitsfachkraft, Unfälle und Beinaheunfälle werden innerhalb von 24 Stunden analysiert."),
            Grid(
                ("Kennzahl", "2025", "2024"),
                (
                    ("Berufsunfälle mit Ausfallzeit pro 1 Mio. Arbeitsstunden", "6.1", "7.4"),
                    ("Ausfalltage wegen Berufsunfällen", "212", "268"),
                    ("Absenzenquote (Krankheit und Unfall)", "3.4 %", "3.6 %"),
                    ("Gemeldete Beinaheunfälle", "146", "121"),
                ),
                (110, 30, 30),
            ),
            Text("Die höhere Zahl gemeldeter Beinaheunfälle werten wir positiv: Sie zeigt, dass die Teams "
                 "Gefahren früher erkennen und melden."),
        ),
    )


def sustainability_overview() -> Page:
    return Page(
        section="Nachhaltigkeit",
        blocks=(
            Heading("Nachhaltigkeit im Überblick"),
            Text("Nachhaltigkeit ist Teil unserer Strategie «Fokus 2030». Wir konzentrieren uns auf die Themen, "
                 "die für unsere Geschäftstätigkeit und unsere Anspruchsgruppen wesentlich sind: Energie und "
                 "Emissionen, Produktsicherheit und Qualität, Arbeitssicherheit, Ausbildung, Sorgfaltspflichten "
                 "in der Lieferkette und Geschäftsintegrität."),
            Grid(
                ("Ziel", "Stand 2025"),
                (
                    ("Scope 1 und 2 bis 2030 minus 50 % gegenüber 2020", "minus 31 %"),
                    ("100 % Strom aus erneuerbaren Quellen bis 2030", "78 %"),
                    ("Berufsunfälle pro 1 Mio. Arbeitsstunden unter 5", "6.1"),
                    ("80 % des Einkaufsvolumens bei bewerteten Lieferanten", "72 %"),
                ),
                (120, 50),
            ),
            Text("Ausführliche Angaben enthält der Nachhaltigkeitsbericht ab Seite 72."),
        ),
    )


def sustainability_products() -> Page:
    return Page(
        section="Nachhaltigkeit",
        blocks=(
            Heading("Produkte und Kreislaufwirtschaft", 2),
            Text("Die grösste Hebelwirkung haben unsere Produkte im Einsatz: Getriebe mit höherem Wirkungsgrad "
                 "senken den Energieverbrauch der Maschinen unserer Kunden über viele Jahre. Die neue "
                 "Getriebegeneration erreicht einen um zwei Prozentpunkte höheren Wirkungsgrad."),
            Text("Revisionen statt Neukauf verlängern die Lebensdauer von Getrieben um durchschnittlich acht "
                 "Jahre. 2025 haben wir 1'450 Getriebe revidiert (Vorjahr 1'280). Späne und Reststücke aus "
                 "Aluminium und Stahl gehen vollständig ins Recycling; das Leichtbaugetriebe nutzt "
                 "Recyclingaluminium für das Gehäuse."),
        ),
    )


def governance_principles() -> Page:
    return Page(
        section="Corporate Governance",
        blocks=(
            Heading("Corporate Governance"),
            Heading("Grundsätze", 2),
            Text("Die Beispiel Components AG ist nicht börsenkotiert. Sie orientiert sich am Swiss Code of Best "
                 "Practice for Corporate Governance, soweit er für ein Familienunternehmen sinnvoll ist."),
            Heading("Aktionariat und Kapitalstruktur", 2),
            Grid(
                ("Aktionärsgruppe", "Anteil am Kapital"),
                (
                    ("Gründerfamilie über die Beispiel Holding AG", "68 %"),
                    ("Mitglieder des Verwaltungsrates und der Geschäftsleitung", "7 %"),
                    ("Mitarbeitende (Beteiligungsprogramm)", "4 %"),
                    ("Übrige Aktionärinnen und Aktionäre", "21 %"),
                ),
                (120, 50),
            ),
            Text("Das Aktienkapital beträgt CHF 5.0 Mio., eingeteilt in 500'000 Namenaktien zu CHF 10. Die "
                 "Übertragung von Aktien bedarf der Zustimmung des Verwaltungsrates; ein Vorkaufsrecht steht der "
                 "Beispiel Holding AG zu. Es bestehen weder genehmigtes noch bedingtes Kapital."),
        ),
    )


def board() -> Page:
    return Page(
        section="Corporate Governance",
        blocks=(
            Heading("Verwaltungsrat", 2),
            Grid(
                ("Name", "Funktion", "Im Amt seit", "Ausschüsse"),
                (
                    ("Dr. Anna Beispiel", "Präsidentin", "2014", "Nomination und Vergütung"),
                    ("Thomas Exempel", "Vizepräsident", "2017", "Prüfung und Risiko (Vorsitz)"),
                    ("Dr. Sandra Modell", "Mitglied", "2019", "Strategie und Technologie"),
                    ("Urs Fiktiv", "Mitglied", "2021", "Prüfung und Risiko"),
                    ("Nina Vorlage", "Mitglied", "2024", "Nomination und Vergütung"),
                ),
                (45, 35, 25, 65),
                numeric_from=4,
            ),
            Text("Der Verwaltungsrat traf sich 2025 zu sieben Sitzungen und einer zweitägigen Strategieklausur. "
                 "Der Ausschuss für Prüfung und Risiko behandelt Rechnungslegung, internes Kontrollsystem, "
                 "Risikomanagement und die Treasury-Policy. Er hat die überarbeitete Treasury-Policy im Oktober "
                 "2025 dem Gesamtverwaltungsrat zur Genehmigung empfohlen."),
            Text("Vier der fünf Mitglieder sind unabhängig. Dr. Anna Beispiel vertritt die Gründerfamilie."),
        ),
    )


def management() -> Page:
    return Page(
        section="Corporate Governance",
        blocks=(
            Heading("Geschäftsleitung", 2),
            Grid(
                ("Name", "Funktion", "Im Amt seit"),
                (
                    ("Peter Muster", "Chief Executive Officer", "2018"),
                    ("Daniel Exempel", "Chief Financial Officer", "2020"),
                    ("Marco Fiktiv", "Leiter Antriebskomponenten", "2016"),
                    ("Eva Modell", "Leiterin Präzisionsteile", "2022"),
                    ("Jonas Vorlage", "Leiter Sensorik und Service", "2019"),
                    ("Sara Platzhalter", "Leiterin Operations", "2023"),
                ),
                (55, 85, 30),
                numeric_from=2,
            ),
            Heading("Revisionsstelle", 2),
            Text("Revisionsstelle ist die Fiktiva Wirtschaftsprüfung AG. Der leitende Revisor, Simon Platzhalter, "
                 "ist seit dem Geschäftsjahr 2022 für das Mandat verantwortlich. Das Honorar ist in Anhang "
                 "Ziff. 18 offengelegt."),
            Heading("Informationspolitik", 2),
            Text("Die Gesellschaft informiert ihre Aktionärinnen und Aktionäre mit dem Geschäftsbericht und einem "
                 "Halbjahresbrief. Banken und Kreditgeber erhalten zusätzlich eine Quartalsberichterstattung "
                 "gemäss Kreditvertrag."),
        ),
    )


def pages() -> tuple[Page, ...]:
    return (
        cover(), contents(), key_figures(), letter_part_one(), letter_part_two(), year_overview(), portrait(),
        strategy_starting_point(), strategy_directions(), strategy_targets(), market_environment(),
        customer_groups(), *business_units(), innovation_organisation(), innovation_projects(),
        production_sites(), quality(), procurement(), employees(), training(), safety(),
        sustainability_overview(), sustainability_products(), governance_principles(), board(), management(),
    )
