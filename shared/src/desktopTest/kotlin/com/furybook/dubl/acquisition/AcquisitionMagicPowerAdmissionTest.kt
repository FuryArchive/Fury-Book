package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Current native Archmage spell card + learning table + power admission, through real FCP/service. */
class AcquisitionMagicPowerAdmissionTest {
    private val catalog by lazy { loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
        FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog }
    private val agony = DefinitionRef("dubl-3.69", "spell_54234bfde5761792")
    private fun state(power: Int, schoolPower: Int = 0): StoredCharacter {
        val magic = CharacterMagic(manaRank = 5, power = power,
            schools = if (schoolPower > 0) listOf(MagicSchool("Разум", schoolPower)) else emptyList())
        return StoredCharacter(DublCharacter("native-caster", magic = magic).normalized(), CharacterSheetExtras(), emptyList(),
            Ledger(Opening("opening", "current-native-archmage", Amount(100, 10), RequirementNode.CreationPhase.Phase.Creating,
                OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
            RequirementNode.CreationPhase.Phase.Creating)
    }

    @Test fun actual_agony_admission_uses_its_six_mana_card_cost_and_current_magic_power() = runBlocking {
        val definition = catalog.definitions.getValue(agony)
        assertEquals("Агония", definition.name)
        val requirement = assertIs<RequirementNode.NumericProperty>(definition.requirements)
        assertEquals("MAGIC_POWER", requirement.propertyId)
        assertEquals(ComparisonOperator.GE, requirement.operator)
        assertEquals(6, requirement.operand)
        assertEquals("1BGajeMuh0clSbybt0IGyqv2_6zxdKEtHvnjh_TbKqGg", requirement.sourceRef.documentId)
        assertTrue(requirement.sourceRef.originalText.contains("использовать и изучать"))
        val request = AcquisitionRequest("native-caster", listOf(AcquisitionTarget(agony, "learn-agony", 1)))
        for (initial in listOf(state(0, 2), state(1))) {
            val denied = plan(request, initial, catalog)
            assertFalse(denied.executable)
            assertTrue(denied.changes.isEmpty(), "Power six cannot be invented from mana reserve five")
            assertEquals(RequirementStatus.Unsatisfied, evaluate(requirement, acquisitionContext(initial, catalog)).status)
        }
        val initial = state(0, 6)
        assertEquals(0, initial.character.magic.power, "School-based characters preserve the existing normalized representation")
        val store = InMemoryAcquisitionStore(mapOf("native-caster" to encodeEnvelope(initial)))
        val service = AcquisitionService(store, { catalog }, { "id" })
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(Amount(20, 0), preview.cost)
        assertEquals(listOf(agony), preview.changes.map { it.definition })
        val receipt = assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("learn", "native-caster", request,
            preview.baseRevision, preview.previewToken))).receipt
        assertEquals(Amount(20, 0), receipt.result.cost)
        val saved = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("native-caster")).state))).state
        assertEquals(6, MagicEquipmentRules.highestMagicPower(saved.character))
        assertEquals(initial.character.magic.manaRank, saved.character.magic.manaRank)
        assertEquals(initial.character.magic.schools, saved.character.magic.schools)
        assertEquals(Amount(80, 10), balance(saved.ledger))
        assertEquals("Агония", saved.character.magic.spells.single().name)
        assertIs<ApplyResult.AlreadyApplied>(service.apply(ConfirmAcquisition("learn", "native-caster", request,
            preview.baseRevision, preview.previewToken)))
        Unit
    }
}
