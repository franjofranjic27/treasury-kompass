# Todo

## Vor dem Pitch

- [ ] Challenge-Geber, Bewertungskriterien und Pitch-Dauer klären
- [ ] FIDLEG-Artikelangaben und die Vermutung bei professionellen Kunden mit Compliance gegenprüfen
- [ ] Klären, ob Absicherungsberatung für KMU als portfoliobezogen (Eignung) oder transaktionsbezogen (Angemessenheit) gilt
- [ ] Schwellen und Gewichte des Regelwerks mit Fachleuten aus der Bank kalibrieren
- [ ] Entscheiden: nur Berater-Copilot oder zusätzlich ein Vorab-Fragebogen für den CFO
- [ ] Datenschutz: echte Jahresrechnungen nur in geschützter Umgebung, LLM-Hosting in der Schweiz oder EU

## Entwicklung

- [x] Stack und Schnitt der Services festlegen (siehe `services/README.md`)
- [ ] Regelwerk als eigenes Modul aus dem Prototyp lösen, mit Tests für die zwei Demo-Kunden (`docs/regelwerk.md`, Abschnitt 9)
- [ ] LLM-Extraktion für 1 bis 2 synthetische Jahresrechnungen (PDF)
- [ ] Live-Kurse und SARON vom SNB-Datenportal
- [ ] PDF-Export des Beratungsprotokolls

## Umsetzung (Plan vom 2026-11-18)

Stack-Entscheide:

- Frontend: Angular 21 mit `@rch-adl/core` (DBL-Design-Library), Chat-Panel rechts auf allen Screens
- `treasury-service`: Spring Boot 4.1, Kotlin. Kunden, Dokumente (PDF in Postgres), FIDLEG-Fragebogen, Regelwerk, Vorschläge, Freigabe, Audit-Trail
- `agent-service`: Python FastAPI, LangChain mit Anthropic, RAG auf pgvector, Embeddings mit Voyage AI
- Postgres mit pgvector, Docker Compose für den lokalen Stack

Ablauf mit Agents:

- [ ] Design-Canvas (Artifact): Start (Kunden, Upload), FIDLEG-Profilierung, Kunden-Dashboard, Beratung, Chat-Panel, Architektur. Review durch Franjo
- [x] Architektur und Specs: `docs/architecture.md`, `docs/specs/use-cases.md`, OpenAPI-Verträge unter `docs/api/`
- [ ] `treasury-service` implementieren (implementer)
- [ ] `agent-service` implementieren (implementer)
- [ ] Tests (test-engineer), Review (code-reviewer), Security-Review (security-advisor: Upload, Secrets, Prompt Injection aus PDFs)
- [ ] Angular-Frontend nach Design-Review
- [ ] Offene Punkte aus `docs/architecture.md`, Abschnitt 18, mit Fachbereich und Compliance klären

### Backlog treasury-service

In Bauordnung. Akzeptanzkriterien in `docs/specs/use-cases.md`, Vertrag in `docs/api/treasury-service.openapi.yaml`.

- [ ] Gradle-Setup: Root-App von start.spring.io (Boot 4.1.2, Kotlin 2.3.21, Java 21 über foojay), Subprojekt `rules` (nur `kotlin("jvm")`), Abhängigkeiten inkl. Flyway-Postgres, RestClient, springdoc 3.2.0, PDFBox 3.0.8; `spring-boot-docker-compose` entfernen (alle UC)
- [ ] Flyway `V1__schema.sql`: alle Tabellen aus Architektur 5.1, Audit-Trigger gegen UPDATE/DELETE (UC-01 bis UC-12, AC-12.4)
- [ ] `rules`: Eingabe- und Ergebnismodelle, Kundensegment R-SEG-01 mit Tests AC-05.1 bis AC-05.5 (UC-05)
- [ ] `rules`: Exposures R-EXP-01 und Risikoprofil R-PRO-01 bis 03, half-up, Tests AC-06.x und AC-07.x (UC-06, UC-07)
- [ ] `rules`: Eignung und BIB R-SUI-01/02, Tests AC-08.x (UC-08)
- [ ] `rules`: Absicherung R-HED-01 bis 07 (Tranchen, Terminkurs, Zinsdifferenz, Collar, Sperre, Szenario −15 bis +15 %), Tests AC-09.x (UC-09)
- [ ] `rules`: Liquidität R-LIQ-01 bis 03, Tests AC-10.x (UC-10)
- [ ] `rules`: Checkliste R-CHK-01 (BIB pro Produkt), Regeltexte wie Prototyp `reasons()`, `RuleCatalog`, Tests AC-11.1, AC-11.2, AC-11.9 (UC-09, UC-11)
- [ ] `common`: Problem-Details mit Slugs aus Architektur 10.2, `AdvisorContext`, `GET /api/me`, `/api/rules`, `/api/market-data` (alle UC)
- [ ] `client`: Anlegen, Liste mit Zusammenfassung, Detail mit Kennzahlen, PATCH (UC-01)
- [ ] `questionnaire`: Katalog mit 23 Schlüsseln und Texten aus dem Prototyp, Validierung, `GET …/questionnaire`, `PUT …/answers/{key}`, Bestätigen einzeln und pro Abschnitt, Vorschläge annehmen/verwerfen (UC-04)
- [ ] `assessment`: `AssessmentService` (Snapshot, Inputs-Hash, Version, Diff → `RULE_DECISION`), `ChangeTracker` mit Aufhebung der Freigabe, `GET …/assessment` inkl. `INCOMPLETE` (UC-05 bis UC-10, AC-01.4)
- [ ] `audit`: Audit-Service für alle Event-Typen, `GET …/audit-events` mit Blättern (UC-12)
- [ ] `demo`: `DemoDataSeeder` nach Architektur 13, danach Neuberechnung (AC-01.1, AC-04.1, AC-04.2, AC-12.3)
- [ ] `document`: Upload mit `PdfValidator`, Liste, Inhalt, Erneut auslesen, Löschen (UC-02, AC-02.1 bis AC-02.3)
- [ ] `agentclient` und `IngestDispatcher`: nach Commit, asynchron, 3 Retries, sonst `INGEST_UNAVAILABLE` (UC-02, AC-02.5)
- [ ] `internalapi`: Service-Key-Filter, Dokumentinhalt, Status-Callback mit `ingestId` und Statusmodell, Antwortvorschläge nach Antwortregeln inkl. Regelvorschlag Mindestliquidität (UC-02, UC-03, UC-14, AC-03.3 bis AC-03.5)
- [ ] `advisory`: Absicherungseinstellungen, Checkliste, Freigabe mit Blockern und `stale-assessment`, Zurückziehen, Freigabe-Historie (UC-09, UC-11, AC-11.3 bis AC-11.8)
- [ ] `advisory`: `ProtocolBuilder` (9 Abschnitte, Text, Einfrieren bei Freigabe), Begründung GET/PUT mit Versionsbindung (UC-12, UC-15, AC-12.1, AC-12.2, AC-15.2, AC-15.3)
- [ ] Optional: `POST /internal/clients/{id}/assessment/simulate` (UC-14, AC-14.5)
- [ ] Dockerfile (Temurin 21, Nicht-Root) und Integrationstests mit Testcontainers für die Akzeptanzkriterien UC-01 bis UC-12

