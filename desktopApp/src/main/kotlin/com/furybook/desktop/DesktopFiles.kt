package com.furybook.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** The system file dialogs Персонажи uses: character transfer files and .fcp packs. */
internal object DesktopFiles {
    fun pickFcp(): Path? = pick("Импорт Fury Content Pack", "*.fcp")

    /** Reads a character file the player picks: null text when it can't be read, no call when cancelled. */
    fun importCharacter(onRead: (String?) -> Unit) {
        val file = pick("Импорт персонажа DUBL", null) ?: return
        onRead(runCatching { Files.readString(file, StandardCharsets.UTF_8) }.getOrNull())
    }

    /** Saves [content] where the player chooses; adds «.dubl» when the name lacks it. */
    fun exportCharacter(fileName: String, content: String, onSaved: (Boolean) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Экспорт персонажа DUBL", FileDialog.SAVE)
        dialog.file = fileName
        dialog.isVisible = true
        val name = dialog.file ?: return
        val chosen = Path.of(dialog.directory, name)
        val target = if (chosen.fileName.toString().endsWith(".dubl", ignoreCase = true)) chosen else chosen.resolveSibling("${chosen.fileName}.dubl")
        onSaved(runCatching { Files.writeString(target, content, StandardCharsets.UTF_8) }.isSuccess)
    }

    private fun pick(title: String, filter: String?): Path? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        filter?.let { dialog.file = it }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return Path.of(dialog.directory, file)
    }
}
