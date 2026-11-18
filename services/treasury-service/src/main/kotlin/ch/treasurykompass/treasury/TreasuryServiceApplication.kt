package ch.treasurykompass.treasury

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TreasuryServiceApplication

fun main(args: Array<String>) {
	runApplication<TreasuryServiceApplication>(*args)
}
