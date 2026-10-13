package com.furybook.android.cloud

import com.furybook.android.data.AndroidAcquisitionStore
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.dubl.acquisition.*
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
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.UUID

class AndroidCloudController(
    context: Context,
    private val characterStore: CharacterStore,
    private val extrasStore: CharacterExtrasStore,
    private val acquisitionStore: AcquisitionStore? = null,
) {
    private val gateway = AndroidFuryCloudGateway(context)
    private val metadataStore = AndroidCloudMetadataStore(context)
    private val api = FuryNativeCloudApi(gateway, acquisitionStore)
    private val campaignApi = FuryCampaignApi(gateway) { UUID.randomUUID().toString() }
    private val ownership = AndroidLegacyCloudOwnership((acquisitionStore as? AndroidAcquisitionStore)?.legacySource?.gate)
    private var legacyLease: AndroidLegacyCloudOwnership.Lease? = null
    private var coordinator: NativeCloudSyncCoordinator? = null
    private var scheduledJob: Job? = null
    private var observedCharacters: ObservingCharacterStore? = null
    private var observedExtras: ObservingCharacterExtrasStore? = null
    private var disposed = false
    var accountGeneration:Long = 0
        private set
    private var legacyAcquisitionPaused=false
    private var localEnabledPackIds: Set<String> = emptySet()

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
    var enabledPackIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var reloadToken by mutableIntStateOf(0)
        private set
    var campaigns by mutableStateOf<List<CampaignSummary>>(emptyList())
        private set
    var campaignDashboard by mutableStateOf<CampaignDashboard?>(null)
        private set
    var campaignMessage by mutableStateOf<String?>(null)
        private set

    init {
        rebuildCoordinator()
    }

    /** T17 supplies the durable store/archive; server guards remain a separate release gate. */
    fun createAcquisitionSync(store: AcquisitionStore, archive: AcquisitionConflictArchive, localDurable: Boolean): AcquisitionSyncProtocol {
        val capabilities=AcquisitionCapabilities(localDurable=localDurable, cloudLedgerV2=false, formatWritable=true)
        return AcquisitionSyncProtocol(store,api.acquisitionTransport(capabilities),archive,capabilities) { UUID.randomUUID().toString() }
    }

    fun bind(characters: ObservingCharacterStore, extras: ObservingCharacterExtrasStore) {
        if (disposed) return
        observedCharacters = characters
        observedExtras = extras
        characters.onSaved = { before, after ->
            val lease = legacyLease
            if (lease != null && ownership.isCurrent(lease)) {
                try { coordinator?.markSnapshotChanged(before, after); scheduleSync() }
                catch (_: StaleLegacyCloudOwnership) { }
            }
        }
        extras.onSaved = { characterId ->
            val lease = legacyLease
            if (lease != null && ownership.isCurrent(lease)) {
                try { coordinator?.markExtrasChanged(characterId); scheduleSync() }
                catch (_: StaleLegacyCloudOwnership) { }
            }
        }
        extras.onDeleted = { if (!disposed) scheduleSync() }
    }

    /** Composition ownership is separate from the CharacterController reload lifetime. */
    fun dispose() {
        if (disposed) return
        disposed = true
        ownership.dispose()
        accountGeneration = ownership.generation
        legacyLease = null
        coordinator = null
        scheduledJob?.cancel()
        scheduledJob = null
        observedCharacters?.onSaved = null
        observedExtras?.onSaved = null
        observedExtras?.onDeleted = null
        observedCharacters = null
        observedExtras = null
    }

    private fun invalidateLegacy() {
        if (disposed) return
        ownership.replace(null)
        accountGeneration = ownership.generation
        legacyLease = null
        coordinator = null
        scheduledJob?.cancel()
        scheduledJob = null
    }

    fun updateEnabledPackIds(ids: Set<String>) {
        val normalized = ids.filter(String::isNotBlank).toSet()
        if (localEnabledPackIds == normalized) return
        localEnabledPackIds = normalized
        enabledPackIds = normalized
        if (session != null) scheduleSync()
    }

    suspend fun restoreAndSync() {
        if (disposed) return
        if (session == null) {
            status = NativeCloudStatus.SIGNED_OUT
            return
        }
        syncInternal(firstLinkAllowed = true)
        if (!disposed) refreshCampaigns()
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        invalidateLegacy()
        status = NativeCloudStatus.CONNECTING
        statusMessage = "Входим в Fury Account…"
        val signedIn = gateway.signIn(email, password).getOrThrow()
        session = signedIn
        conflicts = emptyList()
        rebuildCoordinator()
        syncInternal(firstLinkAllowed = true)
        refreshCampaigns().getOrThrow()
        Unit
    }.onFailure { error ->
        if (!disposed && legacyLease == null && session != null) rebuildCoordinator()
        if (!disposed) {
            status = NativeCloudStatus.ERROR
            statusMessage = error.message ?: "Не удалось войти в Fury Account."
        }
    }

    suspend fun signUp(email: String, password: String, nickname: String): Result<Boolean> = runCatching {
        invalidateLegacy()
        status = NativeCloudStatus.CONNECTING
        statusMessage = "Создаём Fury Account…"
        val created = gateway.signUp(email, password, nickname).getOrThrow()
        if (created == null) {
            if (session != null) rebuildCoordinator()
            status = NativeCloudStatus.SIGNED_OUT
            statusMessage = "Аккаунт создан. Подтвердите email из письма, затем войдите."
            false
        } else {
            session = created
            conflicts = emptyList()
            rebuildCoordinator()
            syncInternal(firstLinkAllowed = true)
            refreshCampaigns().getOrThrow()
            true
        }
    }.onFailure { error ->
        if (!disposed && legacyLease == null && session != null) rebuildCoordinator()
        if (!disposed) {
            status = NativeCloudStatus.ERROR
            statusMessage = error.message ?: "Не удалось создать Fury Account."
        }
    }

    suspend fun signOut() {
        if (disposed) return
        invalidateLegacy()
        val transitionGeneration = accountGeneration
        gateway.signOut()
        ownership.transitionEffect(transitionGeneration) {
            metadataStore.clear()
            coordinator = null
            session = null
            conflicts = emptyList()
            nickname = ""
            lastSyncedAt = null
            lastSyncedBy = null
            campaigns = emptyList()
            campaignDashboard = null
            campaignMessage = null
            status = NativeCloudStatus.SIGNED_OUT
            statusMessage = "Локальные персонажи сохранены на устройстве."
        }
    }

    suspend fun refreshCampaigns(): Result<Unit> = runCatching {
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

    suspend fun createCampaign(name: String): Result<CampaignSummary> = runCatching {
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

    suspend fun joinCampaign(inviteCode: String, characterId: String): Result<CampaignSummary> = runCatching {
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

    suspend fun leaveCampaign(campaignId: String): Result<Unit> = runCatching {
        campaignApi.leaveCampaign(campaignId)
        campaigns = campaignApi.listCampaigns()
        campaignDashboard = campaignDashboard?.takeUnless { it.id == campaignId }
        Unit
    }.onFailure { error ->
        campaignMessage = error.message ?: "Не удалось выйти из кампании."
    }

    fun requestSync() {
        if (session != null && !legacyAcquisitionPaused) scheduleSync(delayMs = 0)
    }

    suspend fun useCloudVersion() {
        val lease = legacyLease ?: return
        if (disposed || !ownership.isCurrent(lease) || pauseLegacyAcquisition(lease)) return
        val active = coordinator ?: return
        if (!ownership.isCurrent(lease)) return
        status = NativeCloudStatus.SYNCING
        val before = localSignature()
        val generation=accountGeneration;val owner=session?.userId;val packs=localEnabledPackIds.toSet()
        runCatching { ownership.run(lease) { active.useCloud(packs) } }
            .onSuccess { outcome ->
                if(!ownership.isCurrent(lease) || generation!=accountGeneration || owner!=session?.userId) return@onSuccess
                conflicts = emptyList()
                applyOutcome(outcome)
                if (before != localSignature()) reloadToken += 1
            }
            .onFailure { if(ownership.isCurrent(lease) && generation==accountGeneration && owner==session?.userId) handleSyncFailure(it) }
    }

    suspend fun keepLocalVersion(conflict: NativeSyncConflict) {
        val lease = legacyLease ?: return
        if (disposed || !ownership.isCurrent(lease) || pauseLegacyAcquisition(lease)) return
        val active = coordinator ?: return
        if (!ownership.isCurrent(lease)) return
        status = NativeCloudStatus.SYNCING
        val before = localSignature()
        val generation=accountGeneration;val owner=session?.userId;val packs=localEnabledPackIds.toSet()
        runCatching { ownership.run(lease) { active.keepLocal(conflict,packs) } }
            .onSuccess { outcome ->
                if(!ownership.isCurrent(lease) || generation!=accountGeneration || owner!=session?.userId) return@onSuccess
                applyOutcome(outcome)
                if (before != localSignature()) reloadToken += 1
            }
            .onFailure { if(ownership.isCurrent(lease) && generation==accountGeneration && owner==session?.userId) handleSyncFailure(it) }
    }

    private fun scheduleSync(delayMs: Long = 900L) {
        if (disposed || session == null || conflicts.isNotEmpty()) return
        val lease = legacyLease ?: return
        if (!ownership.isCurrent(lease)) return
        scheduledJob?.cancel()
        scheduledJob = try { ownership.schedule(lease, delayMs, Dispatchers.Main.immediate) { syncInternal(firstLinkAllowed = false, expectedLease = lease) } }
        catch (_: StaleLegacyCloudOwnership) { null }
    }

    private suspend fun syncInternal(firstLinkAllowed: Boolean, expectedLease: AndroidLegacyCloudOwnership.Lease? = legacyLease) {
        val lease = expectedLease ?: return
        if (disposed || !ownership.isCurrent(lease) || pauseLegacyAcquisition(lease)) return
        if (!ownership.isCurrent(lease)) return
        val generation=accountGeneration
        val owner=session?.userId
        val active = coordinator ?: return
        status = NativeCloudStatus.SYNCING
        statusMessage = if (firstLinkAllowed) {
            "Проверяем локальных персонажей и Fury Cloud…"
        } else {
            "Синхронизация…"
        }
        val before = localSignature()
        val packs=localEnabledPackIds.toSet()
        runCatching {
            ownership.run(lease) { if (firstLinkAllowed) active.linkOrResume(packs) else active.syncNow(packs) }
        }.onSuccess { outcome ->
            if(!ownership.isCurrent(lease) || generation!=accountGeneration || owner!=session?.userId) return@onSuccess
            applyOutcome(outcome)
            if (before != localSignature()) reloadToken += 1
        }.onFailure { if(ownership.isCurrent(lease) && generation==accountGeneration && owner==session?.userId) handleSyncFailure(it) }
    }

    /** Guard precedes coordinator/API/bootstrap writes; passing a store alone is insufficient. */
    private suspend fun pauseLegacyAcquisition(lease: AndroidLegacyCloudOwnership.Lease):Boolean {
        val store=acquisitionStore as? AndroidAcquisitionStore ?: return false
        val protected=try { store.hasProtectedLocalData() }
        catch (cancellation: CancellationException) { throw cancellation }
        catch (_: Exception) { true }
        if(protected && !disposed && accountGeneration == lease.generation && session?.userId == lease.ownerId) {
            legacyAcquisitionPaused=true
            status=NativeCloudStatus.ERROR
            statusMessage="Локальный журнал сохранён отдельно. Старая облачная запись приостановлена; формат журнала не передаётся другому аккаунту."
        }
        return protected
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
        status = if (looksOffline) NativeCloudStatus.OFFLINE else NativeCloudStatus.ERROR
        statusMessage = if (looksOffline) {
            "Нет связи с Fury Cloud. Локальные изменения сохранены и будут отправлены позже."
        } else {
            text.ifBlank { "Ошибка синхронизации Fury Cloud." }
        }
    }

    private fun rebuildCoordinator() {
        if (disposed) return
        val current = session ?: return
        val lease = ownership.replace(current.userId)
        legacyLease = lease
        accountGeneration = ownership.generation
        coordinator = ownership.coordinator(checkNotNull(lease), characterStore, extrasStore, metadataStore,
            gateway, acquisitionStore, androidCloudDeviceLabel()) { UUID.randomUUID().toString() }
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
