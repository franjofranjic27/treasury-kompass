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
| [`docs/architecture.md`](docs/architecture.md) | Architektur, Konfiguration, Sicherheit, Versionen |
| [`reference/`](reference/) | Referenz-Artefakte, u.a. der klickbare HTML-Prototyp |
| [`services/`](services/) | `treasury-service` (Kotlin, Spring Boot) und `agent-service` (Python, FastAPI) |
| [`frontend/`](frontend/) | Angular-Frontend |
| [`infra/`](infra/) | Postgres-Init-Skript (Rollen, Schemas, pgvector) und Gateway-Konfiguration (nginx) |
| [`samples/`](samples/) | Synthetische Jahresrechnungen, Treasury-Policies und ein Prompt-Injection-Testdokument (PDF) mit Generator |
| [`docker-compose.yml`](docker-compose.yml), `.env.example` | Lokaler Stack und Vorlage für die Konfiguration |
| [`tasks/`](tasks/) | Aufgaben und Lessons Learned |

## Lokal starten

Voraussetzungen: Docker mit Compose v2, für die Services ohne Docker zusätzlich JDK 17+ und [uv](https://docs.astral.sh/uv/).

```bash
cp .env.example .env          # Passwörter und API-Schlüssel eintragen, .env wird nicht eingecheckt
docker compose up --build     # postgres, treasury-service, agent-service, gateway
open http://localhost:8088    # Gateway: Frontend, /api → treasury-service, /agent → agent-service
```

| Dienst | Adresse (nur 127.0.0.1) |
| --- | --- |
| Gateway | `http://localhost:8088` (`GATEWAY_PORT`) |
| treasury-service | `http://localhost:8080`, Swagger UI unter `/swagger-ui.html` |
| agent-service | `http://localhost:8000`, API-Doku unter `/docs` |
| PostgreSQL 17 mit pgvector | `localhost:5432` (`POSTGRES_PORT`), Datenbank `treasury_kompass` |

- Nur die Datenbank: `docker compose up -d postgres`, dann die Services lokal starten (`docs/architecture.md`, Abschnitt 15).
- Beim ersten Start legt `infra/postgres/init/01-roles-and-schemas.sh` die Rollen `treasury_app` und `agent_app` mit den Schemas `treasury` und `rag` an. Passwörter in `.env` ändern wirkt erst nach `docker compose down -v` (oder das Skript per `docker compose exec postgres /docker-entrypoint-initdb.d/01-roles-and-schemas.sh` erneut ausführen).
- `AGENT_DB_PASSWORD` steht in der Datenbank-URL des agent-service und darf nur URL-sichere Zeichen enthalten, z.B. `openssl rand -hex 24`. `INTERNAL_API_KEY` mit `openssl rand -hex 32` erzeugen.
- Zurücksetzen inklusive Demo-Daten und Audit-Trail: `docker compose down -v`.
- Das Gateway liefert `/internal/` und `/actuator/` nicht aus (404). Ist Port 5432 schon belegt, `POSTGRES_PORT` in `.env` ändern.
- Beispiel-PDFs für den Upload liegen in [`samples/`](samples/README.md); neu erzeugen mit `uv run samples/generate_samples.py`.

## Prototyp ansehen

```bash
open reference/prototype/treasury-kompass.html
```

Die Datei ist eigenständig (Vanilla JS, nur Google Fonts als externe Quelle). Alle Werte sind Beispieldaten erfundener Firmen, Kurse und Zinsen sind Demo-Annahmen.

## Hinweis

Prototyp für einen Hackathon, keine Anlageberatung. Die Rechtsgrundlagen nach FIDLEG sind vereinfacht dargestellt und noch nicht juristisch geprüft.
