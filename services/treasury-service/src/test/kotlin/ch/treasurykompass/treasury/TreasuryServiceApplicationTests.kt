package ch.treasurykompass.treasury

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Smoke test: the context starts against PostgreSQL (Flyway migration, JPA mappings) and the demo seeder runs
 * the rule engine for both demo clients.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@ActiveProfiles("test", "demo")
class TreasuryServiceApplicationTests {

    @Test
    fun contextLoads() {
    }
}
