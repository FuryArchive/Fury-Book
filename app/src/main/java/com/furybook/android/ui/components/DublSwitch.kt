package com.furybook.android.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryPalette

data class DublSwitchTokens(
    val checkedThumb: Color,
    val checkedTrack: Color,
    val uncheckedThumb: Color,
    val uncheckedTrack: Color,
    val uncheckedBorder: Color,
)

/** Switch colours for a theme; [DublSwitch] reads the active one. */
fun dublSwitchTokens(p: FuryPalette): DublSwitchTokens = DublSwitchTokens(
    checkedThumb = p.text,
    checkedTrack = p.acc,
    uncheckedThumb = p.text,
    uncheckedTrack = p.panel2,
    uncheckedBorder = p.line2,
)

@Composable
fun DublSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = dublSwitchTokens(FuryColors.current)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = tokens.checkedThumb,
            checkedTrackColor = tokens.checkedTrack,
            checkedBorderColor = tokens.checkedTrack,
            uncheckedThumbColor = tokens.uncheckedThumb,
            uncheckedTrackColor = tokens.uncheckedTrack,
            uncheckedBorderColor = tokens.uncheckedBorder,
            disabledCheckedThumbColor = tokens.checkedThumb.copy(alpha = 0.58f),
            disabledCheckedTrackColor = tokens.checkedTrack.copy(alpha = 0.38f),
            disabledCheckedBorderColor = tokens.checkedTrack.copy(alpha = 0.38f),
            disabledUncheckedThumbColor = tokens.uncheckedThumb.copy(alpha = 0.58f),
            disabledUncheckedTrackColor = tokens.uncheckedTrack.copy(alpha = 0.52f),
            disabledUncheckedBorderColor = tokens.uncheckedBorder.copy(alpha = 0.52f),
        ),
    )
}
