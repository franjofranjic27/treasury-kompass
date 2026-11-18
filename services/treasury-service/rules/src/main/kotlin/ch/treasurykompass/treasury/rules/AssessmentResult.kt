package ch.treasurykompass.treasury.rules

/**
 * Pure result of the rule engine for one set of inputs. Field names follow the OpenAPI schema `Assessment`;
 * workflow data (version, checklist state, approval) is added by the application.
 *
 * Values are unrounded doubles; rounding happens only for display ([SwissNumberFormat]). Blocks whose inputs
 * are incomplete are `null`, list blocks that depend on the full proposal are empty.
 *
 * @property recommendedProducts products of the proposal (prototype `m.rec`); empty unless hedges and liquidity are computed
 * @property checklist checklist items (R-CHK-01); empty unless hedges and liquidity are computed
 * @property reasons rule texts (prototype `reasons()`), plain text without markup; empty unless hedges and liquidity are computed
 * @property protocolTexts rule-derived lines for the advisory protocol (prototype `protoSections()`)
 * @property suggestedMinimumLiquidity R-LIQ-01 guide value, available as soon as operating expenses are known
 * @property decisions rule decisions for the audit trail (INV-7), each with a stable [RuleDecision.key]
 */
data class AssessmentResult(
    val status: AssessmentStatus,
    val missingInputs: List<InputKey>,
    val segment: SegmentResult?,
    val exposures: Exposures?,
    val riskProfile: RiskProfile?,
    val suitability: List<Suitability>?,
    val hedges: HedgeOverview?,
    val liquidity: LiquidityAllocation?,
    val recommendedProducts: List<Product>,
    val checklist: List<ChecklistItem>,
    val reasons: List<String>,
    val protocolTexts: ProtocolTexts?,
    val suggestedMinimumLiquidity: Double?,
    val decisions: List<RuleDecision>,
) {
    fun suitabilityOf(product: Product): Suitability? = suitability?.firstOrNull { it.product == product }

    fun hedgeProposalOf(currency: Currency): HedgeProposal? = hedges?.proposals?.firstOrNull { it.currency == currency }
}

// ---------- Segment (R-SEG-01) ----------

/**
 * @property professionalBase large company or professional treasury; the opting-in question is visible only then
 * @property optingIn effective opting-in (only with a professional base)
 * @property summary one-line consequence (prototype `viewPruefung`)
 */
data class SegmentResult(
    val result: ClientSegment,
    val label: String,
    val largeCompany: Boolean,
    val thresholdsExceeded: Int,
    val thresholds: List<ThresholdCheck>,
    val professionalTreasury: Boolean,
    val professionalBase: Boolean,
    val optingIn: Boolean,
    val reason: String,
    val summary: String,
    val legalBasis: String,
    val decisionSteps: List<SegmentDecisionStep>,
    val consequences: List<SegmentConsequence>,
)

/** [exceeded] is `value > limit` (strict). */
data class ThresholdCheck(
    val metric: ThresholdMetric,
    val label: String,
    val value: Double,
    val limit: Double,
    val exceeded: Boolean,
)

data class SegmentDecisionStep(
    val question: String,
    val answer: String,
    val reason: String,
    val legalBasis: String,
)

data class SegmentConsequence(
    val text: String,
    val legalBasis: String,
)

// ---------- Exposures (R-EXP-01, R-LIQ-01) ----------

/** @property fxSensitivityPctEbit Σ sensitivities / EBIT, `1` if EBIT ≤ 0 */
data class Exposures(
    val fx: List<FxExposure>,
    val fxSensitivityPctEbit: Double,
    val interest: InterestExposure,
    val liquidity: LiquidityPosition,
    val stress: List<StressScenario>,
)

