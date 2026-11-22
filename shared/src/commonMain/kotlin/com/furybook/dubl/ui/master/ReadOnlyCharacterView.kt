package com.furybook.dubl.ui.master

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.furybook.dubl.master.ReadOnlyView
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/** A player's character for the GM: the sheet's numbers and lists as cards, with no buttons, rolls or editor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadOnlyCharacterView(view: ReadOnlyView, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(view.name, color = DublText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                listOf(view.concept, "игрок ${view.nickname}", view.updatedAt.takeIf(String::isNotBlank)?.let { "обновлено ${partyTime(it)}" }.orEmpty())
                    .filter(String::isNotBlank).joinToString(" · "),
                color = DublMuted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        StatStrip(view.vitals + view.derived)
        StatStrip(view.attributes)
        if (view.conditions.isNotEmpty()) Section("Состояния") { Text(view.conditions.joinToString(" · "), color = DublText, style = MaterialTheme.typography.bodyMedium) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.md), verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            val card = Modifier.widthIn(min = 260.dp, max = 420.dp)
            Section("Умения", card) { Rows(view.skills, "Нет умений") }
            Section("Навыки", card) { Rows(view.perks.map { it to "" }, "Нет навыков") }
            if (view.schools.isNotEmpty() || view.spells.isNotEmpty()) {
                Section("Магия", card) {
                    Rows(view.schools.map { "Школа «${it.first}»" to "сила ${it.second}" }, null)
                    Rows(view.spells, "Нет изученных заклинаний")
                }
            }
            Section("Снаряжение", card) { Rows(view.gear, "Нет предметов") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatStrip(stats: List<Pair<String, String>>) {
    val p = FuryColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.xs), verticalArrangement = Arrangement.spacedBy(FurySpacing.xs)) {
        stats.forEach { (label, value) ->
            Column(
                Modifier.background(p.panel2, FuryShapes.control).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label.uppercase(), color = DublMuted, style = furyOverlineStyle())
                Text(value, color = DublText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = FuryColors.current
    Column(
        modifier.background(p.panel, FuryShapes.panel).border(1.dp, p.line2, FuryShapes.panel).padding(FurySpacing.md),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.xs),
    ) {
        Text(title.uppercase(), color = DublMuted, style = furyOverlineStyle())
        content()
    }
}

@Composable
private fun Rows(rows: List<Pair<String, String>>, empty: String?) {
    if (rows.isEmpty()) {
        empty?.let { Text(it, color = DublMuted, style = MaterialTheme.typography.bodyMedium) }
        return
    }
    rows.forEach { (name, value) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            Text(name, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.bodyMedium)
            if (value.isNotBlank()) Text(value, color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** «2026-11-21T10:05:00Z» → «21.11 10:05»; anything else as it came. */
fun partyTime(raw: String): String {
    val match = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})""").find(raw) ?: return raw
    val (_, month, day, hour, minute) = match.destructured
    return "$day.$month $hour:$minute"
}
