package ch.treasurykompass.treasury.advisory

import ch.treasurykompass.treasury.assessment.CurrentAssessment
import ch.treasurykompass.treasury.client.Client
import ch.treasurykompass.treasury.common.SwissFormat
import ch.treasurykompass.treasury.questionnaire.AnswerValue
import ch.treasurykompass.treasury.questionnaire.QuestionCatalog
import ch.treasurykompass.treasury.rules.Product
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the protocol is built from. [approvedAt] and [approvedBy] are set only when freezing an approval. */
data class ProtocolInput(
    val client: Client,
    val advisorName: String,
    val assessment: CurrentAssessment,
    val answers: Map<String, AnswerValue>,
    val checklist: List<ChecklistItemView>,
    val rationale: Rationale?,
    val generatedAt: Instant,
    val approvedAt: Instant? = null,
    val approvedBy: String? = null,
)

/**
 * Advisory protocol (Art. 15 FIDLEG) with the nine sections of the prototype (`protoSections()`, `protoText()`).
 * Rule texts are taken over verbatim; missing values (incomplete assessment) are shown as «–».
 */
@Component
class ProtocolBuilder {

    fun build(input: ProtocolInput): ProtocolView {
        // A saved rationale is used only for the version it was written for (UC-15); otherwise the rule texts.
        val currentRationale = input.rationale?.takeIf { it.assessmentVersion == input.assessment.version }
        val sections = listOf(
            clientSection(input),
            financialSection(input),
            goalsSection(input),
            riskProfileSection(input),
            knowledgeSection(input),
            ProtocolSection(
                title = "Empfehlungen",
                items = input.assessment.payload.protocolLines?.recommendations ?: listOf("Keine Empfehlung"),
            ),
            ProtocolSection(
                title = "Begründung",
                paragraphs = currentRationale?.let { paragraphs(it.text) } ?: input.assessment.body.reasons,
            ),
            ProtocolSection(
                title = "Nicht empfohlene Produkte",
                items = input.assessment.payload.protocolLines?.notRecommended ?: listOf("Keine"),
            ),
            ProtocolSection(
                title = "Unterlagen und Hinweise",
                items = input.checklist.map { "${if (it.done) "Erledigt" else "Offen"}: ${it.text} (${it.legalBasis})" },
            ),
        )
        val status = if (input.approvedAt != null) ProtocolStatus.APPROVED else ProtocolStatus.DRAFT
        return ProtocolView(
            clientId = input.client.id,
            status = status,
            assessmentVersion = input.assessment.version,
            generatedAt = input.generatedAt,
            approvedAt = input.approvedAt,
            approvedBy = input.approvedBy,
            rationaleStale = input.rationale != null && currentRationale == null,
            sections = sections,
            text = text(input, sections),
        )
    }

    private fun clientSection(input: ProtocolInput): ProtocolSection {
        val client = input.client
        val industry = listOfNotNull(client.industry, client.employeeCount?.let { "$it Mitarbeitende" }).joinToString(", ")
        val segment = input.assessment.body.segment
        return ProtocolSection(
            title = "Kunde und Dienstleistung",
            rows = listOf(
                ProtocolRow("Kunde", client.name),
                ProtocolRow("Branche", industry.ifEmpty { MISSING }),
                ProtocolRow("Kundensegment", segment?.let { "${it.label}: ${it.reason}" } ?: MISSING),
                ProtocolRow("Dienstleistung", "Anlageberatung unter Berücksichtigung des Kundenportfolios"),
                ProtocolRow("Datum", date(input.approvedAt ?: input.generatedAt)),
                ProtocolRow("Berater", input.advisorName),
            ),
        )
    }

