# Use-Case: FIDLEG-konforme Absicherungsberatung für Firmenkunden

Stand: 18.11.2026

## Ausgangsidee

Firmenkunden füllen einen Fragebogen aus. Danach erhalten sie nach FIDLEG-Standard einen Anlagevorschlag, der auf ihrem Portfolio und ihren Assets basiert, zum Beispiel um ihre Risiken abzusichern.

## Zwei Schärfungen

**1. Firmenkunden statt institutionelle Kunden.**
Institutionelle Kunden nach FIDLEG sind Banken, Versicherungen, Zentralbanken und öffentliche Körperschaften mit professioneller Tresorerie. Für sie gelten die Verhaltensregeln grösstenteils nicht (Art. 20 FIDLEG); es gibt kaum Compliance-Arbeit zu automatisieren. Ein KMU unter den Schwellenwerten und ohne professionelle Tresorerie ist dagegen Privatkunde: volle Eignungsprüfung, Basisinformationsblatt (BIB) und Dokumentationspflicht. Dort ist der Aufwand gross und der Prozess heute manuell.

**2. Absicherung bezieht sich auf das Geschäft, nicht nur aufs Portfolio.**
Das Risiko eines Exporteurs steckt in Umsätzen in Fremdwährung, variabel verzinsten Krediten und Energiepreisen, nicht in der Wertschriftenliste. Input ist deshalb Jahresrechnung, Cashflows nach Währung und Finanzierungsstruktur. Daraus ergeben sich zwei Bausteine:

- **Absicherung:** Währungen, Zinsen, Rohstoffe
- **Anlage der überschüssigen Liquidität**

## Use-Case-Statement

> Ein Firmenkundenberater lädt die Jahresrechnung eines KMU hoch. Das Tool erkennt die Risiken, stellt nur noch die fehlenden Fragen und bestimmt das FIDLEG-Kundensegment. Dann erstellt es einen begründeten Absicherungs- und Anlagevorschlag mit Beratungsprotokoll, in einem Termin statt in mehreren.

## Ablauf

1. **Unterlagen:** Jahresrechnung (PDF) hochladen. Kennzahlen und Fremdwährungsflüsse werden mit Fundstelle ausgelesen und können korrigiert werden.
2. **Risiken:** Wirkung einer Kursbewegung von 10 % und eines Zinsanstiegs um 1 Prozentpunkt auf das EBIT.
3. **Fragebogen:** Nur was fehlt: Treasury-Organisation, Ziele, Policy, Mindestliquidität, Horizont, Risikobereitschaft, Kenntnisse und Erfahrungen. Vorbefüllte Antworten bestätigt der Berater.
4. **FIDLEG-Prüfung:** Kundensegment, Risikoprofil, Eignung pro Produkt.
5. **Vorschlag:** Gestaffelte Währungsabsicherung, Szenario-Rechnung, Aufteilung der Überschussliquidität, Begründung.
6. **Protokoll:** Checkliste vor Abschluss, Freigabe, Beratungsprotokoll (Art. 15 FIDLEG), Audit-Trail.

## FIDLEG-Kundensegmente bei Firmenkunden

| Segment | Beispiel | Konsequenz |
| --- | --- | --- |
| Privatkunde | KMU unter den Schwellenwerten, ohne professionelle Tresorerie | Volle Eignungsprüfung, BIB, Dokumentationspflicht |
| Professionell | Grosses Unternehmen (2 von 3: Bilanzsumme > CHF 20 Mio., Umsatz > CHF 40 Mio., Eigenkapital > CHF 2 Mio.) oder professionelle Tresorerie | Kenntnisse und Risikotragfähigkeit werden vermutet; Verzicht auf Informations- und Dokumentationspflichten möglich |
| Institutionell | Banken, Versicherungen | Verhaltensregeln entfallen weitgehend |

Dazu die Logik für Opting-in und Opting-out nach Art. 5 FIDLEG.

## Designprinzip

Das LLM liest die Dokumente aus, stellt Rückfragen und erklärt den Vorschlag. Ob ein Produkt geeignet ist, entscheidet ein nachvollziehbares Regelwerk mit Audit-Trail. Die Absicherung ist auf das tatsächliche Risiko begrenzt.

## Entscheidungen

| Frage | Entscheid |
| --- | --- |
| Zielsegment | KMU und Mid-Caps, nicht institutionelle Kunden |
| Nutzer | Berater-Copilot; ein Vorab-Fragebogen für den CFO ist optional (offen) |
| Fokus MVP | Währungsabsicherung und Liquiditätsanlage; Zinsen und Rohstoffe auf der Roadmap |
| Rolle des LLM | Extraktion, Rückfragen, Begründung; keine Eignungsentscheide |

## Offene Fragen

- Wer stellt die Challenge, wie lange dauert der Hackathon, wonach wird bewertet?
- Gilt Absicherungsberatung für KMU in der Praxis als portfoliobezogen (Eignung) oder transaktionsbezogen (Angemessenheit)?
- Artikelangaben und Vermutungsregeln mit Compliance gegenprüfen.
- Alternative Zielgruppe Pensionskassen (Anlagelimiten nach BVV 2) wäre ein eigener Case.

## Datenquellen

- Synthetische Jahresrechnungen für Demo-Personas.
- Devisenkurse und SARON: SNB-Datenportal (geplant, im Prototyp noch Demo-Werte).
