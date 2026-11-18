package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Golden test for Beispiel Components AG: `docs/regelwerk.md` section 9 and the use-case acceptance criteria. */
class BeispielComponentsGoldenTest {
    private val result = DemoClients.evaluate(DemoClients.COMPONENTS)

    @Test
    fun `assessment is complete`() {
        assertEquals(AssessmentStatus.COMPLETE, result.status)
        assertEquals(emptyList(), result.missingInputs)
    }

    @Nested
    inner class SegmentTests {
        private val segment = assertNotNull(result.segment)

        @Test
        fun `AC-05_2 professional client, large company with treasury department`() {
            assertEquals(ClientSegment.PROFESSIONAL, segment.result)
            assertEquals(3, segment.thresholdsExceeded)
            assertTrue(segment.largeCompany)
            assertTrue(segment.professionalTreasury)
            assertEquals("grosses Unternehmen, 3 von 3 Schwellenwerten überschritten, professionelle Tresorerie", segment.reason)
            assertEquals("Kenntnisse, Erfahrung und finanzielle Tragbarkeit werden vermutet.", segment.summary)
        }

        @Test
        fun `AC-04_2 opting-in question applies and is answered no`() {
            assertTrue(segment.professionalBase)
            val optingIn = segment.decisionSteps.last()
            assertEquals("Opting-in erklärt?", optingIn.question)
            assertEquals("Nein", optingIn.answer)
            assertEquals("Behandlung als professioneller Kunde", optingIn.reason)
            assertEquals("Opting-in als Privatkunde möglich", segment.consequences.last().text)
        }
    }

    @Nested
    inner class ExposuresTests {
        @Test
        fun `AC-06_2 EUR, USD and interest rate exposures`() {
            with(result.fx(Currency.EUR)) {
                assertClose(52.4, netPosition, EXACT_TOLERANCE)
                assertClose(49.256, netPositionChf, EXACT_TOLERANCE)
                assertClose(4.9256, sensitivityChf, EXACT_TOLERANCE)
                assertClose(0.3848, sensitivityPctEbit)
                assertEquals(RiskLevel.HIGH, riskLevel)
            }
            with(result.fx(Currency.USD)) {
                assertClose(21.0, netPosition, EXACT_TOLERANCE)
                assertClose(16.8, netPositionChf, EXACT_TOLERANCE)
                assertClose(1.68, sensitivityChf, EXACT_TOLERANCE)
                assertClose(0.1313, sensitivityPctEbit)
                assertEquals(RiskLevel.MEDIUM, riskLevel)
                assertEquals(ExposureDirection.NET_INFLOW, direction)
            }
            with(assertNotNull(result.exposures).interest) {
                assertClose(0.25, sensitivityChf, EXACT_TOLERANCE)
                assertClose(0.0195, sensitivityPctEbit)
                assertEquals(RiskLevel.LOW, riskLevel)
            }
        }

        @Test
        fun `minimum liquidity guide value 16_7`() {
            assertEquals(16.7, result.suggestedMinimumLiquidity)
            assertClose(11.1, assertNotNull(result.exposures).liquidity.monthlyOperatingExpenses, EXACT_TOLERANCE)
        }
    }

    @Nested
    inner class RiskProfileTests {
        private val profile = assertNotNull(result.riskProfile)

        @Test
        fun `AC-07_2 capacity 3, willingness 3, profile balanced`() {
            val factors = profile.capacity.factors
            assertEquals(listOf(4, 2, 3, 1), factors.map { it.score })
            assertClose(1.018, factors[1].value)
            assertClose(0.0877, factors[2].value)
            assertClose(0.5161, factors[3].value)
            assertEquals(2.5, profile.capacity.average)
            assertEquals(3, profile.capacity.score)
            assertEquals(listOf(3, 3, 3), profile.willingness.factors.map { it.score })
            assertEquals(3, profile.willingness.score)
            assertEquals(RiskProfileCode.BALANCED, profile.code)
            assertEquals("Ausgewogen", profile.label)
        }

        @Test
        fun `professional note on presumed financial capacity`() {
            assertEquals(
                "Bei professionellen Kunden wird die finanzielle Tragbarkeit vermutet. Die Analyse dient hier der Strukturierung des Vorschlags.",
                profile.note,
            )
        }
    }

