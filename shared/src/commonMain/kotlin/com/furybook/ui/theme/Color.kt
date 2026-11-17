package com.furybook.ui.theme

import androidx.compose.ui.graphics.Color

// Fury HUD palette shared across Android, Desktop and Web.
// The Dubl* names predate the palette and are kept so existing screens reskin without edits.
val DublBackground = Color(0xFF08080A)
val DublSurface = Color(0xFF111114)
val DublSurfaceInset = Color(0xFF08080A)
val DublSurfaceRaised = Color(0xFF16161A)
val DublBorder = Color(0xFF26262C)
val FuryBorderStrong = Color(0xFF34343A)
/** The unfilled part of a resource bar; must stay visible on panels and tiles. */
val FuryBarTrack = FuryBorderStrong
val DublText = Color(0xFFF4F1EC)
val DublMuted = Color(0xFFB4B1AC)

// Crimson is for bars, markers and accent text. White text does not pass contrast on it,
// so filled controls use FuryAccentFill instead.
val DublAccent = Color(0xFFE5384F)
val DublAccentStrong = Color(0xFFFF6B7F)
val DublAccentSoft = Color(0xFF3A1119)
val DublFocus = Color(0xFFFF95A3)
val DublGold = Color(0xFFF5C04E)

val FuryAccentFill = Color(0xFFB82840)
val FuryAccentFillPressed = Color(0xFF8E1B30)
val FuryOnAccentFill = Color.White

val DublHealth = Color(0xFFE5384F)
val DublStamina = Color(0xFFF5C04E)
val DublMana = Color(0xFF6F98D6)
val DublCustomResource = Color(0xFF65B4A4)
val DublDanger = Color(0xFFFF95A3)
