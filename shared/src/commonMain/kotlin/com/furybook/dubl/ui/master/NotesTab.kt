package com.furybook.dubl.ui.master

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.furybook.dubl.master.MasterNote
import com.furybook.dubl.master.newNote
import com.furybook.dubl.master.searchNotes
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublText

/** Заметки: a session log, newest first, with search. Kept on this device only. */
@Composable
internal fun NotesTab(scope: MasterScope) {
    val state = scope.state
    var editing by remember { mutableStateOf<MasterNote?>(null) }
    MasterPage {
        MasterHeader("Заметки", "Журнал сессий. Хранится только на этом устройстве.") {
            FuryButton("+ Новая заметка", {
                val note = newNote(masterId(), masterToday())
                scope.change { it.copy(notes = listOf(note) + it.notes) }
                editing = note
            }, style = FuryButtonStyle.Primary)
        }
        FuryTextField(state.noteQuery, { state.noteQuery = it }, "Поиск по заметкам")
        val notes = searchNotes(state.data.notes, state.noteQuery)
        if (notes.isEmpty()) {
            Text(
                if (state.data.notes.isEmpty()) "Заметок пока нет." else "Ничего не найдено.",
                color = DublMuted, style = MaterialTheme.typography.bodyMedium,
            )
        }
        notes.forEach { note ->
            MasterCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Изменить заметку") { editing = note }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(note.title.ifBlank { "Без названия" }, Modifier.weight(1f), color = DublText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(note.date, color = DublMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (note.text.isNotBlank()) Text(note.text, color = DublText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    editing?.let { note ->
        var title by remember(note.id) { mutableStateOf(note.title) }
        var date by remember(note.id) { mutableStateOf(note.date) }
        var text by remember(note.id) { mutableStateOf(note.text) }
        fun save() {
            val saved = note.copy(title = title.trim(), date = date.trim(), text = text.trimEnd())
            scope.change { d -> d.copy(notes = d.notes.map { if (it.id == note.id) saved else it }) }
            editing = null
        }
        FuryDialogScaffold(
            title = "Заметка",
            onDismiss = ::save,
            primary = FuryDialogAction("Готово", ::save),
            destructive = FuryDialogAction("Удалить", {
                editing = null
                scope.change("Заметка удалена") { d -> d.copy(notes = d.notes.filterNot { it.id == note.id }) }
            }),
        ) {
            FuryTextField(title, { title = it }, "Название")
            FuryTextField(date, { date = it }, "Дата")
            FuryTextField(text, { text = it }, "Текст", singleLine = false)
        }
    }
}
