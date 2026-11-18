package ch.treasurykompass.treasury.questionnaire

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/** Converts answer values to and from the JSON literals stored in `answer.value` and `answer_proposal.value`. */
@Component
class AnswerValueCodec(private val jsonMapper: JsonMapper) {

    fun encode(value: AnswerValue): String = jsonMapper.writeValueAsString(value.raw)

    fun decode(question: QuestionDefinition, json: String): AnswerValue = question.parse(jsonMapper.readTree(json))

    fun decode(questionKey: String, json: String): AnswerValue = decode(QuestionCatalog.require(questionKey), json)
}
