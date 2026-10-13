import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.*
import com.furybook.dubl.state.CharacterSession

private class MemoryStore(initial: AppSnapshot) : CharacterStore {
    var value = initial
    override fun load(): AppSnapshot = value
    override fun save(snapshot: AppSnapshot) { value = snapshot }
}

private fun entry(id: String, name: String, cost: Int = 10) = DevelopmentEntry(
    id = id,
    name = name,
    section = "Навыки",
    category = "Тест",
    cost = cost,
    costType = DevelopmentCostType.XP,
    maxRank = 1,
    requirements = "",
    benefit = "Эффект",
    notes = "",
    tags = emptyList(),
    accessId = null,
    abilityOptions = emptyList(),
    incomplete = false,
    repeatable = false,
    perfectRoot = false,
    mechanicsConflict = "",
    conflictNote = "",
)

fun main() {
    val character = DublCharacter(id = "hero")
    val store = MemoryStore(AppSnapshot(listOf(character), character.id))
    val ids = ArrayDeque(listOf("custom-uid"))
    val session = CharacterSession(store, idFactory = { ids.removeFirst() })

    val override = entry("feat-test", "Локальная версия", 30)
    session.setDevelopmentOverride(override)
    check(session.active.developmentOverrides["feat-test"]?.cost == 30)

    session.resetDevelopmentOverride("feat-test")
    check("feat-test" !in session.active.developmentOverrides)

    val created = session.addCustomDevelopment(entry("", "Своя способность", 17))
    check(created == "custom-development-custom-uid")
    check(session.active.customDevelopmentEntries.single().id == created)

    val changed = session.updateCustomDevelopment(entry(created!!, "Своя способность 2", 25))
    check(changed)
    check(session.active.customDevelopmentEntries.single().cost == 25)

    session.setDevelopmentRank(created, 1)
    check(session.active.development[created]?.rank == 1)
    session.removeCustomDevelopment(created)
    check(session.active.customDevelopmentEntries.isEmpty())
    check(created !in session.active.development) { "removing custom definition must not leave orphan ownership" }

    println("DEVELOPMENT_OVERRIDE_SESSION_OK")
}
