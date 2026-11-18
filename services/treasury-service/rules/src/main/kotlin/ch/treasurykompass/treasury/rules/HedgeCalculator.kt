package ch.treasurykompass.treasury.rules

import kotlin.math.abs
import kotlin.math.min

internal data class HedgeFacts(
    val fx: List<FxExposure>,
    val profile: RiskProfileCode,
    val fxForward: SuitabilityStatus,
    val zeroCostCollar: SuitabilityStatus,
    val policyMaxHedgeRatio: Int,
    val forgoneGainAttitude: ForgoneGainAttitude,
    val settings: Map<Currency, HedgeSetting>,
    val marketData: MarketData,
)

/** R-HED-01 to R-HED-06 (regelwerk § 5); the scenario (R-HED-07) comes from [ScenarioCalculator]. */
internal object HedgeCalculator {
    const val MINIMUM_POSITION_CHF = 0.5
    private const val MAX_RATIO = 100
    private const val COLLAR_SHARE = 0.3
    private const val TRANCHE_COUNT = 4
    private const val MONTHS_PER_TRANCHE = 3
    private const val INFLOW_COLLAR_FLOOR = 0.985
    private const val INFLOW_COLLAR_CAP = 1.03
    private const val OUTFLOW_COLLAR_FLOOR = 0.97
    private const val OUTFLOW_COLLAR_CAP = 1.015
    private const val BLOCKED_REASON =
        "Devisentermingeschäfte nicht geeignet: Es liegen keine Kenntnisse vor. Vor einer Empfehlung Aufklärung " +
            "durchführen und Kenntnisse im Fragebogen erfassen (Art. 14 FIDLEG)."

    fun propose(facts: HedgeFacts): HedgeOverview {
        // INV-2: never above the policy and never above 100 % of the net position (speculation).
        val cap = min(MAX_RATIO, facts.policyMaxHedgeRatio)
        val proposals = facts.fx
            .filter { abs(it.netPositionChf) >= MINIMUM_POSITION_CHF }
            .map { proposal(it, cap, facts) }
        val naturallyHedged = facts.fx
            .filter { it.netPosition != 0.0 && abs(it.netPositionChf) < MINIMUM_POSITION_CHF }
            .map { NaturallyHedgedPosition(it.currency, it.netPosition, it.netPositionChf) }
        val active = proposals.filterNot { it.blocked }
        return HedgeOverview(
            cap = cap,
            proposals = proposals,
            naturallyHedged = naturallyHedged,
            totals = HedgeTotals(
                hedgedVolumeChf = active.sumOf { it.hedgedAmountChf },
                carryChf = active.sumOf { it.carryChf },
            ),
        )
    }

    fun defaultStartRatio(profile: RiskProfileCode): Int = when (profile) {
        RiskProfileCode.SAFETY -> 90
        RiskProfileCode.CONSERVATIVE -> 80
        RiskProfileCode.BALANCED -> 70
        RiskProfileCode.GROWTH -> 60
        RiskProfileCode.DYNAMIC -> 50
    }

    fun actionLabel(currency: Currency, action: HedgeAction): String = when (action) {
        HedgeAction.SELL_FORWARD -> "$currency auf Termin verkaufen"
        HedgeAction.BUY_FORWARD -> "$currency auf Termin kaufen"
    }

    fun decisions(fx: List<FxExposure>, overview: HedgeOverview): List<RuleDecision> = buildList {
        add(capDecision(overview.cap))
        fx.forEach { exposure ->
            add(minimumPositionDecision(exposure))
            overview.proposals.firstOrNull { it.currency == exposure.currency }?.let { proposal ->
                if (proposal.limitedByPolicy) add(limitDecision(proposal, overview.cap))
                add(proposalDecision(proposal))
                add(collarDecision(proposal))
            }
        }
    }

    private fun proposal(exposure: FxExposure, cap: Int, facts: HedgeFacts): HedgeProposal {
        val setting = facts.settings[exposure.currency] ?: HedgeSetting.DEFAULT
        val defaultStart = defaultStartRatio(facts.profile)
        val wanted = setting.startRatio ?: defaultStart
        val start = min(wanted, cap)
        val blocked = facts.fxForward == SuitabilityStatus.NOT_SUITABLE
        val collarAllowed = !blocked && facts.zeroCostCollar != SuitabilityStatus.NOT_SUITABLE
        val collarDefault = facts.zeroCostCollar == SuitabilityStatus.SUITABLE &&
            facts.forgoneGainAttitude != ForgoneGainAttitude.ACCEPTABLE
        val collarEnabled = collarAllowed && (setting.collarEnabled ?: collarDefault)
        val collarShare = if (collarEnabled) COLLAR_SHARE else 0.0
        val inflow = exposure.netPosition > 0
        val action = if (inflow) HedgeAction.SELL_FORWARD else HedgeAction.BUY_FORWARD

        val plan = StaggeringPlan(start, setting.stepDown, cap, blocked, collarShare)
        val tranches = (1..TRANCHE_COUNT).map { index -> tranche(index, exposure, plan, facts.marketData) }
        val hedgedAmountFx = tranches.sumOf { abs(it.hedgedAmountFx) }

        return HedgeProposal(
            currency = exposure.currency,
            direction = exposure.direction,
            hedgeAction = action,
            actionLabel = actionLabel(exposure.currency, action),
            netPosition = exposure.netPosition,
            netPositionChf = exposure.netPositionChf,
            spotRate = exposure.spotRate,
            sensitivityChf = exposure.sensitivityChf,
            sensitivityPctEbit = exposure.sensitivityPctEbit,
            defaultStartRatio = defaultStart,
            requestedStartRatio = setting.startRatio,
            startRatio = start,
            limitedByPolicy = wanted > cap,
            stepDown = setting.stepDown,
            blocked = blocked,
            blockedReason = if (blocked) BLOCKED_REASON else null,
            collarAllowed = collarAllowed,
            collarEnabled = collarEnabled,
            collarShare = collarShare,
            tranches = tranches,
            averageRatio = average(tranches.map { it.effectiveRatio }),
            hedgedAmountFx = hedgedAmountFx,
            hedgedAmountChf = hedgedAmountFx * exposure.spotRate,
            carryChf = tranches.sumOf { it.carryChf },
            scenario = ScenarioCalculator.curve(exposure.spotRate, exposure.netPosition, tranches, collarShare),
        )
    }

