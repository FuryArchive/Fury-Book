package com.furybook.dubl.ui.editor

import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.ui.editor.EditorFixtures.catalog
import com.furybook.dubl.model.effectiveDevelopmentCatalog
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.editor.EditorFixtures.keenHearing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorChangesTest {
    private val catalogFor: (DublCharacter) -> com.furybook.dubl.model.DevelopmentCatalog = { it.effectiveDevelopmentCatalog(catalog) }

    private fun point(character: DublCharacter) = CharacterRestorePoint(character, CharacterSheetExtras())
    private fun changes(after: DublCharacter, before: DublCharacter = hornet) = editorChanges(point(before), point(after), catalogFor, includeChi = false)

    @Test
    fun attributeAndSkillLines() {
        val after = hornet.copy(
            attributes = hornet.attributes + (AttributeId.DEXTERITY to AttributeValue(base = 9)),
            skills = hornet.skills + ("stealth" to hornet.skills.getValue("stealth").copy(rank = 6)),
        )
        val lines = changes(after).lines
        assertTrue(EditorChangeLine("Ловкость 8 → 9", 95) in lines, lines.toString())
        assertTrue(EditorChangeLine("Скрытность 5 → 6", 60) in lines, lines.toString())
    }

    @Test
    fun perkLines() {
        val added = hornet.copy(development = hornet.development + (keenHearing.id to OwnedDevelopment(rank = 1)))
        assertEquals(listOf(EditorChangeLine("+ Острый слух", 30)), changes(added).lines)
        assertEquals(listOf(EditorChangeLine("− Острый слух", -30)), changes(hornet, before = added).lines)
        val raised = added.copy(development = added.development + (keenHearing.id to OwnedDevelopment(rank = 2)))
        assertEquals(listOf(EditorChangeLine("Острый слух: ранг 2", 30)), changes(raised, before = added).lines)
    }

    @Test
    fun experienceAndCreation() {
        val lines = changes(hornet.copy(experience = 4680, creationComplete = false)).lines.map { it.text }
        assertTrue("Опыт 4580 → 4680" in lines, lines.toString())
        assertTrue("Создание открыто снова" in lines, lines.toString())
        assertEquals("Потрачено 110 XP", editorNetText(spent = 110, freeDelta = -110))
        assertEquals("Возвращено 10 XP", editorNetText(spent = -10, freeDelta = 10))
        assertEquals("Опыт не изменился", editorNetText(spent = 0, freeDelta = 0))
        // GM XP is not a refund: it raises free XP without anything being returned.
        assertEquals("Свободно +3500 XP", editorNetText(spent = 0, freeDelta = 3500))
        assertEquals("Потрачено 545 XP · свободно +2955", editorNetText(spent = 545, freeDelta = 2955))
        val gm = changes(hornet.copy(experience = hornet.experience + 100))
        assertEquals(0, gm.spent)
        assertEquals(-100, gm.netSpent)
        assertEquals("−95 XP", editorXpText(95))
        assertEquals("+10 XP", editorXpText(-10))
        assertEquals("", editorXpText(0))
    }

    @Test
    fun netSpentFollowsRemainingXp() {
        val after = hornet.copy(development = hornet.development + (keenHearing.id to OwnedDevelopment(rank = 1)))
        assertEquals(30, changes(after).netSpent)
    }

    @Test
    fun noChangesIsEmpty() {
        val result = changes(hornet)
        assertTrue(result.lines.isEmpty())
        assertEquals(0, result.netSpent)
    }

    @Test
    fun linesForEveryChangeUndoAllWouldRevert() {
        assertEquals(listOf("Опыт создания ${hornet.creationExperience} → 900"), changes(hornet.copy(creationExperience = 900)).lines.map { it.text })
        assertEquals(listOf("Поправка опыта: 50"), changes(hornet.copy(xpAdjustment = 50)).lines.map { it.text })
        val added = hornet.copy(skills = hornet.skills + ("custom-1" to com.furybook.dubl.model.CharacterSkill(id = "custom-1", name = "Гадание")))
        assertEquals(listOf("+ Гадание"), changes(added).lines.map { it.text })
        assertEquals(listOf("− Гадание"), changes(hornet, before = added).lines.map { it.text })
        val owned = hornet.copy(development = hornet.development + (keenHearing.id to OwnedDevelopment(rank = 1)))
        val switched = owned.copy(development = owned.development + (keenHearing.id to OwnedDevelopment(rank = 1, optionIndex = 1)))
        assertEquals(listOf("Острый слух: другой вариант"), changes(switched, before = owned).lines.map { it.text })
        assertEquals(listOf("Другие изменения"), changes(hornet.copy(hpCurrent = hornet.hpCurrent - 1)).lines.map { it.text })
    }

    @Test
    fun settingsOnUnrankedBuiltInAreNotAnAddedSkill() {
        val after = hornet.copy(skills = hornet.skills + ("riding" to com.furybook.dubl.model.CharacterSkill(id = "riding", definitionId = "riding", modifier = 1)))
        assertEquals(listOf("Верховая езда: настройки изменены"), changes(after).lines.map { it.text })
    }

    @Test
    fun hiddenAndEffectLines() {
        val after = hornet.copy(hiddenSkillIds = setOf("stealth"), disabledSkillEffectIds = setOf("fx1"))
        val lines = editorChanges(point(hornet), point(after), catalogFor, includeChi = false, effectNames = mapOf("fx1" to "Ловкие пальцы")).lines.map { it.text }
        assertTrue("Скрыто: Скрытность" in lines, lines.toString())
        assertTrue("Автоматизация: Ловкие пальцы выкл." in lines, lines.toString())
        val back = editorChanges(point(after), point(hornet), catalogFor, includeChi = false, effectNames = mapOf("fx1" to "Ловкие пальцы")).lines.map { it.text }
        assertTrue("Возвращено: Скрытность" in back && "Автоматизация: Ловкие пальцы вкл." in back, back.toString())
    }

    @Test
    fun finishingCreationIsOneLineNotSettingsForEverySkill() {
        val creating = hornet.copy(creationComplete = false)
        val finished = hornet.copy(creationComplete = true, skills = hornet.skills.mapValues { (_, s) -> s.copy(creationRank = s.rank) })
        assertEquals(listOf("Создание завершено"), changes(finished, before = creating).lines.map { it.text })
    }

    @Test
    fun localEditRepricesOwnedRanks() {
        val after = hornet.copy(developmentOverrides = mapOf(EditorFixtures.athleticism.id to EditorFixtures.athleticism.copy(cost = 100)))
        val result = changes(after)
        assertEquals(listOf(EditorChangeLine("Атлетичность: локальная правка", 60)), result.lines)
        assertEquals(60, result.netSpent)
        assertEquals(listOf(EditorChangeLine("Атлетичность: правка сброшена", -60)), changes(hornet, before = after).lines)
    }

    @Test
    fun deletedOwnedOwnPerkIsOneLine() {
        val own = EditorFixtures.entry("custom-development-1", "Гадание", cost = 25)
        val before = hornet.copy(customDevelopmentEntries = listOf(own), development = hornet.development + (own.id to OwnedDevelopment(rank = 1)))
        assertEquals(listOf(EditorChangeLine("− Гадание (своя запись)", -25)), changes(hornet, before = before).lines)
        assertEquals(listOf(EditorChangeLine("+ Гадание (своя запись)", 0)), changes(hornet.copy(customDevelopmentEntries = listOf(own))).lines)
        val renamed = before.copy(customDevelopmentEntries = listOf(own.copy(name = "Ворожба")))
        assertEquals(listOf("Ворожба: запись изменена"), changes(renamed, before = before).lines.map { it.text })
    }

    @Test
    fun chiLines() {
        val after = hornet.copy(chiEnabled = true, chiBonusRanks = 2)
        val lines = editorChanges(point(hornet), point(after), catalogFor, includeChi = true).lines
        assertTrue(EditorChangeLine("ЦИ включено", 0) in lines, lines.toString())
        assertTrue(EditorChangeLine("ЦИ: доп. ранги 0 → 2", 2 * CharacterEconomy.CHI_BONUS_RANK_XP) in lines, lines.toString())
        assertTrue(EditorChangeLine("ЦИ выключено", 0) in editorChanges(point(after), point(hornet), catalogFor, includeChi = true).lines)
    }

    @Test
    fun takingANewOwnPerkIsPricedWithItsOwnCost() {
        val own = EditorFixtures.entry("custom-development-1", "Гадание", cost = 25)
        val after = hornet.copy(customDevelopmentEntries = listOf(own), development = hornet.development + (own.id to OwnedDevelopment(rank = 1)))
        val result = changes(after)
        assertEquals(listOf(EditorChangeLine("+ Гадание (своя запись)", 0), EditorChangeLine("+ Гадание", 25)), result.lines)
        assertEquals(25, result.netSpent)
    }

    @Test
    fun takingALocallyEditedPerkIsPricedWithTheEdit() {
        val after = hornet.copy(
            developmentOverrides = mapOf(keenHearing.id to keenHearing.copy(cost = 10)),
            development = hornet.development + (keenHearing.id to OwnedDevelopment(rank = 1)),
        )
        val result = changes(after)
        assertEquals(listOf(EditorChangeLine("Острый слух: локальная правка", 0), EditorChangeLine("+ Острый слух", 10)), result.lines)
        assertEquals(10, result.netSpent)
    }

    private val shieldSpell = com.furybook.dubl.model.KnownSpell(uid = "s1", name = "Щит", school = "Ограждение", cost = 4)
    private val mage = hornet.copy(
        magic = com.furybook.dubl.model.CharacterMagic(
            manaRank = 1,
            schools = listOf(com.furybook.dubl.model.MagicSchool("Разрушение", 2), com.furybook.dubl.model.MagicSchool("Ограждение", 3)),
            spells = listOf(shieldSpell),
        ),
    )

    @Test
    fun manaRankAndSchoolPowerArePriced() {
        val after = mage.copy(magic = mage.magic.copy(manaRank = 2, schools = listOf(com.furybook.dubl.model.MagicSchool("Разрушение", 4), mage.magic.schools[1])))
        val lines = changes(after, before = mage).lines
        assertTrue(EditorChangeLine("Ранг маны 1 → 2", 100) in lines, lines.toString())
        assertTrue(EditorChangeLine("Разрушение: сила 2 → 4", 50) in lines, lines.toString())
        assertEquals(2, lines.size, lines.toString())
    }

    @Test
    fun removedSchoolAndSpellArePriced() {
        val after = mage.copy(magic = mage.magic.copy(schools = listOf(mage.magic.schools[0]), spells = emptyList()))
        val lines = changes(after, before = mage).lines
        assertTrue(EditorChangeLine("− школа Ограждение", -75) in lines, lines.toString())
        assertTrue(EditorChangeLine("− заклинание Щит", -20) in lines, lines.toString())
        val back = changes(mage, before = after).lines
        assertTrue(EditorChangeLine("+ школа Ограждение", 75) in back, back.toString())
        assertTrue(EditorChangeLine("+ заклинание Щит", 20) in back, back.toString())
    }

    @Test
    fun learnToggleIsPriced() {
        val after = mage.copy(magic = mage.magic.copy(spells = listOf(shieldSpell.copy(learned = false))))
        assertEquals(listOf(EditorChangeLine("Щит: не изучено", -20)), changes(after, before = mage).lines)
        val renamed = mage.copy(magic = mage.magic.copy(spells = listOf(shieldSpell.copy(range = "10 метров"))))
        assertEquals(listOf(EditorChangeLine("Щит: изменено", 0)), changes(renamed, before = mage).lines)
    }

    @Test
    fun gearLinesCostNothing() {
        val rope = com.furybook.dubl.model.GearItem(uid = "r", name = "Верёвка")
        val before = hornet.copy(gear = com.furybook.dubl.model.CharacterGear(items = listOf(rope)))
        val dagger = com.furybook.dubl.model.GearItem(uid = "d", name = "Кинжал")
        val after = before.copy(gear = com.furybook.dubl.model.CharacterGear(loadAutomatic = false, loadManual = 4.0, items = listOf(rope.copy(quantity = 3, carried = false), dagger)))
        val result = changes(after, before = before)
        assertEquals(
            listOf(
                EditorChangeLine("Верёвка: ×1 → ×3", 0), EditorChangeLine("Верёвка: не с собой", 0),
                EditorChangeLine("+ предмет Кинжал", 0), EditorChangeLine("Нагрузка: вручную 4", 0),
            ),
            result.lines,
        )
        assertEquals(0, result.netSpent)
        val removed = changes(before, before = before.copy(gear = before.gear.copy(items = listOf(rope, dagger)))).lines
        assertEquals(listOf(EditorChangeLine("− предмет Кинжал", 0)), removed)
    }
}
