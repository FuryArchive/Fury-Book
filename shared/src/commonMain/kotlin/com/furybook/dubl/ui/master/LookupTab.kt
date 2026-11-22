package com.furybook.dubl.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.dubl.master.LookupEntry
import com.furybook.dubl.master.LookupType
import com.furybook.dubl.master.lookupEntries
import com.furybook.dubl.master.searchLookup
import com.furybook.ui.components.FuryChipGroup
import com.furybook.ui.components.FuryChoice
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing

/** Справочник: one search over the rulebook catalogs on this device, with type chips and a read-only card. */
@Composable
internal fun LookupTab(scope: MasterScope, catalogs: MasterCatalogs) {
    val state = scope.state
    val entries = remember(catalogs) { lookupEntries(catalogs.development, catalogs.magic, catalogs.conditions, catalogs.skills) }
    val results = remember(entries, state.lookupQuery, state.lookupType) { searchLookup(entries, state.lookupQuery, state.lookupType) }
    val picked = entries.firstOrNull { it.id == state.lookupPick && (state.lookupType == null || it.type == state.lookupType) }
    BoxWithConstraints {
        val split = maxWidth >= 800.dp
        MasterPage {
            MasterHeader("Справочник", "Поиск по книгам правил: навыки, заклинания, снаряжение, состояния, умения.")
            FuryTextField(state.lookupQuery, { state.lookupQuery = it }, "Что ищем? Например: горение, кинжал, щит…")
            FuryChipGroup(
                listOf(FuryChoice<LookupType?>(null, "Все")) + LookupType.entries.map { FuryChoice<LookupType?>(it, it.title) },
                state.lookupType, { state.lookupType = it }, columns = if (split) 6 else 3,
            )
            if (split) {
                Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { ResultList(results, picked) { state.lookupPick = it.id } }
                    MasterCard(Modifier.weight(1f)) {
                        if (picked != null) LookupCard(picked) else Text("Выберите запись слева.", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                ResultList(results, null) { state.lookupPick = it.id }
            }
        }
        if (!split && picked != null) {
            FuryDialogScaffold(title = picked.name, subtitle = "${picked.type.title} · ${picked.subtitle}".trimEnd(' ', '·'), onDismiss = { state.lookupPick = null }) {
                LookupCard(picked, showTitle = false)
            }
        }
    }
}

@Composable
private fun ColumnScope.ResultList(results: List<LookupEntry>, picked: LookupEntry?, onPick: (LookupEntry) -> Unit) {
    val p = FuryColors.current
    if (results.isEmpty()) Text("Ничего не найдено", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
    results.forEach { entry ->
        Column(
            Modifier.fillMaxWidth().background(if (entry == picked) p.accSoft else p.panel, FuryShapes.control)
                .clickable(role = Role.Button, onClickLabel = "Открыть ${entry.name}") { onPick(entry) }
                .padding(horizontal = FurySpacing.md, vertical = FurySpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Text(entry.name, color = DublText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(entry.type.title, color = DublMuted, style = MaterialTheme.typography.bodySmall)
            }
            val short = entry.subtitle.ifBlank { entry.text }
            if (short.isNotBlank()) Text(short, color = DublMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LookupCard(entry: LookupEntry, showTitle: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        if (showTitle) {
            Text(entry.name, color = DublText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(listOf(entry.type.title, entry.subtitle).filter(String::isNotBlank).joinToString(" · "), color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        }
        entry.facts.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                Text(label, color = DublMuted, style = MaterialTheme.typography.bodyMedium)
                Text(value, color = DublText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (entry.text.isNotBlank()) Text(entry.text, color = DublText, style = MaterialTheme.typography.bodyMedium)
    }
}
