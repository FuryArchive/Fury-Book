package com.furybook.dubl.acquisition

import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.model.toAcquisitionInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AcquisitionIdentityTest {
    private val knowledge = DefinitionRef("dubl-3.69", "knowledge")
    private val resistance = DefinitionRef("dubl-3.69", "resistance")
    private val weapon = DefinitionRef("dubl-3.69", "weapon-specialization")
    private val newOrigin = AcquisitionOrigin.New
    private val legacyOrigin = AcquisitionOrigin.Legacy("character-1", "schema-1", "revision-1", "exact-source-fingerprint", "owned-weapon")
    private val knowledgeSchema = listOf(ParameterDefinition("knowledgeId", ParameterDomain.UserDefinedIds))
    private val resistanceSchema = listOf(ParameterDefinition("resistanceId", ParameterDomain.StableIds(setOf("fire", "poison"))))
    private val weaponSchema = listOf(ParameterDefinition("weaponTypeId", ParameterDomain.StableIds(setOf("melee", "ranged"))))

    @Test fun different_knowledge_instances() {
        // Merging by family/rank/name instead of parameter and instance IDs must fail.
        val history = AcquisitionInstance("history-1", knowledge, 4, mapOf("knowledgeId" to "history-id"), null, newOrigin)
        val astronomy = AcquisitionInstance("astronomy-1", knowledge, 4, mapOf("knowledgeId" to "astronomy-id"), null, newOrigin)
        assertNotEquals(history.instanceId, astronomy.instanceId)
        assertEquals(listOf(history), selectInstances(knowledge, mapOf("knowledgeId" to "history-id"), listOf(history, astronomy)))
        val names = mutableMapOf("history-id" to "History", "astronomy-id" to "History")
        names["history-id"] = "Renamed history"
        assertEquals(listOf(history), selectInstances(knowledge, mapOf("knowledgeId" to names.keys.first()), listOf(history, astronomy)))
        assertEquals(IdentityValidation.NewInstance, validate(knowledge, 5, knowledgeSchema, RepeatPolicy.DistinctParameter(setOf("knowledgeId")), "another-1", 4, mapOf("knowledgeId" to "another-id"), listOf(history, astronomy)))
    }

    @Test fun resistance_rank_is_not_second_type() {
        val fire = AcquisitionInstance("fire-1", resistance, 1, mapOf("resistanceId" to "fire"), null, newOrigin)
        assertEquals(IdentityValidation.ExistingInstance(fire), validate(resistance, 3, resistanceSchema, RepeatPolicy.DistinctParameter(setOf("resistanceId")), fire.instanceId, 2, fire.parameters, listOf(fire)))
        val raised = fire.copy(rank = 2)
        assertEquals(emptyList(), selectInstances(resistance, mapOf("resistanceId" to "poison"), listOf(raised)))
        assertEquals(1, fire.rank)
        assertEquals(2, raised.rank)
        assertEquals(RequirementStatus.Conflict, validate(resistance, 3, resistanceSchema, RepeatPolicy.DistinctParameter(setOf("resistanceId")), fire.instanceId, 2, mapOf("resistanceId" to "poison"), listOf(fire)).status())
    }

    @Test fun unknown_weapon_parameter_not_melee() {
        val owned = OwnedDevelopment(rank = 2, optionIndex = 7)
        val legacy = owned.toAcquisitionInstance(weapon, legacyOrigin)
        assertEquals(2, legacy.rank)
        assertEquals(emptyMap(), legacy.parameters)
        assertEquals(null, legacy.abilityOptionId)
        assertEquals(emptyList(), selectInstances(weapon, mapOf("weaponTypeId" to "melee"), listOf(legacy)))
        assertEquals(listOf(legacy), selectInstances(weapon, emptyMap(), listOf(legacy)))
        assertEquals(RequirementStatus.ChoiceRequired, validate(weapon, 3, weaponSchema, RepeatPolicy.Single, legacy.instanceId, 3, emptyMap(), listOf(legacy)).status())
        assertEquals(RequirementStatus.Conflict, validate(weapon, 3, weaponSchema, RepeatPolicy.Single, legacy.instanceId, 3, mapOf("weaponTypeId" to "melee"), listOf(legacy)).status())
    }

    @Test fun ability_option_change_is_not_rebuy() {
        val ability = DefinitionRef("dubl-3.69", "ability")
        val owned = AcquisitionInstance("ability-1", ability, 2, emptyMap(), "option-a", newOrigin)
        assertEquals(IdentityValidation.NoChange(owned), validateAcquisitionTarget(ability, 3, emptyList(), RepeatPolicy.Single, owned.instanceId, 2, emptyMap(), "option-a", listOf(owned), setOf("option-a", "option-b")))
        assertEquals(RequirementStatus.Conflict, validateAcquisitionTarget(ability, 3, emptyList(), RepeatPolicy.Single, owned.instanceId, 2, emptyMap(), "option-b", listOf(owned), setOf("option-a", "option-b")).status())
        assertEquals("option-a", owned.abilityOptionId)
    }

    @Test fun correct_wrong_missing_and_blank_parameters() {
        assertEquals(IdentityValidation.NewInstance, validate(weapon, 3, weaponSchema, RepeatPolicy.Single, "new-1", 1, mapOf("weaponTypeId" to "melee")))
        assertEquals(RequirementStatus.Unsatisfied, validate(weapon, 3, weaponSchema, RepeatPolicy.Single, "new-1", 1, mapOf("weaponTypeId" to "unknown")).status())
        assertEquals(RequirementStatus.ChoiceRequired, validate(weapon, 3, weaponSchema, RepeatPolicy.Single, "new-1", 1, emptyMap()).status())
        assertEquals(RequirementStatus.ChoiceRequired, validate(weapon, 3, weaponSchema, RepeatPolicy.Single, "new-1", 1, mapOf("weaponTypeId" to " ")).status())
        assertEquals(RequirementStatus.Unsatisfied, validate(weapon, 3, weaponSchema, RepeatPolicy.Single, "new-1", 1, mapOf("weaponTypeId" to "melee", "invented" to "x")).status())
        assertEquals(emptyList(), selectInstances(weapon, mapOf("weaponTypeId" to ""), emptyList()))
    }

    @Test fun repeat_policy_and_rank_caps_are_definition_constraints() {
        val fire = AcquisitionInstance("fire-1", resistance, 1, mapOf("resistanceId" to "fire"), null, newOrigin)
        val repeat = RepeatPolicy.DistinctParameter(setOf("resistanceId"))
        assertEquals(RequirementStatus.Conflict, validate(resistance, 3, resistanceSchema, repeat, "fire-2", 1, fire.parameters, listOf(fire)).status())
        assertEquals(IdentityValidation.NewInstance, validate(resistance, 3, resistanceSchema, repeat, "poison-1", 1, mapOf("resistanceId" to "poison"), listOf(fire)))
        assertEquals(RequirementStatus.Conflict, validate(resistance, 3, resistanceSchema, RepeatPolicy.Single, "poison-1", 1, mapOf("resistanceId" to "poison"), listOf(fire)).status())
        assertEquals(RequirementStatus.SourceUnresolved, validate(resistance, 3, resistanceSchema, RepeatPolicy.Unresolved("repeat wording unclear"), "poison-1", 1, mapOf("resistanceId" to "poison"), listOf(fire)).status())
        assertEquals(IdentityValidation.ExistingInstance(fire), validate(resistance, 3, resistanceSchema, RepeatPolicy.Unresolved("repeat wording unclear"), fire.instanceId, 2, fire.parameters, listOf(fire)))
        assertEquals(RequirementStatus.Unsatisfied, validate(resistance, 3, resistanceSchema, repeat, fire.instanceId, 4, fire.parameters, listOf(fire)).status())
        assertEquals(RequirementStatus.Unsatisfied, validate(resistance, 3, resistanceSchema, repeat, fire.instanceId, 0, fire.parameters, listOf(fire)).status())
    }

    @Test fun source_unknown_domain_and_option_require_explicit_context() {
        val schema = listOf(ParameterDefinition("weaponTypeId", ParameterDomain.Unresolved("weapon source missing")))
        assertEquals(RequirementStatus.SourceUnresolved, validate(weapon, 3, schema, RepeatPolicy.Single, "weapon-1", 1, mapOf("weaponTypeId" to "melee")).status())
        assertEquals(RequirementStatus.ChoiceRequired, validateAcquisitionTarget(weapon, 3, emptyList(), RepeatPolicy.Single, "weapon-1", 1, emptyMap(), null, emptyList(), setOf("option-a")).status())
        assertEquals(RequirementStatus.Unsatisfied, validateAcquisitionTarget(weapon, 3, emptyList(), RepeatPolicy.Single, "weapon-1", 1, emptyMap(), "missing-option", emptyList(), setOf("option-a")).status())
        assertEquals(RequirementStatus.SourceUnresolved, validateAcquisitionTarget(weapon, 3, emptyList(), RepeatPolicy.Single, "weapon-1", 1, emptyMap(), "option-a", emptyList(), null).status())
    }

    @Test fun legacy_identity_is_stable_and_scoped_without_rank_or_display_name() {
        val first = OwnedDevelopment(1, 0).toAcquisitionInstance(weapon, legacyOrigin)
        val second = OwnedDevelopment(2, 1).toAcquisitionInstance(weapon, legacyOrigin)
        assertEquals(first.instanceId, second.instanceId)
        assertNotEquals(first.instanceId, OwnedDevelopment(1).toAcquisitionInstance(weapon, legacyOrigin.copy(characterId = "character-2")).instanceId)
        assertNotEquals(first.instanceId, OwnedDevelopment(1).toAcquisitionInstance(weapon, legacyOrigin.copy(ownershipKey = "other-ownership")).instanceId)
        assertNotEquals(first.instanceId, OwnedDevelopment(1).toAcquisitionInstance(DefinitionRef("other-pack", weapon.entryId), legacyOrigin).instanceId)
        assertEquals(null, first.abilityOptionId)
        assertEquals("resolved-option", OwnedDevelopment(1, 7).toAcquisitionInstance(weapon, legacyOrigin, "resolved-option").abilityOptionId)
    }

    @Test fun definition_and_instance_ids_do_not_alias_or_accept_blanks() {
        val a = DefinitionRef("pack:a", "b")
        val b = DefinitionRef("pack", "a:b")
        assertNotEquals(a, b)
        assertFailsWith<IllegalArgumentException> { DefinitionRef("", "entry") }
        assertFailsWith<IllegalArgumentException> { AcquisitionInstance(" ", weapon, 1, emptyMap(), null, newOrigin) }
        val other = AcquisitionInstance("collision", knowledge, 1, mapOf("knowledgeId" to "id"), null, newOrigin)
        assertEquals(RequirementStatus.Conflict, validate(weapon, 3, weaponSchema, RepeatPolicy.Single, "collision", 1, mapOf("weaponTypeId" to "melee"), listOf(other)).status())
        assertEquals(RequirementStatus.Conflict, validate(knowledge, 5, knowledgeSchema, RepeatPolicy.Single, other.instanceId, 2, other.parameters, listOf(other, other)).status())
    }

    @Test fun identity_parameters_are_immutable_snapshots() {
        val parameters = mutableMapOf("resistanceId" to "fire")
        val fire = AcquisitionInstance("fire-1", resistance, 1, parameters, null, newOrigin)
        parameters["resistanceId"] = "poison"
        assertEquals("fire", fire.parameters["resistanceId"])
        assertTrue(fire.parameters !is MutableMap<*, *>)
    }

    private fun validate(ref: DefinitionRef, maxRank: Int, schema: List<ParameterDefinition>, repeat: RepeatPolicy, id: String, rank: Int, parameters: Map<String, String>, instances: List<AcquisitionInstance> = emptyList()): IdentityValidation =
        validateAcquisitionTarget(ref, maxRank, schema, repeat, id, rank, parameters, null, instances)

    private fun IdentityValidation.status(): RequirementStatus = (this as IdentityValidation.Blocked).status
}
