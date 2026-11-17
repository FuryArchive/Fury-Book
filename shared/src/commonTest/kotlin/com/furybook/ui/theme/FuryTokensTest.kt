package com.furybook.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuryTokensTest {
    @Test
    fun paletteMatchesSpec() {
        assertEquals(Color(0xFF08080A), DublBackground)
        assertEquals(Color(0xFFE5384F), DublAccent)
        assertEquals(Color(0xFFB82840), FuryAccentFill)
        assertEquals(Color(0xFFF5C04E), DublGold)
    }

    @Test
    fun textPairsReachAA() {
        assertTrue(contrastRatio(DublText, DublBackground) >= 4.5)
        assertTrue(contrastRatio(DublMuted, DublSurfaceRaised) >= 4.5)
        assertTrue(contrastRatio(FuryOnAccentFill, FuryAccentFill) >= 4.5)
        assertTrue(contrastRatio(DublAccentStrong, DublSurface) >= 4.5)
    }

    @Test
    fun whiteOnCrimsonIsNotUsedAsAFill() {
        assertTrue(contrastRatio(Color.White, DublAccent) < 4.5)
    }
}
