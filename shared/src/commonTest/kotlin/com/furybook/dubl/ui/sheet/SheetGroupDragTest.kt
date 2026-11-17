package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetGroupDragTest {
    private val tops = listOf(0f, 50f, 100f)
    private val heights = listOf(50f, 50f, 50f)

    @Test
    fun dropOutsideListIsNull() {
        assertNull(dropIndex(tops, heights, -10f))
        assertNull(dropIndex(tops, heights, 151f))
    }

    @Test
    fun dropBetweenItemsPicksGap() {
        assertEquals(0, dropIndex(tops, heights, 10f))
        assertEquals(1, dropIndex(tops, heights, 30f))
        assertEquals(2, dropIndex(tops, heights, 80f))
        assertEquals(3, dropIndex(tops, heights, 140f))
    }

    @Test
    fun itemDroppedUnderAHeaderLandsFirstInThatGroup() {
        val groups = listOf(SheetGroup("g1", "A", listOf("x", "y")), SheetGroup("g2", "B", listOf("z")))
        val rows = groupManagerRows(groups, { it }, { 0 })
        // rows: [g1, x, y, g2, z]; insert after the g2 header (index 4)
        val moved = applyGroupDrop(groups, rows, rows[1], insertion = 4, movingIds = listOf("x"))
        assertEquals(listOf("y"), moved.first { it.id == "g1" }.itemIds)
        assertEquals(listOf("x", "z"), moved.first { it.id == "g2" }.itemIds)
    }

    @Test
    fun groupDroppedAtTheEndMovesLast() {
        val groups = listOf(SheetGroup("g1", "A", listOf("x")), SheetGroup("g2", "B", listOf("z")))
        val rows = groupManagerRows(groups, { it }, { 0 })
        val moved = applyGroupDrop(groups, rows, rows[0], insertion = rows.size, movingIds = emptyList())
        assertEquals(listOf("g2", "g1"), moved.map { it.id })
    }

    @Test
    fun movingParentMovesChildren() {
        val groups = listOf(SheetGroup("g1", "A", listOf("parent", "child", "other")), SheetGroup("g2", "B", emptyList()))
        val parentById = mapOf("parent" to null, "child" to "parent", "other" to null)
        val rows = groupManagerRows(groups, { it }, { if (it == "child") 1 else 0 })
        val moving = SheetGroupingRules.subtreeBlock("parent", parentById, groups[0].itemIds)
        val moved = applyGroupDrop(groups, rows, rows[1], insertion = rows.size, movingIds = moving)
        assertEquals(listOf("parent", "child"), moved.first { it.id == "g2" }.itemIds)
        assertEquals(listOf("other"), moved.first { it.id == "g1" }.itemIds)
    }

    @Test
    fun resetRestoresDefaults() {
        val host = FakeSheetHost(DublCharacter(id = "c1", name = "Тест"))
        val defaults = normalizedSkillGroups(host)
        host.setSkillGroups(SheetGroupingRules.renameGroup(defaults, defaults.first().id, "Моя группа"))
        assertEquals("Моя группа", normalizedSkillGroups(host).first().title)
        host.setSkillGroups(emptyList())
        assertEquals(defaults, normalizedSkillGroups(host))
    }

    @Test
    fun newGroupIdsAreUniqueAndPrefixed() {
        val a = newSheetGroupId()
        val b = newSheetGroupId()
        assertTrue(a.startsWith("custom:"))
        assertTrue(a != b)
    }
}
