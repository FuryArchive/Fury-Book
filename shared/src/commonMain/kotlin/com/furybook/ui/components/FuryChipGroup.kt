package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryAccentFill
import com.furybook.ui.theme.FuryBorderStrong
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing

data class FuryChoice<T>(val value: T, val label: String, val enabled: Boolean = true)

/** Pick exactly one of a short list. Replaces stacked radio buttons and checkbox lists. */
@Composable
fun <T> FuryChipGroup(
    choices: List<FuryChoice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
) {
    FuryChipGrid(choices, columns, modifier) { choice, chipModifier ->
        val isSelected = choice.value == selected
        FuryChip(
            choice.label, isSelected, choice.enabled,
            chipModifier.selectable(isSelected, choice.enabled, Role.RadioButton) { onSelect(choice.value) },
        )
    }
}

/** Pick any number of a short list. */
@Composable
fun <T> FuryMultiChipGroup(
    choices: List<FuryChoice<T>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
) {
    FuryChipGrid(choices, columns, modifier) { choice, chipModifier ->
        val isSelected = choice.value in selected
        FuryChip(
            choice.label, isSelected, choice.enabled,
            chipModifier.toggleable(isSelected, choice.enabled, Role.Checkbox) { onToggle(choice.value) },
        )
    }
}

@Composable
private fun <T> FuryChipGrid(
    choices: List<FuryChoice<T>>,
    columns: Int,
    modifier: Modifier,
    chip: @Composable (FuryChoice<T>, Modifier) -> Unit,
) {
    val perRow = columns.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        choices.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                row.forEach { choice -> chip(choice, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FuryChip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = FuryMinTouch)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(FuryShapes.control)
            .background(if (selected) FuryAccentFill else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) FuryAccentFill else FuryBorderStrong, FuryShapes.control)
            .then(modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) FuryOnAccentFill else DublText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
