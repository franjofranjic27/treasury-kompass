package ch.treasurykompass.treasury.internalapi

import ch.treasurykompass.treasury.advisory.AssessmentView
import ch.treasurykompass.treasury.advisory.AssessmentViewService
import ch.treasurykompass.treasury.advisory.HedgeSettingUpdateRequest
import ch.treasurykompass.treasury.assessment.Currency
import ch.treasurykompass.treasury.assessment.InputOverrides
import ch.treasurykompass.treasury.common.FieldError
import ch.treasurykompass.treasury.common.ValidationException
import ch.treasurykompass.treasury.document.DocumentController
import ch.treasurykompass.treasury.document.DocumentErrorCode
import ch.treasurykompass.treasury.document.DocumentService
import ch.treasurykompass.treasury.document.DocumentStatus
import ch.treasurykompass.treasury.document.StatusUpdate
import ch.treasurykompass.treasury.questionnaire.CitationInput
import ch.treasurykompass.treasury.questionnaire.Confidence
import ch.treasurykompass.treasury.questionnaire.ProposalBatch
import ch.treasurykompass.treasury.questionnaire.ProposalInput
import ch.treasurykompass.treasury.questionnaire.ProposalOutcome
import ch.treasurykompass.treasury.questionnaire.ProposalService
import ch.treasurykompass.treasury.questionnaire.ProposalSource
import ch.treasurykompass.treasury.questionnaire.QuestionCatalog
import ch.treasurykompass.treasury.questionnaire.RejectionReason
import ch.treasurykompass.treasury.rules.HedgeSetting
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.util.UUID
import ch.treasurykompass.treasury.rules.Currency as RuleCurrency

/** Status values the agent may report; `UPLOADED` is set only by the treasury-service. */
enum class CallbackStatus(val documentStatus: DocumentStatus) {
    TEXT_EXTRACTED(DocumentStatus.TEXT_EXTRACTED),
    INDEXED(DocumentStatus.INDEXED),
    EXTRACTED(DocumentStatus.EXTRACTED),
    FAILED(DocumentStatus.FAILED),
}

data class DocumentStatusUpdateRequest(
    @field:NotNull(message = "ist Pflicht") val ingestId: UUID?,
    @field:NotNull(message = "ist Pflicht") val status: CallbackStatus?,
    @field:Min(1, message = "muss mindestens 1 sein") val pageCount: Int? = null,
    @field:Min(0, message = "darf nicht negativ sein") val chunkCount: Int? = null,
    val errorCode: DocumentErrorCode? = null,
    @field:Size(max = 500, message = "darf höchstens 500 Zeichen lang sein") val errorMessage: String? = null,
)

/** RULE proposals are created by the treasury-service only. */
enum class BatchSource(val proposalSource: ProposalSource) {
    DOCUMENT(ProposalSource.DOCUMENT),
    AGENT(ProposalSource.AGENT),
}

data class CitationInputRequest(
    val documentId: UUID? = null,
    @field:NotNull(message = "ist Pflicht") @field:Min(1, message = "muss mindestens 1 sein") val page: Int?,
    @field:Size(max = 200, message = "darf höchstens 200 Zeichen lang sein") val locationLabel: String? = null,
    @field:NotNull(message = "ist Pflicht") @field:Size(max = 600, message = "darf höchstens 600 Zeichen lang sein") val excerpt: String?,
    @field:NotNull(message = "ist Pflicht") val verified: Boolean?,
)

data class ProposalInputRequest(
    @field:NotBlank(message = "ist Pflicht") @field:Size(max = 200, message = "darf höchstens 200 Zeichen lang sein") val questionKey: String?,
    val value: JsonNode?,
    @field:NotNull(message = "ist Pflicht") val confidence: Confidence?,
    @field:Size(max = 1000, message = "darf höchstens 1000 Zeichen lang sein") val rationale: String? = null,
    @field:Valid val citation: CitationInputRequest? = null,
)

