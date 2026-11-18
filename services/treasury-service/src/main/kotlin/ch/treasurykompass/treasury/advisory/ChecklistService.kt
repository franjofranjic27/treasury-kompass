package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.assessment.AssessmentService
import ch.treasurykompass.treasury.assessment.Change
import ch.treasurykompass.treasury.assessment.ChangeTracker
import ch.treasurykompass.treasury.assessment.ChangeTrigger
import ch.treasurykompass.treasury.assessment.ChecklistItemDefinition
import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.client.ClientService
import ch.treasurykompass.treasury.common.AdvisorContext
import ch.treasurykompass.treasury.common.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Checklist ticks (R-CHK-01). The items come from the rules; only the ticks are stored here. */
@Service
class ChecklistService(
    private val clientService: ClientService,
    private val repository: ChecklistStateRepository,
    private val assessmentService: AssessmentService,
    private val changeTracker: ChangeTracker,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val clock: Clock,
) {

    /** Items of the current rule result with their stored ticks. */
    @Transactional(readOnly = true)
    fun items(clientId: UUID, definitions: List<ChecklistItemDefinition>): List<ChecklistItemView> {
        val states = repository.findByIdClientId(clientId).associateBy { it.id.itemId }
        return definitions.map { item ->
            val state = states[item.id]
            ChecklistItemView(item.id, item.text, item.shortText, item.legalBasis, state?.done ?: false, state?.changedAt)
        }
    }

    /** Ticks or reopens an item of the current checklist; any change revokes an active approval. */
    @Transactional
    fun set(clientId: UUID, itemId: String, done: Boolean): ChecklistItemView {
        clientService.lock(clientId)
        val item = assessmentService.current(clientId).body.checklist.firstOrNull { it.id == itemId }
            ?: throw NotFoundException("Checklistenpunkt $itemId ist nicht Teil der aktuellen Checkliste.")
        val advisor = advisorContext.current()
        val now = Instant.now(clock)
        val existing = repository.findById(ChecklistStateId(clientId, itemId)).orElse(null)
        if ((existing?.done ?: false) == done) {
            return ChecklistItemView(item.id, item.text, item.shortText, item.legalBasis, done, existing?.changedAt)
        }
        val state = existing?.apply {
            this.done = done
            changedAt = now
            changedBy = advisor.name
        } ?: repository.save(ChecklistState(ChecklistStateId(clientId, itemId), done, now, advisor.name))

        val assessment = changeTracker.onChange(clientId, Change(ChangeTrigger.CHECKLIST_CHANGED, itemId))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.CHECKLIST_CHANGED,
            message = "Checkliste ${if (done) "erledigt" else "wieder offen"}: ${item.shortText}",
            rule = null,
            assessmentVersion = assessment.version,
            details = mapOf("itemId" to itemId, "done" to done),
        )
        return ChecklistItemView(item.id, item.text, item.shortText, item.legalBasis, state.done, state.changedAt)
    }
}
