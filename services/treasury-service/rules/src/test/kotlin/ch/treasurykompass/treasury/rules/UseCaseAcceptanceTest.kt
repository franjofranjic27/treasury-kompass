package ch.treasurykompass.treasury.rules

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Rule-related acceptance criteria of `docs/specs/use-cases.md` that vary the seed state of the demo clients.
 * The golden tests cover the seed state itself. Expected texts were cross-checked against the prototype.
 */
class UseCaseAcceptanceTest {
    private val muster = DemoClients.MUSTER
    private val components = DemoClients.COMPONENTS

    @Nested
    inner class Uc03Prefill {
        @Test
        fun `AC-03_5 minimum liquidity guide value from operating expenses alone`() {
            val result = DemoClients.evaluate(AssessmentInput(financials = FinancialsInput(operatingExpenses = 26.1)))
            assertEquals(3.3, result.suggestedMinimumLiquidity)
            assertEquals(AssessmentStatus.INCOMPLETE, result.status)
        }
    }

    @Nested
    inner class Uc05Segment {
        @Test
        fun `AC-05_3 and AC-04_7 total assets 21 make Muster professional`() {
            val result = DemoClients.evaluate(muster.withFinancials { copy(totalAssets = 21.0) })
            val segment = assertNotNull(result.segment)
            assertEquals(ClientSegment.PROFESSIONAL, segment.result)
            assertEquals(2, segment.thresholdsExceeded)
            assertEquals("grosses Unternehmen, 2 von 3 Schwellenwerten überschritten", segment.reason)
            assertTrue(segment.professionalBase, "opting-in question becomes visible")
            val decision = result.decision("segment")
            assertEquals("Kundensegment Professioneller Kunde: grosses Unternehmen, 2 von 3 Schwellenwerten überschritten", decision.message)
            assertEquals("Art. 4 und 5 FIDLEG", decision.legalBasis)
        }

        @Test
        fun `AC-05_4 opting-in makes Components a retail client with full suitability check`() {
            val result = DemoClients.evaluate(components.copy(segment = components.segment.copy(optingIn = true)))
            val segment = assertNotNull(result.segment)
            assertEquals(ClientSegment.RETAIL, segment.result)
            assertEquals("Opting-in erklärt", segment.reason)
            assertTrue(segment.optingIn)
            assertEquals("Das Unternehmen wird als Privatkunde behandelt", segment.decisionSteps.last().reason)
            val instruments = assertNotNull(result.suitability).filter { it.product.financialInstrument }
            assertTrue(instruments.none { it.knowledgePresumed })
            assertTrue(instruments.all { it.knowledgeLevel == KnowledgeLevel.EXPERIENCE && it.note == "Kenntnisse und Erfahrung vorhanden" })
            assertEquals(
                listOf(Product.FX_FORWARD, Product.ZERO_COST_COLLAR, Product.MONEY_MARKET_FUND),
                instruments.filter { it.kidRequired }.map { it.product },
            )
            assertEquals(
                listOf("info", "kid.FX_FORWARD", "kid.ZERO_COST_COLLAR", "kid.MONEY_MARKET_FUND", "riskBrochure", "costDisclosure"),
                result.checklist.map { it.id },
            )
        }

        @Test
        fun `AC-05_5 treasury department alone makes Muster professional`() {
            val result = DemoClients.evaluate(muster.copy(segment = SegmentInput(TreasuryOrganisation.TREASURY_DEPARTMENT)))
            val segment = assertNotNull(result.segment)
            assertEquals(ClientSegment.PROFESSIONAL, segment.result)
            assertEquals("Unternehmen mit professioneller Tresorerie", segment.reason)
            assertFalse(segment.largeCompany)
        }

        @Test
        fun `AC-05 4b stored opting-in is ignored without professional base`() {
            val result = DemoClients.evaluate(muster.copy(segment = muster.segment.copy(optingIn = true)))
            val segment = assertNotNull(result.segment)
            assertEquals(ClientSegment.RETAIL, segment.result)
            assertFalse(segment.optingIn)
            assertEquals("1 von 3 Schwellenwerten überschritten, keine professionelle Tresorerie", segment.reason)
        }
    }

