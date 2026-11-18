"""Geschäftsbericht Beispiel Components AG: Nachhaltigkeitsbericht, Glossar, Adressen und Impressum
(S. 72–86)."""

from __future__ import annotations

from . import components_data as data
from .layout import Bullets, Grid, Heading, Page, Space, Text, TextStyle

WIDE = (110, 30, 30)


def scope_and_materiality() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Nachhaltigkeitsbericht 2025"),
            Heading("Geltungsbereich", 2),
            Text("Der Bericht umfasst alle vollkonsolidierten Gesellschaften (Anhang Ziff. 3) und das "
                 "Kalenderjahr 2025. Die Gruppe ist nicht verpflichtet, einen Bericht über nichtfinanzielle "
                 "Belange nach Art. 964a ff. OR zu erstellen. Sie berichtet freiwillig in Anlehnung an diese "
                 "Bestimmungen und an die GRI-Standards."),
            Heading("Wesentlichkeit", 2),
            Text("Die wesentlichen Themen haben wir 2024 mit Kunden, Mitarbeitenden, Lieferanten, Banken und dem "
                 "Verwaltungsrat erhoben und 2025 bestätigt. Bewertet wurden die Auswirkungen der Gruppe auf "
                 "Umwelt und Gesellschaft sowie die finanziellen Auswirkungen auf die Gruppe."),
            Grid(
                ("Wesentliches Thema", "Belang nach OR", "Seite"),
                (
                    ("Energie und Treibhausgasemissionen", "Umweltbelange, CO2-Ziele", "73"),
                    ("Klimabezogene Chancen und Risiken", "Umweltbelange", "75"),
                    ("Arbeitssicherheit, Aus- und Weiterbildung", "Arbeitnehmerbelange", "77"),
                    ("Sorgfaltspflichten in der Lieferkette", "Menschenrechte", "78"),
                    ("Geschäftsintegrität", "Bekämpfung der Korruption", "79"),
                    ("Informationssicherheit und Datenschutz", "Sozialbelange", "80"),
                ),
                (75, 70, 25),
            ),
        ),
    )


def environment_targets() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Umweltbelange: Ziele und Massnahmen", 2),
            Text("Die Gruppe will ihre Treibhausgasemissionen aus eigenen Quellen und eingekaufter Energie "
                 "(Scope 1 und 2) bis 2030 gegenüber 2020 halbieren und bis 2040 netto null erreichen. Für "
                 "Scope 3 erheben wir seit 2024 die wichtigsten Kategorien."),
            Bullets((
                "Photovoltaik auf den Dächern in Beispielfeld und Süddeutschland, zusammen 1.9 Megawatt Spitzenleistung",
                "Umstellung der Wärmeerzeugung am Hauptsitz von Erdgas auf Wärmepumpen bis 2027",
                "Abwärmenutzung aus Kompressoren und Härteöfen im Werk Tschechien",
                "Strombezug aus erneuerbaren Quellen an allen Standorten bis 2030",
                "Elektrifizierung der Firmenfahrzeuge, 62 % elektrisch per Ende 2025",
            )),
            Text("Die Investitionen in Energieeffizienz und erneuerbare Energie betrugen 2025 rund 8 % der "
                 "Gesamtinvestitionen."),
        ),
    )


def environment_figures() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Umweltbelange: Energie und Emissionen", 2),
            Grid(
                ("Kennzahl", "2025", "2024"),
                (
                    ("Energieverbrauch in MWh", "21'480", "22'310"),
                    ("davon Strom in MWh", "13'920", "14'150"),
                    ("Anteil Strom aus erneuerbaren Quellen", "78 %", "69 %"),
                    ("Scope 1 in Tonnen CO2-Äquivalent", "1'860", "1'990"),
                    ("Scope 2 (marktbasiert) in Tonnen CO2-Äquivalent", "1'710", "1'890"),
                    ("Scope 1 und 2 gegenüber Basisjahr 2020", "-31 %", "-25 %"),
                    ("Wasserverbrauch in m3", "38'400", "39'900"),
                    ("Abfälle in Tonnen, davon rezykliert", "1'240 / 91 %", "1'310 / 89 %"),
                ),
                WIDE,
            ),
            Text("Die Emissionen sanken, weil das Werk Tschechien seit April 2025 Strom aus erneuerbaren Quellen "
                 "bezieht und die Photovoltaikanlage in Süddeutschland in Betrieb ging. Methode: Greenhouse Gas "
                 "Protocol, Emissionsfaktoren der nationalen Umweltbehörden.", TextStyle.SMALL),
        ),
    )


