package ch.treasurykompass.treasury.rules

/**
 * Foreign currencies in the MVP. Amounts are in millions of the currency, rates in CHF per unit.
 * Order matters: proposals, reasons and decisions follow it (EUR before USD, as in the prototype).
 */
enum class Currency(val inflowKey: InputKey, val outflowKey: InputKey) {
    EUR(InputKey.FX_EUR_INFLOW, InputKey.FX_EUR_OUTFLOW),
    USD(InputKey.FX_USD_INFLOW, InputKey.FX_USD_OUTFLOW),
}

enum class ProductUse(val label: String) {
    HEDGING("Absicherung"),
    LIQUIDITY_INVESTMENT("Liquiditätsanlage"),
}

/**
 * Products of the MVP in prototype order (`PRODUCTS`).
 *
 * @property label short name used in texts (prototype `short`)
 * @property fullName long name (prototype `name`)
 * @property financialInstrument `false` for deposits, which FIDLEG does not cover
 * @property kidRequiredForRetail a key information document (Art. 58 FIDLEG) is needed for retail clients
 * @property knowledgeKey questionnaire key for knowledge and experience, `null` if no assessment is needed
 */
enum class Product(
    val label: String,
    val fullName: String,
    val use: ProductUse,
    val financialInstrument: Boolean,
    val kidRequiredForRetail: Boolean,
    val knowledgeKey: InputKey?,
) {
    FX_FORWARD(
        "Devisentermingeschäft", "Devisentermingeschäft",
        ProductUse.HEDGING, true, true, InputKey.KNOWLEDGE_FX_FORWARD,
    ),
    ZERO_COST_COLLAR(
        "Zero-Cost-Collar", "Zero-Cost-Collar (Devisenoptionen)",
        ProductUse.HEDGING, true, true, InputKey.KNOWLEDGE_ZERO_COST_COLLAR,
    ),
    MONEY_MARKET_FUND(
        "Geldmarktfonds CHF", "Geldmarktfonds CHF",
        ProductUse.LIQUIDITY_INVESTMENT, true, true, InputKey.KNOWLEDGE_MONEY_MARKET_FUND,
    ),
    SHORT_TERM_BOND(
        "Obligationen CHF kurz", "Obligationen CHF, Investment Grade, kurze Laufzeit",
        ProductUse.LIQUIDITY_INVESTMENT, true, false, InputKey.KNOWLEDGE_SHORT_TERM_BOND,
    ),
    DEPOSIT(
        "Festgeld / Callgeld", "Festgeld und Callgeld",
        ProductUse.LIQUIDITY_INVESTMENT, false, false, null,
    );

    companion object {
        /** Products that need a knowledge and experience assessment for retail clients. */
        val financialInstruments: List<Product> = entries.filter { it.financialInstrument }
    }
}

enum class KnowledgeLevel(val label: String) {
    NONE("Keine"),
    KNOWLEDGE("Kenntnisse"),
    EXPERIENCE("Kenntnisse und Erfahrung"),
}

enum class TreasuryOrganisation(val label: String) {
    TREASURY_DEPARTMENT("Eigene Treasury-Abteilung mit Fachpersonen"),
    CFO_OR_ACCOUNTING("CFO oder Buchhaltung, neben anderen Aufgaben"),
    OUTSOURCED("An externen Dienstleister ausgelagert"),
}

enum class HedgeObjective(val label: String) {
    BUDGET_CERTAINTY("Budgetsicherheit: Plankurs halten"),
    PROTECT_MARGINS("Margen schützen, Chancen teilweise offen lassen"),
    LOW_HEDGING_COST("Absicherungskosten tief halten"),
}

enum class TolerableDrawdown(val label: String) {
    NONE("Keiner, Kapitalerhalt"),
    UP_TO_2_PERCENT("Bis 2 %"),
    UP_TO_5_PERCENT("Bis 5 %"),
    OVER_5_PERCENT("Mehr als 5 %"),
}

enum class ForgoneGainAttitude(val label: String) {
    ACCEPTABLE("Akzeptabel, Planungssicherheit zählt"),
    TOLERABLE("Ärgerlich, aber tragbar"),
    NOT_ACCEPTABLE("Nicht akzeptabel"),
}

enum class InvestmentHorizon(val label: String) {
    UNDER_1_YEAR("Unter 1 Jahr"),
    ONE_TO_THREE_YEARS("1 bis 3 Jahre"),
    OVER_THREE_YEARS("Über 3 Jahre"),
}

/** Client segment (Art. 4 FIDLEG). [INSTITUTIONAL] is reserved and never computed for corporate clients. */
enum class ClientSegment(val label: String) {
    RETAIL("Privatkunde"),
    PROFESSIONAL("Professioneller Kunde"),
    INSTITUTIONAL("Institutioneller Kunde"),
}

enum class RiskProfileCode(val score: Int, val label: String) {
    SAFETY(1, "Sicherheit"),
    CONSERVATIVE(2, "Konservativ"),
    BALANCED(3, "Ausgewogen"),
    GROWTH(4, "Wachstum"),
    DYNAMIC(5, "Dynamisch");

    companion object {
        fun ofScore(score: Int): RiskProfileCode =
            entries.firstOrNull { it.score == score } ?: throw IllegalArgumentException("No risk profile for score $score")
    }
}

enum class SuitabilityStatus(val label: String) {
    SUITABLE("Geeignet"),
    SUITABLE_AFTER_DISCLOSURE("Nur nach Aufklärung"),
    NOT_SUITABLE("Nicht geeignet"),
    NOT_APPLICABLE("Keine Prüfung nötig"),
}

enum class RiskLevel(val label: String) {
    LOW("Risiko tief"),
    MEDIUM("Risiko mittel"),
    HIGH("Risiko hoch"),
}

enum class ExposureDirection {
    NET_INFLOW,
    NET_OUTFLOW,
    NONE,
}

/** [SELL_FORWARD] hedges a net inflow, [BUY_FORWARD] a net outflow. */
enum class HedgeAction {
    SELL_FORWARD,
    BUY_FORWARD,
}

enum class AssessmentStatus {
    COMPLETE,
    INCOMPLETE,
}

enum class ThresholdMetric(val label: String) {
    TOTAL_ASSETS("Bilanzsumme"),
    REVENUE("Umsatzerlös"),
    EQUITY("Eigenkapital"),
}

enum class CapacityFactor(val label: String) {
    EQUITY_RATIO("Eigenkapitalquote"),
    EXCESS_LIQUIDITY_MONTHS("Überschussliquidität"),
    EBIT_MARGIN("EBIT-Marge"),
    UNHEDGED_FX_RISK("Ungesichertes Währungsrisiko"),
}

enum class WillingnessFactor(val label: String) {
    HEDGE_OBJECTIVE("Ziel der Absicherung"),
    TOLERABLE_DRAWDOWN("Tragbarer Wertrückgang"),
    FORGONE_GAIN("Entgangener Kursgewinn"),
}
