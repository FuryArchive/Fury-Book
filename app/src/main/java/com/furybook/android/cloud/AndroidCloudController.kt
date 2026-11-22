package com.furybook.android.cloud

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.core.cloud.CloudBootstrap
import com.furybook.core.cloud.CampaignDashboard
import com.furybook.core.cloud.CampaignInvite
import com.furybook.core.cloud.CampaignSummary
import com.furybook.core.cloud.FuryCampaignApi
import com.furybook.core.cloud.FuryNativeCloudApi
import com.furybook.core.cloud.NativeCloudStatus
import com.furybook.core.cloud.NativeCloudSyncCoordinator
import com.furybook.core.cloud.NativeSyncConflict
import com.furybook.core.cloud.NativeSyncOutcome
import com.furybook.core.cloud.ObservingCharacterExtrasStore
import com.furybook.core.cloud.ObservingCharacterStore
import com.furybook.ui.account.FuryAccount
import com.furybook.ui.account.FuryAccountConflict
import com.furybook.ui.account.FuryAccountLogEntry
import com.furybook.ui.account.FuryAccountStatus
import com.furybook.ui.account.accountStatusText
import com.furybook.ui.account.nativeAccountConflicts
import com.furybook.ui.account.setNativeNickname
import com.furybook.ui.account.toAccountStatus
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class AndroidCloudController(
    context: Context,
    private val characterStore: CharacterStore,
    private val extrasStore: CharacterExtrasStore,
) : FuryAccount {
    private val gateway = AndroidFuryCloudGateway(context)
    private val metadataStore = AndroidCloudMetadataStore(context)
    private val api = FuryNativeCloudApi(gateway)
    private val campaignApi = FuryCampaignApi(gateway) { UUID.randomUUID().toString() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var coordinator: NativeCloudSyncCoordinator? = null
    private var scheduledJob: Job? = null
    private var localEnabledPackIds: Set<String> = emptySet()

    var session by mutableStateOf(gateway.currentSession())
        private set
    var status_ by mutableStateOf(if (session == null) NativeCloudStatus.SIGNED_OUT else NativeCloudStatus.CONNECTING)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var nativeConflicts by mutableStateOf<List<NativeSyncConflict>>(emptyList())
        private set
    override var nickname by mutableStateOf("")
        private set
    var lastSyncedAt by mutableStateOf<String?>(null)
        private set
    var lastSyncedBy by mutableStateOf<String?>(null)
        private set
    var enabledPackIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var reloadToken by mutableIntStateOf(0)
        private set
    override var campaigns by mutableStateOf<List<CampaignSummary>>(emptyList())
        private set
    var campaignDashboard by mutableStateOf<CampaignDashboard?>(null)
        private set
    var campaignMessage by mutableStateOf<String?>(null)
        private set

    init {
        rebuildCoordinator()
    }

    // FuryAccount: the shared login screen, menu account panel, Персонажи and Мастер read the account through this.
    override val signedIn: Boolean get() = session != null
    override val email: String get() = session?.email.orEmpty()
    override val status: FuryAccountStatus get() = status_.toAccountStatus(pendingChanges)
    override val statusText: String get() = accountStatusText(status, statusMessage, pendingChanges)
    override val pendingChanges: Int get() = coordinator?.pendingCount() ?: 0
    override val conflicts: List<FuryAccountConflict> get() = nativeAccountConflicts(nativeConflicts, characterStore.load())
    override val syncLog: List<FuryAccountLogEntry> get() = emptyList()

    override suspend fun updateNickname(nickname: String): Result<String> = runCatching {
        setNativeNickname(gateway, nickname).also { this.nickname = it }
    }

    override suspend fun syncAndSignOut(): Result<Unit> = runCatching {
        syncInternal(firstLinkAllowed = false)
        check(nativeConflicts.isEmpty()) { "Сначала решите конфликт изменений." }
        check(status_ == NativeCloudStatus.SYNCED) { statusMessage ?: "Не удалось отправить изменения." }
        signOutDiscarding()
    }

    override suspend fun signOutDiscarding() {
        scheduledJob?.cancel()
        gateway.signOut()
        coordinator?.clearLocal()
        coordinator = null
        session = null
        nativeConflicts = emptyList()
        nickname = ""
        lastSyncedAt = null
        lastSyncedBy = null
        campaigns = emptyList()
        campaignDashboard = null
        campaignMessage = null
        status_ = NativeCloudStatus.SIGNED_OUT
        statusMessage = null
        reloadToken += 1
    }

    override suspend fun keepLocal(characterId: String) {
        nativeConflicts.firstOrNull { it.characterId == characterId }?.let { keepLocalVersion(it) }
    }

    override suspend fun useCloud() = useCloudVersion()
    override suspend fun createInvite(campaignId: String): Result<CampaignInvite> = createCampaignInvite(campaignId)
    override suspend fun removeMember(campaignId: String, userId: String): Result<Unit> = removeCampaignMember(campaignId, userId)
    override suspend fun dashboard(campaignId: String): Result<CampaignDashboard> = openGmDashboard(campaignId)

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
        extras.onDeleted = {
            scheduleSync()
        }
    }

    fun updateEnabledPackIds(ids: Set<String>) {
        val normalized = ids.filter(String::isNotBlank).toSet()
        if (localEnabledPackIds == normalized) return
        localEnabledPackIds = normalized
        enabledPackIds = normalized
        if (session != null) scheduleSync()
    }

    suspend fun restoreAndSync() {
        if (session == null) {
            status_ = NativeCloudStatus.SIGNED_OUT
            return
        }
        syncInternal(firstLinkAllowed = true)
        refreshCampaigns()
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        status_ = NativeCloudStatus.CONNECTING
        statusMessage = "Входим в Fury Account…"
        val signedIn = gateway.signIn(email, password).getOrThrow()
        session = signedIn
        nativeConflicts = emptyList()
        rebuildCoordinator()
        syncInternal(firstLinkAllowed = true)
        refreshCampaigns().getOrThrow()
        Unit
    }.onFailure { error ->
        status_ = NativeCloudStatus.ERROR
        statusMessage = error.message ?: "Не удалось войти в Fury Account."
    }

    override suspend fun signUp(email: String, password: String, nickname: String): Result<Boolean> = runCatching {
        status_ = NativeCloudStatus.CONNECTING
        statusMessage = "Создаём Fury Account…"
        val created = gateway.signUp(email, password, nickname).getOrThrow()
        if (created == null) {
            status_ = NativeCloudStatus.SIGNED_OUT
            statusMessage = "Аккаунт создан. Подтвердите email из письма, затем войдите."
            false
        } else {
            session = created
            nativeConflicts = emptyList()
            rebuildCoordinator()
            syncInternal(firstLinkAllowed = true)
            refreshCampaigns().getOrThrow()
            true
        }
    }.onFailure { error ->
        status_ = NativeCloudStatus.ERROR
        statusMessage = error.message ?: "Не удалось создать Fury Account."
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> = gateway.requestPasswordReset(email)

    suspend fun signOut() {
        scheduledJob?.cancel()
        gateway.signOut()
        coordinator?.unlinkPreservingLocal()
        coordinator = null
        session = null
        nativeConflicts = emptyList()
        nickname = ""
        lastSyncedAt = null
        lastSyncedBy = null
        campaigns = emptyList()
        campaignDashboard = null
        campaignMessage = null
        status_ = NativeCloudStatus.SIGNED_OUT
        statusMessage = "Локальные персонажи сохранены на устройстве."
    }

    override suspend fun refreshCampaigns(): Result<Unit> = runCatching {
        if (session == null) {
            campaigns = emptyList()
            campaignDashboard = null
            return@runCatching
        }
        campaigns = campaignApi.listCampaigns()
        val activeId = campaignDashboard?.id
        if (activeId != null && campaigns.none { it.id == activeId && it.isOwner }) {
            campaignDashboard = null
        }
        campaignMessage = null
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось загрузить кампании."
    }

    override suspend fun createCampaign(name: String): Result<CampaignSummary> = runCatching {
        val created = campaignApi.createCampaign(name.trim())
        campaigns = campaignApi.listCampaigns()
        campaignDashboard = campaignApi.getDashboard(created.id)
        campaignMessage = "Кампания создана."
        created
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось создать кампанию."
    }

    suspend fun createCampaignInvite(campaignId: String): Result<CampaignInvite> = runCatching {
        campaignApi.createInvite(campaignId)
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось создать приглашение."
    }

    override suspend fun joinCampaign(inviteCode: String, characterId: String): Result<CampaignSummary> = runCatching {
        val joined = campaignApi.joinCampaign(inviteCode.trim(), characterId)
        campaigns = campaignApi.listCampaigns()
        campaignMessage = "Персонаж добавлен в кампанию."
        joined
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось вступить в кампанию."
    }

    suspend fun setCampaignCharacter(campaignId: String, characterId: String): Result<Unit> = runCatching {
        campaignApi.setCharacter(campaignId, characterId)
        campaigns = campaignApi.listCampaigns()
        Unit
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось сменить персонажа кампании."
    }

    suspend fun openGmDashboard(campaignId: String): Result<CampaignDashboard> = runCatching {
        val summary = campaigns.firstOrNull { it.id == campaignId }
            ?: error("Кампания не найдена.")
        require(summary.isOwner) { "GM Screen доступен только владельцу кампании." }
        campaignApi.getDashboard(campaignId).also { campaignDashboard = it }
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось открыть GM Screen."
    }

    suspend fun refreshGmDashboard(): Result<CampaignDashboard> {
        val active = campaignDashboard ?: return Result.failure(IllegalStateException("GM Screen не открыт."))
        return openGmDashboard(active.id)
    }

    suspend fun removeCampaignMember(campaignId: String, userId: String): Result<Unit> = runCatching {
        campaignApi.removeMember(campaignId, userId)
        campaignDashboard = campaignApi.getDashboard(campaignId)
        campaigns = campaignApi.listCampaigns()
        Unit
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось удалить игрока из кампании."
    }

    override suspend fun leaveCampaign(campaignId: String): Result<Unit> = runCatching {
        campaignApi.leaveCampaign(campaignId)
        campaigns = campaignApi.listCampaigns()
        campaignDashboard = campaignDashboard?.takeUnless { it.id == campaignId }
        Unit
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось выйти из кампании."
    }

    override fun requestSync() {
        if (session != null) scheduleSync(delayMs = 0)
    }

    suspend fun useCloudVersion() {
        val active = coordinator ?: return
        status_ = NativeCloudStatus.SYNCING
        val before = localSignature()
        runCatching { active.useCloud(localEnabledPackIds) }
            .onSuccess { outcome ->
                nativeConflicts = emptyList()
                applyOutcome(outcome)
                if (before != localSignature()) reloadToken += 1
            }
            .onFailure(::handleSyncFailure)
    }

    suspend fun keepLocalVersion(conflict: NativeSyncConflict) {
        val active = coordinator ?: return
        status_ = NativeCloudStatus.SYNCING
        val before = localSignature()
        runCatching { active.keepLocal(conflict, localEnabledPackIds) }
            .onSuccess { outcome ->
                applyOutcome(outcome)
                if (before != localSignature()) reloadToken += 1
            }
            .onFailure(::handleSyncFailure)
    }

    private fun scheduleSync(delayMs: Long = 900L) {
        if (session == null || nativeConflicts.isNotEmpty()) return
        scheduledJob?.cancel()
        scheduledJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            syncInternal(firstLinkAllowed = false)
        }
    }

    private suspend fun syncInternal(firstLinkAllowed: Boolean) {
        val active = coordinator ?: return
        status_ = NativeCloudStatus.SYNCING
        statusMessage = if (firstLinkAllowed) {
            "Проверяем локальных персонажей и Fury Cloud…"
        } else {
            "Синхронизация…"
        }
        val before = localSignature()
        runCatching {
            if (firstLinkAllowed) active.linkOrResume(localEnabledPackIds) else active.syncNow(localEnabledPackIds)
        }.onSuccess { outcome ->
            applyOutcome(outcome)
            if (before != localSignature()) reloadToken += 1
        }.onFailure(::handleSyncFailure)
    }

    private fun applyOutcome(outcome: NativeSyncOutcome) {
        when (outcome) {
            is NativeSyncOutcome.Synced -> {
                nativeConflicts = emptyList()
                updateBootstrap(outcome.bootstrap)
                status_ = NativeCloudStatus.SYNCED
                statusMessage = "Синхронизация с Fury Cloud успешна."
            }
            is NativeSyncOutcome.Conflicts -> {
                nativeConflicts = outcome.items
                status_ = NativeCloudStatus.CONFLICT
                statusMessage = "Есть изменения с другого устройства. Ничего не перезаписано."
            }
        }
    }

    private fun updateBootstrap(bootstrap: CloudBootstrap) {
        nickname = bootstrap.nickname
        enabledPackIds = bootstrap.enabledPackIds
        val newest = bootstrap.characters.maxByOrNull { it.updatedAt }
        lastSyncedAt = newest?.updatedAt
        lastSyncedBy = newest?.updatedBy
    }

    private fun handleSyncFailure(error: Throwable) {
        val text = error.message.orEmpty()
        val looksOffline = text.contains("Unable to resolve host", ignoreCase = true) ||
            text.contains("failed to connect", ignoreCase = true) ||
            text.contains("timeout", ignoreCase = true) ||
            text.contains("network", ignoreCase = true)
        status_ = if (looksOffline) NativeCloudStatus.OFFLINE else NativeCloudStatus.ERROR
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
            deviceLabel = androidCloudDeviceLabel(),
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
