package com.furybook.core.cloud

import com.furybook.core.json.objectValue
import com.furybook.core.json.parseRoot
import com.furybook.core.json.string
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudExtrasProjectionTest {
    private fun extras() = CharacterSheetExtras(
        portraitUri = "local://portrait/hero",
        activeConditions = linkedSetOf(CharacterConditionId.TIRED, CharacterConditionId.BLINDNESS),
        hiddenResourceIds = linkedSetOf(CharacterSheetResourceId.MANA, CharacterSheetResourceId.CHI),
        preferredSkillAttributes = mapOf("skill" to AttributeId.WILL),
        skillGroups = listOf(SheetGroup("skills", "Навыки", listOf("skill"), collapsed = true)),
        developmentGroups = listOf(SheetGroup("development", "Развитие", listOf("dev"))),
        conditionOverrides = mapOf(CharacterConditionId.TIRED to ConditionLocalOverride("Устал", "Локальная заметка")),
        customConditions = listOf(CustomCondition("custom", "Особое", "Описание", active = true)),
        notes = "Legacy notes\nwith \"quotes\"",
        noteEntries = listOf(CharacterNote("note", "Запись", "Body\n\u001e\u001f")),
    )

    @Test fun all_sheet_fields_round_trip_with_the_existing_cloud_projection_shape() {
        val original = extras()
        val raw = CloudExtrasProjection.encode(original)
        val root = parseRoot(raw)
        assertEquals(setOf("portraitUri", "conditions", "hiddenResources", "preferredAttributes", "skillGroups",
            "developmentGroups", "conditionOverrides", "customConditions", "notes", "noteEntries"), root.values.keys)
        assertEquals(original, CloudProtocol.decodeExtras(raw))
        assertEquals(CloudProtocol.extrasJson(original), raw)
        assertEquals(original.notes, root.string("notes"))
        assertEquals(CharacterNoteDataCodec.encode(original.noteEntries), root.string("noteEntries"))
    }

    @Test fun protected_local_projection_keeps_portrait_while_acquisition_wire_omits_it() {
        val canonical = StoredCharacter(DublCharacter("hero"), extras(), emptyList(),
            Ledger(Opening("fresh", "new", Amount(80, 2), RequirementNode.CreationPhase.Phase.Creating,
                OpeningProvenance.NewCharacter), emptyList()),
            emptyList(), emptyList(), SyncMetadata(7), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
        val saved = encodeEnvelope(canonical)
        val projected = CloudExtrasProjection.encode(canonical.extras)
        assertEquals(canonical.extras, CloudProtocol.decodeExtras(projected))
        assertEquals(canonical.extras.portraitUri, parseRoot(projected).string("portraitUri"))
        val wire = parseRoot(encodeSyncEnvelope(canonical)).objectValue("extras")!!
        assertFalse("portraitUri" in wire.values)
        assertTrue("noteEntries" in wire.values)
        assertEquals(saved, encodeEnvelope(canonical))
        assertEquals(Amount(80, 2), balance(canonical.ledger))
    }

    @Test fun absent_portrait_retains_the_existing_omitted_projection_shape() {
        val raw = CloudExtrasProjection.encode(CharacterSheetExtras())
        assertFalse("portraitUri" in parseRoot(raw).values)
        assertEquals(CharacterSheetExtras(), CloudProtocol.decodeExtras(raw))
    }
}
