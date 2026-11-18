"""Text embeddings with Voyage AI (`voyage-4`, 1024 dimensions)."""

from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

import voyageai
from voyageai.error import VoyageError

from app.db.tables import EMBEDDING_DIMENSION

VOYAGE_TIMEOUT_SECONDS = 30.0
# The Voyage SDK retries nothing by default; two retries match the Anthropic SDK default.
VOYAGE_MAX_RETRIES = 2
# Well below the API limits (1000 texts, 320k tokens for voyage-4) for chunks of ~1200 characters.
_BATCH_SIZE = 128


class EmbeddingError(Exception):
    """Embedding provider unreachable or returned an unusable result."""


class Embedder(Protocol):
    @property
    def model_name(self) -> str: ...

    async def embed_documents(self, texts: Sequence[str]) -> list[list[float]]: ...

    async def embed_queries(self, texts: Sequence[str]) -> list[list[float]]: ...


class VoyageEmbedder:
    """Calls the Voyage SDK directly.

    `langchain-voyageai` is not used because it batches with a tokenizer downloaded from the
    Hugging Face Hub (blocking, extra network dependency) and exposes no request timeout.
    """

    def __init__(self, api_key: str, model: str) -> None:
        self._client = voyageai.AsyncClient(
            api_key=api_key,
            timeout=VOYAGE_TIMEOUT_SECONDS,
            max_retries=VOYAGE_MAX_RETRIES,
        )
        self._model = model

    @property
    def model_name(self) -> str:
        return self._model

    async def embed_documents(self, texts: Sequence[str]) -> list[list[float]]:
        return await self._embed(texts, input_type="document")

    async def embed_queries(self, texts: Sequence[str]) -> list[list[float]]:
        return await self._embed(texts, input_type="query")

    async def _embed(self, texts: Sequence[str], *, input_type: str) -> list[list[float]]:
        vectors: list[list[float]] = []
        for start in range(0, len(texts), _BATCH_SIZE):
            batch = list(texts[start : start + _BATCH_SIZE])
            try:
                result = await self._client.embed(
                    batch,
                    model=self._model,
                    input_type=input_type,
                    output_dimension=EMBEDDING_DIMENSION,
                )
            except VoyageError as error:
                raise EmbeddingError(f"Voyage AI: {type(error).__name__}") from error
            vectors.extend([list(map(float, vector)) for vector in result.embeddings])
        _check_shape(vectors, expected_count=len(texts))
        return vectors


def _check_shape(vectors: Sequence[Sequence[float]], *, expected_count: int) -> None:
    if len(vectors) != expected_count:
        raise EmbeddingError(f"{len(vectors)} statt {expected_count} Embeddings erhalten")
    wrong = next((len(vector) for vector in vectors if len(vector) != EMBEDDING_DIMENSION), None)
    if wrong is not None:
        raise EmbeddingError(f"Embedding mit Dimension {wrong} statt {EMBEDDING_DIMENSION}")
