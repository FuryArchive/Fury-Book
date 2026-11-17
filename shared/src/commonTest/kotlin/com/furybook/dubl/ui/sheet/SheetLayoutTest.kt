package com.furybook.dubl.ui.sheet

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetLayoutTest {
    @Test
    fun phoneBelow600() {
        assertEquals(SheetLayout.PHONE, sheetLayoutFor(390))
        assertEquals(SheetLayout.PHONE, sheetLayoutFor(599))
    }

    @Test
    fun wideFrom600Below1100() {
        assertEquals(SheetLayout.WIDE, sheetLayoutFor(600))
        assertEquals(SheetLayout.WIDE, sheetLayoutFor(1099))
    }

    @Test
    fun trayFrom1100() {
        assertEquals(SheetLayout.WIDE_WITH_TRAY, sheetLayoutFor(1100))
    }

    @Test
    fun phoneMetricLabelsAreAbbreviated() {
        assertEquals(
            listOf("Защ", "Рефл", "Иниц", "Стойк", "Бег", "Разм"),
            listOf("Защита", "Рефлексы", "Инициатива", "Стойкость", "Бег", "Размер").map(::sheetPhoneMetricLabel),
        )
        assertEquals("Новое", sheetPhoneMetricLabel("Новое"))
    }
}
