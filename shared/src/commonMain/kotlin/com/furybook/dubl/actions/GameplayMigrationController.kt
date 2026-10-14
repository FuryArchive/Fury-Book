package com.furybook.dubl.actions

import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GameplayMigrationReview internal constructor(val scope: GameplayScope, val request: GameplayMigrationRequest,
    val characterName: String, val endurance: Int)

/** Explicit local format review. No migration is triggered by loading or opening a screen. */
class GameplayMigrationController(private val store: AcquisitionStore, private val scope: () -> GameplayScope,
    private val isCurrent: (GameplayScope) -> Boolean, private val capabilities: () -> GameplayCapabilities,
    private val idFactory: () -> String, private val migrate: suspend (GameplayMigrationRequest) -> GameplayMigrationResult) {
    private val mutex = Mutex()
    private var retained: GameplayMigrationReview? = null
    private var attempted = false
    private var uncertain = false
    private var outcome: GameplayMigrationResult? = null
    val review get() = retained?.takeIf { it.scope == scope() && isCurrent(it.scope) }
    private fun current(captured: GameplayScope) {
        check(captured == scope() && isCurrent(captured)) { "Лист или аккаунт изменился" }
    }
    suspend fun prepare(): GameplayMigrationReview = mutex.withLock {
        check(!attempted) { "Сначала выясните исход прежнего перехода" }
        check(capabilities().writable) { "Локальные действия не включены" }
        val captured = scope(); current(captured)
        val source = (store.load(captured.characterId) as? LoadResult.Loaded)?.state ?: error("Исходный лист недоступен")
        current(captured)
        val canonical = encodeEnvelope(source)
        val command = GameplayMigrationRequest(idFactory(), captured.characterId, source.storageRevision, canonical)
        // Validate isolation, version, operation identity and revision without persisting anything.
        prepareGameplayMigration(source, canonical, command)
        GameplayMigrationReview(captured, command, source.character.name, source.character.enduranceCurrent).also {
            retained = it; outcome = null; uncertain = false
        }
    }
    suspend fun confirm(operationId: String): GameplayMigrationResult = mutex.withLock {
        val selected = checkNotNull(retained) { "Нет подготовленного перехода" }
        check(operationId == selected.request.operationId) { "Открыт другой переход" }; current(selected.scope)
        outcome?.takeIf { it is GameplayMigrationResult.Migrated || it is GameplayMigrationResult.AlreadyMigrated }?.let { return@withLock it }
        if (!capabilities().writable) return@withLock GameplayMigrationResult.Blocked("Локальные действия не включены")
        val wasUncertain = uncertain; attempted = true; uncertain = true
        val result = try { migrate(selected.request) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { GameplayMigrationResult.StorageFailure(failure.message ?: "Исход неизвестен; сохраните прежний ID") }
        outcome = result
        uncertain = when (result) {
            is GameplayMigrationResult.Migrated, is GameplayMigrationResult.AlreadyMigrated -> false
            is GameplayMigrationResult.Blocked -> wasUncertain
            is GameplayMigrationResult.StorageFailure -> true
        }
        current(selected.scope)
        result
    }
    suspend fun dismiss() = mutex.withLock {
        check(!uncertain) { "Исход перехода неизвестен; повторите прежнюю операцию" }
        retained = null; attempted = false; outcome = null
    }
}