def climate_risks() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Klimabezogene Chancen und Risiken", 2),
            Text("Die Analyse folgt der Struktur der Task Force on Climate-related Financial Disclosures. Der "
                 "Ausschuss für Prüfung und Risiko des Verwaltungsrates überwacht die klimabezogenen Risiken."),
            Grid(
                ("Thema", "Art", "Einschätzung"),
                (
                    ("CO2-Abgaben und Energiepreise", "Transitionsrisiko", "mittel; Massnahmen auf Seite 73"),
                    ("Nachfrage nach energieeffizienten Antrieben", "Chance", "hoch; Getriebe mit höherem Wirkungsgrad"),
                    ("Elektrifizierung von Bahn und Industrie", "Chance", "hoch; Sensorik und Service"),
                    ("Hitzeperioden und Starkniederschläge an Standorten", "Physisches Risiko", "tief bis mittel"),
                    ("Anforderungen von Kunden an CO2-Daten", "Transitionsrisiko", "mittel; digitaler Produktpass"),
                ),
                (65, 40, 65),
                numeric_from=3,
            ),
        ),
    )


def social_matters() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Sozialbelange und Gesellschaft", 2),
            Text("Die Gruppe ist an allen Standorten ein wichtiger Arbeitgeber und Ausbildungsbetrieb. Sie "
                 "unterstützt Berufswahlprojekte von Schulen, bietet Schnupperlehren an und arbeitet mit "
                 "regionalen Fachhochschulen zusammen."),
            Text("Produktsicherheit hat in der Medizintechnik und der Bahntechnik besondere Bedeutung. Alle "
                 "sicherheitsrelevanten Teile sind bis zur Materialcharge rückverfolgbar. 2025 gab es keine "
                 "Produktrückrufe."),
            Text("Spenden und Sponsoring beschränken sich auf Bildung, Sport für Jugendliche und gemeinnützige "
                 "Organisationen in den Standortgemeinden. Politische Parteien unterstützt die Gruppe nicht."),
        ),
    )


def employee_figures() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Arbeitnehmerbelange: Kennzahlen", 2),
            Grid(
                ("Kennzahl", "2025", "2024"),
                (
                    ("Vollzeitstellen per 31.12.", str(data.EMPLOYEES_FTE[0]), str(data.EMPLOYEES_FTE[1])),
                    ("Lernende und Studierende im dualen Studium", "38", "36"),
                    ("Frauenanteil gesamt / Kader", "24 % / 18 %", "23 % / 16 %"),
                    ("Weiterbildungstage pro Vollzeitstelle", "3.4", "3.1"),
                    ("Berufsunfälle pro 1 Mio. Arbeitsstunden", "6.1", "7.4"),
                    ("Freiwillige Fluktuation", "7.8 %", "8.9 %"),
                    ("Teilnahme an der Mitarbeitendenbefragung", "81 %", "–"),
                    ("Engagement-Index (0 bis 100)", "74", "–"),
                ),
                WIDE,
            ),
            Text("Die Lohngleichheitsanalyse nach Art. 13a ff. des Gleichstellungsgesetzes für die Schweizer "
                 "Gesellschaften ergab keine unerklärte Lohndifferenz über der Toleranzschwelle."),
        ),
    )


def human_rights() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Menschenrechte und Sorgfaltspflichten", 2),
            Text("Die Gruppe verpflichtet ihre Lieferanten mit einem Verhaltenskodex zur Einhaltung der "
                 "Menschenrechte, fairer Arbeitsbedingungen und von Umweltstandards. Der Kodex ist Bestandteil "
                 "aller Rahmenverträge."),
            Text("Zur Sorgfaltspflicht bei Mineralien und Metallen aus Konfliktgebieten (Art. 964j ff. OR) haben "
                 "wir die Mengenschwellen geprüft. Die Einfuhren von Zinn, Tantal, Wolfram und Gold liegen unter "
                 "den Schwellenwerten der Verordnung; eine Berichtspflicht besteht nicht. Hinweise auf "
                 "Kinderarbeit in der Lieferkette liegen nicht vor."),
            Text("Meldungen zu möglichen Verstössen nimmt eine externe, anonyme Meldestelle entgegen. 2025 gingen "
                 "zwei Meldungen ein; beide wurden abgeklärt und betrafen keine Menschenrechtsfragen."),
        ),
    )


