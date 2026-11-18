package ch.treasurykompass.treasury.questionnaire

import java.math.BigDecimal
import java.math.RoundingMode

/** Typed answer value. [raw] is the JSON representation (number, string or boolean). */
sealed interface AnswerValue {
    val raw: Any

    data class Amount(val value: Double) : AnswerValue {
        override val raw: Any get() = value
    }

    data class Percent(val value: Int) : AnswerValue {
        override val raw: Any get() = value
    }

    /** Code of a CHOICE or KNOWLEDGE_LEVEL option. */
    data class Choice(val code: String) : AnswerValue {
        override val raw: Any get() = code
    }

    data class Flag(val value: Boolean) : AnswerValue {
        override val raw: Any get() = value
    }

    companion object {
        /** Amounts are kept in Mio. with two decimals, rounded half-up (architecture 6). */
        fun amount(value: Double): Amount = Amount(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble())
    }
}

enum class AnswerStatus { PROPOSED, CONFIRMED }

enum class AnswerSource { DOCUMENT, RULE, AGENT, ADVISOR }

enum class ProposalSource { DOCUMENT, RULE, AGENT;

    fun toAnswerSource(): AnswerSource = AnswerSource.valueOf(name)
}

enum class ProposalStatus { OPEN, APPLIED, ACCEPTED, REJECTED, SUPERSEDED }

enum class Confidence { HIGH, MEDIUM, LOW }

enum class ProposalOutcome { APPLIED, OPEN, UNCHANGED, REJECTED }

enum class RejectionReason { NOT_EXTRACTABLE, INVALID_VALUE, UNKNOWN_KEY, NOT_APPLICABLE }
