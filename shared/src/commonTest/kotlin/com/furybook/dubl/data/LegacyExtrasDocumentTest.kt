package com.furybook.dubl.data

import com.furybook.core.json.*
import com.furybook.dubl.model.*
import kotlin.test.*

class LegacyExtrasDocumentTest {
    private val old = """{"version":1,"window":{"tab":3},"characters":{"old":{"portraitUri":"file:///портрет.png","conditions":["WEAKNESS","OLD_CONDITION"],"hiddenResources":["CHI","OLD_RESOURCE"],"preferredAttributes":{"athletics":"STRENGTH","old-skill":"OLD_ATTRIBUTE"},"notes":"Сохранённая заметка","favoriteSkillIds":["athletics"],"oldPanel":{"collapsed":true,"order":[2,1]}},"other":{"notes":"Другой персонаж","oldFlag":false}}}"""

    @Test fun old_sheet_reads_known_values_without_requiring_financial_migration() {
        val document = LegacyExtrasDocument.decode(old)
        val extras = document.load("old")
        assertEquals("Сохранённая заметка", extras.notes)
        assertEquals("file:///портрет.png", extras.portraitUri)
        assertEquals(setOf(CharacterConditionId.WEAKNESS), extras.activeConditions)
        assertEquals(setOf(CharacterSheetResourceId.CHI), extras.hiddenResourceIds)
        assertEquals(mapOf("athletics" to AttributeId.STRENGTH), extras.preferredSkillAttributes)
        assertEquals(parse(old), parse(document.encode()))
        assertFailsWith<IllegalArgumentException> { document.forMigration() }
    }

    @Test fun edit_and_reopen_retain_unknown_fields_values_and_other_characters() {
        val document = LegacyExtrasDocument.decode(old)
        val edited = document.withExtras("old", document.load("old").copy(notes = "Новая заметка"))
        val before = parse(old)
        val after = parse(edited.encode())
        assertEquals(before.values["window"], after.values["window"])
        val beforeRows = before.values["characters"] as JsonValue.Obj
        val afterRows = after.values["characters"] as JsonValue.Obj
        assertEquals(beforeRows.values["other"], afterRows.values["other"])
        val beforeOld = beforeRows.values["old"] as JsonValue.Obj
        val afterOld = afterRows.values["old"] as JsonValue.Obj
        assertEquals(beforeOld.values - "notes", afterOld.values - "notes")
        assertEquals("Новая заметка", LegacyExtrasDocument.decode(edited.encode()).load("old").notes)
    }

    @Test fun editing_conditions_or_preferred_attributes_retains_unrecognized_members() {
        val document = LegacyExtrasDocument.decode(old)
        val extras = document.load("old").copy(activeConditions = setOf(CharacterConditionId.EXHAUSTION),
            hiddenResourceIds = emptySet(), preferredSkillAttributes = mapOf("athletics" to AttributeId.DEXTERITY))
        val row = ((parse(document.withExtras("old", extras).encode()).values["characters"] as JsonValue.Obj).values["old"] as JsonValue.Obj)
        assertEquals(listOf("EXHAUSTION", "OLD_CONDITION"), (row.values["conditions"] as JsonValue.Arr).values.map { it.asString() })
        assertEquals(listOf("OLD_RESOURCE"), (row.values["hiddenResources"] as JsonValue.Arr).values.map { it.asString() })
        assertEquals(jsonString("OLD_ATTRIBUTE"), (row.values["preferredAttributes"] as JsonValue.Obj).values["old-skill"])
    }

    @Test fun known_legacy_encoding_and_cyrillic_survive_migration_without_repricing() {
        val extras = CharacterSheetExtras(notes = "Привет\nмир", noteEntries = listOf(CharacterNote("one", "Заголовок", "Тело")))
        val document = LegacyExtrasDocument.empty().withExtras("old", extras)
        assertEquals(extras, LegacyExtrasDocument.decode(document.encode()).forMigration().getValue("old"))
        val literalControls = document.encode().replace("\\u001f", "\u001f").replace("\\u001e", "\u001e")
        assertEquals(extras, LegacyExtrasDocument.decode(literalControls).load("old"))
    }

    @Test fun future_extras_remain_readable_but_cannot_be_overwritten_or_migrated() {
        val future = LegacyExtrasDocument.decode("""{"version":999,"characters":{"old":{"notes":"Не терять"}}}""")
        assertEquals("Не терять", future.load("old").notes)
        assertFailsWith<UnsupportedLegacyExtrasVersion> { future.withExtras("old", CharacterSheetExtras()) }
        assertFailsWith<UnsupportedLegacyExtrasVersion> { future.forMigration() }
    }

    @Test fun unrelated_edit_preserves_opaque_encoded_values_and_isolates_other_bad_rows() {
        val raw = """{"version":1,"characters":{"old":{"notes":"Запись","skillGroups":"opaque old encoding"},"broken":false}}"""
        val document = LegacyExtrasDocument.decode(raw)
        assertEquals("Запись", document.load("old").notes)
        val edited = document.withExtras("old", document.load("old").copy(portraitUri = "portrait"))
        val rows = parse(edited.encode()).values["characters"] as JsonValue.Obj
        assertEquals(JsonValue.Bool(false), rows.values["broken"])
        assertEquals(jsonString("opaque old encoding"), (rows.values["old"] as JsonValue.Obj).values["skillGroups"])
        assertFails { document.forMigration() }
    }

    private fun parse(raw: String) = JsonReader(raw, strict = true).read() as JsonValue.Obj
}
