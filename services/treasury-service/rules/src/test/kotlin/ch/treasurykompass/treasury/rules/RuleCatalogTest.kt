package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rule catalogue of architecture.md section 7 (OpenAPI `RuleInfo`). */
class RuleCatalogTest {

    @Test
    fun `catalogue holds the 20 rules of the architecture in order`() {
        assertEquals(
            listOf(
                "R-SEG-01", "R-EXP-01", "R-PRO-01", "R-PRO-02", "R-PRO-03", "R-SUI-01", "R-SUI-02",
                "R-HED-01", "R-HED-02", "R-HED-03", "R-HED-04", "R-HED-05", "R-HED-06", "R-HED-07",
                "R-LIQ-01", "R-LIQ-02", "R-LIQ-03", "R-CHK-01", "R-APP-01", "R-APP-02",
            ),
            RuleCatalog.rules.map { it.code },
        )
    }

    @Test
    fun `legal bases as in the architecture`() {
        assertEquals("Art. 4 und 5 FIDLEG", RuleId.SEG_01.legalBasis)
        assertEquals("Art. 58 FIDLEG", RuleId.SUI_02.legalBasis)
        assertEquals("Treasury-Policy", RuleId.HED_02.legalBasis)
        assertEquals("Art. 26 FIDLEG", RuleId.HED_04.legalBasis)
        assertEquals("Art. 8, 14, 26 und 58 FIDLEG", RuleId.CHK_01.legalBasis)
        assertEquals("Art. 15 FIDLEG", RuleId.APP_01.legalBasis)
        assertNull(RuleId.EXP_01.legalBasis)
        assertNull(RuleId.HED_01.legalBasis)
    }

    @Test
    fun `every rule has title and description`() {
        assertTrue(RuleCatalog.rules.all { it.title.isNotBlank() && it.description.isNotBlank() && it.regelwerkSection.isNotBlank() })
        assertEquals(RuleId.HED_06, RuleCatalog.byCode("R-HED-06"))
        assertNull(RuleCatalog.byCode("R-XXX-99"))
    }

    @Test
    fun `decisions keep the legal basis of their rule`() {
        listOf(DemoClients.MUSTER, DemoClients.COMPONENTS).map(DemoClients::evaluate).flatMap { it.decisions }.forEach { decision ->
            if (decision.ruleId.legalBasis != null) assertTrue(!decision.legalBasis.isNullOrBlank(), "legal basis of ${decision.key}")
            assertTrue(decision.message.isNotBlank())
        }
    }
}
