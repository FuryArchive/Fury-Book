package com.furybook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The colour themes the player can pick; [key] is what the platform stores. */
enum class FuryThemeId(val key: String, val title: String) {
    FURY("fury", "Ярость"),
    GRAPHITE("graphite", "Графит"),
    NEON("neon", "Неон"),
    WASTELAND("wasteland", "Пустошь"),
    PARCHMENT("parchment", "Пергамент"),
    ;

    companion object {
        fun fromKey(key: String?): FuryThemeId = entries.firstOrNull { it.key == key?.trim() } ?: FURY
    }
}

/**
 * Every colour role the UI uses. Surfaces from darkest to lightest (bg, head, sunk, panel, panel2, tile,
 * hover), lines, text from strongest to faintest, the accent family (acc2 is the fill that carries
 * [onAcc] text), gold, and the cool tint of derived stats (d*).
 */
data class FuryPalette(
    val isLight: Boolean,
    val bg: Color,
    val head: Color,
    val sunk: Color,
    val panel: Color,
    val panel2: Color,
    val tile: Color,
    val hover: Color,
    val line: Color,
    val line2: Color,
    val line3: Color,
    val track: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val dim: Color,
    val acc: Color,
    val acc2: Color,
    val accPressed: Color,
    val accSoft: Color,
    val accLine: Color,
    val accText: Color,
    val accDeep: Color,
    val onAcc: Color,
    val gold: Color,
    val goldSoft: Color,
    val goldLine: Color,
    val goldText: Color,
    val dBg: Color,
    val dLine: Color,
    val dLabel: Color,
    val dText: Color,
)

private val Graphite = FuryPalette(
    isLight = false,
    bg = Color(0xFF08080A), head = Color(0xFF0D0D10), sunk = Color(0xFF0E0E11), panel = Color(0xFF111114),
    panel2 = Color(0xFF16161A), tile = Color(0xFF18181D), hover = Color(0xFF1C1C22),
    line = Color(0xFF1E1E24), line2 = Color(0xFF26262C), line3 = Color(0xFF3A3A44), track = Color(0xFF34343A),
    text = Color(0xFFF4F1EC), text2 = Color(0xFFC4C4CC), muted = Color(0xFFB4B1AC), dim = Color(0xFF8C8C95),
    acc = Color(0xFFE5384F), acc2 = Color(0xFFB82840), accPressed = Color(0xFF8E1B30), accSoft = Color(0xFF3A1119),
    accLine = Color(0xFF6A2232), accText = Color(0xFFFF6B7F), accDeep = Color(0xFF2A1218), onAcc = Color.White,
    gold = Color(0xFFF5C04E), goldSoft = Color(0xFF2A2112), goldLine = Color(0xFF5A4520), goldText = Color(0xFFD9AE52),
    dBg = Color(0xFF0D1519), dLine = Color(0xFF1A2A31), dLabel = Color(0xFF7FA9BC), dText = Color(0xFFCFE7F2),
)

private val Fury = FuryPalette(
    isLight = false,
    bg = Color(0xFF0B0809), head = Color(0xFF110B0D), sunk = Color(0xFF0E0A0B), panel = Color(0xFF171012),
    panel2 = Color(0xFF1D1416), tile = Color(0xFF22171A), hover = Color(0xFF291C20),
    line = Color(0xFF2C1F23), line2 = Color(0xFF3A282D), line3 = Color(0xFF4E353B), track = Color(0xFF3E2C31),
    text = Color(0xFFF7EEF0), text2 = Color(0xFFDACACE), muted = Color(0xFFB9A6AB), dim = Color(0xFF968187),
    acc = Color(0xFFE8384F), acc2 = Color(0xFFB82840), accPressed = Color(0xFF8E1B30), accSoft = Color(0xFF401620),
    accLine = Color(0xFF6E2334), accText = Color(0xFFFFA6B2), accDeep = Color(0xFF2E1219), onAcc = Color.White,
    gold = Color(0xFFF5C04E), goldSoft = Color(0xFF2F2310), goldLine = Color(0xFF5E4720), goldText = Color(0xFFE0B65E),
    dBg = Color(0xFF11171C), dLine = Color(0xFF203039), dLabel = Color(0xFF8AB3C6), dText = Color(0xFFD8ECF5),
)

