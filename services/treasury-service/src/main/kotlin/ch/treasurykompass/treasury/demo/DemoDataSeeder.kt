package ch.treasurykompass.treasury.demo

import ch.treasurykompass.treasury.assessment.Change
import ch.treasurykompass.treasury.assessment.ChangeTracker
import ch.treasurykompass.treasury.assessment.ChangeTrigger
import ch.treasurykompass.treasury.audit.Actor
import ch.treasurykompass.treasury.audit.AuditEventType
import ch.treasurykompass.treasury.audit.AuditService
import ch.treasurykompass.treasury.audit.RuleReference
import ch.treasurykompass.treasury.client.Client
import ch.treasurykompass.treasury.client.ClientRepository
import ch.treasurykompass.treasury.common.AdvisorContext
import ch.treasurykompass.treasury.questionnaire.Answer
import ch.treasurykompass.treasury.questionnaire.AnswerId
import ch.treasurykompass.treasury.questionnaire.AnswerProposal
import ch.treasurykompass.treasury.questionnaire.AnswerProposalRepository
import ch.treasurykompass.treasury.questionnaire.AnswerRepository
import ch.treasurykompass.treasury.questionnaire.AnswerSource
import ch.treasurykompass.treasury.questionnaire.AnswerStatus
import ch.treasurykompass.treasury.questionnaire.AnswerValue
import ch.treasurykompass.treasury.questionnaire.AnswerValueCodec
import ch.treasurykompass.treasury.questionnaire.Confidence
import ch.treasurykompass.treasury.questionnaire.ProposalSource
import ch.treasurykompass.treasury.questionnaire.ProposalStatus
import ch.treasurykompass.treasury.questionnaire.QuestionCatalog
import ch.treasurykompass.treasury.rules.RuleId
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Loads the two fictional demo clients (architecture 13, `docs/regelwerk.md` section 9) as if their documents had
 * already been read: document values as PROPOSED answers with citations (document name, no document ID), the
 * advisor's answers CONFIRMED. Idempotent per client; the audit trail is append-only, so there is no reset.
 */
@Component
@Profile("demo")
class DemoDataSeeder(
    private val clientRepository: ClientRepository,
    private val answerRepository: AnswerRepository,
    private val proposalRepository: AnswerProposalRepository,
    private val codec: AnswerValueCodec,
    private val changeTracker: ChangeTracker,
    private val auditService: AuditService,
    private val advisorContext: AdvisorContext,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        DemoClients.all.forEach { demo ->
            transactionTemplate.executeWithoutResult {
                if (clientRepository.existsById(demo.id)) {
                    log.info("Demo-Kunde {} existiert bereits", demo.name)
                } else {
                    seed(demo)
                    log.info("Demo-Kunde {} angelegt", demo.name)
                }
            }
        }
    }

    private fun seed(demo: DemoClient) {
        val now = Instant.now(clock)
        clientRepository.saveAndFlush(
            Client(demo.id, demo.name, demo.industry, demo.employeeCount, demo.fiscalYear, demo.shortLabel, now, now),
        )
        auditService.record(
            clientId = demo.id,
            actor = Actor.advisor(advisorContext.current()),
            type = AuditEventType.CLIENT_CREATED,
            message = "Kunde angelegt: ${demo.name} (Demo-Daten)",
            details = mapOf("name" to demo.name),
        )
        demo.answers.forEach { seedAnswer(demo, it, now) }
        val prefilled = demo.answers.count { it.status == AnswerStatus.PROPOSED }
        auditService.record(
            clientId = demo.id,
            actor = Actor.SYSTEM,
            type = AuditEventType.PROPOSAL_CREATED,
            message = "Unterlagen ausgelesen (Demo-Daten): $prefilled vorbefüllte Antworten aus ${demo.documentName}, je mit Fundstelle",
            details = mapOf("documentName" to demo.documentName, "prefilled" to prefilled),
        )
        changeTracker.onChange(demo.id, Change(ChangeTrigger.DEMO_SEEDED))
    }

    private fun seedAnswer(demo: DemoClient, seed: SeedAnswer, now: Instant) {
        val question = QuestionCatalog.require(seed.key)
        val encoded = codec.encode(seed.value)
        val proposal = seed.proposal?.let { citation ->
            val source = ProposalSource.valueOf(seed.source.name)
            proposalRepository.save(
                AnswerProposal(
                    id = UUID.nameUUIDFromBytes("demo:${demo.id}:${question.key}".toByteArray()),
                    clientId = demo.id,
                    questionKey = question.key,
                    value = encoded,
                    source = source,
                    status = ProposalStatus.APPLIED,
                    rejectionReason = null,
                    documentId = null,
                    documentName = demo.documentName,
                    page = citation.page,
                    locationLabel = citation.locationLabel,
                    excerpt = null,
                    citationVerified = false,
                    confidence = citation.confidence,
                    rationale = citation.rationale,
                    conversationId = null,
                    createdAt = now,
                    decidedAt = null,
                    decidedBy = null,
                ),
            ).also {
                if (source == ProposalSource.RULE) {
                    auditService.record(
                        clientId = demo.id,
                        actor = Actor.RULE,
                        type = AuditEventType.PROPOSAL_CREATED,
                        message = "Vorschlag des Regelwerks übernommen, zu bestätigen: ${question.shortLabel} ${question.display(seed.value)}",
                        rule = RuleReference(RuleId.LIQ_01.code, RuleId.LIQ_01.legalBasis),
                        details = mapOf("key" to question.key, "proposalId" to it.id, "source" to source),
                    )
                }
            }
        }
        val confirmed = seed.status == AnswerStatus.CONFIRMED
        answerRepository.save(
            Answer(
                id = AnswerId(demo.id, question.key),
                value = encoded,
                status = seed.status,
                source = seed.source,
                proposalId = proposal?.id,
                updatedAt = now,
                confirmedAt = if (confirmed) now else null,
                confirmedBy = if (confirmed) advisorContext.current().name else null,
            ),
        )
    }
}

