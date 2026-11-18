package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Revokes an active approval after a change (R-APP-02, INV-4). Used only by the change tracking. */
@Component
class ApprovalRevoker(
    private val approvalRepository: ApprovalRepository,
    private val auditService: AuditService,
    private val clock: Clock,
) {

    /** @param reason trigger and subject, e.g. `ANSWER_CHANGED: goals.policyMaxHedgeRatio` */
    @Transactional(propagation = Propagation.MANDATORY)
    fun revokeIfActive(clientId: UUID, reason: String) {
        val approval = approvalRepository.findByClientIdAndRevokedAtIsNull(clientId) ?: return
        approval.revoke(RevocationType.REVOKED_BY_CHANGE, Instant.now(clock), Actor.SYSTEM.name, reason)
        auditService.record(
            clientId = clientId,
            actor = Actor.SYSTEM,
            type = AuditEventType.APPROVAL_REVOKED,
            message = "Freigabe aufgehoben, weil Angaben geändert wurden",
            assessmentVersion = approval.assessmentVersion,
            details = mapOf("approvalId" to approval.id, "reason" to approval.revocationReason),
        )
    }
}