private val Neon = FuryPalette(
    isLight = false,
    bg = Color(0xFF0A0716), head = Color(0xFF0E0A1E), sunk = Color(0xFF0C0919), panel = Color(0xFF140F26),
    panel2 = Color(0xFF1B1531), tile = Color(0xFF211A3B), hover = Color(0xFF271F46),
    line = Color(0xFF2A2148), line2 = Color(0xFF3A2E62), line3 = Color(0xFF4F407F), track = Color(0xFF3A3068),
    text = Color(0xFFF4EFFF), text2 = Color(0xFFD6CDF2), muted = Color(0xFFAEA3D2), dim = Color(0xFF8C80B4),
    acc = Color(0xFFFF2E88), acc2 = Color(0xFFC8166A), accPressed = Color(0xFF9A1052), accSoft = Color(0xFF3D0F30),
    accLine = Color(0xFF74205A), accText = Color(0xFFFFA3D0), accDeep = Color(0xFF2A0C26), onAcc = Color.White,
    gold = Color(0xFF3EE6FF), goldSoft = Color(0xFF0C2A38), goldLine = Color(0xFF1C5C72), goldText = Color(0xFF86F0FF),
    dBg = Color(0xFF1A1338), dLine = Color(0xFF36296E), dLabel = Color(0xFFB7A0FF), dText = Color(0xFFE8DEFF),
)

private val Wasteland = FuryPalette(
    isLight = false,
    bg = Color(0xFF0F0D09), head = Color(0xFF14110C), sunk = Color(0xFF110F0B), panel = Color(0xFF1B1812),
    panel2 = Color(0xFF221E16), tile = Color(0xFF28231A), hover = Color(0xFF2F291E),
    line = Color(0xFF332C20), line2 = Color(0xFF42392B), line3 = Color(0xFF594C39), track = Color(0xFF4A402F),
    text = Color(0xFFF3EBD9), text2 = Color(0xFFD8CDB4), muted = Color(0xFFB6A98D), dim = Color(0xFF93876E),
    acc = Color(0xFFEE8A2E), acc2 = Color(0xFFC26616), accPressed = Color(0xFF944C0F), accSoft = Color(0xFF3C2611),
    accLine = Color(0xFF6E4519), accText = Color(0xFFFFC58F), accDeep = Color(0xFF2C1D0C), onAcc = Color(0xFF1A0F05),
    gold = Color(0xFFBCD65E), goldSoft = Color(0xFF232B10), goldLine = Color(0xFF4C5C1F), goldText = Color(0xFFCCE184),
    dBg = Color(0xFF141B15), dLine = Color(0xFF2A3626), dLabel = Color(0xFFA1BD8E), dText = Color(0xFFDEEDD3),
)

private val Parchment = FuryPalette(
    isLight = true,
    bg = Color(0xFFE8DFCA), head = Color(0xFFE1D6BD), sunk = Color(0xFFE3D9C2), panel = Color(0xFFF6F0E2),
    panel2 = Color(0xFFFBF7EE), tile = Color(0xFFEFE6D2), hover = Color(0xFFEADFC8),
    line = Color(0xFFD8CCB1), line2 = Color(0xFFC8BA9B), line3 = Color(0xFFB09F7C), track = Color(0xFFCDBF9F),
    text = Color(0xFF231910), text2 = Color(0xFF463728), muted = Color(0xFF625140), dim = Color(0xFF6E5A46),
    acc = Color(0xFFA12C25), acc2 = Color(0xFF8A231E), accPressed = Color(0xFF6A1A16), accSoft = Color(0xFFF2DAD2),
    accLine = Color(0xFFD7A69C), accText = Color(0xFF7A1E19), accDeep = Color(0xFFEEDCD2), onAcc = Color(0xFFFFF8EE),
    gold = Color(0xFF8C5E0F), goldSoft = Color(0xFFF2E4C1), goldLine = Color(0xFFD1B26C), goldText = Color(0xFF6E4A0B),
    dBg = Color(0xFFE1E8E5), dLine = Color(0xFFBFCDC8), dLabel = Color(0xFF3C635D), dText = Color(0xFF1C3834),
)

fun furyPalette(id: FuryThemeId): FuryPalette = when (id) {
    FuryThemeId.FURY -> Fury
    FuryThemeId.GRAPHITE -> Graphite
    FuryThemeId.NEON -> Neon
    FuryThemeId.WASTELAND -> Wasteland
    FuryThemeId.PARCHMENT -> Parchment
}

val LocalFuryPalette: ProvidableCompositionLocal<FuryPalette> = staticCompositionLocalOf { furyPalette(FuryThemeId.FURY) }

object FuryColors {
    val current: FuryPalette
        @Composable @ReadOnlyComposable get() = LocalFuryPalette.current
}
