package ch.treasurykompass.treasury.client

import ch.treasurykompass.treasury.advisory.ApprovalStatus
import ch.treasurykompass.treasury.advisory.AssessmentViewService
import ch.treasurykompass.treasury.assessment.ClientSegment
import ch.treasurykompass.treasury.assessment.RiskProfileCode
import ch.treasurykompass.treasury.document.DocumentService
import ch.treasurykompass.treasury.questionnaire.AnswerSource
import ch.treasurykompass.treasury.questionnaire.AnswerStatus
import ch.treasurykompass.treasury.questionnaire.CitationView
import ch.treasurykompass.treasury.questionnaire.Confidence
import ch.treasurykompass.treasury.questionnaire.QuestionnaireService
import ch.treasurykompass.treasury.questionnaire.QuestionnaireViews
import ch.treasurykompass.treasury.questionnaire.SectionId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** OpenAPI `ClientSummary`: master data plus segment, profile and workflow counters for the start screen. */
data class ClientSummaryView(
    val id: UUID,
    val name: String,
    val industry: String?,
    val employeeCount: Int?,
    val fiscalYear: Int?,
    val shortLabel: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val segment: ClientSegment?,
    val riskProfile: RiskProfileCode?,
    val documentCount: Int,
    val openItemCount: Int,
    val approvalStatus: ApprovalStatus,
)

data class KeyFigureView(
    val key: String,
    val label: String,
    val unit: String,
    val value: Double?,
    val status: AnswerStatus?,
    val source: AnswerSource?,
    val citation: CitationView?,
    val confidence: Confidence?,
)

/** OpenAPI `ClientDetail`: summary plus current assessment version and key figures (sections FINANCIALS, FX_FLOWS). */
data class ClientDetailView(
    val id: UUID,
    val name: String,
    val industry: String?,
    val employeeCount: Int?,
    val fiscalYear: Int?,
    val shortLabel: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val segment: ClientSegment?,
    val riskProfile: RiskProfileCode?,
    val documentCount: Int,
    val openItemCount: Int,
    val approvalStatus: ApprovalStatus,
    val assessmentVersion: Int,
    val keyFigures: List<KeyFigureView>,
)

/** Read model across clients, documents, questionnaire and assessment. */
@Service
class ClientOverviewService(
    private val assessmentViewService: AssessmentViewService,
    private val documentService: DocumentService,
    private val questionnaireService: QuestionnaireService,
    private val questionnaireViews: QuestionnaireViews,
) {

    @Transactional(readOnly = true)
    fun summary(client: Client): ClientSummaryView {
        val assessment = assessmentViewService.current(client.id)
        return ClientSummaryView(
            id = client.id,
            name = client.name,
            industry = client.industry,
            employeeCount = client.employeeCount,
            fiscalYear = client.fiscalYear,
            shortLabel = client.shortLabel,
            createdAt = client.createdAt,
            updatedAt = client.updatedAt,
            segment = assessment.segment?.result,
            riskProfile = assessment.riskProfile?.code,
            documentCount = documentService.count(client.id).toInt(),
            openItemCount = assessment.approval.blockers.size,
            approvalStatus = assessment.approval.status,
        )
    }

    @Transactional(readOnly = true)
    fun detail(client: Client): ClientDetailView {
        val summary = summary(client)
        val assessment = assessmentViewService.current(client.id)
        val keyFigures = questionnaireService.overview(client.id)
            .filter { it.question.section == SectionId.FINANCIALS || it.question.section == SectionId.FX_FLOWS }
            .map { overview ->
                val answer = overview.answer?.let(questionnaireViews::answer)
                KeyFigureView(
                    key = overview.question.key,
                    label = overview.question.shortLabel,
                    unit = checkNotNull(overview.question.unit),
                    value = (answer?.value as? Number)?.toDouble(),
                    status = answer?.status,
                    source = answer?.source,
                    citation = answer?.citation,
                    confidence = answer?.confidence,
                )
            }
        return ClientDetailView(
            id = summary.id,
            name = summary.name,
            industry = summary.industry,
            employeeCount = summary.employeeCount,
            fiscalYear = summary.fiscalYear,
            shortLabel = summary.shortLabel,
            createdAt = summary.createdAt,
            updatedAt = summary.updatedAt,
            segment = summary.segment,
            riskProfile = summary.riskProfile,
            documentCount = summary.documentCount,
            openItemCount = summary.openItemCount,
            approvalStatus = summary.approvalStatus,
            assessmentVersion = assessment.version,
            keyFigures = keyFigures,
        )
    }
}
