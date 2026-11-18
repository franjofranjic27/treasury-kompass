"""Key figure extraction with page citations: one structured-output call, no tools (architecture.md 9.1, 12)."""

from __future__ import annotations

import logging
import math
from collections.abc import Sequence
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from uuid import UUID

import anthropic
from langchain_core.exceptions import ModelError
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from app.agent.prompts import EXTRACTION_SYSTEM_PROMPT, extraction_user_message
from app.ingest.citations import excerpt_found_on_page
from app.models.questions import (
    AMOUNT_KEYS,
    CONFIDENCE_ORDER,
    FX_FLOW_KEYS,
    KNOWLEDGE_LEVEL_OPTIONS,
    NEGATIVE_AMOUNT_KEYS,
    TREASURY_ORGANISATION_OPTIONS,
    AnswerValue,
    Confidence,
    ExtractableKey,
)
from app.models.treasury import CitationInput, ProposalInput
from app.rag.search import DocumentSearch
from app.rag.store import PageText

logger = logging.getLogger(__name__)

MAX_EXCERPT_CHARS = 300
MAX_SELECTED_PAGES = 20
_HITS_PER_QUERY = 8
# Fixed retrieval queries for long documents (use-cases.md, UC-03 step 1).
PAGE_SELECTION_QUERIES: tuple[str, ...] = (
    "Bilanz Total Aktiven Eigenkapital flüssige Mittel",
    "Erfolgsrechnung Umsatzerlös Betriebsaufwand Betriebsergebnis EBIT",
    "Anhang Fremdwährungen Umsatz und Einkauf nach Währungen EUR USD",
    "Derivate Devisentermingeschäfte Devisenoptionen offene Kontrakte",
    "Treasury-Organisation Finanzabteilung Group Treasury",
    "Umsatz nach Märkten Regionen Währungen Segmentbericht",
    "Bankkredite Finanzverbindlichkeiten SARON variabler Zins",
)


class ExtractedFigure(BaseModel):
    """One value as returned by the model."""

    question_key: ExtractableKey = Field(description="Schlüssel der Fragebogen-Frage")
    value: float | str = Field(
        description="Betrag in Mio. als Zahl (fin.*, fx.*) oder Code (segment.*, knowledge.*)",
    )
    page: int = Field(description="Seitennummer aus dem Attribut page")
    excerpt: str = Field(description="Wörtlicher Auszug von dieser Seite, höchstens 300 Zeichen")
    location_label: str = Field(description="Kurze Fundstelle, z.B. «Bilanz, S. 4»")
    confidence: Confidence
    rationale: str = Field(description="Ein Satz, wie der Wert zustande kommt")


class ExtractionOutput(BaseModel):
    figures: list[ExtractedFigure]


class ExtractionRefusedError(Exception):
    """The model refused (also after the server-side fallback)."""


class ExtractionFailedError(Exception):
    """The model was unreachable or returned no parseable result."""


@dataclass(frozen=True)
class ExtractionDocument:
    client_id: UUID
    document_id: UUID
    document_name: str
    pages: Sequence[PageText]


class KeyFigureExtractor:
    def __init__(self, model: BaseChatModel, search: DocumentSearch, full_document_max_pages: int) -> None:
        self._structured_model = model.with_structured_output(ExtractionOutput, method="json_schema", include_raw=True)
        self._search = search
        self._full_document_max_pages = full_document_max_pages

    async def extract(self, document: ExtractionDocument) -> list[ProposalInput]:
        pages = await self._select_pages(document)
        output = await self._call_model(document, pages)
        page_texts = {page.page: page.text for page in pages}
        proposals = to_proposals(output.figures, page_texts=page_texts, document_id=document.document_id)
        logger.info(
            "Extraktion %s: %d Seiten gelesen, %d Werte, %d belegt",
            document.document_id,
            len(pages),
            len(proposals),
            sum(1 for proposal in proposals if proposal.citation is not None and proposal.citation.verified),
        )
        return proposals

    async def _select_pages(self, document: ExtractionDocument) -> list[PageText]:
        pages = [page for page in document.pages if page.text.strip()]
        if len(document.pages) <= self._full_document_max_pages:
            return pages
        selected = await self._pages_by_retrieval(document)
        return [page for page in pages if page.page in selected]

    async def _pages_by_retrieval(self, document: ExtractionDocument) -> set[int]:
        vectors = await self._search.embed_queries(PAGE_SELECTION_QUERIES)
        ranked_per_query = [
            [
                hit.page
                for hit in await self._search.search_by_vector(
                    client_id=document.client_id,
                    document_id=document.document_id,
                    vector=vector,
                    k=_HITS_PER_QUERY,
                )
            ]
            for vector in vectors
        ]
        return _round_robin_pages(ranked_per_query, limit=MAX_SELECTED_PAGES)

    async def _call_model(self, document: ExtractionDocument, pages: Sequence[PageText]) -> ExtractionOutput:
        messages = [
            SystemMessage(EXTRACTION_SYSTEM_PROMPT),
            HumanMessage(extraction_user_message(document.document_name, pages, total_pages=len(document.pages))),
        ]
        try:
            result = await self._structured_model.ainvoke(messages)
        except (ModelError, anthropic.APIError) as error:
            raise ExtractionFailedError(f"Sprachmodell nicht erreichbar: {type(error).__name__}") from error
        if not isinstance(result, dict):
            raise ExtractionFailedError("Unerwartetes Ergebnis der strukturierten Ausgabe")
        raw = result.get("raw")
        if isinstance(raw, AIMessage) and raw.response_metadata.get("stop_reason") == "refusal":
            raise ExtractionRefusedError("Das Sprachmodell hat die Extraktion abgelehnt")
        parsed = result.get("parsed")
        if not isinstance(parsed, ExtractionOutput):
            raise ExtractionFailedError("Antwort des Sprachmodells entspricht nicht dem Schema")
        return parsed


