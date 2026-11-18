package ch.treasurykompass.treasury.rules

/** OpenAPI `BlockerType`. */
enum class BlockerType {
    MISSING_INPUT,
    UNCONFIRMED_ANSWER,
    OPEN_PROPOSAL,
    OPEN_CHECKLIST_ITEM,
    DOCUMENT_PROCESSING,
}

/**
 * Workflow state the rule engine does not know itself; supplied by the application.
 *
 * @property completedChecklistItemIds ticked checklist items; ticks of items no longer on the list are ignored
 * @property unconfirmedAnswerKeys question keys with status `PROPOSED` (visible questions only)
 * @property openProposalIds answer proposals with status `OPEN`
 * @property processingDocumentIds documents whose ingest is still running
 */
data class ApprovalWorkflowState(
    val completedChecklistItemIds: Set<String> = emptySet(),
    val unconfirmedAnswerKeys: List<String> = emptyList(),
    val openProposalIds: List<String> = emptyList(),
    val processingDocumentIds: List<String> = emptyList(),
)

/** @property key question key, checklist item ID, proposal ID or document ID (OpenAPI `Blocker.key`) */
data class ApprovalBlocker(
    val type: BlockerType,
    val key: String,
)

data class ApprovalCheck(
    val canApprove: Boolean,
    val blockers: List<ApprovalBlocker>,
)

/**
 * R-APP-01 (regelwerk § 7, Art. 15 FIDLEG, INV-3): approval only without blockers. An incomplete assessment
 * always has at least one [BlockerType.MISSING_INPUT] blocker, so its (empty) checklist cannot be approved.
 * Blocker texts are left to the caller, which owns the question labels.
 */
object ApprovalRule {

    fun check(result: AssessmentResult, state: ApprovalWorkflowState): ApprovalCheck {
        val blockers = buildList {
            result.missingInputs.forEach { add(ApprovalBlocker(BlockerType.MISSING_INPUT, it.key)) }
            state.unconfirmedAnswerKeys.forEach { add(ApprovalBlocker(BlockerType.UNCONFIRMED_ANSWER, it)) }
            state.openProposalIds.forEach { add(ApprovalBlocker(BlockerType.OPEN_PROPOSAL, it)) }
            result.checklist
                .filterNot { it.id in state.completedChecklistItemIds }
                .forEach { add(ApprovalBlocker(BlockerType.OPEN_CHECKLIST_ITEM, it.id)) }
            state.processingDocumentIds.forEach { add(ApprovalBlocker(BlockerType.DOCUMENT_PROCESSING, it)) }
        }
        return ApprovalCheck(canApprove = blockers.isEmpty(), blockers = blockers)
    }
}
