package com.furybook.android.ui.components

import com.furybook.ui.theme.FuryThemeId
import com.furybook.ui.theme.furyPalette
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DublSwitchTokensTest {
    @Test
    fun uncheckedThumbStaysVisuallyDistinctFromTrack() {
        FuryThemeId.entries.forEach { id ->
            val tokens = dublSwitchTokens(furyPalette(id))
            assertNotEquals(tokens.uncheckedThumb, tokens.uncheckedTrack)
            assertTrue(tokens.uncheckedThumb.alpha >= 0.9f)
        }
    }
}
