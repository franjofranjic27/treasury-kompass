package ch.treasurykompass.treasury.questionnaire

import ch.treasurykompass.treasury.assessment.AssessmentService
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
import ch.treasurykompass.treasury.document.DocumentService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Citation as sent by the agent-service; the document name is resolved here. */
data class CitationInput(val documentId: UUID?, val page: Int, val locationLabel: String?, val excerpt: String, val verified: Boolean)

data class ProposalInput(
    val questionKey: String,
    val value: JsonNode?,
    val confidence: Confidence,
    val rationale: String?,
    val citation: CitationInput?,
)

/** Proposals of one extraction (source DOCUMENT) or one chat turn (source AGENT). */
data class ProposalBatch(
    val source: ProposalSource,
    val documentId: UUID?,
    val ingestId: UUID?,
    val conversationId: UUID?,
    val proposals: List<ProposalInput>,
)

/** Proposal commands from the agent-service and the advisor's decisions on open proposals (INV-5). */
@Service
class ProposalService(
    private val clientService: ClientService,
    private val documentService: DocumentService,
    private val proposalRepository: AnswerProposalRepository,
    private val answerRepository: AnswerRepository,
    private val processor: ProposalProcessor,
    private val assessmentService: AssessmentService,
    private val changeTracker: ChangeTracker,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val codec: AnswerValueCodec,
    private val views: QuestionnaireViews,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Processes each proposal on its own; a rejected entry does not fail the batch. */
    @Transactional
    fun receive(clientId: UUID, batch: ProposalBatch): List<ProposalResult> {
        require(batch.source != ProposalSource.RULE) { "RULE-Vorschläge erzeugt nur der treasury-service" }
        clientService.lock(clientId)
        val batchDocument = batch.documentId?.let { documentService.requireOfClient(clientId, it) }
        if (batch.source == ProposalSource.DOCUMENT && batchDocument != null && batch.ingestId != batchDocument.ingestId) {
            throw ProblemException(ProblemType.STALE_INGEST, "Die Verarbeitung ${batch.ingestId} ist nicht mehr aktuell.")
        }
        val context = assessmentService.current(clientId).questionContext
        val results = batch.proposals.map { input ->
            processor.process(
                clientId,
                ProposalCommand(
                    questionKey = input.questionKey,
                    value = input.value,
                    source = batch.source,
                    confidence = input.confidence,
                    rationale = input.rationale,
                    citation = input.citation?.let { citation(clientId, it, batch.documentId) },
                    conversationId = batch.conversationId,
                ),
                context,
            )
        }
        val changedKeys = results.filter { it.answerChanged }.map { it.questionKey }
        if (changedKeys.isNotEmpty()) {
            changeTracker.onChange(clientId, Change(ChangeTrigger.PROPOSAL_APPLIED, changedKeys.joinToString(", ")))
        }
        return results
    }

    @Transactional(readOnly = true)
    fun list(clientId: UUID, status: ProposalStatus?): List<AnswerProposalView> {
        clientService.requireExists(clientId)
        return proposalRepository.findByClientIdAndStatusOrderByCreatedAtDesc(clientId, status ?: ProposalStatus.OPEN)
            .map(views::proposal)
    }

    /** The advisor takes over an open proposal: the answer becomes CONFIRMED with the proposal's value. */
    @Transactional
    fun accept(clientId: UUID, proposalId: UUID): AnswerView {
        clientService.lock(clientId)
        val proposal = requireOpen(clientId, proposalId)
        val question = QuestionCatalog.require(proposal.questionKey)
        if (!question.isVisible(assessmentService.current(clientId).questionContext)) {
            throw ProblemException(ProblemType.QUESTION_NOT_APPLICABLE, "Die Frage «${question.shortLabel}» ist derzeit nicht anwendbar.")
        }
        val advisor = advisorContext.current()
        val now = Instant.now(clock)
        val value = codec.decode(question, proposal.value)
        val existing = answerRepository.findById(AnswerId(clientId, question.key)).orElse(null)
        val previous = existing?.let { codec.decode(question, it.value) }
        val answer = existing?.apply {
            this.value = codec.encode(value)
            source = proposal.source.toAnswerSource()
            this.proposalId = proposal.id
            updatedAt = now
            confirm(now, advisor.name)
        } ?: answerRepository.save(
            Answer(
                id = AnswerId(clientId, question.key),
                value = codec.encode(value),
                status = AnswerStatus.CONFIRMED,
                source = proposal.source.toAnswerSource(),
                proposalId = proposal.id,
                updatedAt = now,
                confirmedAt = now,
                confirmedBy = advisor.name,
            ),
        )
        proposal.decide(ProposalStatus.ACCEPTED, now, advisor.name)

        val assessment = changeTracker.onChange(clientId, Change(ChangeTrigger.PROPOSAL_ACCEPTED, question.key))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.PROPOSAL_ACCEPTED,
            message = "Vorschlag übernommen: ${question.describeChange(previous, value)}",
            assessmentVersion = assessment.version,
            details = mapOf("key" to question.key, "proposalId" to proposal.id, "old" to previous?.raw, "new" to value.raw),
        )
        return views.answer(answer, proposal)
    }

    /** The advisor discards an open proposal; the answer stays as it is. */
    @Transactional
    fun reject(clientId: UUID, proposalId: UUID): AnswerProposalView {
        clientService.lock(clientId)
        val proposal = requireOpen(clientId, proposalId)
        val question = QuestionCatalog.require(proposal.questionKey)
        val advisor = advisorContext.current()
        proposal.decide(ProposalStatus.REJECTED, Instant.now(clock), advisor.name)
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.PROPOSAL_REJECTED,
            message = "Vorschlag verworfen: ${question.shortLabel} ${question.display(codec.decode(question, proposal.value))}",
            assessmentVersion = assessmentService.current(clientId).version,
            details = mapOf("key" to question.key, "proposalId" to proposal.id),
        )
        return views.proposal(proposal)
    }

    private fun requireOpen(clientId: UUID, proposalId: UUID): AnswerProposal {
        val proposal = proposalRepository.findByIdAndClientId(proposalId, clientId)
            ?: throw NotFoundException("Vorschlag $proposalId existiert nicht.")
        if (proposal.status != ProposalStatus.OPEN) {
            throw ProblemException(ProblemType.PROPOSAL_NOT_OPEN, "Der Vorschlag ist nicht mehr offen (Status ${proposal.status}).")
        }
        return proposal
    }

    /**
     * Resolves the document name of a citation. Citations pointing to a document of another client are dropped
     * (client separation, INV-6); the proposal itself is still processed.
     */
    private fun citation(clientId: UUID, input: CitationInput, batchDocumentId: UUID?): CitationData? {
        val documentId = input.documentId ?: batchDocumentId
        val document = documentId?.let { documentService.findOfClient(clientId, it) }
        if (documentId != null && document == null) {
            log.warn("Fundstelle verweist auf ein unbekanntes Dokument {} (Kunde {}), wird verworfen", documentId, clientId)
            return null
        }
        return CitationData(
            documentId = document?.id,
            documentName = document?.filename ?: "Unterlagen",
            page = input.page,
            locationLabel = input.locationLabel,
            excerpt = input.excerpt,
            verified = input.verified,
        )
    }
}
