package com.furybook.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublBorder
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurface
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryAccentFill
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryOnAccentFill
import com.furybook.ui.theme.FuryShapes

data class FuryTab<T>(val value: T, val label: String)

/** Section tabs for wide layouts: a row of pills, the current one filled. */
/** Selected-tab colour change duration (the old desktop rail animated it too). */
private const val FURY_TAB_MS = 160

@Composable
fun <T> FuryTopTabs(
    tabs: List<FuryTab<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { tab ->
            val active = tab.value == selected
            val fill by animateColorAsState(if (active) FuryAccentFill else Color.Transparent, tween(FURY_TAB_MS))
            val textColor by animateColorAsState(if (active) FuryOnAccentFill else DublMuted, tween(FURY_TAB_MS))
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = FuryMinTouch)
                    .clip(FuryShapes.control)
                    .background(fill)
                    .selectable(active, role = Role.Tab) { onSelect(tab.value) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Section tabs for phones: equal-width, 60dp tall, a crimson marker above the current one. */
@Composable
fun <T> FuryBottomTabs(
    tabs: List<FuryTab<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().background(DublSurface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DublBorder))
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val active = tab.value == selected
                val marker by animateColorAsState(if (active) DublAccent else Color.Transparent, tween(FURY_TAB_MS))
                val textColor by animateColorAsState(if (active) DublText else DublMuted, tween(FURY_TAB_MS))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .selectable(active, role = Role.Tab) { onSelect(tab.value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp)
                            .background(marker),
                    )
                    Text(
                        text = tab.label,
                        color = textColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
