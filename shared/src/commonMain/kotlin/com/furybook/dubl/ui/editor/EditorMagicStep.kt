package com.furybook.dubl.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.furybook.dubl.ui.sheet.SheetLayout

/** Магия: mana rank, schools, spellbook and catalog. */
@Composable
internal fun EditorMagicStep(host: DublEditorHost, state: EditorState, data: EditorFrameData, layout: SheetLayout) {
    Column(Modifier.fillMaxSize().padding(editorContentPadding(layout))) {
        EditorStepTitle("Магия", "Запас маны, школы и книга заклинаний.")
    }
}
