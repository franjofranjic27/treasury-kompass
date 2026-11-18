package ch.treasurykompass.treasury.rules

/**
 * Deterministic rule engine of Treasury Kompass (`docs/regelwerk.md`, port of the prototype's `compute()`).
 *
 * A pure function of its arguments: no I/O, no clock, no state. Every block is computed as soon as its own
 * inputs are complete; with missing mandatory inputs the result is [AssessmentStatus.INCOMPLETE] and lists them.
 */
object RuleEngine {

    /**
     * Evaluates all rules for one client.
     *
     * @param input questionnaire answers
     * @param hedgeSettings advisor settings per currency; missing currencies use [HedgeSetting.DEFAULT]
     * @param marketData spot rates and interest rates for every [Currency]
     */
    fun evaluate(
        input: AssessmentInput,
        hedgeSettings: Map<Currency, HedgeSetting> = emptyMap(),
        marketData: MarketData,
    ): AssessmentResult {
        val segment = segmentOf(input)
        val fxExposures = fxExposuresOf(input, marketData)
        val suggestedMinimumLiquidity = input.financials.operatingExpenses?.let(LiquidityAllocator::suggestedMinimumLiquidity)
        val excessLiquidity = excessLiquidityOf(input)
        val exposures = exposuresOf(input, fxExposures, excessLiquidity)
        val riskProfile = riskProfileOf(input, fxExposures, excessLiquidity, segment)
        val suitability = segment?.let { suitabilityOf(it.result, input.knowledge) }
        val hedges = hedgesOf(input, fxExposures, riskProfile, suitability, hedgeSettings, marketData)
        val liquidity = liquidityOf(input, excessLiquidity, riskProfile, suitability)
        val proposal = proposalOf(input, segment, riskProfile, suitability, hedges, liquidity)
        val missingInputs = missingInputsOf(input, segment)

        val decisions = buildList {
            segment?.let { add(SegmentRule.decision(it)) }
            exposures?.let { addAll(ExposureCalculator.decisions(it)) }
            riskProfile?.let { addAll(RiskProfileRule.decisions(it)) }
            if (segment != null && suitability != null) addAll(SuitabilityRule.decisions(segment.result, suitability))
            if (fxExposures != null && hedges != null) addAll(HedgeCalculator.decisions(fxExposures, hedges))
            liquidity?.let { addAll(LiquidityAllocator.decisions(it)) }
            proposal?.let { add(ChecklistBuilder.decision(it.checklist)) }
        }

        return AssessmentResult(
            status = if (missingInputs.isEmpty()) AssessmentStatus.COMPLETE else AssessmentStatus.INCOMPLETE,
            missingInputs = missingInputs,
            segment = segment,
            exposures = exposures,
            riskProfile = riskProfile,
            suitability = suitability,
            hedges = hedges,
            liquidity = liquidity,
            recommendedProducts = proposal?.recommendedProducts.orEmpty(),
            checklist = proposal?.checklist.orEmpty(),
            reasons = proposal?.reasons.orEmpty(),
            protocolTexts = proposal?.protocolTexts,
            suggestedMinimumLiquidity = suggestedMinimumLiquidity,
            decisions = decisions,
        )
    }

    private fun segmentOf(input: AssessmentInput): SegmentResult? {
        val financials = input.financials
        return SegmentRule.evaluate(
            SegmentFacts(
                totalAssets = financials.totalAssets ?: return null,
                revenue = financials.revenue ?: return null,
                equity = financials.equity ?: return null,
                treasuryOrganisation = input.segment.treasuryOrganisation ?: return null,
                optingInDeclared = input.segment.optingIn ?: false,
            ),
        )
    }

    private fun fxExposuresOf(input: AssessmentInput, marketData: MarketData): List<FxExposure>? {
        val ebit = input.financials.ebit ?: return null
        return Currency.entries.map { currency ->
            val flow = input.fxFlow(currency)
            ExposureCalculator.fxExposure(
                currency = currency,
                inflow = flow.inflow ?: return null,
                outflow = flow.outflow ?: return null,
                spotRate = marketData.spotRate(currency),
                ebit = ebit,
            )
        }
    }

