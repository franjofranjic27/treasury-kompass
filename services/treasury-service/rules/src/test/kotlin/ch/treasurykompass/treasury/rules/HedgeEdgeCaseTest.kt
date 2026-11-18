package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edge cases of R-HED-01 to R-HED-07 (regelwerk § 5, INV-2). */
class HedgeEdgeCaseTest {
    private val muster = DemoClients.MUSTER

    @Test
    fun `INV-2 no tranche ever exceeds min of 100 percent and the policy`() {
        val policies = listOf(0, 30, 60, 80, 90, 100)
        val startRatios = listOf(null, 0, 35, 85, 100)
        val stepDowns = listOf(0, 5, 10, 15, 40)
        val willingnessVariants = listOf(
            RiskToleranceInput(TolerableDrawdown.NONE, ForgoneGainAttitude.ACCEPTABLE),
            RiskToleranceInput(TolerableDrawdown.OVER_5_PERCENT, ForgoneGainAttitude.NOT_ACCEPTABLE),
        )
        for (policy in policies) for (start in startRatios) for (step in stepDowns) for (tolerance in willingnessVariants) {
            val input = muster.withGoals { copy(policyMaxHedgeRatio = policy) }.copy(riskTolerance = tolerance)
            val settings = Currency.entries.associateWith { HedgeSetting(startRatio = start, stepDown = step) }
            val hedges = assertNotNull(DemoClients.evaluate(input, settings).hedges)
            assertEquals(policy, hedges.cap)
            hedges.proposals.forEach { proposal ->
                assertTrue(proposal.startRatio <= hedges.cap, "start ≤ cap for policy $policy, start $start")
                proposal.tranches.forEach { tranche ->
                    assertTrue(tranche.ratio in 0..hedges.cap, "ratio within 0..cap: $tranche")
                    assertTrue(tranche.effectiveRatio in 0..hedges.cap)
                    assertTrue(abs(tranche.hedgedAmountFx) <= abs(tranche.amountFx) + 1e-12, "never more than the position")
                }
                assertTrue(proposal.averageRatio <= hedges.cap)
                assertTrue(proposal.hedgedAmountFx <= abs(proposal.netPosition) + 1e-12)
            }
        }
    }

    @Test
    fun `step down below zero is clamped to zero`() {
        val eur = DemoClients.evaluate(muster, mapOf(Currency.EUR to HedgeSetting(stepDown = 40))).proposal(Currency.EUR)
        assertEquals(listOf(80, 40, 0, 0), eur.effectiveRatios())
        assertEquals(30.0, eur.averageRatio)
    }

    @Test
    fun `start ratio zero hedges nothing without blocking`() {
        val eur = DemoClients.evaluate(muster, mapOf(Currency.EUR to HedgeSetting(startRatio = 0))).proposal(Currency.EUR)
        assertEquals(listOf(0, 0, 0, 0), eur.effectiveRatios())
        assertFalse(eur.blocked)
        assertFalse(eur.limitedByPolicy)
    }

    @ParameterizedTest(name = "profile {0} starts at {1} %")
    @CsvSource("SAFETY, 90", "CONSERVATIVE, 80", "BALANCED, 70", "GROWTH, 60", "DYNAMIC, 50")
    fun `default start ratio by risk profile`(profile: RiskProfileCode, expected: Int) {
        assertEquals(expected, HedgeCalculator.defaultStartRatio(profile))
    }

    @Test
    fun `default start ratio above the cap is limited`() {
        // Profile 1 starts at 90 %, policy allows 80 %.
        val input = muster
            .withGoals { copy(hedgeObjective = HedgeObjective.BUDGET_CERTAINTY) }
            .copy(riskTolerance = RiskToleranceInput(TolerableDrawdown.NONE, ForgoneGainAttitude.ACCEPTABLE))
        val eur = DemoClients.evaluate(input).proposal(Currency.EUR)
        assertEquals(90, eur.defaultStartRatio)
        assertEquals(80, eur.startRatio)
        assertTrue(eur.limitedByPolicy)
    }

    @Test
    fun `minimum position of CHF 0_5 Mio is inclusive`() {
        val market = DemoClients.MARKET_DATA.copy(spotRates = mapOf(Currency.EUR to 1.0, Currency.USD to 1.0))
        fun hedgesFor(usdOutflow: Double): HedgeOverview {
            val input = muster.withFxFlow(Currency.USD, inflow = 0.0, outflow = usdOutflow)
            return assertNotNull(RuleEngine.evaluate(input, marketData = market).hedges)
        }
        assertEquals(listOf(Currency.EUR, Currency.USD), hedgesFor(0.5).proposals.map { it.currency })
        val below = hedgesFor(0.49)
        assertEquals(listOf(Currency.EUR), below.proposals.map { it.currency })
        assertEquals(listOf(Currency.USD), below.naturallyHedged.map { it.currency })
    }

