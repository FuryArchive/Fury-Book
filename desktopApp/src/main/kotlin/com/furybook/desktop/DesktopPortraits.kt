package com.furybook.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path

/** Opens the system file dialog for a portrait image; null when cancelled. */
fun chooseDesktopPortraitFile(): Path? {
    val dialog = FileDialog(null as Frame?, "Выбрать портрет", FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return Path.of(dialog.directory, file)
}

/** Reads a portrait copied into the data folder; null when the file is missing or not an image. */
@Composable
fun rememberDesktopPortrait(path: String): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = runCatching { File(path).inputStream().buffered().use(::loadImageBitmap) }.getOrNull()
    }
    return bitmap
}
