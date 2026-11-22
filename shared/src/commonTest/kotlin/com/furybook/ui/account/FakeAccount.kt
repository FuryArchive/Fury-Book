package com.furybook.ui.account

import com.furybook.core.cloud.CampaignDashboard
import com.furybook.core.cloud.CampaignInvite
import com.furybook.core.cloud.CampaignSummary

/** In-memory [FuryAccount] for UI-logic tests: records calls, no network. */
class FakeAccount(
    override var signedIn: Boolean = false,
    override var pendingChanges: Int = 0,
    override var campaigns: List<CampaignSummary> = emptyList(),
    var dashboards: Map<String, CampaignDashboard> = emptyMap(),
) : FuryAccount {
    val calls = mutableListOf<String>()
    override var email: String = ""
    override var nickname: String = ""
    override var status: FuryAccountStatus = FuryAccountStatus.SIGNED_OUT
    override val statusText: String get() = status.name
    override var conflicts: List<FuryAccountConflict> = emptyList()
    override val syncLog: List<FuryAccountLogEntry> = emptyList()

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        calls += "signIn:$email"; signedIn = true; this.email = email; status = FuryAccountStatus.SYNCED
        return Result.success(Unit)
    }
    override suspend fun signUp(email: String, password: String, nickname: String): Result<Boolean> { calls += "signUp:$email"; return Result.success(false) }
    override suspend fun requestPasswordReset(email: String): Result<Unit> { calls += "reset:$email"; return Result.success(Unit) }
    override suspend fun updateNickname(nickname: String): Result<String> { this.nickname = nickname; return Result.success(nickname) }
    override fun requestSync() { calls += "sync" }
    override suspend fun syncAndSignOut(): Result<Unit> { calls += "syncAndSignOut"; pendingChanges = 0; signedIn = false; return Result.success(Unit) }
    override suspend fun signOutDiscarding() { calls += "signOutDiscarding"; signedIn = false; status = FuryAccountStatus.SIGNED_OUT }
    override suspend fun keepLocal(characterId: String) { calls += "keepLocal:$characterId" }
    override suspend fun useCloud() { calls += "useCloud" }
    override suspend fun refreshCampaigns(): Result<Unit> { calls += "refreshCampaigns"; return Result.success(Unit) }
    override suspend fun createCampaign(name: String): Result<CampaignSummary> {
        val c = CampaignSummary("c${campaigns.size + 1}", name, true, null, 0, "now"); campaigns = campaigns + c; return Result.success(c)
    }
    override suspend fun createInvite(campaignId: String): Result<CampaignInvite> = Result.success(CampaignInvite(campaignId, "K7Q2-MX", "later"))
    override suspend fun joinCampaign(code: String, characterId: String): Result<CampaignSummary> {
        calls += "join:$code:$characterId"
        val c = CampaignSummary("j1", "Дорога на Лест", false, characterId, 3, "now"); campaigns = campaigns.filterNot { it.id == c.id } + c
        return Result.success(c)
    }
    override suspend fun leaveCampaign(campaignId: String): Result<Unit> { campaigns = campaigns.filterNot { it.id == campaignId }; return Result.success(Unit) }
    override suspend fun removeMember(campaignId: String, userId: String): Result<Unit> { calls += "remove:$userId"; return Result.success(Unit) }
    override suspend fun dashboard(campaignId: String): Result<CampaignDashboard> =
        dashboards[campaignId]?.let { Result.success(it) } ?: Result.failure(IllegalStateException("нет"))
}
