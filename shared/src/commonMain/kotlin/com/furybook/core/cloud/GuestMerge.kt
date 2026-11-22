package com.furybook.core.cloud

import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.DublCharacter

private const val STARTER_NAME = "Новый персонаж"

/** The fresh «Новый персонаж» every empty roster gets, never touched: not worth carrying into an account. */
private fun DublCharacter.isUntouchedStarter(): Boolean {
    if (name != STARTER_NAME) return false
    val starter = SnapshotCodec.decode(SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter(id = id, name = STARTER_NAME)), id))) { id }
    return starter.characters.single() == this
}

/** Guest characters the account doesn't have yet. An id already in [remote] was uploaded by an earlier sign-in. */
fun guestCharactersToUpload(remote: AppSnapshot, guest: AppSnapshot): List<DublCharacter> {
    val remoteIds = remote.characters.mapTo(HashSet()) { it.id }
    return guest.characters.filter { it.id !in remoteIds && !it.isUntouchedStarter() }
}

/**
 * A web guest signs in: the account's characters first, then the guest's that it lacks, and the guest's active
 * character stays active when it is in the result.
 */
fun mergeGuestIntoRemote(remote: AppSnapshot, guest: AppSnapshot, remoteHasCharacters: Boolean = true): AppSnapshot {
    val joining = guestCharactersToUpload(remote, guest)
    // An empty account pulls as one fresh placeholder; when the guest brings characters, they replace it.
    val kept = if (!remoteHasCharacters && joining.isNotEmpty()) emptyList() else remote.characters
    val characters = kept + joining
    val active = guest.activeCharacterId.takeIf { id -> characters.any { it.id == id } }
        ?: remote.activeCharacterId.takeIf { id -> characters.any { it.id == id } }
        ?: characters.first().id
    return AppSnapshot(characters, active)
}

/**
 * The local guest copy may go only once every character that joined the account is on the server. A plain pull also
 * ends «synced», so being synced alone proves nothing.
 */
fun guestCopyCanGo(joinedIds: Set<String>, serverIds: Set<String>, synced: Boolean): Boolean =
    synced && joinedIds.isNotEmpty() && serverIds.containsAll(joinedIds)
