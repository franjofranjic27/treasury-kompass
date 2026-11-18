"""Vector search over document chunks. Every search is filtered by `client_id` (INV-6)."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from uuid import UUID

from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncEngine

from app.db.tables import document_chunk
from app.rag.embeddings import Embedder


@dataclass(frozen=True)
class SearchHit:
    chunk_id: UUID
    document_id: UUID
    document_name: str
    page: int
    chunk_index: int
    content: str
    score: float


class DocumentSearch:
    def __init__(self, engine: AsyncEngine, embedder: Embedder) -> None:
        self._engine = engine
        self._embedder = embedder

    async def embed_queries(self, queries: Sequence[str]) -> list[list[float]]:
        return await self._embedder.embed_queries(queries)

    async def search(
        self,
        *,
        client_id: UUID,
        query: str,
        k: int,
        document_id: UUID | None = None,
    ) -> list[SearchHit]:
        [vector] = await self._embedder.embed_queries([query])
        return await self.search_by_vector(client_id=client_id, vector=vector, k=k, document_id=document_id)

    async def search_by_vector(
        self,
        *,
        client_id: UUID,
        vector: Sequence[float],
        k: int,
        document_id: UUID | None = None,
    ) -> list[SearchHit]:
        distance = document_chunk.c.embedding.cosine_distance(list(vector)).label("distance")
        query = (
            select(
                document_chunk.c.id,
                document_chunk.c.document_id,
                document_chunk.c.document_name,
                document_chunk.c.page,
                document_chunk.c.chunk_index,
                document_chunk.c.content,
                distance,
            )
            .where(document_chunk.c.client_id == client_id)
            .order_by(distance)
            .limit(k)
        )
        if document_id is not None:
            query = query.where(document_chunk.c.document_id == document_id)
        async with self._engine.begin() as connection:
            # Without iterative scan the HNSW index may return fewer than k rows once the filter applies.
            await connection.execute(text("SET LOCAL hnsw.iterative_scan = relaxed_order"))
            rows = (await connection.execute(query)).all()
        hits = [
            SearchHit(
                chunk_id=row.id,
                document_id=row.document_id,
                document_name=row.document_name,
                page=row.page,
                chunk_index=row.chunk_index,
                content=row.content,
                score=_similarity(row.distance),
            )
            for row in rows
        ]
        # relaxed_order may return rows slightly out of order.
        return sorted(hits, key=lambda hit: hit.score, reverse=True)


def _similarity(cosine_distance: float) -> float:
    return min(1.0, max(0.0, 1.0 - float(cosine_distance)))