    private fun excessLiquidityOf(input: AssessmentInput): Double? {
        val cash = input.financials.cash ?: return null
        val minimumLiquidity = input.goals.minimumLiquidity ?: return null
        return LiquidityAllocator.excessLiquidity(cash, minimumLiquidity)
    }

    private fun exposuresOf(input: AssessmentInput, fx: List<FxExposure>?, excessLiquidity: Double?): Exposures? {
        val financials = input.financials
        val ebit = financials.ebit ?: return null
        val operatingExpenses = financials.operatingExpenses ?: return null
        val interest = ExposureCalculator.interestExposure(financials.floatingRateDebt ?: return null, ebit)
        val liquidity = LiquidityPosition(
            cash = financials.cash ?: return null,
            minimumLiquidity = input.goals.minimumLiquidity ?: return null,
            suggestedMinimumLiquidity = LiquidityAllocator.suggestedMinimumLiquidity(operatingExpenses),
            monthlyOperatingExpenses = LiquidityAllocator.monthlyOperatingExpenses(operatingExpenses),
            excessLiquidity = excessLiquidity ?: return null,
        )
        return ExposureCalculator.exposures(fx ?: return null, ebit, interest, liquidity)
    }

    private fun riskProfileOf(
        input: AssessmentInput,
        fx: List<FxExposure>?,
        excessLiquidity: Double?,
        segment: SegmentResult?,
    ): RiskProfile? {
        val financials = input.financials
        val ebit = financials.ebit ?: return null
        val capacity = RiskProfileRule.capacity(
            CapacityFacts(
                equity = financials.equity ?: return null,
                totalAssets = financials.totalAssets ?: return null,
                excessLiquidity = excessLiquidity ?: return null,
                operatingExpenses = financials.operatingExpenses ?: return null,
                ebit = ebit,
                revenue = financials.revenue ?: return null,
                fxSensitivityPctEbit = ExposureCalculator.fxSensitivityPctEbit(fx ?: return null, ebit),
            ),
        )
        val willingness = RiskProfileRule.willingness(
            hedgeObjective = input.goals.hedgeObjective ?: return null,
            tolerableDrawdown = input.riskTolerance.tolerableDrawdown ?: return null,
            forgoneGainAttitude = input.riskTolerance.forgoneGainAttitude ?: return null,
        )
        return RiskProfileRule.profile(capacity, willingness, segment?.result)
    }

    private fun suitabilityOf(segment: ClientSegment, knowledge: Map<Product, KnowledgeLevel>): List<Suitability>? {
        val knowledgeComplete = Product.financialInstruments.all { it in knowledge }
        if (segment == ClientSegment.RETAIL && !knowledgeComplete) return null
        return SuitabilityRule.evaluate(segment, knowledge)
    }

    private fun hedgesOf(
        input: AssessmentInput,
        fx: List<FxExposure>?,
        riskProfile: RiskProfile?,
        suitability: List<Suitability>?,
        hedgeSettings: Map<Currency, HedgeSetting>,
        marketData: MarketData,
    ): HedgeOverview? = HedgeCalculator.propose(
        HedgeFacts(
            fx = fx ?: return null,
            profile = riskProfile?.code ?: return null,
            fxForward = suitability?.statusOf(Product.FX_FORWARD) ?: return null,
            zeroCostCollar = suitability.statusOf(Product.ZERO_COST_COLLAR),
            policyMaxHedgeRatio = input.goals.policyMaxHedgeRatio ?: return null,
            forgoneGainAttitude = input.riskTolerance.forgoneGainAttitude ?: return null,
            settings = hedgeSettings,
            marketData = marketData,
        ),
    )

