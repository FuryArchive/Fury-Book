package com.furybook.dubl.model

import com.furybook.dubl.data.parseDevelopmentCatalog
import java.io.File

private fun edgeEntry(id: String, name: String, requirement: String = "-") = DevelopmentEntry(
    id, name, "Навыки", "Тест", 30, DevelopmentCostType.XP, 1,
    requirement, "", "", emptyList(), null, emptyList(), false, false, false, "", "",
)

fun main() {
    val failures = mutableListOf<String>()
    fun scenario(name: String, body: () -> Unit) {
        try { body(); println("PASS $name") }
        catch (e: Exception) { failures += "$name: ${e.message}"; println("FAIL ${failures.last()}") }
    }
    val entries = File("shared/src/commonMain/resources/fcp").walkTopDown()
        .filter { it.isFile && it.name.startsWith("development_") && it.extension == "json" }
        .flatMap { parseDevelopmentCatalog(it.readText()).entries }.distinctBy { it.id }.toList()
    val catalog = DevelopmentCatalog("bundled", entries)
    val empty = DublCharacter(id = "edges", experience = 10000)

    for ((alias, canonical) in listOf(
        "Джит Кун До" to "Джит Кун-До",
        "Дэрвиш" to "Танец ветра (Дэрвиш)",
        "Открытая Ладонь" to "Школа Открытой Ладони",
    )) {
        scenario("catalog resolves explicit alias $alias") {
            val expected = entries.single { it.name == canonical }
            check(catalog.matchingName(alias).map { it.id } == listOf(expected.id))
        }
        scenario("unowned alias $alias fails instead of manual") {
            val check = DevelopmentRules(empty, catalog, DevelopmentProgress())
                .requirements(edgeEntry("alias-test", "Alias test", alias)).single()
            check(check.status == RequirementStatus.FAIL) { check.toString() }
        }
    }
    scenario("exact custom name wins over a rulebook alias") {
        val canonical = edgeEntry("canonical", "Танец ветра (Дэрвиш)")
        val custom = edgeEntry("custom", "Дэрвиш")
        check(DevelopmentCatalog("test", listOf(canonical, custom)).matchingName("Дэрвиш") == listOf(custom))
    }
    scenario("style alias does not resolve a similarly named technique") {
        val matches = catalog.matchingName("Дэрвиш")
        check(matches.size == 1 && matches.single().tags.any { developmentNormalize(it) == "боевой стиль" })
        check(catalog.matchingName("Танец ветра").none { it.id == matches.single().id })
    }
    scenario("renaming a built-in knowledge preserves its canonical specialization") {
        val character = empty.copy(skills = mapOf("knowledge_academic" to CharacterSkill(
            "knowledge_academic", "knowledge_academic", name = "Любимое знание", rank = 4,
        )))
        val feat = edgeEntry("renamed", "Renamed", "Знание (Академическое) 4")
        check(DevelopmentRules(character, catalog, DevelopmentProgress()).requirements(feat).single().status == RequirementStatus.OK)
    }
    scenario("rank-five mana satisfies a rank-three requirement") {
        val character = empty.copy(magic = empty.magic.copy(manaRank = 5))
        val feat = edgeEntry("mana", "Mana", "Базовый запас маны 3")
        check(DevelopmentRules(character, catalog, DevelopmentProgress()).requirements(feat).single().status == RequirementStatus.OK)
    }
    scenario("rankless mana requirement is deterministic when missing") {
        val feat = edgeEntry("mana-empty", "Mana", "Базовый запас маны")
        check(DevelopmentRules(empty, catalog, DevelopmentProgress()).requirements(feat).single().status == RequirementStatus.FAIL)
    }
    scenario("acquisition respects the user's specialization choice") {
        val feat = edgeEntry("choose", "Choose", "Ремесло (Кузнец / Бронник) 4")
        val local = DevelopmentCatalog("test", listOf(feat))
        val planner = DevelopmentAcquisitionPlanner(empty, local)
        val initial = planner.plan(DevelopmentAcquisitionRequest.single(feat.id))
        check(initial.canApply)
        val choice = initial.choices.single()
        val desired = choice.options.indexOfFirst { "Бронник" in it.label }
        check(desired >= 0)
        val chosen = planner.plan(DevelopmentAcquisitionRequest.single(feat.id, choiceSelections = mapOf(choice.id to desired)))
        check(chosen.canApply)
        check(chosen.projectedCharacter.skills.values.any { it.name == "Ремесло (Бронник)" && it.rank == 4 })
        check(chosen.projectedCharacter.skills.values.none { it.name == "Ремесло (Кузнец)" })
    }
    scenario("insufficient XP blocks an otherwise valid automatic plan") {
        val feat = edgeEntry("budget", "Budget", "Знание (Магия) 3")
        val plan = DevelopmentAcquisitionPlanner(empty.copy(experience = 0), DevelopmentCatalog("test", listOf(feat)))
            .plan(DevelopmentAcquisitionRequest.single(feat.id))
        check(!plan.canApply && !plan.canAfford)
    }
    scenario("explicit GM agreement is not silently accepted") {
        val feat = edgeEntry("unknown", "Unknown", "Требуется согласование с мастером")
        check(DevelopmentRules(empty, catalog, DevelopmentProgress()).requirements(feat).single().status == RequirementStatus.MANUAL)
    }
    scenario("source rank contradiction remains explicit") {
        val parent = edgeEntry("parent", "Одноранговый навык")
        val child = edgeEntry("child", "Child", "Одноранговый навык 2")
        val local = DevelopmentCatalog("test", listOf(parent, child))
        check(DevelopmentRules(empty, local, DevelopmentProgress()).requirements(child).single().status == RequirementStatus.MANUAL)
        check(!DevelopmentAcquisitionPlanner(empty, local).plan(DevelopmentAcquisitionRequest.single(child.id)).canApply)
    }
    check(failures.isEmpty()) { failures.joinToString("\n") }
    println("AUTOMATIC_REQUIREMENT_EDGES_OK")
}
