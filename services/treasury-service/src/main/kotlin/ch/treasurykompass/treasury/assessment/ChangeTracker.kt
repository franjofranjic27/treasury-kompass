package ch.treasurykompass.treasury.assessment

import ch.treasurykompass.treasury.advisory.ApprovalRevoker
import ch.treasurykompass.treasury.questionnaire.RuleProposalService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** What caused a change. Everything except creation and seeding revokes an active approval (INV-4). */
enum class ChangeTrigger(val revokesApproval: Boolean) {
    CLIENT_CREATED(false),
    CLIENT_CHANGED(true),
    DOCUMENT_UPLOADED(true),
    DOCUMENT_DELETED(true),
    ANSWER_CHANGED(true),
    PROPOSAL_APPLIED(true),
    PROPOSAL_ACCEPTED(true),
    HEDGE_SETTINGS_CHANGED(true),
    CHECKLIST_CHANGED(true),
    RATIONALE_CHANGED(true),
    DEMO_SEEDED(false),
}

/** @property subject what changed, e.g. a question key; becomes part of the revocation reason. */
data class Change(val trigger: ChangeTrigger, val subject: String? = null) {
    val reason: String get() = subject?.let { "${trigger.name}: $it" } ?: trigger.name
}

/**
 * Single entry point after every change of a client's inputs or workflow state (architecture 4.1, INV-4, INV-7):
 * derives rule proposals (R-LIQ-01), recalculates the assessment (new version and audited decisions only if the
 * inputs changed) and revokes an active approval.
 */
@Component
class ChangeTracker(
    private val assessmentService: AssessmentService,
    private val ruleProposalService: RuleProposalService,
    private val approvalRevoker: ApprovalRevoker,
) {

    /** Must run in the command's transaction while it holds the client lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun onChange(clientId: UUID, change: Change): CurrentAssessment {
        val evaluated = assessmentService.evaluate(clientId)
        ruleProposalService.proposeMinimumLiquidity(clientId, evaluated.suggestedMinimumLiquidity)
        val (current, _) = assessmentService.recalculate(clientId)
        if (change.trigger.revokesApproval) approvalRevoker.revokeIfActive(clientId, change.reason)
        return current
    }
}
