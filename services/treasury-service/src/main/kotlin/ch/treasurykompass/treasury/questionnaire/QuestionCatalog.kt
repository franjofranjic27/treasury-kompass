package ch.treasurykompass.treasury.questionnaire

import ch.treasurykompass.treasury.common.NotFoundException
import ch.treasurykompass.treasury.common.SwissFormat
import ch.treasurykompass.treasury.common.ValidationException
import tools.jackson.databind.JsonNode

enum class SectionId(val title: String, val legalBasis: String) {
    FINANCIALS("Finanzielle Verhältnisse", "Art. 12 FIDLEG"),
    FX_FLOWS("Fremdwährungsflüsse, nächste 12 Monate", "Art. 12 FIDLEG"),
    SEGMENT("Kundensegmentierung", "Art. 4 und 5 FIDLEG"),
    GOALS("Ziele und Bedürfnisse", "Art. 12 FIDLEG"),
    RISK_TOLERANCE("Risikobereitschaft", "Art. 12 FIDLEG"),
    KNOWLEDGE("Kenntnisse und Erfahrungen", "Art. 12 FIDLEG"),
}

enum class QuestionType { AMOUNT, PERCENT, CHOICE, BOOLEAN, KNOWLEDGE_LEVEL }

data class QuestionOption(val value: Any, val label: String)

/** When a question is shown. Answers to invisible questions are ignored (architecture 6). */
enum class Visibility { ALWAYS, PROFESSIONAL_BASE }

enum class Requirement { ALWAYS, OPTIONAL, UNLESS_PROFESSIONAL }

/** Client-specific facts that make questions visible, required or change their hint. */
data class QuestionContext(
    val professionalBase: Boolean,
    val professional: Boolean,
    val suggestedMinimumLiquidity: Double?,
) {
    companion object {
        val UNKNOWN = QuestionContext(professionalBase = false, professional = false, suggestedMinimumLiquidity = null)
    }
}

