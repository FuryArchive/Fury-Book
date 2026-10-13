package com.furybook.android.state

import com.furybook.dubl.acquisition.AcquisitionCapabilities
import com.furybook.dubl.acquisition.StoredCharacter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Actual Android min26/target/process-death runtime evidence is not available. No rollout toggle. */
internal object AndroidAcquisitionRuntimeGate {
    val production = AcquisitionCapabilities(localDurable = false, cloudLedgerV2 = false, formatWritable = true)
}

data class AndroidCommandScope(val storageIdentity: String, val characterId: String, val ownerId: String?,
    val controllerGeneration: Long, val selectionGeneration: Long, val accountGeneration: Long,
    val operationId: String, val expectedStorageRevision: Long, val viewCharacterId: String = characterId)
class StaleAndroidCommandScope : IllegalStateException("Character/controller/account scope changed")

/** Work and durable reload happen on IO. Publication has a final Main generation check. */
internal class AndroidAcquisitionQueue(
    private val current: (AndroidCommandScope) -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val commandCurrent: (AndroidCommandScope) -> Boolean = current,
) {
    private val serial = Mutex()
    /** Return durable acknowledgements before view reload; a view failure cannot erase a receipt. */
    suspend fun <T> execute(scope: AndroidCommandScope, work: suspend () -> T): T = serial.withLock {
        withContext(main) { if (!commandCurrent(scope)) throw StaleAndroidCommandScope() }
        withContext(io) { work() }
    }
    suspend fun <T> run(scope: AndroidCommandScope, work: suspend () -> T,
        acknowledged: (T) -> Boolean, reload: suspend () -> StoredCharacter,
        publish: (T, StoredCharacter?) -> Unit): T = serial.withLock {
        withContext(main) { if (!current(scope)) throw StaleAndroidCommandScope() }
        val pair = withContext(io) {
            val result = work()
            result to if (acknowledged(result)) reload() else null
        }
        withContext(main) {
            if (!current(scope)) throw StaleAndroidCommandScope()
            publish(pair.first, pair.second)
        }
        pair.first
    }
}
