package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/** Inputs outside the catalogue ranges are programming errors and fail fast. */
class InputValidationTest {

    @Test
    fun `amounts must not be negative except equity and EBIT`() {
        assertFailsWith<IllegalArgumentException> { FinancialsInput(totalAssets = -1.0) }
        assertFailsWith<IllegalArgumentException> { FinancialsInput(cash = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { FxFlowInput(inflow = -0.1) }
        assertFailsWith<IllegalArgumentException> { GoalsInput(minimumLiquidity = -3.0) }
        FinancialsInput(equity = -1.0, ebit = -1.0)
    }

    @Test
    fun `ratios must be whole percentages between 0 and 100`() {
        assertFailsWith<IllegalArgumentException> { GoalsInput(policyMaxHedgeRatio = 120) }
        assertFailsWith<IllegalArgumentException> { HedgeSetting(startRatio = 110) }
        assertFailsWith<IllegalArgumentException> { HedgeSetting(startRatio = -5) }
        assertFailsWith<IllegalArgumentException> { HedgeSetting(stepDown = -10) }
    }

    @Test
    fun `knowledge is only accepted for financial instruments`() {
        assertFailsWith<IllegalArgumentException> { AssessmentInput(knowledge = mapOf(Product.DEPOSIT to KnowledgeLevel.EXPERIENCE)) }
    }

    @Test
    fun `market data must cover every currency`() {
        assertFailsWith<IllegalArgumentException> {
            MarketData(spotRates = mapOf(Currency.EUR to 0.94), chfInterestRate = 0.0, interestRates = mapOf(Currency.EUR to 0.02, Currency.USD to 0.0375))
        }
        assertFailsWith<IllegalArgumentException> {
            MarketData(spotRates = mapOf(Currency.EUR to 0.94, Currency.USD to 0.0), chfInterestRate = 0.0, interestRates = mapOf(Currency.EUR to 0.02, Currency.USD to 0.0375))
        }
    }

    @Test
    fun `unknown question keys are rejected`() {
        assertFailsWith<IllegalArgumentException> { InputKey.fromKey("fin.unknown") }
    }
}
