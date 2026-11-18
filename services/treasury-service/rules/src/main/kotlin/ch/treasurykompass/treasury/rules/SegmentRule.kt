package ch.treasurykompass.treasury.rules

internal data class SegmentFacts(
    val totalAssets: Double,
    val revenue: Double,
    val equity: Double,
    val treasuryOrganisation: TreasuryOrganisation,
    val optingInDeclared: Boolean,
)

/**
 * R-SEG-01 (regelwerk § 1, Art. 4 und 5 FIDLEG). Institutional clients are out of scope; opting-out is not
 * offered to companies (ADR-20), so a retail client can never become professional by declaration.
 */
internal object SegmentRule {
    private const val LEGAL_BASIS = "Art. 4 und 5 FIDLEG"
    private const val LARGE_COMPANY_MIN_THRESHOLDS = 2
    private val LIMITS = mapOf(
        ThresholdMetric.TOTAL_ASSETS to 20.0,
        ThresholdMetric.REVENUE to 40.0,
        ThresholdMetric.EQUITY to 2.0,
    )

    private val PROFESSIONAL_CONSEQUENCES = listOf(
        SegmentConsequence("Kenntnisse, Erfahrung und finanzielle Tragbarkeit werden vermutet; Anlageziele werden geprüft", "Art. 12 FIDLEG"),
        SegmentConsequence("Kein Basisinformationsblatt erforderlich", "Art. 58 FIDLEG"),
        SegmentConsequence("Verzicht auf Informations- und Dokumentationspflichten möglich", "Art. 20 FIDLEG"),
        SegmentConsequence("Opting-in als Privatkunde möglich", "Art. 5 FIDLEG"),
    )

    private val RETAIL_CONSEQUENCES = listOf(
        SegmentConsequence("Eignungsprüfung: finanzielle Verhältnisse, Anlageziele, Kenntnisse und Erfahrungen", "Art. 12 FIDLEG"),
        SegmentConsequence("Abraten, wenn ein Produkt nicht geeignet ist", "Art. 14 FIDLEG"),
        SegmentConsequence("Basisinformationsblatt vor Abschluss aushändigen", "Art. 58 FIDLEG"),
        SegmentConsequence("Beratung dokumentieren, Rechenschaft ablegen", "Art. 15 und 16 FIDLEG"),
    )

    fun evaluate(facts: SegmentFacts): SegmentResult {
        val thresholds = thresholdChecks(facts)
        val exceeded = thresholds.count { it.exceeded }
        val largeCompany = exceeded >= LARGE_COMPANY_MIN_THRESHOLDS
        val professionalTreasury = facts.treasuryOrganisation == TreasuryOrganisation.TREASURY_DEPARTMENT
        val professionalBase = largeCompany || professionalTreasury
        // A stored opting-in without professional base is ignored (question not visible).
        val optingIn = professionalBase && facts.optingInDeclared
        val segment = if (professionalBase && !optingIn) ClientSegment.PROFESSIONAL else ClientSegment.RETAIL
        val professional = segment == ClientSegment.PROFESSIONAL

        return SegmentResult(
            result = segment,
            label = segment.label,
            largeCompany = largeCompany,
            thresholdsExceeded = exceeded,
            thresholds = thresholds,
            professionalTreasury = professionalTreasury,
            professionalBase = professionalBase,
            optingIn = optingIn,
            reason = reason(professional, largeCompany, exceeded, professionalTreasury, optingIn),
            summary = if (professional) {
                "Kenntnisse, Erfahrung und finanzielle Tragbarkeit werden vermutet."
            } else {
                "Höchstes Schutzniveau mit vollständiger Eignungsprüfung."
            },
            legalBasis = LEGAL_BASIS,
            decisionSteps = decisionSteps(largeCompany, exceeded, professionalTreasury, facts.treasuryOrganisation, professionalBase, optingIn),
            consequences = if (professional) PROFESSIONAL_CONSEQUENCES else RETAIL_CONSEQUENCES,
        )
    }

    fun decision(segment: SegmentResult): RuleDecision = RuleDecision(
        key = "segment",
        ruleId = RuleId.SEG_01,
        legalBasis = LEGAL_BASIS,
        value = segment.result.name,
        message = "Kundensegment ${segment.label}: ${segment.reason}",
    )

    private fun thresholdChecks(facts: SegmentFacts): List<ThresholdCheck> = listOf(
        ThresholdMetric.TOTAL_ASSETS to facts.totalAssets,
        ThresholdMetric.REVENUE to facts.revenue,
        ThresholdMetric.EQUITY to facts.equity,
    ).map { (metric, value) ->
        val limit = LIMITS.getValue(metric)
        ThresholdCheck(metric = metric, label = metric.label, value = value, limit = limit, exceeded = value > limit)
    }

    private fun reason(
        professional: Boolean,
        largeCompany: Boolean,
        exceeded: Int,
        professionalTreasury: Boolean,
        optingIn: Boolean,
    ): String = when {
        professional && largeCompany ->
            "grosses Unternehmen, $exceeded von 3 Schwellenwerten überschritten${if (professionalTreasury) ", professionelle Tresorerie" else ""}"
        professional -> "Unternehmen mit professioneller Tresorerie"
        optingIn -> "Opting-in erklärt"
        else -> "$exceeded von 3 Schwellenwerten überschritten, keine professionelle Tresorerie"
    }

    private fun decisionSteps(
        largeCompany: Boolean,
        exceeded: Int,
        professionalTreasury: Boolean,
        treasuryOrganisation: TreasuryOrganisation,
        professionalBase: Boolean,
        optingIn: Boolean,
    ): List<SegmentDecisionStep> = buildList {
        add(SegmentDecisionStep("Institutioneller Kunde?", "Nein", "Kein Finanzintermediär, keine Versicherung, keine Zentralbank", "Art. 4 Abs. 4 FIDLEG"))
        add(SegmentDecisionStep("Grosses Unternehmen?", yesNo(largeCompany), "$exceeded von 3 Schwellenwerten überschritten, mindestens 2 nötig", "Art. 4 Abs. 5 FIDLEG"))
        add(SegmentDecisionStep("Professionelle Tresorerie?", yesNo(professionalTreasury), treasuryOrganisation.label, "Art. 4 Abs. 3 FIDLEG"))
        if (professionalBase) {
            val reason = if (optingIn) "Das Unternehmen wird als Privatkunde behandelt" else "Behandlung als professioneller Kunde"
            add(SegmentDecisionStep("Opting-in erklärt?", yesNo(optingIn), reason, "Art. 5 FIDLEG"))
        }
    }

    private fun yesNo(value: Boolean): String = if (value) "Ja" else "Nein"
}
