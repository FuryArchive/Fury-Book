package com.furybook.dubl.acquisition

import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.*
import kotlin.test.*

class LegacySnapshotValidationTest {
    private fun raw(fields: String) = """{"schema":11,"activeCharacterId":"real","characters":[{"id":"real",$fields}]}"""
    private fun corrupt(raw: String) {
        assertEquals(raw, assertIs<LoadResult.Corrupt>(decodeEnvelope(raw)).raw)
        val origin = LegacyOrigin("real", 11, "source", raw, listOf("dubl-3.69"))
        assertEquals(raw, assertIs<MigrationResult.Corrupt>(prepareLegacy(raw, origin, null)).raw)
    }
    @Test fun primary_and_owned_identities_cannot_be_missing_duplicate_or_wrong_type() {
        for (raw in listOf(
            """{"schema":11,"characters":[{}]}""",
            """{"schema":11,"characters":[{"id":"real"},{"id":"real"}]}""",
            """{"schema":11,"characters":[{"id":42}]}""",
            raw("\"development\":[{\"rank\":2}]"),
            raw("\"skills\":[{\"rank\":2}]"),
            raw("\"development\":[{\"id\":\"owned\",\"rank\":1},{\"id\":\"owned\",\"rank\":2}]"),
        )) corrupt(raw)
    }
    @Test fun malformed_present_financial_and_ownership_fields_are_not_optional_defaults() {
        for (fields in listOf(
            "\"experience\":\"invalid\"", "\"creationExperience\":false", "\"xpAdjustment\":null",
            "\"abilityPointsOverride\":\"invalid\"", "\"creationComplete\":\"invalid\"",
            "\"chiBonusRanks\":2147483648", "\"attributes\":[]", "\"attributes\":{\"STRENGTH\":{\"base\":\"invalid\"}}",
            "\"development\":{}", "\"development\":[false]", "\"development\":[{\"id\":\"owned\",\"rank\":1.5}]",
            "\"skills\":[{\"id\":\"skill\",\"rank\":\"invalid\"}]",
            "\"magic\":{\"manaRank\":\"invalid\"}", "\"magic\":{\"schools\":[{\"name\":\"School\",\"level\":\"invalid\"}]}",
            "\"magic\":{\"spells\":[{\"learned\":\"invalid\"}]}", "\"magic\":{\"spells\":[{\"xpOverride\":\"invalid\"}]}",
            "\"magic\":{\"spells\":[{\"uid\":\"same\"},{\"uid\":\"same\"}]}",
        )) corrupt(raw(fields))
    }
    @Test fun supported_old_schemas_keep_optional_defaults_and_stable_nested_id_compatibility() {
        for (schema in 1..SnapshotCodec.SCHEMA) {
            val raw = """{"schema":$schema,"characters":[{"id":"real","experience":"20","magic":{"schools":[{"name":"Unknown","level":"2"}],"spells":[{"name":"Old spell"}]},"gear":{"items":[{"qty":"2"}]},"customResources":[{"name":"Old resource","maximum":"3"}]}]}"""
            assertIs<LoadResult.MigrationRequired>(decodeEnvelope(raw))
            val origin = LegacyOrigin("real", schema, "source", raw, listOf("dubl-3.69"))
            val one = assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw, origin, null)).pending
            val two = assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw, origin, null)).pending
            assertEquals(one.character, two.character)
            assertEquals(20, one.character.experience); assertEquals(20, one.character.creationExperience)
            assertEquals(schema < 5, one.character.creationComplete)
            assertEquals(2, one.character.magic.schools.single().rank)
            assertEquals(2, one.character.gear.items.single().quantity)
            assertTrue(one.character.magic.spells.single().uid.startsWith("legacy-generated:"))
            assertTrue(one.character.gear.items.single().uid.startsWith("legacy-generated:"))
        }
    }
    @Test fun fractional_number_lexemes_cannot_round_into_valid_financial_integers() {
        for (number in listOf("1.00000000000000000001", "2147483647.0000001", "1e-400"))
            corrupt(raw("\"experience\":$number"))
        for (number in listOf("20.0", "2e1", "200e-1", "0.0e99"))
            assertIs<LoadResult.MigrationRequired>(decodeEnvelope(raw("\"experience\":$number")))
    }
    @Test fun absent_null_and_blank_old_nested_uids_all_keep_deterministic_generation() {
        for (uid in listOf("", "\"uid\":null,", "\"uid\":\"\",")) {
            val source = raw("\"magic\":{\"spells\":[{$uid\"learned\":\"true\"}]},\"gear\":{\"items\":[{$uid\"qty\":\"2\"}]},\"customResources\":[{$uid\"name\":\"Resource\"}]")
            val origin = LegacyOrigin("real", 11, "source", source, listOf("dubl-3.69"))
            val one = assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(source, origin, null)).pending.character
            val two = assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(source, origin, null)).pending.character
            assertEquals(one, two)
            assertTrue(one.magic.spells.single().uid.startsWith("legacy-generated:"))
            assertTrue(one.gear.items.single().uid.startsWith("legacy-generated:"))
            assertTrue(one.customResources.single().uid.startsWith("legacy-generated:"))
        }
    }
}
