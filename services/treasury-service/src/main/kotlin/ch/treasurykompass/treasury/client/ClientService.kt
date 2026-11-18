package ch.treasurykompass.treasury.client

import ch.treasurykompass.treasury.assessment.Change
import ch.treasurykompass.treasury.assessment.ChangeTracker
import ch.treasurykompass.treasury.assessment.ChangeTrigger
import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.common.AdvisorContext
import ch.treasurykompass.treasury.common.NotFoundException
import ch.treasurykompass.treasury.common.ProblemException
import ch.treasurykompass.treasury.common.ProblemType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Master data of a client. All fields except [name] are optional. */
data class ClientData(
    val name: String,
    val industry: String?,
    val employeeCount: Int?,
    val fiscalYear: Int?,
    val shortLabel: String?,
)

/** Field-wise change of a merge patch: only present fields are applied, `null` clears optional fields. */
data class ClientPatch(
    val name: String? = null,
    val industry: PatchValue<String>? = null,
    val employeeCount: PatchValue<Int>? = null,
    val fiscalYear: PatchValue<Int>? = null,
    val shortLabel: PatchValue<String>? = null,
)

/** A value that is present in a patch, possibly `null`. Absence is modelled as `null` of the wrapper. */
data class PatchValue<T>(val value: T?)

@Service
class ClientService(
    private val repository: ClientRepository,
    private val changeTracker: ChangeTracker,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val clock: Clock,
) {

    /**
     * Locks the client row (`SELECT … FOR UPDATE`) for the current command. Every command that changes a
     * client's state calls this first, which serialises concurrent changes per client.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun lock(clientId: UUID): Client = repository.lockById(clientId) ?: throw notFound(clientId)

    @Transactional(readOnly = true)
    fun get(clientId: UUID): Client = repository.findById(clientId).orElseThrow { notFound(clientId) }

    @Transactional(readOnly = true)
    fun requireExists(clientId: UUID) {
        if (!repository.existsById(clientId)) throw notFound(clientId)
    }

    @Transactional(readOnly = true)
    fun list(query: String?): List<Client> {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty()) return repository.findAllOrderedByName()
        val escaped = trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return repository.search(escaped)
    }

    @Transactional
    fun create(data: ClientData, id: UUID = UUID.randomUUID()): Client {
        val name = data.name.trim()
        if (repository.existsByNameIgnoringCase(name)) throw duplicate(name)
        val now = Instant.now(clock)
        val client = repository.save(
            Client(
                id = id,
                name = name,
                industry = data.industry?.trim(),
                employeeCount = data.employeeCount,
                fiscalYear = data.fiscalYear,
                shortLabel = data.shortLabel?.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.flush()
        val assessment = changeTracker.onChange(client.id, Change(ChangeTrigger.CLIENT_CREATED))
        auditService.record(
            clientId = client.id,
            actor = Actor.advisor(advisorContext.current()),
            type = AuditEventType.CLIENT_CREATED,
            message = "Kunde angelegt: ${client.name}",
            assessmentVersion = assessment.version,
            details = mapOf("name" to client.name),
        )
        return client
    }

    @Transactional
    fun update(clientId: UUID, patch: ClientPatch): Client {
        val client = lock(clientId)
        val changes = linkedMapOf<String, Pair<Any?, Any?>>()

        patch.name?.trim()?.let { name ->
            if (name != client.name) {
                if (repository.existsByNameIgnoringCaseExcluding(name, clientId)) throw duplicate(name)
                changes["name"] = client.name to name
                client.name = name
            }
        }
        patch.industry?.let { apply("industry", client.industry, it.value?.trim(), changes) { v -> client.industry = v } }
        patch.employeeCount?.let { apply("employeeCount", client.employeeCount, it.value, changes) { v -> client.employeeCount = v } }
        patch.fiscalYear?.let { apply("fiscalYear", client.fiscalYear, it.value, changes) { v -> client.fiscalYear = v } }
        patch.shortLabel?.let { apply("shortLabel", client.shortLabel, it.value?.trim(), changes) { v -> client.shortLabel = v } }
        if (changes.isEmpty()) return client

        client.updatedAt = Instant.now(clock)
        val assessment = changeTracker.onChange(clientId, Change(ChangeTrigger.CLIENT_CHANGED, changes.keys.joinToString(", ")))
        auditService.record(
            clientId = clientId,
            actor = Actor.advisor(advisorContext.current()),
            type = AuditEventType.CLIENT_UPDATED,
            message = "Stammdaten geändert: ${changes.keys.joinToString(", ") { FIELD_LABELS.getValue(it) }}",
            assessmentVersion = assessment.version,
            details = changes.mapValues { (_, oldNew) -> mapOf("old" to oldNew.first, "new" to oldNew.second) },
        )
        return client
    }

    private fun <T> apply(field: String, old: T?, new: T?, changes: MutableMap<String, Pair<Any?, Any?>>, setter: (T?) -> Unit) {
        if (old == new) return
        changes[field] = old to new
        setter(new)
    }

    private fun notFound(clientId: UUID) = NotFoundException("Kunde $clientId existiert nicht.")

    private fun duplicate(name: String) =
        ProblemException(ProblemType.DUPLICATE_CLIENT, "Ein Kunde mit dem Namen «$name» existiert bereits.")

    private companion object {
        val FIELD_LABELS = mapOf(
            "name" to "Name",
            "industry" to "Branche",
            "employeeCount" to "Mitarbeitende",
            "fiscalYear" to "Geschäftsjahr",
            "shortLabel" to "Kurzlabel",
        )
    }
}