    @Nested
    inner class SuitabilityTests {
        @Test
        fun `AC-08_2 all instruments suitable with presumed knowledge and no KID`() {
            val suitability = assertNotNull(result.suitability)
            val instruments = suitability.filter { it.product.financialInstrument }
            assertEquals(4, instruments.size)
            assertTrue(instruments.all { it.status == SuitabilityStatus.SUITABLE && it.knowledgePresumed })
            assertTrue(instruments.all { it.note == "Kenntnisse und Erfahrung vermutet" && it.knowledgeLevel == null && it.legalBasis == null })
            assertTrue(suitability.none { it.kidRequired })
            assertEquals("Kein Basisinformationsblatt nötig (Professioneller Kunde)", result.decision("kid").message)
        }
    }

    @Nested
    inner class HedgingTests {
        private val hedges = assertNotNull(result.hedges)
        private val eur = result.proposal(Currency.EUR)
        private val usd = result.proposal(Currency.USD)

        @Test
        fun `AC-09_4 staggering 70 60 50 40 with collar for both currencies`() {
            assertEquals(90, hedges.cap)
            listOf(eur, usd).forEach { proposal ->
                assertEquals(HedgeAction.SELL_FORWARD, proposal.hedgeAction)
                assertEquals(70, proposal.startRatio)
                assertEquals(listOf(70, 60, 50, 40), proposal.effectiveRatios())
                assertEquals(55.0, proposal.averageRatio)
                assertTrue(proposal.collarAllowed)
                assertTrue(proposal.collarEnabled)
                assertEquals(0.3, proposal.collarShare)
            }
        }

        @Test
        fun `AC-09_4 volumes, carry and collar band`() {
            assertClose(28.82, eur.hedgedAmountFx, EXACT_TOLERANCE)
            assertClose(-0.2125, eur.carryChf)
            assertClose(0.9213, eur.tranches.first().collarFloor)
            assertClose(0.9634, eur.tranches.first().collarCap)
            assertClose(11.55, usd.hedgedAmountFx, EXACT_TOLERANCE)
            assertClose(-0.1343, usd.carryChf)
        }

        @Test
        fun `AC-09_4 scenario EUR minus 10 percent`() {
            val point = assertNotNull(eur.scenarioAt(-10))
            assertClose(-4.9256, point.unhedgedChf)
            assertClose(-2.6407, point.hedgedChf)
        }

        @Test
        fun `doubles are bit-identical to the prototype`() {
            assertEquals(-0.2125253146114452, eur.carryChf, 1e-15)
            assertEquals(-2.6406699581367046, assertNotNull(eur.scenarioAt(-10)).hedgedChf, 1e-15)
            assertEquals(1.0457371393672181, assertNotNull(eur.scenarioAt(5)).hedgedChf, 1e-15)
            assertEquals(-0.13429959880613515, usd.carryChf, 1e-15)
        }
    }

    @Nested
    inner class LiquidityTests {
        @Test
        fun `AC-10_2 excess 11_3 split 20 25 55`() {
            val liquidity = assertNotNull(result.liquidity)
            assertClose(11.3, liquidity.excessLiquidity, EXACT_TOLERANCE)
            assertEquals(listOf(20, 25, 55), liquidity.shares())
            listOf(2.26, 2.825, 6.215).zip(liquidity.parts).forEach { (expected, part) -> assertClose(expected, part.amountChf, EXACT_TOLERANCE) }
            assertNull(liquidity.note)
        }
    }

