package ch.treasurykompass.treasury.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

enum class ActorType(val defaultName: String?) {
    ADVISOR(null),
    SYSTEM("System"),
    RULE("Regelwerk"),
    AGENT("KI-Assistent"),
}

/** Event types of the OpenAPI contract (`AuditEventType`). */
enum class AuditEventType {
    CLIENT_CREATED,
    CLIENT_UPDATED,
    DOCUMENT_UPLOADED,
    DOCUMENT_STATUS_CHANGED,
    DOCUMENT_DELETED,
    ANSWER_SET,
    ANSWER_CONFIRMED,
    PROPOSAL_CREATED,
    PROPOSAL_ACCEPTED,
    PROPOSAL_REJECTED,
    RULE_DECISION,
    HEDGE_SETTINGS_CHANGED,
    CHECKLIST_CHANGED,
    RATIONALE_SAVED,
    APPROVED,
    APPROVAL_REVOKED,
    APPROVAL_WITHDRAWN,
}

/** Append-only audit entry (INV-7). The database trigger rejects UPDATE and DELETE. */
@Entity
@Immutable
@Table(name = "audit_event")
class AuditEvent(
    @Column(nullable = false, updatable = false)
    val clientId: UUID,
    @Column(nullable = false, updatable = false)
    val occurredAt: Instant,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val actorType: ActorType,
    @Column(nullable = false, updatable = false)
    val actorName: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val eventType: AuditEventType,
    @Column(nullable = false, updatable = false)
    val message: String,
    @Column(updatable = false)
    val ruleId: String?,
    @Column(updatable = false)
    val legalBasis: String?,
    @Column(updatable = false)
    val assessmentVersion: Int?,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    val details: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
