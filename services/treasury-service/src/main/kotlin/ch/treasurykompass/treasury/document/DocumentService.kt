package ch.treasurykompass.treasury.document

import ch.treasurykompass.treasury.assessment.Change
import ch.treasurykompass.treasury.assessment.ChangeTracker
import ch.treasurykompass.treasury.assessment.ChangeTrigger
import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.client.ClientService
import ch.treasurykompass.treasury.common.AdvisorContext
import ch.treasurykompass.treasury.common.NotFoundException
import ch.treasurykompass.treasury.common.ProblemException
import ch.treasurykompass.treasury.common.ProblemType
import ch.treasurykompass.treasury.common.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

data class DocumentView(
    val id: UUID,
    val clientId: UUID,
    val filename: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String,
    val pageCount: Int?,
    val status: DocumentStatus,
    val chunkCount: Int?,
    val errorCode: DocumentErrorCode?,
    val errorMessage: String?,
    val uploadedAt: Instant,
    val uploadedBy: String,
    val statusChangedAt: Instant,
)

/** PDF bytes with the metadata needed for delivery. */
class DocumentFile(val document: Document, val bytes: ByteArray)

/** Status callback of the agent-service (`POST /internal/documents/{id}/status`). */
data class StatusUpdate(
    val ingestId: UUID,
    val status: DocumentStatus,
    val pageCount: Int?,
    val chunkCount: Int?,
    val errorCode: DocumentErrorCode?,
    val errorMessage: String?,
)

/** Published inside the upload transaction; the ingest starts only after commit ([IngestDispatcher]). */
data class IngestRequested(
    val documentId: UUID,
    val ingestId: UUID,
    val clientId: UUID,
    val filename: String,
    val sha256: String,
    val sizeBytes: Long,
    val pageCount: Int,
)

