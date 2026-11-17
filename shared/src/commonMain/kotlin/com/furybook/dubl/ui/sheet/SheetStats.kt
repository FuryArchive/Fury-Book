package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.furybook.ui.components.FuryTile
import com.furybook.ui.theme.FurySpacing

/**
 * Attribute or metric tiles in [columns] columns. A tile with [SheetStat.info] opens the formula popup
 * through [onOpenInfo] (the popup offers the roll); otherwise a tile with a [SheetStat.roll] rolls.
 */
@Composable
fun SheetStatGrid(
    stats: List<SheetStat>,
    columns: Int,
    compact: Boolean,
    onRoll: (SheetRollSubject) -> Unit,
    modifier: Modifier = Modifier,
    onOpenInfo: ((SheetStat) -> Unit)? = null,
) {
    val perRow = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        stats.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                row.forEach { stat ->
                    val roll = stat.roll
                    val open: (() -> Unit)? = when {
                        stat.info != null && onOpenInfo != null -> { { onOpenInfo(stat) } }
                        roll != null -> { { onRoll(roll) } }
                        else -> null
                    }
                    FuryTile(
                        label = stat.label,
                        value = stat.valueText,
                        modifier = Modifier.weight(1f),
                        onClick = open,
                        compact = compact,
                    )
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
