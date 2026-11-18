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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Advisor commands on answers (architecture 6, answer rules). */
@Service
class AnswerService(
    private val clientService: ClientService,
    private val answerRepository: AnswerRepository,
    private val assessmentService: AssessmentService,
    private val changeTracker: ChangeTracker,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val codec: AnswerValueCodec,
    private val views: QuestionnaireViews,
    private val clock: Clock,
) {

    /**
     * Sets a value: the answer becomes CONFIRMED with source ADVISOR, also when it was PROPOSED before.
     * Setting the value that is already stored only confirms it and therefore changes nothing else.
     */
    @Transactional
    fun set(clientId: UUID, questionKey: String, rawValue: JsonNode?): AnswerView {
        clientService.lock(clientId)
        val question = QuestionCatalog.require(questionKey)
        requireVisible(clientId, question)
        val value = question.parse(rawValue)
        val existing = answerRepository.findById(AnswerId(clientId, question.key)).orElse(null)
        val previous = existing?.let { codec.decode(question, it.value) }
        if (existing != null && previous == value) {
            if (existing.status == AnswerStatus.PROPOSED) confirm(existing, question)
            return views.answer(existing)
        }

        val advisor = advisorContext.current()
        val now = Instant.now(clock)
        val answer = existing?.apply {
            this.value = codec.encode(value)
            source = AnswerSource.ADVISOR
            proposalId = null
            updatedAt = now
            confirm(now, advisor.name)
        } ?: answerRepository.save(
            Answer(
                id = AnswerId(clientId, question.key),
                value = codec.encode(value),
                status = AnswerStatus.CONFIRMED,
                source = AnswerSource.ADVISOR,
                proposalId = null,
                updatedAt = now,
                confirmedAt = now,
                confirmedBy = advisor.name,
            ),
        )
        val assessment = changeTracker.onChange(clientId, Change(ChangeTrigger.ANSWER_CHANGED, question.key))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.ANSWER_SET,
            message = (if (previous == null) "Antwort erfasst: " else "Antwort geändert: ") + question.describeChange(previous, value),
            assessmentVersion = assessment.version,
            details = mapOf("key" to question.key, "old" to previous?.raw, "new" to value.raw),
        )
        return views.answer(answer)
    }

    /** Confirms a prefilled answer without changing its value; idempotent, no new version, no revocation. */
    @Transactional
    fun confirm(clientId: UUID, questionKey: String): AnswerView {
        clientService.lock(clientId)
        val question = QuestionCatalog.require(questionKey)
        val answer = answerRepository.findById(AnswerId(clientId, question.key)).orElseThrow {
            NotFoundException("Zur Frage ${question.key} gibt es noch keine Antwort.")
        }
        if (answer.status == AnswerStatus.PROPOSED) confirm(answer, question)
        return views.answer(answer)
    }

    /** Confirms all PROPOSED answers of the visible questions of one section. */
    @Transactional
    fun confirmSection(clientId: UUID, sectionId: SectionId): SectionConfirmResult {
        clientService.lock(clientId)
        val context = assessmentService.current(clientId).questionContext
        val answers = answerRepository.findByIdClientId(clientId).associateBy { it.questionKey }
        val confirmed = QuestionCatalog.section(sectionId)
            .filter { it.isVisible(context) }
            .mapNotNull { question -> answers[question.key]?.takeIf { it.status == AnswerStatus.PROPOSED }?.let { question to it } }
            .map { (question, answer) ->
                confirm(answer, question)
                question.key
            }
        return SectionConfirmResult(sectionId, confirmed)
    }

    private fun confirm(answer: Answer, question: QuestionDefinition) {
        val advisor = advisorContext.current()
        answer.confirm(Instant.now(clock), advisor.name)
        auditService.record(
            clientId = answer.id.clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.ANSWER_CONFIRMED,
            message = "Vorbefüllte Antwort bestätigt: ${question.shortLabel}",
            assessmentVersion = assessmentService.current(answer.id.clientId).version,
            details = mapOf("key" to question.key, "value" to codec.decode(question, answer.value).raw, "source" to answer.source),
        )
    }

    private fun requireVisible(clientId: UUID, question: QuestionDefinition) {
        if (!question.isVisible(assessmentService.current(clientId).questionContext)) {
            throw ProblemException(
                ProblemType.QUESTION_NOT_APPLICABLE,
                "Die Frage «${question.shortLabel}» ist für diesen Kunden derzeit nicht anwendbar.",
            )
        }
    }
}
