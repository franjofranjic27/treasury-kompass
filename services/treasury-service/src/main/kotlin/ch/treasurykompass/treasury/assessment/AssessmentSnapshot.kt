package ch.treasurykompass.treasury.assessment

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class AssessmentSnapshotId(
    @Column(name = "client_id")
    val clientId: UUID,
    @Column(name = "version")
    val version: Int,
) : Serializable

/** Immutable rule result for one version of the inputs (ADR-08). */
@Entity
@Immutable
@Table(name = "assessment_snapshot")
class AssessmentSnapshot(
    @EmbeddedId
    val id: AssessmentSnapshotId,
    @Column(nullable = false, columnDefinition = "char(64)")
    val inputsHash: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    val result: String,
    @Column(nullable = false)
    val createdAt: Instant,
)

interface AssessmentSnapshotRepository : JpaRepository<AssessmentSnapshot, AssessmentSnapshotId> {

    @Query("select s from AssessmentSnapshot s where s.id.clientId = :clientId order by s.id.version desc limit 1")
    fun findLatest(clientId: UUID): AssessmentSnapshot?
}
