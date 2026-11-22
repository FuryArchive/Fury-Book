package com.furybook.dubl.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopArchiveStoreTest {
    @Test
    fun archiveSurvivesANewInstance() {
        val file = Files.createTempDirectory("archive").resolve("nested/archive.json")
        assertEquals(emptySet(), DesktopArchiveStore(file).load())
        DesktopArchiveStore(file).save(setOf("a", "b"))
        assertEquals(setOf("a", "b"), DesktopArchiveStore(file).load())
    }
}
