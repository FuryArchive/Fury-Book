package com.furybook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.DublBackground
import com.furybook.ui.theme.DublDanger
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryAccentFill
import com.furybook.ui.theme.FuryBorderStrong
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.FuryShapes

enum class FuryButtonStyle {
    /** The one filled action in a view. */
    Primary,

    /** Outlined; the default for everything else. */
    Secondary,

    /** Text only, for low-priority actions. */
    Ghost,

    /** Text only in the danger colour, for destructive actions. */
    Danger,

    /** Black fill, for use on top of the crimson roll tray. */
    OnAccent,
}

@Composable
fun FuryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FuryButtonStyle = FuryButtonStyle.Secondary,
    enabled: Boolean = true,
) {
    val fill = when (style) {
        FuryButtonStyle.Primary -> FuryAccentFill
        FuryButtonStyle.OnAccent -> DublBackground
        else -> Color.Transparent
    }
    val content = when (style) {
        FuryButtonStyle.Primary, FuryButtonStyle.OnAccent -> FuryOnAccentFill
        FuryButtonStyle.Danger -> DublDanger
        else -> DublText
    }
    val outline = if (style == FuryButtonStyle.Secondary) BorderStroke(2.dp, FuryBorderStrong) else null
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = FuryMinTouch, minHeight = FuryMinTouch)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(FuryShapes.control)
            .background(fill)
            .then(if (outline != null) Modifier.border(outline, FuryShapes.control) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (style == FuryButtonStyle.Primary || style == FuryButtonStyle.OnAccent) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A 44dp square button for a glyph or icon. [label] is what a screen reader announces. */
@Composable
fun FuryIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(FuryMinTouch)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(FuryShapes.control)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
