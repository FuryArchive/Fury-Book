package com.furybook.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Where the player's theme choice lives on this device. */
interface FuryThemeStore {
    fun load(): FuryThemeId
    fun save(id: FuryThemeId)
}

class InMemoryFuryThemeStore(initial: FuryThemeId = FuryThemeId.FURY) : FuryThemeStore {
    private var current = initial
    override fun load(): FuryThemeId = current
    override fun save(id: FuryThemeId) {
        current = id
    }
}

/** The active theme and how to change it; provided by [FuryThemedApp], read by the theme button. */
class FuryThemeChoice(val selected: FuryThemeId, val select: (FuryThemeId) -> Unit)

val LocalFuryThemeChoice = staticCompositionLocalOf<FuryThemeChoice?> { null }

/** App root: loads the stored theme, applies it, and saves the player's next choice. */
@Composable
fun FuryThemedApp(store: FuryThemeStore, content: @Composable () -> Unit) {
    var theme by remember(store) { mutableStateOf(store.load()) }
    val choice = remember(theme, store) {
        FuryThemeChoice(theme) { next ->
            store.save(next)
            theme = next
        }
    }
    CompositionLocalProvider(LocalFuryThemeChoice provides choice) {
        DublTheme(theme, content)
    }
}
