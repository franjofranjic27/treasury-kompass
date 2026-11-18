package ch.treasurykompass.treasury.rules

import kotlin.math.abs

/** R-EXP-01 (regelwerk § 2): net positions, sensitivities, risk levels and the stress table. */
internal object ExposureCalculator {
    private const val FX_SHOCK = 0.1
    private const val INTEREST_SHOCK = 0.01
    private const val HIGH_RISK_PCT_EBIT = 0.3
    private const val MEDIUM_RISK_PCT_EBIT = 0.1

    /** Without a positive EBIT the share of EBIT is undefined; the prototype then reports 0. */
    fun fxExposure(currency: Currency, inflow: Double, outflow: Double, spotRate: Double, ebit: Double): FxExposure {
        val net = round2(inflow - outflow)
        val netChf = net * spotRate
        val sensitivity = abs(netChf) * FX_SHOCK
        val sensitivityPctEbit = if (ebit > 0) sensitivity / ebit else 0.0
        return FxExposure(
            currency = currency,
            inflow = inflow,
            outflow = outflow,
            netPosition = net,
            spotRate = spotRate,
            netPositionChf = netChf,
            sensitivityChf = sensitivity,
            sensitivityPctEbit = sensitivityPctEbit,
            direction = directionOf(net),
            riskLevel = riskLevelOf(sensitivityPctEbit),
        )
    }

    /** Σ sensitivities / EBIT; with EBIT ≤ 0 the whole currency risk counts as uncovered (1 = 100 %). */
    fun fxSensitivityPctEbit(fx: List<FxExposure>, ebit: Double): Double {
        val total = fx.sumOf { it.sensitivityChf }
        return if (ebit > 0) total / ebit else 1.0
    }

    fun interestExposure(floatingRateDebt: Double, ebit: Double): InterestExposure {
        val sensitivity = floatingRateDebt * INTEREST_SHOCK
        val pctEbit = if (ebit > 0) sensitivity / ebit else 0.0
        return InterestExposure(
            floatingRateDebt = floatingRateDebt,
            sensitivityChf = sensitivity,
            sensitivityPctEbit = pctEbit,
            riskLevel = riskLevelOf(pctEbit),
        )
    }

    fun exposures(fx: List<FxExposure>, ebit: Double, interest: InterestExposure, liquidity: LiquidityPosition): Exposures =
        Exposures(
            fx = fx,
            fxSensitivityPctEbit = fxSensitivityPctEbit(fx, ebit),
            interest = interest,
            liquidity = liquidity,
            stress = stressScenarios(fx, interest),
        )

    fun decisions(exposures: Exposures): List<RuleDecision> =
        exposures.fx.map { fx ->
            val outcome = if (fx.direction == ExposureDirection.NONE) "Keine Nettoposition" else fx.riskLevel.label
            RuleDecision(
                key = "exposure.${fx.currency}",
                ruleId = RuleId.EXP_01,
                legalBasis = RuleId.EXP_01.legalBasis,
                value = if (fx.direction == ExposureDirection.NONE) ExposureDirection.NONE.name else fx.riskLevel.name,
                message = "Währungsrisiko ${fx.currency}: $outcome",
            )
        } + RuleDecision(
            key = "exposure.interest",
            ruleId = RuleId.EXP_01,
            legalBasis = RuleId.EXP_01.legalBasis,
            value = exposures.interest.riskLevel.name,
            message = "Zinsrisiko: ${exposures.interest.riskLevel.label}",
        )

    private fun stressScenarios(fx: List<FxExposure>, interest: InterestExposure): List<StressScenario> =
        fx.filter { it.netPosition != 0.0 }.map { exposure ->
            val inflow = exposure.direction == ExposureDirection.NET_INFLOW
            StressScenario(
                label = "${exposure.currency} ${if (inflow) "−" else "+"}10 %",
                effect = if (inflow) "Zuflüsse verlieren an Wert" else "Einkauf wird teurer",
                ebitImpactChf = exposure.sensitivityChf,
                ebitImpactPct = exposure.sensitivityPctEbit,
            )
        } + StressScenario(
            label = "Zins +1 Prozentpunkt",
            effect = "Bankkredit SARON-basiert",
            ebitImpactChf = interest.sensitivityChf,
            ebitImpactPct = interest.sensitivityPctEbit,
        )

    private fun directionOf(net: Double): ExposureDirection = when {
        net > 0 -> ExposureDirection.NET_INFLOW
        net < 0 -> ExposureDirection.NET_OUTFLOW
        else -> ExposureDirection.NONE
    }

    private fun riskLevelOf(pctEbit: Double): RiskLevel = when {
        pctEbit >= HIGH_RISK_PCT_EBIT -> RiskLevel.HIGH
        pctEbit >= MEDIUM_RISK_PCT_EBIT -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }
}
