package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DublEditorHostTest {
    private val character = DublCharacter(
        id = "c1", name = "Шершень", experience = 100,
        skills = mapOf("stealth" to CharacterSkill(id = "stealth", definitionId = "stealth", rank = 0)),
    )

    @Test
    fun fakeHostDelegatesEditorCalls() {
        val host: DublEditorHost = FakeSheetHost(character)
        val point = host.restorePoint()
        host.changeSkillRank("stealth", 1)
        assertEquals(1, host.active.skills.getValue("stealth").rank)
        val result = host.acquireDevelopment(DevelopmentAcquisitionRequest.single("a"))
        assertTrue(result.applied, result.message)
        assertEquals(1, host.active.development.getValue("a").rank)
        assertTrue(host.restore(point))
        assertEquals(0, host.active.skills.getValue("stealth").rank)
    }

    @Test
    fun skillSettingsReachTheApplication() {
        val host: DublEditorHost = FakeSheetHost(character)
        host.setSkillModifier("stealth", 2)
        host.setSkillFormulaNote("stealth", "+2 в темноте")
        host.setPreferredSkillAttribute("stealth", AttributeId.PERCEPTION)
        host.hideSkill("stealth")
        assertEquals(2, host.active.skills.getValue("stealth").modifier)
        assertEquals("+2 в темноте", host.active.skills.getValue("stealth").formulaNote)
        assertTrue("stealth" in host.active.hiddenSkillIds)
        assertEquals(AttributeId.PERCEPTION, host.extras.preferredSkillAttributes["stealth"])
        host.restoreSkill("stealth")
        assertTrue("stealth" !in host.active.hiddenSkillIds)
    }

    @Test
    fun perkEditingReachesTheApplication() {
        val host: DublEditorHost = FakeSheetHost(EditorFixtures.hornet, developmentCatalogOverride = EditorFixtures.catalog)
        val id = host.addCustomDevelopment(EditorFixtures.entry("", "Гадание"))!!
        assertEquals("Гадание", host.developmentCatalog.byId(id)!!.name)
        assertTrue(host.updateCustomDevelopment(host.developmentCatalog.byId(id)!!.copy(name = "Ворожба")))
        assertEquals("Ворожба", host.developmentCatalog.byId(id)!!.name)
        host.setDevelopmentOverride(EditorFixtures.keenHearing.copy(cost = 10))
        assertEquals(10, host.developmentCatalog.byId("feat_hear")!!.cost)
        assertEquals(30, host.canonicalDevelopmentCatalog.byId("feat_hear")!!.cost)
        host.resetDevelopmentOverride("feat_hear")
        assertEquals(30, host.developmentCatalog.byId("feat_hear")!!.cost)
        host.removeCustomDevelopment(id)
        assertNull(host.developmentCatalog.byId(id))
    }

    @Test
    fun chiSetupReachesTheApplication() {
        val host: DublEditorHost = FakeSheetHost(EditorFixtures.hornet, chiPackEnabled = true)
        host.setChiEnabled(true)
        assertTrue(host.active.chiActive)
        host.setChiBonusRanks(3)
        assertEquals(3, host.active.chiBonusRanks)
    }
}
