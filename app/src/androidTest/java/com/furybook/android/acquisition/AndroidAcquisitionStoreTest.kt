package com.furybook.android.acquisition

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.furybook.android.data.*
import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Genuine device sources. Assembly alone does not verify API26/target/process-death acceptance. */
@RunWith(AndroidJUnit4::class)
class AndroidAcquisitionStoreTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun checked_android_replace_reopen_and_same_id_replay()=runBlocking {
        val id=UUID.randomUUID().toString();val root=fixtureRoot(context.dataDir.toPath(),id);Files.createDirectories(root)
        try {
            val first=AndroidAcquisitionStore(root,AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")))
            assertEquals(CommitResult.Committed,first.compareAndSet("synthetic",-1,fixtureState()))
            assertTrue(fixtureService(first).apply(fixtureCommand()) is ApplyResult.Applied)
            val second=AndroidAcquisitionStore(root,AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")))
            assertTrue(fixtureService(second).apply(fixtureCommand()) is ApplyResult.AlreadyApplied)
            val state=(second.load("synthetic") as LoadResult.Loaded).state
            assertEquals(80L,balance(state.ledger).xp);assertEquals(1,state.receipts.size);assertEquals(1,state.outbox.size)
        } finally { deleteFixture(root) }
    }

    @Test fun separate_android_process_death_at_replace_boundaries_reopens_without_duplicate() {
        for(stage in listOf(AndroidAcquisitionWriteStage.BeforeReplace,AndroidAcquisitionWriteStage.AfterReplace,AndroidAcquisitionWriteStage.AfterDirectoryFlush)) {
            val id=UUID.randomUUID().toString();val root=fixtureRoot(context.dataDir.toPath(),id)
            try {
                val original=ProbeClient(context,id)
                val oldPid=original.call("initialize").getInt("pid")
                original.send("purchase",stage.name)
                assertTrue("Probe process did not die at $stage",original.death.await(20,TimeUnit.SECONDS));original.close()
                ProbeClient(context,id).use { fresh ->
                    val recovered=fresh.call("load");assertNotEquals(oldPid,recovered.getInt("pid"))
                    if(stage==AndroidAcquisitionWriteStage.BeforeReplace) { assertEquals(100L,recovered.getLong("xp"));assertEquals(0,recovered.getInt("receipts")) }
                    else { assertEquals(80L,recovered.getLong("xp"));assertEquals(1,recovered.getInt("receipts")) }
                    val replay=fresh.call("purchase")
                    assertEquals(if(stage==AndroidAcquisitionWriteStage.BeforeReplace) "Applied" else "AlreadyApplied",replay.getString("result"))
                    assertEquals(80L,replay.getLong("xp"));assertEquals(1,replay.getInt("receipts"));assertEquals(1,replay.getInt("outbox"))
                    fresh.send("stop");assertTrue(fresh.death.await(20,TimeUnit.SECONDS))
                }
            } finally { deleteFixture(root) }
        }
    }

    @Test fun independent_android_processes_use_authoritative_file_lock_cas()=runBlocking {
        val id=UUID.randomUUID().toString();val root=fixtureRoot(context.dataDir.toPath(),id)
        try {
            ProbeClient(context,id).use { probe ->
                probe.call("initialize")
                val local=AndroidAcquisitionStore(root,AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")))
                probe.send("cas")
                val localResult=local.compareAndSet("synthetic",0,fixtureState().copy(storageRevision=1))
                val remote=probe.receive().getString("result")
                assertEquals(1,listOf(localResult.toString(),remote).count { it==CommitResult.Committed.toString() })
                assertEquals(1L,(local.load("synthetic") as LoadResult.Loaded).state.storageRevision)
                probe.send("stop");assertTrue(probe.death.await(20,TimeUnit.SECONDS))
            }
        } finally { deleteFixture(root) }
    }
    private fun deleteFixture(root:java.nio.file.Path) { if(Files.exists(root)) Files.walk(root).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
}

private class ProbeClient(private val context:Context,private val fixture:String):AutoCloseable {
    val death=CountDownLatch(1)
    private val connected=CountDownLatch(1)
    private val replies=LinkedBlockingQueue<Bundle>()
    private val callbackThread=HandlerThread("t13-instrumentation-replies").apply { start() }
    private val receiver=Messenger(object:Handler(callbackThread.looper) { override fun handleMessage(msg:Message) { replies.put(Bundle(msg.data)) } })
    private lateinit var remote:Messenger
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) { binder.linkToDeath({ death.countDown() },0);remote=Messenger(binder);connected.countDown() }
        override fun onServiceDisconnected(name:ComponentName) { death.countDown() }
    }
    init { check(context.bindService(Intent(context,AndroidAcquisitionProcessProbeService::class.java),connection,Context.BIND_AUTO_CREATE));check(connected.await(20,TimeUnit.SECONDS)) }
    fun send(mode:String,fault:String?=null) { remote.send(Message.obtain().apply { replyTo=receiver;data=Bundle().apply { putString("fixture",fixture);putString("mode",mode);putString("fault",fault) } }) }
    fun receive():Bundle { val response=checkNotNull(replies.poll(20,TimeUnit.SECONDS));check(!response.containsKey("error")) { response.getString("error").orEmpty() };return response }
    fun call(mode:String):Bundle { send(mode);return receive() }
    override fun close() { context.unbindService(connection);callbackThread.quitSafely() }
}
