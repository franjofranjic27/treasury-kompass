package ch.treasurykompass.treasury.rules

/**
 * Questionnaire keys (architecture.md section 6, OpenAPI `QuestionKey`) in catalogue order.
 * `missingInputs` of an [AssessmentResult] reports these keys.
 */
enum class InputKey(val key: String) {
    REVENUE("fin.revenue"),
    TOTAL_ASSETS("fin.totalAssets"),
    EQUITY("fin.equity"),
    EBIT("fin.ebit"),
    CASH("fin.cash"),
    OPERATING_EXPENSES("fin.operatingExpenses"),
    FLOATING_RATE_DEBT("fin.floatingRateDebt"),
    FX_EUR_INFLOW("fx.eur.inflow"),
    FX_EUR_OUTFLOW("fx.eur.outflow"),
    FX_USD_INFLOW("fx.usd.inflow"),
    FX_USD_OUTFLOW("fx.usd.outflow"),
    TREASURY_ORGANISATION("segment.treasuryOrganisation"),
    OPTING_IN("segment.optingIn"),
    HEDGE_OBJECTIVE("goals.hedgeObjective"),
    POLICY_MAX_HEDGE_RATIO("goals.policyMaxHedgeRatio"),
    MINIMUM_LIQUIDITY("goals.minimumLiquidity"),
    INVESTMENT_HORIZON("goals.investmentHorizon"),
    TOLERABLE_DRAWDOWN("risk.tolerableDrawdown"),
    FORGONE_GAIN_ATTITUDE("risk.forgoneGainAttitude"),
    KNOWLEDGE_FX_FORWARD("knowledge.fxForward"),
    KNOWLEDGE_ZERO_COST_COLLAR("knowledge.zeroCostCollar"),
    KNOWLEDGE_MONEY_MARKET_FUND("knowledge.moneyMarketFund"),
    KNOWLEDGE_SHORT_TERM_BOND("knowledge.shortTermBond");

    companion object {
        fun fromKey(key: String): InputKey =
            entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown question key: $key")
    }
}

/**
 * Answers relevant to the rule engine. `null` means "not answered"; the engine then reports the key in
 * `missingInputs` and leaves the dependent blocks out. Amounts in CHF millions (FX flows in millions of
 * the foreign currency), as stored by the questionnaire.
 *
 * Only confirmed and proposed answers of *visible* questions should be passed in; the engine ignores
 * [SegmentInput.optingIn] anyway when the client has no professional base.
 *
 * @property knowledge knowledge and experience per financial instrument; [Product.DEPOSIT] is not allowed
 * @property knowledgeEvidence optional citation label for a knowledge answer taken from the documents
 *   (e.g. «Anhang Ziff. 7, offene Devisentermingeschäfte»); used verbatim in the reasons for [Product.FX_FORWARD]
 */
data class AssessmentInput(
    val financials: FinancialsInput = FinancialsInput(),
    val fxFlows: Map<Currency, FxFlowInput> = emptyMap(),
    val segment: SegmentInput = SegmentInput(),
    val goals: GoalsInput = GoalsInput(),
    val riskTolerance: RiskToleranceInput = RiskToleranceInput(),
    val knowledge: Map<Product, KnowledgeLevel> = emptyMap(),
    val knowledgeEvidence: Map<Product, String> = emptyMap(),
) {
    init {
        require(knowledge.keys.all { it.financialInstrument }) { "Knowledge is only assessed for financial instruments" }
        require(knowledgeEvidence.keys.all { it.financialInstrument }) { "Knowledge evidence is only kept for financial instruments" }
    }

    fun fxFlow(currency: Currency): FxFlowInput = fxFlows[currency] ?: FxFlowInput()
}

