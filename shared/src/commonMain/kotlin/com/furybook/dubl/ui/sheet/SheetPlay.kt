package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.CharacterNote
import com.furybook.dubl.model.displayNotes
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.furyOverlineStyle

/** Skill and perk columns side by side from tablet width up; one column on phones. */
internal fun sheetPlayColumns(layout: SheetLayout): Int = if (layout == SheetLayout.PHONE) 1 else 2

/** Derived stats: one row of six on tablet (desktop shows them in its identity row), 3×2 on phones. */
internal fun sheetPlayDerivedColumns(layout: SheetLayout): Int = if (layout == SheetLayout.PHONE) 3 else 6

private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/**
 * Play mode («Игра») for every width. Desktop fits one screen: header, then Умения | Навыки scrolling on
 * their own, with roll, combat and notes in the right tray. Tablet and phone pin the compact header and
 * scroll the rest. Rolls open as before: in the tray on desktop, in the roll dialog elsewhere.
 */
@Composable
internal fun SheetPlayBody(
    host: DublSheetEditHost,
    layout: SheetLayout,
    state: SheetScreenState,
    playState: SheetPlayState,
    portrait: (@Composable (Dp) -> Unit)?,
    trayWidth: Dp,
    roll: @Composable (Modifier) -> Unit,
) {
    val character = host.active
    val open: (SheetRollSubject) -> Unit = { subject -> state.open(subject, dialog = layout != SheetLayout.WIDE_WITH_TRAY) }
    val skills = remember(host, character, host.extras, host.developmentCatalog, playState.rankedOnly) {
        sheetSkillsByAttribute(host, playState.rankedOnly)
    }
    val totals = remember(host, character) { sheetSkillTotals(host) }
    val perks = remember(host, character, host.extras, host.developmentCatalog) { sheetPerkGroups(host) }
    val compact = layout != SheetLayout.WIDE_WITH_TRAY
    val skillsColumn: @Composable (Modifier, Boolean) -> Unit = { modifier, scrollable ->
        SheetPlaySkills(
            groups = skills,
            totals = totals,
            rankedOnly = playState.rankedOnly,
            onToggleRanked = { playState.rankedOnly = !playState.rankedOnly },
            onRollAttribute = { open(SheetRollSubject.Attribute(it)) },
            onRollSkill = { open(SheetRollSubject.Skill(it)) },
            compact = compact,
            scrollable = scrollable,
            modifier = modifier,
        )
    }
    val perksColumn: @Composable (Modifier, Boolean) -> Unit = { modifier, scrollable ->
        SheetPlayPerks(perks, playState, { id -> state.openDialog(SheetDialog.DevelopmentDetails(id)) }, compact, scrollable, modifier)
    }
    val combat: @Composable (Modifier) -> Unit = { modifier ->
        PlayCard("Боевые действия", modifier) {
            SheetCombat(SHEET_COMBAT_CONTEXTS, onRoll = { context -> open(SheetRollSubject.Context(context)) })
        }
    }
    val notes: @Composable (Modifier, Boolean) -> Unit = { modifier, scrollable ->
        SheetPlayNotes(host.extras.displayNotes(), playState, host, scrollable, modifier)
    }
    val editor = { state.updateEditing(true) }
    when (layout) {
        SheetLayout.WIDE_WITH_TRAY -> Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SheetPlayHeaderWide(host, state, playState, editor, portrait?.let { slot -> { slot(52.dp) } })
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    skillsColumn(Modifier.weight(1f).fillMaxHeight(), true)
                    perksColumn(Modifier.weight(1.35f).fillMaxHeight(), true)
                }
            }
            Column(
                Modifier.width(trayWidth).fillMaxHeight().padding(top = 14.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                roll(Modifier.fillMaxWidth())
                combat(Modifier.fillMaxWidth())
                notes(Modifier.fillMaxWidth().weight(1f), true)
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            SheetPlayHeaderCompact(host, state, playState, editor, portrait?.let { slot -> { slot(36.dp) } })
            val padding = if (layout == SheetLayout.PHONE) 12.dp else 20.dp
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = padding, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlayDerivedGrid(sheetMetrics(character), sheetPlayDerivedColumns(layout), state)
                if (sheetPlayColumns(layout) == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        skillsColumn(Modifier.weight(1f), false)
                        perksColumn(Modifier.weight(1.2f), false)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        combat(Modifier.weight(1f))
                        notes(Modifier.weight(1f), false)
                    }
                } else {
                    skillsColumn(Modifier.fillMaxWidth(), false)
                    perksColumn(Modifier.fillMaxWidth(), false)
                    combat(Modifier.fillMaxWidth())
                    notes(Modifier.fillMaxWidth(), false)
                }
            }
        }
    }
}

