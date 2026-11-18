package ch.treasurykompass.treasury.assessment

import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.audit.RuleReference
import ch.treasurykompass.treasury.marketdata.MarketDataProvider
import ch.treasurykompass.treasury.questionnaire.QuestionContext
import ch.treasurykompass.treasury.rules.RuleEngine
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

/** The assessment of one version of a client's inputs. */
data class CurrentAssessment(
    val clientId: UUID,
    val version: Int,
    val computedAt: Instant,
    val payload: SnapshotPayload,
) {
    val body: AssessmentBody get() = payload.body

    /** Visibility, requirement and hints of the questionnaire depend on the segment and R-LIQ-01. */
    val questionContext: QuestionContext
        get() = QuestionContext(
            professionalBase = body.segment?.professionalBase ?: false,
            professional = body.segment?.result == ClientSegment.PROFESSIONAL,
            suggestedMinimumLiquidity = payload.suggestedMinimumLiquidity,
        )
}

/**
 * Runs the rule engine and keeps versioned snapshots (ADR-08): a new version is stored only when the inputs hash
 * changes, and every changed rule decision is written to the audit trail (INV-7).
 */
@Service
class AssessmentService(
    private val snapshotRepository: AssessmentSnapshotRepository,
    private val inputAssembler: RuleInputAssembler,
    private val marketDataProvider: MarketDataProvider,
    private val auditService: AuditService,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) {

    // Stable key order, so that equal inputs always produce the same hash.
    private val canonicalMapper: JsonMapper = jsonMapper.rebuild()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build()

    /** Evaluates the current inputs without storing anything. */
    @Transactional(readOnly = true)
    fun evaluate(clientId: UUID, overrides: InputOverrides = InputOverrides.NONE): SnapshotPayload =
        evaluateInputs(clientId, overrides).second

    /**
     * Recalculates and stores a new snapshot if the inputs changed. Callers hold the client lock.
     *
     * @return the current assessment and whether a new version was created
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun recalculate(clientId: UUID): Pair<CurrentAssessment, Boolean> {
        val (inputsHash, payload) = evaluateInputs(clientId, InputOverrides.NONE).let { (inputs, payload) -> hash(inputs) to payload }
        val latest = snapshotRepository.findLatest(clientId)
        if (latest != null && latest.inputsHash == inputsHash) return toCurrent(latest) to false

        val version = (latest?.id?.version ?: 0) + 1
        val snapshot = snapshotRepository.save(
            AssessmentSnapshot(
                id = AssessmentSnapshotId(clientId, version),
                inputsHash = inputsHash,
                result = jsonMapper.writeValueAsString(payload),
                createdAt = Instant.now(clock),
            ),
        )
        auditChangedDecisions(clientId, previous = latest?.let(::payloadOf), current = payload, version = version)
        return toCurrent(snapshot) to true
    }

    /** The latest stored version. Every client has one from creation on (CLIENT_CREATED). */
    @Transactional(readOnly = true)
    fun current(clientId: UUID): CurrentAssessment {
        val snapshot = snapshotRepository.findLatest(clientId)
            ?: throw IllegalStateException("Kunde $clientId hat keine Beurteilung")
        return toCurrent(snapshot)
    }

    /** What-if evaluation on top of the stored inputs; reports the base version and stores nothing. */
    @Transactional(readOnly = true)
    fun simulate(clientId: UUID, overrides: InputOverrides): CurrentAssessment {
        val base = current(clientId)
        return base.copy(computedAt = Instant.now(clock), payload = evaluate(clientId, overrides))
    }

    private fun evaluateInputs(clientId: UUID, overrides: InputOverrides): Pair<EngineInputs, SnapshotPayload> {
        val marketData = marketDataProvider.current()
        val inputs = inputAssembler.assemble(clientId, marketData, overrides)
        val result = RuleEngine.evaluate(inputs.input, inputs.hedgeSettings, inputs.marketData)
        return inputs to AssessmentMapper.toPayload(result, marketData)
    }

    private fun auditChangedDecisions(clientId: UUID, previous: SnapshotPayload?, current: SnapshotPayload, version: Int) {
        val previousByKey = previous?.decisions.orEmpty().associateBy { it.key }
        current.decisions
            .filter { previousByKey[it.key]?.message != it.message }
            .forEach { decision ->
                auditService.record(
                    clientId = clientId,
                    actor = Actor.RULE,
                    type = AuditEventType.RULE_DECISION,
                    message = decision.message,
                    rule = RuleReference(decision.ruleId, decision.legalBasis),
                    assessmentVersion = version,
                    details = mapOf("decision" to decision.key, "old" to previousByKey[decision.key]?.value, "new" to decision.value),
                )
            }
    }

    private fun hash(inputs: EngineInputs): String {
        val canonical = canonicalMapper.writeValueAsBytes(inputs)
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical))
    }

    private fun payloadOf(snapshot: AssessmentSnapshot): SnapshotPayload = jsonMapper.readValue(snapshot.result, SnapshotPayload::class.java)

    private fun toCurrent(snapshot: AssessmentSnapshot) = CurrentAssessment(
        clientId = snapshot.id.clientId,
        version = snapshot.id.version,
        computedAt = snapshot.createdAt,
        payload = payloadOf(snapshot),
    )
}
