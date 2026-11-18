package ch.treasurykompass.treasury.questionnaire

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.util.UUID

/** `value` is validated against the catalogue entry of the question (type, range, options). */
data class AnswerUpdateRequest(val value: JsonNode?)

@RestController
@RequestMapping("/api/clients/{clientId}")
class QuestionnaireController(
    private val questionnaireService: QuestionnaireService,
    private val answerService: AnswerService,
    private val proposalService: ProposalService,
) {

    @GetMapping("/questionnaire")
    fun questionnaire(@PathVariable clientId: UUID): QuestionnaireView = questionnaireService.questionnaire(clientId)

    @PostMapping("/questionnaire/sections/{sectionId}/confirm")
    fun confirmSection(@PathVariable clientId: UUID, @PathVariable sectionId: SectionId): SectionConfirmResult =
        answerService.confirmSection(clientId, sectionId)

    @PutMapping("/answers/{questionKey}")
    fun setAnswer(
        @PathVariable clientId: UUID,
        @PathVariable questionKey: String,
        @RequestBody request: AnswerUpdateRequest,
    ): AnswerView = answerService.set(clientId, questionKey, request.value)

    @PostMapping("/answers/{questionKey}/confirm")
    fun confirmAnswer(@PathVariable clientId: UUID, @PathVariable questionKey: String): AnswerView =
        answerService.confirm(clientId, questionKey)

    @GetMapping("/answer-proposals")
    fun proposals(@PathVariable clientId: UUID, @RequestParam(required = false) status: ProposalStatus?): List<AnswerProposalView> =
        proposalService.list(clientId, status)

    @PostMapping("/answer-proposals/{proposalId}/accept")
    fun accept(@PathVariable clientId: UUID, @PathVariable proposalId: UUID): AnswerView = proposalService.accept(clientId, proposalId)

    @PostMapping("/answer-proposals/{proposalId}/reject")
    fun reject(@PathVariable clientId: UUID, @PathVariable proposalId: UUID): AnswerProposalView = proposalService.reject(clientId, proposalId)
}
