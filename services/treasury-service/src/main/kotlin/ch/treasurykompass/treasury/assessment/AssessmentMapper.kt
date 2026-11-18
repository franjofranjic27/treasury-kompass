package ch.treasurykompass.treasury.assessment

import ch.treasurykompass.treasury.marketdata.MarketDataView
import ch.treasurykompass.treasury.rules.AssessmentResult
import ch.treasurykompass.treasury.rules.CapacityFactorScore
import ch.treasurykompass.treasury.rules.Exposures
import ch.treasurykompass.treasury.rules.HedgeOverview
import ch.treasurykompass.treasury.rules.HedgeProposal
import ch.treasurykompass.treasury.rules.LiquidityAllocation
import ch.treasurykompass.treasury.rules.Product
import ch.treasurykompass.treasury.rules.RiskProfile
import ch.treasurykompass.treasury.rules.RuleDecision
import ch.treasurykompass.treasury.rules.SegmentResult
import ch.treasurykompass.treasury.rules.Suitability
import ch.treasurykompass.treasury.rules.WillingnessFactorScore
import ch.treasurykompass.treasury.rules.AssessmentStatus as RuleAssessmentStatus
import ch.treasurykompass.treasury.rules.ClientSegment as RuleClientSegment
import ch.treasurykompass.treasury.rules.Currency as RuleCurrency
import ch.treasurykompass.treasury.rules.ExposureDirection as RuleExposureDirection
import ch.treasurykompass.treasury.rules.HedgeAction as RuleHedgeAction
import ch.treasurykompass.treasury.rules.RiskLevel as RuleRiskLevel
import ch.treasurykompass.treasury.rules.RiskProfileCode as RuleRiskProfileCode
import ch.treasurykompass.treasury.rules.SuitabilityStatus as RuleSuitabilityStatus

/** Rule decision as stored with the snapshot, compared by [key] between versions (INV-7). */
data class DecisionRecord(val key: String, val ruleId: String, val legalBasis: String?, val value: String, val message: String)

/** Rule-derived protocol lines (prototype `protoSections()`): recommendations and products advised against. */
data class ProtocolLines(val recommendations: List<String>, val notRecommended: List<String>)

/**
 * Everything persisted in `assessment_snapshot.result`: the API body plus what later steps need without re-running
 * the rules (audit diff, protocol, R-LIQ-01 guide value).
 */
data class SnapshotPayload(
    val body: AssessmentBody,
    val decisions: List<DecisionRecord>,
    val protocolLines: ProtocolLines?,
    val suggestedMinimumLiquidity: Double?,
)

/** Maps the framework-free rule result onto the API representation (architecture 4.1). */
object AssessmentMapper {

    fun toPayload(result: AssessmentResult, marketData: MarketDataView): SnapshotPayload = SnapshotPayload(
        body = AssessmentBody(
            status = status(result.status),
            missingInputs = result.missingInputs.map { it.key },
            marketData = marketData,
            segment = result.segment?.let(::segment),
            exposures = result.exposures?.let(::exposures),
            riskProfile = result.riskProfile?.let(::riskProfile),
            suitability = result.suitability?.map(::suitability),
            hedges = result.hedges?.let(::hedges),
            liquidity = result.liquidity?.let(::liquidity),
            recommendedProducts = result.recommendedProducts.map(::product),
            checklist = result.checklist.map { ChecklistItemDefinition(it.id, it.text, it.shortText, it.legalBasis) },
            reasons = result.reasons,
        ),
        decisions = result.decisions.map(::decision),
        protocolLines = result.protocolTexts?.let { ProtocolLines(it.recommendations, it.notRecommended) },
        suggestedMinimumLiquidity = result.suggestedMinimumLiquidity,
    )

    private fun decision(decision: RuleDecision) =
        DecisionRecord(decision.key, decision.ruleId.code, decision.legalBasis, decision.value, decision.message)

    private fun segment(segment: SegmentResult) = SegmentResultView(
        result = clientSegment(segment.result),
        label = segment.label,
        largeCompany = segment.largeCompany,
        thresholdsExceeded = segment.thresholdsExceeded,
        thresholds = segment.thresholds.map { ThresholdView(it.metric.name, it.label, it.value, it.limit, it.exceeded) },
        professionalTreasury = segment.professionalTreasury,
        professionalBase = segment.professionalBase,
        optingIn = segment.optingIn,
        reason = segment.reason,
        legalBasis = segment.legalBasis,
        decisionSteps = segment.decisionSteps.map { DecisionStepView(it.question, it.answer, it.reason, it.legalBasis) },
        consequences = segment.consequences.map { ConsequenceView(it.text, it.legalBasis) },
    )

    private fun exposures(exposures: Exposures) = ExposuresView(
        fx = exposures.fx.map {
            FxExposureView(
                currency = currency(it.currency),
                inflow = it.inflow,
                outflow = it.outflow,
                netPosition = it.netPosition,
                spotRate = it.spotRate,
                netPositionChf = it.netPositionChf,
                sensitivityChf = it.sensitivityChf,
                sensitivityPctEbit = it.sensitivityPctEbit,
                direction = direction(it.direction),
                riskLevel = riskLevel(it.riskLevel),
            )
        },
        fxSensitivityPctEbit = exposures.fxSensitivityPctEbit,
        interest = exposures.interest.let {
            InterestExposureView(it.floatingRateDebt, it.sensitivityChf, it.sensitivityPctEbit, riskLevel(it.riskLevel))
        },
        liquidity = exposures.liquidity.let {
            LiquidityPositionView(it.cash, it.minimumLiquidity, it.suggestedMinimumLiquidity, it.monthlyOperatingExpenses, it.excessLiquidity)
        },
        stress = exposures.stress.map { StressItemView(it.label, it.effect, it.ebitImpactChf, it.ebitImpactPct) },
    )

