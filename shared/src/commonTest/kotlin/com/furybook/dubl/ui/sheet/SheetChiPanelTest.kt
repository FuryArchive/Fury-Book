package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.ChiCatalog
import com.furybook.dubl.model.ChiTechnique
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheetChiPanelTest {
    private val technique = ChiTechnique("t1", "Удар ладонью", "Школа ветра", chiCost = 2, action = "1 ОД", effect = "Отбрасывает цель.", requirements = "")
    private val monk = DublCharacter(
        id = "m1", name = "Монах", chiEnabled = true, chiBonusRanks = 3,
        attributes = AttributeId.entries.associateWith { AttributeValue(base = 4) },
    )
    private fun host(character: DublCharacter) = FakeSheetHost(
        character, chiPackEnabled = true, chiCatalogOverride = ChiCatalog("t", emptyList(), listOf(technique)),
    )

    @Test
    fun usingATechniqueSpendsItsCost() {
        val h = host(monk.copy(chiCurrent = 5))
        val panel = sheetChiPanel(h)
        assertEquals(5, panel.current)
        assertTrue(panel.techniques.single().canUse)
        assertTrue(sheetUseChiTechnique(h, "t1"))
        assertEquals(3, h.active.chiCurrent)
    }

    @Test
    fun techniqueThatCannotBeUsedSpendsNothing() {
        val low = host(monk.copy(chiCurrent = 1))
        assertFalse(sheetUseChiTechnique(low, "t1"))
        assertEquals(1, low.active.chiCurrent)
        assertEquals("Недостаточно ЦИ: нужно 2", sheetChiPanel(low).techniques.single().reason)
        val off = host(monk.copy(chiEnabled = false, chiCurrent = 0))
        assertFalse(sheetUseChiTechnique(off, "t1"))
        assertEquals("Запас ЦИ недоступен", sheetChiPanel(off).techniques.single().reason)
        assertFalse(sheetUseChiTechnique(host(monk.copy(chiCurrent = 5)), "missing"))
    }
}
