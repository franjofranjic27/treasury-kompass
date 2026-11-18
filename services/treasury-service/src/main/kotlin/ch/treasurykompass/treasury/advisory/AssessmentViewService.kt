package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.assessment.AssessmentService
import ch.treasurykompass.treasury.assessment.CurrentAssessment
import ch.treasurykompass.treasury.assessment.InputOverrides
import ch.treasurykompass.treasury.client.ClientService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Builds the `Assessment` response: the stored rule result of the current version, with checklist ticks and approval
 * state mixed in on read (they must not create new versions, architecture 5.1).
 */
@Service
class AssessmentViewService(
    private val clientService: ClientService,
    private val assessmentService: AssessmentService,
    private val checklistService: ChecklistService,
    private val approvalService: ApprovalService,
) {

    @Transactional(readOnly = true)
    fun current(clientId: UUID): AssessmentView {
        clientService.requireExists(clientId)
        return view(assessmentService.current(clientId))
    }

    /** What-if result on top of the stored inputs; version of the base, nothing stored, no audit. */
    @Transactional(readOnly = true)
    fun simulate(clientId: UUID, overrides: InputOverrides): AssessmentView {
        clientService.requireExists(clientId)
        return view(assessmentService.simulate(clientId, overrides))
    }

    private fun view(assessment: CurrentAssessment): AssessmentView {
        val body = assessment.body
        val checklist = checklistService.items(assessment.clientId, body.checklist)
        return AssessmentView(
            clientId = assessment.clientId,
            version = assessment.version,
            computedAt = assessment.computedAt,
            status = body.status,
            missingInputs = body.missingInputs,
            marketData = body.marketData,
            segment = body.segment,
            exposures = body.exposures,
            riskProfile = body.riskProfile,
            suitability = body.suitability,
            hedges = body.hedges,
            liquidity = body.liquidity,
            recommendedProducts = body.recommendedProducts,
            checklist = checklist,
            reasons = body.reasons,
            approval = approvalService.state(assessment.clientId, assessment, checklist),
        )
    }
}
