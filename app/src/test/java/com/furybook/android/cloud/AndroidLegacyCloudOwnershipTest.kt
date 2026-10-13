package com.furybook.android.cloud

import com.furybook.android.data.AndroidLegacyWriteGate
import com.furybook.core.cloud.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Actual consumed ownership helper + Shared coordinator, host stores/coroutines; no Android proof. */
class AndroidLegacyCloudOwnershipTest {
    @Test fun held_direct_bootstrap_cannot_persist_after_owner_transition_disposal_or_source_protection() = runBlocking {
        for (transition in listOf("sign-out", "B", "dispose", "source")) {
            val gate = AndroidLegacyWriteGate()
            val ownership = AndroidLegacyCloudOwnership(gate)
            val lease = checkNotNull(ownership.replace("A"))
            val stores = Stores()
            val held = HeldTransport()
            val operation = async(Dispatchers.Default) { runCatching { ownership.run(lease) { owned ->
                ownership.coordinator(owned, stores.characters, stores.extras, stores.metadata, held, null, "synthetic") { "new" }.useCloud(emptySet())
            } } }
            val response = held.waiting.await()
            when (transition) {
                "sign-out" -> ownership.replace(null)
                "B" -> ownership.replace("B")
                "dispose" -> ownership.dispose()
                else -> gate.protect()
            }
            response.resume(bootstrap())
            operation.await()
            assertEquals("stale $transition character save", 0, stores.characterWrites)
            assertEquals("stale $transition extras save/delete", 0, stores.extrasWrites)
            assertEquals("stale $transition clean metadata", 0, stores.metadataWrites)
            assertEquals(stores.initial, stores.snapshot)
            ownership.dispose()
        }
    }

    @Test fun held_link_bootstrap_cannot_start_continuation_rpc_after_B_transition() = runBlocking {
        val ownership = AndroidLegacyCloudOwnership()
        val lease = checkNotNull(ownership.replace("A"))
        val stores = Stores()
        val held = HeldTransport()
        val operation = async(Dispatchers.Default) { runCatching { ownership.run(lease) { owned ->
            ownership.coordinator(owned, stores.characters, stores.extras, stores.metadata, held, null, "synthetic") { "new" }.linkOrResume(emptySet())
        } } }
        val response = held.waiting.await()
        ownership.replace("B")
        response.resume(bootstrap())
        operation.await()
        assertEquals(listOf("get_web_bootstrap"), held.functions)
        assertEquals(0, stores.characterWrites + stores.extrasWrites + stores.metadataWrites)
        ownership.dispose()
    }

    @Test fun current_owner_link_continues_and_session_admission_refuses_B_or_stale_refresh() = runBlocking {
        val ownership = AndroidLegacyCloudOwnership()
        val lease = checkNotNull(ownership.replace("A"))
        val stores = Stores()
        val held = HeldTransport()
        val operation = async(Dispatchers.Default) { ownership.run(lease) { owned ->
            ownership.coordinator(owned, stores.characters, stores.extras, stores.metadata, held, null, "synthetic") { "new" }.linkOrResume(emptySet())
        } }
        held.waiting.await().resume("""{"nickname":"A","activeCharacterId":"","enabledPackIds":[],"stateRevision":0,"characters":[]}""")
        operation.await()
        assertEquals(listOf("get_web_bootstrap", "native_link_device"), held.functions)
        assertEquals(1, stores.characterWrites)
        assertTrue(stores.extrasWrites > 0)
        assertEquals("A", stores.linkedUserId)

        var sessionOwner = "B"
        var effects = 0
        assertTrue(runCatching { ownership.sessionBoundary(lease, { sessionOwner == lease.ownerId }) { effects++ } }.exceptionOrNull() is StaleLegacyCloudOwnership)
        assertEquals(0, effects)
        sessionOwner = "A"
        ownership.sessionBoundary(lease, { sessionOwner == lease.ownerId }) { effects++ }
        assertEquals(1, effects)
        ownership.replace("B")
        assertTrue(runCatching { ownership.sessionBoundary(lease, { sessionOwner == lease.ownerId }) { effects++ } }.exceptionOrNull() is StaleLegacyCloudOwnership)
        assertEquals(1, effects)
        ownership.dispose()
    }