    private fun financialSection(input: ProtocolInput): ProtocolSection {
        val body = input.assessment.body
        val revenue = amount(input, "fin.revenue")
        val ebit = amount(input, "fin.ebit")
        val equityRatio = body.riskProfile?.capacity?.factors?.firstOrNull { it.factor == "EQUITY_RATIO" }?.value
        val excess = body.exposures?.liquidity?.excessLiquidity ?: body.liquidity?.excessLiquidity
        return ProtocolSection(
            title = "Finanzielle Verhältnisse",
            rows = listOf(
                ProtocolRow("Umsatz und EBIT", if (revenue != null && ebit != null) "${chf(revenue)} und ${chf(ebit)}" else MISSING),
                ProtocolRow("Eigenkapitalquote", equityRatio?.let { SwissFormat.percent(it) } ?: MISSING),
                ProtocolRow("Flüssige Mittel", amount(input, "fin.cash")?.let(::chf) ?: MISSING),
                ProtocolRow("Mindestliquidität", amount(input, QuestionCatalog.MINIMUM_LIQUIDITY)?.let(::chf) ?: MISSING),
                ProtocolRow("Anlagefähig", excess?.let(::chf) ?: MISSING),
            ),
        )
    }

    private fun goalsSection(input: ProtocolInput): ProtocolSection {
        val policy = (input.answers["goals.policyMaxHedgeRatio"] as? AnswerValue.Percent)?.value
        return ProtocolSection(
            title = "Ziele und Bedürfnisse",
            rows = listOf(
                ProtocolRow("Ziel der Absicherung", label(input, "goals.hedgeObjective")),
                ProtocolRow("Maximale Absicherungsquote", policy?.let { "$it % gemäss Treasury-Policy" } ?: MISSING),
                ProtocolRow("Anlagehorizont", label(input, "goals.investmentHorizon")),
            ),
        )
    }

    private fun riskProfileSection(input: ProtocolInput): ProtocolSection {
        val profile = input.assessment.body.riskProfile
        return ProtocolSection(
            title = "Risikoprofil",
            rows = listOf(
                ProtocolRow("Risikofähigkeit", profile?.let { "${it.capacity.score} von 5" } ?: MISSING),
                ProtocolRow("Risikobereitschaft", profile?.let { "${it.willingness.score} von 5" } ?: MISSING),
                ProtocolRow("Risikoprofil", profile?.let { "${it.score} · ${it.label}" } ?: MISSING),
            ),
        )
    }

    private fun knowledgeSection(input: ProtocolInput): ProtocolSection {
        val suitability = input.assessment.body.suitability
        val rows = Product.financialInstruments.map { product ->
            val result = suitability?.firstOrNull { it.product.name == product.name }
            val value = when {
                result?.knowledgePresumed == true -> "vermutet (professioneller Kunde)"
                else -> label(input, checkNotNull(product.knowledgeKey).key)
            }
            ProtocolRow(product.label, value)
        }
        return ProtocolSection(title = "Kenntnisse und Erfahrungen", rows = rows)
    }

    private fun text(input: ProtocolInput, sections: List<ProtocolSection>): String = buildString {
        append("BERATUNGSPROTOKOLL – ANLAGEBERATUNG\n")
        append("${input.client.name} · ${date(input.approvedAt ?: input.generatedAt)}\n\n")
        sections.forEachIndexed { index, section ->
            append("${index + 1}. ${section.title}\n")
            section.rows.forEach { append("   ${it.label}: ${it.value}\n") }
            section.items.forEach { append("   – $it\n") }
            section.paragraphs.forEach { append("   $it\n") }
            append('\n')
        }
        append(
            input.approvedAt?.let { "Freigegeben am ${date(it)}, ${time(it)}\n" } ?: "Status: Entwurf, nicht freigegeben\n",
        )
    }

    private fun amount(input: ProtocolInput, key: String): Double? = (input.answers[key] as? AnswerValue.Amount)?.value

    private fun label(input: ProtocolInput, key: String): String {
        val value = input.answers[key] ?: return MISSING
        return QuestionCatalog.require(key).labelOf(value) ?: MISSING
    }

    private fun chf(value: Double) = "CHF ${SwissFormat.number(value)} Mio."

    private fun paragraphs(text: String): List<String> = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun date(instant: Instant): String = DATE.format(instant.atZone(ZONE))

    private fun time(instant: Instant): String = TIME.format(instant.atZone(ZONE))

    private companion object {
        const val MISSING = "–"
        val ZONE: ZoneId = ZoneId.of("Europe/Zurich")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
