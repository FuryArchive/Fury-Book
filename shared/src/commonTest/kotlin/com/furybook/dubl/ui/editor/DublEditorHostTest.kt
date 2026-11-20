package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentCatalog
import kotlin.test.assertFalse
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

    @Test
    fun magicReachesTheApplication() {
        val host: DublEditorHost = FakeSheetHost(character)
        assertTrue(host.addMagicSchool("Разрушение", 2, ""))
        host.setMagicSchoolPower("Разрушение", 4)
        assertEquals(4, host.active.magic.schools[0].rank)
        host.setMagicManaRank(2)
        assertEquals(2, host.active.magic.manaRank)
        val uid = host.addCustomSpell(KnownSpell(uid = "", name = "Искра", custom = true))
        host.setSpellLearned(uid, false)
        assertFalse(host.active.magic.spells.single { it.uid == uid }.learned)
        host.updateSpell(host.active.magic.spells.single { it.uid == uid }.copy(name = "Искра+"))
        assertEquals("Искра+", host.active.magic.spells.single { it.uid == uid }.name)
        assertTrue(host.updateMagicSchool(0, "Разрушение", 3, "заметка"))
        assertEquals("заметка", host.active.magic.schools[0].note)
        host.removeSpell(uid)
        assertTrue(host.active.magic.spells.none { it.uid == uid })
        host.removeMagicSchool(0)
        assertTrue(host.active.magic.schools.isEmpty())
    }

    @Test
    fun gearReachesTheApplication() {
        val host: DublEditorHost = FakeSheetHost(character)
        val uid = host.addCustomGear(GearItem(uid = "", name = "Верёвка", load = 1.0))
        host.setGearItemQuantity(uid, 3)
        host.setGearItemCarried(uid, false)
        val item = host.active.gear.items.single { it.uid == uid }
        assertEquals(3, item.quantity)
        assertFalse(item.carried)
        host.setGearLoadAutomatic(false)
        host.setGearManualLoad(2.5)
        assertFalse(host.active.gear.loadAutomatic)
        assertEquals(2.5, host.active.gear.loadManual)
        host.removeGearItem(uid)
        assertTrue(host.active.gear.items.isEmpty())
    }

    @Test
    fun catalogGearLoadsAreRepaired() {
        val entry = GearCatalogEntry("gear_x", "Лук", "Снаряжение", "Предметы", mapOf("Треб." to "3", "Вес" to "9"), "")
        val host: DublEditorHost = FakeSheetHost(character, magicEquipmentCatalogOverride = MagicEquipmentCatalog("t", emptyList(), listOf(entry)))
        val uid = host.addCatalogGear(host.magicEquipmentCatalog.gear.single())
        host.updateGearItem(host.active.gear.items.single { it.uid == uid }.copy(load = 9.0))
        host.syncCatalogGearLoads(host.magicEquipmentCatalog.gear)
        assertEquals(3.0, host.active.gear.items.single { it.uid == uid }.load)
    }
}
