package com.furybook.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Decodes a portrait stored as a data URL; null for anything else or a broken image. */
@Composable
fun rememberWebPortrait(dataUrl: String): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, dataUrl) {
        value = runCatching {
            if (dataUrl.startsWith("data:image/")) dataUrlBytes(dataUrl).decodeToImageBitmap() else null
        }.getOrNull()
    }
    return bitmap
}
