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
fun mergeGuestIntoRemote(remote: AppSnapshot, guest: AppSnapshot): AppSnapshot {
    val characters = remote.characters + guestCharactersToUpload(remote, guest)
    val active = guest.activeCharacterId.takeIf { id -> characters.any { it.id == id } } ?: remote.activeCharacterId
    return AppSnapshot(characters, active)
}
