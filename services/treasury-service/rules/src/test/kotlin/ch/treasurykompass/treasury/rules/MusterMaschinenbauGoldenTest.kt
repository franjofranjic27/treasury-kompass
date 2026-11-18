package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden test for Muster Maschinenbau AG: `docs/regelwerk.md` section 9 and the acceptance criteria of
 * `docs/specs/use-cases.md`. Texts and exact doubles were cross-checked against the prototype's `compute()`.
 */
class MusterMaschinenbauGoldenTest {
    private val result = DemoClients.evaluate(DemoClients.MUSTER)

    @Test
    fun `assessment is complete`() {
        assertEquals(AssessmentStatus.COMPLETE, result.status)
        assertEquals(emptyList(), result.missingInputs)
    }

    @Nested
    inner class SegmentTests {
        private val segment = assertNotNull(result.segment)

        @Test
        fun `AC-05_1 retail client with one of three thresholds and no professional treasury`() {
            assertEquals(ClientSegment.RETAIL, segment.result)
            assertEquals("Privatkunde", segment.label)
            assertEquals(1, segment.thresholdsExceeded)
            assertEquals(listOf(false, false, true), segment.thresholds.map { it.exceeded })
            assertFalse(segment.largeCompany)
            assertFalse(segment.professionalTreasury)
            assertEquals("1 von 3 Schwellenwerten überschritten, keine professionelle Tresorerie", segment.reason)
            assertEquals("Art. 4 und 5 FIDLEG", segment.legalBasis)
        }

        @Test
        fun `AC-04_1 opting-in question is not applicable without professional base`() {
            assertFalse(segment.professionalBase)
            assertFalse(segment.optingIn)
            assertEquals(
                listOf("Institutioneller Kunde?", "Grosses Unternehmen?", "Professionelle Tresorerie?"),
                segment.decisionSteps.map { it.question },
            )
        }

        @Test
        fun `decision steps and consequences as in the prototype`() {
            val largeCompany = segment.decisionSteps[1]
            assertEquals("Nein", largeCompany.answer)
            assertEquals("1 von 3 Schwellenwerten überschritten, mindestens 2 nötig", largeCompany.reason)
            assertEquals("Art. 4 Abs. 5 FIDLEG", largeCompany.legalBasis)
            assertEquals("CFO oder Buchhaltung, neben anderen Aufgaben", segment.decisionSteps[2].reason)
            assertEquals(
                listOf("Art. 12 FIDLEG", "Art. 14 FIDLEG", "Art. 58 FIDLEG", "Art. 15 und 16 FIDLEG"),
                segment.consequences.map { it.legalBasis },
            )
            assertTrue(segment.consequences.none { "Opting-out" in it.text }, "ADR-20: no opting-out for companies")
        }
    }

    @Nested
    inner class ExposuresTests {
        @Test
        fun `AC-06_1 EUR net inflow with high risk`() {
            val eur = result.fx(Currency.EUR)
            assertClose(14.2, eur.netPosition, EXACT_TOLERANCE)
            assertClose(13.348, eur.netPositionChf, EXACT_TOLERANCE)
            assertClose(1.3348, eur.sensitivityChf, EXACT_TOLERANCE)
            assertClose(0.5803, eur.sensitivityPctEbit)
            assertEquals(RiskLevel.HIGH, eur.riskLevel)
            assertEquals(ExposureDirection.NET_INFLOW, eur.direction)
        }

        @Test
        fun `AC-06_1 USD net outflow with medium risk`() {
            val usd = result.fx(Currency.USD)
            assertClose(-3.1, usd.netPosition, EXACT_TOLERANCE)
            assertClose(-2.48, usd.netPositionChf, EXACT_TOLERANCE)
            assertClose(0.248, usd.sensitivityChf, EXACT_TOLERANCE)
            assertClose(0.1078, usd.sensitivityPctEbit)
            assertEquals(RiskLevel.MEDIUM, usd.riskLevel)
            assertEquals(ExposureDirection.NET_OUTFLOW, usd.direction)
        }

        @Test
        fun `AC-06_1 interest rate risk and total currency risk`() {
            val exposures = assertNotNull(result.exposures)
            assertClose(0.06, exposures.interest.sensitivityChf, EXACT_TOLERANCE)
            assertClose(0.0261, exposures.interest.sensitivityPctEbit)
            assertEquals(RiskLevel.LOW, exposures.interest.riskLevel)
            assertClose(0.6882, exposures.fxSensitivityPctEbit)
        }

        @Test
        fun `stress table and liquidity position`() {
            val exposures = assertNotNull(result.exposures)
            assertEquals(listOf("EUR −10 %", "USD +10 %", "Zins +1 Prozentpunkt"), exposures.stress.map { it.label })
            assertEquals(
                listOf("Zuflüsse verlieren an Wert", "Einkauf wird teurer", "Bankkredit SARON-basiert"),
                exposures.stress.map { it.effect },
            )
            assertClose(1.3348, exposures.stress[0].ebitImpactChf, EXACT_TOLERANCE)
            with(exposures.liquidity) {
                assertClose(6.4, cash, EXACT_TOLERANCE)
                assertClose(3.3, minimumLiquidity, EXACT_TOLERANCE)
                assertClose(3.3, suggestedMinimumLiquidity, EXACT_TOLERANCE)
                assertClose(2.175, monthlyOperatingExpenses, EXACT_TOLERANCE)
                assertClose(3.1, excessLiquidity, EXACT_TOLERANCE)
            }
            assertEquals(3.3, result.suggestedMinimumLiquidity)
        }
    }

