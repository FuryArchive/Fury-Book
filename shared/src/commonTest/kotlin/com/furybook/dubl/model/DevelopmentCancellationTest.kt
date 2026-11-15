package com.furybook.dubl.model

import kotlin.test.*

class DevelopmentCancellationTest {
    private class Cancelled : RuntimeException()
    private fun entry(id: String, requirements: String = "-") = DevelopmentEntry(
        id, id, "Навыки", "Тест", 5, DevelopmentCostType.XP, 20,
        requirements, "", "", emptyList(), null, emptyList(), false, false, false, "", "",
    )
    private val character = DublCharacter("test", experience = 10000)

    @Test fun cancelledIndexDoesNotPublishPartialResultsAndCanBeRetried() {
        val parent = entry("parent")
        val catalog = DevelopmentCatalog("test", listOf(parent) + (1..50).map { entry("child-$it", "parent") })
        var calls = 0
        var cancelled = true
        val planner = DevelopmentAcquisitionPlanner(character, catalog) {
            calls++
            if (cancelled && calls == 10) throw Cancelled()
        }
        assertFailsWith<Cancelled> { planner.unlocks(parent.id) }
        assertEquals(10, calls)
        cancelled = false
        assertEquals(50, planner.unlocks(parent.id).size)
        assertEquals(DevelopmentAcquisitionPlanner(character, catalog).unlocks(parent.id), planner.unlocks(parent.id))
    }

    @Test fun cancellationPropagatesFromRecursiveRequirements() {
        val parent = entry("parent", "Телосложение 2")
        val child = entry("child", "parent")
        val catalog = DevelopmentCatalog("test", listOf(parent, child))
        val owned = character.copy(development = mapOf("parent" to OwnedDevelopment(1)))
        var calls = 0
        val rules = DevelopmentRules(owned, catalog, DevelopmentProgress(owned.development)) {
            if (++calls == 3) throw Cancelled()
        }
        assertFailsWith<Cancelled> { rules.requirements(child) }
        assertEquals(3, calls)
    }

    @Test fun cancelledPurchaseDoesNotMutateCharacter() {
        val health = entry("health", "Телосложение 2")
        val catalog = DevelopmentCatalog("test", listOf(health))
        var calls = 0
        val planner = DevelopmentAcquisitionPlanner(character, catalog) { if (++calls == 4) throw Cancelled() }
        assertFailsWith<Cancelled> { planner.plan(DevelopmentAcquisitionRequest.single(health.id, targetRank = 20)) }
        assertTrue(character.development.isEmpty())
        assertEquals(0, character.constitution)
    }
}
