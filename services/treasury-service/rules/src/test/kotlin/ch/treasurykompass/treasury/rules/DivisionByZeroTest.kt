package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Division by zero as in the prototype (architecture.md section 7). */
class DivisionByZeroTest {
    private val muster = DemoClients.MUSTER

    private fun capacityFactors(input: AssessmentInput): List<CapacityFactorScore> =
        assertNotNull(DemoClients.evaluate(input).riskProfile).capacity.factors

    @Test
    fun `total assets zero gives equity ratio zero`() {
        val equityRatio = capacityFactors(muster.withFinancials { copy(totalAssets = 0.0) })[0]
        assertClose(0.0, equityRatio.value, EXACT_TOLERANCE)
        assertEquals(1, equityRatio.score)
    }

    @Test
    fun `operating expenses zero give zero months and a zero guide value`() {
        val input = muster.withFinancials { copy(operatingExpenses = 0.0) }
        val result = DemoClients.evaluate(input)
        val excessMonths = assertNotNull(result.riskProfile).capacity.factors[1]
        assertClose(0.0, excessMonths.value, EXACT_TOLERANCE)
        assertEquals(1, excessMonths.score)
        assertEquals(0.0, result.suggestedMinimumLiquidity)
        assertClose(0.0, assertNotNull(result.exposures).liquidity.monthlyOperatingExpenses, EXACT_TOLERANCE)
    }

    @Test
    fun `revenue zero gives EBIT margin zero`() {
        val margin = capacityFactors(muster.withFinancials { copy(revenue = 0.0) })[2]
        assertClose(0.0, margin.value, EXACT_TOLERANCE)
        assertEquals(1, margin.score)
    }

    @Test
    fun `negative EBIT counts as fully uncovered currency risk`() {
        val result = DemoClients.evaluate(muster.withFinancials { copy(ebit = -1.5) })
        val exposures = assertNotNull(result.exposures)
        exposures.fx.forEach { assertClose(0.0, it.sensitivityPctEbit, EXACT_TOLERANCE) }
        assertEquals(1.0, exposures.fxSensitivityPctEbit)
        assertClose(0.0, exposures.interest.sensitivityPctEbit, EXACT_TOLERANCE)
        val factors = assertNotNull(result.riskProfile).capacity.factors
        assertEquals(1, factors[2].score, "negative margin")
        assertEquals(1, factors[3].score, "100 % unhedged currency risk")
        assertEquals("100 % des EBIT bei 10 % Kursbewegung", factors[3].displayValue)
        assertEquals(RiskLevel.LOW, result.fx(Currency.EUR).riskLevel)
    }

    @Test
    fun `negative equity is allowed and scores lowest`() {
        val equityRatio = capacityFactors(muster.withFinancials { copy(equity = -2.0) })[0]
        assertEquals(1, equityRatio.score)
        assertEquals("−10 %", equityRatio.displayValue)
    }
}
