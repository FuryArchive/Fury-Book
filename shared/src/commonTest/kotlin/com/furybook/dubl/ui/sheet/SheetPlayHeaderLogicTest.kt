package com.furybook.dubl.ui.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetPlayHeaderLogicTest {
    private fun vital(id: String, current: Int, max: Int) =
        SheetVital(id, id, current, max, SheetVitalColor.CUSTOM, null, null, restoreable = false)

    @Test
    fun pipsOnlyForSmallNonHealthResources() {
        assertFalse(resourceUsesPips(vital("health", 23, 8)))
        assertTrue(resourceUsesPips(vital("custom:a", 3, 5)))
        assertTrue(resourceUsesPips(vital("custom:b", 10, 10)))
        assertFalse(resourceUsesPips(vital("custom:c", 37, 60)))
        assertFalse(resourceUsesPips(vital("mana", 0, 0)))
    }

    private fun chips(n: Int) = (1..n).map { SheetConditionChip("k$it", "C$it", active = true, custom = false) }

    @Test
    fun conditionsCapWhenCollapsed() {
        val (shown, hidden) = visibleConditions(chips(8), expanded = false)
        assertEquals(5, shown.size)
        assertEquals(3, hidden)
        val (all, none) = visibleConditions(chips(8), expanded = true)
        assertEquals(8, all.size)
        assertEquals(0, none)
        val (few, zero) = visibleConditions(chips(3), expanded = false)
        assertEquals(3, few.size)
        assertEquals(0, zero)
    }

    @Test
    fun playStateResetsOnCharacterSwitch() {
        val state = SheetPlayState()
        state.syncCharacter("a")
        state.selectedResource = "custom:x"
        state.selectedCondition = "DAZED"
        state.conditionsExpanded = true
        state.perkQuery = "маны"
        state.noteQuery = "долг"
        state.perkFilter = SheetPerkFilter.ACTIVE
        state.expandedPerks = setOf("p")
        state.expandedNotes = setOf("n")
        state.syncCharacter("a")
        assertEquals("маны", state.perkQuery)
        state.syncCharacter("b")
        assertNull(state.selectedResource)
        assertNull(state.selectedCondition)
        assertFalse(state.conditionsExpanded)
        assertEquals("", state.perkQuery)
        assertEquals("", state.noteQuery)
        assertEquals(SheetPerkFilter.ALL, state.perkFilter)
        assertTrue(state.expandedPerks.isEmpty())
        assertTrue(state.expandedNotes.isEmpty())
        assertTrue(state.rankedOnly)
        assertTrue(state.perksBrief)
    }
}

class SheetIdentityRowTest {
    @Test
    fun derivedTilesKeepTheirWidthWhenThereIsRoom() = assertEquals(74, derivedTileWidthDp(1392 - 340))

    @Test
    fun derivedTilesShrinkBeforeTheNameDoes() {
        val width = derivedTileWidthDp(900)
        kotlin.test.assertNotNull(width)
        kotlin.test.assertTrue(width in 48..73)
    }

    @Test
    fun narrowDesktopMovesDerivedTilesToTheirOwnRow() = kotlin.test.assertNull(derivedTileWidthDp(720))
}

class SheetTouchTargetTest {
    @Test
    fun touchLayoutsGetFullSizeControls() {
        assertEquals(44, sheetControlMinHeightDp(compact = true))
        assertEquals(30, sheetControlMinHeightDp(compact = false))
    }
}
