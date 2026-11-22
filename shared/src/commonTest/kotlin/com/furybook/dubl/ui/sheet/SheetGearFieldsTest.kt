package com.furybook.dubl.ui.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SheetGearFieldsTest {
    @Test
    fun fieldSpellingsNormalise() {
        listOf("Треб", "Требование", "Треб.").forEach { key ->
            assertEquals("2", gearStat(mapOf(key to "2"), GearStat.REQUIREMENT), key)
        }
        listOf("Специальные свойства", "Спец Свойства", "Спец. свойства", "Спецсвойства").forEach { key ->
            assertEquals("Лёгкое", gearStat(mapOf(key to "Лёгкое"), GearStat.PROPERTIES), key)
        }
        assertEquals("1 / - / -", gearStat(mapOf("Скор-ть²" to "1 / - / -"), GearStat.SPEED))
        assertEquals("10-50", gearStat(mapOf("Дальность (Баз-Макс)" to "10-50"), GearStat.RANGE))
        assertEquals(null, gearStat(mapOf("Треб." to "  "), GearStat.REQUIREMENT))
    }

    @Test
    fun kindsByFields() {
        assertEquals(GearKind.WEAPON, gearKind(mapOf("Урон" to "d3+1", "Броня" to "1")))
        assertEquals(GearKind.ARMOUR, gearKind(mapOf("Броня" to "2")))
        assertEquals(GearKind.OTHER, gearKind(mapOf("Вес" to "1,5 кг")))
    }

    @Test
    fun weaponLineOrderAndLimit() {
        val nagan = mapOf(
            "Урон" to "1d4+6", "Треб." to "1", "Дальность" to "10-50",
            "Скор-ть" to "1 / - / -", "Магазин" to "7", "Калибр" to "7.62х38",
        )
        assertEquals(
            listOf(GearStat.DAMAGE, GearStat.REQUIREMENT, GearStat.RANGE, GearStat.SPEED),
            gearLine(nagan, 4).map { it.stat },
        )
        assertEquals(listOf(GearStat.ARMOUR, GearStat.REQUIREMENT), gearLine(mapOf("Треб" to "1", "Броня" to "2"), 5).map { it.stat })
        assertEquals(emptyList(), gearLine(mapOf("Вес" to "2"), 5))
    }

    @Test
    fun descriptionThatRepeatsFieldsIsDropped() {
        val fields = mapOf("Урон" to "1d4+1", "Треб." to "1", "Дальность" to "10/50", "Вес" to "0,3", "Специальные свойства" to "Легкое, Натяжка")
        val description = "Урон: 1d4+1\nТреб.: 1\nДальность: 10/50\nВес: 0,3\nСпециальные свойства: Легкое, Натяжка"
        assertEquals("", gearDescriptionExtra(description, fields))
        assertEquals("Тихий", gearDescriptionExtra("$description\nТихий", fields))
        assertEquals("Пеньковая", gearDescriptionExtra("Пеньковая", emptyMap()))
    }

    @Test
    fun categorySpellingsMerge() {
        assertEquals(gearCategoryKey("Пистолеты/Револьверы"), gearCategoryKey("Пистолеты и револьверы"))
        assertEquals(gearCategoryKey("Пистолеты-пулеметы"), gearCategoryKey("Пистолеты-пулемёты"))
        assertNotEquals(gearCategoryKey("Автоматы"), gearCategoryKey("Пистолеты и револьверы"))
    }
}
