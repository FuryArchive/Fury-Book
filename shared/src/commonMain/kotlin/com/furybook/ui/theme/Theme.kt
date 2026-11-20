package com.furybook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Material roles for screens that still use stock Material components. `primary` doubles as the
 * label colour of TextButton and OutlinedButton, so it has to be readable as text on every
 * surface; new Fury components take their fills from FuryAccentFill directly instead.
 */
fun furyColorScheme(p: FuryPalette): ColorScheme {
    val base = if (p.isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = p.accText,
        onPrimary = p.bg,
        primaryContainer = p.accSoft,
        onPrimaryContainer = p.text,
        secondary = p.gold,
        onSecondary = p.bg,
        secondaryContainer = p.goldSoft,
        onSecondaryContainer = p.text,
        background = p.bg,
        onBackground = p.text,
        surface = p.panel,
        onSurface = p.text,
        surfaceVariant = p.panel2,
        surfaceDim = p.bg,
        surfaceBright = p.panel2,
        surfaceContainerLowest = p.bg,
        surfaceContainerLow = p.panel,
        surfaceContainer = p.panel,
        surfaceContainerHigh = p.panel2,
        surfaceContainerHighest = p.panel2,
        onSurfaceVariant = p.muted,
        outline = p.line2,
        outlineVariant = p.line2,
        error = p.accText,
        inverseSurface = p.text,
        inverseOnSurface = p.bg,
    )
}

@Composable
fun DublTheme(themeId: FuryThemeId = FuryThemeId.FURY, content: @Composable () -> Unit) {
    val palette = furyPalette(themeId)
    CompositionLocalProvider(LocalFuryPalette provides palette) {
        MaterialTheme(
            colorScheme = furyColorScheme(palette),
            typography = furyTypography(),
            content = content,
        )
    }
}
