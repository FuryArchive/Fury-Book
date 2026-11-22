package com.furybook.dubl.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPortraitsTest {
    @Test
    fun signOutRemovesOnlyTheAccountsPortraits() {
        val dir = Files.createTempDirectory("portraits")
        listOf("a-1.png", "a-2.jpg", "b-1.png", "ab-1.png").forEach { Files.writeString(dir.resolve(it), "x") }
        DesktopPortraits.deleteFor(setOf("a"), dir)
        assertEquals(listOf("ab-1.png", "b-1.png"), Files.list(dir).map { it.fileName.toString() }.sorted().toList())
        DesktopPortraits.deleteFor(setOf("a"), dir.resolve("missing"))
    }
}
