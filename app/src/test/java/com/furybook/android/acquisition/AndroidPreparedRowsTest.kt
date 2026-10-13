package com.furybook.android.acquisition

import com.furybook.android.ui.screens.prepareDevelopmentRows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AndroidPreparedRowsTest {
    @Test fun all_plan_owned_rows_are_prepared_off_caller_thread_and_reads_do_not_compute() = runBlocking<Unit> {
        val caller = Thread.currentThread()
        var calls = 0
        val prepared = prepareDevelopmentRows(listOf("all", "plan", "owned"), { it }) {
            assertNotSame(caller, Thread.currentThread())
            calls++
            "ready:$it"
        }
        repeat(4) { assertEquals("ready:owned", prepared.getValue("owned")) }
        assertEquals(3, calls)
        val changed = prepareDevelopmentRows(listOf("all"), { it }) { "other-character:$it" }
        assertEquals("other-character:all", changed.getValue("all"))
        assertFalse(changed.containsKey("owned"))
    }
}