    @Nested
    inner class Uc06Exposures {
        @Test
        fun `AC-06_3 EBIT zero sets shares of EBIT to zero and total currency risk to 100 percent`() {
            val result = DemoClients.evaluate(muster.withFinancials { copy(ebit = 0.0) })
            val exposures = assertNotNull(result.exposures)
            exposures.fx.forEach { assertClose(0.0, it.sensitivityPctEbit, EXACT_TOLERANCE) }
            assertEquals(1.0, exposures.fxSensitivityPctEbit)
            assertClose(0.0, exposures.interest.sensitivityPctEbit, EXACT_TOLERANCE)
        }
    }

    @Nested
    inner class Uc07RiskProfile {
        @Test
        fun `AC-07_3 total assets 21 lower the equity ratio score`() {
            val profile = assertNotNull(DemoClients.evaluate(muster.withFinancials { copy(totalAssets = 21.0) }).riskProfile)
            val equityRatio = profile.capacity.factors.first()
            assertClose(0.3762, equityRatio.value)
            assertEquals(3, equityRatio.score)
            assertEquals(2.25, profile.capacity.average)
            assertEquals(2, profile.capacity.score)
            assertEquals(2, profile.score)
        }

        @Test
        fun `AC-07_4 no excess liquidity gives excess factor 1`() {
            val profile = assertNotNull(DemoClients.evaluate(muster.withGoals { copy(minimumLiquidity = 6.4) }).riskProfile)
            val excess = profile.capacity.factors[1]
            assertClose(0.0, excess.value, EXACT_TOLERANCE)
            assertEquals(1, excess.score)
            assertEquals(2, profile.capacity.score)
            assertEquals(RiskProfileCode.CONSERVATIVE, profile.code)
        }

        @Test
        fun `AC-07_5 high willingness is limited by capacity`() {
            val input = muster
                .withGoals { copy(hedgeObjective = HedgeObjective.LOW_HEDGING_COST) }
                .copy(riskTolerance = RiskToleranceInput(TolerableDrawdown.OVER_5_PERCENT, ForgoneGainAttitude.NOT_ACCEPTABLE))
            val profile = assertNotNull(DemoClients.evaluate(input).riskProfile)
            assertClose(13.0 / 3, profile.willingness.average, EXACT_TOLERANCE)
            assertEquals(4, profile.willingness.score)
            assertEquals(3, profile.score)
            assertEquals(RiskProfileCode.BALANCED, profile.code)
        }
    }

    @Nested
    inner class Uc08Suitability {
        @Test
        fun `AC-08_3 no FX forward knowledge makes it unsuitable and is audited`() {
            val result = DemoClients.evaluate(muster.withKnowledge(Product.FX_FORWARD to KnowledgeLevel.NONE))
            assertEquals(SuitabilityStatus.NOT_SUITABLE, result.suitabilityStatus(Product.FX_FORWARD))
            val decision = result.decision("suitability.FX_FORWARD")
            assertEquals("Devisentermingeschäft: Nicht geeignet, keine Kenntnisse, abraten", decision.message)
            assertEquals(RuleId.SUI_01, decision.ruleId)
            assertEquals("Art. 14 FIDLEG", decision.legalBasis)
        }
    }

