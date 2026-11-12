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
        // Remove inheritance and incomplete-benefit flags to identify requirement syntax gaps.
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
    scenario("reported rank-five knowledge specializations") {
        assertStatus(scholar(knowledge("academic", 5, "Знание (Академическое)"), knowledge("custom", 5, "Знание (Подмышки)")), twoKnowledge, RequirementStatus.OK)
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
    scenario("missing named specialization fails rather than becoming manual") {
        assertStatus(scholar(), "Знание (Магия) 3", RequirementStatus.FAIL)
    }
    scenario("satisfied counted prerequisite permits acquisition without extra ranks") {
        val character = scholar(knowledge("a", 5), knowledge("b", 5))
        val entry = feat(twoKnowledge)
        val plan = DevelopmentAcquisitionPlanner(character, DevelopmentCatalog("test", listOf(entry)))
            .plan(DevelopmentAcquisitionRequest.single(entry.id))
        check(plan.canApply) { "Plan rejected: ${plan.unresolvedRequirements}" }
        check(plan.projectedCharacter.skills == character.skills) { "Satisfied skills must not be changed" }
        check(plan.projectedCharacter.development[entry.id]?.rank == 1)
        check(character.development.isEmpty()) { "Planning mutated original character" }
    }
    check(failures.isEmpty()) { failures.joinToString("\n") }
    println("AUTOMATIC_REQUIREMENTS_OK")
}