data class DocumentDeleted(val documentId: UUID)

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val contentRepository: DocumentContentRepository,
    private val pdfValidator: PdfValidator,
    private val clientService: ClientService,
    private val changeTracker: ChangeTracker,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val events: ApplicationEventPublisher,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun upload(clientId: UUID, originalFilename: String?, bytes: ByteArray): DocumentView {
        clientService.lock(clientId)
        if (bytes.isEmpty()) throw ValidationException("file", "Datei fehlt oder ist leer")
        val pdf = pdfValidator.validate(bytes)
        val sha256 = sha256(bytes)
        if (documentRepository.existsByClientIdAndSha256(clientId, sha256)) {
            throw ProblemException(ProblemType.DUPLICATE_DOCUMENT, "Diese Datei wurde für diesen Kunden bereits hochgeladen.")
        }
        val advisor = advisorContext.current()
        val now = Instant.now(clock)
        val document = documentRepository.save(
            Document(
                id = UUID.randomUUID(),
                clientId = clientId,
                filename = sanitizeFilename(originalFilename),
                contentType = PDF_CONTENT_TYPE,
                sizeBytes = bytes.size.toLong(),
                sha256 = sha256,
                pageCount = pdf.pageCount,
                status = DocumentStatus.UPLOADED,
                ingestId = UUID.randomUUID(),
                chunkCount = null,
                errorCode = null,
                errorMessage = null,
                uploadedAt = now,
                uploadedBy = advisor.name,
                statusChangedAt = now,
            ),
        )
        contentRepository.save(DocumentContent(document.id, bytes))
        documentRepository.flush()

        val assessment = changeTracker.onChange(clientId, Change(ChangeTrigger.DOCUMENT_UPLOADED, document.filename))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.DOCUMENT_UPLOADED,
            message = "Datei hochgeladen: ${document.filename} (${document.pageCount} Seiten)",
            assessmentVersion = assessment.version,
            details = mapOf("documentId" to document.id, "filename" to document.filename, "sha256" to sha256, "sizeBytes" to document.sizeBytes),
        )
        requestIngest(document)
        return toView(document)
    }

    @Transactional(readOnly = true)
    fun list(clientId: UUID): List<DocumentView> {
        clientService.requireExists(clientId)
        return documentRepository.findByClientIdOrderByUploadedAtDesc(clientId).map(::toView)
    }

    @Transactional(readOnly = true)
    fun get(clientId: UUID, documentId: UUID): DocumentView = toView(requireOfClient(clientId, documentId))

    @Transactional(readOnly = true)
    fun content(clientId: UUID, documentId: UUID): DocumentFile = file(requireOfClient(clientId, documentId))

    /** For the agent-service: bytes without client context (the key protects the endpoint). */
    @Transactional(readOnly = true)
    fun content(documentId: UUID): DocumentFile = file(require(documentId))

    @Transactional(readOnly = true)
    fun requireOfClient(clientId: UUID, documentId: UUID): Document =
        documentRepository.findByIdAndClientId(documentId, clientId) ?: throw notFound(documentId)

    @Transactional(readOnly = true)
    fun findOfClient(clientId: UUID, documentId: UUID): Document? = documentRepository.findByIdAndClientId(documentId, clientId)

    @Transactional(readOnly = true)
    fun processing(clientId: UUID): List<Document> =
        documentRepository.findByClientIdOrderByUploadedAtDesc(clientId).filter { it.status.processing }

    @Transactional(readOnly = true)
    fun count(clientId: UUID): Long = documentRepository.countByClientId(clientId)

    /** «Erneut auslesen»: new ingest ID, status UPLOADED, ingest starts after commit. */
    @Transactional
    fun reprocess(clientId: UUID, documentId: UUID): DocumentView {
        clientService.lock(clientId)
        val document = requireOfClient(clientId, documentId)
        val previous = document.status
        document.restartIngest(Instant.now(clock))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisorContext.current()),
            type = AuditEventType.DOCUMENT_STATUS_CHANGED,
            message = "Erneut auslesen gestartet: ${document.filename}",
            details = mapOf("documentId" to document.id, "old" to previous, "new" to document.status, "ingestId" to document.ingestId),
        )
        requestIngest(document)
        return toView(document)
    }

    /** Deletes bytes and metadata; answers and proposals stay (their citations keep the document name). */
    @Transactional
    fun delete(clientId: UUID, documentId: UUID) {
        clientService.lock(clientId)
        val document = requireOfClient(clientId, documentId)
        contentRepository.deleteById(document.id)
        documentRepository.delete(document)
        documentRepository.flush()
        val assessment = changeTracker.onChange(clientId, Change(ChangeTrigger.DOCUMENT_DELETED, document.filename))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisorContext.current()),
            type = AuditEventType.DOCUMENT_DELETED,
            message = "Dokument gelöscht: ${document.filename}",
            assessmentVersion = assessment.version,
            details = mapOf("documentId" to document.id, "filename" to document.filename),
        )
        events.publishEvent(DocumentDeleted(document.id))
    }

    /**
     * Applies an ingest status callback. A repeated callback with the current status is accepted unchanged,
     * because the agent-service retries on timeouts.
     */
    @Transactional
    fun updateStatus(documentId: UUID, update: StatusUpdate) {
        // Lock first, then load: the entity must not be read before the client lock is held.
        val clientId = documentRepository.findClientId(documentId) ?: throw notFound(documentId)
        clientService.lock(clientId)
        val document = require(documentId)
        if (update.ingestId != document.ingestId) {
            throw ProblemException(ProblemType.STALE_INGEST, "Die Verarbeitung ${update.ingestId} ist nicht mehr aktuell.")
        }
        if (update.status == document.status) return
        if (!document.status.canTransitionTo(update.status)) {
            throw ProblemException(
                ProblemType.INVALID_STATUS_TRANSITION,
                "Statuswechsel ${document.status} → ${update.status} ist nicht erlaubt.",
            )
        }
        val previous = document.status
        val now = Instant.now(clock)
        when (update.status) {
            DocumentStatus.FAILED -> document.fail(
                update.errorCode ?: throw ValidationException("errorCode", "ist bei Status FAILED Pflicht"),
                update.errorMessage,
                now,
            )
            else -> {
                document.status = update.status
                document.statusChangedAt = now
                update.pageCount?.let { document.pageCount = it }
                update.chunkCount?.let { document.chunkCount = it }
            }
        }
        auditService.record(
            clientId = clientId,
            actor = Actor.SYSTEM,
            type = AuditEventType.DOCUMENT_STATUS_CHANGED,
            message = statusMessage(document),
            details = mapOf(
                "documentId" to document.id,
                "ingestId" to document.ingestId,
                "old" to previous,
                "new" to document.status,
                "errorCode" to document.errorCode,
            ),
        )
    }

    /** Called when the agent-service stayed unreachable; ignored if a newer ingest has started meanwhile. */
    @Transactional
    fun markIngestUnavailable(documentId: UUID, ingestId: UUID) {
        val clientId = documentRepository.findClientId(documentId) ?: return
        clientService.lock(clientId)
        val document = require(documentId)
        if (document.ingestId != ingestId || document.status != DocumentStatus.UPLOADED) return
        document.fail(DocumentErrorCode.INGEST_UNAVAILABLE, "Die Verarbeitung konnte nicht gestartet werden (agent-service nicht erreichbar).", Instant.now(clock))
        auditService.record(
            clientId = document.clientId,
            actor = Actor.SYSTEM,
            type = AuditEventType.DOCUMENT_STATUS_CHANGED,
            message = statusMessage(document),
            details = mapOf("documentId" to document.id, "ingestId" to ingestId, "new" to document.status, "errorCode" to document.errorCode),
        )
        log.warn("Ingest {} für Dokument {} nicht gestartet: agent-service nicht erreichbar", ingestId, documentId)
    }

    private fun requestIngest(document: Document) {
        events.publishEvent(
            IngestRequested(
                documentId = document.id,
                ingestId = document.ingestId,
                clientId = document.clientId,
                filename = document.filename,
                sha256 = document.sha256,
                sizeBytes = document.sizeBytes,
                pageCount = document.pageCount,
            ),
        )
    }

    private fun statusMessage(document: Document): String = when (document.status) {
        DocumentStatus.UPLOADED -> "Datei hochgeladen: ${document.filename}"
        DocumentStatus.TEXT_EXTRACTED -> "Text erkannt: ${document.filename}, ${document.pageCount} Seiten"
        DocumentStatus.INDEXED -> "Unterlagen indexiert: ${document.filename}, ${document.chunkCount ?: 0} Abschnitte"
        DocumentStatus.EXTRACTED -> "${document.filename} ausgelesen, Werte mit Fundstellen verknüpft"
        DocumentStatus.FAILED -> "Verarbeitung fehlgeschlagen: ${document.filename} (${document.errorCode})"
    }

    private fun file(document: Document): DocumentFile {
        val content = contentRepository.findById(document.id).orElseThrow { notFound(document.id) }
        return DocumentFile(document, content.bytes)
    }

    private fun require(documentId: UUID): Document = documentRepository.findById(documentId).orElseThrow { notFound(documentId) }

    private fun notFound(documentId: UUID) = NotFoundException("Dokument $documentId existiert nicht.")

    private fun sha256(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun toView(document: Document) = DocumentView(
        id = document.id,
        clientId = document.clientId,
        filename = document.filename,
        contentType = document.contentType,
        sizeBytes = document.sizeBytes,
        sha256 = document.sha256,
        pageCount = document.pageCount,
        status = document.status,
        chunkCount = document.chunkCount,
        errorCode = document.errorCode,
        errorMessage = document.errorMessage,
        uploadedAt = document.uploadedAt,
        uploadedBy = document.uploadedBy,
        statusChangedAt = document.statusChangedAt,
    )

    companion object {
        const val PDF_CONTENT_TYPE = "application/pdf"
        private const val MAX_FILENAME = 255
        private const val FALLBACK_FILENAME = "dokument.pdf"

        /** The name is for display only: path parts and control characters removed, at most 255 characters. */
        fun sanitizeFilename(original: String?): String = original.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot { it.isISOControl() }
            .trim()
            .take(MAX_FILENAME)
            .ifBlank { FALLBACK_FILENAME }
    }
}
