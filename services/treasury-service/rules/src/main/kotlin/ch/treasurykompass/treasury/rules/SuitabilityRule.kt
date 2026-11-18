package ch.treasurykompass.treasury.rules

/** R-SUI-01 and R-SUI-02 (regelwerk § 4, Art. 12, 14 und 58 FIDLEG). */
internal object SuitabilityRule {
    private const val KID_LEGAL_BASIS = "Art. 58 FIDLEG"

    /**
     * @param knowledge must contain every financial instrument for retail clients; ignored for professional
     *   clients, whose knowledge and experience are presumed
     */
    fun evaluate(segment: ClientSegment, knowledge: Map<Product, KnowledgeLevel>): List<Suitability> {
        val presumed = segment != ClientSegment.RETAIL
        return Product.entries.map { product ->
            val level = if (presumed || !product.financialInstrument) {
                null
            } else {
                requireNotNull(knowledge[product]) { "Knowledge for $product is required for retail clients" }
            }
            assess(product, presumed, level)
        }
    }

    fun decisions(segment: ClientSegment, suitability: List<Suitability>): List<RuleDecision> =
        suitability.map { item ->
            RuleDecision(
                key = "suitability.${item.product}",
                ruleId = RuleId.SUI_01,
                legalBasis = item.legalBasis ?: RuleId.SUI_01.legalBasis,
                value = item.status.name,
                message = "${item.productLabel}: ${item.label}, ${item.note}",
            )
        } + kidDecision(segment, suitability)

    private fun assess(product: Product, presumed: Boolean, level: KnowledgeLevel?): Suitability {
        val outcome = when {
            !product.financialInstrument ->
                Outcome(SuitabilityStatus.NOT_APPLICABLE, "Einlage, kein Finanzinstrument nach FIDLEG", null)
            presumed -> Outcome(SuitabilityStatus.SUITABLE, "Kenntnisse und Erfahrung vermutet", null)
            level == KnowledgeLevel.EXPERIENCE ->
                Outcome(SuitabilityStatus.SUITABLE, "Kenntnisse und Erfahrung vorhanden", "Art. 12 FIDLEG")
            level == KnowledgeLevel.KNOWLEDGE ->
                Outcome(SuitabilityStatus.SUITABLE_AFTER_DISCLOSURE, "Erfahrung fehlt, Warnhinweis dokumentieren", "Art. 12 FIDLEG")
            else -> Outcome(SuitabilityStatus.NOT_SUITABLE, "keine Kenntnisse, abraten", "Art. 14 FIDLEG")
        }
        return Suitability(
            product = product,
            productLabel = product.label,
            use = product.use,
            status = outcome.status,
            label = outcome.status.label,
            note = outcome.note,
            knowledgeLevel = level,
            knowledgePresumed = presumed && product.financialInstrument,
            kidRequired = !presumed && product.kidRequiredForRetail,
            legalBasis = outcome.legalBasis,
        )
    }

    private fun kidDecision(segment: ClientSegment, suitability: List<Suitability>): RuleDecision {
        val kidProducts = suitability.filter { it.kidRequired }
        return RuleDecision(
            key = "kid",
            ruleId = RuleId.SUI_02,
            legalBasis = KID_LEGAL_BASIS,
            value = kidProducts.joinToString(",") { it.product.name }.ifEmpty { "NONE" },
            message = if (kidProducts.isEmpty()) {
                "Kein Basisinformationsblatt nötig (${segment.label})"
            } else {
                "Basisinformationsblatt nötig: ${kidProducts.joinToString(", ") { it.productLabel }}"
            },
        )
    }

    private data class Outcome(val status: SuitabilityStatus, val note: String, val legalBasis: String?)
}
