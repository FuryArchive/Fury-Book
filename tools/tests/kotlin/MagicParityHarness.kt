import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.*
import com.furybook.dubl.state.CharacterSession

fun main() {
    val initial = DublCharacter(
        id = "mage",
        experience = 10_000,
        creationExperience = 10_000,
        magic = CharacterMagic(
            manaRank = 2,
            schools = listOf(MagicSchool("Разрушение", 2)),
        ),
        manaCurrent = 4,
    ).normalized()
    val session = CharacterSession(InMemoryCharacterStore(AppSnapshot(listOf(initial), initial.id)), idFactory = { "id-1" })

    check(session.active.effectiveManaMaximum == 4)
    check(session.addMagicSchool("Воплощение", 6, "test"))
    check(session.active.effectiveManaMaximum == 13)
    check(session.active.manaCurrent == 13) { "adding a school during creation must refill mana to the new maximum" }

    val invocationIndex = session.active.magic.schools.indexOfFirst { it.name == "Воплощение" }
    check(invocationIndex >= 0)
    check(session.updateMagicSchool(invocationIndex, "Воплощение", 10, "raised"))
    check(session.active.effectiveManaMaximum == 25)
    check(session.active.manaCurrent == 25) { "editing school power during creation must refill mana" }

    session.changeMana(-1000)
    check(session.active.manaCurrent == 0)
    session.changeMana(1000)
    check(session.active.manaCurrent == session.active.effectiveManaMaximum)

    val completeBefore = session.active.magic.manaRank
    session.updateActive { it.copy(creationComplete = true) }
    session.setMagicManaRank(5)
    check(session.active.magic.manaRank == completeBefore) { "base mana rank is creation-only" }

    val incomplete = SpellCatalogEntry(
        id = "draft",
        name = "Черновик",
        school = "Молитва",
        cost = 0,
        manaText = "",
        time = "",
        range = "",
        area = "",
        action = "",
        duration = "",
        description = "incomplete",
        enhancement = "",
        incomplete = true,
        conflictNote = "",
    )
    check(session.addCatalogSpell(incomplete)) { "incomplete canonical spells must remain locally addable" }
    val unresolved = session.active.magic.spells.single { it.catalogId == incomplete.id }
    check(unresolved.incomplete)
    session.updateSpell(unresolved.uid) { it.copy(description = "локальная трактовка") }
    check(session.active.magic.spells.single { it.uid == unresolved.uid }.description == "локальная трактовка")

    val catalogSpell = SpellCatalogEntry(
        id = "spell-ok",
        name = "Искра",
        school = "Разрушение / Воплощение",
        cost = 6,
        manaText = "6",
        time = "1 ОД",
        range = "20 м",
        area = "1 цель",
        action = "Магия",
        duration = "Мгновенно",
        description = "Описание",
        enhancement = "Усиление",
        incomplete = false,
        conflictNote = "",
    )
    check(session.addCatalogSpell(catalogSpell))
    val known = session.active.magic.spells.single { it.catalogId == catalogSpell.id }
    check(known.name == catalogSpell.name)
    check(known.school == catalogSpell.school)
    check(known.cost == catalogSpell.cost)
    check(known.manaText == catalogSpell.manaText)
    check(known.time == catalogSpell.time)
    check(known.range == catalogSpell.range)
    check(known.area == catalogSpell.area)
    check(known.action == catalogSpell.action)
    check(known.duration == catalogSpell.duration)
    check(known.description == catalogSpell.description)
    check(known.enhancement == catalogSpell.enhancement)
    check(known.learned)

    check(MagicEquipmentRules.manaRankXp(session.active) == 200)
    check(MagicEquipmentRules.learnXpCost(6) == 20)
    check(MagicEquipmentRules.learnedSpellXp(session.active) == 20)
    check(MagicEquipmentRules.magicSchoolPowerXp(session.active) == (2 + 10) * 25)

    println("MAGIC_PARITY_OK")
}
