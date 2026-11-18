package ch.treasurykompass.treasury.audit

import ch.treasurykompass.treasury.common.Advisor
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Who caused an audit event. */
data class Actor(val type: ActorType, val name: String) {
    companion object {
        val SYSTEM = Actor(ActorType.SYSTEM, ActorType.SYSTEM.defaultName!!)
        val RULE = Actor(ActorType.RULE, ActorType.RULE.defaultName!!)
        val AGENT = Actor(ActorType.AGENT, ActorType.AGENT.defaultName!!)

        fun advisor(advisor: Advisor) = Actor(ActorType.ADVISOR, advisor.name.take(MAX_ACTOR_NAME))

        private const val MAX_ACTOR_NAME = 100
    }
}

/** Rule ID and legal basis of a rule decision (INV-7). */
data class RuleReference(val ruleId: String, val legalBasis: String?)

@Service
class AuditService(
    private val repository: AuditEventRepository,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) {

    /** Must run inside the transaction of the change it documents, so that change and audit commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun record(
        clientId: UUID,
        actor: Actor,
        type: AuditEventType,
        message: String,
        rule: RuleReference? = null,
        assessmentVersion: Int? = null,
        details: Map<String, Any?>? = null,
    ): AuditEvent = repository.save(
        AuditEvent(
            clientId = clientId,
            occurredAt = Instant.now(clock),
            actorType = actor.type,
            actorName = actor.name,
            eventType = type,
            message = message,
            ruleId = rule?.ruleId,
            legalBasis = rule?.legalBasis,
            assessmentVersion = assessmentVersion,
            details = details?.let(jsonMapper::writeValueAsString),
        ),
    )

    @Transactional(readOnly = true)
    fun page(clientId: UUID, limit: Int, before: Long?): AuditEventPage {
        // One extra row tells whether an older page exists without a count query.
        val rows = if (before == null) {
            repository.findByClientIdOrderByIdDesc(clientId, Limit.of(limit + 1))
        } else {
            repository.findByClientIdAndIdLessThanOrderByIdDesc(clientId, before, Limit.of(limit + 1))
        }
        val items = rows.take(limit).map(::toView)
        return AuditEventPage(items, if (rows.size > limit) items.last().id else null)
    }

    private fun toView(event: AuditEvent) = AuditEventView(
        id = requireNotNull(event.id),
        clientId = event.clientId,
        occurredAt = event.occurredAt,
        actorType = event.actorType,
        actorName = event.actorName,
        eventType = event.eventType,
        message = event.message,
        ruleId = event.ruleId,
        legalBasis = event.legalBasis,
        assessmentVersion = event.assessmentVersion,
        details = event.details?.let(jsonMapper::readTree),
    )
}
