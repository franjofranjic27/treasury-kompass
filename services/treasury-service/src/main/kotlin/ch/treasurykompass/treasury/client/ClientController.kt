package ch.treasurykompass.treasury.client

import ch.treasurykompass.treasury.common.FieldError
import ch.treasurykompass.treasury.common.ValidationException
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class ClientCreateRequest(
    @field:NotBlank(message = "darf nicht leer sein")
    @field:Size(max = 200, message = "darf höchstens 200 Zeichen lang sein")
    val name: String?,
    @field:Size(max = 200, message = "darf höchstens 200 Zeichen lang sein")
    val industry: String? = null,
    @field:Min(0, message = "darf nicht negativ sein")
    val employeeCount: Int? = null,
    @field:Min(2000, message = "muss zwischen 2000 und 2100 liegen")
    @field:Max(2100, message = "muss zwischen 2000 und 2100 liegen")
    val fiscalYear: Int? = null,
    @field:Size(max = 60, message = "darf höchstens 60 Zeichen lang sein")
    val shortLabel: String? = null,
) {
    fun toData() = ClientData(name!!, industry, employeeCount, fiscalYear, shortLabel)
}

data class ClientView(
    val id: UUID,
    val name: String,
    val industry: String?,
    val employeeCount: Int?,
    val fiscalYear: Int?,
    val shortLabel: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(client: Client) = ClientView(
            client.id, client.name, client.industry, client.employeeCount, client.fiscalYear, client.shortLabel,
            client.createdAt, client.updatedAt,
        )
    }
}

@RestController
@RequestMapping("/api/clients")
class ClientController(
    private val clientService: ClientService,
    private val overviewService: ClientOverviewService,
) {

    @GetMapping
    fun list(@RequestParam(required = false) @Size(max = 200) query: String?): List<ClientSummaryView> =
        clientService.list(query).map(overviewService::summary)

    @PostMapping
    fun create(@Valid @RequestBody request: ClientCreateRequest): ResponseEntity<ClientView> {
        val client = clientService.create(request.toData())
        val location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(client.id).toUri()
        return ResponseEntity.created(location).body(ClientView.of(client))
    }

    @GetMapping("/{clientId}")
    fun get(@PathVariable clientId: UUID): ClientDetailView = overviewService.detail(clientService.get(clientId))

    /** JSON merge patch: only present fields change, `null` clears optional fields (OpenAPI `ClientUpdate`). */
    @PatchMapping("/{clientId}")
    fun update(@PathVariable clientId: UUID, @RequestBody patch: JsonNode): ClientView =
        ClientView.of(clientService.update(clientId, ClientPatchParser.parse(patch)))
}

/** Parses and validates a merge patch body; unknown fields are rejected like everywhere else. */
internal object ClientPatchParser {

    private val fields = setOf("name", "industry", "employeeCount", "fiscalYear", "shortLabel")

    fun parse(node: JsonNode): ClientPatch {
        if (!node.isObject) throw ValidationException("body", "muss ein JSON-Objekt sein")
        val errors = mutableListOf<FieldError>()
        node.propertyNames().filterNot { it in fields }.forEach { errors += FieldError(it, "ist kein bekanntes Feld") }

        val name = if (node.has("name")) text(node, "name", 200, errors, nullable = false)?.value else null
        val patch = ClientPatch(
            name = name,
            industry = if (node.has("industry")) text(node, "industry", 200, errors, nullable = true) else null,
            employeeCount = if (node.has("employeeCount")) integer(node, "employeeCount", 0..Int.MAX_VALUE, errors) else null,
            fiscalYear = if (node.has("fiscalYear")) integer(node, "fiscalYear", 2000..2100, errors) else null,
            shortLabel = if (node.has("shortLabel")) text(node, "shortLabel", 60, errors, nullable = true) else null,
        )
        if (errors.isNotEmpty()) throw ValidationException(errors)
        return patch
    }

    private fun text(node: JsonNode, field: String, maxLength: Int, errors: MutableList<FieldError>, nullable: Boolean): PatchValue<String>? {
        val value = node.get(field)
        return when {
            value.isNull && nullable -> PatchValue(null)
            !value.isString -> null.also { errors += FieldError(field, "muss ein Text sein") }
            value.stringValue().isBlank() && !nullable -> null.also { errors += FieldError(field, "darf nicht leer sein") }
            value.stringValue().length > maxLength -> null.also { errors += FieldError(field, "darf höchstens $maxLength Zeichen lang sein") }
            else -> PatchValue(value.stringValue())
        }
    }

    private fun integer(node: JsonNode, field: String, range: IntRange, errors: MutableList<FieldError>): PatchValue<Int>? {
        val value = node.get(field)
        return when {
            value.isNull -> PatchValue(null)
            !value.isIntegralNumber || !value.canConvertToInt() -> null.also { errors += FieldError(field, "muss eine ganze Zahl sein") }
            value.intValue() !in range -> null.also { errors += FieldError(field, rangeMessage(range)) }
            else -> PatchValue(value.intValue())
        }
    }

    private fun rangeMessage(range: IntRange) =
        if (range.last == Int.MAX_VALUE) "darf nicht negativ sein" else "muss zwischen ${range.first} und ${range.last} liegen"
}
