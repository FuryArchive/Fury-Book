package com.furybook.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuryTokensTest {
    private val graphite = furyPalette(FuryThemeId.GRAPHITE)

    @Test
    fun paletteMatchesSpec() {
        assertEquals(Color(0xFF08080A), graphite.bg)
        assertEquals(Color(0xFFE5384F), graphite.acc)
        assertEquals(Color(0xFFB82840), graphite.acc2)
        assertEquals(Color(0xFFF5C04E), graphite.gold)
    }

    @Test
    fun textPairsReachAA() {
        assertTrue(contrastRatio(graphite.text, graphite.bg) >= 4.5)
        assertTrue(contrastRatio(graphite.muted, graphite.panel2) >= 4.5)
        assertTrue(contrastRatio(graphite.onAcc, graphite.acc2) >= 4.5)
        assertTrue(contrastRatio(graphite.accText, graphite.panel) >= 4.5)
    }

    @Test
    fun whiteOnCrimsonIsNotUsedAsAFill() {
        assertTrue(contrastRatio(Color.White, graphite.acc) < 4.5)
    }

    @Test
    fun materialPrimaryIsReadableAsTextOnEverySurface() = FuryThemeId.entries.forEach { id ->
        // Material uses primary as the label colour of TextButton and OutlinedButton.
        val scheme = furyColorScheme(furyPalette(id))
        listOf(scheme.surface, scheme.surfaceVariant, scheme.surfaceContainerHigh).forEach {
            assertTrue(contrastRatio(scheme.primary, it) >= 4.5, "$id primary on $it")
        }
        assertTrue(contrastRatio(scheme.onPrimary, scheme.primary) >= 4.5, "$id onPrimary")
    }

    @Test
    fun dialogAndMenuSurfacesUseThePalette() = FuryThemeId.entries.forEach { id ->
        val p = furyPalette(id)
        val scheme = furyColorScheme(p)
        assertEquals(p.panel2, scheme.surfaceContainerHigh)
        assertEquals(p.panel, scheme.surfaceContainer)
    }

    @Test
    fun emptyBarTrackIsVisibleOnPanelsAndTiles() = FuryThemeId.entries.forEach { id ->
        val p = furyPalette(id)
        assertTrue(contrastRatio(p.track, p.panel) >= 1.3, "$id track/panel")
        assertTrue(contrastRatio(p.track, p.panel2) >= 1.3, "$id track/panel2")
    }
}
