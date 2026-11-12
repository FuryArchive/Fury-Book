package com.furybook.dubl.model

import com.furybook.dubl.data.parseDevelopmentCatalog
import java.io.File

private fun feat(requirements: String) = DevelopmentEntry(
    id = "test-erudition", name = "Эрудиция", section = "Навыки", category = "Знания",
    cost = 30, costType = DevelopmentCostType.XP, maxRank = 1,
    requirements = requirements, benefit = "", notes = "", tags = emptyList(),
    accessId = null, abilityOptions = emptyList(), incomplete = false, repeatable = false,
    perfectRoot = false, mechanicsConflict = "", conflictNote = "",
)

private fun knowledge(id: String, rank: Int, name: String = "Знание ($id)", template: Boolean = true) = CharacterSkill(
    id = id, definitionId = if (template) "knowledge_template" else null,
    name = name, rank = rank, categoryOverride = SkillCategory.KNOWLEDGE,
)

private fun scholar(vararg skills: CharacterSkill) = DublCharacter(
    id = "regression", experience = 10000, skills = skills.associateBy { it.id },
)

private fun assertStatus(character: DublCharacter, requirement: String, expected: RequirementStatus) {
    val entry = feat(requirement)
    val catalog = DevelopmentCatalog("regression", listOf(entry))
    val checks = DevelopmentRules(character, catalog, DevelopmentProgress(character.development)).requirements(entry)
    check(checks.size == 1 && checks.single().status == expected) {
        "$requirement: expected $expected, got $checks"
    }
}

private fun audit() {
    val files = File("shared/src/commonMain/resources/fcp").walkTopDown()
        .filter { it.isFile && it.name.startsWith("development_") && it.extension == "json" }.toList()
    val entries = files.flatMap { parseDevelopmentCatalog(it.readText()).entries }.distinctBy { it.id }
    val catalog = DevelopmentCatalog("audit", entries)
    val empty = DublCharacter(id = "audit")
    val rules = DevelopmentRules(empty, catalog, DevelopmentProgress())
    var count = 0
    entries.forEach { entry ->
        val isolated = entry.copy(accessId = null, incomplete = false, mechanicsConflict = "", perfectRoot = false)
        val manual = rules.requirements(isolated).filter { it.status == RequirementStatus.MANUAL }
        if (manual.isNotEmpty()) {
            count++
            println("MANUAL | ${entry.id} | ${entry.name} | ${entry.requirements} | ${manual.joinToString { it.text }}")
        }
    }
    println("REQUIREMENTS_AUDIT entries=${entries.size} manualEntries=$count")
}

