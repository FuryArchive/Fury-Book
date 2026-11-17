package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import com.furybook.dubl.model.CharacterNote
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing

/**
 * Titled notes, each collapsible. With [host] set (edit mode or any time on wide screens) notes can be
 * added, edited and deleted in a dialog; title ≤ 120 and body ≤ 12 000 characters, as on the old desktop sheet.
 */
@Composable
fun SheetNotes(notes: List<CharacterNote>, host: DublSheetEditHost?, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<CharacterNote?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
        if (notes.isEmpty()) Text("Заметок пока нет", color = DublMuted, style = MaterialTheme.typography.bodyMedium)
        notes.forEach { note ->
            val open = note.id in expanded
            Column(
                Modifier.fillMaxWidth().clip(FuryShapes.panel).background(DublSurfaceRaised).padding(FurySpacing.md),
                verticalArrangement = Arrangement.spacedBy(FurySpacing.xs),
            ) {
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = FuryMinTouch)
                        .clickable(role = Role.Button, onClickLabel = if (open) "Свернуть" else "Развернуть") {
                            expanded = if (open) expanded - note.id else expanded + note.id
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(note.title.ifBlank { "Без названия" }, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (open) "▴" else "▾", color = DublMuted)
                }
                if (open) {
                    Text(note.body, color = DublText, style = MaterialTheme.typography.bodyMedium)
                    host?.let { FuryButton("Изменить", { editing = note }, style = FuryButtonStyle.Ghost) }
                } else if (note.body.isNotBlank()) {
                    Text(note.body, color = DublMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        host?.let { FuryButton("Добавить заметку", { adding = true }, style = FuryButtonStyle.Ghost) }
    }
    if (host != null && (adding || editing != null)) {
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
private fun NoteEditor(note: CharacterNote?, onSave: (String, String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note?.id) { mutableStateOf(note?.body.orEmpty()) }
    FuryDialogScaffold(
        title = if (note == null) "Новая заметка" else "Заметка",
        subtitle = "До $SHEET_NOTE_TITLE_MAX символов в названии и $SHEET_NOTE_BODY_MAX в тексте",
        onDismiss = onDismiss,
        primary = FuryDialogAction("Сохранить", { onSave(title.trim(), body); onDismiss() }, enabled = title.isNotBlank()),
        secondary = FuryDialogAction("Отмена", onDismiss),
        destructive = onDelete?.let { delete -> FuryDialogAction("Удалить", { delete(); onDismiss() }) },
    ) {
        FuryTextField(title, { title = noteTitleInput(it) }, "Название", error = if (title.isBlank()) "Нужно название" else null)
        FuryTextField(body, { body = noteBodyInput(it) }, "Текст", singleLine = false)
    }
}
