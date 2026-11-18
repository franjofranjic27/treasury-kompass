package ch.treasurykompass.treasury.assessment

import ch.treasurykompass.treasury.marketdata.MarketDataView

/*
 * API representation of the rule result (OpenAPI schema `Assessment`, sections `status` to `reasons`).
 * It is stored as JSON in `assessment_snapshot.result`; checklist state and approval are mixed in on read.
 */

data class AssessmentBody(
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
    val checklist: List<ChecklistItemDefinition>,
    val reasons: List<String>,
) {
    fun suitabilityOf(product: ProductCode): SuitabilityView? = suitability?.firstOrNull { it.product == product }
}

enum class AssessmentStatus { COMPLETE, INCOMPLETE }

enum class ClientSegment { RETAIL, PROFESSIONAL, INSTITUTIONAL }

enum class RiskProfileCode { SAFETY, CONSERVATIVE, BALANCED, GROWTH, DYNAMIC }

enum class ProductCode { FX_FORWARD, ZERO_COST_COLLAR, MONEY_MARKET_FUND, SHORT_TERM_BOND, DEPOSIT }

enum class SuitabilityStatus { SUITABLE, SUITABLE_AFTER_DISCLOSURE, NOT_SUITABLE, NOT_APPLICABLE }

enum class RiskLevel { LOW, MEDIUM, HIGH }

enum class ExposureDirection { NET_INFLOW, NET_OUTFLOW, NONE }

enum class HedgeAction { SELL_FORWARD, BUY_FORWARD }

enum class Currency { EUR, USD }

data class SegmentResultView(
    val result: ClientSegment,
    val label: String,
    val largeCompany: Boolean,
    val thresholdsExceeded: Int,
    val thresholds: List<ThresholdView>,
    val professionalTreasury: Boolean,
    val professionalBase: Boolean,
    val optingIn: Boolean,
    val reason: String,
    val legalBasis: String,
    val decisionSteps: List<DecisionStepView>,
    val consequences: List<ConsequenceView>,
)

data class ThresholdView(val metric: String, val label: String, val value: Double, val limit: Double, val exceeded: Boolean)

data class DecisionStepView(val question: String, val answer: String, val reason: String, val legalBasis: String)

data class ConsequenceView(val text: String, val legalBasis: String)

data class ExposuresView(
    val fx: List<FxExposureView>,
    val fxSensitivityPctEbit: Double,
    val interest: InterestExposureView,
    val liquidity: LiquidityPositionView,
    val stress: List<StressItemView>,
)

data class FxExposureView(
    val currency: Currency,
    val inflow: Double,
    val outflow: Double,
    val netPosition: Double,
    val spotRate: Double,
    val netPositionChf: Double,
    val sensitivityChf: Double,
    val sensitivityPctEbit: Double,
    val direction: ExposureDirection,
    val riskLevel: RiskLevel,
)

data class InterestExposureView(
    val floatingRateDebt: Double,
    val sensitivityChf: Double,
    val sensitivityPctEbit: Double,
    val riskLevel: RiskLevel,
)

data class LiquidityPositionView(
    val cash: Double,
    val minimumLiquidity: Double,
    val suggestedMinimumLiquidity: Double,
    val monthlyOperatingExpenses: Double,
    val excessLiquidity: Double,
)

data class StressItemView(val label: String, val effect: String, val ebitImpactChf: Double, val ebitImpactPct: Double)

data class RiskProfileView(
    val capacity: CapacityView,
    val willingness: WillingnessView,
    val score: Int,
    val code: RiskProfileCode,
    val label: String,
    val legalBasis: String,
    val note: String?,
)

data class CapacityView(val score: Int, val average: Double, val factors: List<CapacityFactorView>)

data class CapacityFactorView(val factor: String, val label: String, val value: Double, val displayValue: String, val score: Int)

data class WillingnessView(val score: Int, val average: Double, val factors: List<WillingnessFactorView>)

data class WillingnessFactorView(val factor: String, val label: String, val answer: String, val answerLabel: String, val score: Int)

data class SuitabilityView(
    val product: ProductCode,
    val productLabel: String,
    val use: String,
    val status: SuitabilityStatus,
    val label: String,
    val note: String,
    val knowledgeLevel: String?,
    val knowledgePresumed: Boolean,
    val kidRequired: Boolean,
    val legalBasis: String?,
)

data class HedgeOverviewView(
    val cap: Int,
    val proposals: List<HedgeProposalView>,
    val naturallyHedged: List<NaturallyHedgedView>,
    val totals: HedgeTotalsView,
)

data class NaturallyHedgedView(val currency: Currency, val netPosition: Double, val netPositionChf: Double)

data class HedgeTotalsView(val hedgedVolumeChf: Double, val carryChf: Double)

data class HedgeProposalView(
    val currency: Currency,
    val direction: ExposureDirection,
    val hedgeAction: HedgeAction,
    val actionLabel: String,
    val netPosition: Double,
    val netPositionChf: Double,
    val spotRate: Double,
    val sensitivityChf: Double,
    val sensitivityPctEbit: Double,
    val defaultStartRatio: Int,
    val requestedStartRatio: Int?,
    val startRatio: Int,
    val limitedByPolicy: Boolean,
    val stepDown: Int,
    val blocked: Boolean,
    val blockedReason: String?,
    val collarAllowed: Boolean,
    val collarEnabled: Boolean,
    val collarShare: Double,
    val tranches: List<TrancheView>,
    val averageRatio: Double,
    val hedgedAmountFx: Double,
    val hedgedAmountChf: Double,
    val carryChf: Double,
    val scenario: List<ScenarioPointView>,
)

data class TrancheView(
    val index: Int,
    val maturityMonths: Int,
    val tenorYears: Double,
    val forwardRate: Double,
    val ratio: Int,
    val effectiveRatio: Int,
    val amountFx: Double,
    val hedgedAmountFx: Double,
    val collarFloor: Double,
    val collarCap: Double,
    val carryChf: Double,
)

data class ScenarioPointView(val shiftPct: Int, val spotRate: Double, val unhedgedChf: Double, val hedgedChf: Double)

data class LiquidityAllocationView(
    val excessLiquidity: Double,
    val horizon: String,
    val profileCode: RiskProfileCode,
    val noExcess: Boolean,
    val parts: List<LiquidityPartView>,
    val shiftedProducts: List<ProductCode>,
    val note: String?,
)

data class LiquidityPartView(val product: ProductCode, val sharePct: Int, val amountChf: Double, val suitabilityStatus: SuitabilityStatus)

/** Checklist item as produced by the rules (R-CHK-01), without workflow state. */
data class ChecklistItemDefinition(val id: String, val text: String, val shortText: String, val legalBasis: String)
