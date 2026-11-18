package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** R-APP-01 (INV-3): approval only without blockers. */
class ApprovalRuleTest {
    private val complete = DemoClients.evaluate(DemoClients.COMPONENTS)
    private val allTicked = ApprovalWorkflowState(completedChecklistItemIds = complete.checklist.map { it.id }.toSet())

    @Test
    fun `complete assessment with ticked checklist can be approved`() {
        val check = ApprovalRule.check(complete, allTicked)
        assertTrue(check.canApprove)
    }

    @Test
    fun `blockers are listed by type in a fixed order`() {
        val state = ApprovalWorkflowState(
            completedChecklistItemIds = setOf("info"),
            unconfirmedAnswerKeys = listOf("fin.totalAssets"),
            openProposalIds = listOf("proposal-1"),
            processingDocumentIds = listOf("document-1"),
        )
        val check = ApprovalRule.check(complete, state)
        assertFalse(check.canApprove)
        assertEquals(
            listOf(
                ApprovalBlocker(BlockerType.UNCONFIRMED_ANSWER, "fin.totalAssets"),
                ApprovalBlocker(BlockerType.OPEN_PROPOSAL, "proposal-1"),
                ApprovalBlocker(BlockerType.OPEN_CHECKLIST_ITEM, "riskBrochure"),
                ApprovalBlocker(BlockerType.OPEN_CHECKLIST_ITEM, "costDisclosure"),
                ApprovalBlocker(BlockerType.DOCUMENT_PROCESSING, "document-1"),
            ),
            check.blockers,
        )
    }

    @Test
    fun `missing inputs block approval even without checklist`() {
        val incomplete = DemoClients.evaluate(DemoClients.COMPONENTS.withGoals { copy(investmentHorizon = null) })
        val check = ApprovalRule.check(incomplete, allTicked)
        assertFalse(check.canApprove)
        assertEquals(listOf(ApprovalBlocker(BlockerType.MISSING_INPUT, "goals.investmentHorizon")), check.blockers)
    }

    @Test
    fun `ticks of items no longer on the list are ignored`() {
        val state = allTicked.copy(completedChecklistItemIds = allTicked.completedChecklistItemIds + "kid.FX_FORWARD")
        assertTrue(ApprovalRule.check(complete, state).canApprove)
    }

    @Test
    fun `a newly recommended KID product opens a new item`() {
        val retail = DemoClients.evaluate(DemoClients.COMPONENTS.copy(segment = DemoClients.COMPONENTS.segment.copy(optingIn = true)))
        val check = ApprovalRule.check(retail, allTicked)
        assertEquals(
            listOf("kid.FX_FORWARD", "kid.ZERO_COST_COLLAR", "kid.MONEY_MARKET_FUND"),
            check.blockers.map { it.key },
        )
    }
}
