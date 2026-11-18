package ch.treasurykompass.treasury.common

import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import java.time.Clock

@Configuration(proxyBeanMethods = false)
@EnableAsync
@ConfigurationPropertiesScan("ch.treasurykompass.treasury")
class ApplicationConfig {

    /** All timestamps are UTC (architecture 5); tests can replace the clock. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
