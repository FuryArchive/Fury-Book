package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.CharacterMagic
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.MagicSchool
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.sheet.FakeSheetHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorMagicModelsTest {
    private val shield = KnownSpell(uid = "s1", name = "Щит", school = "Ограждение", cost = 1, description = "Броня 3")
    private val spark = KnownSpell(uid = "s2", name = "Искра", school = "Разрушение", cost = 4)
    private val vision = KnownSpell(uid = "s3", name = "Видение", school = "Прорицание", cost = 1, learned = false)
    private val mage = hornet.copy(
        creationComplete = false,
        magic = CharacterMagic(
            manaRank = 2,
            schools = listOf(MagicSchool("Разрушение", 3), MagicSchool("Ограждение", 1)),
            spells = listOf(shield, spark, vision),
        ),
    )

    private fun entry(id: String, name: String, school: String, cost: Int) =
        SpellCatalogEntry(id, name, school, cost, cost.toString(), "1 ОД", "", "", "", "", "Текст", "", false, "")

    @Test
    fun rankLockedAfterCreation() {
        assertTrue(editorMagicHeader(FakeSheetHost(mage.copy(creationComplete = true))).rankLocked)
        assertFalse(editorMagicHeader(FakeSheetHost(mage)).rankLocked)
        assertEquals(200, editorMagicHeader(FakeSheetHost(mage)).rankXp)
    }

    @Test
    fun headerExplainsZeroMana() {
        val none = mage.copy(magic = mage.magic.copy(manaRank = 0))
        assertNotNull(editorMagicHeader(FakeSheetHost(none)).zeroReason)
        val header = editorMagicHeader(FakeSheetHost(mage))
        assertNull(header.zeroReason)
        assertEquals(MagicEquipmentRules.manaMaximum(mage), header.manaMaximum)
        assertEquals(100, header.schoolXp)
        assertEquals(20 + 10, header.spellXp)
    }

    @Test
    fun addableSchoolsSkipOwned() {
        val addable = editorAddableSchools(mage)
        assertFalse("Разрушение" in addable)
        assertEquals(11, addable.size)
        assertEquals(listOf("Ограждение", "Разрушение"), editorSchoolRows(mage).map { it.name })
        assertEquals(75, editorSchoolRows(mage)[1].xp)
    }

    @Test
    fun spellbookFiltersAndGroups() {
        val learned = editorSpellGroups(mage, EditorSpellFilter.LEARNED, null, "")
        assertTrue(learned.flatMap { it.rows }.none { it.name == "Видение" })
        assertEquals(listOf("Ограждение"), editorSpellGroups(mage, EditorSpellFilter.ALL, "Ограждение", "").map { it.school })
        assertEquals(listOf("Щит"), editorSpellGroups(mage, EditorSpellFilter.ALL, null, "щит").flatMap { it.rows }.map { it.name })
        assertEquals(listOf("Щит"), editorSpellGroups(mage, EditorSpellFilter.ALL, null, "броня").flatMap { it.rows }.map { it.name })
        val spark = editorSpellGroups(mage, EditorSpellFilter.ALL, "Разрушение", "").single().rows.single()
        assertEquals(20, spark.learnXp)
        assertEquals("нужна сила школы 4", spark.weakText)
    }

    @Test
    fun catalogMarksKnownAndUsable() {
        val catalog = MagicEquipmentCatalog("t", listOf(entry("c1", "Щит", "Ограждение", 1), entry("c2", "Неудача", "Разрушение", 6)), emptyList())
        val host = FakeSheetHost(mage.copy(magic = mage.magic.copy(spells = listOf(shield.copy(catalogId = "c1")))), magicEquipmentCatalogOverride = catalog)
        val rows = editorSpellCatalog(host, "", null, usableOnly = false)
        assertTrue(rows.single { it.id == "c1" }.known)
        assertFalse(rows.single { it.id == "c2" }.usable)
        assertEquals(listOf("c1"), editorSpellCatalog(host, "", null, usableOnly = true).map { it.id })
        assertEquals(listOf("c2"), editorSpellCatalog(host, "неуд", null, usableOnly = false).map { it.id })
        assertEquals(listOf("c2"), editorSpellCatalog(host, "", "Разрушение", usableOnly = false).map { it.id })
    }

    @Test
    fun spellDetailFromCatalogAndBook() {
        val detail = editorSpellDetail(mage, entry("c2", "Неудача", "Разрушение", 6))
        assertEquals("Неудача", detail.title)
        assertEquals("нужна сила школы 6", detail.weakText)
        assertEquals(listOf("Время" to "1 ОД"), detail.facts)
        assertNull(editorSpellDetail(mage, shield).weakText)
    }

    @Test
    fun schoolDetailOffersRename() {
        val choices = editorSchoolChoices(mage, index = 1)
        assertEquals("Ограждение", choices.first { it == "Ограждение" })
        assertFalse("Разрушение" in choices)
        assertEquals(12, choices.size)
        assertEquals(editorAddableSchools(mage), editorSchoolChoices(mage, index = null))
    }

    @Test
    fun spellXpComesFromTheRule() {
        val incomplete = spark.copy(uid = "s9", incomplete = true)
        assertEquals(0, editorSpellLearnXp(mage, incomplete))
        assertEquals(20, editorSpellLearnXp(mage, spark))
        assertEquals(5, editorSpellLearnXp(mage, spark.copy(xpOverride = 5)))
        assertEquals(null, editorSpellLearnXp(mage, spark.copy(cost = 25)))
        val row = editorSpellGroups(mage.copy(magic = mage.magic.copy(spells = listOf(incomplete))), EditorSpellFilter.ALL, null, "").single().rows.single()
        assertEquals(0, row.learnXp)
    }

    @Test
    fun headerUsesTheSheetsManaMaximum() {
        val overridden = mage.copy(magic = mage.magic.copy(manaRank = 0), manaMaximumOverride = 15)
        val header = editorMagicHeader(FakeSheetHost(overridden))
        assertEquals(15, header.manaMaximum)
        assertNull(header.zeroReason)
    }
}
