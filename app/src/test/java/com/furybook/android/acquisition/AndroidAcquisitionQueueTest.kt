package com.furybook.android.acquisition

import com.furybook.android.state.*
import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Real host coroutine dispatchers test the queue contract; this is not Android Main/runtime proof. */
class AndroidAcquisitionQueueTest {
    private fun scope(character: String = "real", owner: String? = "owner", generation: Long = 0) =
        AndroidCommandScope("synthetic-local", character, owner, 0, generation, generation, "operation", 0)

    @Test fun acknowledgement_is_reloaded_on_io_before_main_publication() = runBlocking {
        Executors.newSingleThreadExecutor { Thread(it,"t13-host-io") }.asCoroutineDispatcher().use { io ->
            Executors.newSingleThreadExecutor { Thread(it,"t13-host-publication") }.asCoroutineDispatcher().use { main ->
                val queue=AndroidAcquisitionQueue({ true },io,main)
                val events=mutableListOf<String>()
                val result=queue.run(scope(),{ events+="work:${Thread.currentThread().name.substringBefore(" @coroutine")}";CommitResult.Committed },
                    { it==CommitResult.Committed },{ events+="reload:${Thread.currentThread().name.substringBefore(" @coroutine")}";t13State() }) { _,state ->
                    assertNotNull(state);events+="publish:${Thread.currentThread().name.substringBefore(" @coroutine")}"
                }
                assertEquals(CommitResult.Committed,result)
                assertEquals(listOf("work:t13-host-io","reload:t13-host-io","publish:t13-host-publication"),events)
            }
        }
    }

    @Test fun failure_never_reloads_or_publishes_unacknowledged_character() = runBlocking {
        var reloads=0;var published:StoredCharacter?=null
        val queue=AndroidAcquisitionQueue({ true },Dispatchers.Default,Dispatchers.Unconfined)
        val failure=CommitResult.StorageFailure("synthetic flush failure")
        assertEquals(failure,queue.run(scope(),{ failure },{ false },{ reloads++;t13State() }) { _,state -> published=state })
        assertEquals(0,reloads);assertNull(published)
    }

    @Test fun queued_scope_change_prevents_old_work() = runBlocking {
        val current=AtomicReference(scope());var work=0
        val queue=AndroidAcquisitionQueue({ it==current.get() },Dispatchers.Default,Dispatchers.Unconfined)
        val stale=current.get();current.set(scope("B"))
        try { queue.run(stale,{ work++;CommitResult.Committed },{ true },{ t13State() }) { _,_ -> fail("stale publication") };fail("stale result") }
        catch (_:StaleAndroidCommandScope) { }
        assertEquals(0,work)
    }

    @Test fun completed_A_cannot_publish_or_return_success_in_B_scope() = runBlocking {
        for(next in listOf(scope("B"),scope(owner="another"),scope(generation=1))) {
            val current=AtomicReference(scope());var publications=0
            val queue=AndroidAcquisitionQueue({ it==current.get() },Dispatchers.Default,Dispatchers.Unconfined)
            try {
                queue.run(current.get(),{ current.set(next);CommitResult.Committed },{ true },{ t13State() }) { _,_ -> publications++ }
                fail("A success escaped into a changed character/account/generation scope")
            } catch (_:StaleAndroidCommandScope) { }
            assertEquals(0,publications)
        }
    }

    @Test fun controller_disposal_before_callback_prevents_result_and_publication() = runBlocking {
        var alive=true;var published=false
        val queue=AndroidAcquisitionQueue({ alive },Dispatchers.Default,Dispatchers.Unconfined)
        try {
            queue.run(scope(),{ alive=false;CommitResult.Committed },{ true },{ t13State() }) { _,_ -> published=true }
            fail("disposed callback returned success")
        } catch (_:StaleAndroidCommandScope) { }
        assertFalse(published)
    }

    @Test fun durable_ack_with_failed_recovery_reload_never_publishes() = runBlocking {
        var published=false
        val queue=AndroidAcquisitionQueue({ true },Dispatchers.Default,Dispatchers.Unconfined)
        try {
            queue.run(scope(),{ CommitResult.Committed },{ true },{ error("recovery file force failed") }) { _,_ -> published=true }
            fail("uncertain recovery returned an acknowledged display")
        } catch(failure:IllegalStateException) { assertEquals("recovery file force failed",failure.message) }
        assertFalse(published)
    }

    @Test fun legacy_prewrite_gate_refuses_before_touching_character_or_extras() {
        val gate=com.furybook.android.data.AndroidLegacyWriteGate();var writes=0
        gate.protect()
        for(write in listOf({ writes++;"character" },{ writes++;"extras" })) {
            try { gate.legacyWrite(write);fail("protected writer ran") } catch(_:IllegalStateException) { }
        }
        assertEquals(0,writes)
    }

    @Test fun legacy_main_caller_does_not_block_behind_source_io_lock() {
        val gate=com.furybook.android.data.AndroidLegacyWriteGate()
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val thread=Thread { gate.captured { entered.countDown();check(release.await(10,java.util.concurrent.TimeUnit.SECONDS)) } }.apply { start() }
        try {
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS))
            try { gate.legacyWrite { fail("legacy write overlapped source capture") };fail("busy source gate did not refuse") } catch(_:IllegalStateException) { }
        } finally { release.countDown();thread.join(10000);assertFalse(thread.isAlive) }
    }

    @Test fun production_runtime_gate_cannot_enable_android_purchase_or_cloud_writer() {
        assertFalse(AndroidAcquisitionRuntimeGate.production.localDurable)
        assertFalse(AndroidAcquisitionRuntimeGate.production.cloudLedgerV2)
        assertTrue(AndroidAcquisitionRuntimeGate.production.formatWritable)
    }
}
