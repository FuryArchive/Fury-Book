import com.furybook.dubl.application.ApplicationUndoManager
import com.furybook.dubl.application.DevelopmentApplication
import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.*
import com.furybook.dubl.state.CharacterSession

private fun entry(
    id: String,
    name: String,
    cost: Int = 30,
    costType: DevelopmentCostType = DevelopmentCostType.XP,
    maxRank: Int = 1,
    requirements: String = "-",
    accessId: String? = null,
    abilityOptions: List<AbilityOption> = emptyList(),
    section: String = "Основные навыки",
): DevelopmentEntry = DevelopmentEntry(
    id = id,
    name = name,
    section = section,
    category = "Атакующие",
    cost = cost,
    costType = costType,
    maxRank = maxRank,
    requirements = requirements,
    benefit = "Тестовый эффект",
    notes = "",
    tags = emptyList(),
    accessId = accessId,
    abilityOptions = abilityOptions,
    incomplete = false,
    repeatable = false,
    perfectRoot = false,
    mechanicsConflict = "",
    conflictNote = "",
)

private fun baseCharacter(): DublCharacter {
    val attrs = defaultAttributes() +
        (AttributeId.STRENGTH to AttributeValue(base = 3)) +
        (AttributeId.WILL to AttributeValue(base = 3))
    return DublCharacter(
        id = "c1",
        experience = 2_000,
        creationExperience = 2_000,
        attributes = attrs,
        skills = mapOf(
            "melee_weapon" to CharacterSkill("melee_weapon", "melee_weapon", rank = 2),
            "unarmed" to CharacterSkill("unarmed", "unarmed", rank = 0),
        ),
    )
}