    @Test fun disposal_cancels_delayed_and_active_children_and_caller_cancellation_cannot_orphan() = runBlocking {
        val ownership = AndroidLegacyCloudOwnership()
        val lease = checkNotNull(ownership.replace("A"))
        val stores = Stores()
        val held = HeldTransport()
        val scheduled = ownership.schedule(lease, 200) { ownership.run(lease) { owned ->
            ownership.coordinator(owned, stores.characters, stores.extras, stores.metadata, held, null, "synthetic") { "new" }.useCloud(emptySet())
        } }
        val direct = async(Dispatchers.Default) { runCatching { ownership.run(lease) { owned ->
            ownership.coordinator(owned, stores.characters, stores.extras, stores.metadata, held, null, "synthetic") { "new" }.useCloud(emptySet())
        } } }
        held.waiting.await()
        ownership.dispose()
        ownership.dispose()
        held.waiting.await().resume(bootstrap())
        direct.await()
        scheduled.join()
        assertTrue(scheduled.isCancelled)
        assertEquals(listOf("get_web_bootstrap"), held.functions)
        assertEquals(0, stores.characterWrites + stores.extrasWrites + stores.metadataWrites)
        assertTrue(runCatching { ownership.schedule(lease, 0) { } }.exceptionOrNull() is StaleLegacyCloudOwnership)
        assertTrue(runCatching { ownership.run(lease) { owned ->
            ownership.coordinator(owned, stores.characters, stores.extras, stores.metadata, held, null, "synthetic") { "new" }.useCloud(emptySet())
        } }.exceptionOrNull() is StaleLegacyCloudOwnership)

        val second = AndroidLegacyCloudOwnership()
        val secondLease = checkNotNull(second.replace("A"))
        val secondStores = Stores()
        val secondHeld = HeldTransport()
        val childReady = CompletableDeferred<Job>()
        val operationReady = CompletableDeferred<AndroidLegacyCloudOwnership.Lease>()
        val caller = launch(Dispatchers.Default) {
            second.run(secondLease) { owned ->
                childReady.complete(currentCoroutineContext()[Job]!!)
                operationReady.complete(owned)
                second.coordinator(owned, secondStores.characters, secondStores.extras, secondStores.metadata, secondHeld, null, "synthetic") { "new" }.linkOrResume(emptySet())
            }
        }
        val response = secondHeld.waiting.await()
        val actualChild = childReady.await()
        val operationLease = operationReady.await()
        assertSame(actualChild, operationLease.job)
        caller.cancelAndJoin()
        assertTrue(actualChild.isCancelled)
        response.resume("""{"nickname":"A","activeCharacterId":"","enabledPackIds":[],"stateRevision":0,"characters":[]}""")
        actualChild.join()
        var sessionEffects = 0
        runCatching { second.sessionBoundary(operationLease, { true }) { sessionEffects++ } }
        assertEquals("no late RPC, write or session effect after owned child cancellation",
            Triple(listOf("get_web_bootstrap"), 0, 0),
            Triple(secondHeld.functions.toList(), secondStores.characterWrites + secondStores.extrasWrites + secondStores.metadataWrites, sessionEffects))
        second.dispose()
    }

    private class HeldTransport : FuryCloudTransport {
        val waiting = CompletableDeferred<Continuation<String>>()
        val functions = mutableListOf<String>()
        override suspend fun rpc(function: String, jsonBody: String): String {
            functions += function
            return if (functions.size == 1) suspendCoroutine { waiting.complete(it) } else bootstrap()
        }
    }

    private class Stores {
        val initial = AppSnapshot(listOf(DublCharacter("local", name = "Keep local")), "local")
        var snapshot = initial
        var characterWrites = 0
        var extrasWrites = 0
        var metadataWrites = 0
        private var meta = NativeCloudMetadata()
        val linkedUserId get() = meta.linkedUserId
        val characters = object : CharacterStore {
            override fun load() = snapshot
            override fun save(snapshot: AppSnapshot) { characterWrites++; this@Stores.snapshot = snapshot }
        }
        val extras = object : CharacterExtrasStore {
            override fun load(characterId: String) = CharacterSheetExtras(notes = "Keep extras")
            override fun save(characterId: String, extras: CharacterSheetExtras) { extrasWrites++ }
            override fun delete(characterId: String) { extrasWrites++ }
        }
        val metadata = object : NativeCloudMetadataStore {
            override fun load() = meta
            override fun save(metadata: NativeCloudMetadata) { metadataWrites++; meta = metadata }
            override fun clear() { metadataWrites++; meta = NativeCloudMetadata() }
        }
    }
    companion object {
        private fun bootstrap(): String {
            val raw = SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("remote", name = "A remote")), "remote"))
            // Exact supported bootstrap shape from Shared's public transport contract.
            val character = raw.substringAfter("\"characters\":[").substringBeforeLast(']')
            return """{"nickname":"A","activeCharacterId":"remote","enabledPackIds":[],"stateRevision":1,"characters":[{"id":"remote","data":$character,"extras":{"notes":"A extras"},"revision":1,"updatedAt":"synthetic","updatedBy":"A"}]}"""
        }
    }
}