    @Nested
    inner class Uc09Hedging {
        @Test
        fun `AC-09_5 policy 60 limits both currencies`() {
            val result = DemoClients.evaluate(muster.withGoals { copy(policyMaxHedgeRatio = 60) })
            assertEquals(60, assertNotNull(result.hedges).cap)
            Currency.entries.forEach { currency ->
                val proposal = result.proposal(currency)
                assertEquals(listOf(60, 50, 40, 30), proposal.effectiveRatios())
                assertEquals(45.0, proposal.averageRatio)
                assertTrue(proposal.limitedByPolicy)
                assertEquals(80, proposal.defaultStartRatio)
            }
            assertEquals("Absicherungsquote EUR auf 60 % begrenzt (Treasury-Policy)", result.decision("hedge.EUR.limitedByPolicy").message)
            assertEquals(RuleId.HED_02, result.decision("hedge.EUR.limitedByPolicy").ruleId)
        }

        @Test
        fun `AC-09_6 requested start ratio above the policy is capped`() {
            val result = DemoClients.evaluate(muster, mapOf(Currency.EUR to HedgeSetting(startRatio = 90)))
            val eur = result.proposal(Currency.EUR)
            assertEquals(90, eur.requestedStartRatio)
            assertEquals(80, eur.startRatio)
            assertTrue(eur.limitedByPolicy)
            assertFalse(result.proposal(Currency.USD).limitedByPolicy)
        }

        @Test
        fun `AC-09_7 no step down keeps 80 percent in all quarters`() {
            val eur = DemoClients.evaluate(muster, mapOf(Currency.EUR to HedgeSetting(stepDown = 0))).proposal(Currency.EUR)
            assertEquals(listOf(80, 80, 80, 80), eur.effectiveRatios())
            assertEquals(80.0, eur.averageRatio)
        }

        @Test
        fun `AC-09_8 hedging is blocked without FX forward knowledge`() {
            val result = DemoClients.evaluate(muster.withKnowledge(Product.FX_FORWARD to KnowledgeLevel.NONE))
            Currency.entries.forEach { currency ->
                val proposal = result.proposal(currency)
                assertTrue(proposal.blocked)
                assertEquals(listOf(0, 0, 0, 0), proposal.effectiveRatios())
                assertEquals(listOf(80, 70, 60, 50), proposal.tranches.map { it.ratio }, "nominal ratios stay visible")
                assertClose(0.0, proposal.hedgedAmountFx, EXACT_TOLERANCE)
                assertClose(0.0, proposal.carryChf, EXACT_TOLERANCE)
                proposal.scenario.forEach { point -> assertClose(point.unhedgedChf, point.hedgedChf, 1e-12) }
            }
            assertFalse(Product.FX_FORWARD in result.recommendedProducts)
            assertEquals("BLOCKED", result.decision("hedge.EUR.proposal").value)
            assertEquals(RuleId.HED_05, result.decision("hedge.EUR.proposal").ruleId)
            assertClose(0.0, assertNotNull(result.hedges).totals.hedgedVolumeChf, EXACT_TOLERANCE)
        }

        @Test
        fun `AC-09_9 small USD position is naturally hedged`() {
            val result = DemoClients.evaluate(muster.withFxFlow(Currency.USD, inflow = 0.0, outflow = 0.6))
            val hedges = assertNotNull(result.hedges)
            assertEquals(listOf(Currency.EUR), hedges.proposals.map { it.currency })
            val usd = hedges.naturallyHedged.single()
            assertEquals(Currency.USD, usd.currency)
            assertClose(-0.6, usd.netPosition, EXACT_TOLERANCE)
            assertContains(
                result.reasons,
                "USD: Die Nettoposition von 0.6 Mio. USD ist klein. Zu- und Abflüsse gleichen sich weitgehend aus, eine Absicherung ist nicht nötig.",
            )
            assertEquals("NATURALLY_HEDGED", result.decision("hedge.USD.minimumPosition").value)
        }

        @Test
        fun `AC-09_10 collar request is ignored when not allowed`() {
            val eur = DemoClients.evaluate(muster, mapOf(Currency.EUR to HedgeSetting(collarEnabled = true))).proposal(Currency.EUR)
            assertFalse(eur.collarAllowed)
            assertFalse(eur.collarEnabled)
            assertClose(0.0, eur.collarShare, EXACT_TOLERANCE)
        }

        @Test
        fun `AC-09_10 professional Muster gets the collar by default`() {
            val result = DemoClients.evaluate(muster.withFinancials { copy(totalAssets = 21.0) })
            Currency.entries.forEach { currency ->
                val proposal = result.proposal(currency)
                assertTrue(proposal.collarAllowed)
                assertTrue(proposal.collarEnabled)
            }
            assertClose(-0.6045, assertNotNull(result.proposal(Currency.EUR).scenarioAt(-10)).hedgedChf)
        }
    }