/** Section `FINANCIALS`. Equity and EBIT may be negative, all other amounts are ≥ 0. */
data class FinancialsInput(
    val revenue: Double? = null,
    val totalAssets: Double? = null,
    val equity: Double? = null,
    val ebit: Double? = null,
    val cash: Double? = null,
    val operatingExpenses: Double? = null,
    val floatingRateDebt: Double? = null,
) {
    init {
        requireAmount(InputKey.REVENUE, revenue)
        requireAmount(InputKey.TOTAL_ASSETS, totalAssets)
        requireAmount(InputKey.EQUITY, equity, allowNegative = true)
        requireAmount(InputKey.EBIT, ebit, allowNegative = true)
        requireAmount(InputKey.CASH, cash)
        requireAmount(InputKey.OPERATING_EXPENSES, operatingExpenses)
        requireAmount(InputKey.FLOATING_RATE_DEBT, floatingRateDebt)
    }
}

/** Section `FX_FLOWS`: expected inflows and outflows of the next 12 months, in millions of the currency. */
data class FxFlowInput(
    val inflow: Double? = null,
    val outflow: Double? = null,
) {
    init {
        require(inflow == null || (inflow.isFinite() && inflow >= 0)) { "FX inflow must be a finite amount ≥ 0" }
        require(outflow == null || (outflow.isFinite() && outflow >= 0)) { "FX outflow must be a finite amount ≥ 0" }
    }
}

/** Section `SEGMENT`. A missing opting-in answer counts as `false` (catalogue default). */
data class SegmentInput(
    val treasuryOrganisation: TreasuryOrganisation? = null,
    val optingIn: Boolean? = null,
)

/** Section `GOALS`. [policyMaxHedgeRatio] is a whole percentage 0–100. */
data class GoalsInput(
    val hedgeObjective: HedgeObjective? = null,
    val policyMaxHedgeRatio: Int? = null,
    val minimumLiquidity: Double? = null,
    val investmentHorizon: InvestmentHorizon? = null,
) {
    init {
        require(policyMaxHedgeRatio == null || policyMaxHedgeRatio in 0..100) { "Policy max hedge ratio must be 0–100" }
        requireAmount(InputKey.MINIMUM_LIQUIDITY, minimumLiquidity)
    }
}

/** Section `RISK_TOLERANCE`. */
data class RiskToleranceInput(
    val tolerableDrawdown: TolerableDrawdown? = null,
    val forgoneGainAttitude: ForgoneGainAttitude? = null,
)

/**
 * Advisor's hedge settings for one currency (table `hedge_setting`).
 *
 * @property startRatio requested ratio for the next 3 months in percent; `null` = default by risk profile
 * @property stepDown reduction per quarter in percentage points
 * @property collarEnabled `null` = rule default (R-HED-06); an explicit `true` only takes effect if the collar is allowed
 */
data class HedgeSetting(
    val startRatio: Int? = null,
    val stepDown: Int = DEFAULT_STEP_DOWN,
    val collarEnabled: Boolean? = null,
) {
    init {
        require(startRatio == null || startRatio in 0..100) { "Start ratio must be 0–100" }
        require(stepDown in 0..100) { "Step down must be 0–100 percentage points" }
    }

    companion object {
        const val DEFAULT_STEP_DOWN = 10
        val DEFAULT = HedgeSetting()
    }
}

/**
 * Market data (spot rates in CHF per unit of the foreign currency, interest rates p.a. as decimal fraction,
 * e.g. `0.02` for 2 %). Every [Currency] must be present.
 */
data class MarketData(
    val spotRates: Map<Currency, Double>,
    val chfInterestRate: Double,
    val interestRates: Map<Currency, Double>,
) {
    init {
        Currency.entries.forEach { currency ->
            val spot = spotRates[currency]
            require(spot != null && spot.isFinite() && spot > 0) { "Spot rate for $currency must be a positive number" }
            val rate = interestRates[currency]
            require(rate != null && rate.isFinite() && rate > -1) { "Interest rate for $currency must be a number > -1" }
        }
        require(chfInterestRate.isFinite() && chfInterestRate > -1) { "CHF interest rate must be a number > -1" }
    }

    fun spotRate(currency: Currency): Double = spotRates.getValue(currency)

    fun interestRate(currency: Currency): Double = interestRates.getValue(currency)
}

private fun requireAmount(key: InputKey, value: Double?, allowNegative: Boolean = false) {
    require(value == null || (value.isFinite() && (allowNegative || value >= 0))) {
        "${key.key} must be a finite amount${if (allowNegative) "" else " ≥ 0"}"
    }
}
