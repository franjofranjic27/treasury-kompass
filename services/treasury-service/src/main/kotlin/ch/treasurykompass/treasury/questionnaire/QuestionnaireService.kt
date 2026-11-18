package ch.treasurykompass.treasury.questionnaire

import ch.treasurykompass.treasury.assessment.AssessmentService
import ch.treasurykompass.treasury.client.ClientService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Answers of visible questions, as needed for blockers and key figures. */
data class AnswerOverview(
    val question: QuestionDefinition,
    val answer: Answer?,
    val openProposals: List<AnswerProposal>,
)

/** Read model of the questionnaire (catalogue, answers, citations, open proposals, progress). */
@Service
class QuestionnaireService(
    private val clientService: ClientService,
    private val assessmentService: AssessmentService,
    private val answerRepository: AnswerRepository,
    private val proposalRepository: AnswerProposalRepository,
    private val views: QuestionnaireViews,
    private val codec: AnswerValueCodec,
) {

    @Transactional(readOnly = true)
    fun questionnaire(clientId: UUID): QuestionnaireView {
        clientService.requireExists(clientId)
        val assessment = assessmentService.current(clientId)
        val context = assessment.questionContext
        val answers = answerRepository.findByIdClientId(clientId).associateBy { it.questionKey }
        val proposals = proposalRepository.findByClientIdOrderByCreatedAtDesc(clientId)
        val proposalsById = proposals.associateBy { it.id }
        val openByKey = proposals.filter { it.status == ProposalStatus.OPEN }.groupBy { it.questionKey }

        val sections = SectionId.entries.map { section ->
            QuestionnaireSectionView(
                id = section,
                title = section.title,
                legalBasis = section.legalBasis,
                questions = QuestionCatalog.section(section).map { question ->
                    val answer = answers[question.key]
                    QuestionView(
                        key = question.key,
                        section = question.section,
                        text = question.text,
                        shortLabel = question.shortLabel,
                        hint = question.hintFor(context),
                        type = question.type,
                        unit = question.unit,
                        min = question.min,
                        max = question.max,
                        options = question.options.map { QuestionOptionView(it.value, it.label) },
                        required = question.isRequired(context),
                        visible = question.isVisible(context),
                        extractable = question.extractable,
                        answer = answer?.let { views.answer(it, it.proposalId?.let(proposalsById::get)) },
                        openProposals = openByKey[question.key].orEmpty().map(views::proposal),
                    )
                },
            )
        }
        val visible = sections.flatMap { it.questions }.filter { it.visible }
        return QuestionnaireView(
            clientId = clientId,
            assessmentVersion = assessment.version,
            sections = sections,
            progress = QuestionnaireProgress(
                total = QuestionCatalog.questions.size,
                visible = visible.size,
                answered = visible.count { it.answer != null },
                confirmed = visible.count { it.answer?.status == AnswerStatus.CONFIRMED },
                proposed = visible.count { it.answer?.status == AnswerStatus.PROPOSED },
                openProposals = visible.sumOf { it.openProposals.size },
                missingRequired = visible.count { it.required && it.answer == null },
            ),
        )
    }

    /** Stored answer values by question key. */
    @Transactional(readOnly = true)
    fun values(clientId: UUID): Map<String, AnswerValue> =
        answerRepository.findByIdClientId(clientId).associate { it.questionKey to codec.decode(it.questionKey, it.value) }

    /** Visible questions with their answers and open proposals, in catalogue order. */
    @Transactional(readOnly = true)
    fun overview(clientId: UUID): List<AnswerOverview> {
        val context = assessmentService.current(clientId).questionContext
        val answers = answerRepository.findByIdClientId(clientId).associateBy { it.questionKey }
        val open = proposalRepository.findByClientIdAndStatusOrderByCreatedAtDesc(clientId, ProposalStatus.OPEN).groupBy { it.questionKey }
        return QuestionCatalog.questions
            .filter { it.isVisible(context) }
            .map { AnswerOverview(it, answers[it.key], open[it.key].orEmpty()) }
    }
}
