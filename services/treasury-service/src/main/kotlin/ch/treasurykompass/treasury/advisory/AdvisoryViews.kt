package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.assessment.AssessmentStatus
import ch.treasurykompass.treasury.assessment.ExposuresView
import ch.treasurykompass.treasury.assessment.HedgeOverviewView
import ch.treasurykompass.treasury.assessment.LiquidityAllocationView
import ch.treasurykompass.treasury.assessment.ProductCode
import ch.treasurykompass.treasury.assessment.RiskProfileView
import ch.treasurykompass.treasury.assessment.SegmentResultView
import ch.treasurykompass.treasury.assessment.SuitabilityView
import ch.treasurykompass.treasury.marketdata.MarketDataView
import java.time.Instant
import java.util.UUID

enum class ApprovalStatus { NOT_APPROVED, APPROVED }

enum class BlockerType { MISSING_INPUT, UNCONFIRMED_ANSWER, OPEN_PROPOSAL, OPEN_CHECKLIST_ITEM, DOCUMENT_PROCESSING }

/** @property key question key, checklist item ID, proposal ID or document ID, depending on [type] */
data class Blocker(val type: BlockerType, val key: String?, val text: String)

data class ChecklistItemView(
    val id: String,
    val text: String,
    val shortText: String,
    val legalBasis: String,
    val done: Boolean,
    val changedAt: Instant?,
)

data class ApprovalStateView(
    val status: ApprovalStatus,
    val canApprove: Boolean,
    val blockers: List<Blocker>,
    val approvalId: UUID?,
    val approvedAt: Instant?,
    val approvedBy: String?,
    val assessmentVersion: Int?,
)

/** OpenAPI schema `Assessment`: rule result of one version plus checklist state and approval. */
data class AssessmentView(
    val clientId: UUID,
    val version: Int,
    val computedAt: Instant,
    val status: AssessmentStatus,
    val missingInputs: List<String>,
    val marketData: MarketDataView,
    val segment: SegmentResultView?,
    val exposures: ExposuresView?,
    val riskProfile: RiskProfileView?,
    val suitability: List<SuitabilityView>?,
    val hedges: HedgeOverviewView?,
    val liquidity: LiquidityAllocationView?,
    val recommendedProducts: List<ProductCode>,
    val checklist: List<ChecklistItemView>,
    val reasons: List<String>,
    val approval: ApprovalStateView,
)

data class ProtocolRow(val label: String, val value: String)

data class ProtocolSection(
    val title: String,
    val rows: List<ProtocolRow> = emptyList(),
    val items: List<String> = emptyList(),
    val paragraphs: List<String> = emptyList(),
)

enum class ProtocolStatus { DRAFT, APPROVED }

data class ProtocolView(
    val clientId: UUID,
    val status: ProtocolStatus,
    val assessmentVersion: Int,
    val generatedAt: Instant,
    val approvedAt: Instant?,
    val approvedBy: String?,
    val rationaleStale: Boolean,
    val sections: List<ProtocolSection>,
    val text: String,
)

data class ApprovalView(
    val id: UUID,
    val clientId: UUID,
    val assessmentVersion: Int,
    val approvedAt: Instant,
    val approvedBy: String,
    val active: Boolean,
    val revokedAt: Instant?,
    val revokedBy: String?,
    val revocationType: RevocationType?,
    val revocationReason: String?,
    val protocol: ProtocolView,
)

data class HedgeSettingView(
    val currency: String,
    val startRatio: Int?,
    val stepDown: Int,
    val collarEnabled: Boolean?,
    val updatedAt: Instant,
)

data class RationaleView(
    val text: String?,
    val assessmentVersion: Int?,
    val source: RationaleSource?,
    val savedAt: Instant?,
    val savedBy: String?,
    val stale: Boolean,
)