    @Nested
    inner class RiskProfileTests {
        private val profile = assertNotNull(result.riskProfile)

        @Test
        fun `AC-07_1 risk capacity 2_5 rounds half up to 3`() {
            val factors = profile.capacity.factors
            assertEquals(
                listOf(CapacityFactor.EQUITY_RATIO, CapacityFactor.EXCESS_LIQUIDITY_MONTHS, CapacityFactor.EBIT_MARGIN, CapacityFactor.UNHEDGED_FX_RISK),
                factors.map { it.factor },
            )
            assertClose(0.4115, factors[0].value)
            assertClose(1.4253, factors[1].value)
            assertClose(0.0810, factors[2].value)
            assertClose(0.6882, factors[3].value)
            assertEquals(listOf(4, 2, 3, 1), factors.map { it.score })
            assertEquals(2.5, profile.capacity.average)
            assertEquals(3, profile.capacity.score)
        }

        @Test
        fun `capacity display values as in the prototype`() {
            assertEquals(
                listOf("41 %", "1.4 Monate Betriebsaufwand", "8.1 %", "69 % des EBIT bei 10 % Kursbewegung"),
                profile.capacity.factors.map { it.displayValue },
            )
        }

        @Test
        fun `AC-07_1 willingness 2 and profile conservative`() {
            assertEquals(listOf(1, 2, 3), profile.willingness.factors.map { it.score })
            assertEquals(
                listOf("Budgetsicherheit: Plankurs halten", "Bis 2 %", "Ärgerlich, aber tragbar"),
                profile.willingness.factors.map { it.answerLabel },
            )
            assertEquals(listOf("BUDGET_CERTAINTY", "UP_TO_2_PERCENT", "TOLERABLE"), profile.willingness.factors.map { it.answer })
            assertEquals(2, profile.willingness.score)
            assertEquals(2, profile.score)
            assertEquals(RiskProfileCode.CONSERVATIVE, profile.code)
            assertEquals("Konservativ", profile.label)
            assertEquals("Art. 12 FIDLEG", profile.legalBasis)
            assertNull(profile.note)
        }
    }

    @Nested
    inner class SuitabilityTests {
        @Test
        fun `AC-08_1 suitability per product`() {
            assertEquals(SuitabilityStatus.SUITABLE, result.suitabilityStatus(Product.FX_FORWARD))
            assertEquals(SuitabilityStatus.NOT_SUITABLE, result.suitabilityStatus(Product.ZERO_COST_COLLAR))
            assertEquals(SuitabilityStatus.SUITABLE_AFTER_DISCLOSURE, result.suitabilityStatus(Product.MONEY_MARKET_FUND))
            assertEquals(SuitabilityStatus.SUITABLE, result.suitabilityStatus(Product.SHORT_TERM_BOND))
            assertEquals(SuitabilityStatus.NOT_APPLICABLE, result.suitabilityStatus(Product.DEPOSIT))
        }

        @Test
        fun `AC-08_1 collar is advised against with Art 14`() {
            val collar = assertNotNull(result.suitabilityOf(Product.ZERO_COST_COLLAR))
            assertEquals("keine Kenntnisse, abraten", collar.note)
            assertEquals("Art. 14 FIDLEG", collar.legalBasis)
            assertEquals(KnowledgeLevel.NONE, collar.knowledgeLevel)
            assertFalse(collar.knowledgePresumed)
        }

        @Test
        fun `AC-08_1 key information documents for retail client`() {
            assertEquals(
                listOf(Product.FX_FORWARD, Product.ZERO_COST_COLLAR, Product.MONEY_MARKET_FUND),
                assertNotNull(result.suitability).filter { it.kidRequired }.map { it.product },
            )
        }
    }

