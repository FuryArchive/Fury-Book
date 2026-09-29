package com.furybook.desktop.cloud

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.cloud.CloudBootstrap
import com.furybook.cloud.FuryNativeCloudApi
import com.furybook.cloud.NativeCloudStatus
import com.furybook.cloud.NativeCloudSyncCoordinator
import com.furybook.cloud.NativeSyncConflict
import com.furybook.cloud.NativeSyncOutcome
import com.furybook.cloud.ObservingCharacterExtrasStore
import com.furybook.cloud.ObservingCharacterStore
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DesktopCloudController(
    private val characterStore: CharacterStore,
    private val extrasStore: CharacterExtrasStore,
) {
    private val gateway = DesktopFuryCloudGateway()
    private val metadataStore = DesktopCloudMetadataStore()
    private val api = FuryNativeCloudApi(gateway)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var coordinator: NativeCloudSyncCoordinator? = null
    private var scheduledJob: Job? = null
    private var chiEnabled = false

    var session by mutableStateOf(gateway.currentSession())
        private set
    var status by mutableStateOf(if (session == null) NativeCloudStatus.SIGNED_OUT else NativeCloudStatus.CONNECTING)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var conflicts by mutableStateOf<List<NativeSyncConflict>>(emptyList())
        private set
    var nickname by mutableStateOf("")
        private set
    var lastSyncedAt by mutableStateOf<String?>(null)
        private set
    var lastSyncedBy by mutableStateOf<String?>(null)
        private set
    var reloadToken by mutableIntStateOf(0)
        private set

    init {
        rebuildCoordinator()
    }

    fun bind(
        characters: ObservingCharacterStore,
        extras: ObservingCharacterExtrasStore,
    ) {
        characters.onSaved = { before, after ->
            coordinator?.markSnapshotChanged(before, after)
            scheduleSync()
        }
        extras.onSaved = { characterId ->
            coordinator?.markExtrasChanged(characterId)
            scheduleSync()
        }
        extras.onDeleted = { scheduleSync() }
    }

    fun setChiEnabled(enabled: Boolean) {
        if (chiEnabled == enabled) return
        chiEnabled = enabled
        if (session != null) scheduleSync()
    }

    suspend fun restoreAndSync() {
        if (session == null) {
            status = NativeCloudStatus.SIGNED_OUT
            return
        }
        syncInternal(firstLinkAllowed = true)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        status = NativeCloudStatus.CONNECTING
        statusMessage = "Входим в Fury Account…"
        val signedIn = gateway.signIn(email, password).getOrThrow()
        session = signedIn
        conflicts = emptyList()
        rebuildCoordinator()
        syncInternal(firstLinkAllowed = true)
        Unit
    }.onFailure { error ->
        status = NativeCloudStatus.ERROR
        statusMessage = error.message ?: "Не удалось войти в Fury Account."
    }

    suspend fun signUp(email: String, password: String, nickname: String): Result<Boolean> = runCatching {
        status = NativeCloudStatus.CONNECTING
        statusMessage = "Создаём Fury Account…"
        val created = gateway.signUp(email, password, nickname).getOrThrow()
        if (created == null) {
            status = NativeCloudStatus.SIGNED_OUT
            statusMessage = "Аккаунт создан. Подтвердите email из письма, затем войдите."
            false
        } else {
            session = created
            conflicts = emptyList()
            rebuildCoordinator()
            syncInternal(firstLinkAllowed = true)
            true
        }
    }.onFailure { error ->
        status = NativeCloudStatus.ERROR
        statusMessage = error.message ?: "Не удалось создать Fury Account."
    }

    suspend fun signOut() {
        scheduledJob?.cancel()
        gateway.signOut()
        coordinator?.unlinkPreservingLocal()
        coordinator = null
        session = null
        conflicts = emptyList()
        nickname = ""
        lastSyncedAt = null
        lastSyncedBy = null
        status = NativeCloudStatus.SIGNED_OUT
        statusMessage = "Локальные персонажи сохранены на компьютере."
    }

    fun requestSync() {
        if (session != null) scheduleSync(delayMs = 0)
    }

    suspend fun useCloudVersion() {
        val active = coordinator ?: return
        status = NativeCloudStatus.SYNCING
        val before = localSignature()
        runCatching { active.useCloud(chiEnabled) }
            .onSuccess { outcome ->
                conflicts = emptyList()
                applyOutcome(outcome)
                if (before != localSignature()) reloadToken += 1
            }
            .onFailure(::handleSyncFailure)
    }

    suspend fun keepLocalVersion(conflict: NativeSyncConflict) {
        val active = coordinator ?: return
        status = NativeCloudStatus.SYNCING
        val before = localSignature()
        runCatching { active.keepLocal(conflict, chiEnabled) }
            .onSuccess { outcome ->
                applyOutcome(outcome)
                if (before != localSignature()) reloadToken += 1
            }
            .onFailure(::handleSyncFailure)
    }

    private fun scheduleSync(delayMs: Long = 900L) {
        if (session == null || conflicts.isNotEmpty()) return
        scheduledJob?.cancel()
        scheduledJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            syncInternal(firstLinkAllowed = false)
        }
    }

    private suspend fun syncInternal(firstLinkAllowed: Boolean) {
        val active = coordinator ?: return
        status = NativeCloudStatus.SYNCING
        statusMessage = if (firstLinkAllowed) {
            "Проверяем локальных персонажей и Fury Cloud…"
        } else {
            "Синхронизация…"
        }
        val before = localSignature()
        runCatching {
            if (firstLinkAllowed) active.linkOrResume(chiEnabled) else active.syncNow(chiEnabled)
        }.onSuccess { outcome ->
            applyOutcome(outcome)
            if (before != localSignature()) reloadToken += 1
        }.onFailure(::handleSyncFailure)
    }

    private fun applyOutcome(outcome: NativeSyncOutcome) {
        when (outcome) {
            is NativeSyncOutcome.Synced -> {
                conflicts = emptyList()
                updateBootstrap(outcome.bootstrap)
                status = NativeCloudStatus.SYNCED
                statusMessage = "Синхронизация с Fury Cloud успешна."
            }
            is NativeSyncOutcome.Conflicts -> {
                conflicts = outcome.items
                status = NativeCloudStatus.CONFLICT
                statusMessage = "Есть изменения с другого устройства. Ничего не перезаписано."
            }
        }
    }

    private fun updateBootstrap(bootstrap: CloudBootstrap) {
        nickname = bootstrap.nickname
        val newest = bootstrap.characters.maxByOrNull { it.updatedAt }
        lastSyncedAt = newest?.updatedAt
        lastSyncedBy = newest?.updatedBy
    }

    private fun handleSyncFailure(error: Throwable) {
        val text = error.message.orEmpty()
        val looksOffline = text.contains("connect", ignoreCase = true) ||
            text.contains("timeout", ignoreCase = true) ||
            text.contains("network", ignoreCase = true) ||
            text.contains("unreachable", ignoreCase = true)
        status = if (looksOffline) NativeCloudStatus.OFFLINE else NativeCloudStatus.ERROR
        statusMessage = if (looksOffline) {
            "Нет связи с Fury Cloud. Локальные изменения сохранены и будут отправлены позже."
        } else {
            text.ifBlank { "Ошибка синхронизации Fury Cloud." }
        }
    }

    private fun rebuildCoordinator() {
        val current = session ?: return
        coordinator = NativeCloudSyncCoordinator(
            userId = current.userId,
            characterStore = characterStore,
            extrasStore = extrasStore,
            metadataStore = metadataStore,
            api = api,
            idFactory = { UUID.randomUUID().toString() },
            deviceLabel = desktopCloudDeviceLabel(),
        )
    }

    private fun localSignature(): LocalSignature {
        val snapshot = characterStore.load()
        return LocalSignature(
            snapshot = snapshot,
            extras = snapshot.characters.associate { it.id to extrasStore.load(it.id) },
        )
    }

    private data class LocalSignature(
        val snapshot: AppSnapshot,
        val extras: Map<String, CharacterSheetExtras>,
    )
}
