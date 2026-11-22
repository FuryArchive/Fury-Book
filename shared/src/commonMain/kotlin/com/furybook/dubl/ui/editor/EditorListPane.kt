package com.furybook.dubl.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.remember
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing

private val PANE_WIDTH = 360.dp

/**
 * The Навыки step's layout for Магия and Снаряжение: the list on the left and [pane] beside it on wide screens;
 * on narrower ones the pane covers the list and gets «← К списку» ([onBack]).
 */
@Composable
internal fun EditorListPane(
    layout: SheetLayout,
    header: @Composable () -> Unit,
    list: LazyListScope.() -> Unit,
    pane: (@Composable () -> Unit)?,
    emptyPane: String,
    onBack: () -> Unit,
    /** Which pane is open: each keeps its own scroll position (a catalog scrolled far down doesn't push the detail out of view). */
    paneKey: Any? = null,
) {
    val padding = editorContentPadding(layout)
    val scrolls = remember { mutableMapOf<Any?, ScrollState>() }
    val paneScroll = scrolls.getOrPut(paneKey) { ScrollState(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val split = layout == SheetLayout.WIDE_WITH_TRAY && editorPerksSplit(maxWidth.value.toInt())
        when {
            split -> Row(Modifier.fillMaxSize().padding(start = padding, end = padding, top = padding), horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                    item { header() }
                    list()
                    item { Box(Modifier.heightIn(min = padding)) }
                }
                Column(Modifier.width(PANE_WIDTH).fillMaxHeight().verticalScroll(paneScroll).padding(bottom = padding)) {
                    if (pane != null) pane() else EditorCard(Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
                        Text(emptyPane, color = FuryColors.current.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            pane != null -> Column(Modifier.fillMaxSize().verticalScroll(paneScroll).padding(padding), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Text(
                    "← К списку", Modifier.clickable(role = Role.Button, onClick = onBack).padding(vertical = 10.dp),
                    color = FuryColors.current.goldText, style = MaterialTheme.typography.labelLarge,
                )
                pane()
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(padding), verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                item { header() }
                list()
            }
        }
    }
}