    @Nested
    inner class HedgingTests {
        private val hedges = assertNotNull(result.hedges)
        private val eur = result.proposal(Currency.EUR)
        private val usd = result.proposal(Currency.USD)

        @Test
        fun `AC-09_1 EUR staggering 80 70 60 50 with average 65`() {
            assertEquals(80, hedges.cap)
            assertEquals(HedgeAction.SELL_FORWARD, eur.hedgeAction)
            assertEquals("EUR auf Termin verkaufen", eur.actionLabel)
            assertEquals(80, eur.defaultStartRatio)
            assertNull(eur.requestedStartRatio)
            assertEquals(80, eur.startRatio)
            assertFalse(eur.limitedByPolicy)
            assertEquals(listOf(80, 70, 60, 50), eur.effectiveRatios())
            assertEquals(65.0, eur.averageRatio)
            assertClose(9.23, eur.hedgedAmountFx, EXACT_TOLERANCE)
            assertClose(8.6762, eur.hedgedAmountChf, EXACT_TOLERANCE)
            assertClose(-0.0987, eur.carryChf)
            assertFalse(eur.collarAllowed)
            assertFalse(eur.collarEnabled)
            assertEquals(0.0, eur.collarShare)
            assertFalse(eur.blocked)
            assertNull(eur.blockedReason)
        }

        @Test
        fun `AC-09_1 EUR forward rates and tranches`() {
            listOf(0.9353, 0.9307, 0.9261, 0.9216).zip(eur.tranches).forEach { (expected, tranche) ->
                assertClose(expected, tranche.forwardRate)
            }
            assertEquals(listOf(3, 6, 9, 12), eur.tranches.map { it.maturityMonths })
            assertEquals(listOf(0.25, 0.5, 0.75, 1.0), eur.tranches.map { it.tenorYears })
            with(eur.tranches.first()) {
                assertClose(3.55, amountFx, EXACT_TOLERANCE)
                assertClose(2.84, hedgedAmountFx, EXACT_TOLERANCE)
                assertClose(0.921294, collarFloor, EXACT_TOLERANCE)
                assertClose(0.963383, collarCap, EXACT_TOLERANCE)
                assertClose(-0.013282, carryChf, EXACT_TOLERANCE)
            }
        }

        @Test
        fun `AC-09_2 scenario EUR minus 10 percent`() {
            val point = assertNotNull(eur.scenarioAt(-10))
            assertClose(0.846, point.spotRate, EXACT_TOLERANCE)
            assertClose(-1.3348, point.unhedgedChf)
            assertClose(-0.5659, point.hedgedChf)
            assertEquals("−1.33", SwissNumberFormat.number(point.unhedgedChf, 2))
            assertEquals("−0.57", SwissNumberFormat.number(point.hedgedChf, 2))
        }

        @Test
        fun `scenario curve from minus 15 to plus 15 percent`() {
            assertEquals((-15..15).toList(), eur.scenario.map { it.shiftPct })
            val unchanged = assertNotNull(eur.scenarioAt(0))
            assertEquals(0.0, unchanged.unhedgedChf)
        }

        @Test
        fun `AC-09_3 USD buy forward and totals`() {
            assertEquals(HedgeAction.BUY_FORWARD, usd.hedgeAction)
            assertEquals("USD auf Termin kaufen", usd.actionLabel)
            assertEquals(listOf(80, 70, 60, 50), usd.effectiveRatios())
            assertClose(2.015, usd.hedgedAmountFx, EXACT_TOLERANCE)
            assertClose(0.0340, usd.carryChf)
            assertClose(-0.775, usd.tranches.first().amountFx, EXACT_TOLERANCE)
            assertClose(10.2882, hedges.totals.hedgedVolumeChf, EXACT_TOLERANCE)
            assertClose(-0.0647, hedges.totals.carryChf)
            assertEquals(emptyList(), hedges.naturallyHedged)
        }

        @Test
        fun `doubles are bit-identical to the prototype`() {
            assertEquals(-0.09871416364816374, eur.carryChf, 1e-15)
            assertEquals(-0.5658941636481636, assertNotNull(eur.scenarioAt(-10)).hedgedChf, 1e-15)
            assertEquals(0.03397565227728199, usd.carryChf, 1e-15)
            assertEquals(0.7687925696594429, usd.tranches.first().collarFloor, 1e-15)
        }
    }

