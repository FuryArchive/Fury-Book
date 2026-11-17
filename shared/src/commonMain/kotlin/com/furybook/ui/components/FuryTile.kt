package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.furyOverlineStyle

/**
 * A stat tile: small label on top, big number underneath. Pass [onClick] to make it rollable.
 * [compact] is the phone size: tighter padding and a mixed-case 11sp label instead of the overline.
 */
@Composable
fun FuryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) 68.dp else 112.dp)
            .clip(FuryShapes.panel)
            .background(DublSurfaceRaised)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (compact) Modifier.padding(horizontal = 4.dp, vertical = 8.dp) else Modifier.padding(12.dp)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Phone tiles are narrow: the label is mixed-case 11sp body text (B phone mockup), not the overline.
        Text(
            text = if (compact) label else label.uppercase(),
            color = DublMuted,
            style = if (compact) MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp) else furyOverlineStyle(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = DublText,
            style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