@Composable
private fun PlayCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(FuryColors.current.panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title.uppercase(), color = FuryColors.current.muted, style = furyOverlineStyle())
        content()
    }
}

/** Tablet and phone: the derived stats in their own tint, full names, value on the right. */
@Composable
private fun PlayDerivedGrid(stats: List<SheetStat>, columns: Int, state: SheetScreenState) {
    val p = FuryColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { stat ->
                    val info = stat.info
                    val shape = RoundedCornerShape(8.dp)
                    Row(
                        Modifier.weight(1f).heightIn(min = 34.dp).clip(shape).background(p.dBg).border(1.dp, p.dLine, shape)
                            .then(if (info != null) Modifier.clickable(role = Role.Button, onClickLabel = "Формула ${stat.label}") { state.openDialog(SheetDialog.Stat(info, stat.roll)) } else Modifier)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stat.label, Modifier.weight(1f), color = p.dLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stat.valueText, color = p.dText, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), maxLines = 1, softWrap = false)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Notes as a list: title plus one line; tap opens a note in place, «Изменить» edits it, search past six notes. */
@Composable
fun SheetPlayNotes(
    notes: List<CharacterNote>,
    playState: SheetPlayState,
    host: DublSheetEditHost,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = FuryColors.current
    var editing by remember { mutableStateOf<CharacterNote?>(null) }
    var adding by remember { mutableStateOf(false) }
    val shown = filterNotes(notes, playState.noteQuery)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(p.panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ЗАМЕТКИ", color = p.muted, style = furyOverlineStyle())
            Text("${notes.size}", Modifier.weight(1f), color = p.dim, style = MaterialTheme.typography.bodySmall)
            HeaderToggle("+ заметка") { adding = true }
        }
        if (notes.size > NOTE_SEARCH_THRESHOLD) {
            SheetSearchField(playState.noteQuery, { playState.noteQuery = it }, "Поиск по заметкам…", Modifier.fillMaxWidth())
        }
        val row: @Composable (CharacterNote) -> Unit = { note -> NoteRow(note, playState) { editing = note } }
        when {
            notes.isEmpty() -> Text("Заметок пока нет", color = p.dim, style = MaterialTheme.typography.bodyMedium)
            shown.isEmpty() -> Text("Ничего не найдено", color = p.dim, style = MaterialTheme.typography.bodyMedium)
            scrollable -> LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.id }) { row(it) }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { shown.forEach { row(it) } }
        }
    }
    if (adding || editing != null) {
        NoteEditor(
            note = editing,
            onSave = { title, body ->
                val current = editing
                if (current == null) host.addNote(title, body) else host.updateNote(current.id, title, body)
            },
            onDelete = editing?.let { note -> { host.removeNote(note.id) } },
            onDismiss = { adding = false; editing = null },
        )
    }
}

@Composable
private fun NoteRow(note: CharacterNote, playState: SheetPlayState, onEdit: () -> Unit) {
    val p = FuryColors.current
    val open = note.id in playState.expandedNotes
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = if (open) "Свернуть" else "Развернуть") {
                playState.expandedNotes = if (open) playState.expandedNotes - note.id else playState.expandedNotes + note.id
            }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(note.title.ifBlank { "Без названия" }, color = p.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (note.body.isNotBlank()) {
            Text(note.body, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = if (open) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
        }
        if (open) {
            Box(Modifier.height(4.dp))
            Text(
                "Изменить", Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onEdit).padding(vertical = 4.dp),
                color = p.goldText, style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
