package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.assessment.Currency
import jakarta.validation.Valid
import ch.treasurykompass.treasury.common.ValidationException
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** `null` start ratio or collar means rule default; a missing step-down means the default of 10 percentage points. */
data class HedgeSettingUpdateRequest(
    @field:Min(0, message = "muss zwischen 0 und 100 liegen")
    @field:Max(100, message = "muss zwischen 0 und 100 liegen")
    val startRatio: Int? = null,
    val stepDown: Int? = null,
    val collarEnabled: Boolean? = null,
) {
    fun toCommand(): HedgeSettingCommand {
        if (stepDown != null && stepDown !in ALLOWED_STEP_DOWNS) throw ValidationException("stepDown", "muss 0, 5, 10 oder 15 sein")
        return HedgeSettingCommand(startRatio, stepDown ?: DEFAULT_STEP_DOWN, collarEnabled)
    }

    private companion object {
        val ALLOWED_STEP_DOWNS = setOf(0, 5, 10, 15)
        const val DEFAULT_STEP_DOWN = 10
    }
}

data class ChecklistUpdateRequest(@field:NotNull(message = "ist Pflicht") val done: Boolean?)

data class ApprovalRequest(@field:NotNull(message = "ist Pflicht") val assessmentVersion: Int?)

data class RationaleUpdateRequest(
    @field:NotBlank(message = "darf nicht leer sein")
    @field:Size(max = 8000, message = "darf höchstens 8000 Zeichen lang sein")
    val text: String?,
    @field:NotNull(message = "ist Pflicht") val assessmentVersion: Int?,
    val source: RationaleSource? = null,
)

@RestController
@RequestMapping("/api/clients/{clientId}")
class AdvisoryController(
    private val assessmentViewService: AssessmentViewService,
    private val hedgeSettingService: HedgeSettingService,
    private val checklistService: ChecklistService,
    private val rationaleService: RationaleService,
    private val approvalService: ApprovalService,
) {

    @GetMapping("/assessment")
    fun assessment(@PathVariable clientId: UUID): AssessmentView = assessmentViewService.current(clientId)

    @PutMapping("/hedge-settings/{currency}")
    fun setHedgeSetting(
        @PathVariable clientId: UUID,
        @PathVariable currency: Currency,
        @Valid @RequestBody request: HedgeSettingUpdateRequest,
    ): HedgeSettingView = hedgeSettingService.set(clientId, currency, request.toCommand())

    @PutMapping("/checklist/{itemId}")
    fun setChecklistItem(
        @PathVariable clientId: UUID,
        @PathVariable itemId: String,
        @Valid @RequestBody request: ChecklistUpdateRequest,
    ): ChecklistItemView = checklistService.set(clientId, itemId, request.done!!)

    @GetMapping("/rationale")
    fun rationale(@PathVariable clientId: UUID): RationaleView = rationaleService.get(clientId)

    @PutMapping("/rationale")
    fun saveRationale(@PathVariable clientId: UUID, @Valid @RequestBody request: RationaleUpdateRequest): RationaleView =
        rationaleService.save(clientId, request.text!!, request.assessmentVersion!!, request.source ?: RationaleSource.ADVISOR)

    @PostMapping("/approval")
    @ResponseStatus(HttpStatus.CREATED)
    fun approve(@PathVariable clientId: UUID, @Valid @RequestBody request: ApprovalRequest): ApprovalView =
        approvalService.approve(clientId, request.assessmentVersion!!)

    @DeleteMapping("/approval")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@PathVariable clientId: UUID) = approvalService.withdraw(clientId)

    @GetMapping("/approvals")
    fun approvals(@PathVariable clientId: UUID): List<ApprovalView> = approvalService.list(clientId)

    @GetMapping("/protocol")
    fun protocol(@PathVariable clientId: UUID): ProtocolView = approvalService.protocol(clientId)
}