    @Test
    fun `zero net position is neither hedged nor naturally hedged`() {
        val result = DemoClients.evaluate(muster.withFxFlow(Currency.USD, inflow = 2.0, outflow = 2.0))
        val hedges = assertNotNull(result.hedges)
        assertEquals(listOf(Currency.EUR), hedges.proposals.map { it.currency })
        assertEquals(emptyList(), hedges.naturallyHedged)
        assertEquals(ExposureDirection.NONE, result.fx(Currency.USD).direction)
        assertEquals("NO_POSITION", result.decision("hedge.USD.minimumPosition").value)
        assertEquals("Währungsrisiko USD: Keine Nettoposition", result.decision("exposure.USD").message)
        assertEquals(listOf("EUR −10 %", "Zins +1 Prozentpunkt"), assertNotNull(result.exposures).stress.map { it.label })
    }

    @Test
    fun `blocked forward also blocks a suitable collar`() {
        val input = muster.withKnowledge(Product.FX_FORWARD to KnowledgeLevel.NONE, Product.ZERO_COST_COLLAR to KnowledgeLevel.EXPERIENCE)
        val result = DemoClients.evaluate(input, mapOf(Currency.EUR to HedgeSetting(collarEnabled = true)))
        val eur = result.proposal(Currency.EUR)
        assertEquals(SuitabilityStatus.SUITABLE, result.suitabilityStatus(Product.ZERO_COST_COLLAR))
        assertTrue(eur.blocked)
        assertFalse(eur.collarAllowed)
        assertFalse(eur.collarEnabled)
        assertEquals(
            "Devisentermingeschäfte nicht geeignet: Es liegen keine Kenntnisse vor. Vor einer Empfehlung Aufklärung durchführen " +
                "und Kenntnisse im Fragebogen erfassen (Art. 14 FIDLEG).",
            eur.blockedReason,
        )
        assertEquals("EUR: Zero-Cost-Collar nicht erlaubt, Devisentermingeschäfte gesperrt", result.decision("hedge.EUR.collar").message)
        assertFalse(Product.ZERO_COST_COLLAR in result.recommendedProducts)
    }

    @Test
    fun `collar after disclosure is allowed but not enabled by default`() {
        val input = muster.withKnowledge(Product.ZERO_COST_COLLAR to KnowledgeLevel.KNOWLEDGE)
        val result = DemoClients.evaluate(input)
        val eur = result.proposal(Currency.EUR)
        assertTrue(eur.collarAllowed)
        assertFalse(eur.collarEnabled)
        assertEquals("DISABLED", result.decision("hedge.EUR.collar").value)

        val enabled = DemoClients.evaluate(input, mapOf(Currency.EUR to HedgeSetting(collarEnabled = true)))
        assertTrue(enabled.proposal(Currency.EUR).collarEnabled)
        assertFalse(enabled.proposal(Currency.USD).collarEnabled)
        assertTrue(enabled.checklist.any { it.id == "warning.ZERO_COST_COLLAR" })
        assertTrue(enabled.checklist.any { it.id == "kid.ZERO_COST_COLLAR" })
    }

    @Test
    fun `acceptable forgone gain keeps the collar off by default`() {
        val input = DemoClients.COMPONENTS.copy(
            riskTolerance = DemoClients.COMPONENTS.riskTolerance.copy(forgoneGainAttitude = ForgoneGainAttitude.ACCEPTABLE),
        )
        val eur = DemoClients.evaluate(input).proposal(Currency.EUR)
        assertTrue(eur.collarAllowed)
        assertFalse(eur.collarEnabled)
    }

    @Test
    fun `advisor can switch off the default collar`() {
        val result = DemoClients.evaluate(DemoClients.COMPONENTS, mapOf(Currency.EUR to HedgeSetting(collarEnabled = false)))
        val eur = result.proposal(Currency.EUR)
        assertFalse(eur.collarEnabled)
        assertEquals(0.0, eur.collarShare, 0.0)
        assertTrue(result.proposal(Currency.USD).collarEnabled)
        assertTrue(Product.ZERO_COST_COLLAR in result.recommendedProducts)
        assertTrue(assertNotNull(result.protocolTexts).recommendations.first().endsWith("Volumen 28.82 Mio. EUR, Devisentermingeschäfte"))
    }

    @Test
    fun `collar limits the scenario within its band`() {
        val eur = DemoClients.evaluate(DemoClients.COMPONENTS).proposal(Currency.EUR)
        val tranche = eur.tranches.first()
        val deepFall = ScenarioCalculator.point(eur.spotRate, eur.netPosition, eur.tranches, eur.collarShare, -15)
        assertTrue(deepFall.spotRate < tranche.collarFloor, "spot below the floor, collar pays the floor")
        assertTrue(abs(deepFall.hedgedChf) < abs(deepFall.unhedgedChf))
        assertEquals(31, eur.scenario.size)
    }

    @Test
    fun `default hedge setting applies when none is given`() {
        val eur = DemoClients.evaluate(muster).proposal(Currency.EUR)
        assertNull(eur.requestedStartRatio)
        assertEquals(HedgeSetting.DEFAULT_STEP_DOWN, eur.stepDown)
    }
}
