package ch.treasurykompass.treasury.agentclient

import ch.treasurykompass.treasury.internalapi.InternalApiProperties
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.retry.RetryException
import org.springframework.core.retry.RetryPolicy
import org.springframework.core.retry.RetryTemplate
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID

@Validated
@ConfigurationProperties("treasury.agent")
data class AgentProperties(
    @field:NotNull val baseUrl: URI,
    @field:NotNull val connectTimeout: Duration,
    @field:NotNull val readTimeout: Duration,
    @field:Valid val retry: Retry,
) {
    /** Default: 3 retries after 1, 2 and 4 seconds (architecture 10.3). */
    data class Retry(
        @field:PositiveOrZero val maxRetries: Long,
        @field:NotNull val initialDelay: Duration,
        @field:Positive val multiplier: Double,
    )
}

/** Body of `POST /internal/ingest` (agent-service OpenAPI `IngestRequest`). */
data class IngestRequest(
    val ingestId: UUID,
    val documentId: UUID,
    val clientId: UUID,
    val filename: String,
    val sha256: String,
    val sizeBytes: Long,
    val pageCount: Int?,
)

/** The agent-service could not be reached after all retries. */
class AgentUnavailableException(message: String, cause: Throwable?) : RuntimeException(message, cause)

/**
 * The only place with HTTP calls to the agent-service. Retries connection problems and 5xx responses;
 * 4xx responses are programming errors and fail immediately.
 */
@Component
class AgentServiceClient(properties: AgentProperties, internalApi: InternalApiProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(properties.baseUrl.toString())
        .requestFactory(
            // HTTP/1.1: the JDK client otherwise sends an h2c upgrade, which uvicorn rejects with 400
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(properties.connectTimeout)
                    .build(),
            )
                .apply { setReadTimeout(properties.readTimeout) },
        )
        .defaultHeaders { headers -> internalApi.serviceKey?.let { headers.set(InternalApiProperties.HEADER, it) } }
        .build()

    private val retryTemplate = RetryTemplate(
        RetryPolicy.builder()
            .maxRetries(properties.retry.maxRetries)
            .delay(properties.retry.initialDelay)
            .multiplier(properties.retry.multiplier)
            .predicate { it is ResourceAccessException || it is HttpServerErrorException }
            .build(),
    )

    /** Starts the asynchronous ingest; the agent answers 202 and reports progress via status callbacks. */
    fun startIngest(request: IngestRequest) = withRetry("Ingest ${request.ingestId}") {
        restClient.post()
            .uri("/internal/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity()
    }

    /** Removes pages and chunks of a deleted document from the RAG index (idempotent on the agent side). */
    fun purgeDocument(documentId: UUID) = withRetry("Löschen von Dokument $documentId") {
        restClient.delete()
            .uri("/internal/documents/{documentId}", documentId)
            .retrieve()
            .toBodilessEntity()
    }

    private fun withRetry(operation: String, call: () -> Any?) {
        try {
            retryTemplate.execute { call() }
        } catch (ex: RetryException) {
            log.warn("agent-service nicht erreichbar: {} nach {} Versuchen", operation, ex.retryCount + 1)
            throw AgentUnavailableException("agent-service nicht erreichbar: $operation", ex.cause)
        }
    }
}
