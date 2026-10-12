package com.furybook.dubl.acquisition

import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopLegacyValidationTest {
    private fun raw() = SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("real", experience = 100,
        development = mapOf("owned" to OwnedDevelopment(2)))), "real"))

    private suspend fun refused(raw: String) {
        val dir = Files.createTempDirectory("t12-invalid-legacy")
        val file = dir.resolve("characters.json")
        Files.writeString(file, raw)
        val bytes = Files.readAllBytes(file)
        var manufactured = false
        assertFails { DesktopCharacterStore(file) { manufactured = true; "manufactured-id" }.load() }
        assertFalse(manufactured)
        val store = DesktopAcquisitionStore(dir)
        assertEquals(raw, assertIs<LoadResult.Corrupt>(store.load("real")).raw)
        assertEquals(raw, assertIs<MigrationResult.Corrupt>(store.prepareMigration("real", listOf("dubl-3.69"), null)).raw)
        assertFalse(Files.exists(store.envelopePath("real")))
        assertContentEquals(bytes, Files.readAllBytes(file))
    }

    @Test fun missing_primary_identity_never_manufactures_a_character() = runBlocking {
        refused("""{"schema":11,"activeCharacterId":"real","characters":[{}]}""")
    }
    @Test fun malformed_experience_and_owned_rank_never_become_zero_or_absence() = runBlocking {
        refused(raw().replace("\"experience\":100", "\"experience\":\"invalid\"")
            .replace("\"rank\":2", "\"rank\":\"invalid\""))
    }
    @Test fun numeric_strings_keep_the_existing_valid_projection_and_ownership() = runBlocking {
        val raw = raw().replace("\"experience\":100", "\"experience\":\"100\"")
            .replace("\"rank\":2", "\"rank\":\"2\"")
        val dir = Files.createTempDirectory("t12-numeric-legacy")
        val file = dir.resolve("characters.json"); Files.writeString(file, raw)
        val character = DesktopCharacterStore(file).load().activeCharacter
        assertEquals(100, character.experience); assertEquals(2, character.development.getValue("owned").rank)
        val pending = assertIs<MigrationResult.NeedsBalanceConfirmation>(DesktopAcquisitionStore(dir)
            .prepareMigration("real", listOf("dubl-3.69"), null)).pending
        assertEquals(character, pending.character); assertEquals(listOf("development:owned"), pending.unresolvedOwnershipKeys)
        assertEquals(raw, Files.readString(file))
    }
}
