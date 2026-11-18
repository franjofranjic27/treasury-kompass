"""SQLAlchemy Core table definitions for schema `rag`. The DDL itself lives in `schema.sql`."""

from __future__ import annotations

from pgvector.sqlalchemy import Vector
from sqlalchemy import Column, DateTime, Integer, MetaData, String, Table, Text
from sqlalchemy.dialects.postgresql import JSONB, UUID

EMBEDDING_DIMENSION = 1024

metadata = MetaData(schema="rag")

document_page = Table(
    "document_page",
    metadata,
    Column("document_id", UUID(as_uuid=True), primary_key=True),
    Column("client_id", UUID(as_uuid=True), nullable=False),
    Column("page", Integer, primary_key=True),
    Column("text", Text, nullable=False),
    Column("char_count", Integer, nullable=False),
)

document_chunk = Table(
    "document_chunk",
    metadata,
    Column("id", UUID(as_uuid=True), primary_key=True),
    Column("client_id", UUID(as_uuid=True), nullable=False),
    Column("document_id", UUID(as_uuid=True), nullable=False),
    Column("document_name", String(255), nullable=False),
    Column("page", Integer, nullable=False),
    Column("chunk_index", Integer, nullable=False),
    Column("content", Text, nullable=False),
    Column("embedding", Vector(EMBEDDING_DIMENSION), nullable=False),
    Column("embedding_model", String(40), nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
)

ingest_job = Table(
    "ingest_job",
    metadata,
    Column("ingest_id", UUID(as_uuid=True), primary_key=True),
    Column("document_id", UUID(as_uuid=True), nullable=False),
    Column("client_id", UUID(as_uuid=True), nullable=False),
    Column("status", String(16), nullable=False),
    Column("stage", String(20), nullable=False),
    Column("error_code", String(40)),
    Column("error_message", String(500)),
    Column("started_at", DateTime(timezone=True), nullable=False),
    Column("finished_at", DateTime(timezone=True)),
)

conversation = Table(
    "conversation",
    metadata,
    Column("id", UUID(as_uuid=True), primary_key=True),
    Column("client_id", UUID(as_uuid=True), nullable=False),
    Column("title", String(200), nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
    Column("updated_at", DateTime(timezone=True), nullable=False),
)

turn = Table(
    "turn",
    metadata,
    Column("id", UUID(as_uuid=True), primary_key=True),
    Column("conversation_id", UUID(as_uuid=True), nullable=False),
    Column("seq", Integer, nullable=False),
    Column("status", String(12), nullable=False),
    Column("user_message", Text, nullable=False),
    Column("assistant_message", Text),
    Column("context", JSONB),
    Column("lc_messages", JSONB, nullable=False),
    Column("citations", JSONB, nullable=False),
    Column("tool_events", JSONB, nullable=False),
    Column("proposals", JSONB, nullable=False),
    Column("usage", JSONB),
    Column("created_at", DateTime(timezone=True), nullable=False),
    Column("completed_at", DateTime(timezone=True)),
)
