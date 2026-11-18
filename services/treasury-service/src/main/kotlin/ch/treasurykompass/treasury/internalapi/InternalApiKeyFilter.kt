package ch.treasurykompass.treasury.internalapi

import ch.treasurykompass.treasury.common.ProblemType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest

/**
 * Shared service key for all paths under `/internal` (architecture 12). Only from the environment (`TREASURY_INTERNAL_API_KEY`);
 * without a key the internal API rejects every request (fail closed).
 */
@ConfigurationProperties("treasury.internal-api")
data class InternalApiProperties(val serviceKey: String?) {

    init {
        require(serviceKey.isNullOrEmpty() || serviceKey.length >= MIN_KEY_LENGTH) {
            "TREASURY_INTERNAL_API_KEY muss mindestens $MIN_KEY_LENGTH Zeichen lang sein"
        }
    }

    val configured: Boolean get() = !serviceKey.isNullOrEmpty()

    // Never print the key (e.g. in actuator or debug output).
    override fun toString(): String = "InternalApiProperties(configured=$configured)"

    companion object {
        const val HEADER = "X-Service-Key"
        const val MIN_KEY_LENGTH = 32
    }
}

/** Rejects requests under `/internal` without the correct `X-Service-Key` with 401 `unauthorized`. */
class InternalApiKeyFilter(
    private val properties: InternalApiProperties,
    private val jsonMapper: JsonMapper,
) : OncePerRequestFilter() {

    private val expectedKey: ByteArray? = properties.serviceKey?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (isAuthorized(request.getHeader(InternalApiProperties.HEADER))) {
            filterChain.doFilter(request, response)
            return
        }
        // Written by hand: filters run outside Spring MVC, so ProblemDetail's property flattening is not available here.
        val type = ProblemType.UNAUTHORIZED
        val problem = mapOf(
            "type" to type.type.toString(),
            "title" to type.title,
            "status" to type.status.value(),
            "detail" to "Header ${InternalApiProperties.HEADER} fehlt oder ist ungültig.",
            "instance" to request.requestURI,
            "code" to type.slug,
        )
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(response.outputStream, problem)
    }

    private fun isAuthorized(presentedKey: String?): Boolean {
        val expected = expectedKey ?: return false
        if (presentedKey == null) return false
        // Constant-time comparison, so that the key cannot be guessed byte by byte from response times.
        return MessageDigest.isEqual(expected, presentedKey.toByteArray(Charsets.UTF_8))
    }
}

@Configuration(proxyBeanMethods = false)
class InternalApiConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun internalApiKeyFilter(properties: InternalApiProperties, jsonMapper: JsonMapper): FilterRegistrationBean<InternalApiKeyFilter> {
        if (!properties.configured) {
            log.warn("TREASURY_INTERNAL_API_KEY ist nicht gesetzt: /internal/** lehnt alle Anfragen ab")
        }
        return FilterRegistrationBean(InternalApiKeyFilter(properties, jsonMapper)).apply {
            addUrlPatterns("/internal/*")
            order = Ordered.HIGHEST_PRECEDENCE + 10
        }
    }
}
