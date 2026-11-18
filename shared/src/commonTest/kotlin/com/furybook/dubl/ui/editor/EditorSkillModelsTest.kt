package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AttributeId.DEXTERITY
import com.furybook.dubl.model.AttributeId.INTELLIGENCE
import com.furybook.dubl.model.AttributeId.PERCEPTION
import com.furybook.dubl.model.AttributeId.STRENGTH
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.ui.editor.EditorFixtures.catalog
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorSkillModelsTest {
    private fun host(character: DublCharacter = hornet) = FakeSheetHost(character, developmentCatalogOverride = catalog)

    @Test
    fun detailShowsBreakdownPerAllowedAttribute() {
        val stealth = CharacterSkill(id = "stealth", definitionId = "stealth", rank = 5, attributes = listOf(DEXTERITY, PERCEPTION))
        val detail = editorSkillDetail(host(hornet.copy(skills = hornet.skills + ("stealth" to stealth))), "stealth")!!
        assertEquals(listOf(DEXTERITY, PERCEPTION), detail.bonus.map { it.attribute })
        val dex = detail.bonus.first()
        assertEquals(8 + 5, dex.total)
        assertTrue("+5" in dex.breakdown, dex.breakdown)
        assertEquals("Скрытность", detail.name)
    }

    @Test
    fun customAndBuiltInFlags() {
        val host = host()
        val builtIn = editorSkillDetail(host, "stealth")!!
        assertTrue(builtIn.canResetToBook)
        assertEquals(false, builtIn.canDelete)
        assertNull(builtIn.origin)
        val id = host.addCustomSkill("Гадание", "", listOf(INTELLIGENCE), UntrainedRule.YES)!!
        val custom = editorSkillDetail(host, id)!!
        assertEquals(false, custom.canResetToBook)
        assertTrue(custom.canDelete)
        assertEquals("Своё", custom.origin)
    }

    @Test
    fun toggledAttributesKeepPrimaryAndOrder() {
        assertEquals(listOf(DEXTERITY, PERCEPTION, STRENGTH), editorToggledAttributes(listOf(DEXTERITY, PERCEPTION), STRENGTH))
        assertEquals(listOf(DEXTERITY), editorToggledAttributes(listOf(DEXTERITY, PERCEPTION), PERCEPTION))
        assertEquals(listOf(DEXTERITY), editorToggledAttributes(listOf(DEXTERITY), DEXTERITY))
        assertEquals(listOf(PERCEPTION), editorToggledAttributes(listOf(DEXTERITY, PERCEPTION), DEXTERITY))
    }

    @Test
    fun missingSkillHasNoDetail() {
        assertNull(editorSkillDetail(host(), "nope"))
    }
}
