package com.furybook.dubl.data

import com.furybook.ui.theme.FuryThemeId
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopThemeStoreTest {
    @Test
    fun missingFileLoadsDefault() {
        val dir = Files.createTempDirectory("theme")
        assertEquals(FuryThemeId.FURY, DesktopThemeStore(dir.resolve("ui-theme.txt")).load())
    }

    @Test
    fun savedThemeSurvivesANewInstance() {
        val file = Files.createTempDirectory("theme").resolve("nested/ui-theme.txt")
        DesktopThemeStore(file).save(FuryThemeId.NEON)
        assertEquals(FuryThemeId.NEON, DesktopThemeStore(file).load())
    }

    @Test
    fun garbageLoadsDefault() {
        val file = Files.createTempDirectory("theme").resolve("ui-theme.txt")
        Files.writeString(file, "garbage")
        assertEquals(FuryThemeId.FURY, DesktopThemeStore(file).load())
    }
}