    private fun liquidityOf(
        input: AssessmentInput,
        excessLiquidity: Double?,
        riskProfile: RiskProfile?,
        suitability: List<Suitability>?,
    ): LiquidityAllocation? = LiquidityAllocator.allocate(
        LiquidityFacts(
            excessLiquidity = excessLiquidity ?: return null,
            horizon = input.goals.investmentHorizon ?: return null,
            profile = riskProfile?.code ?: return null,
            moneyMarketFund = suitability?.statusOf(Product.MONEY_MARKET_FUND) ?: return null,
            shortTermBond = suitability.statusOf(Product.SHORT_TERM_BOND),
        ),
    )

    /** Recommendation, checklist and texts need the complete proposal (hedges and liquidity). */
    private fun proposalOf(
        input: AssessmentInput,
        segment: SegmentResult?,
        riskProfile: RiskProfile?,
        suitability: List<Suitability>?,
        hedges: HedgeOverview?,
        liquidity: LiquidityAllocation?,
    ): CompleteProposal? {
        if (segment == null || riskProfile == null || suitability == null || hedges == null || liquidity == null) return null
        val recommendedProducts = ChecklistBuilder.recommendedProducts(hedges, liquidity)
        val reasons = ReasonWriter.reasons(
            ReasonFacts(
                segment = segment.result,
                profile = riskProfile.code,
                hedgeObjective = input.goals.hedgeObjective ?: return null,
                forgoneGainAttitude = input.riskTolerance.forgoneGainAttitude ?: return null,
                horizon = liquidity.horizon,
                fxForward = suitability.statusOf(Product.FX_FORWARD),
                fxForwardEvidence = input.knowledgeEvidence[Product.FX_FORWARD],
                hedges = hedges,
                liquidity = liquidity,
            ),
        )
        return CompleteProposal(
            recommendedProducts = recommendedProducts,
            checklist = ChecklistBuilder.build(segment.result, recommendedProducts, suitability),
            reasons = reasons,
            protocolTexts = ReasonWriter.protocolTexts(hedges, liquidity, suitability),
        )
    }

    private fun missingInputsOf(input: AssessmentInput, segment: SegmentResult?): List<InputKey> {
        val financials = input.financials
        val answered = buildMap {
            put(InputKey.REVENUE, financials.revenue)
            put(InputKey.TOTAL_ASSETS, financials.totalAssets)
            put(InputKey.EQUITY, financials.equity)
            put(InputKey.EBIT, financials.ebit)
            put(InputKey.CASH, financials.cash)
            put(InputKey.OPERATING_EXPENSES, financials.operatingExpenses)
            put(InputKey.FLOATING_RATE_DEBT, financials.floatingRateDebt)
            Currency.entries.forEach { currency ->
                put(currency.inflowKey, input.fxFlow(currency).inflow)
                put(currency.outflowKey, input.fxFlow(currency).outflow)
            }
            put(InputKey.TREASURY_ORGANISATION, input.segment.treasuryOrganisation)
            put(InputKey.HEDGE_OBJECTIVE, input.goals.hedgeObjective)
            put(InputKey.POLICY_MAX_HEDGE_RATIO, input.goals.policyMaxHedgeRatio)
            put(InputKey.MINIMUM_LIQUIDITY, input.goals.minimumLiquidity)
            put(InputKey.INVESTMENT_HORIZON, input.goals.investmentHorizon)
            put(InputKey.TOLERABLE_DRAWDOWN, input.riskTolerance.tolerableDrawdown)
            put(InputKey.FORGONE_GAIN_ATTITUDE, input.riskTolerance.forgoneGainAttitude)
            // Knowledge is mandatory unless the client is known to be professional (catalogue, section 6).
            if (segment?.result != ClientSegment.PROFESSIONAL) {
                Product.financialInstruments.forEach { product -> put(checkNotNull(product.knowledgeKey), input.knowledge[product]) }
            }
        }
        return answered.filterValues { it == null }.keys.sortedBy { it.ordinal }
    }

    private fun List<Suitability>.statusOf(product: Product): SuitabilityStatus = first { it.product == product }.status

    private data class CompleteProposal(
        val recommendedProducts: List<Product>,
        val checklist: List<ChecklistItem>,
        val reasons: List<String>,
        val protocolTexts: ProtocolTexts,
    )
}