    @Nested
    inner class Uc10Liquidity {
        @Test
        fun `AC-10_3 unsuitable bonds shift to the money market fund`() {
            val result = DemoClients.evaluate(muster.withKnowledge(Product.SHORT_TERM_BOND to KnowledgeLevel.NONE))
            val liquidity = assertNotNull(result.liquidity)
            assertEquals(listOf(45, 55, 0), liquidity.shares())
            assertEquals(listOf(Product.SHORT_TERM_BOND), liquidity.shiftedProducts)
            assertEquals("Obligationen CHF kurz umgeschichtet, weil Kenntnisse fehlen (Art. 14 FIDLEG).", liquidity.note)
            assertTrue(result.reasons.last().endsWith("45 % Festgeld / Callgeld, 55 % Geldmarktfonds CHF. Produkte ohne ausreichende Kenntnisse wurden umgeschichtet."))
            assertEquals(RuleId.LIQ_03, result.decision("liquidity.shift").ruleId)
        }

        @Test
        fun `AC-10_4 unsuitable bonds and fund shift everything to deposits`() {
            val input = muster.withKnowledge(Product.SHORT_TERM_BOND to KnowledgeLevel.NONE, Product.MONEY_MARKET_FUND to KnowledgeLevel.NONE)
            val liquidity = assertNotNull(DemoClients.evaluate(input).liquidity)
            assertEquals(listOf(100, 0, 0), liquidity.shares())
            assertEquals(listOf(Product.SHORT_TERM_BOND, Product.MONEY_MARKET_FUND), liquidity.shiftedProducts)
            assertEquals("Obligationen CHF kurz und Geldmarktfonds CHF umgeschichtet, weil Kenntnisse fehlen (Art. 14 FIDLEG).", liquidity.note)
        }

        @Test
        fun `AC-10_5 no excess liquidity recommends only FX forwards`() {
            val result = DemoClients.evaluate(muster.withGoals { copy(minimumLiquidity = 6.4) })
            val liquidity = assertNotNull(result.liquidity)
            assertTrue(liquidity.noExcess)
            assertEquals(emptyList(), liquidity.parts)
            assertEquals(
                "Keine Überschussliquidität: Die flüssigen Mittel liegen nicht über der Mindestliquidität. Es wird keine Anlage empfohlen.",
                liquidity.note,
            )
            assertEquals(listOf(Product.FX_FORWARD), result.recommendedProducts)
            assertEquals("Liquidität: Keine Überschussliquidität, daher keine Anlageempfehlung.", result.reasons.last())
            assertEquals(listOf("info", "kid.FX_FORWARD", "riskBrochure", "costDisclosure"), result.checklist.map { it.id })
        }
    }

    @Nested
    inner class Uc11Checklist {
        @Test
        fun `AC-11_9 no KID item for a blocked FX forward`() {
            val result = DemoClients.evaluate(muster.withKnowledge(Product.FX_FORWARD to KnowledgeLevel.NONE))
            assertFalse(result.checklist.any { it.id == "kid.FX_FORWARD" })
            assertEquals(
                listOf("info", "kid.MONEY_MARKET_FUND", "riskBrochure", "costDisclosure", "warning.MONEY_MARKET_FUND"),
                result.checklist.map { it.id },
            )
        }

        @Test
        fun `AC-11_3 seed state of Muster has 11 unconfirmed answers and 6 open checklist items`() {
            val result = DemoClients.evaluate(muster)
            val unconfirmed = List(11) { "question-$it" }
            val check = ApprovalRule.check(result, ApprovalWorkflowState(unconfirmedAnswerKeys = unconfirmed))
            assertFalse(check.canApprove)
            assertEquals(11, check.blockers.count { it.type == BlockerType.UNCONFIRMED_ANSWER })
            assertEquals(6, check.blockers.count { it.type == BlockerType.OPEN_CHECKLIST_ITEM })
        }

        @Test
        fun `AC-11_4 confirmed answers and completed checklist allow approval`() {
            val result = DemoClients.evaluate(muster)
            val check = ApprovalRule.check(result, ApprovalWorkflowState(completedChecklistItemIds = result.checklist.map { it.id }.toSet()))
            assertTrue(check.canApprove)
            assertEquals(emptyList(), check.blockers)
        }
    }

    @Nested
    inner class Uc12Protocol {
        @Test
        fun `blocked hedge appears as no recommendation in the protocol`() {
            val result = DemoClients.evaluate(muster.withKnowledge(Product.FX_FORWARD to KnowledgeLevel.NONE))
            val texts = assertNotNull(result.protocolTexts)
            assertEquals("EUR auf Termin verkaufen: keine Empfehlung, Eignung fehlt", texts.recommendations.first())
            assertEquals(
                listOf(
                    "Devisentermingeschäft: keine Kenntnisse, abraten (Art. 14 FIDLEG)",
                    "Zero-Cost-Collar: keine Kenntnisse, abraten (Art. 14 FIDLEG)",
                ),
                texts.notRecommended,
            )
            assertEquals(
                "USD: Der erwartete Nettoabfluss von 3.1 Mio. USD belastet das EBIT um CHF 0.25 Mio. (11 % des EBIT), wenn der USD um 10 % steigt. " +
                    "Devisentermingeschäfte werden derzeit nicht empfohlen, weil keine Kenntnisse vorliegen. " +
                    "Nach einer dokumentierten Aufklärung ist die Empfehlung neu zu prüfen.",
                result.reasons[1],
            )
        }
    }
}
