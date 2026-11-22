package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing

/**
 * Loads a stored portrait reference into a bitmap, or null when it can't be read. Platforms supply it:
 * desktop reads a copied file, web decodes a data URL, Android opens a persistable content URI.
 */
typealias SheetPortraitLoader = @Composable (uri: String) -> ImageBitmap?

/** Portrait or a placeholder with the first letter of the name; [onPick]/[onRemove] show the edit buttons. */
@Composable
fun SheetPortrait(
    uri: String?,
    name: String,
    loader: SheetPortraitLoader?,
    onPick: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
) {
    val bitmap = if (uri.isNullOrBlank() || loader == null) null else loader(uri)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FurySpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(FuryShapes.panel).background(DublSurfaceRaised), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = "Портрет персонажа", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
            } else {
                Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = DublMuted, style = MaterialTheme.typography.headlineMedium)
            }
        }
        onPick?.let { FuryButton(if (uri.isNullOrBlank()) "Портрет" else "Сменить", it, style = FuryButtonStyle.Ghost) }
        if (!uri.isNullOrBlank()) onRemove?.let { FuryButton("Убрать", it, style = FuryButtonStyle.Ghost) }
    }
}
