package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.DublBackground
import com.furybook.ui.theme.FuryAccentFill
import com.furybook.ui.theme.FuryAccentFillPressed
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.furyOverlineStyle
import com.furybook.ui.theme.FurySpacing

/** One addend of a roll, for display only: "+ 8 Ловкость". */
data class FuryRollTerm(val label: String, val value: Int)

/**
 * A finished roll as the tray shows it. The tray never rolls dice or applies rules; the caller
 * builds this from the shared roll engine's result. [keptDice] are the indices of [dice] that
 * count towards the total (advantage/hindrance drop the rest); [specialResult] is a critical
 * success/failure line shown under the total.
 */
data class FuryRollView(
    val title: String,
    val dice: List<Int>,
    val terms: List<FuryRollTerm>,
    val total: Int,
    val outcome: String? = null,
    val keptDice: Set<Int> = dice.indices.toSet(),
    val specialResult: String? = null,
)

data class FuryRollHistoryEntry(val title: String, val total: Int)

private const val HISTORY_ROWS = 5
private const val DROPPED_DIE_ALPHA = 0.4f

/** "9 + 8 Ловкость − 2 Поправка": the kept dice sum, then every non-zero term with its sign. */
fun furyRollBreakdown(view: FuryRollView): String = buildString {
    append(view.dice.filterIndexed { index, _ -> index in view.keptDice }.sum())
    view.terms.filter { it.value != 0 }.forEach { term ->
        append(if (term.value < 0) " − " else " + ")
        append(kotlin.math.abs(term.value))
        append(' ')
        append(term.label)
    }
}

/**
 * The docked roll panel (wide layouts) or the body of the roll sheet (phones).
 * [controls] is where the caller puts advantage, hindrance and modifier buttons.
 * With [emptyRollLabel] set, the controls and a roll button with that label also show before the
 * first roll. With [blockedReason] set, the roll button is disabled and that line shows under it.
 */
@Composable
fun FuryRollTray(
    view: FuryRollView?,
    history: List<FuryRollHistoryEntry>,
    onRollAgain: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = "Нажмите на характеристику или умение, чтобы бросить",
    emptyRollLabel: String? = null,
    blockedReason: String? = null,
    controls: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.clip(FuryShapes.panel).background(FuryAccentFill).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (view == null) {
            Text("БРОСОК", color = FuryOnAccentFill, style = furyOverlineStyle())
            Text(emptyText, color = FuryOnAccentFill, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                text = "БРОСОК · ${view.title}".uppercase(),
                color = FuryOnAccentFill,
                style = furyOverlineStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(view.total.toString(), color = FuryOnAccentFill, style = MaterialTheme.typography.displayLarge, maxLines = 1)
            if (!view.specialResult.isNullOrBlank()) {
                Text(view.specialResult, color = FuryOnAccentFill, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                view.dice.forEachIndexed { index, face ->
                    Box(
                        Modifier.size(44.dp).alpha(if (index in view.keptDice) 1f else DROPPED_DIE_ALPHA)
                            .clip(FuryShapes.panel).background(FuryOnAccentFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(face.toString(), color = DublBackground, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Text(furyRollBreakdown(view), color = FuryOnAccentFill, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            if (!view.outcome.isNullOrBlank()) {
                Text(
                    text = view.outcome,
                    modifier = Modifier.fillMaxWidth().clip(FuryShapes.control).background(FuryAccentFillPressed)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    color = FuryOnAccentFill,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (view != null || emptyRollLabel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), content = controls)
            val label = if (view == null) emptyRollLabel.orEmpty() else "Бросить снова"
            FuryButton(label, onRollAgain, Modifier.fillMaxWidth(), style = FuryButtonStyle.OnAccent, enabled = blockedReason == null)
            if (blockedReason != null) {
                Text(blockedReason, color = FuryOnAccentFill, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (history.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(FuryOnAccentFill))
            history.take(HISTORY_ROWS).forEach { entry ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        entry.title, Modifier.weight(1f), color = FuryOnAccentFill,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(entry.total.toString(), color = FuryOnAccentFill, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
