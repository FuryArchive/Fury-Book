package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.dubl.ui.editor.DublEditorHost
import com.furybook.dubl.ui.editor.EditorSquareButton
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.furyOverlineStyle

/** Заклинания: learned spells with their mana and check; a tap (or «Сотворить») opens the cast window. */
@Composable
internal fun SheetPlaySpells(
    view: SheetSpellsView,
    onCast: (String) -> Unit,
    onAdd: () -> Unit,
    compact: Boolean,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = FuryColors.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(p.panel).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Заклинания", color = p.text, style = MaterialTheme.typography.titleLarge)
                Text("мана ${view.current}/${view.maximum} · восстановление ${view.recovery}/раунд", color = p.muted, style = MaterialTheme.typography.bodySmall)
            }
            HeaderToggle("+ Заклинание", onAdd, compact)
        }
        if (view.rows.isEmpty()) {
            Text("Заклинаний нет", Modifier.fillMaxWidth().padding(16.dp), color = p.dim, style = MaterialTheme.typography.bodyMedium)
        } else if (scrollable) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(view.rows, key = { it.uid }) { row -> SpellRow(row, compact) { onCast(row.uid) } }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { view.rows.forEach { row -> SpellRow(row, compact) { onCast(row.uid) } } }
        }
    }
}

@Composable
private fun SpellRow(row: SheetSpellRow, compact: Boolean, onCast: () -> Unit) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = sheetControlMinHeightDp(compact).dp).clip(shape).background(p.panel2)
            .clickable(onClickLabel = "Сотворить: ${row.name}", role = Role.Button, onClick = onCast)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.name, Modifier.weight(1f, fill = false), color = p.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.manaLabel, color = p.manaText, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            }
            if (row.meta.isNotBlank()) Text(row.meta, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            row.weakText?.let { Text(it, color = p.goldText, style = MaterialTheme.typography.labelSmall) }
        }
        SheetOutlineAction("Сотворить", compact, onCast)
    }
}

/** A small outlined action inside a play row («Сотворить»). */
@Composable
internal fun SheetOutlineAction(text: String, compact: Boolean, onClick: () -> Unit) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.heightIn(min = sheetControlMinHeightDp(compact).dp).clip(shape).border(1.dp, p.line2, shape).background(p.panel)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = p.text, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

/** A «Ключ  значение» list for spell facts and gear fields. */
@Composable
internal fun SheetFacts(facts: List<Pair<String, String>>) {
    val p = FuryColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        facts.forEach { (key, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(key, Modifier.width(120.dp), color = p.muted, style = MaterialTheme.typography.bodySmall)
                Text(value, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * The cast window: the spell's full text, then «Мана» (prefilled from the spell; −/+ or typed) and
 * «Сотворить · N маны», which spends N. A too-weak school only warns, as on the old screens.
 */
@Composable
internal fun SheetCastDialog(host: DublEditorHost, uid: String, onDismiss: () -> Unit) {
    val spell = host.active.magic.spells.firstOrNull { it.uid == uid }
    if (spell == null) {
        onDismiss()
        return
    }
    val p = FuryColors.current
    var amountText by remember(uid) { mutableStateOf(sheetSpellDefaultMana(spell).toString()) }
    val amount = sheetCastAmount(amountText)
    val current = host.active.manaCurrent
    val blocked = sheetCastBlockedReason(current, amount)
    FuryDialogScaffold(
        title = spell.name,
        subtitle = listOf(spell.school, sheetSpellManaLabel(spell)).filter { it.isNotBlank() }.joinToString(" · "),
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сотворить · $amount маны", { if (sheetCastSpell(host, uid, amount)) onDismiss() }, enabled = blocked == null),
        secondary = FuryDialogAction("Отмена", onDismiss),
    ) {
        SheetFacts(
            listOf("Время" to spell.time, "Дистанция" to spell.range, "Область" to spell.area, "Проверка" to spell.action, "Длительность" to spell.duration)
                .filter { it.second.isNotBlank() },
        )
        if (spell.description.isNotBlank()) Text(spell.description, color = p.text2, style = MaterialTheme.typography.bodyMedium)
        if (spell.enhancement.isNotBlank()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.goldSoft).border(1.dp, p.goldLine, RoundedCornerShape(10.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("УСИЛЕНИЕ", color = p.goldText, style = furyOverlineStyle())
                Text(spell.enhancement, color = p.text2, style = MaterialTheme.typography.bodySmall)
            }
        }
        sheetSpellWeakText(host.active, spell)?.let { Text(it, color = p.goldText, style = MaterialTheme.typography.labelMedium) }
        if (spell.conflictNote.isNotBlank()) {
            Text("РАСХОЖДЕНИЕ КНИГИ", color = p.muted, style = furyOverlineStyle())
            Text(spell.conflictNote, color = p.muted, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorSquareButton("−", "Меньше маны", amount > 0, 44.dp) { amountText = (amount - 1).coerceAtLeast(0).toString() }
            FuryTextField(
                amountText, { amountText = it.filter(Char::isDigit).take(4) }, "Мана", Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            EditorSquareButton("+", "Больше маны", true, 44.dp) { amountText = (amount + 1).toString() }
        }
        Text("есть $current из ${host.active.effectiveManaMaximum}", color = p.muted, style = MaterialTheme.typography.labelSmall)
        blocked?.let { Text(it, color = p.accText, style = MaterialTheme.typography.labelMedium) }
    }
}
