# Services

Zwei Backend-Services plus Frontend. Die Grenze zwischen deterministischem Regelwerk und KI ist die Service-Grenze. Details: [`../docs/architecture.md`](../docs/architecture.md), Use Cases: [`../docs/specs/use-cases.md`](../docs/specs/use-cases.md).

| Service | Pfad | Stack | Aufgabe | Vertrag |
| --- | --- | --- | --- | --- |
| treasury-service | [`treasury-service/`](treasury-service/) | Kotlin 2.3, Spring Boot 4.1, Java 21, Flyway, Schema `treasury` | Kunden, Unterlagen (PDF in Postgres), FIDLEG-Fragebogen, Regelwerk (Subprojekt `rules/`, reines Kotlin), Absicherungs- und Liquiditätsvorschlag, Checkliste, Freigabe, Protokoll, Audit-Trail | [`treasury-service.openapi.yaml`](../docs/api/treasury-service.openapi.yaml) |
| agent-service | [`agent-service/`](agent-service/) | Python 3.12, FastAPI, LangChain/LangGraph, Anthropic (`claude-opus-5-5`), Voyage AI (`voyage-4`), pgvector, Schema `rag` | Ingest (Text pro Seite, Chunks, Embeddings), Extraktion mit Fundstelle als Antwortvorschläge, Chat-Agent mit Tools und Fundstellen (SSE), Begründungsentwurf | [`agent-service.openapi.yaml`](../docs/api/agent-service.openapi.yaml) |
| Frontend | [`../frontend/`](../frontend/) | Angular 21, `@rch-adl/core` 21.1.0 | Start, FIDLEG-Fragebogen, Kunden-Dashboard, Beratung, Chat-Panel rechts | nutzt beide Verträge |

Aus den früheren Kandidaten:

| Kandidat | Wo jetzt |
| --- | --- |
| Extraktion | agent-service (`ingest`), Ergebnis nur als Vorschlag an den treasury-service |
| Regelwerk | treasury-service, Subprojekt `rules` |
| Marktdaten | treasury-service, Demo-Werte als Konfiguration; SNB-Datenportal auf der Roadmap |
| Vorschlag und Protokoll | treasury-service (Regeltexte, Protokoll, Audit-Trail); Begründungsentwurf per LLM im agent-service; PDF-Export Roadmap |

Lokal starten: [`../docs/architecture.md`](../docs/architecture.md), Abschnitt 15.
