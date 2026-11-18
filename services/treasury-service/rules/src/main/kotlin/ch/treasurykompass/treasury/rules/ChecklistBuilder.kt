package ch.treasurykompass.treasury.rules

/** Recommended products and R-CHK-01 (regelwerk § 7). */
internal object ChecklistBuilder {
    const val INFO_ID = "info"
    const val RISK_BROCHURE_ID = "riskBrochure"
    const val COST_DISCLOSURE_ID = "costDisclosure"
    private const val KID_PREFIX = "kid."
    private const val WARNING_PREFIX = "warning."

    /** Prototype `m.rec`: FX forward, collar, then the liquidity products with a share, in product order. */
    fun recommendedProducts(hedges: HedgeOverview, liquidity: LiquidityAllocation): List<Product> = buildList {
        if (hedges.proposals.any { !it.blocked }) add(Product.FX_FORWARD)
        if (hedges.proposals.any { it.collarEnabled }) add(Product.ZERO_COST_COLLAR)
        if (!liquidity.noExcess) liquidity.parts.filter { it.sharePct > 0 }.forEach { add(it.product) }
    }

    /**
     * Key information documents are one item per product (`kid.<PRODUCT>`, ADR-09), so that a newly recommended
     * product gets its own open item instead of hiding behind an already ticked collective item.
     */
    fun build(segment: ClientSegment, recommended: List<Product>, suitability: List<Suitability>): List<ChecklistItem> {
        val byProduct = suitability.associateBy { it.product }
        val professional = segment == ClientSegment.PROFESSIONAL
        return buildList {
            add(
                ChecklistItem(
                    id = INFO_ID,
                    text = "Informationen zu Finanzdienstleister, Dienstleistung, Risiken und Kosten abgegeben" +
                        if (professional) " (Verzicht möglich)" else "",
                    shortText = "Informationen abgeben",
                    legalBasis = "Art. 8 FIDLEG",
                ),
            )
            recommended.filter { byProduct.getValue(it).kidRequired }.forEach { product ->
                add(
                    ChecklistItem(
                        id = KID_PREFIX + product.name,
                        text = "Basisinformationsblatt ausgehändigt: ${product.label}",
                        shortText = "Basisinformationsblatt ${product.label} aushändigen",
                        legalBasis = "Art. 58 FIDLEG",
                    ),
                )
            }
            add(
                ChecklistItem(
                    id = RISK_BROCHURE_ID,
                    text = "Broschüre «Risiken im Handel mit Finanzinstrumenten» abgegeben",
                    shortText = "Risikobroschüre abgeben",
                    legalBasis = "Art. 8 FIDLEG",
                ),
            )
            add(
                ChecklistItem(
                    id = COST_DISCLOSURE_ID,
                    text = "Kosten, Marge im Terminkurs und allfällige Entschädigungen Dritter offengelegt",
                    shortText = "Kosten offenlegen",
                    legalBasis = "Art. 26 FIDLEG",
                ),
            )
            recommended.filter { byProduct.getValue(it).status == SuitabilityStatus.SUITABLE_AFTER_DISCLOSURE }.forEach { product ->
                add(
                    ChecklistItem(
                        id = WARNING_PREFIX + product.name,
                        text = "Aufklärung und Warnhinweis dokumentiert: ${product.label}",
                        shortText = "Warnhinweis ${product.label} dokumentieren",
                        legalBasis = "Art. 14 FIDLEG",
                    ),
                )
            }
        }
    }

    fun decision(checklist: List<ChecklistItem>): RuleDecision = RuleDecision(
        key = "checklist",
        ruleId = RuleId.CHK_01,
        legalBasis = RuleId.CHK_01.legalBasis,
        value = checklist.joinToString(",") { it.id },
        message = "Checkliste: ${checklist.joinToString(", ") { it.shortText }}",
    )
}
