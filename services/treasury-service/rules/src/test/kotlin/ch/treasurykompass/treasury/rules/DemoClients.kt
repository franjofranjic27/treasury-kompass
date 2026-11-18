package ch.treasurykompass.treasury.rules

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Demo clients and market data of `docs/regelwerk.md` sections 8 and 9 (seed values, architecture.md section 13). */
object DemoClients {

    val MARKET_DATA = MarketData(
        spotRates = mapOf(Currency.EUR to 0.94, Currency.USD to 0.80),
        chfInterestRate = 0.0,
        interestRates = mapOf(Currency.EUR to 0.02, Currency.USD to 0.0375),
    )

    /** Evidence label as in the prototype's `prefilled['knowledge.fwd']`, so reason texts match verbatim. */
    const val MUSTER_FX_FORWARD_EVIDENCE = "Anhang Ziff. 7, offene Devisentermingeschäfte"

    val MUSTER = AssessmentInput(
        financials = FinancialsInput(
            revenue = 28.4,
            totalAssets = 19.2,
            equity = 7.9,
            ebit = 2.3,
            cash = 6.4,
            operatingExpenses = 26.1,
            floatingRateDebt = 6.0,
        ),
        fxFlows = mapOf(
            Currency.EUR to FxFlowInput(inflow = 18.7, outflow = 4.5),
            Currency.USD to FxFlowInput(inflow = 0.0, outflow = 3.1),
        ),
        segment = SegmentInput(treasuryOrganisation = TreasuryOrganisation.CFO_OR_ACCOUNTING),
        goals = GoalsInput(
            hedgeObjective = HedgeObjective.BUDGET_CERTAINTY,
            policyMaxHedgeRatio = 80,
            minimumLiquidity = 3.3,
            investmentHorizon = InvestmentHorizon.UNDER_1_YEAR,
        ),
        riskTolerance = RiskToleranceInput(
            tolerableDrawdown = TolerableDrawdown.UP_TO_2_PERCENT,
            forgoneGainAttitude = ForgoneGainAttitude.TOLERABLE,
        ),
        knowledge = mapOf(
            Product.FX_FORWARD to KnowledgeLevel.EXPERIENCE,
            Product.ZERO_COST_COLLAR to KnowledgeLevel.NONE,
            Product.MONEY_MARKET_FUND to KnowledgeLevel.KNOWLEDGE,
            Product.SHORT_TERM_BOND to KnowledgeLevel.EXPERIENCE,
        ),
        knowledgeEvidence = mapOf(Product.FX_FORWARD to MUSTER_FX_FORWARD_EVIDENCE),
    )

    val COMPONENTS = AssessmentInput(
        financials = FinancialsInput(
            revenue = 146.0,
            totalAssets = 112.0,
            equity = 46.0,
            ebit = 12.8,
            cash = 28.0,
            operatingExpenses = 133.2,
            floatingRateDebt = 25.0,
        ),
        fxFlows = mapOf(
            Currency.EUR to FxFlowInput(inflow = 85.4, outflow = 33.0),
            Currency.USD to FxFlowInput(inflow = 33.0, outflow = 12.0),
        ),
        segment = SegmentInput(treasuryOrganisation = TreasuryOrganisation.TREASURY_DEPARTMENT, optingIn = false),
        goals = GoalsInput(
            hedgeObjective = HedgeObjective.PROTECT_MARGINS,
            policyMaxHedgeRatio = 90,
            minimumLiquidity = 16.7,
            investmentHorizon = InvestmentHorizon.ONE_TO_THREE_YEARS,
        ),
        riskTolerance = RiskToleranceInput(
            tolerableDrawdown = TolerableDrawdown.UP_TO_5_PERCENT,
            forgoneGainAttitude = ForgoneGainAttitude.TOLERABLE,
        ),
        knowledge = Product.financialInstruments.associateWith { KnowledgeLevel.EXPERIENCE },
        knowledgeEvidence = mapOf(Product.FX_FORWARD to "Anhang Ziff. 9, Devisentermingeschäfte"),
    )

    fun evaluate(input: AssessmentInput, hedgeSettings: Map<Currency, HedgeSetting> = emptyMap()): AssessmentResult =
        RuleEngine.evaluate(input, hedgeSettings, MARKET_DATA)
}

fun AssessmentInput.withFinancials(transform: FinancialsInput.() -> FinancialsInput): AssessmentInput =
    copy(financials = financials.transform())

fun AssessmentInput.withGoals(transform: GoalsInput.() -> GoalsInput): AssessmentInput = copy(goals = goals.transform())

fun AssessmentInput.withKnowledge(vararg levels: Pair<Product, KnowledgeLevel>): AssessmentInput =
    copy(knowledge = knowledge + levels)

fun AssessmentInput.withFxFlow(currency: Currency, inflow: Double, outflow: Double): AssessmentInput =
    copy(fxFlows = fxFlows + (currency to FxFlowInput(inflow, outflow)))

/** Absolute tolerance for values the use cases state with 4 decimals. */
const val AC_TOLERANCE = 1e-4

/** Tolerance of architecture.md section 7 for exact values. */
const val EXACT_TOLERANCE = 1e-6

fun assertClose(expected: Double, actual: Double, tolerance: Double = AC_TOLERANCE, message: String? = null) =
    assertEquals(expected, actual, tolerance, message)

fun AssessmentResult.proposal(currency: Currency): HedgeProposal =
    assertNotNull(hedgeProposalOf(currency), "Hedge proposal for $currency expected")

fun AssessmentResult.suitabilityStatus(product: Product): SuitabilityStatus =
    assertNotNull(suitabilityOf(product), "Suitability for $product expected").status

fun AssessmentResult.decision(key: String): RuleDecision =
    assertNotNull(decisions.firstOrNull { it.key == key }, "Decision $key expected")

fun AssessmentResult.fx(currency: Currency): FxExposure =
    assertNotNull(exposures?.fx?.firstOrNull { it.currency == currency }, "Exposure for $currency expected")

fun HedgeProposal.effectiveRatios(): List<Int> = tranches.map { it.effectiveRatio }

fun LiquidityAllocation.shares(): List<Int> = parts.map { it.sharePct }
