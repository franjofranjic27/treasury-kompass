package ch.treasurykompass.treasury.questionnaire

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

data class CitationView(
    val documentId: UUID?,
    val documentName: String,
    val page: Int?,
    val locationLabel: String?,
    val excerpt: String?,
    val verified: Boolean,
)

data class AnswerView(
    val questionKey: String,
    val value: Any,
    val valueLabel: String?,
    val status: AnswerStatus,
    val source: AnswerSource,
    val citation: CitationView?,
    val confidence: Confidence?,
    val note: String?,
    val updatedAt: Instant,
    val confirmedAt: Instant?,
    val confirmedBy: String?,
)

data class AnswerProposalView(
    val id: UUID,
    val questionKey: String,
    val value: Any,
    val valueLabel: String?,
    val source: ProposalSource,
    val status: ProposalStatus,
    val citation: CitationView?,
    val confidence: Confidence?,
    val rationale: String?,
    val conversationId: UUID?,
    val createdAt: Instant,
    val decidedAt: Instant?,
    val decidedBy: String?,
)

data class QuestionOptionView(val value: Any, val label: String)

data class QuestionView(
    val key: String,
    val section: SectionId,
    val text: String,
    val shortLabel: String,
    val hint: String?,
    val type: QuestionType,
    val unit: String?,
    val min: Double?,
    val max: Double?,
    val options: List<QuestionOptionView>,
    val required: Boolean,
    val visible: Boolean,
    val extractable: Boolean,
    val answer: AnswerView?,
    val openProposals: List<AnswerProposalView>,
)

data class QuestionnaireSectionView(
    val id: SectionId,
    val title: String,
    val legalBasis: String,
    val questions: List<QuestionView>,
)

data class QuestionnaireProgress(
    val total: Int,
    val visible: Int,
    val answered: Int,
    val confirmed: Int,
    val proposed: Int,
    val openProposals: Int,
    val missingRequired: Int,
)

data class QuestionnaireView(
    val clientId: UUID,
    val assessmentVersion: Int,
    val sections: List<QuestionnaireSectionView>,
    val progress: QuestionnaireProgress,
)

data class SectionConfirmResult(val sectionId: SectionId, val confirmedKeys: List<String>)

/** Maps answers and proposals to their API representation. */
@Component
class QuestionnaireViews(
    private val codec: AnswerValueCodec,
    private val proposalRepository: AnswerProposalRepository,
) {

    fun answer(answer: Answer): AnswerView = answer(answer, answer.proposalId?.let(proposalRepository::findById)?.orElse(null))

    fun answer(answer: Answer, proposal: AnswerProposal?): AnswerView {
        val question = QuestionCatalog.require(answer.questionKey)
        val value = codec.decode(question, answer.value)
        return AnswerView(
            questionKey = answer.questionKey,
            value = value.raw,
            valueLabel = question.labelOf(value),
            status = answer.status,
            source = answer.source,
            citation = proposal?.let(::citation),
            confidence = proposal?.confidence,
            note = proposal?.rationale,
            updatedAt = answer.updatedAt,
            confirmedAt = answer.confirmedAt,
            confirmedBy = answer.confirmedBy,
        )
    }

    fun proposal(proposal: AnswerProposal): AnswerProposalView {
        val question = QuestionCatalog.require(proposal.questionKey)
        val value = codec.decode(question, proposal.value)
        return AnswerProposalView(
            id = proposal.id,
            questionKey = proposal.questionKey,
            value = value.raw,
            valueLabel = question.labelOf(value),
            source = proposal.source,
            status = proposal.status,
            citation = citation(proposal),
            confidence = proposal.confidence,
            rationale = proposal.rationale,
            conversationId = proposal.conversationId,
            createdAt = proposal.createdAt,
            decidedAt = proposal.decidedAt,
            decidedBy = proposal.decidedBy,
        )
    }

    private fun citation(proposal: AnswerProposal): CitationView? = proposal.documentName?.let {
        CitationView(
            documentId = proposal.documentId,
            documentName = it,
            page = proposal.page,
            locationLabel = proposal.locationLabel,
            excerpt = proposal.excerpt,
            verified = proposal.citationVerified ?: false,
        )
    }
}
