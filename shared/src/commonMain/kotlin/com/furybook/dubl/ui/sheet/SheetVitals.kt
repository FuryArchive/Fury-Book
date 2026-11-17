package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.components.FuryBar
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryIconButton
import com.furybook.ui.theme.DublCustomResource
import com.furybook.ui.theme.DublHealth
import com.furybook.ui.theme.DublMana
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublStamina
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

internal const val SHEET_NO_VISIBLE_VITALS = "Нет видимых ресурсов"

internal fun sheetVitalColor(color: SheetVitalColor): Color = when (color) {
    SheetVitalColor.HEALTH -> DublHealth
    SheetVitalColor.STAMINA -> DublStamina
    SheetVitalColor.MANA -> DublMana
    SheetVitalColor.CUSTOM -> DublCustomResource
}

/**
 * Resource tiles with −/+ buttons. [onChange] receives the step (−1 or +1); the host clamps.
 * Phones get one compact row per vital (− · label, value and bar · +, as in the B phone mockup);
 * wider layouts put up to three cards side by side.
 */
@Composable
fun SheetVitals(
    vitals: List<SheetVital>,
    compact: Boolean,
    onChange: (SheetVital, Int) -> Unit,
    onRestore: (SheetVital) -> Unit,
    modifier: Modifier = Modifier,
) {
    val perRow = if (compact) 1 else 3
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        if (vitals.isEmpty()) {
            // Desktop HeroResources wording, for a sheet whose every resource is hidden.
            Text(SHEET_NO_VISIBLE_VITALS, color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        }
        vitals.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                row.forEach { vital ->
                    if (compact) {
                        VitalRow(vital, onChange, onRestore, Modifier.weight(1f))
                    } else {
                        VitalTile(vital, compact, onChange, onRestore, Modifier.weight(1f))
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Phone: `−`, then the label and `current / max` over the bar, then `+`. */
@Composable
private fun VitalRow(
    vital: SheetVital,
    onChange: (SheetVital, Int) -> Unit,
    onRestore: (SheetVital) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.clip(FuryShapes.panel).background(DublSurfaceRaised).padding(FurySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuryIconButton("Уменьшить ${vital.label}", { onChange(vital, -1) }) {
            Text("−", color = DublText, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vital.label.uppercase(), Modifier.weight(1f), color = DublMuted, style = furyOverlineStyle(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("${vital.current} / ${vital.maximum}", color = DublText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            FuryBar(vital.current, vital.maximum, sheetVitalColor(vital.color))
            if (vital.restoreable) {
                FuryButton("Восстановить", { onRestore(vital) }, Modifier.align(Alignment.End), style = FuryButtonStyle.Ghost)
            }
        }
        FuryIconButton("Увеличить ${vital.label}", { onChange(vital, 1) }) {
            Text("+", color = DublText, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun VitalTile(
    vital: SheetVital,
    compact: Boolean,
    onChange: (SheetVital, Int) -> Unit,
    onRestore: (SheetVital) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.clip(FuryShapes.panel).background(DublSurfaceRaised).padding(if (compact) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                vital.label.uppercase(), Modifier.weight(1f), color = DublMuted, style = furyOverlineStyle(),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${vital.current} / ${vital.maximum}",
                color = DublText,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                maxLines = 1,
            )
        }
        FuryBar(vital.current, vital.maximum, sheetVitalColor(vital.color))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FuryIconButton("Уменьшить ${vital.label}", { onChange(vital, -1) }) {
                Text("−", color = DublText, style = MaterialTheme.typography.titleLarge)
            }
            FuryIconButton("Увеличить ${vital.label}", { onChange(vital, 1) }) {
                Text("+", color = DublText, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.weight(1f))
            if (vital.restoreable) {
                FuryButton("Восстановить", { onRestore(vital) }, style = FuryButtonStyle.Ghost)
            }
        }
    }
}
