"""Rationale draft from the current assessment (UC-15). Formulates only; stores nothing."""

from __future__ import annotations

import logging
from datetime import UTC, datetime
from uuid import UUID

import anthropic
from langchain_core.exceptions import ModelError
from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from pydantic import ValidationError

from app.agent.prompts import RATIONALE_SYSTEM_PROMPT, system_data
from app.clients.treasury import TreasuryClient, TreasuryError, TreasuryNotFoundError
from app.models.rationale import Audience, RationaleDraft
from app.models.treasury import AssessmentHeader, HedgesView, JsonDocument
from app.problems import ProblemCode, ProblemError

logger = logging.getLogger(__name__)

_AUDIENCE_NOTE = {
    "CLIENT": "Zielgruppe: CLIENT (verständlich für den CFO, ohne Regel-IDs).",
    "ADVISOR": "Zielgruppe: ADVISOR (knapp, mit Regel-IDs).",
}


class RationaleService:
    def __init__(self, *, model: BaseChatModel, treasury: TreasuryClient, model_name: str) -> None:
        self._model = model
        self._treasury = treasury
        self._model_name = model_name

    async def draft(self, client_id: UUID, audience: Audience) -> RationaleDraft:
        assessment = await self._assessment(client_id)
        header = _header(assessment)
        if header.status == "INCOMPLETE":
            missing = ", ".join(header.missing_inputs) or "unbekannt"
            raise ProblemError(ProblemCode.ASSESSMENT_INCOMPLETE, f"Es fehlen Pflichtangaben: {missing}.")
        messages = [
            SystemMessage(RATIONALE_SYSTEM_PROMPT),
            HumanMessage(_user_message(assessment, audience)),
        ]
        response = await self._invoke(messages)
        text = response.text.strip()
        if not text:
            raise ProblemError(ProblemCode.LLM_UNAVAILABLE, "Das Sprachmodell hat keinen Text geliefert.")
        return RationaleDraft(
            client_id=client_id,
            assessment_version=header.version,
            text=text,
            model=self._model_name,
            generated_at=datetime.now(UTC),
        )

    async def _assessment(self, client_id: UUID) -> JsonDocument:
        try:
            return await self._treasury.get_assessment(client_id)
        except TreasuryNotFoundError as error:
            raise ProblemError(ProblemCode.NOT_FOUND, "Kunde nicht gefunden.") from error
        except TreasuryError as error:
            raise ProblemError(ProblemCode.UPSTREAM_UNAVAILABLE, "treasury-service nicht erreichbar.") from error

    async def _invoke(self, messages: list[SystemMessage | HumanMessage]) -> AIMessage:
        try:
            response = await self._model.ainvoke(messages)
        except (ModelError, anthropic.APIError) as error:
            logger.warning("Begründungsentwurf: Sprachmodell nicht verfügbar", exc_info=True)
            raise ProblemError(ProblemCode.LLM_UNAVAILABLE, "Das Sprachmodell ist nicht erreichbar.") from error
        if not isinstance(response, AIMessage):
            raise ProblemError(ProblemCode.INTERNAL_ERROR, "Unerwartete Antwort des Sprachmodells.")
        if response.response_metadata.get("stop_reason") == "refusal":
            # The contract has no refusal problem code; the draft is simply not available.
            raise ProblemError(ProblemCode.LLM_UNAVAILABLE, "Das Sprachmodell hat den Entwurf abgelehnt.")
        return response


def _header(assessment: JsonDocument) -> AssessmentHeader:
    try:
        return AssessmentHeader.model_validate(assessment)
    except ValidationError as error:
        raise ProblemError(ProblemCode.UPSTREAM_UNAVAILABLE, "Beurteilung entspricht nicht dem Vertrag.") from error


def hedge_staggering(assessment: JsonDocument) -> list[str]:
    """Tranche ratios per currency exactly as the rules engine set them, e.g. «EUR: 80 / 70 / 60 / 50 %»."""
    if not isinstance(assessment, dict) or not isinstance(assessment.get("hedges"), dict):
        return []
    try:
        hedges = HedgesView.model_validate(assessment["hedges"])
    except ValidationError:
        logger.warning("hedges entspricht nicht dem Vertrag; Staffelung wird nicht vorgegeben", exc_info=True)
        return []
    return [
        f"{proposal.currency}: {' / '.join(str(t.effective_ratio) for t in proposal.tranches)} %"
        for proposal in hedges.proposals
    ]


def _user_message(assessment: JsonDocument, audience: Audience) -> str:
    staggering = "\n".join(hedge_staggering(assessment)) or "keine Absicherung vorgeschlagen"
    return (
        f"{system_data('assessment', assessment)}\n\n"
        f"<staffelung>\n{staggering}\n</staffelung>\n\n"
        f"{_AUDIENCE_NOTE[audience]} Formuliere die Begründung."
    )
