package com.furybook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing
import kotlinx.coroutines.delay

/** One short message at the bottom of a screen, optionally with «Вернуть». */
class FuryToastState {
    var text by mutableStateOf<String?>(null)
        private set
    var undo by mutableStateOf<(() -> Unit)?>(null)
        private set
    private var serial by mutableStateOf(0)

    fun show(message: String, onUndo: (() -> Unit)? = null) {
        text = message
        undo = onUndo
        serial++
    }

    fun clear() { text = null; undo = null }

    /** The undo behind «Вернуть» is gone: keep the message, drop the button so it never does nothing. */
    fun dropUndo() { undo = null }

    internal val key: Int get() = serial
}

/** Draws [state]'s message over the bottom of the screen; a toast with «Вернуть» stays longer. */
@Composable
fun FuryToastHost(state: FuryToastState, modifier: Modifier = Modifier, bottomPadding: androidx.compose.ui.unit.Dp = 24.dp) {
    val text = state.text ?: return
    val undo = state.undo
    LaunchedEffect(state.key) {
        delay(if (undo != null) 5000L else 2600L)
        state.clear()
    }
    val p = FuryColors.current
    Box(modifier.fillMaxSize().padding(bottom = bottomPadding), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.widthIn(max = 560.dp).padding(horizontal = FurySpacing.lg).clip(RoundedCornerShape(12.dp)).background(p.text)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 14.dp, end = if (undo != null) 4.dp else 14.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, Modifier.weight(1f, fill = false), color = p.bg, style = MaterialTheme.typography.labelLarge)
            if (undo != null) {
                Box(
                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { state.clear(); undo() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Вернуть", color = if (p.isLight) p.goldSoft else p.acc2, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
