package com.furybook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material roles for screens that still use stock Material components. `primary` doubles as the
 * label colour of TextButton and OutlinedButton, so it has to be readable as text on every
 * surface; new Fury components take their fills from FuryAccentFill directly instead.
 */
val FuryColorScheme = darkColorScheme(
    primary = DublAccentStrong,
    onPrimary = DublBackground,
    primaryContainer = DublAccentSoft,
    onPrimaryContainer = DublText,
    secondary = DublGold,
    onSecondary = Color(0xFF211B13),
    secondaryContainer = Color(0xFF2A2208),
    onSecondaryContainer = DublText,
    background = DublBackground,
    onBackground = DublText,
    surface = DublSurface,
    onSurface = DublText,
    surfaceVariant = DublSurfaceRaised,
    surfaceDim = DublBackground,
    surfaceBright = DublSurfaceRaised,
    surfaceContainerLowest = DublBackground,
    surfaceContainerLow = DublSurface,
    surfaceContainer = DublSurface,
    surfaceContainerHigh = DublSurfaceRaised,
    surfaceContainerHighest = DublSurfaceRaised,
    onSurfaceVariant = DublMuted,
    outline = DublBorder,
    outlineVariant = DublBorder,
    error = DublDanger,
)

@Composable
fun DublTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FuryColorScheme,
        typography = furyTypography(),
        content = content,
    )
}
