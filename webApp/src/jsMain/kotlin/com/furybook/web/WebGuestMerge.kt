package com.furybook.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.core.cloud.guestCharactersToUpload
import com.furybook.core.cloud.guestCopyCanGo
import com.furybook.core.cloud.mergeGuestIntoRemote
import com.furybook.dubl.data.SnapshotCodec

/** Guest characters merged into the account and waiting for their first upload; the local copy stays until then. */
object WebGuestUpload {
    var joinedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    internal fun start(ids: Set<String>) { joinedIds = ids }

    /** Called on every sync phase: the guest copy goes only once every joined character is on the server. */
    fun onSyncPhase(phase: CloudSyncPhase, cloud: WebCloudSync) {
        if (guestCopyCanGo(joinedIds, cloud.idsOnServer(), synced = phase == CloudSyncPhase.SYNCED)) {
            BrowserGuestStorage.clear()
            joinedIds = emptySet()
        }
    }

    /** Signed out: nothing is waiting any more (the guest copy, if any, stays for the next sign-in). */
    fun reset() { joinedIds = emptySet() }
}

/**
 * Loads the account into the browser's working copy. Guest characters still in this browser are added after the
 * account's and sent through the normal save path; they are never replaced by the pull, and the guest copy is not
 * touched until the pull has worked. Returns how many guest characters joined the account.
 */
suspend fun hydrateRemoteMergingGuest(cloud: WebCloudSync): Int {
    val guestRaw = BrowserGuestStorage.snapshotRaw()
    val guestExtrasRaw = BrowserGuestStorage.extrasRaw()
    val remote = cloud.pull()
    clearBrowserSessionData()
    val store = BrowserCharacterStore()
    val extras = BrowserCharacterExtrasStore()
    store.replaceRaw(remote.snapshot)
    extras.replaceRaw(remote.extras)
    BrowserPackState.chiEnabled = remote.chiEnabled
    val guest = guestRaw?.let { raw -> runCatching { SnapshotCodec.decode(raw, ::webUuid) }.getOrNull() } ?: return 0
    val account = SnapshotCodec.decode(remote.snapshot, ::webUuid)
    val joining = guestCharactersToUpload(account, guest)
    if (joining.isEmpty()) {
        // Everything the guest had is already in the account (or was only the untouched starter).
        BrowserGuestStorage.clear()
        return 0
    }
    store.replaceRaw(SnapshotCodec.encode(mergeGuestIntoRemote(account, guest, remoteHasCharacters = remote.hasCharacters)))
    extras.importFrom(guestExtrasRaw, joining.map { it.id })
    WebGuestUpload.start(joining.mapTo(HashSet()) { it.id })
    cloud.schedule(store.raw(), extras.raw(), BrowserPackState.chiEnabled)
    return joining.size
}
