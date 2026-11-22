package com.furybook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.FuryThemeId
import com.furybook.ui.theme.LocalFuryThemeChoice
import com.furybook.ui.theme.furyPalette

/** One swatch per theme (that theme's panel and accent); the selected one is ringed in the text colour. */
@Composable
fun FuryThemePicker(selected: FuryThemeId, onSelect: (FuryThemeId) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        FuryThemeId.entries.forEach { id ->
            val on = id == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(FuryMinTouch)
                        .semantics { contentDescription = "Тема: ${id.title}"; this.selected = on }
                        .clickable(role = Role.RadioButton) { onSelect(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    val palette = furyPalette(id)
                    val ring = if (on) FuryColors.current.text else FuryColors.current.line2
                    Canvas(Modifier.size(32.dp).border(2.dp, ring, CircleShape).padding(4.dp)) {
                        drawArc(palette.panel, 135f, 180f, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
                        drawArc(palette.acc, -45f, 180f, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
                    }
                }
                Text(id.title, color = if (on) FuryColors.current.text else FuryColors.current.muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** «Тема» button for the shell's account slot; opens the picker in a small dialog. Needs [FuryThemedApp] above it. */
@Composable
fun FuryThemeButton(modifier: Modifier = Modifier) {
    val choice = LocalFuryThemeChoice.current ?: return
    var open by remember { mutableStateOf(false) }
    FuryIconButton("Тема оформления", { open = true }, modifier) {
        Text("◐", color = FuryColors.current.text, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
    if (open) {
        FuryDialogScaffold(title = "Тема оформления", onDismiss = { open = false }, maxWidth = 420.dp) {
            FuryThemePicker(choice.selected, choice.select)
        }
    }
}
