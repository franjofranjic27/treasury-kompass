"""DTOs of the treasury-service contract (docs/api/treasury-service.openapi.yaml) used by this service."""

from __future__ import annotations

from enum import StrEnum
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, JsonValue

from app.models.common import CamelModel, StrictCamelModel
from app.models.questions import AnswerValue, Confidence, QuestionKey

Currency = Literal["EUR", "USD"]
StepDown = Literal[0, 5, 10, 15]


class DocumentStatus(StrEnum):
    TEXT_EXTRACTED = "TEXT_EXTRACTED"
    INDEXED = "INDEXED"
    EXTRACTED = "EXTRACTED"
    FAILED = "FAILED"


class DocumentErrorCode(StrEnum):
    INGEST_UNAVAILABLE = "INGEST_UNAVAILABLE"
    CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH"
    PDF_PARSE_ERROR = "PDF_PARSE_ERROR"
    NO_TEXT_LAYER = "NO_TEXT_LAYER"
    EMBEDDING_FAILED = "EMBEDDING_FAILED"
    EXTRACTION_FAILED = "EXTRACTION_FAILED"
    LLM_REFUSAL = "LLM_REFUSAL"
    INTERNAL_ERROR = "INTERNAL_ERROR"


class ProposalSource(StrEnum):
    DOCUMENT = "DOCUMENT"
    AGENT = "AGENT"


class ProposalOutcome(StrEnum):
    APPLIED = "APPLIED"
    OPEN = "OPEN"
    UNCHANGED = "UNCHANGED"
    REJECTED = "REJECTED"


class DocumentStatusUpdate(StrictCamelModel):
    ingest_id: UUID
    status: DocumentStatus
    page_count: int | None = Field(default=None, ge=1)
    chunk_count: int | None = Field(default=None, ge=0)
    error_code: DocumentErrorCode | None = None
    error_message: str | None = Field(default=None, max_length=500)


class CitationInput(StrictCamelModel):
    document_id: UUID | None
    page: int = Field(ge=1)
    location_label: str | None = Field(default=None, max_length=200)
    excerpt: str = Field(max_length=600)
    verified: bool


class ProposalInput(StrictCamelModel):
    question_key: QuestionKey
    value: AnswerValue
    confidence: Confidence
    rationale: str | None = Field(default=None, max_length=1000)
    citation: CitationInput | None = None


class ProposalBatch(StrictCamelModel):
    source: ProposalSource
    document_id: UUID | None = None
    ingest_id: UUID | None = None
    conversation_id: UUID | None = None
    proposals: tuple[ProposalInput, ...] = Field(min_length=1, max_length=50)


class ProposalResult(CamelModel):
    model_config = ConfigDict(extra="ignore")

    question_key: str
    proposal_id: UUID | None = None
    outcome: ProposalOutcome
    reason: str | None = None


class ProposalBatchResult(CamelModel):
    model_config = ConfigDict(extra="ignore")

    results: tuple[ProposalResult, ...]


class HedgeSettingUpdate(StrictCamelModel):
    start_ratio: int | None = Field(ge=0, le=100)
    step_down: StepDown = 10
    collar_enabled: bool | None = None


class SimulationRequest(StrictCamelModel):
    overrides: dict[QuestionKey, AnswerValue] = Field(default_factory=dict)
    hedge_settings: dict[Currency, HedgeSettingUpdate] = Field(default_factory=dict)


class DocumentContent(BaseModel):
    model_config = ConfigDict(frozen=True)

    data: bytes
    client_id: UUID | None
    sha256: str | None


class ClientInfo(CamelModel):
    """The few client fields the agent needs; the full `ClientDetail` is passed through as JSON."""

    model_config = ConfigDict(extra="ignore")

    id: UUID
    name: str


class AssessmentHeader(CamelModel):
    model_config = ConfigDict(extra="ignore")

    version: int
    status: Literal["COMPLETE", "INCOMPLETE"]
    missing_inputs: tuple[str, ...] = ()


class QuestionOption(CamelModel):
    model_config = ConfigDict(extra="ignore")

    value: AnswerValue
    label: str


class QuestionInfo(CamelModel):
    model_config = ConfigDict(extra="ignore")

    key: str
    short_label: str
    type: str
    unit: str | None = None
    options: tuple[QuestionOption, ...] = ()


class QuestionnaireSectionInfo(CamelModel):
    model_config = ConfigDict(extra="ignore")

    questions: tuple[QuestionInfo, ...]


class QuestionnaireInfo(CamelModel):
    model_config = ConfigDict(extra="ignore")

    sections: tuple[QuestionnaireSectionInfo, ...]

    def question(self, key: str) -> QuestionInfo | None:
        for section in self.sections:
            for question in section.questions:
                if question.key == key:
                    return question
        return None


JsonDocument = dict[str, JsonValue] | list[JsonValue]


class TrancheView(CamelModel):
    model_config = ConfigDict(extra="ignore")

    index: int
    ratio: int
    effective_ratio: int


class HedgeProposalView(CamelModel):
    model_config = ConfigDict(extra="ignore")

    currency: Currency
    blocked: bool = False
    tranches: tuple[TrancheView, ...]


class HedgesView(CamelModel):
    model_config = ConfigDict(extra="ignore")

    cap: int
    proposals: tuple[HedgeProposalView, ...] = ()
