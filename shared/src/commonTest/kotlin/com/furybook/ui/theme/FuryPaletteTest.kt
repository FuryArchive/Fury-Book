package com.furybook.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuryPaletteTest {
    @Test
    fun graphiteIsTodaysPalette() {
        val p = furyPalette(FuryThemeId.GRAPHITE)
        assertEquals(Color(0xFF08080A), p.bg)
        assertEquals(Color(0xFF111114), p.panel)
        assertEquals(Color(0xFF16161A), p.panel2)
        assertEquals(Color(0xFF26262C), p.line2)
        assertEquals(Color(0xFF34343A), p.track)
        assertEquals(Color(0xFFF4F1EC), p.text)
        assertEquals(Color(0xFFB4B1AC), p.muted)
        assertEquals(Color(0xFFE5384F), p.acc)
        assertEquals(Color(0xFFFF6B7F), p.accText)
        assertEquals(Color(0xFF3A1119), p.accSoft)
        assertEquals(Color(0xFFF5C04E), p.gold)
        assertEquals(Color(0xFFB82840), p.acc2)
        assertEquals(Color(0xFF8E1B30), p.accPressed)
        assertEquals(Color.White, p.onAcc)
    }

    @Test
    fun everyPaletteReachesContrast() = FuryThemeId.entries.forEach { id ->
        val p = furyPalette(id)
        listOf(
            p.text to p.bg, p.text to p.panel2, p.muted to p.panel2, p.dim to p.panel, p.onAcc to p.acc2,
            p.accText to p.panel, p.accText to p.accSoft, p.goldText to p.goldSoft, p.dLabel to p.dBg, p.dText to p.dBg,
        ).forEach { (fg, bg) -> assertTrue(contrastRatio(fg, bg) >= 4.5, "$id: $fg on $bg = ${contrastRatio(fg, bg)}") }
        assertTrue(contrastRatio(p.track, p.panel) >= 1.3, "$id track")
        assertTrue(contrastRatio(p.gold, p.panel) >= 3.0 && contrastRatio(p.acc, p.panel) >= 3.0, "$id meters")
    }

    @Test
    fun onlyParchmentIsLight() =
        assertEquals(listOf(FuryThemeId.PARCHMENT), FuryThemeId.entries.filter { furyPalette(it).isLight })

    @Test
    fun unknownKeyFallsBackToFury() {
        assertEquals(FuryThemeId.FURY, FuryThemeId.fromKey("nope"))
        assertEquals(FuryThemeId.FURY, FuryThemeId.fromKey(null))
        assertEquals(FuryThemeId.NEON, FuryThemeId.fromKey("neon"))
    }
}
