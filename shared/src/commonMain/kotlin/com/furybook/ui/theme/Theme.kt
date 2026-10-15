package com.furybook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DublColors = darkColorScheme(
    primary = DublAccent,
    onPrimary = Color.White,
    primaryContainer = DublAccentSoft,
    onPrimaryContainer = DublText,
    secondary = DublGold,
    onSecondary = Color(0xFF211B13),
    secondaryContainer = Color(0xFF2D271E),
    onSecondaryContainer = DublText,
    background = DublBackground,
    onBackground = DublText,
    surface = DublSurface,
    onSurface = DublText,
    surfaceVariant = DublSurfaceRaised,
    onSurfaceVariant = DublMuted,
    outline = DublBorder,
    outlineVariant = DublBorder,
    error = DublDanger,
)

@Composable
fun DublTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DublColors,
        typography = DublTypography,
    ) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground,
            content = content)
    }
}