    @Nested
    inner class LiquidityTests {
        private val liquidity = assertNotNull(result.liquidity)

        @Test
        fun `AC-10_1 excess 3_1 split 45 40 15`() {
            assertClose(3.1, liquidity.excessLiquidity, EXACT_TOLERANCE)
            assertFalse(liquidity.noExcess)
            assertEquals(listOf(Product.DEPOSIT, Product.MONEY_MARKET_FUND, Product.SHORT_TERM_BOND), liquidity.parts.map { it.product })
            assertEquals(listOf(45, 40, 15), liquidity.shares())
            listOf(1.395, 1.24, 0.465).zip(liquidity.parts).forEach { (expected, part) -> assertClose(expected, part.amountChf, EXACT_TOLERANCE) }
            assertEquals(
                listOf(SuitabilityStatus.NOT_APPLICABLE, SuitabilityStatus.SUITABLE_AFTER_DISCLOSURE, SuitabilityStatus.SUITABLE),
                liquidity.parts.map { it.suitabilityStatus },
            )
            assertEquals(emptyList(), liquidity.shiftedProducts)
            assertNull(liquidity.note)
            assertEquals(RiskProfileCode.CONSERVATIVE, liquidity.profileCode)
        }
    }

    @Nested
    inner class ChecklistAndTextsTests {
        @Test
        fun `recommended products`() {
            assertEquals(
                listOf(Product.FX_FORWARD, Product.DEPOSIT, Product.MONEY_MARKET_FUND, Product.SHORT_TERM_BOND),
                result.recommendedProducts,
            )
        }

        @Test
        fun `AC-11_1 six checklist items with one KID item per product`() {
            assertEquals(
                listOf("info", "kid.FX_FORWARD", "kid.MONEY_MARKET_FUND", "riskBrochure", "costDisclosure", "warning.MONEY_MARKET_FUND"),
                result.checklist.map { it.id },
            )
            val kid = result.checklist[1]
            assertEquals("Basisinformationsblatt ausgehändigt: Devisentermingeschäft", kid.text)
            assertEquals("Basisinformationsblatt Devisentermingeschäft aushändigen", kid.shortText)
            assertEquals("Art. 58 FIDLEG", kid.legalBasis)
            assertEquals("Informationen zu Finanzdienstleister, Dienstleistung, Risiken und Kosten abgegeben", result.checklist[0].text)
            assertEquals("Aufklärung und Warnhinweis dokumentiert: Geldmarktfonds CHF", result.checklist[5].text)
            assertEquals("Art. 14 FIDLEG", result.checklist[5].legalBasis)
        }

        @Test
        fun `reasons verbatim from the prototype`() {
            assertEquals(
                listOf(
                    "EUR: Der erwartete Nettozufluss von 14.2 Mio. EUR belastet das EBIT um CHF 1.33 Mio. (58 % des EBIT), wenn der EUR um 10 % fällt. " +
                        "Bei Risikoprofil Konservativ und dem Ziel «Budgetsicherheit: Plankurs halten» empfehlen wir eine gestaffelte Absicherung " +
                        "von 80 / 70 / 60 / 50 % je Quartal (Ø 65 %). Devisentermingeschäfte sind geeignet, weil Kenntnisse und Erfahrung belegt sind " +
                        "(Anhang Ziff. 7, offene Devisentermingeschäfte). Devisenoptionen werden nicht empfohlen, weil keine Kenntnisse vorliegen. " +
                        "Die Zinsdifferenz kostet rund CHF 0.10 Mio.",
                    "USD: Der erwartete Nettoabfluss von 3.1 Mio. USD belastet das EBIT um CHF 0.25 Mio. (11 % des EBIT), wenn der USD um 10 % steigt. " +
                        "Bei Risikoprofil Konservativ und dem Ziel «Budgetsicherheit: Plankurs halten» empfehlen wir eine gestaffelte Absicherung " +
                        "von 80 / 70 / 60 / 50 % je Quartal (Ø 65 %). Devisentermingeschäfte sind geeignet, weil Kenntnisse und Erfahrung belegt sind " +
                        "(Anhang Ziff. 7, offene Devisentermingeschäfte). Devisenoptionen werden nicht empfohlen, weil keine Kenntnisse vorliegen. " +
                        "Die Zinsdifferenz bringt rund CHF 0.03 Mio.",
                    "Liquidität: Nach Abzug der Mindestliquidität sind CHF 3.1 Mio. anlagefähig. Beim Horizont «Unter 1 Jahr» und Profil Konservativ " +
                        "steht der Kapitalerhalt im Vordergrund: 45 % Festgeld / Callgeld, 40 % Geldmarktfonds CHF, 15 % Obligationen CHF kurz.",
                ),
                result.reasons,
            )
        }

        @Test
        fun `AC-12_1 protocol texts verbatim from the prototype`() {
            val texts = assertNotNull(result.protocolTexts)
            assertEquals(
                listOf(
                    "EUR auf Termin verkaufen: gestaffelt 80 / 70 / 60 / 50 % (Ø 65 %), Volumen 9.23 Mio. EUR, Devisentermingeschäfte",
                    "USD auf Termin kaufen: gestaffelt 80 / 70 / 60 / 50 % (Ø 65 %), Volumen 2.02 Mio. USD, Devisentermingeschäfte",
                    "Festgeld / Callgeld: CHF 1.40 Mio. (45 %)",
                    "Geldmarktfonds CHF: CHF 1.24 Mio. (40 %)",
                    "Obligationen CHF kurz: CHF 0.47 Mio. (15 %)",
                ),
                texts.recommendations,
            )
            assertEquals(listOf("Zero-Cost-Collar: keine Kenntnisse, abraten (Art. 14 FIDLEG)"), texts.notRecommended)
        }
    }

