package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.assessment.AssessmentService
import ch.treasurykompass.treasury.assessment.Change
import ch.treasurykompass.treasury.assessment.ChangeTracker
import ch.treasurykompass.treasury.assessment.ChangeTrigger
import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.client.ClientService
import ch.treasurykompass.treasury.common.AdvisorContext
import ch.treasurykompass.treasury.common.ProblemException
import ch.treasurykompass.treasury.common.ProblemType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** The advisor's rationale text, bound to the assessment version it was written for (UC-15). */
@Service
class RationaleService(
    private val clientService: ClientService,
    private val repository: RationaleRepository,
    private val assessmentService: AssessmentService,
    private val changeTracker: ChangeTracker,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun get(clientId: UUID): RationaleView {
        clientService.requireExists(clientId)
        val rationale = repository.findById(clientId).orElse(null)
            ?: return RationaleView(null, null, null, null, null, stale = false)
        return toView(rationale, assessmentService.current(clientId).version)
    }

    /** Saving revokes an active approval (trigger RATIONALE_CHANGED). */
    @Transactional
    fun save(clientId: UUID, text: String, assessmentVersion: Int, source: RationaleSource): RationaleView {
        clientService.lock(clientId)
        val current = assessmentService.current(clientId).version
        if (assessmentVersion != current) {
            throw ProblemException(
                ProblemType.STALE_ASSESSMENT,
                "Die Begründung bezieht sich auf Version $assessmentVersion, aktuell ist Version $current. Bitte einen neuen Entwurf erstellen.",
            )
        }
        val advisor = advisorContext.current()
        val now = Instant.now(clock)
        val rationale = repository.findById(clientId).orElse(null)?.apply {
            this.text = text
            this.assessmentVersion = assessmentVersion
            this.source = source
            savedAt = now
            savedBy = advisor.name
        } ?: repository.save(Rationale(clientId, assessmentVersion, text, source, now, advisor.name))

        val assessment = changeTracker.onChange(clientId, Change(ChangeTrigger.RATIONALE_CHANGED))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.RATIONALE_SAVED,
            message = if (source == RationaleSource.AGENT_DRAFT) "Begründung gespeichert (KI-Entwurf unverändert übernommen)" else "Begründung gespeichert",
            assessmentVersion = assessment.version,
            details = mapOf("source" to source, "length" to text.length),
        )
        return toView(rationale, assessment.version)
    }

    private fun toView(rationale: Rationale, currentVersion: Int) = RationaleView(
        text = rationale.text,
        assessmentVersion = rationale.assessmentVersion,
        source = rationale.source,
        savedAt = rationale.savedAt,
        savedBy = rationale.savedBy,
        stale = rationale.assessmentVersion != currentVersion,
    )
}
