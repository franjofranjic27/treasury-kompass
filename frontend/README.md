# Treasury Kompass – Frontend

- Lokal: `npm install && npm start` (HTTP gegen `/api` → :8080 und `/agent` → :8000, siehe `proxy.conf.json`; Header-Schalter «Demo-Daten» nutzt Mock-Daten).
- Build: `npm run build` (Produktions-Build nutzt Mock-Daten, Ausgabe `dist/treasury-kompass/browser`).
- Vercel: Projekt importieren, Root Directory `frontend`, Einstellungen kommen aus `vercel.json`. Die SPA-Rewrite-Regel lässt `/api/…` und `/agent/…` aus, dort antwortet Vercel mit 404 statt mit `index.html`.

## Verhalten ohne Backend

- Wer «Demo-Daten» ausschaltet, löst zuerst `GET /api/me` aus. Antwortet kein Backend mit JSON, bleibt die App im Demo-Modus und zeigt einen Hinweis.
- Im HTTP-Modus werden Netzwerkfehler, 5xx und Antworten ohne JSON (z.B. eine HTML-Seite) zu `BackendUnavailableError`. Die App zeigt dann ein Banner mit «Zu Demo-Daten wechseln», nie einen Parser-Fehler.

## Layout und Chat

- Die Shell ist genau ein Viewport hoch (Header, Body, Footer). Es scrollen nur der Hauptbereich und der Chatverlauf.
- Der Copilot lässt sich über den Header-Button ein- und ausblenden; der Zustand bleibt in `localStorage`. Unter 1024 px ist der Chat eine Schublade, die geschlossen startet (Escape schliesst sie).
- Antworten des Copilots sind Markdown (GFM). Sie werden mit `marked` gerendert, mit DOMPurify auf eine feste Tag-Liste bereinigt und erst dann über `[innerHTML]` gebunden. Roh-HTML des Modells erscheint als Text, Links nur http(s) in neuem Tab, Marker wie `[S1]` werden zu Quellen-Chips.