    private fun tranche(index: Int, exposure: FxExposure, plan: StaggeringPlan, marketData: MarketData): Tranche {
        val spot = exposure.spotRate
        val inflow = exposure.netPosition > 0
        val tenor = index / TRANCHE_COUNT.toDouble()
        // Covered interest parity; operation order as in the prototype for bit-identical results.
        val forward = spot * (1 + marketData.chfInterestRate * tenor) / (1 + marketData.interestRate(exposure.currency) * tenor)
        val ratio = clamp(plan.startRatio - (index - 1) * plan.stepDown, 0, plan.cap)
        val effectiveRatio = if (plan.blocked) 0 else ratio
        val amount = exposure.netPosition / TRANCHE_COUNT
        val hedged = amount * effectiveRatio / 100
        return Tranche(
            index = index,
            maturityMonths = index * MONTHS_PER_TRANCHE,
            tenorYears = tenor,
            forwardRate = forward,
            ratio = ratio,
            effectiveRatio = effectiveRatio,
            amountFx = amount,
            hedgedAmountFx = hedged,
            collarFloor = forward * if (inflow) INFLOW_COLLAR_FLOOR else OUTFLOW_COLLAR_FLOOR,
            collarCap = forward * if (inflow) INFLOW_COLLAR_CAP else OUTFLOW_COLLAR_CAP,
            carryChf = hedged * (1 - plan.collarShare) * (forward - spot),
        )
    }

    private fun capDecision(cap: Int) = RuleDecision(
        key = "hedge.cap",
        ruleId = RuleId.HED_02,
        legalBasis = RuleId.HED_02.legalBasis,
        value = cap.toString(),
        message = "Obergrenze der Absicherungsquote $cap % (Treasury-Policy, höchstens 100 % der Nettoposition)",
    )

    private fun minimumPositionDecision(exposure: FxExposure): RuleDecision {
        val (value, outcome) = when {
            exposure.netPosition == 0.0 -> "NO_POSITION" to "keine Nettoposition"
            abs(exposure.netPositionChf) >= MINIMUM_POSITION_CHF -> "HEDGE" to "Nettoposition ab CHF 0.5 Mio., Absicherung wird vorgeschlagen"
            else -> "NATURALLY_HEDGED" to "Nettoposition unter CHF 0.5 Mio., natürlich abgesichert"
        }
        return RuleDecision(
            key = "hedge.${exposure.currency}.minimumPosition",
            ruleId = RuleId.HED_01,
            legalBasis = RuleId.HED_01.legalBasis,
            value = value,
            message = "${exposure.currency}: $outcome",
        )
    }

    private fun limitDecision(proposal: HedgeProposal, cap: Int) = RuleDecision(
        key = "hedge.${proposal.currency}.limitedByPolicy",
        ruleId = RuleId.HED_02,
        legalBasis = RuleId.HED_02.legalBasis,
        value = cap.toString(),
        message = "Absicherungsquote ${proposal.currency} auf $cap % begrenzt (Treasury-Policy)",
    )

    private fun proposalDecision(proposal: HedgeProposal): RuleDecision =
        if (proposal.blocked) {
            RuleDecision(
                key = "hedge.${proposal.currency}.proposal",
                ruleId = RuleId.HED_05,
                legalBasis = RuleId.HED_05.legalBasis,
                value = "BLOCKED",
                message = "${proposal.actionLabel}: keine Empfehlung, Eignung fehlt",
            )
        } else {
            RuleDecision(
                key = "hedge.${proposal.currency}.proposal",
                ruleId = RuleId.HED_03,
                legalBasis = RuleId.HED_03.legalBasis,
                value = proposal.tranches.joinToString("/") { it.effectiveRatio.toString() },
                message = "${proposal.actionLabel}: gestaffelt ${ReasonWriter.staggering(proposal)}",
            )
        }

    private fun collarDecision(proposal: HedgeProposal): RuleDecision {
        val (value, outcome) = when {
            proposal.collarEnabled -> "ENABLED" to "70 % Devisentermingeschäfte und 30 % Zero-Cost-Collar"
            proposal.collarAllowed -> "DISABLED" to "nur Devisentermingeschäfte, Zero-Cost-Collar möglich"
            proposal.blocked -> "NOT_ALLOWED" to "Zero-Cost-Collar nicht erlaubt, Devisentermingeschäfte gesperrt"
            else -> "NOT_ALLOWED" to "Zero-Cost-Collar nicht erlaubt, keine Kenntnisse"
        }
        return RuleDecision(
            key = "hedge.${proposal.currency}.collar",
            ruleId = RuleId.HED_06,
            legalBasis = RuleId.HED_06.legalBasis,
            value = value,
            message = "${proposal.currency}: $outcome",
        )
    }

    private data class StaggeringPlan(
        val startRatio: Int,
        val stepDown: Int,
        val cap: Int,
        val blocked: Boolean,
        val collarShare: Double,
    )
}
