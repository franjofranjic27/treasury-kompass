"""Persistence of page texts and embedded chunks per document (schema `rag`)."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime
from uuid import UUID, uuid4

from sqlalchemy import delete, insert, select
from sqlalchemy.ext.asyncio import AsyncEngine

from app.db.tables import document_chunk, document_page


@dataclass(frozen=True)
class PageText:
    page: int
    text: str


@dataclass(frozen=True)
class EmbeddedChunk:
    page: int
    chunk_index: int
    content: str
    embedding: Sequence[float]


class DocumentStore:
    def __init__(self, engine: AsyncEngine) -> None:
        self._engine = engine

    async def replace_pages(self, *, client_id: UUID, document_id: UUID, pages: Sequence[PageText]) -> None:
        rows = [
            {
                "document_id": document_id,
                "client_id": client_id,
                "page": page.page,
                "text": page.text,
                "char_count": len(page.text),
            }
            for page in pages
        ]
        async with self._engine.begin() as connection:
            await connection.execute(delete(document_page).where(document_page.c.document_id == document_id))
            if rows:
                await connection.execute(insert(document_page), rows)

    async def replace_chunks(
        self,
        *,
        client_id: UUID,
        document_id: UUID,
        document_name: str,
        embedding_model: str,
        chunks: Sequence[EmbeddedChunk],
    ) -> int:
        now = datetime.now(UTC)
        rows = [
            {
                "id": uuid4(),
                "client_id": client_id,
                "document_id": document_id,
                "document_name": document_name,
                "page": chunk.page,
                "chunk_index": chunk.chunk_index,
                "content": chunk.content,
                "embedding": list(chunk.embedding),
                "embedding_model": embedding_model,
                "created_at": now,
            }
            for chunk in chunks
        ]
        async with self._engine.begin() as connection:
            await connection.execute(delete(document_chunk).where(document_chunk.c.document_id == document_id))
            if rows:
                await connection.execute(insert(document_chunk), rows)
        return len(rows)

    async def load_pages(self, *, client_id: UUID, document_id: UUID) -> list[PageText]:
        query = (
            select(document_page.c.page, document_page.c.text)
            .where(document_page.c.client_id == client_id, document_page.c.document_id == document_id)
            .order_by(document_page.c.page)
        )
        async with self._engine.connect() as connection:
            result = await connection.execute(query)
            return [PageText(page=row.page, text=row.text) for row in result]

    async def purge_document(self, document_id: UUID) -> None:
        async with self._engine.begin() as connection:
            await connection.execute(delete(document_chunk).where(document_chunk.c.document_id == document_id))
            await connection.execute(delete(document_page).where(document_page.c.document_id == document_id))
