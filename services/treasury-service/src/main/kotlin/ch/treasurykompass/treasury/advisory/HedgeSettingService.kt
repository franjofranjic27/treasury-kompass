package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.assessment.AssessmentBody
import ch.treasurykompass.treasury.assessment.AssessmentService
import ch.treasurykompass.treasury.assessment.Change
import ch.treasurykompass.treasury.assessment.ChangeTracker
import ch.treasurykompass.treasury.assessment.ChangeTrigger
import ch.treasurykompass.treasury.assessment.Currency
import ch.treasurykompass.treasury.assessment.ProductCode
import ch.treasurykompass.treasury.assessment.SuitabilityStatus
import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.client.ClientService
import ch.treasurykompass.treasury.common.AdvisorContext
import ch.treasurykompass.treasury.common.ProblemException
import ch.treasurykompass.treasury.common.ProblemType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID
import ch.treasurykompass.treasury.rules.HedgeSetting as RuleHedgeSetting

/** Advisor settings of one currency; `null` means rule default. Ranges are validated by the request DTO. */
data class HedgeSettingCommand(val startRatio: Int?, val stepDown: Int, val collarEnabled: Boolean?)

/**
 * Start ratio, step-down and collar per currency (R-HED-02 to R-HED-06). A start ratio above the cap is stored but
 * the rules apply the cap (INV-2); switching on a collar that is not allowed is rejected.
 */
@Service
class HedgeSettingService(
    private val clientService: ClientService,
    private val repository: HedgeSettingRepository,
    private val assessmentService: AssessmentService,
    private val changeTracker: ChangeTracker,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val clock: Clock,
) {

    @Transactional
    fun set(clientId: UUID, currency: Currency, command: HedgeSettingCommand): HedgeSettingView {
        clientService.lock(clientId)
        if (command.collarEnabled == true && !collarAllowed(assessmentService.current(clientId).body, currency)) {
            throw ProblemException(
                ProblemType.COLLAR_NOT_ALLOWED,
                "Ein Zero-Cost-Collar ist für $currency nicht erlaubt: Devisenoptionen bzw. Devisentermingeschäfte sind nicht geeignet (Art. 14 FIDLEG).",
            )
        }
        val id = HedgeSettingId(clientId, currency.name)
        val existing = repository.findById(id).orElse(null)
        val unchanged = existing?.let {
            it.startRatio == command.startRatio && it.stepDown == command.stepDown && it.collarEnabled == command.collarEnabled
        } ?: (command == DEFAULT)
        if (unchanged) return toView(existing ?: HedgeSetting(id, null, DEFAULT.stepDown, null, Instant.now(clock)))

        val now = Instant.now(clock)
        val setting = existing?.apply {
            startRatio = command.startRatio
            stepDown = command.stepDown
            collarEnabled = command.collarEnabled
            updatedAt = now
        } ?: repository.save(HedgeSetting(id, command.startRatio, command.stepDown, command.collarEnabled, now))

        val assessment = changeTracker.onChange(clientId, Change(ChangeTrigger.HEDGE_SETTINGS_CHANGED, currency.name))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisorContext.current()),
            type = AuditEventType.HEDGE_SETTINGS_CHANGED,
            message = describe(currency, command),
            assessmentVersion = assessment.version,
            details = mapOf(
                "currency" to currency,
                "startRatio" to command.startRatio,
                "stepDown" to command.stepDown,
                "collarEnabled" to command.collarEnabled,
            ),
        )
        return toView(setting)
    }

    /**
     * R-HED-06: allowed if the proposal says so; without a proposal (incomplete or naturally hedged) from the
     * suitability of FX forward and collar, as far as it is known.
     */
    private fun collarAllowed(body: AssessmentBody, currency: Currency): Boolean {
        body.hedges?.proposals?.firstOrNull { it.currency == currency }?.let { return it.collarAllowed }
        val notSuitable = listOf(ProductCode.FX_FORWARD, ProductCode.ZERO_COST_COLLAR)
            .any { body.suitabilityOf(it)?.status == SuitabilityStatus.NOT_SUITABLE }
        return !notSuitable
    }

    private fun describe(currency: Currency, command: HedgeSettingCommand): String {
        val start = command.startRatio?.let { "Startquote für die nächsten 3 Monate $it %" } ?: "Startquote nach Risikoprofil"
        val instrument = when (command.collarEnabled) {
            true -> "mit Zero-Cost-Collar"
            false -> "nur Devisentermingeschäfte"
            null -> "Instrument nach Regelstandard"
        }
        return "Absicherung $currency angepasst: $start, Abstufung ${command.stepDown} Prozentpunkte pro Quartal, $instrument"
    }

    private fun toView(setting: HedgeSetting) =
        HedgeSettingView(setting.id.currency, setting.startRatio, setting.stepDown, setting.collarEnabled, setting.updatedAt)

    private companion object {
        val DEFAULT = HedgeSettingCommand(startRatio = null, stepDown = RuleHedgeSetting.DEFAULT_STEP_DOWN, collarEnabled = null)
    }
}
