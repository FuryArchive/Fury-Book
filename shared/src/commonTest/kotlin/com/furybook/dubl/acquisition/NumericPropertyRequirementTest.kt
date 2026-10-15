package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class NumericPropertyRequirementTest {
    private val source = """{"documentId":"1BGajeMuh0clSbybt0IGyqv2_6zxdKEtHvnjh_TbKqGg","documentVersion":"AHj4eMTQLuP9yVLKMPfXF04bxfCO4CCdfFXf8dea1DFmXBthsgIY7WeNoLTvikfr2giHlMTW9-sl2j486Z7ztA","sheet":"t.0","paragraph":20,"originalText":"Сила магии определяет насколько дорогие по мане заклинания Вы способны использовать и изучать."}"""
    private val ref = DefinitionRef("dubl-3.69", "spell_54234bfde5761792")
    private fun load(property: String = "MAGIC_POWER", operand: Int = 6): CatalogLoadResult {
        val requirement = """{"type":"NumericProperty","nodeId":"agony/power","sourceRef":$source,"propertyId":"$property","operator":"GE","operand":$operand}"""
        val manifest = """{"format":"fury.content-pack","formatVersion":1,"id":"dubl-3.69","name":"Core","version":"3.69","ruleset":{"id":"dubl","version":"3.69","engineApi":1},"modules":[],"entries":[{"kind":"dubl.acquisition","path":"acquisition.json","modules":[],"order":1}]}"""
        val payload = """{"schemaVersion":1,"catalogRevision":"numeric-test","resolutionRevision":"native-current","definitions":[{
          "ref":{"packId":"dubl-3.69","entryId":"spell_54234bfde5761792"},"kind":"SPELL","name":"Агония","maxRank":1,
          "repeatPolicy":{"type":"Single"},"sourceStatus":"Confirmed","sourceRef":$source,"parameterDefinitions":[],"abilityOptionIds":[],"requirementsText":"Сила магии не ниже 6",
          "requirements":$requirement,"priceRule":{"type":"Fixed","xp":"20","ap":"0","sourceRef":$source}}],"diagnostics":[]}"""
        return loadAcquisitionCatalog(listOf(FcpContentPack.load("", FcpTextSource { when (it) {
            "manifest.json" -> manifest; "acquisition.json" -> payload; else -> null
        } })), false)
    }
    private fun state(magic: CharacterMagic) = StoredCharacter(DublCharacter("caster", magic = magic), CharacterSheetExtras(), emptyList(),
        Ledger(Opening("opening", "numeric-test", Amount(100, 3), RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.NewCharacter), emptyList()),
        emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)

    @Test fun power_uses_existing_school_or_legacy_property_without_deriving_it_from_mana_reserve() {
        val catalog = load().catalog
        val requirement = catalog.definitions.getValue(ref).requirements
        for ((magic, expected) in listOf(
            CharacterMagic(manaRank = 5, power = 0, schools = listOf(MagicSchool("Разум", 6))) to 6,
            CharacterMagic(manaRank = 5, power = 0, schools = listOf(MagicSchool("Разум", 2))) to 2,
            CharacterMagic(manaRank = 5, power = 8, schools = listOf(MagicSchool("Разум", 2))) to 2,
            CharacterMagic(manaRank = 1, power = 6) to 6,
            CharacterMagic(manaRank = 1, power = 1) to 1,
            CharacterMagic(manaRank = 5, power = 0) to 0)) {
            val result = evaluate(requirement, acquisitionContext(state(magic), catalog))
            assertEquals(RequirementValue.Number(expected), result.actualValue)
            assertEquals(RequirementValue.Number(6), result.requiredValue)
            assertEquals(if (expected >= 6) RequirementStatus.Satisfied else RequirementStatus.Unsatisfied, result.status)
        }
    }

    @Test fun unknown_property_and_invalid_boundary_are_diagnostic_source_blocks() {
        val unknown = load("SPELL_LEVEL")
        assertEquals(RequirementStatus.SourceUnresolved, evaluate(unknown.catalog.definitions.getValue(ref).requirements,
            acquisitionContext(state(CharacterMagic(power = 20)), unknown.catalog)).status)
        assertTrue(unknown.diagnostics.any { it.kind == "unsupported-numeric-property" })
        val invalid = load(operand = -1)
        assertTrue(invalid.diagnostics.any { it.kind == "numeric-property-out-of-range" })
    }

    @Test fun planner_cannot_buy_or_replace_an_unsatisfied_readonly_magic_power_fact() {
        val catalog = load().catalog
        val initial = state(CharacterMagic(manaRank = 5, power = 0))
        val target = AcquisitionTarget(ref, "agony", 1)
        val preview = plan(AcquisitionRequest("caster", listOf(target)), initial, catalog)
        assertFalse(preview.executable)
        assertTrue(preview.changes.isEmpty())
        assertTrue(preview.issues.any { it.status == RequirementStatus.Unsatisfied })
        val key = acquisitionChoiceId(ref, "agony/power")
        val spoofed = plan(AcquisitionRequest("caster", listOf(target), prerequisiteSelections = mapOf(key to listOf(target))), initial, catalog)
        assertFalse(spoofed.executable)
        assertTrue(spoofed.changes.isEmpty())
    }

    @Test fun zero_power_threshold_does_not_imply_a_mana_reserve_purchase() {
        val catalog = load(operand = 0).catalog
        val preview = plan(AcquisitionRequest("caster", listOf(AcquisitionTarget(ref, "agony", 1))),
            state(CharacterMagic()), catalog)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(listOf(ref), preview.changes.map { it.definition })
    }

    @Test fun numeric_catalog_requirement_keeps_existing_strict_receipt_and_envelope_schema() = runBlocking {
        val catalog = load().catalog
        val initial = state(CharacterMagic(manaRank = 2, power = 6))
        val store = InMemoryAcquisitionStore(mapOf("caster" to encodeEnvelope(initial)))
        val service = AcquisitionService(store, { catalog }, { "operation" })
        val request = AcquisitionRequest("caster", listOf(AcquisitionTarget(ref, "agony", 1)))
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        val receipt = assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("learn", "caster", request,
            preview.baseRevision, preview.previewToken))).receipt
        assertEquals(Amount(20, 0), receipt.result.cost)
        val saved = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("caster")).state))).state
        assertEquals(6, saved.character.magic.power)
        assertEquals(2, saved.character.magic.manaRank)
        assertEquals(receipt, saved.receipts.single())
        assertTrue(saved.character.magic.spells.single().learned)
    }
}