    @Nested
    inner class AuditDecisionsTests {
        @Test
        fun `AC-12_3 segment and profile decisions with rule ID and legal basis`() {
            val segment = result.decision("segment")
            assertEquals(RuleId.SEG_01, segment.ruleId)
            assertEquals("R-SEG-01", segment.ruleId.code)
            assertEquals("Art. 4 und 5 FIDLEG", segment.legalBasis)
            assertEquals("RETAIL", segment.value)
            assertEquals("Kundensegment Privatkunde: 1 von 3 Schwellenwerten überschritten, keine professionelle Tresorerie", segment.message)

            val profile = result.decision("riskProfile")
            assertEquals(RuleId.PRO_03, profile.ruleId)
            assertEquals("Art. 12 FIDLEG", profile.legalBasis)
            assertEquals("Risikoprofil Konservativ: Risikofähigkeit 3/5, Risikobereitschaft 2/5", profile.message)
        }

        @Test
        fun `suitability decisions verbatim from the prototype audit trail`() {
            val collar = result.decision("suitability.ZERO_COST_COLLAR")
            assertEquals("Zero-Cost-Collar: Nicht geeignet, keine Kenntnisse, abraten", collar.message)
            assertEquals("Art. 14 FIDLEG", collar.legalBasis)
            val fund = result.decision("suitability.MONEY_MARKET_FUND")
            assertEquals("Geldmarktfonds CHF: Nur nach Aufklärung, Erfahrung fehlt, Warnhinweis dokumentieren", fund.message)
            assertEquals("Art. 12 FIDLEG", fund.legalBasis)
        }

        @Test
        fun `hedge, liquidity and checklist decisions`() {
            assertEquals("EUR auf Termin verkaufen: gestaffelt 80 / 70 / 60 / 50 % (Ø 65 %)", result.decision("hedge.EUR.proposal").message)
            assertEquals("80/70/60/50", result.decision("hedge.EUR.proposal").value)
            assertEquals("NOT_ALLOWED", result.decision("hedge.EUR.collar").value)
            assertEquals("80", result.decision("hedge.cap").value)
            assertEquals("45/40/15", result.decision("liquidity.allocation").value)
            assertEquals(RuleId.CHK_01, result.decision("checklist").ruleId)
            assertTrue(result.decisions.none { it.key == "hedge.EUR.limitedByPolicy" })
        }

        @Test
        fun `AC-12_5 every decision has a rule ID and a unique key`() {
            assertTrue(result.decisions.isNotEmpty())
            assertEquals(result.decisions.size, result.decisions.map { it.key }.toSet().size)
            assertTrue(result.decisions.all { it.ruleId.code.matches(Regex("^R-[A-Z]{3}-[0-9]{2}$")) })
        }
    }
}
