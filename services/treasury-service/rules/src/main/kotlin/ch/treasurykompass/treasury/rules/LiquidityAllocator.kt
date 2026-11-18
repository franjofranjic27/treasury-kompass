package ch.treasurykompass.treasury.rules

import kotlin.math.max

internal data class LiquidityFacts(
    val excessLiquidity: Double,
    val horizon: InvestmentHorizon,
    val profile: RiskProfileCode,
    val moneyMarketFund: SuitabilityStatus,
    val shortTermBond: SuitabilityStatus,
)

/** R-LIQ-01 to R-LIQ-03 (regelwerk § 3 Mindestliquidität and § 6). */
internal object LiquidityAllocator {
    private const val MONTHS_PER_YEAR = 12
    private const val MINIMUM_LIQUIDITY_MONTHS = 1.5
    private const val NO_EXCESS_NOTE =
        "Keine Überschussliquidität: Die flüssigen Mittel liegen nicht über der Mindestliquidität. Es wird keine Anlage empfohlen."

    /** Shares in percent: deposit · money market fund · short-term bonds, by horizon and profile score. */
    private val ALLOCATION: Map<InvestmentHorizon, Map<RiskProfileCode, Split>> = mapOf(
        InvestmentHorizon.UNDER_1_YEAR to byProfile(Split(70, 30, 0), Split(45, 40, 15), Split(35, 40, 25), Split(30, 40, 30)),
        InvestmentHorizon.ONE_TO_THREE_YEARS to byProfile(Split(50, 30, 20), Split(30, 30, 40), Split(20, 25, 55), Split(15, 25, 60)),
        InvestmentHorizon.OVER_THREE_YEARS to byProfile(Split(40, 30, 30), Split(20, 25, 55), Split(10, 20, 70), Split(10, 20, 70)),
    )

    fun monthlyOperatingExpenses(operatingExpenses: Double): Double = operatingExpenses / MONTHS_PER_YEAR

    /** Guide value 1.5 months of operating expenses, computed in exactly this order (architecture.md section 7). */
    fun suggestedMinimumLiquidity(operatingExpenses: Double): Double =
        round1(operatingExpenses / MONTHS_PER_YEAR * MINIMUM_LIQUIDITY_MONTHS)

    fun excessLiquidity(cash: Double, minimumLiquidity: Double): Double = max(0.0, round2(cash - minimumLiquidity))

    fun allocate(facts: LiquidityFacts): LiquidityAllocation {
        val base = ALLOCATION.getValue(facts.horizon).getValue(facts.profile)
        var deposit = base.deposit
        var moneyMarketFund = base.moneyMarketFund
        var shortTermBond = base.shortTermBond
        val shifted = mutableListOf<Product>()
        // R-LIQ-03, order as in the prototype: bonds first (to the fund if suitable), then the fund.
        if (facts.shortTermBond == SuitabilityStatus.NOT_SUITABLE && shortTermBond > 0) {
            if (facts.moneyMarketFund != SuitabilityStatus.NOT_SUITABLE) moneyMarketFund += shortTermBond else deposit += shortTermBond
            shifted += Product.SHORT_TERM_BOND
            shortTermBond = 0
        }
        if (facts.moneyMarketFund == SuitabilityStatus.NOT_SUITABLE && moneyMarketFund > 0) {
            deposit += moneyMarketFund
            shifted += Product.MONEY_MARKET_FUND
            moneyMarketFund = 0
        }

        val noExcess = facts.excessLiquidity <= 0
        val statusOf = mapOf(
            Product.DEPOSIT to SuitabilityStatus.NOT_APPLICABLE,
            Product.MONEY_MARKET_FUND to facts.moneyMarketFund,
            Product.SHORT_TERM_BOND to facts.shortTermBond,
        )
        val parts = if (noExcess) {
            emptyList()
        } else {
            listOf(Product.DEPOSIT to deposit, Product.MONEY_MARKET_FUND to moneyMarketFund, Product.SHORT_TERM_BOND to shortTermBond)
                .map { (product, share) ->
                    LiquidityPart(product, share, facts.excessLiquidity * share / 100, statusOf.getValue(product))
                }
        }
        val effectiveShifts = if (noExcess) emptyList() else shifted.toList()
        return LiquidityAllocation(
            excessLiquidity = facts.excessLiquidity,
            horizon = facts.horizon,
            profileCode = facts.profile,
            noExcess = noExcess,
            parts = parts,
            shiftedProducts = effectiveShifts,
            note = when {
                noExcess -> NO_EXCESS_NOTE
                effectiveShifts.isNotEmpty() ->
                    "${effectiveShifts.joinToString(" und ") { it.label }} umgeschichtet, weil Kenntnisse fehlen (Art. 14 FIDLEG)."
                else -> null
            },
        )
    }

    fun decisions(allocation: LiquidityAllocation): List<RuleDecision> = buildList {
        add(
            RuleDecision(
                key = "liquidity.excess",
                ruleId = RuleId.LIQ_01,
                legalBasis = RuleId.LIQ_01.legalBasis,
                value = allocation.excessLiquidity.toString(),
                message = if (allocation.noExcess) {
                    "Keine Überschussliquidität, daher keine Anlageempfehlung"
                } else {
                    "Anlagefähige Überschussliquidität CHF ${SwissNumberFormat.number(allocation.excessLiquidity)} Mio."
                },
            ),
        )
        if (allocation.noExcess) return@buildList
        add(
            RuleDecision(
                key = "liquidity.allocation",
                ruleId = RuleId.LIQ_02,
                legalBasis = RuleId.LIQ_02.legalBasis,
                value = allocation.parts.joinToString("/") { it.sharePct.toString() },
                message = "Aufteilung ${sharesText(allocation)} (Horizont «${allocation.horizon.label}», " +
                    "Profil ${allocation.profileCode.label})",
            ),
        )
        if (allocation.shiftedProducts.isNotEmpty()) {
            add(
                RuleDecision(
                    key = "liquidity.shift",
                    ruleId = RuleId.LIQ_03,
                    legalBasis = RuleId.LIQ_03.legalBasis,
                    value = allocation.shiftedProducts.joinToString(",") { it.name },
                    message = "${allocation.shiftedProducts.joinToString(" und ") { it.label }} umgeschichtet, weil Kenntnisse fehlen",
                ),
            )
        }
    }

    /** «45 % Festgeld / Callgeld, 40 % Geldmarktfonds CHF, …» over the parts with a share (prototype `reasons()`). */
    fun sharesText(allocation: LiquidityAllocation): String =
        allocation.parts.filter { it.sharePct > 0 }.joinToString(", ") { "${it.sharePct} % ${it.product.label}" }

    private fun byProfile(safety: Split, conservative: Split, balanced: Split, growthAndDynamic: Split): Map<RiskProfileCode, Split> = mapOf(
        RiskProfileCode.SAFETY to safety,
        RiskProfileCode.CONSERVATIVE to conservative,
        RiskProfileCode.BALANCED to balanced,
        RiskProfileCode.GROWTH to growthAndDynamic,
        RiskProfileCode.DYNAMIC to growthAndDynamic,
    )

    private data class Split(val deposit: Int, val moneyMarketFund: Int, val shortTermBond: Int)
}
