package com.furybook.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.core.cloud.CampaignDashboard
import com.furybook.core.cloud.CampaignInvite
import com.furybook.core.cloud.CampaignSummary
import com.furybook.core.cloud.FuryCampaignApi
import com.furybook.ui.account.FuryAccount
import com.furybook.ui.account.FuryAccountConflict
import com.furybook.ui.account.FuryAccountLogEntry
import com.furybook.ui.account.FuryAccountStatus
import com.furybook.ui.account.accountStatusText

/**
 * Web's [FuryAccount]: the Supabase client plus the session [WebRoot] keeps. [onSignedIn] loads the account
 * (and merges guest characters); [onSignedOut] empties the browser's copy.
 */
class WebAccount(
    val cloud: WebCloudSync,
    private val onSignedIn: suspend (WebAuthSession) -> Unit,
    private val onSignedOut: suspend () -> Unit,
    private val onUseCloud: suspend () -> Unit,
) : FuryAccount {
    var session: WebAuthSession? by mutableStateOf(null)
    private val campaignApi = FuryCampaignApi(cloud) { webUuid() }
    private var campaignList by mutableStateOf<List<CampaignSummary>>(emptyList())

    override val signedIn: Boolean get() = session != null
    override val email: String get() = session?.email.orEmpty()
    override val nickname: String get() = cloud.profileNickname
    override val status: FuryAccountStatus get() = when {
        session == null -> FuryAccountStatus.SIGNED_OUT
        cloud.conflict != null -> FuryAccountStatus.CONFLICT
        else -> when (cloud.status.phase) {
            CloudSyncPhase.CONNECTING -> FuryAccountStatus.CONNECTING
            CloudSyncPhase.PENDING -> FuryAccountStatus.PENDING
            CloudSyncPhase.SYNCING -> FuryAccountStatus.SYNCING
            CloudSyncPhase.SYNCED -> FuryAccountStatus.SYNCED
            CloudSyncPhase.ERROR -> FuryAccountStatus.ERROR
            CloudSyncPhase.CONFLICT -> FuryAccountStatus.CONFLICT
        }
    }
    override val statusText: String get() = accountStatusText(status, cloud.status.message, pendingChanges)
    /** Web syncs one batch at a time, so it knows only whether something waits, not how many. */
    override val pendingChanges: Int get() = if (cloud.status.phase == CloudSyncPhase.PENDING || cloud.status.phase == CloudSyncPhase.ERROR) 1 else 0
    override val conflicts: List<FuryAccountConflict> get() = listOfNotNull(cloud.conflict?.let { FuryAccountConflict(it.characterId, it.characterName) })
    override val syncLog: List<FuryAccountLogEntry> get() = cloud.status.history.map { FuryAccountLogEntry(it.changedAt, "${it.characterName} · ${it.action}") }
    override val campaigns: List<CampaignSummary> get() = campaignList

    // The session is published only after [onSignedIn] loaded the account, so the app never shows the guest's
    // working copy as if it were the account's.
    override suspend fun signIn(email: String, password: String): Result<Unit> = cloud.signIn(email, password).mapCatching { authenticated ->
        onSignedIn(authenticated)
        session = authenticated
    }

    override suspend fun signUp(email: String, password: String, nickname: String): Result<Boolean> = cloud.signUp(email, password, nickname).mapCatching { created ->
        if (created == null) return@mapCatching false
        onSignedIn(created)
        session = created
        true
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> = cloud.requestPasswordReset(email)
    override suspend fun updateNickname(nickname: String): Result<String> = cloud.updateNickname(nickname)
    override fun requestSync() { /* web syncs every change on its own; «Синхронизировать» retries the last batch */ }

    override suspend fun syncAndSignOut(): Result<Unit> = runCatching {
        if (pendingChanges > 0) cloud.retryLast().getOrThrow()
        check(cloud.conflict == null) { "Сначала решите конфликт изменений." }
        signOutDiscarding()
    }

    override suspend fun signOutDiscarding() {
        cloud.signOut()
        session = null
        campaignList = emptyList()
        onSignedOut()
    }

    override suspend fun keepLocal(characterId: String) { cloud.forceConflict() }
    override suspend fun useCloud() { cloud.clearConflictForReload(); onUseCloud() }

    override suspend fun refreshCampaigns(): Result<Unit> = runCatching { campaignList = campaignApi.listCampaigns() }
    override suspend fun createCampaign(name: String): Result<CampaignSummary> = runCatching {
        campaignApi.createCampaign(name.trim()).also { refreshCampaigns() }
    }
    override suspend fun createInvite(campaignId: String): Result<CampaignInvite> = runCatching { campaignApi.createInvite(campaignId) }
    override suspend fun joinCampaign(code: String, characterId: String): Result<CampaignSummary> = runCatching {
        campaignApi.joinCampaign(code.trim(), characterId).also { refreshCampaigns() }
    }
    override suspend fun leaveCampaign(campaignId: String): Result<Unit> = runCatching { campaignApi.leaveCampaign(campaignId); refreshCampaigns().getOrThrow() }
    override suspend fun removeMember(campaignId: String, userId: String): Result<Unit> = runCatching { campaignApi.removeMember(campaignId, userId) }
    override suspend fun dashboard(campaignId: String): Result<CampaignDashboard> = runCatching { campaignApi.getDashboard(campaignId) }
}
