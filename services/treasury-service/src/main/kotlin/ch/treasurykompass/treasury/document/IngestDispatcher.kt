package ch.treasurykompass.treasury.document

import ch.treasurykompass.treasury.agentclient.AgentServiceClient
import ch.treasurykompass.treasury.agentclient.AgentUnavailableException
import ch.treasurykompass.treasury.agentclient.IngestRequest
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Hands documents to the agent-service after the upload transaction has committed (ADR-04): asynchronously, with
 * retries in [AgentServiceClient]. If the agent stays unreachable, the document is marked `FAILED` with
 * `INGEST_UNAVAILABLE`; «Erneut auslesen» starts a new attempt.
 */
@Component
class IngestDispatcher(
    private val agentClient: AgentServiceClient,
    private val documentService: DocumentService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onIngestRequested(event: IngestRequested) {
        try {
            agentClient.startIngest(
                IngestRequest(
                    ingestId = event.ingestId,
                    documentId = event.documentId,
                    clientId = event.clientId,
                    filename = event.filename,
                    sha256 = event.sha256,
                    sizeBytes = event.sizeBytes,
                    pageCount = event.pageCount,
                ),
            )
        } catch (ex: AgentUnavailableException) {
            log.debug("Ingest-Start fehlgeschlagen", ex)
            documentService.markIngestUnavailable(event.documentId, event.ingestId)
        }
    }

    /**
     * Best effort after the retries: if the purge fails, the chunks of the deleted document stay searchable for the
     * same client in the agent-service until they are removed there. The failure is logged as an error.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDocumentDeleted(event: DocumentDeleted) {
        try {
            agentClient.purgeDocument(event.documentId)
        } catch (ex: AgentUnavailableException) {
            log.error("Index von Dokument {} konnte im agent-service nicht gelöscht werden", event.documentId, ex)
        }
    }
}
