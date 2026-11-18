package ch.treasurykompass.treasury.marketdata

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

enum class MarketDataSource { DEMO, SNB }

/** Demo spot rates and interest rates (docs/regelwerk.md section 8), bound from `treasury.market-data`. */
@Validated
@ConfigurationProperties("treasury.market-data")
data class MarketDataProperties(
    @field:NotNull val source: MarketDataSource,
    @field:NotNull val asOf: LocalDate,
    @field:NotEmpty val spotRates: Map<String, Double>,
    @field:NotEmpty val interestRates: Map<String, Double>,
)

/** OpenAPI schema `MarketData`: spot rates in CHF per unit of foreign currency, interest rates p.a. as decimals. */
data class MarketDataView(
    val source: MarketDataSource,
    val asOf: LocalDate,
    val spotRates: Map<String, Double>,
    val interestRates: Map<String, Double>,
)

@Component
class MarketDataProvider(private val properties: MarketDataProperties) {

    init {
        // Fail fast: the rules need a spot rate for every hedgeable currency and CHF plus foreign interest rates.
        val missingSpot = HEDGE_CURRENCIES - properties.spotRates.keys
        val missingRates = (HEDGE_CURRENCIES + "CHF") - properties.interestRates.keys
        require(missingSpot.isEmpty() && missingRates.isEmpty()) {
            "treasury.market-data unvollständig: Kassakurse fehlen für $missingSpot, Zinssätze für $missingRates"
        }
    }

    fun current(): MarketDataView = MarketDataView(
        source = properties.source,
        asOf = properties.asOf,
        spotRates = HEDGE_CURRENCIES.associateWith { properties.spotRates.getValue(it) },
        interestRates = (listOf("CHF") + HEDGE_CURRENCIES).associateWith { properties.interestRates.getValue(it) },
    )

    companion object {
        val HEDGE_CURRENCIES = listOf("EUR", "USD")
    }
}

@RestController
@RequestMapping("/api/market-data")
class MarketDataController(private val provider: MarketDataProvider) {
    @GetMapping
    fun get(): MarketDataView = provider.current()
}
