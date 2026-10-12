package com.furybook.dubl.acquisition

import com.furybook.content.*
import com.furybook.dubl.model.*
import java.io.File
import kotlin.test.*

class AcquisitionPriceOracleTest {
    private val matrix = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .map { File(it, "rulesets/dubl-3.69/acquisition/expected_prices.json") }.first { it.isFile }
    private val catalog by lazy {
        val source = FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }
        loadAcquisitionCatalog(listOf("dubl-3.69", "dubl-chi-3.69").map { FcpContentPack.load("fcp/$it", source) }, true).catalog
    }
    private val fixtures by lazy { AcquisitionPriceFixtures({ id -> matrix.useLines { lines ->
        lines.first { it.startsWith("{\"caseId\":\"$id\",") }.removeSuffix(",") } }, catalog) }
    private fun quote(f: PriceFixture): Amount {
        val result = quote(f.change, f.context)
        return assertIs<PriceResult.Known>(result, "${f.change}: $result").cost
    }

    @Test fun approved_price_anchors() {
        val blockCase = fixtures.load("block_dex3_speed2")
        val marathonCase = fixtures.load("marathon_con4")
        val sprinterCase = fixtures.load("sprinter_dex3_str3")
        val eagleCase = fixtures.load("eagle_price_only")
        assertEquals(20L, quote(blockCase).xp)
        assertEquals(30L, quote(marathonCase).xp)
        assertEquals(30L, quote(sprinterCase).xp)
        assertEquals(45L, quote(eagleCase).xp)
        assertEquals(RequirementStatus.SourceUnresolved, evaluate(catalog.definitions.getValue(eagleCase.change.definition).requirements, eagleCase.context).status)
    }

    @Test fun self_taught_first_rank() {
        assertEquals(10L, quote(fixtures.load("skill_rank1_creation")).xp)
        assertEquals(5L, quote(fixtures.load("skill_rank1_after_creation")).xp)
        assertEquals(10L, quote(fixtures.load("skill_rank1_after_creation_no_discount")).xp)
    }

    @Test fun unconfirmed_second_rank_is_unknown() {
        val f = fixtures.load("resistance_first")
        assertIs<PriceResult.Unknown>(quote(f.change.copy(fromRank=1, toRank=2), f.context))
    }

    @Test fun numerical_anchors_have_canonical_legal_predecessors() {
        for (id in listOf("block_dex3_speed2", "block_dex2_speed3", "marathon_con4", "sprinter_dex3_str3")) {
            val f = fixtures.load(id)
            assertEquals(RequirementStatus.Satisfied, evaluate(catalog.definitions.getValue(f.change.definition).requirements, f.context).status, id)
        }
        val second = fixtures.load("resistance_second_type")
        assertTrue(second.context.instances.any { it.definition == second.change.definition && it.parameters["resistanceType"] == "fire" })
        assertEquals("poison", second.change.parameters["resistanceType"])
        assertEquals(20L, quote(second).xp)
    }

    @Test fun quoted_payment_is_not_repriced_when_a_later_catalog_changes() {
        val f = fixtures.load("receipt_immutable")
        val paid = quote(f)
        val definition = f.context.catalog.definitions.getValue(f.change.definition)
        val laterCatalog = f.context.catalog.copy(definitions=f.context.catalog.definitions +
            (definition.ref to definition.copy(priceRule=PriceRule.Fixed(30, 0, definition.sourceRef))))
        val later = AcquisitionContext(f.context.character, f.context.instances, f.context.learnedSpellIds,
            f.context.creationPhase, f.context.externalEvidence, laterCatalog)
        assertEquals(Amount(30, 0), assertIs<PriceResult.Known>(quote(f.change, later)).cost)
        val remaining = f.opening - paid
        assertEquals(f.expected.remaining, remaining)
        assertEquals(f.expected.undoBalance, remaining + paid)
    }

    @Test fun specialization_semantics_are_preserved_without_claiming_admission() {
        fun f(semantic: String, instance: String = "FIRST") = fixtures.load(
            "knowledge_template|0|1|DEFAULT|AFTER_CREATION|SELF_TAUGHT|$instance|$semantic")
        val same = f("SAME_ID")
        val renamed = f("RENAMED_SAME_ID")
        val other = f("OTHER_ID")
        assertEquals(same.change.parameters, renamed.change.parameters)
        assertNotEquals(same.change.parameters, other.change.parameters)
        assertEquals(mapOf("specialization" to "spec:a"), same.change.parameters)
        for (semantic in listOf("EMPTY", "UNKNOWN")) {
            val unknown = f(semantic)
            assertTrue(unknown.change.parameters.isEmpty())
            assertEquals(5L, quote(unknown).xp)
            assertNotEquals(RequirementStatus.Satisfied,
                evaluate(catalog.definitions.getValue(unknown.change.definition).requirements, unknown.context).status)
        }
        val second = f("TWO_DISTINCT", "SECOND_DISTINCT")
        assertEquals("spec:b", second.change.parameters["specialization"])
        assertTrue(second.context.instances.any { it.definition == second.change.definition && it.parameters["specialization"] == "spec:a" })
        assertEquals(5L, quote(second).xp)
    }

    @Test fun every_confirmed_case_checks_cost_remaining_and_undo_expectation() {
        var confirmed = 0
        var unverified = 0
        matrix.forEachLine { line ->
            if (line.startsWith("{\"caseId\":")) {
                if (line.contains("\"evidenceStatus\":\"CONFIRMED\"")) {
                    val f = fixtures.decode(line.removeSuffix(","))
                    val quoted = quote(f)
                    val charged = if (f.expectedApply == "BLOCKED_REQUIREMENTS") {
                        assertEquals(f.knownQuote, quoted)
                        assertEquals(RequirementStatus.Unsatisfied,
                            evaluate(catalog.definitions.getValue(f.change.definition).requirements, f.context).status)
                        Amount(0, 0) // Expected financial effect only; T10 apply is not implemented here.
                    } else quoted
                    assertEquals(f.expected.cost, charged, line.substringBefore(",\"sourceId"))
                    val remaining = f.opening - charged
                    assertEquals(f.expected.remaining, remaining)
                    assertEquals(f.expected.undoBalance, remaining + charged)
                    confirmed++
                } else unverified++
            }
        }
        assertTrue(confirmed >= 10349)
        assertTrue(unverified > 0, "Source release gate must remain open")
        println("Independent price oracle: $confirmed confirmed, $unverified source-unverified")
    }
}
