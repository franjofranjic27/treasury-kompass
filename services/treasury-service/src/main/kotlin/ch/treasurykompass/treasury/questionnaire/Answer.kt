package ch.treasurykompass.treasury.questionnaire

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
import org.springframework.data.jpa.repository.Query
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class AnswerId(
    @Column(name = "client_id")
    val clientId: UUID,
    @Column(name = "question_key")
    val questionKey: String,
) : Serializable

/** Current answer per question. [value] holds the JSON literal (number, string or boolean). */
@Entity
@Table(name = "answer")
class Answer(
    @EmbeddedId
    val id: AnswerId,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var value: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AnswerStatus,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: AnswerSource,
    var proposalId: UUID?,
    @Column(nullable = false)
    var updatedAt: Instant,
    var confirmedAt: Instant?,
    var confirmedBy: String?,
) {
    val questionKey: String get() = id.questionKey

    fun confirm(at: Instant, by: String) {
        status = AnswerStatus.CONFIRMED
        confirmedAt = at
        confirmedBy = by
    }
}

@Entity
@Table(name = "answer_proposal")
class AnswerProposal(
    @Id
    val id: UUID,
    @Column(nullable = false, updatable = false)
    val clientId: UUID,
    @Column(nullable = false, updatable = false)
    val questionKey: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val value: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val source: ProposalSource,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProposalStatus,
    @Enumerated(EnumType.STRING)
    var rejectionReason: RejectionReason?,
    val documentId: UUID?,
    val documentName: String?,
    val page: Int?,
    val locationLabel: String?,
    val excerpt: String?,
    val citationVerified: Boolean?,
    @Enumerated(EnumType.STRING)
    val confidence: Confidence?,
    val rationale: String?,
    val conversationId: UUID?,
    @Column(nullable = false, updatable = false)
    val createdAt: Instant,
    var decidedAt: Instant?,
    var decidedBy: String?,
) {
    val hasCitation: Boolean get() = documentName != null

    fun decide(newStatus: ProposalStatus, at: Instant, by: String) {
        status = newStatus
        decidedAt = at
        decidedBy = by
    }
}

interface AnswerRepository : JpaRepository<Answer, AnswerId> {
    fun findByIdClientId(clientId: UUID): List<Answer>
}

interface AnswerProposalRepository : JpaRepository<AnswerProposal, UUID> {

    fun findByIdAndClientId(id: UUID, clientId: UUID): AnswerProposal?

    fun findByClientIdOrderByCreatedAtDesc(clientId: UUID): List<AnswerProposal>

    fun findByClientIdAndStatusOrderByCreatedAtDesc(clientId: UUID, status: ProposalStatus): List<AnswerProposal>

    fun findByClientIdAndQuestionKeyAndStatusIn(clientId: UUID, questionKey: String, statuses: Collection<ProposalStatus>): List<AnswerProposal>

    fun findByIdIn(ids: Collection<UUID>): List<AnswerProposal>

    @Query(
        "select p from AnswerProposal p where p.clientId = :clientId and p.questionKey = :questionKey " +
            "and p.source = :source order by p.createdAt desc limit 1",
    )
    fun findLatest(clientId: UUID, questionKey: String, source: ProposalSource): AnswerProposal?

    fun countByClientIdAndStatus(clientId: UUID, status: ProposalStatus): Long
}