data class QuestionDefinition(
    val key: String,
    val section: SectionId,
    val type: QuestionType,
    val text: String,
    val shortLabel: String,
    val extractable: Boolean,
    val unit: String? = null,
    val hint: String? = null,
    val allowNegative: Boolean = false,
    val options: List<QuestionOption> = emptyList(),
    val requirement: Requirement = Requirement.ALWAYS,
    val visibility: Visibility = Visibility.ALWAYS,
) {
    val min: Double? = when (type) {
        QuestionType.AMOUNT -> if (allowNegative) null else 0.0
        QuestionType.PERCENT -> 0.0
        else -> null
    }
    val max: Double? = if (type == QuestionType.PERCENT) 100.0 else null

    fun isVisible(context: QuestionContext): Boolean =
        visibility == Visibility.ALWAYS || context.professionalBase

    fun isRequired(context: QuestionContext): Boolean = when (requirement) {
        Requirement.ALWAYS -> true
        Requirement.OPTIONAL -> false
        Requirement.UNLESS_PROFESSIONAL -> !context.professional
    }

    fun hintFor(context: QuestionContext): String? = when {
        key == GOALS_MINIMUM_LIQUIDITY && context.suggestedMinimumLiquidity != null ->
            "Richtwert: 1.5 Monate Betriebsaufwand, CHF ${SwissFormat.number(context.suggestedMinimumLiquidity)} Mio."
        section == SectionId.KNOWLEDGE && context.professional ->
            "Bei professionellen Kunden werden Kenntnisse und Erfahrungen vermutet. Die Angaben sind freiwillig und verbessern die Beratung."
        else -> hint
    }

    /** Parses and validates a JSON value against this question; field errors refer to `value`. */
    fun parse(node: JsonNode?): AnswerValue {
        if (node == null || node.isNull) throw invalid("darf nicht leer sein")
        return when (type) {
            QuestionType.AMOUNT -> parseAmount(node)
            QuestionType.PERCENT -> parsePercent(node)
            QuestionType.BOOLEAN -> if (node.isBoolean) AnswerValue.Flag(node.booleanValue()) else throw invalid("muss true oder false sein")
            QuestionType.CHOICE, QuestionType.KNOWLEDGE_LEVEL -> parseChoice(node)
        }
    }

    /** Display text of an option (CHOICE, BOOLEAN, KNOWLEDGE_LEVEL), otherwise `null`. */
    fun labelOf(value: AnswerValue): String? = options.firstOrNull { it.value == value.raw }?.label
        ?.takeIf { type != QuestionType.PERCENT }

    /** Value as shown in audit messages, e.g. `19.2 Mio.`, `80 %` or the option label. */
    fun display(value: AnswerValue): String = when (value) {
        is AnswerValue.Amount -> "${SwissFormat.number(value.value, 1, 2)} Mio."
        is AnswerValue.Percent -> "${value.value} %"
        is AnswerValue.Choice, is AnswerValue.Flag -> labelOf(value) ?: value.raw.toString()
    }

    /** Audit text of a value change, e.g. `Bilanzsumme 19.2 → 21.0 Mio.` or `Ziel der Absicherung: … → …`. */
    fun describeChange(old: AnswerValue?, new: AnswerValue): String {
        fun bare(value: AnswerValue): String = when (value) {
            is AnswerValue.Amount -> SwissFormat.number(value.value, 1, 2)
            is AnswerValue.Percent -> value.value.toString()
            else -> display(value)
        }
        val change = old?.let { "${bare(it)} → ${bare(new)}" } ?: bare(new)
        return when (new) {
            is AnswerValue.Amount -> "$shortLabel $change Mio."
            is AnswerValue.Percent -> "$shortLabel $change %"
            else -> "$shortLabel: $change"
        }
    }

    private fun parseAmount(node: JsonNode): AnswerValue.Amount {
        if (!node.isNumber) throw invalid("muss eine Zahl in Mio. sein")
        val value = node.doubleValue()
        if (!value.isFinite()) throw invalid("muss eine endliche Zahl sein")
        if (!allowNegative && value < 0) throw invalid("darf nicht negativ sein")
        return AnswerValue.amount(value)
    }

    private fun parsePercent(node: JsonNode): AnswerValue.Percent {
        if (!node.isNumber) throw invalid("muss eine ganze Zahl zwischen 0 und 100 sein")
        val value = node.doubleValue()
        if (value % 1.0 != 0.0) throw invalid("muss eine ganze Zahl sein")
        if (value < 0 || value > 100) throw invalid("muss zwischen 0 und 100 liegen")
        return AnswerValue.Percent(value.toInt())
    }

    private fun parseChoice(node: JsonNode): AnswerValue.Choice {
        val codes = options.map { it.value as String }
        if (!node.isString || node.stringValue() !in codes) throw invalid("muss einer der Werte ${codes.joinToString(", ")} sein")
        return AnswerValue.Choice(node.stringValue())
    }

    private fun invalid(message: String) = ValidationException("value", message, "$shortLabel: $message.")

    companion object {
        const val GOALS_MINIMUM_LIQUIDITY = "goals.minimumLiquidity"
    }
}

/**
 * The FIDLEG questionnaire (architecture 6). Texts and option labels follow the prototype (`QUESTIONS`, `OPTS`, `QSHORT`).
 */
object QuestionCatalog {

    const val OPERATING_EXPENSES = "fin.operatingExpenses"
    const val MINIMUM_LIQUIDITY = QuestionDefinition.GOALS_MINIMUM_LIQUIDITY

    private val knowledgeOptions = listOf(
        QuestionOption("NONE", "Keine"),
        QuestionOption("KNOWLEDGE", "Kenntnisse"),
        QuestionOption("EXPERIENCE", "Kenntnisse und Erfahrung"),
    )

