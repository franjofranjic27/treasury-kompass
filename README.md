# Treasury Kompass

Copilot für Firmenkundenberater: Aus der Jahresrechnung eines Firmenkunden entsteht ein FIDLEG-konformer Absicherungs- und Anlagevorschlag mit Beratungsprotokoll.

Hackathon-Projekt. Stand: klickbarer Prototyp mit Beispieldaten, noch keine Services.

## Use-Case in Kürze

- **Zielgruppe:** exportorientierte KMU und Mid-Caps in der Schweiz mit Fremdwährungsflüssen, variabel verzinsten Krediten und überschüssiger Liquidität.
- **Problem:** Die meisten KMU sind nach FIDLEG Privatkunden. Eine Absicherungsberatung braucht dann volle Eignungsprüfung, Basisinformationsblatt und Dokumentation. Das läuft heute grösstenteils von Hand.
- **Lösung:** Jahresrechnung hochladen, Risiken erkennen, nur fehlende Fragen stellen, Kundensegment und Eignung nach festen Regeln bestimmen, Vorschlag mit Begründung und Protokoll erzeugen. Der Berater bestätigt und gibt frei.
- **Prinzip:** Ein LLM liest aus und formuliert. Segment, Risikoprofil und Eignung entscheidet ein deterministisches Regelwerk mit Audit-Trail.

## Struktur

| Pfad | Inhalt |
| --- | --- |
| [`docs/use-case.md`](docs/use-case.md) | Geschärfter Use-Case, Zielgruppe, FIDLEG-Einordnung, Entscheidungen |
| [`docs/one-pager.md`](docs/one-pager.md) | One-Pager für den Pitch |
| [`docs/regelwerk.md`](docs/regelwerk.md) | Regeln und Formeln des Prototyps, Grundlage für die Services |
| [`reference/`](reference/) | Referenz-Artefakte, u.a. der klickbare HTML-Prototyp |
| [`services/`](services/) | Platz für die Services, noch leer |
| [`tasks/`](tasks/) | Aufgaben und Lessons Learned |

## Prototyp ansehen

```bash
open reference/prototype/treasury-kompass.html
```

Die Datei ist eigenständig (Vanilla JS, nur Google Fonts als externe Quelle). Alle Werte sind Beispieldaten erfundener Firmen, Kurse und Zinsen sind Demo-Annahmen.

## Hinweis

Prototyp für einen Hackathon, keine Anlageberatung. Die Rechtsgrundlagen nach FIDLEG sind vereinfacht dargestellt und noch nicht juristisch geprüft.
