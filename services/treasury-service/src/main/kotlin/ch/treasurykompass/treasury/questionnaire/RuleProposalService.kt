package ch.treasurykompass.treasury.questionnaire

import ch.treasurykompass.treasury.audit.RuleReference
import ch.treasurykompass.treasury.rules.RuleId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/**
 * R-LIQ-01: as long as the minimum liquidity is not confirmed, the rules' guide value (1.5 monthly operating expenses)
 * is proposed with source RULE. A new proposal is created only when the guide value changes, so that a later
 * proposal from the documents or the chat is not overwritten by unrelated changes.
 */
@Service
class RuleProposalService(
    private val answerRepository: AnswerRepository,
    private val proposalRepository: AnswerProposalRepository,
    private val processor: ProposalProcessor,
    private val codec: AnswerValueCodec,
    private val jsonMapper: JsonMapper,
) {

    /** @return `true` if the minimum liquidity answer was written */
    @Transactional(propagation = Propagation.MANDATORY)
    fun proposeMinimumLiquidity(clientId: UUID, suggestedMinimumLiquidity: Double?): Boolean {
        if (suggestedMinimumLiquidity == null) return false
        val question = QuestionCatalog.require(QuestionCatalog.MINIMUM_LIQUIDITY)
        val suggestion = AnswerValue.amount(suggestedMinimumLiquidity)
        val answer = answerRepository.findById(AnswerId(clientId, question.key)).orElse(null)
        if (answer?.status == AnswerStatus.CONFIRMED) return false
        val latestRuleProposal = proposalRepository.findLatest(clientId, question.key, ProposalSource.RULE)
        if (latestRuleProposal != null && codec.decode(question, latestRuleProposal.value) == suggestion) return false

        val operatingExpenses = answerRepository.findById(AnswerId(clientId, QuestionCatalog.OPERATING_EXPENSES)).orElse(null)
        val basis = operatingExpenses?.proposalId?.let(proposalRepository::findById)?.orElse(null)
        val result = processor.process(
            clientId,
            ProposalCommand(
                questionKey = question.key,
                value = jsonMapper.valueToTree(suggestion.raw),
                source = ProposalSource.RULE,
                confidence = null,
                rationale = basis?.locationLabel?.let { "Vorschlag aus dem Betriebsaufwand ($it)" } ?: "Vorschlag aus dem Betriebsaufwand",
                citation = basis?.documentName?.let {
                    CitationData(basis.documentId, it, basis.page, basis.locationLabel, basis.excerpt, basis.citationVerified ?: false)
                },
                rule = RuleReference(RuleId.LIQ_01.code, RuleId.LIQ_01.legalBasis),
            ),
            // Minimum liquidity is always visible; the context only matters for opting-in.
            QuestionContext.UNKNOWN,
        )
        return result.answerChanged
    }
}
