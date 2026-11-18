# Treasury Kompass

Hackathon-Projekt: FIDLEG-konforme Anlegerprofilierung und Absicherungs- bzw. Anlagevorschläge für Firmenkunden (KMU, Mid-Caps) einer Schweizer Bank. Nutzer ist der Firmenkundenberater (Copilot, Human in the Loop).

## Kontext

- Fachtexte und UI auf Deutsch, Schweizer Schreibweise («ss» statt «ß»).
- Use-Case und Entscheidungen: `docs/use-case.md`. Regeln und Formeln: `docs/regelwerk.md`.
- `reference/prototype/treasury-kompass.html` ist die Referenz-Implementierung für UI-Flow und Regeln. Weicht neuer Code davon ab, gilt `docs/regelwerk.md`; Änderungen an den Regeln dort nachführen.
- Offene Punkte und nächste Schritte: `tasks/todo.md`.

## Getroffene Entscheidungen

- Zielgruppe sind Firmenkunden, nicht institutionelle Kunden: Für institutionelle Kunden entfallen die FIDLEG-Verhaltensregeln weitgehend (Art. 20).
- Berater-Copilot, kein Self-Service für den Kunden.
- MVP: Währungsabsicherung (EUR, USD gegen CHF) und Anlage der Überschussliquidität. Zinsen, Rohstoffe, Hedge Accounting: Roadmap.
- LLM nur für Extraktion, Rückfragen und Begründungstexte. Segment, Risikoprofil, Eignung und Quoten berechnet ein deterministisches Regelwerk, jede Entscheidung mit Rechtsgrundlage im Audit-Trail.
- Absicherungsquote nie über der Treasury-Policy und nie über 100 % der Nettoposition (sonst Spekulation).
- Freigabe nur, wenn vorbefüllte Antworten bestätigt und die Checkliste erledigt ist; jede spätere Änderung hebt sie auf.

## Daten

- Nur Beispieldaten erfundener Firmen. Keine echten Kunden- oder Finanzdaten ins Repo.
- Kurse und Zinssätze im Prototyp sind Demo-Annahmen (geplant: SNB-Datenportal).
