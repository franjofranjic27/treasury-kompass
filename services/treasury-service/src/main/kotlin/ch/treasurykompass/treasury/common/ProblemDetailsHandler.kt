package ch.treasurykompass.treasury.common

import jakarta.servlet.http.HttpServletRequest
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/**
 * Renders every error as RFC 9457 problem details with `type = urn:treasury-kompass:problem:<slug>`
 * and the `code` extension (architecture 10.2). Framework exceptions are mapped by HTTP status.
 */
@RestControllerAdvice
class ProblemDetailsHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ProblemException::class)
    fun handleProblem(ex: ProblemException, request: HttpServletRequest): ResponseEntity<ProblemDetail> =
        respond(problemDetail(ex.problem, ex.detail, ex.properties, request.requestURI))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        val constraint = (ex.cause as? ConstraintViolationException)?.constraintName.orEmpty()
        val (type, detail) = when {
            constraint.contains("client_name_lower_uq") -> ProblemType.DUPLICATE_CLIENT to "Ein Kunde mit diesem Namen existiert bereits."
            constraint.contains("document_client_sha256_uq") -> ProblemType.DUPLICATE_DOCUMENT to "Diese Datei wurde für diesen Kunden bereits hochgeladen."
            constraint.contains("approval_active_uq") -> ProblemType.APPROVAL_BLOCKED to "Es besteht bereits eine aktive Freigabe."
            else -> {
                log.error("Unerwartete Integritätsverletzung (Constraint '{}')", constraint, ex)
                ProblemType.INTERNAL_ERROR to INTERNAL_ERROR_DETAIL
            }
        }
        return respond(problemDetail(type, detail, emptyMap(), request.requestURI))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        log.error("Unerwarteter Fehler bei {} {}", request.method, request.requestURI, ex)
        return respond(problemDetail(ProblemType.INTERNAL_ERROR, INTERNAL_ERROR_DETAIL, emptyMap(), request.requestURI))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors.map { FieldError(it.field, it.defaultMessage ?: "ungültiger Wert") } +
            ex.bindingResult.globalErrors.map { FieldError(it.objectName, it.defaultMessage ?: "ungültiger Wert") }
        return validationProblem(errors, request)
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.parameterValidationResults.flatMap { result ->
            val name = result.methodParameter.parameterName ?: "parameter"
            result.resolvableErrors.map { FieldError(name, it.defaultMessage ?: "ungültiger Wert") }
        }
        return validationProblem(errors, request)
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = problemDetail(
            ProblemType.VALIDATION_ERROR,
            "Der Request-Body fehlt, ist kein gültiges JSON oder enthält Felder mit falschem Typ.",
            emptyMap(),
            requestUri(request),
        )
        return ResponseEntity.status(problem.status).body(problem)
    }

    /** Normalises the bodies produced by [ResponseEntityExceptionHandler] for standard MVC exceptions. */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val type = ProblemType.forStatus(statusCode.value())
        val problem = problemDetail(type, defaultDetail(type), emptyMap(), requestUri(request))
        problem.status = statusCode.value()
        return ResponseEntity.status(statusCode).headers(headers).body(problem)
    }

    private fun validationProblem(errors: List<FieldError>, request: WebRequest): ResponseEntity<Any> {
        val detail = errors.joinToString("; ") { "${it.field}: ${it.message}" }.ifBlank { defaultDetail(ProblemType.VALIDATION_ERROR) }
        val problem = problemDetail(ProblemType.VALIDATION_ERROR, detail, mapOf("errors" to errors), requestUri(request))
        return ResponseEntity.status(problem.status).body(problem)
    }

    private fun respond(problem: ProblemDetail): ResponseEntity<ProblemDetail> = ResponseEntity.status(problem.status).body(problem)

    private fun requestUri(request: WebRequest): String? = (request as? ServletWebRequest)?.request?.requestURI

    private fun defaultDetail(type: ProblemType): String = when (type) {
        ProblemType.NOT_FOUND -> "Die angeforderte Ressource existiert nicht."
        ProblemType.METHOD_NOT_ALLOWED -> "Diese Methode ist für die Ressource nicht erlaubt."
        ProblemType.PAYLOAD_TOO_LARGE -> "Die Datei überschreitet die maximale Grösse."
        ProblemType.UNSUPPORTED_MEDIA_TYPE -> "Der Inhaltstyp der Anfrage wird nicht unterstützt."
        ProblemType.VALIDATION_ERROR -> "Die Anfrage ist ungültig."
        ProblemType.UPSTREAM_UNAVAILABLE -> "Ein abhängiger Dienst ist nicht erreichbar."
        else -> INTERNAL_ERROR_DETAIL
    }

    companion object {
        private const val INTERNAL_ERROR_DETAIL = "Ein unerwarteter Fehler ist aufgetreten."

        fun problemDetail(type: ProblemType, detail: String, properties: Map<String, Any>, instance: String?): ProblemDetail =
            ProblemDetail.forStatusAndDetail(type.status, detail).apply {
                this.type = type.type
                this.title = type.title
                if (instance != null) this.instance = URI.create(instance)
                setProperty("code", type.slug)
                properties.forEach { (key, value) -> setProperty(key, value) }
            }
    }
}
