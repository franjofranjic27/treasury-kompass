package ch.treasurykompass.treasury.audit

import ch.treasurykompass.treasury.client.ClientService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class AuditEventView(
    val id: Long,
    val clientId: UUID,
    val occurredAt: Instant,
    val actorType: ActorType,
    val actorName: String,
    val eventType: AuditEventType,
    val message: String,
    val ruleId: String?,
    val legalBasis: String?,
    val assessmentVersion: Int?,
    val details: JsonNode?,
)

/** Newest first. [nextBefore] is the cursor for the next (older) page, `null` on the last page. */
data class AuditEventPage(val items: List<AuditEventView>, val nextBefore: Long?)

@RestController
@RequestMapping("/api/clients/{clientId}/audit-events")
class AuditController(
    private val auditService: AuditService,
    private val clientService: ClientService,
) {

    @GetMapping
    fun list(
        @PathVariable clientId: UUID,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) limit: Int,
        @RequestParam(required = false) @Min(1) before: Long?,
    ): AuditEventPage {
        clientService.requireExists(clientId)
        return auditService.page(clientId, limit, before)
    }
}
