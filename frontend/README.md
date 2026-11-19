# Treasury Kompass – Frontend

Die App läuft nur lokal, es gibt kein Hosting.

## Starten

- Ganzer Stack: einmal `npm ci && npm run build` (Ausgabe `dist/treasury-kompass/browser`), dann im Repo-Root `docker compose up -d --build` und http://localhost:8088 öffnen. Das Gateway liefert den Build aus und leitet `/api` an den treasury-service und `/agent` an den agent-service. Nach einem neuen Build `docker compose up -d --force-recreate gateway`: Der Build legt den Ordner neu an, und je nach Docker-Version hängt der Bind-Mount sonst noch am alten.
- Entwicklung: `npm ci && npm start` (`ng serve` auf http://localhost:4200). `proxy.conf.json` leitet `/api` → :8080 und `/agent` → :8000, die Services laufen dafür in Compose oder lokal.

## Datenmodus

- Standard ist das echte Backend. Beim Start fragt die App `GET /api/me` ab (5 s Timeout). Antwortet kein Backend mit JSON, startet sie mit den Demo-Daten und zeigt einen Hinweis, der sich schliessen lässt. Dieser Rückfall wird nicht gespeichert, der nächste Start versucht es wieder mit dem Backend.
- Der Header-Schalter «Demo-Daten» speichert die Wahl in `localStorage` und geht dem Standard vor. Wer ihn ausschaltet, löst zuerst `GET /api/me` aus. Antwortet kein Backend mit JSON, bleibt die App im Demo-Modus und zeigt einen Hinweis.
- Im HTTP-Modus werden Netzwerkfehler, 5xx und Antworten ohne JSON (z.B. eine HTML-Seite) zu `BackendUnavailableError`. Die App zeigt dann ein Banner mit «Zu Demo-Daten wechseln», nie einen Parser-Fehler. Das gilt auch, wenn das Backend während der Sitzung ausfällt oder die gespeicherte Wahl «Backend» auf keines trifft.

## Layout und Chat

- Die Shell ist genau ein Viewport hoch (Header, Body, Footer). Es scrollen nur der Hauptbereich und der Chatverlauf.
- Der Copilot lässt sich über den Header-Button ein- und ausblenden; der Zustand bleibt in `localStorage`. Unter 1024 px ist der Chat eine Schublade, die geschlossen startet (Escape schliesst sie).
- Antworten des Copilots sind Markdown (GFM). Sie werden mit `marked` gerendert, mit DOMPurify auf eine feste Tag-Liste bereinigt und erst dann über `[innerHTML]` gebunden. Roh-HTML des Modells erscheint als Text, Links nur http(s) in neuem Tab, Marker wie `[S1]` werden zu Quellen-Chips.
