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

    // Compatibility navigation is direct metadata only; executable text grammar was removed.
    check(planner.unlocks(powerStrike.id).isEmpty())
    check(planner.unlockCounts()[access.id] == 1)
    for (request in listOf(
        DevelopmentAcquisitionRequest.single(reckless.id, includeTarget=false),
        DevelopmentAcquisitionRequest.single(reckless.id),
        DevelopmentAcquisitionRequest.single(access.id, optionIndex=1),
        DevelopmentAcquisitionRequest.single(child.id, enforceBudget=false),
        DevelopmentAcquisitionRequest.single(manual.id),
    )) {
        val plan = planner.plan(request)
        check(!plan.canApply)
        check(plan.steps.isEmpty())
        check(plan.unresolvedRequirements.single().contains("StoredCharacter"))
        check(plan.projectedCharacter == character)
    }
    // Untyped application callers cannot write a guessed canonical purchase or create an Undo record.
    val store = InMemoryCharacterStore(AppSnapshot(listOf(character), character.id))
    val session = CharacterSession(store) { "unused" }
    val before = session.active
    val undo = ApplicationUndoManager()
    val application = DevelopmentApplication(session, undo)
    val result = application.acquire(catalog, DevelopmentAcquisitionRequest.single(reckless.id))
    check(!result.applied)
    check(session.active == before)
    check(store.load().characters.single() == before)
    check(!undo.canUndo)
    println("DEVELOPMENT_ACQUISITION_OK")
}
