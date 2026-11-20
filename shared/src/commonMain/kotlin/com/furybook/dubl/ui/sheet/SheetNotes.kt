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

@Composable
internal fun NoteEditor(note: CharacterNote?, onSave: (String, String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
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