/** @property sensitivityChf `|netPositionChf| × 10 %`, i.e. the EBIT burden of an adverse 10 % move */
data class FxExposure(
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

/** @property sensitivityChf additional interest expense per year at +1 percentage point */
data class InterestExposure(
    val floatingRateDebt: Double,
    val sensitivityChf: Double,
    val sensitivityPctEbit: Double,
    val riskLevel: RiskLevel,
)

data class LiquidityPosition(
    val cash: Double,
    val minimumLiquidity: Double,
    val suggestedMinimumLiquidity: Double,
    val monthlyOperatingExpenses: Double,
    val excessLiquidity: Double,
)

/** @property ebitImpactChf burden as a positive number */
data class StressScenario(
    val label: String,
    val effect: String,
    val ebitImpactChf: Double,
    val ebitImpactPct: Double,
)

// ---------- Risk profile (R-PRO-01 to R-PRO-03) ----------

/**
 * @property score `min(capacity.score, willingness.score)`
 * @property note hint for professional clients (financial capacity presumed), otherwise `null`
 */
data class RiskProfile(
    val capacity: RiskCapacity,
    val willingness: RiskWillingness,
    val score: Int,
    val code: RiskProfileCode,
    val label: String,
    val legalBasis: String,
    val note: String?,
)

/** @property score half-up rounded [average] */
data class RiskCapacity(
    val score: Int,
    val average: Double,
    val factors: List<CapacityFactorScore>,
)

data class CapacityFactorScore(
    val factor: CapacityFactor,
    val label: String,
    val value: Double,
    val displayValue: String,
    val score: Int,
)

/** @property score half-up rounded [average] */
data class RiskWillingness(
    val score: Int,
    val average: Double,
    val factors: List<WillingnessFactorScore>,
)

/** @property answer enum name of the answer, e.g. `BUDGET_CERTAINTY` */
data class WillingnessFactorScore(
    val factor: WillingnessFactor,
    val label: String,
    val answer: String,
    val answerLabel: String,
    val score: Int,
)

// ---------- Suitability (R-SUI-01, R-SUI-02) ----------

/**
 * @property knowledgeLevel the client's answer; `null` for deposits and when knowledge is presumed
 * @property knowledgePresumed professional client, knowledge and experience presumed
 * @property kidRequired key information document required (Art. 58 FIDLEG)
 * @property legalBasis legal basis of the individual result as in the prototype; `null` where the prototype has none
 */
data class Suitability(
    val product: Product,
    val productLabel: String,
    val use: ProductUse,
    val status: SuitabilityStatus,
    val label: String,
    val note: String,
    val knowledgeLevel: KnowledgeLevel?,
    val knowledgePresumed: Boolean,
    val kidRequired: Boolean,
    val legalBasis: String?,
)

// ---------- Hedging (R-HED-01 to R-HED-07) ----------

/**
 * @property cap `min(100, policyMaxHedgeRatio)`
 * @property naturallyHedged positions ≠ 0 below the minimum position of CHF 0.5 Mio.
 */
data class HedgeOverview(
    val cap: Int,
    val proposals: List<HedgeProposal>,
    val naturallyHedged: List<NaturallyHedgedPosition>,
    val totals: HedgeTotals,
)

/** Sums over the proposals that are not blocked (prototype `overviewTiles`). */
data class HedgeTotals(
    val hedgedVolumeChf: Double,
    val carryChf: Double,
)

data class NaturallyHedgedPosition(
    val currency: Currency,
    val netPosition: Double,
    val netPositionChf: Double,
)

/**
 * Staggered hedge proposal for one currency.
 *
 * @property defaultStartRatio start ratio by risk profile (1 → 90, 2 → 80, 3 → 70, 4 → 60, 5 → 50)
 * @property requestedStartRatio start ratio set by the advisor, `null` if the default applies
 * @property startRatio effective start ratio, never above the cap
 * @property limitedByPolicy requested (or default) start ratio was above the cap
 * @property blocked FX forward not suitable (R-HED-05): effective ratios are 0
 * @property collarEnabled effective collar setting
 * @property collarShare `0.3` with collar, else `0`
 * @property averageRatio mean of the effective tranche ratios
 * @property hedgedAmountFx Σ |hedged amount| of the tranches
 * @property carryChf interest differential against spot; negative = cost
 * @property scenario EBIT effect for spot moves from −15 % to +15 % in steps of 1
 */
data class HedgeProposal(
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
    val tranches: List<Tranche>,
    val averageRatio: Double,
    val hedgedAmountFx: Double,
    val hedgedAmountChf: Double,
    val carryChf: Double,
    val scenario: List<ScenarioPoint>,
) {
    fun scenarioAt(shiftPct: Int): ScenarioPoint? = scenario.firstOrNull { it.shiftPct == shiftPct }
}

/**
 * One of four quarterly tranches.
 *
 * @property ratio `clamp(start − (index − 1) × stepDown, 0, cap)`
 * @property effectiveRatio `0` if the proposal is blocked, else [ratio]
 * @property amountFx `netPosition / 4`, signed like the position
 * @property hedgedAmountFx `amountFx × effectiveRatio / 100`, signed like the position
 */
data class Tranche(
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

/** @property spotRate shifted spot `S × (1 + shiftPct / 100)` */
data class ScenarioPoint(
    val shiftPct: Int,
    val spotRate: Double,
    val unhedgedChf: Double,
    val hedgedChf: Double,
)

// ---------- Liquidity (R-LIQ-01 to R-LIQ-03) ----------

/**
 * @property parts order DEPOSIT, MONEY_MARKET_FUND, SHORT_TERM_BOND, including shares of 0; empty if [noExcess]
 * @property shiftedProducts products moved away for lack of suitability (R-LIQ-03); empty if [noExcess]
 * @property note shift hint (R-LIQ-03) or no-excess hint, verbatim from the prototype
 */
data class LiquidityAllocation(
    val excessLiquidity: Double,
    val horizon: InvestmentHorizon,
    val profileCode: RiskProfileCode,
    val noExcess: Boolean,
    val parts: List<LiquidityPart>,
    val shiftedProducts: List<Product>,
    val note: String?,
)

data class LiquidityPart(
    val product: Product,
    val sharePct: Int,
    val amountChf: Double,
    val suitabilityStatus: SuitabilityStatus,
)

// ---------- Checklist, protocol, audit ----------

/** @property id `info`, `riskBrochure`, `costDisclosure`, `kid.<PRODUCT>` or `warning.<PRODUCT>` */
data class ChecklistItem(
    val id: String,
    val text: String,
    val shortText: String,
    val legalBasis: String,
)

/**
 * Rule-derived lines of the advisory protocol (prototype `protoSections()`), with the prototype's
 * fallbacks «Keine Empfehlung» and «Keine» already applied.
 */
data class ProtocolTexts(
    val recommendations: List<String>,
    val notRecommended: List<String>,
)

/**
 * One rule decision for the audit trail.
 *
 * @property key stable identifier of the decided aspect, e.g. `segment`, `suitability.FX_FORWARD`, `hedge.EUR.proposal`;
 *   compare decisions of two versions by key
 * @property value compact machine-readable outcome, e.g. `RETAIL` or `80/70/60/50`, for audit details (old → new)
 * @property message German audit text
 */
data class RuleDecision(
    val key: String,
    val ruleId: RuleId,
    val legalBasis: String?,
    val value: String,
    val message: String,
)