data class ProposalBatchRequest(
    @field:NotNull(message = "ist Pflicht") val source: BatchSource?,
    val documentId: UUID? = null,
    val ingestId: UUID? = null,
    val conversationId: UUID? = null,
    @field:NotNull(message = "ist Pflicht")
    @field:Size(min = 1, max = 50, message = "muss 1 bis 50 Vorschläge enthalten")
    @field:Valid
    val proposals: List<ProposalInputRequest>?,
) {
    fun toBatch(): ProposalBatch {
        val errors = buildList {
            if (source == BatchSource.DOCUMENT && documentId == null) add(FieldError("documentId", "ist bei Quelle DOCUMENT Pflicht"))
            if (source == BatchSource.DOCUMENT && ingestId == null) add(FieldError("ingestId", "ist bei Quelle DOCUMENT Pflicht"))
            if (source == BatchSource.AGENT && conversationId == null) add(FieldError("conversationId", "ist bei Quelle AGENT Pflicht"))
        }
        if (errors.isNotEmpty()) throw ValidationException(errors)
        return ProposalBatch(
            source = source!!.proposalSource,
            documentId = documentId,
            ingestId = ingestId,
            conversationId = conversationId,
            proposals = proposals!!.map { input ->
                ProposalInput(
                    questionKey = input.questionKey!!,
                    value = input.value,
                    confidence = input.confidence!!,
                    rationale = input.rationale,
                    citation = input.citation?.let { CitationInput(it.documentId, it.page!!, it.locationLabel, it.excerpt!!, it.verified!!) },
                )
            },
        )
    }
}

data class ProposalResultView(val questionKey: String, val proposalId: UUID?, val outcome: ProposalOutcome, val reason: RejectionReason?)

data class ProposalBatchResultView(val results: List<ProposalResultView>)

data class SimulationRequest(
    val overrides: Map<String, JsonNode?>? = null,
    @field:Valid val hedgeSettings: Map<Currency, HedgeSettingUpdateRequest>? = null,
)

/**
 * Endpoints for the agent-service (paths under `/internal`, protected by [InternalApiKeyFilter]). None of them can confirm an
 * answer or approve a proposal (INV-5).
 */
@RestController
@RequestMapping("/internal")
class InternalController(
    private val documentService: DocumentService,
    private val proposalService: ProposalService,
    private val assessmentViewService: AssessmentViewService,
) {

    @GetMapping("/documents/{documentId}/content")
    fun content(@PathVariable documentId: UUID): ResponseEntity<ByteArray> {
        val file = documentService.content(documentId)
        val headers = HttpHeaders().apply {
            set("X-Client-Id", file.document.clientId.toString())
            set("X-Document-Sha256", file.document.sha256)
        }
        return DocumentController.pdfResponse(file, headers)
    }

    @PostMapping("/documents/{documentId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun status(@PathVariable documentId: UUID, @Valid @RequestBody request: DocumentStatusUpdateRequest) {
        if (request.status == CallbackStatus.FAILED && request.errorCode == null) {
            throw ValidationException("errorCode", "ist bei Status FAILED Pflicht")
        }
        documentService.updateStatus(
            documentId,
            StatusUpdate(
                ingestId = request.ingestId!!,
                status = request.status!!.documentStatus,
                pageCount = request.pageCount,
                chunkCount = request.chunkCount,
                errorCode = request.errorCode,
                errorMessage = request.errorMessage,
            ),
        )
    }

    @PostMapping("/clients/{clientId}/answer-proposals")
    fun proposals(@PathVariable clientId: UUID, @Valid @RequestBody request: ProposalBatchRequest): ProposalBatchResultView =
        ProposalBatchResultView(
            proposalService.receive(clientId, request.toBatch())
                .map { ProposalResultView(it.questionKey, it.proposalId, it.outcome, it.reason) },
        )

    @PostMapping("/clients/{clientId}/assessment/simulate")
    fun simulate(@PathVariable clientId: UUID, @Valid @RequestBody request: SimulationRequest): AssessmentView =
        assessmentViewService.simulate(clientId, overridesOf(request))

    private fun overridesOf(request: SimulationRequest): InputOverrides {
        val errors = mutableListOf<FieldError>()
        val answers = request.overrides.orEmpty().mapNotNull { (key, node) ->
            val question = QuestionCatalog.find(key)
            if (question == null) {
                errors += FieldError("overrides.$key", "ist keine bekannte Frage")
                return@mapNotNull null
            }
            try {
                key to question.parse(node)
            } catch (ex: ValidationException) {
                errors += FieldError("overrides.$key", ex.errors.first().message)
                null
            }
        }.toMap()
        val hedgeSettings = request.hedgeSettings.orEmpty().mapNotNull { (currency, update) ->
            try {
                val command = update.toCommand()
                RuleCurrency.valueOf(currency.name) to HedgeSetting(command.startRatio, command.stepDown, command.collarEnabled)
            } catch (ex: ValidationException) {
                errors += FieldError("hedgeSettings.$currency.${ex.errors.first().field}", ex.errors.first().message)
                null
            }
        }.toMap()
        if (errors.isNotEmpty()) throw ValidationException(errors)
        return InputOverrides(answers, hedgeSettings)
    }
}
