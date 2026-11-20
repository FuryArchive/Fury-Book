package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.CharacterGear
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.MagicEquipmentRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetGearTest {
    private fun carrying(vararg items: GearItem) = DublCharacter(id = "g", name = "Вика", gear = CharacterGear(items = items.toList()))

    private val dagger = GearItem(uid = "d", name = "Кинжал", load = 0.5, fields = mapOf("Урон" to "d3+1", "Треб." to "0"))
    private val armour = GearItem(uid = "a", name = "Кожаная броня", load = 2.0, fields = mapOf("Броня" to "2", "Треб." to "1"))
    private val rope = GearItem(uid = "r", name = "Верёвка", quantity = 2, load = 1.0)
    private val bedroll = GearItem(uid = "b", name = "Спальник", load = 1.5, carried = false)

    @Test
    fun onlyCarriedItemsGroupedWeaponsArmourOther() {
        val view = sheetGear(carrying(rope, bedroll, armour, dagger))
        assertEquals(listOf(GearKind.WEAPON, GearKind.ARMOUR, GearKind.OTHER), view.groups.map { it.first })
        assertEquals(listOf("Кинжал"), view.groups[0].second.map { it.name })
        assertEquals(listOf("Верёвка"), view.groups[2].second.map { it.name })
        assertEquals(2, view.groups[2].second.single().quantity)
        assertEquals(listOf(GearStat.DAMAGE, GearStat.REQUIREMENT), view.groups[0].second.single().line.map { it.stat })
        assertEquals("4.5", view.load)
    }

    @Test
    fun loadAndBurdenComeFromRules() {
        val heavy = carrying(GearItem(uid = "h", name = "Сейф", load = 30.0))
        val view = sheetGear(heavy)
        assertEquals(MagicEquipmentRules.burden(heavy).title, view.burden)
        assertTrue(view.penalty < 0)
        assertEquals(MagicEquipmentRules.equipmentCapacity(heavy), view.capacity)
        assertEquals("−2 к атакам и защите", sheetGearPenaltyText(-2))
        assertNull(sheetGearPenaltyText(0))
    }

    @Test
    fun detailDropsRepeatedDescription() {
        val bow = GearItem(
            uid = "bow", name = "Короткий лук", category = "Снаряжение",
            fields = mapOf("Урон" to "1d4+1", "Треб." to "1"),
            description = "Урон: 1d4+1\nТреб.: 1",
        )
        val detail = sheetGearDetail(carrying(bow), "bow")!!
        assertEquals("", detail.extraDescription)
        assertEquals(listOf("Урон" to "1d4+1", "Треб." to "1"), detail.fields)
        assertNull(sheetGearDetail(carrying(bow), "gone"))
    }

    @Test
    fun quantityNeverBelowOne() {
        val host = FakeSheetHost(carrying(GearItem(uid = "r", name = "Верёвка")))
        sheetChangeGearQuantity(host, "r", -1)
        assertEquals(1, host.active.gear.items.single().quantity)
        sheetChangeGearQuantity(host, "r", 2)
        assertEquals(3, host.active.gear.items.single().quantity)
    }
}
