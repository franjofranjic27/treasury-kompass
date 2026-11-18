package ch.treasurykompass.treasury.assessment

import ch.treasurykompass.treasury.advisory.HedgeSettingRepository
import ch.treasurykompass.treasury.marketdata.MarketDataView
import ch.treasurykompass.treasury.questionnaire.AnswerProposalRepository
import ch.treasurykompass.treasury.questionnaire.AnswerRepository
import ch.treasurykompass.treasury.questionnaire.AnswerSource
import ch.treasurykompass.treasury.questionnaire.AnswerValue
import ch.treasurykompass.treasury.questionnaire.AnswerValueCodec
import ch.treasurykompass.treasury.questionnaire.QuestionCatalog
import ch.treasurykompass.treasury.rules.AssessmentInput
import ch.treasurykompass.treasury.rules.FinancialsInput
import ch.treasurykompass.treasury.rules.FxFlowInput
import ch.treasurykompass.treasury.rules.ForgoneGainAttitude
import ch.treasurykompass.treasury.rules.GoalsInput
import ch.treasurykompass.treasury.rules.HedgeObjective
import ch.treasurykompass.treasury.rules.HedgeSetting
import ch.treasurykompass.treasury.rules.InputKey
import ch.treasurykompass.treasury.rules.InvestmentHorizon
import ch.treasurykompass.treasury.rules.KnowledgeLevel
import ch.treasurykompass.treasury.rules.MarketData
import ch.treasurykompass.treasury.rules.Product
import ch.treasurykompass.treasury.rules.RiskToleranceInput
import ch.treasurykompass.treasury.rules.SegmentInput
import ch.treasurykompass.treasury.rules.TolerableDrawdown
import ch.treasurykompass.treasury.rules.TreasuryOrganisation
import org.springframework.stereotype.Component
import java.util.UUID
import ch.treasurykompass.treasury.rules.Currency as RuleCurrency

/** Everything the rule engine needs for one client; also the basis of the inputs hash. */
data class EngineInputs(
    val input: AssessmentInput,
    val hedgeSettings: Map<RuleCurrency, HedgeSetting>,
    val marketData: MarketData,
)

/** Overrides for what-if simulations; nothing is persisted. */
data class InputOverrides(
    val answers: Map<String, AnswerValue> = emptyMap(),
    val hedgeSettings: Map<RuleCurrency, HedgeSetting> = emptyMap(),
) {
    companion object {
        val NONE = InputOverrides()
    }
}

/**
 * Builds the rule input from the stored answers (status PROPOSED or CONFIRMED, as in the prototype),
 * the advisor's hedge settings and the market data.
 */
