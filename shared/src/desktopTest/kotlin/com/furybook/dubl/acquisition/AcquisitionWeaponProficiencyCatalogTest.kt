package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlin.test.*

class AcquisitionWeaponProficiencyCatalogTest {
    private val catalog by lazy { loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
        FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog }
    private fun ref(id: String) = DefinitionRef("dubl-3.69", id)
    private val proficiency = ref("weapon_proficiency_template")
    private fun state(instances: List<AcquisitionInstance>, creating: Boolean = true): StoredCharacter {
        val phase = if (creating) RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized
        return StoredCharacter(DublCharacter("native-weapons", creationComplete = !creating), CharacterSheetExtras(), instances,
            Ledger(Opening("opening", "native-source-regression", Amount(1000, 10), phase, OpeningProvenance.NewCharacter), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }
    @Test fun actual_combat_rank_two_includes_one_explicit_compatible_group_without_a_paid_price() {
        val shooting = catalog.definitions.getValue(ref("shooting"))
        val slot = shooting.includedGrants.single { it.activateAtRank == 2 }
        val definition = catalog.definitions.getValue(proficiency)
        assertIs<PriceRule.Unknown>(definition.priceRule)
        assertEquals(4, slot.sourceRef.paragraph)
        assertTrue(slot.creationOnly)
        assertFalse(slot.requireNewOwnership)
        val group = definition.parameterDefinitions.single().choices.single { it.id == "bows" }
        val owner = AcquisitionInstance("shooting-owned", shooting.ref, 1, emptyMap(), null, AcquisitionOrigin.New)
        val before = state(listOf(owner))
        val target = AcquisitionTarget(shooting.ref, owner.instanceId, 2)
        val selected = AcquisitionTarget(proficiency, "bows-proficiency", 1, mapOf("groupId" to group.id, "groupName" to group.label))
        val request = AcquisitionRequest("native-weapons", listOf(target), prerequisiteSelections =
            mapOf(includedGrantChoiceId(shooting.ref, slot) to listOf(selected)))
        val result = plan(request, before, catalog)
        assertTrue(result.executable, result.issues.toString())
        assertEquals(listOf(shooting.ref, proficiency), result.changes.map { it.definition })
        assertEquals(Amount(20, 0), result.cost)
        assertEquals(Amount.ZERO, result.prices.last().cost)
        assertEquals(group.weaponKinds.map { it.id }.toSet(), AcquisitionWeaponProficiencies
            .ownedKinds(result.projectedContext.instances, catalog).map { it.id }.toSet())
        assertTrue(group.weaponKinds.size >= 2)
        val oldGroup=AcquisitionInstance(selected.instanceId!!,proficiency,1,selected.parameters,null,AcquisitionOrigin.New)
        val noOp=plan(request,state(listOf(owner,oldGroup)),catalog)
        assertTrue(noOp.executable,noOp.issues.toString())
        assertEquals(listOf(shooting.ref),noOp.changes.map { it.definition })
        assertEquals(Amount(20,0),noOp.cost)
        assertEquals(oldGroup,noOp.projectedContext.instances.single { it.definition==proficiency })
        val wrong = definition.parameterDefinitions.single().choices.single { it.id == "polearms" }
        val incompatible = selected.copy(parameters = mapOf("groupId" to wrong.id, "groupName" to wrong.label))
        assertFalse(plan(AcquisitionRequest("native-weapons", listOf(target), prerequisiteSelections =
            mapOf(includedGrantChoiceId(shooting.ref, slot) to listOf(incompatible))), before, catalog).executable)
        val noCredit = plan(AcquisitionRequest("native-weapons", listOf(selected)), before, catalog)
        assertFalse(noCredit.executable)
    }

    @Test fun actual_summon_weapon_choices_use_owned_groups_and_each_repeat_adds_a_new_kind() {
        val definition = catalog.definitions.getValue(proficiency)
        val group = definition.parameterDefinitions.single().choices.single { it.id == "bows" }
        val owned = AcquisitionInstance("bows-proficiency", proficiency, 1,
            mapOf("groupId" to group.id, "groupName" to group.label), null, AcquisitionOrigin.New)
        val saved = state(listOf(owned), creating = false)
        val spell = catalog.definitions.getValue(ref("spell_e3bebd434e638daa"))
        val kinds = AcquisitionWeaponProficiencies.ownedKinds(saved.instances, catalog)
        assertTrue(kinds.size >= 3)
        assertTrue(spell.parameterDefinitions.all { it.selectionSource == ParameterSelectionSource.OwnedWeaponKinds })
        assertNotNull(spell.newParameterValueRequirement)
        assertTrue(acquisitionParameterFields(spell, saved, catalog, 1).all { field ->
            !field.userDefined && field.options.map { it.id }.toSet() == kinds.map { it.id }.toSet()
        })
        fun parameters(first: WeaponProficiencyKind, second: WeaponProficiencyKind) = mapOf("weaponKind1" to first.id,
            "weaponKind1Name" to first.label, "weaponKind2" to second.id, "weaponKind2Name" to second.label)
        fun check(values: Map<String,String>, previous: List<AcquisitionInstance> = saved.instances) = validateAcquisitionTarget(
            spell.ref, spell.maxRank!!, spell.parameterDefinitions, spell.repeatPolicy, "new-summon", 1, values, null,
            previous, spell.abilityOptionIds, catalog)
        assertIs<IdentityValidation.NewInstance>(check(parameters(kinds[0], kinds[1])))
        val prior = AcquisitionInstance("old-summon", spell.ref, 1, parameters(kinds[0], kinds[1]), null, AcquisitionOrigin.New)
        assertIs<IdentityValidation.Blocked>(check(parameters(kinds[1], kinds[0]), saved.instances + prior))
        assertIs<IdentityValidation.NewInstance>(check(parameters(kinds[0], kinds[2]), saved.instances + prior))
        assertIs<IdentityValidation.Blocked>(check(parameters(kinds[0], WeaponProficiencyKind("invented-kind", kinds[1].label, spell.sourceRef))))
    }
}