def _round_robin_pages(ranked_per_query: Sequence[Sequence[int]], *, limit: int) -> set[int]:
    """Take the best page of every query, then the second best, ... until `limit` distinct pages."""
    selected: list[int] = []
    depth = max((len(ranked) for ranked in ranked_per_query), default=0)
    for rank in range(depth):
        for ranked in ranked_per_query:
            if rank < len(ranked) and ranked[rank] not in selected:
                selected.append(ranked[rank])
                if len(selected) == limit:
                    return set(selected)
    return set(selected)


def to_proposals(
    figures: Sequence[ExtractedFigure],
    *,
    page_texts: dict[int, str],
    document_id: UUID,
) -> list[ProposalInput]:
    """Validate, verify citations and keep the most confident figure per key."""
    best: dict[str, ProposalInput] = {}
    for figure in figures:
        value = _validated_value(figure)
        if value is None:
            logger.info("Extraktion: Wert für %s verworfen (Typ oder Bereich ungültig)", figure.question_key)
            continue
        proposal = _proposal(figure, value, page_texts=page_texts, document_id=document_id)
        current = best.get(proposal.question_key)
        if current is None or CONFIDENCE_ORDER[proposal.confidence] > CONFIDENCE_ORDER[current.confidence]:
            best[proposal.question_key] = proposal
    return list(best.values())


def _proposal(
    figure: ExtractedFigure,
    value: AnswerValue,
    *,
    page_texts: dict[int, str],
    document_id: UUID,
) -> ProposalInput:
    excerpt = figure.excerpt.strip()[:MAX_EXCERPT_CHARS]
    page_text = page_texts.get(figure.page)
    verified = page_text is not None and excerpt_found_on_page(excerpt, page_text)
    confidence = figure.confidence if verified else "LOW"
    if figure.question_key in FX_FLOW_KEYS:
        # Annual reports describe the past year; the questionnaire asks for the next 12 months.
        confidence = _at_most(confidence, "MEDIUM")
    citation = CitationInput(
        document_id=document_id,
        page=max(1, figure.page),
        location_label=figure.location_label.strip()[:200] or None,
        excerpt=excerpt,
        verified=verified,
    )
    return ProposalInput(
        question_key=figure.question_key,
        value=value,
        confidence=confidence,
        rationale=figure.rationale.strip()[:1000] or None,
        citation=citation,
    )


def _at_most(confidence: Confidence, ceiling: Confidence) -> Confidence:
    return confidence if CONFIDENCE_ORDER[confidence] <= CONFIDENCE_ORDER[ceiling] else ceiling


def _validated_value(figure: ExtractedFigure) -> AnswerValue | None:
    key = figure.question_key
    if key in AMOUNT_KEYS:
        return _amount(figure.value, allow_negative=key in NEGATIVE_AMOUNT_KEYS)
    if key == "segment.treasuryOrganisation":
        return _option(figure.value, TREASURY_ORGANISATION_OPTIONS)
    return _option(figure.value, KNOWLEDGE_LEVEL_OPTIONS)


def _amount(value: float | str, *, allow_negative: bool) -> float | None:
    if isinstance(value, str) or not math.isfinite(value):
        return None
    amount = float(Decimal(str(value)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
    if amount < 0 and not allow_negative:
        return None
    return amount


def _option(value: float | str, allowed: frozenset[str]) -> str | None:
    if not isinstance(value, str):
        return None
    code = value.strip().upper()
    return code if code in allowed else None