    val questions: List<QuestionDefinition> = listOf(
        amount("fin.revenue", SectionId.FINANCIALS, "Wie hoch ist der Umsatzerlös des letzten Geschäftsjahres?", "Umsatzerlös"),
        amount("fin.totalAssets", SectionId.FINANCIALS, "Wie hoch ist die Bilanzsumme per Bilanzstichtag?", "Bilanzsumme"),
        amount("fin.equity", SectionId.FINANCIALS, "Wie hoch ist das Eigenkapital per Bilanzstichtag?", "Eigenkapital", allowNegative = true),
        amount("fin.ebit", SectionId.FINANCIALS, "Wie hoch ist das Betriebsergebnis vor Zinsen und Steuern (EBIT)?", "Betriebsergebnis (EBIT)", allowNegative = true),
        amount("fin.cash", SectionId.FINANCIALS, "Wie hoch sind die flüssigen Mittel per Bilanzstichtag?", "Flüssige Mittel"),
        amount(OPERATING_EXPENSES, SectionId.FINANCIALS, "Wie hoch ist der Betriebsaufwand des letzten Geschäftsjahres?", "Betriebsaufwand"),
        amount("fin.floatingRateDebt", SectionId.FINANCIALS, "Wie hoch ist der variabel verzinste Bankkredit (SARON-basiert)?", "Bankkredit, SARON-basiert"),
        amount("fx.eur.inflow", SectionId.FX_FLOWS, "Welche EUR-Zuflüsse erwartet das Unternehmen in den nächsten 12 Monaten?", "EUR-Zuflüsse", unit = "EUR Mio."),
        amount("fx.eur.outflow", SectionId.FX_FLOWS, "Welche EUR-Abflüsse erwartet das Unternehmen in den nächsten 12 Monaten?", "EUR-Abflüsse", unit = "EUR Mio."),
        amount("fx.usd.inflow", SectionId.FX_FLOWS, "Welche USD-Zuflüsse erwartet das Unternehmen in den nächsten 12 Monaten?", "USD-Zuflüsse", unit = "USD Mio."),
        amount("fx.usd.outflow", SectionId.FX_FLOWS, "Welche USD-Abflüsse erwartet das Unternehmen in den nächsten 12 Monaten?", "USD-Abflüsse", unit = "USD Mio."),
        QuestionDefinition(
            key = "segment.treasuryOrganisation",
            section = SectionId.SEGMENT,
            type = QuestionType.CHOICE,
            text = "Wie werden die Finanzen des Unternehmens geführt?",
            shortLabel = "Treasury-Organisation",
            extractable = true,
            hint = "Professionelle Tresorerie heisst: Mindestens eine fachlich ausgewiesene Person mit Erfahrung im Finanzbereich ist mit der Bewirtschaftung der Finanzmittel betraut.",
            options = listOf(
                QuestionOption("TREASURY_DEPARTMENT", "Eigene Treasury-Abteilung mit Fachpersonen"),
                QuestionOption("CFO_OR_ACCOUNTING", "CFO oder Buchhaltung, neben anderen Aufgaben"),
                QuestionOption("OUTSOURCED", "An externen Dienstleister ausgelagert"),
            ),
        ),
        QuestionDefinition(
            key = "segment.optingIn",
            section = SectionId.SEGMENT,
            type = QuestionType.BOOLEAN,
            text = "Soll das Unternehmen als Privatkunde behandelt werden?",
            shortLabel = "Opting-in",
            extractable = false,
            hint = "Opting-in: höheres Schutzniveau mit vollständiger Eignungsprüfung und Basisinformationsblättern.",
            options = listOf(QuestionOption(false, "Nein"), QuestionOption(true, "Ja, Opting-in erklären")),
            requirement = Requirement.OPTIONAL,
            visibility = Visibility.PROFESSIONAL_BASE,
        ),
        QuestionDefinition(
            key = "goals.hedgeObjective",
            section = SectionId.GOALS,
            type = QuestionType.CHOICE,
            text = "Was ist das Hauptziel der Absicherung?",
            shortLabel = "Ziel der Absicherung",
            extractable = false,
            options = listOf(
                QuestionOption("BUDGET_CERTAINTY", "Budgetsicherheit: Plankurs halten"),
                QuestionOption("PROTECT_MARGINS", "Margen schützen, Chancen teilweise offen lassen"),
                QuestionOption("LOW_HEDGING_COST", "Absicherungskosten tief halten"),
            ),
        ),
        QuestionDefinition(
            key = "goals.policyMaxHedgeRatio",
            section = SectionId.GOALS,
            type = QuestionType.PERCENT,
            text = "Welche maximale Absicherungsquote erlaubt die Treasury-Policy?",
            shortLabel = "Maximale Absicherungsquote",
            extractable = false,
            unit = "%",
            options = listOf(
                QuestionOption(60, "60 %"),
                QuestionOption(80, "80 %"),
                QuestionOption(90, "90 %"),
                QuestionOption(100, "100 % oder keine Vorgabe"),
            ),
        ),
        amount(MINIMUM_LIQUIDITY, SectionId.GOALS, "Welche Mindestliquidität muss jederzeit verfügbar bleiben?", "Mindestliquidität", extractable = false),
        QuestionDefinition(
            key = "goals.investmentHorizon",
            section = SectionId.GOALS,
            type = QuestionType.CHOICE,
            text = "Wie lange kann die überschüssige Liquidität angelegt werden?",
            shortLabel = "Anlagehorizont",
            extractable = false,
            options = listOf(
                QuestionOption("UNDER_1_YEAR", "Unter 1 Jahr"),
                QuestionOption("ONE_TO_THREE_YEARS", "1 bis 3 Jahre"),
                QuestionOption("OVER_THREE_YEARS", "Über 3 Jahre"),
            ),
        ),
        QuestionDefinition(
            key = "risk.tolerableDrawdown",
            section = SectionId.RISK_TOLERANCE,
            type = QuestionType.CHOICE,
            text = "Welcher vorübergehende Wertrückgang der Liquiditätsanlage innert 12 Monaten wäre tragbar?",
            shortLabel = "Tragbarer Wertrückgang",
            extractable = false,
            options = listOf(
                QuestionOption("NONE", "Keiner, Kapitalerhalt"),
                QuestionOption("UP_TO_2_PERCENT", "Bis 2 %"),
                QuestionOption("UP_TO_5_PERCENT", "Bis 5 %"),
                QuestionOption("OVER_5_PERCENT", "Mehr als 5 %"),
            ),
        ),
        QuestionDefinition(
            key = "risk.forgoneGainAttitude",
            section = SectionId.RISK_TOLERANCE,
            type = QuestionType.CHOICE,
            text = "Der Euro steigt nach Abschluss der Absicherung um 8 %. Wie beurteilt die Geschäftsleitung den entgangenen Kursgewinn?",
            shortLabel = "Entgangener Kursgewinn",
            extractable = false,
            options = listOf(
                QuestionOption("ACCEPTABLE", "Akzeptabel, Planungssicherheit zählt"),
                QuestionOption("TOLERABLE", "Ärgerlich, aber tragbar"),
                QuestionOption("NOT_ACCEPTABLE", "Nicht akzeptabel"),
            ),
        ),
        knowledge("knowledge.fxForward", "Devisentermingeschäften", "Erfahrung mit Devisentermingeschäften"),
        knowledge("knowledge.zeroCostCollar", "Zero-Cost-Collars (Devisenoptionen)", "Erfahrung mit Devisenoptionen"),
        knowledge("knowledge.moneyMarketFund", "Geldmarktfonds CHF", "Erfahrung mit Geldmarktfonds"),
        knowledge("knowledge.shortTermBond", "Obligationen CHF (Investment Grade, kurze Laufzeit)", "Erfahrung mit Obligationen"),
    )

