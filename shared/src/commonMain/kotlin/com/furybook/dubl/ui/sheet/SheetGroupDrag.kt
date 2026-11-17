package com.furybook.dubl.ui.sheet

import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules
import kotlin.random.Random

/** One line of the group manager: a group header or one of its items. */
sealed interface GroupManagerRow {
    val key: String

    data class Header(val groupId: String, val title: String, val count: Int, val collapsed: Boolean) : GroupManagerRow {
        override val key: String get() = "group:$groupId"
    }

    data class Item(val groupId: String, val itemId: String, val label: String, val depth: Int) : GroupManagerRow {
        override val key: String get() = "item:$itemId"
    }
}

/** Headers followed by their items; items without a label (no longer on the character) are skipped. */
fun groupManagerRows(groups: List<SheetGroup>, labelOf: (String) -> String?, depthOf: (String) -> Int): List<GroupManagerRow> =
    groups.flatMap { group ->
        val items = group.itemIds.mapNotNull { id -> labelOf(id)?.let { GroupManagerRow.Item(group.id, id, it, depthOf(id)) } }
        listOf(GroupManagerRow.Header(group.id, group.title, items.size, group.collapsed)) + items
    }

/**
 * Insertion slot (0..rows) for a drag at [dragY], given each row's top and height in the same coordinates:
 * above a row's middle inserts before it. Null when the drag is outside the list, so a drop there changes nothing.
 */
fun dropIndex(itemTops: List<Float>, itemHeights: List<Float>, dragY: Float): Int? {
    if (itemTops.isEmpty()) return null
    val top = itemTops.first()
    val bottom = itemTops.last() + itemHeights.last()
    if (dragY < top || dragY > bottom) return null
    itemTops.forEachIndexed { index, rowTop -> if (dragY < rowTop + itemHeights[index] / 2f) return index }
    return itemTops.size
}

/**
 * Applies a drop of [dragged] at [insertion] (a slot in [rows] as they were when the drag started).
 * A header moves its whole group; an item moves [movingIds] (itself, or a parent with its subtree).
 */
fun applyGroupDrop(
    groups: List<SheetGroup>,
    rows: List<GroupManagerRow>,
    dragged: GroupManagerRow,
    insertion: Int,
    movingIds: List<String>,
): List<SheetGroup> {
    val before = rows.take(insertion.coerceIn(0, rows.size))
    return when (dragged) {
        is GroupManagerRow.Header -> {
            val target = before.count { it is GroupManagerRow.Header && it.groupId != dragged.groupId }
            SheetGroupingRules.moveGroupToIndex(groups, dragged.groupId, target)
        }
        is GroupManagerRow.Item -> {
            val moving = movingIds.ifEmpty { listOf(dragged.itemId) }.toSet()
            val anchor = before.lastOrNull { it !is GroupManagerRow.Item || it.itemId !in moving }
            val (groupId, index) = when (anchor) {
                null -> (groups.firstOrNull()?.id ?: return groups) to 0
                is GroupManagerRow.Header -> anchor.groupId to 0
                is GroupManagerRow.Item -> {
                    val position = before.count { it is GroupManagerRow.Item && it.groupId == anchor.groupId && it.itemId !in moving }
                    anchor.groupId to position
                }
            }
            SheetGroupingRules.moveItems(groups, moving.toList().sortedBy { id -> rows.indexOfFirst { it is GroupManagerRow.Item && it.itemId == id } }, groupId, index)
        }
    }
}

/** A fresh id for a group the player creates; the same on every platform (no platform UUID). */
fun newSheetGroupId(): String = "custom:" + Random.nextLong().toULong().toString(16) + Random.nextInt().toUInt().toString(16)
