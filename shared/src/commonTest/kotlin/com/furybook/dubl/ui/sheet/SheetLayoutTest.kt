package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
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
    fun playSheetColumns() {
        assertEquals(1, sheetPlayColumns(SheetLayout.PHONE))
        assertEquals(2, sheetPlayColumns(SheetLayout.WIDE))
        assertEquals(2, sheetPlayColumns(SheetLayout.WIDE_WITH_TRAY))
    }

    @Test
    fun playDerivedRowIsSixWideAndThreeOnPhones() {
        assertEquals(3, sheetPlayDerivedColumns(SheetLayout.PHONE))
        assertEquals(6, sheetPlayDerivedColumns(SheetLayout.WIDE))
    }

    @Test
    fun rightTabsHideSpellsWithoutMagic() {
        assertEquals(listOf(SheetRightTab.PERKS, SheetRightTab.GEAR), sheetRightTabs(showSpells = false))
        assertEquals(listOf(SheetRightTab.PERKS, SheetRightTab.SPELLS, SheetRightTab.GEAR), sheetRightTabs(showSpells = true))
        assertEquals(SheetRightTab.PERKS, sheetRightTabOrDefault(SheetRightTab.SPELLS, showSpells = false))
        assertEquals(SheetRightTab.GEAR, sheetRightTabOrDefault(SheetRightTab.GEAR, showSpells = false))
    }
}