fun main() {
    val powerStrike = entry(
        id = "power-strike",
        name = "Мощный удар",
        cost = 40,
        requirements = "Сила 3",
    )
    val reckless = entry(
        id = "reckless",
        name = "Безрассудная атака",
        cost = 30,
        requirements = "Мощный удар, Воля 4; Холодное оружие 4 ИЛИ Рукопашный бой 4",
    )
    val access = entry(
        id = "access",
        name = "Тестовая ветка",
        cost = 1,
        costType = DevelopmentCostType.ABILITY,
        abilityOptions = listOf(AbilityOption("Мастерство", 1), AbilityOption("Сверхъестественное", 2)),
        section = "Ветки способностей",
    )
    val child = entry(
        id = "child",
        name = "Дочерний навык",
        cost = 25,
        accessId = access.id,
        requirements = "Мощный удар",
        section = "Ветки способностей",
    )
    val manual = entry(
        id = "manual",
        name = "Сюжетная техника",
        requirements = "На усмотрение мастера",
    )
    val catalog = DevelopmentCatalog("test", listOf(powerStrike, reckless, access, child, manual))

    val character = baseCharacter()
    val planner = DevelopmentAcquisitionPlanner(character, catalog)

    val powerStrikeUnlocks = planner.unlocks(powerStrike.id)
    check(powerStrikeUnlocks.map { it.id }.toSet() == setOf(reckless.id, child.id)) { "unexpected reverse unlocks: $powerStrikeUnlocks" }
    check(planner.unlockCounts()[powerStrike.id] == 2)
    check(planner.unlockCounts()[access.id] == 1)

    val prereqs = planner.plan(
        DevelopmentAcquisitionRequest.single(reckless.id, includeTarget = false),
    )
    check(prereqs.unresolvedRequirements.isEmpty())
    check(prereqs.steps.any { it is DevelopmentAcquisitionStep.Attribute && it.attribute == AttributeId.WILL && it.fromValue == 3 && it.toValue == 4 })
    check(prereqs.steps.any { it is DevelopmentAcquisitionStep.Skill && it.skillId == "melee_weapon" && it.fromRank == 2 && it.toRank == 4 })
    check(prereqs.steps.any { it is DevelopmentAcquisitionStep.Development && it.entryId == powerStrike.id })
    check(prereqs.steps.none { it is DevelopmentAcquisitionStep.Development && it.entryId == reckless.id })
    check(prereqs.xpCost == 180) { "expected 180 XP prerequisites, got ${prereqs.xpCost}: ${prereqs.steps}" }
    check(prereqs.choices.size == 1)
    check(prereqs.choices.first().options.size == 2)
    check(prereqs.choices.first().selectedIndex == 0)

    val choiceId = prereqs.choices.first().id
    val unarmedPath = planner.plan(
        DevelopmentAcquisitionRequest.single(
            reckless.id,
            includeTarget = false,
            choiceSelections = mapOf(choiceId to 1),
        ),
    )
    check(unarmedPath.steps.any { it is DevelopmentAcquisitionStep.Skill && it.skillId == "unarmed" && it.toRank == 4 })
    check(unarmedPath.steps.none { it is DevelopmentAcquisitionStep.Skill && it.skillId == "melee_weapon" && it.toRank == 4 })
    check(unarmedPath.xpCost == 210)

    val withTarget = planner.plan(DevelopmentAcquisitionRequest.single(reckless.id, includeTarget = true))
    check(withTarget.xpCost == 210)
    check(withTarget.steps.any { it is DevelopmentAcquisitionStep.Development && it.entryId == reckless.id })
    check(withTarget.canApply)

    val cheapestAccess = planner.plan(DevelopmentAcquisitionRequest.single(access.id, includeTarget = true))
    val cheapestAccessStep = cheapestAccess.steps.filterIsInstance<DevelopmentAcquisitionStep.Development>().single { it.entryId == access.id }
    check(cheapestAccessStep.optionIndex == 0)
    check(cheapestAccessStep.abilityCost == 1)

    val explicitAccess = planner.plan(DevelopmentAcquisitionRequest.single(access.id, includeTarget = true, optionIndex = 1))
    val explicitAccessStep = explicitAccess.steps.filterIsInstance<DevelopmentAcquisitionStep.Development>().single { it.entryId == access.id }
    check(explicitAccessStep.optionIndex == 1)
    check(explicitAccessStep.abilityCost == 2)

    val nestedAccess = planner.plan(DevelopmentAcquisitionRequest.single(child.id, includeTarget = true))
    check(nestedAccess.steps.any { it is DevelopmentAcquisitionStep.Development && it.entryId == access.id && it.abilityCost == 1 })
    check(nestedAccess.steps.any { it is DevelopmentAcquisitionStep.Development && it.entryId == powerStrike.id })
    check(nestedAccess.steps.any { it is DevelopmentAcquisitionStep.Development && it.entryId == child.id })
    check(nestedAccess.abilityCost == 1)

    val unresolved = planner.plan(DevelopmentAcquisitionRequest.single(manual.id, includeTarget = true))
    check(unresolved.unresolvedRequirements.isNotEmpty())
    check(!unresolved.canApply)

    // Batch plans must deduplicate shared prerequisites.
    val batch = planner.plan(
        DevelopmentAcquisitionRequest(
            targets = listOf(
                DevelopmentAcquisitionTarget(reckless.id),
                DevelopmentAcquisitionTarget(child.id),
            ),
        ),
    )
    check(batch.steps.count { it is DevelopmentAcquisitionStep.Development && it.entryId == powerStrike.id } == 1)

    // Application re-plans and commits the whole plan as one atomic mutation.
    val store = InMemoryCharacterStore(AppSnapshot(listOf(character), character.id))
    val session = CharacterSession(store) { "unused" }
    val undo = ApplicationUndoManager()
    val application = DevelopmentApplication(session, undo)
    val result = application.acquire(catalog, DevelopmentAcquisitionRequest.single(reckless.id, includeTarget = true))
    check(result.applied)
    check(session.active.will == 4)
    check(session.active.resolveSkill("melee_weapon")!!.rank == 4)
    check(session.active.developmentRank(powerStrike.id) == 1)
    check(session.active.developmentRank(reckless.id) == 1)
    check(undo.canUndo)
    check(store.load().characters.single() == session.active) { "acquisition must save the entire projection" }
    check(undo.undoLast())
    check(session.active.will == 3)
    check(session.active.resolveSkill("melee_weapon")!!.rank == 2)
    check(session.active.developmentRank(reckless.id) == 0)
    check(store.load().characters.single() == session.active) { "Undo must save the prior character" }
    check(!undo.undoLast()) { "one purchase has exactly one Undo boundary" }

    val beforeManual = session.active
    val rejected = application.acquire(catalog, DevelopmentAcquisitionRequest.single(manual.id, includeTarget = true))
    check(!rejected.applied)
    check(session.active == beforeManual)
    check(store.load().characters.single() == beforeManual) { "rejected unresolved plan must not save a partial character" }

    println("DEVELOPMENT_ACQUISITION_OK")
}
