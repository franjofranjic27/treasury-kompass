package ch.treasurykompass.treasury.questionnaire

import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.audit.RuleReference
import ch.treasurykompass.treasury.common.ValidationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Fundstelle of a proposal. [documentName] is kept even if the document is deleted later. */
data class CitationData(
    val documentId: UUID?,
    val documentName: String,
    val page: Int?,
    val locationLabel: String?,
    val excerpt: String?,
    val verified: Boolean,
)

/** One proposal as received (value still unvalidated) or derived by a rule. */
data class ProposalCommand(
    val questionKey: String,
    val value: JsonNode?,
    val source: ProposalSource,
    val confidence: Confidence?,
    val rationale: String?,
    val citation: CitationData?,
    val conversationId: UUID? = null,
    val rule: RuleReference? = null,
)

data class ProposalResult(
    val questionKey: String,
    val proposalId: UUID?,
    val outcome: ProposalOutcome,
    val reason: RejectionReason?,
    /** The stored answer was written (new or changed value); the caller must run the change tracking. */
    val answerChanged: Boolean,
)

/**
 * Applies one proposal according to the answer rules (architecture 6, INV-5): proposals never confirm an answer
 * and never overwrite a confirmed one. Does not run change tracking; callers do that once per command.
 */
@Component
class ProposalProcessor(
    private val answerRepository: AnswerRepository,
    private val proposalRepository: AnswerProposalRepository,
    private val codec: AnswerValueCodec,
    private val auditService: AuditService,
    private val clock: Clock,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun process(clientId: UUID, command: ProposalCommand, context: QuestionContext): ProposalResult {
        val question = QuestionCatalog.find(command.questionKey)
            ?: return rejectWithoutRecord(clientId, command, RejectionReason.UNKNOWN_KEY, "unbekannte Frage")
        val value = try {
            question.parse(command.value)
        } catch (ex: ValidationException) {
            return rejectWithoutRecord(clientId, command, RejectionReason.INVALID_VALUE, "ungültiger Wert, ${ex.errors.first().message}")
        }
        if (command.source == ProposalSource.DOCUMENT && !question.extractable) {
            return reject(clientId, command, question, value, RejectionReason.NOT_EXTRACTABLE, "nicht aus Unterlagen ableitbar")
        }
        if (!question.isVisible(context)) {
            return reject(clientId, command, question, value, RejectionReason.NOT_APPLICABLE, "Frage derzeit nicht anwendbar")
        }

        val now = Instant.now(clock)
        val answer = answerRepository.findById(AnswerId(clientId, question.key)).orElse(null)
        return when {
            answer == null || answer.status == AnswerStatus.PROPOSED -> apply(clientId, command, question, value, answer, now)
            codec.decode(question, answer.value) == value -> keepConfirmed(clientId, command, question, value, now)
            else -> openForAdvisor(clientId, command, question, value, answer, now)
        }
    }

    private fun apply(
        clientId: UUID,
        command: ProposalCommand,
        question: QuestionDefinition,
        value: AnswerValue,
        answer: Answer?,
        now: Instant,
    ): ProposalResult {
        supersede(clientId, question.key, setOf(ProposalStatus.APPLIED, ProposalStatus.OPEN), now)
        val proposal = save(clientId, command, question, value, ProposalStatus.APPLIED, now)
        val previousValue = answer?.let { codec.decode(question, it.value) }
        if (answer == null) {
            answerRepository.save(
                Answer(
                    id = AnswerId(clientId, question.key),
                    value = codec.encode(value),
                    status = AnswerStatus.PROPOSED,
                    source = command.source.toAnswerSource(),
                    proposalId = proposal.id,
                    updatedAt = now,
                    confirmedAt = null,
                    confirmedBy = null,
                ),
            )
        } else {
            answer.value = codec.encode(value)
            answer.source = command.source.toAnswerSource()
            answer.proposalId = proposal.id
            answer.updatedAt = now
        }
        record(
            clientId, command, proposal.id, ProposalOutcome.APPLIED,
            "Vorschlag ${sourcePhrase(command.source)} übernommen, zu bestätigen: ${question.shortLabel} ${question.display(value)}" +
                command.citation?.let { " (${it.locationLabel ?: it.documentName})" }.orEmpty(),
        )
        return ProposalResult(question.key, proposal.id, ProposalOutcome.APPLIED, null, answerChanged = previousValue != value)
    }

    private fun keepConfirmed(clientId: UUID, command: ProposalCommand, question: QuestionDefinition, value: AnswerValue, now: Instant): ProposalResult {
        supersede(clientId, question.key, setOf(ProposalStatus.OPEN), now)
        val proposal = save(clientId, command, question, value, ProposalStatus.APPLIED, now)
        record(
            clientId, command, proposal.id, ProposalOutcome.UNCHANGED,
            "Vorschlag ${sourcePhrase(command.source)} entspricht der bestätigten Antwort: ${question.shortLabel} ${question.display(value)}",
        )
        return ProposalResult(question.key, proposal.id, ProposalOutcome.UNCHANGED, null, answerChanged = false)
    }

    private fun openForAdvisor(
        clientId: UUID,
        command: ProposalCommand,
        question: QuestionDefinition,
        value: AnswerValue,
        answer: Answer,
        now: Instant,
    ): ProposalResult {
        supersede(clientId, question.key, setOf(ProposalStatus.OPEN), now)
        val proposal = save(clientId, command, question, value, ProposalStatus.OPEN, now)
        val confirmed = codec.decode(question, answer.value)
        record(
            clientId, command, proposal.id, ProposalOutcome.OPEN,
            "Vorschlag ${sourcePhrase(command.source)} offen: ${question.shortLabel} ${question.display(value)}, " +
                "bestätigt ist ${question.display(confirmed)}",
        )
        return ProposalResult(question.key, proposal.id, ProposalOutcome.OPEN, null, answerChanged = false)
    }

    private fun reject(
        clientId: UUID,
        command: ProposalCommand,
        question: QuestionDefinition,
        value: AnswerValue,
        reason: RejectionReason,
        reasonText: String,
    ): ProposalResult {
        val now = Instant.now(clock)
        val proposal = save(clientId, command, question, value, ProposalStatus.REJECTED, now).apply {
            rejectionReason = reason
            decide(ProposalStatus.REJECTED, now, Actor.SYSTEM.name)
        }
        recordRejection(clientId, command, proposal.id, reason, "Vorschlag abgelehnt: ${question.shortLabel} ($reasonText)")
        return ProposalResult(question.key, proposal.id, ProposalOutcome.REJECTED, reason, answerChanged = false)
    }

    /** Unknown keys and invalid values cannot be stored as proposals; they are only audited. */
    private fun rejectWithoutRecord(clientId: UUID, command: ProposalCommand, reason: RejectionReason, reasonText: String): ProposalResult {
        val label = QuestionCatalog.find(command.questionKey)?.shortLabel ?: command.questionKey.take(MAX_KEY_IN_MESSAGE)
        recordRejection(clientId, command, null, reason, "Vorschlag abgelehnt: $label ($reasonText)")
        return ProposalResult(command.questionKey, null, ProposalOutcome.REJECTED, reason, answerChanged = false)
    }

    private fun supersede(clientId: UUID, questionKey: String, statuses: Set<ProposalStatus>, now: Instant) {
        proposalRepository.findByClientIdAndQuestionKeyAndStatusIn(clientId, questionKey, statuses)
            .forEach { it.decide(ProposalStatus.SUPERSEDED, now, Actor.SYSTEM.name) }
    }

    private fun save(
        clientId: UUID,
        command: ProposalCommand,
        question: QuestionDefinition,
        value: AnswerValue,
        status: ProposalStatus,
        now: Instant,
    ): AnswerProposal {
        val citation = command.citation
        // Unverified citations never carry more than LOW confidence (architecture 12, Fundstellen).
        val confidence = if (citation != null && !citation.verified) Confidence.LOW else command.confidence
        return proposalRepository.save(
            AnswerProposal(
                id = UUID.randomUUID(),
                clientId = clientId,
                questionKey = question.key,
                value = codec.encode(value),
                source = command.source,
                status = status,
                rejectionReason = null,
                documentId = citation?.documentId,
                documentName = citation?.documentName,
                page = citation?.page,
                locationLabel = citation?.locationLabel,
                excerpt = citation?.excerpt,
                citationVerified = citation?.verified,
                confidence = confidence,
                rationale = command.rationale,
                conversationId = command.conversationId,
                createdAt = now,
                decidedAt = null,
                decidedBy = null,
            ),
        )
    }

    private fun record(clientId: UUID, command: ProposalCommand, proposalId: UUID, outcome: ProposalOutcome, message: String) {
        auditService.record(
            clientId = clientId,
            actor = actorOf(command.source),
            type = AuditEventType.PROPOSAL_CREATED,
            message = message,
            rule = command.rule,
            details = details(command, proposalId, outcome, null),
        )
    }

    private fun recordRejection(clientId: UUID, command: ProposalCommand, proposalId: UUID?, reason: RejectionReason, message: String) {
        auditService.record(
            clientId = clientId,
            actor = Actor.SYSTEM,
            type = AuditEventType.PROPOSAL_REJECTED,
            message = message,
            details = details(command, proposalId, ProposalOutcome.REJECTED, reason),
        )
    }

    private fun details(command: ProposalCommand, proposalId: UUID?, outcome: ProposalOutcome, reason: RejectionReason?) = mapOf(
        "key" to command.questionKey.take(MAX_KEY_IN_MESSAGE),
        "proposalId" to proposalId,
        "source" to command.source,
        "outcome" to outcome,
        "reason" to reason,
        "documentId" to command.citation?.documentId,
        "conversationId" to command.conversationId,
    )

    private fun actorOf(source: ProposalSource): Actor = when (source) {
        ProposalSource.RULE -> Actor.RULE
        ProposalSource.DOCUMENT, ProposalSource.AGENT -> Actor.AGENT
    }

    private fun sourcePhrase(source: ProposalSource): String = when (source) {
        ProposalSource.DOCUMENT -> "aus den Unterlagen"
        ProposalSource.AGENT -> "aus dem Chat"
        ProposalSource.RULE -> "des Regelwerks"
    }

    private companion object {
        const val MAX_KEY_IN_MESSAGE = 64
    }
}
