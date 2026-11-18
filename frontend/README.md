# Treasury Kompass – Frontend

- Lokal: `npm install && npm start` (HTTP gegen `/api` → :8080 und `/agent` → :8000, siehe `proxy.conf.json`; Header-Schalter «Demo-Daten» nutzt Mock-Daten).
- Build: `npm run build` (Produktions-Build nutzt Mock-Daten, Ausgabe `dist/treasury-kompass/browser`).
- Vercel: Projekt importieren, Root Directory `frontend`, Einstellungen kommen aus `vercel.json`.