    @Nested
    inner class ChecklistAndTextsTests {
        @Test
        fun `recommended products include the collar`() {
            assertEquals(
                listOf(Product.FX_FORWARD, Product.ZERO_COST_COLLAR, Product.DEPOSIT, Product.MONEY_MARKET_FUND, Product.SHORT_TERM_BOND),
                result.recommendedProducts,
            )
        }

        @Test
        fun `AC-11_2 three checklist items, information waiver possible`() {
            assertEquals(listOf("info", "riskBrochure", "costDisclosure"), result.checklist.map { it.id })
            assertTrue(result.checklist.first().text.endsWith("(Verzicht möglich)"))
        }

        @Test
        fun `reasons verbatim from the prototype`() {
            assertEquals(
                listOf(
                    "EUR: Der erwartete Nettozufluss von 52.4 Mio. EUR belastet das EBIT um CHF 4.93 Mio. (38 % des EBIT), wenn der EUR um 10 % fällt. " +
                        "Bei Risikoprofil Ausgewogen und dem Ziel «Margen schützen, Chancen teilweise offen lassen» empfehlen wir eine gestaffelte " +
                        "Absicherung von 70 / 60 / 50 / 40 % je Quartal (Ø 55 %). Beim professionellen Kunden werden Kenntnisse und Erfahrung mit " +
                        "Devisentermingeschäften vermutet. 30 % laufen über einen Zero-Cost-Collar, damit ein Teil der Kurschancen offen bleibt. " +
                        "Das entspricht der Antwort «Ärgerlich, aber tragbar». Die Zinsdifferenz kostet rund CHF 0.21 Mio.",
                    "USD: Der erwartete Nettozufluss von 21.0 Mio. USD belastet das EBIT um CHF 1.68 Mio. (13 % des EBIT), wenn der USD um 10 % fällt. " +
                        "Bei Risikoprofil Ausgewogen und dem Ziel «Margen schützen, Chancen teilweise offen lassen» empfehlen wir eine gestaffelte " +
                        "Absicherung von 70 / 60 / 50 / 40 % je Quartal (Ø 55 %). Beim professionellen Kunden werden Kenntnisse und Erfahrung mit " +
                        "Devisentermingeschäften vermutet. 30 % laufen über einen Zero-Cost-Collar, damit ein Teil der Kurschancen offen bleibt. " +
                        "Das entspricht der Antwort «Ärgerlich, aber tragbar». Die Zinsdifferenz kostet rund CHF 0.13 Mio.",
                    "Liquidität: Nach Abzug der Mindestliquidität sind CHF 11.3 Mio. anlagefähig. Beim Horizont «1 bis 3 Jahre» und Profil " +
                        "Ausgewogen ist eine höhere Beimischung von Obligationen vertretbar: 20 % Festgeld / Callgeld, 25 % Geldmarktfonds CHF, " +
                        "55 % Obligationen CHF kurz.",
                ),
                result.reasons,
            )
        }

        @Test
        fun `protocol texts verbatim from the prototype`() {
            val texts = assertNotNull(result.protocolTexts)
            assertEquals(
                listOf(
                    "EUR auf Termin verkaufen: gestaffelt 70 / 60 / 50 / 40 % (Ø 55 %), Volumen 28.82 Mio. EUR, 70 % Devisentermingeschäfte und 30 % Zero-Cost-Collar",
                    "USD auf Termin verkaufen: gestaffelt 70 / 60 / 50 / 40 % (Ø 55 %), Volumen 11.55 Mio. USD, 70 % Devisentermingeschäfte und 30 % Zero-Cost-Collar",
                    "Festgeld / Callgeld: CHF 2.26 Mio. (20 %)",
                    "Geldmarktfonds CHF: CHF 2.83 Mio. (25 %)",
                    "Obligationen CHF kurz: CHF 6.22 Mio. (55 %)",
                ),
                texts.recommendations,
            )
            assertEquals(listOf("Keine"), texts.notRecommended)
        }

        @Test
        fun `segment decision verbatim`() {
            assertEquals(
                "Kundensegment Professioneller Kunde: grosses Unternehmen, 3 von 3 Schwellenwerten überschritten, professionelle Tresorerie",
                result.decision("segment").message,
            )
            assertEquals("Risikoprofil Ausgewogen: Risikofähigkeit 3/5, Risikobereitschaft 3/5", result.decision("riskProfile").message)
        }
    }
}
