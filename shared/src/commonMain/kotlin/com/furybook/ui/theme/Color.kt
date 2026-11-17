package com.furybook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Legacy names for the active theme's colours (see Palette.kt). They read LocalFuryPalette, so every
// screen that uses them follows the player's theme; they can only be read inside composition.
val DublBackground: Color @Composable @ReadOnlyComposable get() = FuryColors.current.bg
val DublSurface: Color @Composable @ReadOnlyComposable get() = FuryColors.current.panel
val DublSurfaceInset: Color @Composable @ReadOnlyComposable get() = FuryColors.current.bg
val DublSurfaceRaised: Color @Composable @ReadOnlyComposable get() = FuryColors.current.panel2
val DublBorder: Color @Composable @ReadOnlyComposable get() = FuryColors.current.line2
val FuryBorderStrong: Color @Composable @ReadOnlyComposable get() = FuryColors.current.track
/** The unfilled part of a resource bar; must stay visible on panels and tiles. */
val FuryBarTrack: Color @Composable @ReadOnlyComposable get() = FuryColors.current.track
val DublText: Color @Composable @ReadOnlyComposable get() = FuryColors.current.text
val DublMuted: Color @Composable @ReadOnlyComposable get() = FuryColors.current.muted

// The accent is for bars, markers and accent text. Text does not pass contrast on it,
// so filled controls use FuryAccentFill (acc2) with FuryOnAccentFill instead.
val DublAccent: Color @Composable @ReadOnlyComposable get() = FuryColors.current.acc
val DublAccentStrong: Color @Composable @ReadOnlyComposable get() = FuryColors.current.accText
val DublAccentSoft: Color @Composable @ReadOnlyComposable get() = FuryColors.current.accSoft
val DublFocus: Color @Composable @ReadOnlyComposable get() = FuryColors.current.accText
val DublGold: Color @Composable @ReadOnlyComposable get() = FuryColors.current.gold

val FuryAccentFill: Color @Composable @ReadOnlyComposable get() = FuryColors.current.acc2
val FuryAccentFillPressed: Color @Composable @ReadOnlyComposable get() = FuryColors.current.accPressed
val FuryOnAccentFill: Color @Composable @ReadOnlyComposable get() = FuryColors.current.onAcc

val DublHealth: Color @Composable @ReadOnlyComposable get() = FuryColors.current.acc
val DublStamina: Color @Composable @ReadOnlyComposable get() = FuryColors.current.gold
// Mana and custom resources keep their colour in every theme.
val DublMana = Color(0xFF6F98D6)
val DublCustomResource = Color(0xFF65B4A4)
// Helpful conditions (Воодушевление, Невидимость) keep one green chip in every theme.
val FuryHelpfulChip = Color(0xFF14302A)
val FuryHelpfulChipText = Color(0xFF7FE0C4)
val DublDanger: Color @Composable @ReadOnlyComposable get() = FuryColors.current.accText