    private fun riskProfile(profile: RiskProfile) = RiskProfileView(
        capacity = CapacityView(profile.capacity.score, profile.capacity.average, profile.capacity.factors.map(::capacityFactor)),
        willingness = WillingnessView(
            profile.willingness.score,
            profile.willingness.average,
            profile.willingness.factors.map(::willingnessFactor),
        ),
        score = profile.score,
        code = riskProfileCode(profile.code),
        label = profile.label,
        legalBasis = profile.legalBasis,
        note = profile.note,
    )

    private fun capacityFactor(factor: CapacityFactorScore) =
        CapacityFactorView(factor.factor.name, factor.label, factor.value, factor.displayValue, factor.score)

    private fun willingnessFactor(factor: WillingnessFactorScore) =
        WillingnessFactorView(factor.factor.name, factor.label, factor.answer, factor.answerLabel, factor.score)

    private fun suitability(suitability: Suitability) = SuitabilityView(
        product = product(suitability.product),
        productLabel = suitability.productLabel,
        use = suitability.use.name,
        status = suitabilityStatus(suitability.status),
        label = suitability.label,
        note = suitability.note,
        knowledgeLevel = suitability.knowledgeLevel?.name,
        knowledgePresumed = suitability.knowledgePresumed,
        kidRequired = suitability.kidRequired,
        legalBasis = suitability.legalBasis,
    )

    private fun hedges(overview: HedgeOverview) = HedgeOverviewView(
        cap = overview.cap,
        proposals = overview.proposals.map(::hedgeProposal),
        naturallyHedged = overview.naturallyHedged.map { NaturallyHedgedView(currency(it.currency), it.netPosition, it.netPositionChf) },
        totals = HedgeTotalsView(overview.totals.hedgedVolumeChf, overview.totals.carryChf),
    )

    private fun hedgeProposal(proposal: HedgeProposal) = HedgeProposalView(
        currency = currency(proposal.currency),
        direction = direction(proposal.direction),
        hedgeAction = hedgeAction(proposal.hedgeAction),
        actionLabel = proposal.actionLabel,
        netPosition = proposal.netPosition,
        netPositionChf = proposal.netPositionChf,
        spotRate = proposal.spotRate,
        sensitivityChf = proposal.sensitivityChf,
        sensitivityPctEbit = proposal.sensitivityPctEbit,
        defaultStartRatio = proposal.defaultStartRatio,
        requestedStartRatio = proposal.requestedStartRatio,
        startRatio = proposal.startRatio,
        limitedByPolicy = proposal.limitedByPolicy,
        stepDown = proposal.stepDown,
        blocked = proposal.blocked,
        blockedReason = proposal.blockedReason,
        collarAllowed = proposal.collarAllowed,
        collarEnabled = proposal.collarEnabled,
        collarShare = proposal.collarShare,
        tranches = proposal.tranches.map {
            TrancheView(
                index = it.index,
                maturityMonths = it.maturityMonths,
                tenorYears = it.tenorYears,
                forwardRate = it.forwardRate,
                ratio = it.ratio,
                effectiveRatio = it.effectiveRatio,
                amountFx = it.amountFx,
                hedgedAmountFx = it.hedgedAmountFx,
                collarFloor = it.collarFloor,
                collarCap = it.collarCap,
                carryChf = it.carryChf,
            )
        },
        averageRatio = proposal.averageRatio,
        hedgedAmountFx = proposal.hedgedAmountFx,
        hedgedAmountChf = proposal.hedgedAmountChf,
        carryChf = proposal.carryChf,
        scenario = proposal.scenario.map { ScenarioPointView(it.shiftPct, it.spotRate, it.unhedgedChf, it.hedgedChf) },
    )

    private fun liquidity(allocation: LiquidityAllocation) = LiquidityAllocationView(
        excessLiquidity = allocation.excessLiquidity,
        horizon = allocation.horizon.name,
        profileCode = riskProfileCode(allocation.profileCode),
        noExcess = allocation.noExcess,
        parts = allocation.parts.map { LiquidityPartView(product(it.product), it.sharePct, it.amountChf, suitabilityStatus(it.suitabilityStatus)) },
        shiftedProducts = allocation.shiftedProducts.map(::product),
        note = allocation.note,
    )

    private fun clientSegment(segment: RuleClientSegment) = ClientSegment.valueOf(segment.name)

    private fun riskProfileCode(code: RuleRiskProfileCode) = RiskProfileCode.valueOf(code.name)

    private fun product(product: Product) = ProductCode.valueOf(product.name)

    private fun suitabilityStatus(status: RuleSuitabilityStatus) = SuitabilityStatus.valueOf(status.name)

    private fun currency(currency: RuleCurrency) = Currency.valueOf(currency.name)

    private fun direction(direction: RuleExposureDirection) = ExposureDirection.valueOf(direction.name)

    private fun riskLevel(level: RuleRiskLevel) = RiskLevel.valueOf(level.name)

    private fun hedgeAction(action: RuleHedgeAction) = HedgeAction.valueOf(action.name)

    private fun status(status: RuleAssessmentStatus) = AssessmentStatus.valueOf(status.name)
}
