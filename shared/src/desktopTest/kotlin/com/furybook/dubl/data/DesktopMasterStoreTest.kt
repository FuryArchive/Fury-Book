package com.furybook.dubl.data

import com.furybook.dubl.master.MasterData
import com.furybook.dubl.master.MasterNote
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopMasterStoreTest {
    @Test
    fun masterDataSurvivesANewInstance() {
        val file = Files.createTempDirectory("master").resolve("nested/master.json")
        assertEquals(MasterData(), DesktopMasterStore(file).load())
        val data = MasterData(notes = listOf(MasterNote("n1", "Сессия 1")))
        DesktopMasterStore(file).save(data)
        assertEquals(data, DesktopMasterStore(file).load())
        assertFalse(Files.exists(file.resolveSibling("master.json.tmp")))
    }
}
