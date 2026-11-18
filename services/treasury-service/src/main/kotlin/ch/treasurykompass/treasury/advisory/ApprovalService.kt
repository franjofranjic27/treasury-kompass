package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.assessment.AssessmentService
import ch.treasurykompass.treasury.assessment.CurrentAssessment
import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.client.ClientService
import ch.treasurykompass.treasury.common.AdvisorContext
import ch.treasurykompass.treasury.common.ProblemException
import ch.treasurykompass.treasury.common.ProblemType
import ch.treasurykompass.treasury.document.DocumentService
import ch.treasurykompass.treasury.questionnaire.AnswerStatus
import ch.treasurykompass.treasury.questionnaire.QuestionCatalog
import ch.treasurykompass.treasury.questionnaire.QuestionnaireService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Approval of proposal and protocol (R-APP-01, INV-3): only without blockers and only for the assessment version the
 * advisor has seen. The protocol is frozen with the approval.
 */
@Service
class ApprovalService(
    private val clientService: ClientService,
    private val assessmentService: AssessmentService,
    private val questionnaireService: QuestionnaireService,
    private val documentService: DocumentService,
    private val checklistService: ChecklistService,
    private val rationaleRepository: RationaleRepository,
    private val approvalRepository: ApprovalRepository,
    private val protocolBuilder: ProtocolBuilder,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) {

    /** Approval status and blockers for a (possibly simulated) assessment. */
    @Transactional(readOnly = true)
    fun state(clientId: UUID, assessment: CurrentAssessment, checklist: List<ChecklistItemView>): ApprovalStateView {
        val blockers = blockers(clientId, assessment, checklist)
        val active = approvalRepository.findByClientIdAndRevokedAtIsNull(clientId)
        return ApprovalStateView(
            status = if (active != null) ApprovalStatus.APPROVED else ApprovalStatus.NOT_APPROVED,
            canApprove = active == null && blockers.isEmpty(),
            blockers = blockers,
            approvalId = active?.id,
            approvedAt = active?.approvedAt,
            approvedBy = active?.approvedBy,
            assessmentVersion = active?.assessmentVersion,
        )
    }

    @Transactional
    fun approve(clientId: UUID, seenVersion: Int): ApprovalView {
        val client = clientService.lock(clientId)
        if (approvalRepository.findByClientIdAndRevokedAtIsNull(clientId) != null) {
            throw ProblemException(ProblemType.APPROVAL_BLOCKED, "Der Vorschlag ist bereits freigegeben.", mapOf("blockers" to emptyList<Blocker>()))
        }
        val assessment = assessmentService.current(clientId)
        if (assessment.version != seenVersion) throw staleAssessment(seenVersion, assessment.version)
        val checklist = checklistService.items(clientId, assessment.body.checklist)
        val blockers = blockers(clientId, assessment, checklist)
        if (blockers.isNotEmpty()) {
            throw ProblemException(ProblemType.APPROVAL_BLOCKED, blockedDetail(blockers), mapOf("blockers" to blockers))
        }

        val advisor = advisorContext.current()
        val now = Instant.now(clock)
        val protocol = protocolBuilder.build(
            ProtocolInput(
                client = client,
                advisorName = advisor.name,
                assessment = assessment,
                answers = questionnaireService.values(clientId),
                checklist = checklist,
                rationale = rationaleRepository.findById(clientId).orElse(null),
                generatedAt = now,
                approvedAt = now,
                approvedBy = advisor.name,
            ),
        )
        val approval = approvalRepository.save(
            Approval(
                id = UUID.randomUUID(),
                clientId = clientId,
                assessmentVersion = assessment.version,
                approvedAt = now,
                approvedBy = advisor.name,
                protocol = jsonMapper.writeValueAsString(protocol),
                protocolText = protocol.text,
            ),
        )
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.APPROVED,
            message = "Vorschlag und Beratungsprotokoll freigegeben",
            assessmentVersion = assessment.version,
            details = mapOf("approvalId" to approval.id),
        )
        return toView(approval)
    }

    /** The advisor withdraws the active approval (WITHDRAWN). */
    @Transactional
    fun withdraw(clientId: UUID) {
        clientService.lock(clientId)
        val approval = approvalRepository.findByClientIdAndRevokedAtIsNull(clientId)
            ?: throw ProblemException(ProblemType.NO_ACTIVE_APPROVAL, "Es gibt keine aktive Freigabe.")
        val advisor = advisorContext.current()
        approval.revoke(RevocationType.WITHDRAWN, Instant.now(clock), advisor.name, "Vom Berater zurückgezogen")
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisor),
            type = AuditEventType.APPROVAL_WITHDRAWN,
            message = "Freigabe zurückgezogen",
            assessmentVersion = approval.assessmentVersion,
            details = mapOf("approvalId" to approval.id),
        )
    }

    @Transactional(readOnly = true)
    fun list(clientId: UUID): List<ApprovalView> {
        clientService.requireExists(clientId)
        return approvalRepository.findByClientIdOrderByApprovedAtDesc(clientId).map(::toView)
    }

    /** Frozen protocol of the active approval, otherwise a live draft of the current version. */
    @Transactional(readOnly = true)
    fun protocol(clientId: UUID): ProtocolView {
        val client = clientService.get(clientId)
        approvalRepository.findByClientIdAndRevokedAtIsNull(clientId)?.let { return protocolOf(it) }
        val assessment = assessmentService.current(clientId)
        return protocolBuilder.build(
            ProtocolInput(
                client = client,
                advisorName = advisorContext.current().name,
                assessment = assessment,
                answers = questionnaireService.values(clientId),
                checklist = checklistService.items(clientId, assessment.body.checklist),
                rationale = rationaleRepository.findById(clientId).orElse(null),
                generatedAt = Instant.now(clock),
            ),
        )
    }

    private fun blockers(clientId: UUID, assessment: CurrentAssessment, checklist: List<ChecklistItemView>): List<Blocker> {
        val answers = questionnaireService.overview(clientId)
        return buildList {
            assessment.body.missingInputs.forEach { key ->
                add(Blocker(BlockerType.MISSING_INPUT, key, "Angabe fehlt: ${QuestionCatalog.find(key)?.shortLabel ?: key}"))
            }
            answers.filter { it.answer?.status == AnswerStatus.PROPOSED }.forEach {
                add(Blocker(BlockerType.UNCONFIRMED_ANSWER, it.question.key, "Bestätigen: ${it.question.shortLabel}"))
            }
            answers.flatMap { overview -> overview.openProposals.map { overview.question to it } }.forEach { (question, proposal) ->
                add(Blocker(BlockerType.OPEN_PROPOSAL, proposal.id.toString(), "Vorschlag prüfen: ${question.shortLabel}"))
            }
            checklist.filterNot { it.done }.forEach { add(Blocker(BlockerType.OPEN_CHECKLIST_ITEM, it.id, it.shortText)) }
            documentService.processing(clientId).forEach {
                add(Blocker(BlockerType.DOCUMENT_PROCESSING, it.id.toString(), "Unterlagen werden noch verarbeitet: ${it.filename}"))
            }
        }
    }

    /** Summary like the prototype, e.g. «Vor der Freigabe 11 vorbefüllte Antworten bestätigen und 6 Punkte der Checkliste erledigen.» */
    private fun blockedDetail(blockers: List<Blocker>): String {
        val counts = blockers.groupingBy { it.type }.eachCount()
        val parts = BlockerType.entries.mapNotNull { type ->
            val n = counts[type] ?: return@mapNotNull null
            when (type) {
                BlockerType.MISSING_INPUT -> if (n == 1) "1 fehlende Angabe ergänzen" else "$n fehlende Angaben ergänzen"
                BlockerType.UNCONFIRMED_ANSWER -> if (n == 1) "1 vorbefüllte Antwort bestätigen" else "$n vorbefüllte Antworten bestätigen"
                BlockerType.OPEN_PROPOSAL -> if (n == 1) "1 offenen Vorschlag prüfen" else "$n offene Vorschläge prüfen"
                BlockerType.OPEN_CHECKLIST_ITEM -> if (n == 1) "1 Punkt der Checkliste erledigen" else "$n Punkte der Checkliste erledigen"
                BlockerType.DOCUMENT_PROCESSING -> if (n == 1) "die Verarbeitung von 1 Dokument abwarten" else "die Verarbeitung von $n Dokumenten abwarten"
            }
        }
        val joined = if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " und " + parts.last()
        return "Vor der Freigabe $joined."
    }

    private fun staleAssessment(seen: Int, current: Int) = ProblemException(
        ProblemType.STALE_ASSESSMENT,
        "Die Beurteilung hat sich geändert (gesehen: Version $seen, aktuell: Version $current). Bitte neu laden und prüfen.",
    )

    private fun protocolOf(approval: Approval): ProtocolView = jsonMapper.readValue(approval.protocol, ProtocolView::class.java)

    private fun toView(approval: Approval) = ApprovalView(
        id = approval.id,
        clientId = approval.clientId,
        assessmentVersion = approval.assessmentVersion,
        approvedAt = approval.approvedAt,
        approvedBy = approval.approvedBy,
        active = approval.active,
        revokedAt = approval.revokedAt,
        revokedBy = approval.revokedBy,
        revocationType = approval.revocationType,
        revocationReason = approval.revocationReason,
        protocol = protocolOf(approval),
    )
}
