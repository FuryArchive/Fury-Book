package com.furybook.ui.account

import com.furybook.core.cloud.CampaignDashboard
import com.furybook.core.cloud.CampaignInvite
import com.furybook.core.cloud.CampaignSummary

enum class FuryAccountStatus { SIGNED_OUT, CONNECTING, SYNCING, SYNCED, PENDING, OFFLINE, CONFLICT, ERROR }

/** A character changed on two devices; [deleted] when this device deleted it and another one changed it. */
data class FuryAccountConflict(val characterId: String, val name: String, val deleted: Boolean = false)

data class FuryAccountLogEntry(val time: String, val text: String)

/**
 * The Fury Account as the shared screens see it: sign-in, sync state and campaigns. Desktop and Android adapt
 * their native cloud controllers, web its Supabase client; no platform adds rules of its own. Properties are
 * Compose-observable in the adapters.
 */
interface FuryAccount {
    val signedIn: Boolean
    val email: String
    val nickname: String
    val status: FuryAccountStatus
    val statusText: String
    /** Characters changed or deleted here and not yet sent to the account. */
    val pendingChanges: Int
    val conflicts: List<FuryAccountConflict>
    val syncLog: List<FuryAccountLogEntry>
    /** «Последняя синхронизация: …», or null before the first one. */
    val lastSync: String? get() = null
    val campaigns: List<CampaignSummary>

    suspend fun signIn(email: String, password: String): Result<Unit>
    /** True when signed in at once; false when the email must be confirmed first. */
    suspend fun signUp(email: String, password: String, nickname: String): Result<Boolean>
    suspend fun requestPasswordReset(email: String): Result<Unit>
    suspend fun updateNickname(nickname: String): Result<String>
    fun requestSync()
    /** Sends pending changes, then signs out and clears the account's characters from this device. */
    suspend fun syncAndSignOut(): Result<Unit>
    /** Signs out and clears the account's characters from this device; unsent changes are lost. */
    suspend fun signOutDiscarding()
    suspend fun keepLocal(characterId: String)
    suspend fun useCloud()

    suspend fun refreshCampaigns(): Result<Unit>
    suspend fun createCampaign(name: String): Result<CampaignSummary>
    suspend fun createInvite(campaignId: String): Result<CampaignInvite>
    suspend fun joinCampaign(code: String, characterId: String): Result<CampaignSummary>
    suspend fun leaveCampaign(campaignId: String): Result<Unit>
    suspend fun removeMember(campaignId: String, userId: String): Result<Unit>
    suspend fun dashboard(campaignId: String): Result<CampaignDashboard>
}