def anti_corruption() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Bekämpfung der Korruption", 2),
            Text("Der Verhaltenskodex der Gruppe verbietet Bestechung, Vorteilsgewährung und unzulässige "
                 "Geschenke. Vertriebsmitarbeitende und Einkäufer absolvieren alle zwei Jahre eine Schulung; 2025 "
                 "lag die Teilnahmequote bei 96 %."),
            Text("Vertriebspartner ausserhalb der OECD werden vor Vertragsabschluss geprüft. 2025 wurden keine "
                 "Korruptionsfälle festgestellt."),
            Text("Sanktionen und Exportkontrollen prüft der Bereich Recht und Compliance für jede Bestellung aus "
                 "Ländern mit erhöhtem Risiko. Lieferungen an sanktionierte Personen oder Länder finden nicht "
                 "statt."),
        ),
    )


def information_security() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Informationssicherheit und Datenschutz", 2),
            Text("Die Informatik der Gruppe ist zentral organisiert. Produktionsnetze sind von den Büronetzen "
                 "getrennt, der Zugriff von aussen erfolgt nur mit Mehrfaktor-Authentifizierung. 2025 haben wir "
                 "zwei Notfallübungen durchgeführt und alle Mitarbeitenden mit Computerarbeitsplatz zu Phishing "
                 "geschult."),
            Text("Personendaten bearbeitet die Gruppe nach dem Schweizer Datenschutzgesetz und der "
                 "Datenschutz-Grundverordnung der EU. Es gab 2025 keine meldepflichtigen Datenschutzverletzungen."),
        ),
    )


def supplier_assessment() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Lieferantenbewertung", 2),
            Text("Lieferanten mit einem Jahresvolumen über CHF 100'000 bewerten wir jährlich nach Qualität, "
                 "Liefertreue, Preis und Nachhaltigkeit. Lieferanten mit erhöhtem Risiko besuchen wir vor Ort."),
            Grid(
                ("Kennzahl", "2025", "2024"),
                (
                    ("Aktive Lieferanten", "604", "617"),
                    ("Anteil Einkaufsvolumen bei bewerteten Lieferanten", "72 %", "64 %"),
                    ("Audits vor Ort", "18", "14"),
                    ("Lieferanten mit Massnahmenplan", "9", "11"),
                    ("Beendete Lieferbeziehungen wegen Verstössen", "1", "0"),
                ),
                WIDE,
            ),
        ),
    )


def sustainability_figures() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("Kennzahlenübersicht Nachhaltigkeit", 2),
            Grid(
                ("Bereich", "Kennzahl", "2025", "2024"),
                (
                    ("Umwelt", "Scope 1 und 2 in Tonnen CO2-Äquivalent", "3'570", "3'880"),
                    ("Umwelt", "Anteil Strom aus erneuerbaren Quellen", "78 %", "69 %"),
                    ("Umwelt", "Recyclingquote Abfälle", "91 %", "89 %"),
                    ("Mitarbeitende", "Berufsunfälle pro 1 Mio. Arbeitsstunden", "6.1", "7.4"),
                    ("Mitarbeitende", "Weiterbildungstage pro Vollzeitstelle", "3.4", "3.1"),
                    ("Lieferkette", "Einkaufsvolumen bei bewerteten Lieferanten", "72 %", "64 %"),
                    ("Integrität", "Teilnahme Schulung Verhaltenskodex", "96 %", "93 %"),
                    ("Produkte", "Revidierte Getriebe", "1'450", "1'280"),
                ),
                (35, 85, 25, 25),
                numeric_from=2,
            ),
        ),
    )


def gri_index() -> Page:
    return Page(
        section="Nachhaltigkeitsbericht",
        blocks=(
            Heading("GRI-Inhaltsindex (Auszug)", 2),
            Grid(
                ("GRI-Angabe", "Inhalt", "Seite"),
                (
                    ("2-1", "Organisationsprofil", "7"),
                    ("2-9", "Führungsstruktur", "29 bis 31"),
                    ("2-22", "Erklärung zur Strategie für nachhaltige Entwicklung", "4, 27"),
                    ("3-1, 3-2", "Wesentliche Themen", "72"),
                    ("302-1", "Energieverbrauch", "74"),
                    ("305-1, 305-2", "Treibhausgasemissionen Scope 1 und 2", "74"),
                    ("403-9", "Arbeitsbedingte Verletzungen", "26, 77"),
                    ("404-1", "Aus- und Weiterbildung", "25, 77"),
                    ("205-2", "Schulung zur Korruptionsbekämpfung", "79"),
                    ("308-1, 414-1", "Bewertung von Lieferanten", "81"),
                ),
                (35, 105, 30),
            ),
        ),
    )