internal data class SeedCitation(val locationLabel: String, val page: Int?, val confidence: Confidence, val rationale: String? = null)

internal data class SeedAnswer(
    val key: String,
    val value: AnswerValue,
    val source: AnswerSource,
    val status: AnswerStatus,
    val proposal: SeedCitation? = null,
)

internal data class DemoClient(
    val id: UUID,
    val name: String,
    val industry: String,
    val employeeCount: Int,
    val fiscalYear: Int,
    val shortLabel: String,
    val documentName: String,
    val answers: List<SeedAnswer>,
)

/** Fictional companies only (CLAUDE.md, Daten). Values from architecture section 13. */
internal object DemoClients {

    val MUSTER_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000001")
    val COMPONENTS_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000002")

    private fun document(key: String, value: AnswerValue, label: String, page: Int?, confidence: Confidence = Confidence.HIGH, rationale: String? = null) =
        SeedAnswer(key, value, AnswerSource.DOCUMENT, AnswerStatus.PROPOSED, SeedCitation(label, page, confidence, rationale))

    private fun advisor(key: String, value: AnswerValue) = SeedAnswer(key, value, AnswerSource.ADVISOR, AnswerStatus.CONFIRMED)

    private fun rule(key: String, value: AnswerValue, rationale: String, label: String, page: Int?) =
        SeedAnswer(key, value, AnswerSource.RULE, AnswerStatus.PROPOSED, SeedCitation(label, page, Confidence.HIGH, rationale))

    private fun amount(value: Double) = AnswerValue.amount(value)

    private fun choice(code: String) = AnswerValue.Choice(code)

    private val muster = DemoClient(
        id = MUSTER_ID,
        name = "Muster Maschinenbau AG",
        industry = "Maschinenbau",
        employeeCount = 140,
        fiscalYear = 2025,
        shortLabel = "Export-KMU",
        documentName = "Jahresrechnung_2025_Muster_Maschinenbau.pdf",
        answers = listOf(
            document("fin.revenue", amount(28.4), "Erfolgsrechnung, S. 5", 5),
            document("fin.totalAssets", amount(19.2), "Bilanz, S. 4", 4),
            document("fin.equity", amount(7.9), "Bilanz, S. 4", 4),
            document("fin.ebit", amount(2.3), "Erfolgsrechnung, S. 5", 5),
            document("fin.cash", amount(6.4), "Bilanz, S. 4", 4),
            document("fin.operatingExpenses", amount(26.1), "Erfolgsrechnung, S. 5", 5, Confidence.MEDIUM, "Summe Betriebsaufwand"),
            document("fin.floatingRateDebt", amount(6.0), "Anhang Ziff. 5, S. 11", 11),
            document(
                "fx.eur.inflow", amount(18.7), "Anhang Ziff. 3", null, Confidence.MEDIUM,
                "Umsatz nach Märkten, auf 12 Monate hochgerechnet",
            ),
            document(
                "fx.eur.outflow", amount(4.5), "Anhang Ziff. 3", null, Confidence.MEDIUM,
                "Umsatz nach Märkten, auf 12 Monate hochgerechnet",
            ),
            // Kreditorenliste 2025, vom Kunden ergänzt
            advisor("fx.usd.inflow", amount(0.0)),
            advisor("fx.usd.outflow", amount(3.1)),
            advisor("segment.treasuryOrganisation", choice("CFO_OR_ACCOUNTING")),
            advisor("goals.hedgeObjective", choice("BUDGET_CERTAINTY")),
            advisor("goals.policyMaxHedgeRatio", AnswerValue.Percent(80)),
            rule(QuestionCatalog.MINIMUM_LIQUIDITY, amount(3.3), "Vorschlag aus dem Betriebsaufwand (Erfolgsrechnung, S. 5)", "Erfolgsrechnung, S. 5", 5),
            advisor("goals.investmentHorizon", choice("UNDER_1_YEAR")),
            advisor("risk.tolerableDrawdown", choice("UP_TO_2_PERCENT")),
            advisor("risk.forgoneGainAttitude", choice("TOLERABLE")),
            document(
                "knowledge.fxForward", choice("EXPERIENCE"), "Anhang Ziff. 7, offene Devisentermingeschäfte", null, Confidence.MEDIUM,
                "Anhang Ziff. 7 nennt offene Devisentermingeschäfte über EUR 2.0 Mio. per 31.12.2025. Daraus wird Erfahrung " +
                    "mit Devisentermingeschäften abgeleitet.",
            ),
            advisor("knowledge.zeroCostCollar", choice("NONE")),
            advisor("knowledge.moneyMarketFund", choice("KNOWLEDGE")),
            advisor("knowledge.shortTermBond", choice("EXPERIENCE")),
        ),
    )

