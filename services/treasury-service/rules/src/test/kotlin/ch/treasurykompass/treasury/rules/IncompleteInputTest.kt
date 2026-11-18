package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INCOMPLETE status (architecture.md section 7): only blocks with complete inputs are computed. */
class IncompleteInputTest {
    private val muster = DemoClients.MUSTER

    @Test
    fun `empty input lists every mandatory key in catalogue order`() {
        val result = DemoClients.evaluate(AssessmentInput())
        assertEquals(AssessmentStatus.INCOMPLETE, result.status)
        assertEquals(InputKey.entries - InputKey.OPTING_IN, result.missingInputs)
        assertNull(result.segment)
        assertNull(result.exposures)
        assertNull(result.riskProfile)
        assertNull(result.suitability)
        assertNull(result.hedges)
        assertNull(result.liquidity)
        assertNull(result.protocolTexts)
        assertNull(result.suggestedMinimumLiquidity)
        assertEquals(emptyList(), result.recommendedProducts)
        assertEquals(emptyList(), result.checklist)
        assertEquals(emptyList(), result.reasons)
        assertEquals(emptyList(), result.decisions)
    }

    @Test
    fun `opting-in is optional and defaults to no`() {
        val result = DemoClients.evaluate(DemoClients.COMPONENTS.copy(segment = SegmentInput(TreasuryOrganisation.TREASURY_DEPARTMENT)))
        assertEquals(AssessmentStatus.COMPLETE, result.status)
        assertEquals(ClientSegment.PROFESSIONAL, assertNotNull(result.segment).result)
    }

    @Test
    fun `segment is computed from key figures and treasury organisation alone`() {
        val input = AssessmentInput(
            financials = FinancialsInput(revenue = 28.4, totalAssets = 19.2, equity = 7.9),
            segment = SegmentInput(TreasuryOrganisation.CFO_OR_ACCOUNTING),
        )
        val result = DemoClients.evaluate(input)
        assertEquals(ClientSegment.RETAIL, assertNotNull(result.segment).result)
        assertNull(result.riskProfile)
        assertEquals(listOf("segment"), result.decisions.map { it.key })
        assertTrue(InputKey.KNOWLEDGE_FX_FORWARD in result.missingInputs, "retail client needs knowledge answers")
    }

    @Test
    fun `knowledge is not mandatory for professional clients`() {
        val result = DemoClients.evaluate(DemoClients.COMPONENTS.copy(knowledge = emptyMap()))
        assertEquals(AssessmentStatus.COMPLETE, result.status)
        assertTrue(assertNotNull(result.suitability).filter { it.product.financialInstrument }.all { it.knowledgePresumed })
    }

    @Test
    fun `missing knowledge of a retail client blocks suitability and the proposal`() {
        val result = DemoClients.evaluate(muster.copy(knowledge = muster.knowledge - Product.SHORT_TERM_BOND))
        assertEquals(AssessmentStatus.INCOMPLETE, result.status)
        assertEquals(listOf(InputKey.KNOWLEDGE_SHORT_TERM_BOND), result.missingInputs)
        assertNotNull(result.segment)
        assertNotNull(result.riskProfile)
        assertNull(result.suitability)
        assertNull(result.hedges)
        assertNull(result.liquidity)
        assertEquals(emptyList(), result.checklist)
        assertEquals(emptyList(), result.reasons)
    }

    @Test
    fun `missing policy only removes the hedge proposal`() {
        val result = DemoClients.evaluate(muster.withGoals { copy(policyMaxHedgeRatio = null) })
        assertEquals(listOf(InputKey.POLICY_MAX_HEDGE_RATIO), result.missingInputs)
        assertNull(result.hedges)
        assertNotNull(result.liquidity)
        assertNotNull(result.riskProfile)
        assertNotNull(result.suitability)
        assertEquals(emptyList(), result.recommendedProducts)
        assertTrue(result.decisions.none { it.key.startsWith("hedge.") })
    }

    @Test
    fun `missing floating rate debt only removes the exposure block`() {
        val result = DemoClients.evaluate(muster.withFinancials { copy(floatingRateDebt = null) })
        assertEquals(listOf(InputKey.FLOATING_RATE_DEBT), result.missingInputs)
        assertNull(result.exposures)
        assertNotNull(result.riskProfile)
        assertNotNull(result.hedges)
        assertEquals(3, result.reasons.size)
    }

    @Test
    fun `missing FX flow of one currency lists both keys of that currency`() {
        val result = DemoClients.evaluate(muster.copy(fxFlows = muster.fxFlows - Currency.USD))
        assertEquals(listOf(InputKey.FX_USD_INFLOW, InputKey.FX_USD_OUTFLOW), result.missingInputs)
        assertNull(result.exposures)
        assertNull(result.riskProfile, "risk capacity needs the total currency risk")
        assertNotNull(result.segment)
    }

    @Test
    fun `missing willingness answer removes profile, hedges and liquidity`() {
        val result = DemoClients.evaluate(muster.copy(riskTolerance = muster.riskTolerance.copy(tolerableDrawdown = null)))
        assertEquals(listOf(InputKey.TOLERABLE_DRAWDOWN), result.missingInputs)
        assertNull(result.riskProfile)
        assertNull(result.hedges)
        assertNull(result.liquidity)
        assertNotNull(result.exposures)
        assertNotNull(result.suitability)
    }

    @Test
    fun `missing minimum liquidity keeps the guide value available`() {
        val result = DemoClients.evaluate(muster.withGoals { copy(minimumLiquidity = null) })
        assertEquals(listOf(InputKey.MINIMUM_LIQUIDITY), result.missingInputs)
        assertEquals(3.3, result.suggestedMinimumLiquidity)
        assertNull(result.exposures)
        assertNull(result.riskProfile)
        assertNull(result.liquidity)
    }

    @Test
    fun `input keys map to catalogue keys`() {
        assertEquals("fx.usd.outflow", InputKey.FX_USD_OUTFLOW.key)
        assertEquals(InputKey.KNOWLEDGE_ZERO_COST_COLLAR, InputKey.fromKey("knowledge.zeroCostCollar"))
        assertEquals(23, InputKey.entries.map { it.key }.toSet().size)
    }
}
