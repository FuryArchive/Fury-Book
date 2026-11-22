package com.furybook.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.furybook.core.cloud.guestCharactersToUpload
import com.furybook.core.cloud.mergeGuestIntoRemote
import com.furybook.dubl.data.SnapshotCodec

/** Guest characters merged into the account and waiting for their first upload; the local copy stays until then. */
object WebGuestUpload {
    var waiting by mutableStateOf(0)
        private set

    internal fun start(count: Int) { waiting = count }

    /** Called on every sync phase: once the merged characters are on the server, the guest copy can go. */
    fun onSyncPhase(phase: CloudSyncPhase) {
        if (waiting > 0 && phase == CloudSyncPhase.SYNCED) {
            BrowserGuestStorage.clear()
            waiting = 0
        }
    }
}

/**
 * Loads the account into the browser's working copy. A guest's characters (still in localStorage) are added
 * after the account's and sent through the normal save path; they are never replaced by the pull. Returns how
 * many guest characters joined the account.
 */
suspend fun hydrateRemoteMergingGuest(cloud: WebCloudSync): Int {
    val guestRaw = BrowserGuestStorage.snapshotRaw()
    val guestExtrasRaw = BrowserGuestStorage.extrasRaw()
    clearBrowserSessionData()
    val remote = cloud.pull()
    val store = BrowserCharacterStore()
    val extras = BrowserCharacterExtrasStore()
    store.replaceRaw(remote.snapshot)
    extras.replaceRaw(remote.extras)
    BrowserPackState.chiEnabled = remote.chiEnabled
    val guest = guestRaw?.let { raw -> runCatching { SnapshotCodec.decode(raw, ::webUuid) }.getOrNull() } ?: return 0
    val account = SnapshotCodec.decode(remote.snapshot, ::webUuid)
    val joining = guestCharactersToUpload(account, guest)
    if (joining.isEmpty()) {
        BrowserGuestStorage.clear()
        return 0
    }
    store.replaceRaw(SnapshotCodec.encode(mergeGuestIntoRemote(account, guest)))
    extras.importFrom(guestExtrasRaw, joining.map { it.id })
    WebGuestUpload.start(joining.size)
    cloud.schedule(store.raw(), extras.raw(), BrowserPackState.chiEnabled)
    return joining.size
}