    private val byKey: Map<String, QuestionDefinition> = questions.associateBy { it.key }

    fun find(key: String): QuestionDefinition? = byKey[key]

    fun require(key: String): QuestionDefinition = find(key) ?: throw NotFoundException("Frage $key existiert nicht.")

    fun section(section: SectionId): List<QuestionDefinition> = questions.filter { it.section == section }

    private fun amount(
        key: String,
        section: SectionId,
        text: String,
        shortLabel: String,
        unit: String = "CHF Mio.",
        allowNegative: Boolean = false,
        extractable: Boolean = true,
    ) = QuestionDefinition(
        key = key,
        section = section,
        type = QuestionType.AMOUNT,
        text = text,
        shortLabel = shortLabel,
        extractable = extractable,
        unit = unit,
        allowNegative = allowNegative,
    )

    private fun knowledge(key: String, productPhrase: String, shortLabel: String) = QuestionDefinition(
        key = key,
        section = SectionId.KNOWLEDGE,
        type = QuestionType.KNOWLEDGE_LEVEL,
        text = "Welche Kenntnisse und Erfahrungen haben die zuständigen Personen mit $productPhrase?",
        shortLabel = shortLabel,
        extractable = true,
        options = knowledgeOptions,
        requirement = Requirement.UNLESS_PROFESSIONAL,
    )
}