    private val components = DemoClient(
        id = COMPONENTS_ID,
        name = "Beispiel Components AG",
        industry = "Industriekomponenten",
        employeeCount = 610,
        fiscalYear = 2025,
        shortLabel = "Mid-Cap",
        documentName = "Geschaeftsbericht_2025_Beispiel_Components.pdf",
        answers = listOf(
            document("fin.revenue", amount(146.0), "Konzernerfolgsrechnung, S. 52", 52),
            document("fin.totalAssets", amount(112.0), "Konzernbilanz, S. 51", 51),
            document("fin.equity", amount(46.0), "Konzernbilanz, S. 51", 51),
            document("fin.ebit", amount(12.8), "Konzernerfolgsrechnung, S. 52", 52),
            document("fin.cash", amount(28.0), "Konzernbilanz, S. 51", 51),
            document("fin.operatingExpenses", amount(133.2), "Konzernerfolgsrechnung, S. 52", 52),
            document("fin.floatingRateDebt", amount(25.0), "Anhang Ziff. 14, S. 66", 66),
            document("fx.eur.inflow", amount(85.4), "Segmentbericht, S. 41", 41, Confidence.MEDIUM, "Umsatz und Einkauf nach Währungen"),
            document("fx.eur.outflow", amount(33.0), "Segmentbericht, S. 41", 41, Confidence.MEDIUM, "Umsatz und Einkauf nach Währungen"),
            document("fx.usd.inflow", amount(33.0), "Segmentbericht, S. 41", 41, Confidence.MEDIUM, "Umsatz und Einkauf nach Währungen"),
            document("fx.usd.outflow", amount(12.0), "Segmentbericht, S. 41", 41, Confidence.MEDIUM, "Umsatz und Einkauf nach Währungen"),
            document(
                "segment.treasuryOrganisation", choice("TREASURY_DEPARTMENT"), "Geschäftsbericht, S. 34", 34, Confidence.HIGH,
                "Group Treasury mit 3 Vollzeitstellen",
            ),
            advisor("segment.optingIn", AnswerValue.Flag(false)),
            advisor("goals.hedgeObjective", choice("PROTECT_MARGINS")),
            advisor("goals.policyMaxHedgeRatio", AnswerValue.Percent(90)),
            rule(
                QuestionCatalog.MINIMUM_LIQUIDITY, amount(16.7), "Vorschlag aus dem Betriebsaufwand (Konzernerfolgsrechnung, S. 52)",
                "Konzernerfolgsrechnung, S. 52", 52,
            ),
            advisor("goals.investmentHorizon", choice("ONE_TO_THREE_YEARS")),
            advisor("risk.tolerableDrawdown", choice("UP_TO_5_PERCENT")),
            advisor("risk.forgoneGainAttitude", choice("TOLERABLE")),
            document(
                "knowledge.fxForward", choice("EXPERIENCE"), "Anhang Ziff. 9, Devisentermingeschäfte", null, Confidence.HIGH,
                "Anhang Ziff. 9 nennt Devisentermingeschäfte mit einem Kontraktvolumen von CHF 38 Mio.",
            ),
            document(
                "knowledge.zeroCostCollar", choice("EXPERIENCE"), "Anhang Ziff. 9, Devisenoptionen", null, Confidence.HIGH,
                "Anhang Ziff. 9 nennt Devisenoptionen über CHF 12 Mio.",
            ),
            advisor("knowledge.moneyMarketFund", choice("EXPERIENCE")),
            advisor("knowledge.shortTermBond", choice("EXPERIENCE")),
        ),
    )

    val all: List<DemoClient> = listOf(muster, components)
}
