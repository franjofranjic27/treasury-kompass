-- Schema rag of the agent-service (docs/architecture.md, section 5.2).
-- Idempotent: applied at every start. Schema and extension are normally created by
-- infra/postgres/init (role agent_app has no CREATE on the database); the guarded
-- blocks only create them when missing, e.g. in tests running as superuser.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'rag') THEN
        CREATE SCHEMA rag;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        CREATE EXTENSION vector SCHEMA public;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS rag.document_page (
    document_id uuid        NOT NULL,
    client_id   uuid        NOT NULL,
    page        int         NOT NULL CHECK (page >= 1),
    text        text        NOT NULL,
    char_count  int         NOT NULL CHECK (char_count >= 0),
    PRIMARY KEY (document_id, page)
);
CREATE INDEX IF NOT EXISTS document_page_client_idx ON rag.document_page (client_id);

CREATE TABLE IF NOT EXISTS rag.document_chunk (
    id              uuid         PRIMARY KEY,
    client_id       uuid         NOT NULL,
    document_id     uuid         NOT NULL,
    document_name   varchar(255) NOT NULL,
    page            int          NOT NULL CHECK (page >= 1),
    chunk_index     int          NOT NULL CHECK (chunk_index >= 0),
    content         text         NOT NULL,
    embedding       vector(1024) NOT NULL,
    embedding_model varchar(40)  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS document_chunk_client_document_idx ON rag.document_chunk (client_id, document_id);
CREATE INDEX IF NOT EXISTS document_chunk_embedding_idx
    ON rag.document_chunk USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE TABLE IF NOT EXISTS rag.ingest_job (
    ingest_id     uuid         PRIMARY KEY,
    document_id   uuid         NOT NULL,
    client_id     uuid         NOT NULL,
    status        varchar(16)  NOT NULL CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    stage         varchar(20)  NOT NULL,
    error_code    varchar(40),
    error_message varchar(500),
    started_at    timestamptz  NOT NULL DEFAULT now(),
    finished_at   timestamptz
);
CREATE INDEX IF NOT EXISTS ingest_job_document_idx ON rag.ingest_job (document_id);

CREATE TABLE IF NOT EXISTS rag.conversation (
    id         uuid         PRIMARY KEY,
    client_id  uuid         NOT NULL,
    title      varchar(200) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS conversation_client_updated_idx ON rag.conversation (client_id, updated_at DESC);

-- lc_messages holds every LangChain message of the turn, thinking blocks included, exactly as
-- exchanged with the model. Turns are written once and never updated (append-only history).
CREATE TABLE IF NOT EXISTS rag.turn (
    id                uuid        PRIMARY KEY,
    conversation_id   uuid        NOT NULL REFERENCES rag.conversation (id) ON DELETE CASCADE,
    seq               int         NOT NULL CHECK (seq >= 1),
    status            varchar(12) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED', 'CANCELLED')),
    user_message      text        NOT NULL,
    assistant_message text,
    context           jsonb,
    lc_messages       jsonb       NOT NULL DEFAULT '[]'::jsonb,
    citations         jsonb       NOT NULL DEFAULT '[]'::jsonb,
    tool_events       jsonb       NOT NULL DEFAULT '[]'::jsonb,
    proposals         jsonb       NOT NULL DEFAULT '[]'::jsonb,
    usage             jsonb,
    created_at        timestamptz NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    UNIQUE (conversation_id, seq)
);

CREATE OR REPLACE FUNCTION rag.reject_turn_update() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'rag.turn ist append-only';
END
$$;

CREATE OR REPLACE TRIGGER turn_append_only
    BEFORE UPDATE ON rag.turn
    FOR EACH ROW EXECUTE FUNCTION rag.reject_turn_update();