### Backlog agent-service

In Bauordnung. Vertrag in `docs/api/agent-service.openapi.yaml`, Modell-Hinweise in `docs/architecture.md`, Abschnitt 16.

- [ ] Projekt-Setup mit `uv` (Python 3.12, Versionen aus Architektur 16), `config.py`, FastAPI-App, `GET /health`, ruff, pytest (alle UC)
- [ ] DB: `schema.sql` für Schema `rag` (HNSW-Index), AsyncEngine mit psycopg 3 (UC-02, UC-13)
- [ ] `security.py` (X-Service-Key) und `clients/treasury.py` (httpx, Timeouts, 3 Retries) (UC-02, UC-03)
- [ ] Synthetische Jahresrechnungen als PDF mit den Demo-Werten und Seitenzahlen aus Architektur 13 in `samples/`, Generator-Skript (UC-02, UC-03, UC-13)
- [ ] Ingest Teil 1: `POST /internal/ingest` (idempotent, `ingest_job`), Inhalt holen, SHA-256 prüfen, Text pro Seite mit pypdf, `NO_TEXT_LAYER`, Callback `TEXT_EXTRACTED` (UC-02, AC-02.4)
- [ ] Ingest Teil 2: seitentreues Chunking, Voyage-Embeddings (`voyage-4`, 1024), Chunks ersetzen, Callback `INDEXED`; `DELETE /internal/documents/{id}` (UC-02)
- [ ] `llm.py`: `ChatAnthropic` für `claude-opus-5-5` (Effort, Fallback, kein temperature, kein erzwungenes tool_choice) (UC-03, UC-13 bis UC-15)
- [ ] Extraktion: Seitenauswahl, Prompt mit Seiten als untrusted Daten, `with_structured_output(method="json_schema")`, nur extrahierbare Schlüssel, Fundstellen-Prüfung, Vorschläge posten, Callback `EXTRACTED` (UC-03, AC-03.1, AC-03.2, AC-03.6)
- [ ] `rag/search.py`: Vektorsuche mit Pflichtfilter `client_id`, `hnsw.iterative_scan` (UC-13, AC-13.2)
- [ ] Tools: Lese-Tools gegen die öffentlichen GET-Endpunkte, `propose_answer` gegen `/internal/…/answer-proposals`, `clientId` aus Tool-Runtime-Kontext, optional `simulate_assessment` (UC-13, UC-14)
- [ ] Agent: `create_agent` mit statischem Systemprompt (Rollen, Zitierregel `[S1]`, Injection-Regeln, Schreib-Tool nur auf ausdrückliche Bitte), `ModelCallLimitMiddleware` 8, `ToolCallLimitMiddleware` 5 für `propose_answer` (UC-13, UC-14, AC-13.4, AC-14.3)
- [ ] `POST /agent/chat`: SSE-Mapping auf die 8 Events, Kontext als Präfix der Benutzernachricht, Turn append-only speichern (`lc_messages` unverändert), Abbruch → `CANCELLED` (UC-13, UC-14, AC-13.1, AC-13.3, AC-14.2)
- [ ] Gesprächs-Endpunkte: Liste pro Kunde, Detail mit Turns, Löschen (UC-13, AC-13.5)
- [ ] `POST /agent/clients/{id}/rationale-draft` (UC-15, AC-15.1)
- [ ] Tests: Unit (Fundstellen-Prüfung, SSE-Mapping, Kundenfilter, Antwortverarbeitung), HTTP-Mocks mit respx; manueller Smoke-Test gegen die echte API für AC-03.1, AC-13.1, AC-13.4, AC-14.2
- [ ] Dockerfile (`python:3.12.16-slim-trixie`, uv 0.12.19, Nicht-Root)
