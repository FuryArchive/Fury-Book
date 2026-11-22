package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.CharacterGear
import com.furybook.dubl.model.GearCatalogEntry
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.ui.editor.EditorFixtures.hornet
import com.furybook.dubl.ui.sheet.FakeSheetHost
import com.furybook.dubl.ui.sheet.GearKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorGearModelsTest {
    private val nagan = GearItem(uid = "n", name = "Револьвер Нагана", load = 1.0, fields = mapOf("Урон" to "1d4+6", "Треб." to "1", "Калибр" to "7.62х38"))
    private val rope = GearItem(uid = "r", name = "Верёвка", quantity = 2, load = 1.0, description = "Пеньковая")
    private val bedroll = GearItem(uid = "b", name = "Спальник", load = 1.5, carried = false)
    private val character = hornet.copy(gear = CharacterGear(items = listOf(rope, nagan, bedroll)))

    @Test
    fun countAndBurden() {
        val header = editorGearHeader(character)
        assertEquals("4 шт. · 3 позиции", header.count)
        assertEquals(MagicEquipmentRules.burden(character).title, header.burden)
        assertEquals("3", header.load)
        assertTrue(header.automatic)
    }

    @Test
    fun inventorySearchesFields() {
        val all = editorInventory(character, "")
        assertEquals(listOf(GearKind.WEAPON, GearKind.OTHER), all.map { it.first })
        assertEquals("Урон 1d4+6 · Треб 1", all[0].second.single().line)
        assertEquals(listOf("Револьвер Нагана"), editorInventory(character, "7.62").flatMap { it.second }.map { it.name })
        assertEquals(listOf("Верёвка"), editorInventory(character, "пенько").flatMap { it.second }.map { it.name })
    }

    @Test
    fun catalogCategoriesMerge() {
        val entries = listOf(
            GearCatalogEntry("a", "Наган", "Пистолеты/Револьверы", "Предметы", mapOf("Урон" to "1d4+6"), ""),
            GearCatalogEntry("b", "ТТ", "Пистолеты и револьверы", "Предметы", mapOf("Урон" to "1d6+4"), ""),
            GearCatalogEntry("c", "АК", "Автоматы", "Предметы", mapOf("Урон" to "2d4+6"), "Надёжный"),
        )
        assertEquals(2, editorGearCategories(entries).size)
        val host = FakeSheetHost(hornet.copy(gear = CharacterGear(items = listOf(nagan.copy(catalogId = "a")))), magicEquipmentCatalogOverride = MagicEquipmentCatalog("t", emptyList(), entries))
        val pistols = editorGearCategories(entries).first().first
        assertEquals(listOf("Наган", "ТТ"), editorGearCatalog(host, "", pistols).map { it.name })
        assertEquals(1, editorGearCatalog(host, "", pistols).first().owned)
        assertEquals(listOf("АК"), editorGearCatalog(host, "надёжн", null).map { it.name })
    }

    @Test
    fun gearFormRoundTripsFields() {
        val form = editorGearForm(nagan)
        assertEquals("Урон: 1d4+6\nТреб.: 1\nКалибр: 7.62х38", form.fields)
        val saved = form.copy(fields = "Урон: 1d6\nТреб.: 2\nбез двоеточия", load = "1,5").toItem(nagan)
        assertEquals(mapOf("Урон" to "1d6", "Треб." to "2"), saved.fields)
        assertEquals(1.5, saved.load)
        assertEquals("n", saved.uid)
        assertTrue(editorGearForm(null).toItem(null).custom)
    }

    @Test
    fun gearFormRejectsBadInput() {
        val blank = editorGearForm(null)
        assertTrue("Нужно название" in blank.errors())
        assertTrue("Количество — целое число от 1" in blank.copy(name = "x", quantity = "0").errors())
        assertTrue("Нагрузка — число" in blank.copy(name = "x", load = "a").errors())
        assertEquals(emptyList(), blank.copy(name = "x").errors())
        assertEquals(2.5, editorManualLoad("2,5"))
        assertNull(editorManualLoad("abc"))
    }
}