def glossary() -> Page:
    return Page(
        section="Glossar",
        blocks=(
            Heading("Glossar"),
            Grid(
                ("Begriff", "Erklärung"),
                (
                    ("Book-to-Bill", "Verhältnis von Auftragseingang zu Nettoerlös einer Periode"),
                    ("EBIT", "Betriebsergebnis vor Finanzergebnis und Steuern"),
                    ("EBIT-Marge", "EBIT in % des Nettoerlöses"),
                    ("Eigenkapitalquote", "Eigenkapital in % der Bilanzsumme"),
                    ("Free Cashflow", "Geldfluss aus Geschäftstätigkeit abzüglich Geldfluss aus Investitionstätigkeit"),
                    ("Lokalwährungen", "Veränderung zu konstanten Wechselkursen des Vorjahres"),
                    ("SARON", "Swiss Average Rate Overnight, Referenzzinssatz für Franken"),
                    ("Devisentermingeschäft", "Vereinbarung, einen Währungsbetrag zu einem festen Kurs an einem "
                                              "späteren Datum zu tauschen"),
                    ("Zero-Cost-Collar", "Kombination aus gekaufter und verkaufter Devisenoption, die den Kurs "
                                         "innerhalb einer Bandbreite absichert, ohne Prämie"),
                    ("Treasury-Policy", "Vom Verwaltungsrat genehmigte Regeln für Liquidität, Finanzierung und "
                                        "Absicherung von Finanzrisiken"),
                    ("Scope 1, 2, 3", "Treibhausgasemissionen aus eigenen Quellen, eingekaufter Energie und der "
                                      "übrigen Wertschöpfungskette"),
                    ("Swiss GAAP FER", "Fachempfehlungen zur Rechnungslegung für Schweizer Unternehmen"),
                ),
                (45, 125),
                numeric_from=2,
            ),
        ),
    )


def addresses() -> Page:
    return Page(
        section="Adressen",
        blocks=(
            Heading("Standorte und Adressen"),
            Grid(
                ("Gesellschaft", "Adresse"),
                (
                    (data.COMPANY, data.ADDRESS),
                    ("Beispiel Sensorik AG", "Sensorweg 2, 9997 Beispieltal"),
                    ("Beispiel Components GmbH", "Musterstrasse 40, Süddeutschland (fiktive Adresse)"),
                    ("Beispiel Components s.r.o.", "Industriestrasse 7, Tschechien (fiktive Adresse)"),
                    ("Beispiel Components Inc.", "100 Example Drive, USA (fiktive Adresse)"),
                ),
                (60, 110),
                numeric_from=2,
            ),
            Heading("Finanzkalender", 2),
            Grid(
                ("Termin", "Datum"),
                (("Generalversammlung 2026", "8. Mai 2026"), ("Auszahlung der Dividende", "14. Mai 2026"),
                 ("Halbjahresbrief 2026", "August 2026"), ("Geschäftsbericht 2026", "März 2027")),
                (110, 60),
            ),
            Heading("Kontakt", 2),
            Text("Daniel Exempel, Chief Financial Officer · Claudia Fiktiv, Leiterin Group Treasury"),
        ),
    )


def imprint() -> Page:
    return Page(
        section="Impressum",
        blocks=(
            Heading("Impressum"),
            Text(f"Herausgeberin: {data.COMPANY}, {data.ADDRESS}"),
            Text("Konzept und Text: Finanzen und Kommunikation der Beispiel Components AG"),
            Text("Der Geschäftsbericht erscheint in deutscher Sprache. Massgebend ist die gedruckte Fassung."),
            Space(8),
            Heading("Hinweis", 2),
            Text("Dieses Dokument ist ein fiktives Beispiel für den Hackathon «Treasury Kompass». Firma, Personen, "
                 "Adressen und Zahlen sind frei erfunden. Ähnlichkeiten mit realen Unternehmen oder Personen sind "
                 "zufällig. Das Dokument enthält keine echten Kunden- oder Finanzdaten."),
            Heading("Zukunftsgerichtete Aussagen", 3),
            Text("Der Bericht enthält Aussagen über künftige Entwicklungen. Sie beruhen auf Annahmen der "
                 "Geschäftsleitung und können von der tatsächlichen Entwicklung abweichen.", TextStyle.SMALL),
        ),
    )


def pages() -> tuple[Page, ...]:
    return (
        scope_and_materiality(), environment_targets(), environment_figures(), climate_risks(), social_matters(),
        employee_figures(), human_rights(), anti_corruption(), information_security(), supplier_assessment(),
        sustainability_figures(), gri_index(), glossary(), addresses(), imprint(),
    )
