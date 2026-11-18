package ch.treasurykompass.treasury.common

/** A single field error in the `errors` extension of a `validation-error` problem. */
data class FieldError(val field: String, val message: String)

/**
 * Domain error that is rendered as `application/problem+json`.
 *
 * @property properties additional problem extensions such as `errors` or `blockers`
 */
open class ProblemException(
    val problem: ProblemType,
    val detail: String,
    val properties: Map<String, Any> = emptyMap(),
) : RuntimeException(detail)

class NotFoundException(detail: String) : ProblemException(ProblemType.NOT_FOUND, detail)

class ValidationException(val errors: List<FieldError>, detail: String = errors.joinToString("; ") { it.message }) :
    ProblemException(ProblemType.VALIDATION_ERROR, detail, mapOf("errors" to errors)) {

    constructor(field: String, message: String, detail: String = "$field: $message") :
        this(listOf(FieldError(field, message)), detail)
}
