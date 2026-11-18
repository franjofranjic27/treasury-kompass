package ch.treasurykompass.treasury.rules

import kotlin.math.abs

internal data class ReasonFacts(
    val segment: ClientSegment,
    val profile: RiskProfileCode,
    val hedgeObjective: HedgeObjective,
    val forgoneGainAttitude: ForgoneGainAttitude,
    val horizon: InvestmentHorizon,
    val fxForward: SuitabilityStatus,
    val fxForwardEvidence: String?,
    val hedges: HedgeOverview,
    val liquidity: LiquidityAllocation,
)

/**
 * Rule texts, verbatim from the prototype's `reasons()` and `protoSections()`. The prototype marks the subject
 * («EUR:», «Liquidität:») with `<strong>`; the API delivers plain text, as the prototype's `protoText()` does.
 */
internal object ReasonWriter {
    private const val CAPITAL_PRESERVATION_MAX_PROFILE_SCORE = 2

    fun reasons(facts: ReasonFacts): List<String> = buildList {
        facts.hedges.proposals.forEach { add(hedgeReason(it, facts)) }
        facts.hedges.naturallyHedged.forEach { add(naturallyHedgedReason(it)) }
        add(liquidityReason(facts))
    }

    fun protocolTexts(hedges: HedgeOverview, liquidity: LiquidityAllocation, suitability: List<Suitability>): ProtocolTexts {
        val recommendations = buildList {
            hedges.proposals.forEach { add(recommendationLine(it)) }
            if (!liquidity.noExcess) {
                liquidity.parts.filter { it.sharePct > 0 }.forEach { part ->
                    add("${part.product.label}: CHF ${SwissNumberFormat.number(part.amountChf, 2)} Mio. (${part.sharePct} %)")
                }
            }
        }
        val notRecommended = suitability
            .filter { it.status == SuitabilityStatus.NOT_SUITABLE }
            .map { "${it.productLabel}: ${it.note} (Art. 14 FIDLEG)" }
        return ProtocolTexts(
            recommendations = recommendations.ifEmpty { listOf("Keine Empfehlung") },
            notRecommended = notRecommended.ifEmpty { listOf("Keine") },
        )
    }

    /** «80 / 70 / 60 / 50 % (Ø 65 %)» */
    fun staggering(proposal: HedgeProposal): String =
        "${proposal.tranches.joinToString(" / ") { it.effectiveRatio.toString() }} % " +
            "(Ø ${SwissNumberFormat.number(proposal.averageRatio, 0)} %)"

    private fun hedgeReason(proposal: HedgeProposal, facts: ReasonFacts): String {
        val inflow = proposal.direction == ExposureDirection.NET_INFLOW
        val currency = proposal.currency
        val flow = if (inflow) {
            "Nettozufluss von ${SwissNumberFormat.number(proposal.netPosition)} Mio. $currency"
        } else {
            "Nettoabfluss von ${SwissNumberFormat.number(-proposal.netPosition)} Mio. $currency"
        }
        val move = if (inflow) "um 10 % fällt" else "um 10 % steigt"
        val intro = "$currency: Der erwartete $flow belastet das EBIT um CHF ${SwissNumberFormat.number(proposal.sensitivityChf, 2)} Mio. " +
            "(${SwissNumberFormat.percent(proposal.sensitivityPctEbit)} des EBIT), wenn der $currency $move."
        if (proposal.blocked) {
            return "$intro Devisentermingeschäfte werden derzeit nicht empfohlen, weil keine Kenntnisse vorliegen. " +
                "Nach einer dokumentierten Aufklärung ist die Empfehlung neu zu prüfen."
        }
        return buildString {
            append(intro)
            append(" Bei Risikoprofil ${facts.profile.label} und dem Ziel «${facts.hedgeObjective.label}» empfehlen wir eine ")
            append("gestaffelte Absicherung von ${proposal.tranches.joinToString(" / ") { it.effectiveRatio.toString() }} % je Quartal ")
            append("(Ø ${SwissNumberFormat.number(proposal.averageRatio, 0)} %).")
            append(fxForwardSentence(facts))
            when {
                proposal.collarEnabled -> append(
                    " 30 % laufen über einen Zero-Cost-Collar, damit ein Teil der Kurschancen offen bleibt. " +
                        "Das entspricht der Antwort «${facts.forgoneGainAttitude.label}».",
                )
                !proposal.collarAllowed -> append(" Devisenoptionen werden nicht empfohlen, weil keine Kenntnisse vorliegen.")
            }
            append(
                if (proposal.carryChf < 0) {
                    " Die Zinsdifferenz kostet rund CHF ${SwissNumberFormat.number(-proposal.carryChf, 2)} Mio."
                } else {
                    " Die Zinsdifferenz bringt rund CHF ${SwissNumberFormat.number(proposal.carryChf, 2)} Mio."
                },
            )
        }
    }

    private fun fxForwardSentence(facts: ReasonFacts): String = when {
        facts.segment == ClientSegment.PROFESSIONAL ->
            " Beim professionellen Kunden werden Kenntnisse und Erfahrung mit Devisentermingeschäften vermutet."
        facts.fxForward == SuitabilityStatus.SUITABLE ->
            " Devisentermingeschäfte sind geeignet, weil Kenntnisse und Erfahrung belegt sind" +
                (facts.fxForwardEvidence?.let { " ($it)" } ?: "") + "."
        else -> " Devisentermingeschäfte nur nach dokumentierter Aufklärung, weil Erfahrung fehlt."
    }

    private fun naturallyHedgedReason(position: NaturallyHedgedPosition): String =
        "${position.currency}: Die Nettoposition von ${SwissNumberFormat.number(abs(position.netPosition))} Mio. ${position.currency} " +
            "ist klein. Zu- und Abflüsse gleichen sich weitgehend aus, eine Absicherung ist nicht nötig."

    private fun liquidityReason(facts: ReasonFacts): String {
        val liquidity = facts.liquidity
        if (liquidity.noExcess) return "Liquidität: Keine Überschussliquidität, daher keine Anlageempfehlung."
        val stance = if (facts.profile.score <= CAPITAL_PRESERVATION_MAX_PROFILE_SCORE) {
            "steht der Kapitalerhalt im Vordergrund"
        } else {
            "ist eine höhere Beimischung von Obligationen vertretbar"
        }
        val shifted = if (liquidity.shiftedProducts.isNotEmpty()) " Produkte ohne ausreichende Kenntnisse wurden umgeschichtet." else ""
        return "Liquidität: Nach Abzug der Mindestliquidität sind CHF ${SwissNumberFormat.number(liquidity.excessLiquidity)} Mio. " +
            "anlagefähig. Beim Horizont «${facts.horizon.label}» und Profil ${facts.profile.label} $stance: " +
            "${LiquidityAllocator.sharesText(liquidity)}.$shifted"
    }

    private fun recommendationLine(proposal: HedgeProposal): String {
        if (proposal.blocked) return "${proposal.actionLabel}: keine Empfehlung, Eignung fehlt"
        val instruments = if (proposal.collarEnabled) "70 % Devisentermingeschäfte und 30 % Zero-Cost-Collar" else "Devisentermingeschäfte"
        return "${proposal.actionLabel}: gestaffelt ${staggering(proposal)}, " +
            "Volumen ${SwissNumberFormat.number(proposal.hedgedAmountFx, 2)} Mio. ${proposal.currency}, $instruments"
    }
}
