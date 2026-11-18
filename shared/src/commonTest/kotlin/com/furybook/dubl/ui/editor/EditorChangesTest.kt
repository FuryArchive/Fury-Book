package com.furybook.dubl.ui.editor

import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.ui.editor.EditorFixtures.catalog
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.editor.EditorFixtures.keenHearing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorChangesTest {
    private fun point(character: DublCharacter) = CharacterRestorePoint(character, CharacterSheetExtras())
    private fun changes(after: DublCharacter, before: DublCharacter = hornet) = editorChanges(point(before), point(after), catalog, includeChi = false)

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
        assertEquals("Потрачено 110 XP", editorNetText(110))
        assertEquals("Возвращено 10 XP", editorNetText(-10))
        assertEquals("Опыт не изменился", editorNetText(0))
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
}
