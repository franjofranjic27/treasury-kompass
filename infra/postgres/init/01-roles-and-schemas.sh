#!/usr/bin/env bash
# Rollen und Schemas für Treasury Kompass (docs/architecture.md, Abschnitte 5 und 14).
#
# Läuft einmal als Superuser, wenn das Datenverzeichnis neu angelegt wird
# (/docker-entrypoint-initdb.d). Das Skript ist idempotent und kann manuell erneut laufen,
# z.B. nach einem Passwortwechsel in .env:
#   docker compose exec postgres /docker-entrypoint-initdb.d/01-roles-and-schemas.sh
#
# Ergebnis:
#   treasury_app  Owner von Schema treasury (Flyway im treasury-service)
#   agent_app     Owner von Schema rag (agent-service), search_path = rag, public
#   vector        Extension in public, damit agent_app den Typ über den search_path findet
#   Keine Rolle hat Zugriff auf das Schema der anderen; PUBLIC darf weder verbinden noch anlegen.

# Kein "set -u": Ohne Ausführungsrecht würde der Entrypoint das Skript per "source" laden,
# und -u bliebe dann für den Rest des Entrypoints aktiv.
set -Eeo pipefail

: "${POSTGRES_USER:?POSTGRES_USER fehlt}"
: "${POSTGRES_DB:?POSTGRES_DB fehlt}"
: "${TREASURY_DB_PASSWORD:?TREASURY_DB_PASSWORD fehlt}"
: "${AGENT_DB_PASSWORD:?AGENT_DB_PASSWORD fehlt}"

# Passwörter und Datenbankname liest psql per \getenv direkt aus der Umgebung (nicht über die
# Kommandozeile, also nicht in "ps" sichtbar) und setzt sie mit :'…' bzw. :"…" korrekt
# gequotet ein. Das Heredoc ist gequotet, die Shell expandiert darin nichts.
psql --no-psqlrc --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv treasury_password TREASURY_DB_PASSWORD
\getenv agent_password AGENT_DB_PASSWORD
\getenv db_name POSTGRES_DB

BEGIN;

SELECT format('CREATE ROLE %I', role_name)
FROM unnest(ARRAY['treasury_app', 'agent_app']) AS role_name
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = role_name)
\gexec

ALTER ROLE treasury_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'treasury_password';
ALTER ROLE agent_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'agent_password';

REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db_name" TO treasury_app, agent_app;

-- Ab PostgreSQL 15 ohnehin Standard, hier explizit. USAGE auf public bleibt (Typ vector).
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE SCHEMA IF NOT EXISTS treasury AUTHORIZATION treasury_app;
CREATE SCHEMA IF NOT EXISTS rag AUTHORIZATION agent_app;
ALTER SCHEMA treasury OWNER TO treasury_app;
ALTER SCHEMA rag OWNER TO agent_app;
REVOKE ALL ON SCHEMA treasury FROM PUBLIC;
REVOKE ALL ON SCHEMA rag FROM PUBLIC;

ALTER ROLE agent_app SET search_path = rag, public;

COMMIT;
SQL
