package ch.treasurykompass.treasury.rules

internal data class CapacityFacts(
    val equity: Double,
    val totalAssets: Double,
    val excessLiquidity: Double,
    val operatingExpenses: Double,
    val ebit: Double,
    val revenue: Double,
    val fxSensitivityPctEbit: Double,
)

/** R-PRO-01 to R-PRO-03 (regelwerk § 3, Art. 12 FIDLEG). */
internal object RiskProfileRule {
    private const val LEGAL_BASIS = "Art. 12 FIDLEG"
    private const val MIN_SCORE = 1
    private const val MAX_SCORE = 5
    private const val PROFESSIONAL_NOTE =
        "Bei professionellen Kunden wird die finanzielle Tragbarkeit vermutet. Die Analyse dient hier der Strukturierung des Vorschlags."

    private val EQUITY_RATIO_CUTS = listOf(0.2, 0.3, 0.4, 0.5)
    private val EXCESS_MONTHS_CUTS = listOf(1.0, 2.0, 4.0, 6.0)
    private val EBIT_MARGIN_CUTS = listOf(0.03, 0.06, 0.09, 0.12)
    private val UNHEDGED_FX_CUTS = listOf(0.05, 0.15, 0.3, 0.5)

    fun capacity(facts: CapacityFacts): RiskCapacity {
        // Division by zero as in the prototype: the affected ratio becomes 0.
        val equityRatio = if (facts.totalAssets > 0) facts.equity / facts.totalAssets else 0.0
        val monthly = LiquidityAllocator.monthlyOperatingExpenses(facts.operatingExpenses)
        val excessMonths = if (monthly > 0) facts.excessLiquidity / monthly else 0.0
        val margin = if (facts.revenue > 0) facts.ebit / facts.revenue else 0.0
        val fxRisk = facts.fxSensitivityPctEbit

        val factors = listOf(
            capacityFactor(CapacityFactor.EQUITY_RATIO, equityRatio, SwissNumberFormat.percent(equityRatio), band(equityRatio, EQUITY_RATIO_CUTS)),
            capacityFactor(
                CapacityFactor.EXCESS_LIQUIDITY_MONTHS, excessMonths,
                "${SwissNumberFormat.number(excessMonths)} Monate Betriebsaufwand", band(excessMonths, EXCESS_MONTHS_CUTS),
            ),
            capacityFactor(CapacityFactor.EBIT_MARGIN, margin, SwissNumberFormat.percent(margin, 1), band(margin, EBIT_MARGIN_CUTS)),
            // Inverted scale: the more unhedged currency risk, the lower the capacity.
            capacityFactor(
                CapacityFactor.UNHEDGED_FX_RISK, fxRisk,
                "${SwissNumberFormat.percent(fxRisk)} des EBIT bei 10 % Kursbewegung", MAX_SCORE + 1 - band(fxRisk, UNHEDGED_FX_CUTS),
            ),
        )
        val average = average(factors.map { it.score })
        return RiskCapacity(score = scoreOf(average), average = average, factors = factors)
    }

    fun willingness(
        hedgeObjective: HedgeObjective,
        tolerableDrawdown: TolerableDrawdown,
        forgoneGainAttitude: ForgoneGainAttitude,
    ): RiskWillingness {
        val factors = listOf(
            willingnessFactor(WillingnessFactor.HEDGE_OBJECTIVE, hedgeObjective.name, hedgeObjective.label, scoreOf(hedgeObjective)),
            willingnessFactor(WillingnessFactor.TOLERABLE_DRAWDOWN, tolerableDrawdown.name, tolerableDrawdown.label, scoreOf(tolerableDrawdown)),
            willingnessFactor(WillingnessFactor.FORGONE_GAIN, forgoneGainAttitude.name, forgoneGainAttitude.label, scoreOf(forgoneGainAttitude)),
        )
        val average = average(factors.map { it.score })
        return RiskWillingness(score = scoreOf(average), average = average, factors = factors)
    }

    /** @param segment `null` if the segment is not yet known; then no professional note is given */
    fun profile(capacity: RiskCapacity, willingness: RiskWillingness, segment: ClientSegment?): RiskProfile {
        val code = RiskProfileCode.ofScore(minOf(capacity.score, willingness.score))
        return RiskProfile(
            capacity = capacity,
            willingness = willingness,
            score = code.score,
            code = code,
            label = code.label,
            legalBasis = LEGAL_BASIS,
            note = if (segment == ClientSegment.PROFESSIONAL) PROFESSIONAL_NOTE else null,
        )
    }

    fun decisions(profile: RiskProfile): List<RuleDecision> = listOf(
        RuleDecision(
            key = "riskCapacity",
            ruleId = RuleId.PRO_01,
            legalBasis = LEGAL_BASIS,
            value = profile.capacity.score.toString(),
            message = "Risikofähigkeit ${profile.capacity.score}/5: " +
                profile.capacity.factors.joinToString(", ") { "${it.label} ${it.score}" },
        ),
        RuleDecision(
            key = "riskWillingness",
            ruleId = RuleId.PRO_02,
            legalBasis = LEGAL_BASIS,
            value = profile.willingness.score.toString(),
            message = "Risikobereitschaft ${profile.willingness.score}/5: " +
                profile.willingness.factors.joinToString(", ") { "${it.label} ${it.score}" },
        ),
        RuleDecision(
            key = "riskProfile",
            ruleId = RuleId.PRO_03,
            legalBasis = LEGAL_BASIS,
            value = profile.code.name,
            message = "Risikoprofil ${profile.label}: Risikofähigkeit ${profile.capacity.score}/5, " +
                "Risikobereitschaft ${profile.willingness.score}/5",
        ),
    )

    /** Half-up, so that an average of exactly 2.5 becomes 3 (both demo clients depend on it). */
    private fun scoreOf(average: Double): Int = clamp(roundHalfUp(average), MIN_SCORE, MAX_SCORE)

    private fun scoreOf(objective: HedgeObjective): Int = when (objective) {
        HedgeObjective.BUDGET_CERTAINTY -> 1
        HedgeObjective.PROTECT_MARGINS -> 3
        HedgeObjective.LOW_HEDGING_COST -> 4
    }

    private fun scoreOf(drawdown: TolerableDrawdown): Int = when (drawdown) {
        TolerableDrawdown.NONE -> 1
        TolerableDrawdown.UP_TO_2_PERCENT -> 2
        TolerableDrawdown.UP_TO_5_PERCENT -> 3
        TolerableDrawdown.OVER_5_PERCENT -> 5
    }

    private fun scoreOf(attitude: ForgoneGainAttitude): Int = when (attitude) {
        ForgoneGainAttitude.ACCEPTABLE -> 1
        ForgoneGainAttitude.TOLERABLE -> 3
        ForgoneGainAttitude.NOT_ACCEPTABLE -> 4
    }

    private fun capacityFactor(factor: CapacityFactor, value: Double, displayValue: String, score: Int) =
        CapacityFactorScore(factor = factor, label = factor.label, value = value, displayValue = displayValue, score = score)

    private fun willingnessFactor(factor: WillingnessFactor, answer: String, answerLabel: String, score: Int) =
        WillingnessFactorScore(factor = factor, label = factor.label, answer = answer, answerLabel = answerLabel, score = score)
}