fun main(args: Array<String>) {
    if ("--audit" in args) { audit(); return }
    val failures = mutableListOf<String>()
    fun scenario(name: String, body: () -> Unit) {
        try { body(); println("PASS $name") }
        catch (e: Throwable) { failures += "$name: ${e.message}"; println("FAIL ${failures.last()}") }
    }
    val twoKnowledge = "Два и более Знаний 4 ранга"
    scenario("reported built-in and custom rank-five knowledge") {
        assertStatus(scholar(CharacterSkill("knowledge_academic", "knowledge_academic", rank = 5), knowledge("custom", 5, "Знание (Подмышки)")), twoKnowledge, RequirementStatus.OK)
    }
    scenario("two rank-four knowledge specializations") {
        assertStatus(scholar(knowledge("a", 4), knowledge("b", 4)), twoKnowledge, RequirementStatus.OK)
    }
    scenario("one rank-three knowledge is insufficient") {
        assertStatus(scholar(knowledge("a", 5), knowledge("b", 3)), twoKnowledge, RequirementStatus.FAIL)
    }
    scenario("one knowledge cannot count twice") {
        assertStatus(scholar(knowledge("a", 10)), twoKnowledge, RequirementStatus.FAIL)
    }
    scenario("untrained knowledge does not count") {
        assertStatus(scholar(knowledge("a", 5), knowledge("b", 0)), twoKnowledge, RequirementStatus.FAIL)
    }
    scenario("renamed template-backed knowledge retains family") {
        assertStatus(scholar(knowledge("a", 4, "История империи"), knowledge("b", 4, "Космология")), twoKnowledge, RequirementStatus.OK)
    }
    scenario("freeform knowledge names are supported") {
        assertStatus(scholar(knowledge("a", 5, "Знание (История)", false), knowledge("b", 5, "Знание: Культура", false)), twoKnowledge, RequirementStatus.OK)
    }
    scenario("knowledge category alone is not a rule identity") {
        assertStatus(scholar(knowledge("a", 5), knowledge("other", 5, "Медицина", false)), twoKnowledge, RequirementStatus.FAIL)
    }
    scenario("hidden learned knowledge still counts") {
        assertStatus(scholar(knowledge("a", 4), knowledge("b", 4)).copy(hiddenSkillIds = setOf("b")), twoKnowledge, RequirementStatus.OK)
    }
    scenario("duplicate copies of the same specialization cannot satisfy a count") {
        assertStatus(scholar(knowledge("a", 5, "Знание (История)"), knowledge("b", 5, "Знание: История")), twoKnowledge, RequirementStatus.FAIL)
    }
    scenario("missing named specialization fails rather than becoming manual") {
        assertStatus(scholar(), "Знание (Магия) 3", RequirementStatus.FAIL)
    }
    scenario("postfix any specialization syntax") {
        assertStatus(scholar(knowledge("a", 5)), "Знание 4 (Любое)", RequirementStatus.OK)
    }
    scenario("profession family is supported") {
        assertStatus(scholar(CharacterSkill("cook", "profession_template", name = "Профессия (Повар)", rank = 5)), "Профессия (любая) 4", RequirementStatus.OK)
    }
    scenario("alternative specializations within parentheses") {
        assertStatus(scholar(CharacterSkill("smith", "craft_template", name = "Ремесло (Бронник)", rank = 4)), "Ремесло (Кузнец / Бронник) 4", RequirementStatus.OK)
    }
    scenario("skill prefix on an alternative") {
        assertStatus(scholar(CharacterSkill("throwing", "throwing", rank = 3)), "умение Метание или Стрельба 3", RequirementStatus.OK)
    }
    scenario("one-of-skills prefix with slash specialization") {
        assertStatus(scholar(CharacterSkill("dance", "performance_template", name = "Исполнение (Танец)", rank = 1)), "одно из умений: Знание (Астрология) или Исполнение (Танец/Медитация)", RequirementStatus.OK)
    }
    scenario("one-rank feat does not inherit a skill rank across OR") {
        val meditation = feat("-").copy(id = "meditation", name = "Медитация")
        val entry = feat("Медитация (навык) или Внимательность 4")
        val catalog = DevelopmentCatalog("test", listOf(meditation, entry))
        val character = scholar().copy(development = mapOf(meditation.id to OwnedDevelopment(1)))
        val checks = DevelopmentRules(character, catalog, DevelopmentProgress(character.development)).requirements(entry)
        check(checks.all { it.status == RequirementStatus.OK }) { checks.toString() }
    }
    scenario("and-or spelling is an inclusive alternative") {
        val parry = feat("-").copy(id = "parry", name = "Парирование")
        val block = feat("-").copy(id = "block", name = "Блокирование")
        val entry = feat("Парирование и/или Блокирование")
        val catalog = DevelopmentCatalog("test", listOf(parry, block, entry))
        val character = scholar().copy(development = mapOf(block.id to OwnedDevelopment(1)))
        check(DevelopmentRules(character, catalog, DevelopmentProgress(character.development)).requirements(entry).all { it.status == RequirementStatus.OK })
    }
    scenario("satisfied counted prerequisite permits acquisition without extra ranks") {
        val character = scholar(knowledge("a", 5), knowledge("b", 5))
        val entry = feat(twoKnowledge)
        val plan = DevelopmentAcquisitionPlanner(character, DevelopmentCatalog("test", listOf(entry)))
            .plan(DevelopmentAcquisitionRequest.single(entry.id))
        check(plan.canApply) { "Plan rejected: ${plan.unresolvedRequirements}" }
        check(plan.projectedCharacter.skills == character.skills)
        check(plan.projectedCharacter.development[entry.id]?.rank == 1)
        check(plan.xpCost == 30)
        check(character.development.isEmpty())
    }
    scenario("planner trains only the missing rank of existing knowledge") {
        val character = scholar(knowledge("a", 5), knowledge("b", 3))
        val entry = feat(twoKnowledge)
        val catalog = DevelopmentCatalog("test", listOf(entry))
        val plan = DevelopmentAcquisitionPlanner(character, catalog).plan(DevelopmentAcquisitionRequest.single(entry.id))
        check(plan.canApply) { plan.unresolvedRequirements.toString() }
        check(plan.projectedCharacter.skills.getValue("b").rank == 4)
        check(plan.projectedCharacter.skills.getValue("a").rank == 5)
        check(plan.xpCost == 70) { "Expected 40 XP prerequisite + 30 XP feat, got ${plan.xpCost}" }
        check(DevelopmentRules(plan.projectedCharacter, catalog, DevelopmentProgress(plan.projectedCharacter.development)).requirements(entry).all { it.status == RequirementStatus.OK })
        check(character.skills.getValue("b").rank == 3)
    }
    scenario("planner does not invent an unspecified knowledge specialization") {
        val character = scholar(CharacterSkill("knowledge_academic", "knowledge_academic", rank = 5))
        val entry = feat(twoKnowledge)
        val plan = DevelopmentAcquisitionPlanner(character, DevelopmentCatalog("test", listOf(entry))).plan(DevelopmentAcquisitionRequest.single(entry.id))
        check(!plan.canApply)
        check(plan.projectedCharacter.skills.keys == character.skills.keys)
    }
    scenario("planner creates an explicitly required named specialization") {
        val character = scholar()
        val entry = feat("Знание (Магия) 3")
        val catalog = DevelopmentCatalog("test", listOf(entry))
        val plan = DevelopmentAcquisitionPlanner(character, catalog).plan(DevelopmentAcquisitionRequest.single(entry.id))
        check(plan.canApply) { plan.unresolvedRequirements.toString() }
        check(plan.projectedCharacter.resolvedSkills().any { it.name == "Знание (Магия)" && it.rank == 3 })
        check(plan.xpCost == 90)
        check(character.skills.isEmpty())
    }
    scenario("unknown source condition is not silently accepted") {
        assertStatus(scholar(), "Неописанное условие мастера", RequirementStatus.MANUAL)
    }
    check(failures.isEmpty()) { failures.joinToString("\n") }
    println("AUTOMATIC_REQUIREMENTS_OK")
}
