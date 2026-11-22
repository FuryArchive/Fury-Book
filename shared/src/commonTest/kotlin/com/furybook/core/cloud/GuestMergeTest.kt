package com.furybook.core.cloud

import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals

class GuestMergeTest {
    private val a = DublCharacter(id = "A", name = "Шершень")
    private val b = DublCharacter(id = "B", name = "Вика", experience = 10)
    private val c = DublCharacter(id = "C", name = "Гром")

    @Test
    fun guestCharactersMergeIntoRemote() {
        val merged = mergeGuestIntoRemote(AppSnapshot(listOf(a), "A"), AppSnapshot(listOf(b, c), "C"))
        assertEquals(listOf("A", "B", "C"), merged.characters.map { it.id })
        assertEquals("C", merged.activeCharacterId)
        assertEquals(listOf("B", "C"), guestCharactersToUpload(AppSnapshot(listOf(a), "A"), AppSnapshot(listOf(b, c), "C")).map { it.id })
    }

    @Test
    fun alreadyUploadedGuestIsNotDuplicated() {
        // An earlier sign-in uploaded B, then the upload of C was interrupted: B is remote now.
        val remote = AppSnapshot(listOf(a, b.copy(name = "Вика (с сервера)")), "A")
        val merged = mergeGuestIntoRemote(remote, AppSnapshot(listOf(b, c), "B"))
        assertEquals(listOf("A", "B", "C"), merged.characters.map { it.id })
        assertEquals("Вика (с сервера)", merged.characters[1].name)
        assertEquals("B", merged.activeCharacterId)
    }

    @Test
    fun untouchedStarterCharacterStaysBehind() {
        // A guest who never played has only the fresh «Новый персонаж»; it must not land in the account.
        val fresh = SnapshotCodec.decode(SnapshotCodec.encode(SnapshotCodec.fresh { "S" })) { "x" }
        val merged = mergeGuestIntoRemote(AppSnapshot(listOf(a), "A"), fresh)
        assertEquals(listOf("A"), merged.characters.map { it.id })
        assertEquals("A", merged.activeCharacterId)
        assertEquals(emptyList(), guestCharactersToUpload(AppSnapshot(listOf(a), "A"), fresh))
    }

    @Test
    fun guestCopyStaysUntilEveryJoinedCharacterIsOnTheServer() {
        // A pull after a failed upload also ends «synced»: that alone must not delete the guest copy.
        assertEquals(false, guestCopyCanGo(setOf("B", "C"), serverIds = setOf("A"), synced = true))
        assertEquals(false, guestCopyCanGo(setOf("B", "C"), serverIds = setOf("A", "B"), synced = true))
        assertEquals(false, guestCopyCanGo(setOf("B"), serverIds = setOf("A", "B"), synced = false))
        assertEquals(true, guestCopyCanGo(setOf("B", "C"), serverIds = setOf("A", "B", "C"), synced = true))
        assertEquals(false, guestCopyCanGo(emptySet(), serverIds = setOf("A"), synced = true))
    }

    @Test
    fun aNewAccountDoesNotKeepItsPlaceholderStarter() {
        // An empty account pulls as one fresh «Новый персонаж»; the guest's own characters replace it.
        val placeholder = AppSnapshot(listOf(DublCharacter(id = "S", name = "Новый персонаж")), "S")
        val merged = mergeGuestIntoRemote(placeholder, AppSnapshot(listOf(b, c), "C"), remoteHasCharacters = false)
        assertEquals(listOf("B", "C"), merged.characters.map { it.id })
        assertEquals("C", merged.activeCharacterId)
        // With nothing to bring, the placeholder stays.
        assertEquals(placeholder, mergeGuestIntoRemote(placeholder, AppSnapshot(emptyList<DublCharacter>().ifEmpty { listOf(DublCharacter(id = "S")) }, "S"), remoteHasCharacters = false))
    }
}
