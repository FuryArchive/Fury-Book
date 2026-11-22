package com.furybook.ui.account

import com.furybook.core.cloud.FuryCloudTransport
import com.furybook.core.cloud.FuryNativeCloudApi
import com.furybook.core.cloud.NativeCloudMetadata
import com.furybook.core.cloud.NativeCloudMetadataStore
import com.furybook.core.cloud.NativeCloudSyncCoordinator
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuryAccountTest {
    private class MemoryExtras : CharacterExtrasStore {
        val map = mutableMapOf<String, CharacterSheetExtras>()
        override fun load(characterId: String) = map[characterId] ?: CharacterSheetExtras()
        override fun save(characterId: String, extras: CharacterSheetExtras) { map[characterId] = extras }
        override fun delete(characterId: String) { map.remove(characterId) }
    }

    private class MemoryMetadata : NativeCloudMetadataStore {
        var value = NativeCloudMetadata()
        override fun load() = value
        override fun save(metadata: NativeCloudMetadata) { value = metadata }
        override fun clear() { value = NativeCloudMetadata() }
    }

    private object NoTransport : FuryCloudTransport {
        override suspend fun rpc(function: String, jsonBody: String): String = error("offline")
    }

    private val a = DublCharacter(id = "a", name = "Вика")
    private val b = DublCharacter(id = "b", name = "Шершень")

    private fun coordinator(store: InMemoryCharacterStore, extras: MemoryExtras, metadata: MemoryMetadata) =
        NativeCloudSyncCoordinator("u", store, extras, metadata, FuryNativeCloudApi(NoTransport), { "fresh" }, "test")

    @Test
    fun pendingCountFollowsDirtyIds() {
        val before = AppSnapshot(listOf(a, b), "a")
        val store = InMemoryCharacterStore(before)
        val sync = coordinator(store, MemoryExtras(), MemoryMetadata())
        assertEquals(0, sync.pendingCount())
        sync.markSnapshotChanged(before, before.copy(characters = listOf(a.copy(name = "Вика 2"), b)))
        assertEquals(1, sync.pendingCount())
        sync.markSnapshotChanged(before, AppSnapshot(listOf(a), "a"))
        assertEquals(2, sync.pendingCount())
    }

    @Test
    fun signOutDiscardingClearsDevice() {
        val store = InMemoryCharacterStore(AppSnapshot(listOf(a, b), "a"))
        val extras = MemoryExtras().apply { save("a", CharacterSheetExtras(notes = "x")); save("b", CharacterSheetExtras(notes = "y")) }
        val metadata = MemoryMetadata().apply { value = NativeCloudMetadata(linkedUserId = "u", dirtyIds = setOf("a")) }
        coordinator(store, extras, metadata).clearLocal()
        val after = store.load()
        assertTrue(after.characters.none { it.id == "a" || it.id == "b" })
        assertTrue(extras.map.isEmpty())
        assertEquals(NativeCloudMetadata(), metadata.value)
    }

    @Test
    fun fakeAccountSignsInAndOut() = runSuspend {
        val account = FakeAccount()
        assertFalse(account.signedIn)
        assertTrue(account.signIn("a@b.co", "12345678").isSuccess)
        assertTrue(account.signedIn)
        account.signOutDiscarding()
        assertFalse(account.signedIn)
        assertEquals(listOf("signIn:a@b.co", "signOutDiscarding"), account.calls)
    }
}
