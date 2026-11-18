package ch.treasurykompass.treasury.common

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@ConfigurationProperties("treasury.advisor")
data class AdvisorProperties(
    @field:NotBlank val id: String,
    @field:NotBlank val name: String,
)

/** The acting advisor. User management is out of scope (architecture 12), the identity is mocked from configuration. */
data class Advisor(val id: String, val name: String)

/**
 * Single source of the current advisor identity. Replace with the OIDC principal once authentication exists.
 */
@Component
class AdvisorContext(private val properties: AdvisorProperties) {
    fun current(): Advisor = Advisor(properties.id, properties.name)
}

@RestController
@RequestMapping("/api/me")
class MeController(private val advisorContext: AdvisorContext) {
    @GetMapping
    fun me(): Advisor = advisorContext.current()
}
