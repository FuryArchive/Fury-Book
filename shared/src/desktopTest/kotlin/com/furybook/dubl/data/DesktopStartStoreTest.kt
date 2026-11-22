package com.furybook.dubl.data

import com.furybook.ui.account.FuryStartMode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopStartStoreTest {
    @Test
    fun missingFileMeansNotChosen() {
        assertEquals(FuryStartMode.NONE, DesktopStartStore(Files.createTempDirectory("start").resolve("start-mode.txt")).load())
    }

    @Test
    fun savedModeSurvivesANewInstance() {
        val file = Files.createTempDirectory("start").resolve("nested/start-mode.txt")
        DesktopStartStore(file).save(FuryStartMode.GUEST)
        assertEquals(FuryStartMode.GUEST, DesktopStartStore(file).load())
    }
}
