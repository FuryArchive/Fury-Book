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

    @Test
    fun materialPrimaryIsReadableAsTextOnEverySurface() {
        // Material uses primary as the label colour of TextButton and OutlinedButton.
        listOf(FuryColorScheme.surface, FuryColorScheme.surfaceVariant, FuryColorScheme.surfaceContainerHigh).forEach {
            assertTrue(contrastRatio(FuryColorScheme.primary, it) >= 4.5)
        }
        assertTrue(contrastRatio(FuryColorScheme.onPrimary, FuryColorScheme.primary) >= 4.5)
    }

    @Test
    fun dialogAndMenuSurfacesUseThePalette() {
        assertEquals(DublSurfaceRaised, FuryColorScheme.surfaceContainerHigh)
        assertEquals(DublSurface, FuryColorScheme.surfaceContainer)
    }

    @Test
    fun emptyBarTrackIsVisibleOnPanelsAndTiles() {
        assertTrue(contrastRatio(FuryBarTrack, DublSurface) >= 1.3)
        assertTrue(contrastRatio(FuryBarTrack, DublSurfaceRaised) >= 1.3)
    }
}
