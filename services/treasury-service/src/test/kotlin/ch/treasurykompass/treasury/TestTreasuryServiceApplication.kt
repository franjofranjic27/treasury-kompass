package ch.treasurykompass.treasury

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<TreasuryServiceApplication>().with(TestcontainersConfiguration::class).run(*args)
}
