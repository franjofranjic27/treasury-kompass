package ch.treasurykompass.treasury.document

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

/** Ingest status (architecture 10.1). */
enum class DocumentStatus {
    UPLOADED,
    TEXT_EXTRACTED,
    INDEXED,
    EXTRACTED,
    FAILED;

    /** Documents in these states block the approval (blocker DOCUMENT_PROCESSING). */
    val processing: Boolean get() = this in setOf(UPLOADED, TEXT_EXTRACTED, INDEXED)

    /** Allowed callback transitions: forward one stage at a time, or to FAILED while processing. */
    fun canTransitionTo(target: DocumentStatus): Boolean = when (target) {
        TEXT_EXTRACTED -> this == UPLOADED
        INDEXED -> this == TEXT_EXTRACTED
        EXTRACTED -> this == INDEXED
        FAILED -> processing
        UPLOADED -> false
    }
}

enum class DocumentErrorCode {
    INGEST_UNAVAILABLE,
    CHECKSUM_MISMATCH,
    PDF_PARSE_ERROR,
    NO_TEXT_LAYER,
    EMBEDDING_FAILED,
    EXTRACTION_FAILED,
    LLM_REFUSAL,
    INTERNAL_ERROR,
}

/** Metadata of an uploaded PDF. The bytes live in [DocumentContent] so that lists never load them. */
@Entity
@Table(name = "document")
class Document(
    @Id
    val id: UUID,
    @Column(nullable = false, updatable = false)
    val clientId: UUID,
    @Column(nullable = false, updatable = false)
    val filename: String,
    @Column(nullable = false, updatable = false)
    val contentType: String,
    @Column(nullable = false, updatable = false)
    val sizeBytes: Long,
    @Column(nullable = false, updatable = false, columnDefinition = "char(64)")
    val sha256: String,
    @Column(nullable = false)
    var pageCount: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DocumentStatus,
    @Column(nullable = false)
    var ingestId: UUID,
    var chunkCount: Int?,
    @Enumerated(EnumType.STRING)
    var errorCode: DocumentErrorCode?,
    var errorMessage: String?,
    @Column(nullable = false, updatable = false)
    val uploadedAt: Instant,
    @Column(nullable = false, updatable = false)
    val uploadedBy: String,
    @Column(nullable = false)
    var statusChangedAt: Instant,
) {
    /** «Erneut auslesen»: back to UPLOADED with a new ingest ID, so that callbacks of the old run are rejected. */
    fun restartIngest(at: Instant): UUID {
        ingestId = UUID.randomUUID()
        status = DocumentStatus.UPLOADED
        errorCode = null
        errorMessage = null
        statusChangedAt = at
        return ingestId
    }

    fun fail(code: DocumentErrorCode, message: String?, at: Instant) {
        status = DocumentStatus.FAILED
        errorCode = code
        errorMessage = message?.take(MAX_ERROR_MESSAGE)
        statusChangedAt = at
    }

    private companion object {
        const val MAX_ERROR_MESSAGE = 500
    }
}

@Entity
@Table(name = "document_content")
class DocumentContent(
    @Id
    val documentId: UUID,
    // Plain bytea: no @Lob, which Hibernate would map to an oid (architecture 5.1).
    @Column(nullable = false, updatable = false)
    val bytes: ByteArray,
)

interface DocumentRepository : JpaRepository<Document, UUID> {
    fun findByClientIdOrderByUploadedAtDesc(clientId: UUID): List<Document>

    fun findByIdAndClientId(id: UUID, clientId: UUID): Document?

    fun existsByClientIdAndSha256(clientId: UUID, sha256: String): Boolean

    fun countByClientId(clientId: UUID): Long

    @Query("select d.clientId from Document d where d.id = :id")
    fun findClientId(id: UUID): UUID?
}

interface DocumentContentRepository : JpaRepository<DocumentContent, UUID>