@Component
class RuleInputAssembler(
    private val answerRepository: AnswerRepository,
    private val proposalRepository: AnswerProposalRepository,
    private val hedgeSettingRepository: HedgeSettingRepository,
    private val codec: AnswerValueCodec,
) {

    fun assemble(clientId: UUID, marketData: MarketDataView, overrides: InputOverrides = InputOverrides.NONE): EngineInputs {
        val answers = answerRepository.findByIdClientId(clientId)
        val values = answers.associate { it.questionKey to codec.decode(it.questionKey, it.value) } + overrides.answers
        val evidence = knowledgeEvidence(answers.filter { it.source == AnswerSource.DOCUMENT && it.questionKey !in overrides.answers })
        val storedSettings = hedgeSettingRepository.findByIdClientId(clientId).associate {
            RuleCurrency.valueOf(it.id.currency) to HedgeSetting(it.startRatio, it.stepDown, it.collarEnabled)
        }
        return EngineInputs(
            input = toInput(values, evidence),
            hedgeSettings = sortedByCurrency(storedSettings + overrides.hedgeSettings),
            marketData = toMarketData(marketData),
        )
    }

    /** Citation label of document-based knowledge answers, used verbatim in the reasons (prototype `prefilled`). */
    private fun knowledgeEvidence(documentAnswers: List<ch.treasurykompass.treasury.questionnaire.Answer>): Map<Product, String> {
        val knowledgeAnswers = documentAnswers.filter { KNOWLEDGE_PRODUCTS.containsKey(it.questionKey) && it.proposalId != null }
        if (knowledgeAnswers.isEmpty()) return emptyMap()
        val proposals = proposalRepository.findByIdIn(knowledgeAnswers.mapNotNull { it.proposalId }).associateBy { it.id }
        return KNOWLEDGE_PRODUCTS.entries.mapNotNull { (key, product) ->
            val proposal = knowledgeAnswers.firstOrNull { it.questionKey == key }?.proposalId?.let(proposals::get)
            val label = proposal?.locationLabel ?: proposal?.documentName
            label?.let { product to it }
        }.toMap()
    }

    private fun toInput(values: Map<String, AnswerValue>, evidence: Map<Product, String>): AssessmentInput {
        fun amount(key: InputKey) = (values[key.key] as? AnswerValue.Amount)?.value
        fun percent(key: InputKey) = (values[key.key] as? AnswerValue.Percent)?.value
        fun choice(key: InputKey) = (values[key.key] as? AnswerValue.Choice)?.code
        fun flag(key: InputKey) = (values[key.key] as? AnswerValue.Flag)?.value

        return AssessmentInput(
            financials = FinancialsInput(
                revenue = amount(InputKey.REVENUE),
                totalAssets = amount(InputKey.TOTAL_ASSETS),
                equity = amount(InputKey.EQUITY),
                ebit = amount(InputKey.EBIT),
                cash = amount(InputKey.CASH),
                operatingExpenses = amount(InputKey.OPERATING_EXPENSES),
                floatingRateDebt = amount(InputKey.FLOATING_RATE_DEBT),
            ),
            fxFlows = RuleCurrency.entries.associateWith { FxFlowInput(amount(it.inflowKey), amount(it.outflowKey)) },
            segment = SegmentInput(
                treasuryOrganisation = choice(InputKey.TREASURY_ORGANISATION)?.let(TreasuryOrganisation::valueOf),
                optingIn = flag(InputKey.OPTING_IN),
            ),
            goals = GoalsInput(
                hedgeObjective = choice(InputKey.HEDGE_OBJECTIVE)?.let(HedgeObjective::valueOf),
                policyMaxHedgeRatio = percent(InputKey.POLICY_MAX_HEDGE_RATIO),
                minimumLiquidity = amount(InputKey.MINIMUM_LIQUIDITY),
                investmentHorizon = choice(InputKey.INVESTMENT_HORIZON)?.let(InvestmentHorizon::valueOf),
            ),
            riskTolerance = RiskToleranceInput(
                tolerableDrawdown = choice(InputKey.TOLERABLE_DRAWDOWN)?.let(TolerableDrawdown::valueOf),
                forgoneGainAttitude = choice(InputKey.FORGONE_GAIN_ATTITUDE)?.let(ForgoneGainAttitude::valueOf),
            ),
            knowledge = KNOWLEDGE_PRODUCTS.entries.mapNotNull { (key, product) ->
                (values[key] as? AnswerValue.Choice)?.let { product to KnowledgeLevel.valueOf(it.code) }
            }.toMap(),
            knowledgeEvidence = evidence,
        )
    }

    private fun toMarketData(view: MarketDataView) = MarketData(
        spotRates = RuleCurrency.entries.associateWith { view.spotRates.getValue(it.name) },
        chfInterestRate = view.interestRates.getValue("CHF"),
        interestRates = RuleCurrency.entries.associateWith { view.interestRates.getValue(it.name) },
    )

    private fun sortedByCurrency(settings: Map<RuleCurrency, HedgeSetting>): Map<RuleCurrency, HedgeSetting> =
        RuleCurrency.entries.mapNotNull { currency -> settings[currency]?.let { currency to it } }.toMap()

    private companion object {
        val KNOWLEDGE_PRODUCTS: Map<String, Product> = Product.financialInstruments.associateBy { checkNotNull(it.knowledgeKey).key }

        init {
            check(KNOWLEDGE_PRODUCTS.keys.all { QuestionCatalog.find(it) != null }) { "Kenntnisfragen fehlen im Katalog" }
        }
    }
}
