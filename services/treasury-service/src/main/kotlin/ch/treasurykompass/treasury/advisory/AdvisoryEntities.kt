package ch.treasurykompass.treasury.advisory

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class HedgeSettingId(
    @Column(name = "client_id")
    val clientId: UUID,
    @Column(name = "currency", columnDefinition = "char(3)")
    val currency: String,
) : Serializable

/** Advisor overrides per currency; `null` fields mean "rule default". */
@Entity
@Table(name = "hedge_setting")
class HedgeSetting(
    @EmbeddedId
    val id: HedgeSettingId,
    var startRatio: Int?,
    @Column(nullable = false)
    var stepDown: Int,
    var collarEnabled: Boolean?,
    @Column(nullable = false)
    var updatedAt: Instant,
)

interface HedgeSettingRepository : JpaRepository<HedgeSetting, HedgeSettingId> {
    fun findByIdClientId(clientId: UUID): List<HedgeSetting>
}

@Embeddable
data class ChecklistStateId(
    @Column(name = "client_id")
    val clientId: UUID,
    @Column(name = "item_id")
    val itemId: String,
) : Serializable

/** Stored tick per checklist item. Ticks of items that drop out of the checklist are kept and count again when they return. */
@Entity
@Table(name = "checklist_state")
class ChecklistState(
    @EmbeddedId
    val id: ChecklistStateId,
    @Column(nullable = false)
    var done: Boolean,
    @Column(nullable = false)
    var changedAt: Instant,
    @Column(nullable = false)
    var changedBy: String,
)

interface ChecklistStateRepository : JpaRepository<ChecklistState, ChecklistStateId> {
    fun findByIdClientId(clientId: UUID): List<ChecklistState>
}

enum class RationaleSource { AGENT_DRAFT, ADVISOR }

@Entity
@Table(name = "rationale")
class Rationale(
    @Id
    val clientId: UUID,
    @Column(nullable = false)
    var assessmentVersion: Int,
    @Column(nullable = false)
    var text: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: RationaleSource,
    @Column(nullable = false)
    var savedAt: Instant,
    @Column(nullable = false)
    var savedBy: String,
)

interface RationaleRepository : JpaRepository<Rationale, UUID>

enum class RevocationType { REVOKED_BY_CHANGE, WITHDRAWN }

/** Approval with the protocol frozen at approval time. Kept as history after revocation. */
@Entity
@Table(name = "approval")
class Approval(
    @Id
    val id: UUID,
    @Column(nullable = false, updatable = false)
    val clientId: UUID,
    @Column(nullable = false, updatable = false)
    val assessmentVersion: Int,
    @Column(nullable = false, updatable = false)
    val approvedAt: Instant,
    @Column(nullable = false, updatable = false)
    val approvedBy: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val protocol: String,
    @Column(nullable = false, updatable = false)
    val protocolText: String,
    var revokedAt: Instant? = null,
    var revokedBy: String? = null,
    @Enumerated(EnumType.STRING)
    var revocationType: RevocationType? = null,
    var revocationReason: String? = null,
) {
    val active: Boolean get() = revokedAt == null

    fun revoke(type: RevocationType, at: Instant, by: String, reason: String) {
        check(active) { "Freigabe $id ist bereits aufgehoben" }
        revocationType = type
        revokedAt = at
        revokedBy = by
        revocationReason = reason.take(MAX_REASON)
    }

    private companion object {
        const val MAX_REASON = 300
    }
}

interface ApprovalRepository : JpaRepository<Approval, UUID> {
    fun findByClientIdAndRevokedAtIsNull(clientId: UUID): Approval?

    fun findByClientIdOrderByApprovedAtDesc(clientId: UUID): List<Approval>
}
