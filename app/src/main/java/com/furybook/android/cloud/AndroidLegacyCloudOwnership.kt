package com.furybook.android.cloud

import com.furybook.android.data.AndroidLegacyWriteGate
import com.furybook.core.cloud.*
import com.furybook.dubl.acquisition.AcquisitionStore
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras
import kotlinx.coroutines.*

/** The immutable owner of one legacy cloud coordinator and all of its continuations. */
internal class AndroidLegacyCloudOwnership(
    private val sourceGate: AndroidLegacyWriteGate? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    internal class Lease internal constructor(
        val ownerId: String, val generation: Long, internal val job: Job,
        internal val account: Lease? = null,
    )
    private val lock = Any()
    private val root = SupervisorJob()
    private var current: Lease? = null
    private var disposed = false
    var generation: Long = 0
        private set

    fun replace(ownerId: String?): Lease? {
        val previous: Lease?
        val next: Lease?
        synchronized(lock) {
            check(!disposed) { "Cloud controller is disposed" }
            previous = current
            generation = Math.addExact(generation, 1)
            next = ownerId?.let { Lease(it, generation, SupervisorJob(root)) }
            current = next
        }
        previous?.job?.cancel()
        return next
    }

    fun isCurrent(lease: Lease): Boolean = synchronized(lock) {
        val account = lease.account ?: lease
        !disposed && current === account && account.job.isActive && lease.job.isActive &&
            lease.ownerId == account.ownerId && lease.generation == account.generation &&
            sourceGate?.readOnly != true
    }

    fun check(lease: Lease) {
        if (!isCurrent(lease)) throw StaleLegacyCloudOwnership()
    }

    /** A short linearization boundary. The action must be an already-warmed local memory effect. */
    fun <T> effect(lease: Lease, action: () -> T): T = synchronized(lock) {
        check(lease)
        if (sourceGate != null) sourceGate.legacyWrite(action) else action()
    }

    /** Admission/session update is atomic with invalidation; never enclose a request here. */
    fun <T> sessionBoundary(lease: Lease, matches: () -> Boolean, action: () -> T): T = synchronized(lock) {
        check(lease)
        if (!matches()) throw StaleLegacyCloudOwnership()
        action()
    }

    /** Sign-out's raw metadata unlink is allowed only while its invalidated transition still owns the gap. */
    fun transitionEffect(expectedGeneration: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (disposed || generation != expectedGeneration || current != null) return@synchronized false
        action()
        true
    }

    suspend fun <T> run(lease: Lease, action: suspend (Lease) -> T): T {
        currentCoroutineContext().ensureActive()
        val child = synchronized(lock) {
            check(lease)
            if (lease.account != null) throw StaleLegacyCloudOwnership()
            CoroutineScope(lease.job + io).async(start = CoroutineStart.LAZY) {
                val operation = Lease(lease.ownerId, lease.generation, currentCoroutineContext()[Job]!!, lease)
                check(operation)
                val value = action(operation)
                check(operation)
                value
            }
        }
        try { child.start(); return child.await() }
        finally {
            // Cancellation and local-effect admission use the same short boundary.
            synchronized(lock) { if (!child.isCompleted) child.cancel() }
        }
    }

    fun schedule(lease: Lease, delayMillis: Long, dispatcher: CoroutineDispatcher = io, action: suspend () -> Unit): Job = synchronized(lock) {
        check(lease)
        CoroutineScope(lease.job + dispatcher).launch {
            delay(delayMillis)
            check(lease)
            action()
        }
    }

    fun dispose() {
        synchronized(lock) {
            if (disposed) return
            disposed = true
            current = null
            generation = Math.addExact(generation, 1)
        }
        root.cancel()
    }

    fun coordinator(lease: Lease, characters: CharacterStore, extrasStore: CharacterExtrasStore,
        metadataStore: NativeCloudMetadataStore, transport: FuryCloudTransport, acquisition: AcquisitionStore?,
        device: String, idFactory: () -> String): NativeCloudSyncCoordinator {
        val scopedTransport = if (transport is AndroidFuryCloudGateway) transport.scopedLegacyTransport(this, lease) else transport
        val boundTransport = object : FuryCloudTransport {
            override suspend fun rpc(function: String, jsonBody: String): String {
                check(lease)
                val result = scopedTransport.rpc(function, jsonBody)
                check(lease)
                return result
            }
        }
        val boundCharacters = object : CharacterStore {
            override fun load(): AppSnapshot {
                check(lease)
                val result = characters.load()
                check(lease)
                return result
            }
            override fun save(snapshot: AppSnapshot) { effect(lease) { characters.save(snapshot) } }
        }
        val boundExtras = object : CharacterExtrasStore {
            override fun load(characterId: String): CharacterSheetExtras {
                check(lease)
                val result = extrasStore.load(characterId)
                check(lease)
                return result
            }
            override fun save(characterId: String, extras: CharacterSheetExtras) { effect(lease) { extrasStore.save(characterId, extras) } }
            override fun delete(characterId: String) { effect(lease) { extrasStore.delete(characterId) } }
        }
        val boundMetadata = object : NativeCloudMetadataStore {
            override fun load(): NativeCloudMetadata {
                check(lease)
                val result = metadataStore.load()
                check(lease)
                return result
            }
            override fun save(metadata: NativeCloudMetadata) { effect(lease) { metadataStore.save(metadata) } }
            override fun clear() { effect(lease) { metadataStore.clear() } }
        }
        return NativeCloudSyncCoordinator(lease.ownerId, boundCharacters, boundExtras, boundMetadata,
            FuryNativeCloudApi(boundTransport, acquisition), idFactory, device, acquisition)
    }

}

internal class StaleLegacyCloudOwnership : CancellationException("Legacy cloud owner, source or controller lifetime changed")
