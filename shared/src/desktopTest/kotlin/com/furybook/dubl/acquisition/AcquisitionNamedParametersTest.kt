package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlin.test.*

class AcquisitionNamedParametersTest {
    private val catalog by lazy { loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
        FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog }

    @Test fun actual_weapon_choice_keeps_source_class_and_explicit_display_name() {
        val definition=catalog.definitions.getValue(DefinitionRef("dubl-3.69","feat_2334f8b24105194c"))
        val choice=definition.parameterDefinitions.single { it.key=="weaponId" }.choices.single { it.id=="gear_397435f006fed83a" }
        fun check(parameters:Map<String,String>)=validateAcquisitionTarget(definition.ref,checkNotNull(definition.maxRank),
            definition.parameterDefinitions,definition.repeatPolicy,"weapon-specialization",1,parameters,null,emptyList(),catalog=catalog)
        val parameters=mapOf("weaponId" to choice.id,"weaponName" to choice.label,"weaponTypeId" to "ranged")
        assertEquals(IdentityValidation.NewInstance,check(parameters))
        assertEquals(RequirementStatus.Conflict,assertIs<IdentityValidation.Blocked>(check(parameters+("weaponTypeId" to "melee"))).status)
        assertEquals(IdentityValidation.NewInstance,check(mapOf("weaponId" to "custom:blaster", "weaponName" to "Мой бластер", "weaponTypeId" to "ranged")))
        assertIs<IdentityValidation.Blocked>(check(mapOf("weaponId" to "custom:blaster", "weaponName" to "Мой бластер")))
        assertEquals(RequirementStatus.ChoiceRequired,assertIs<IdentityValidation.Blocked>(check(parameters+("weaponId" to "custom:counterfeit"))).status)
    }

    @Test fun actual_combat_stimulus_fortitude_uses_the_shared_character_property_once() {
        fun properties(node:RequirementNode):List<RequirementNode.NumericProperty> = when(node) {
            is RequirementNode.NumericProperty -> listOf(node)
            is RequirementNode.AllOf -> node.children.flatMap(::properties)
            is RequirementNode.AnyOf -> node.children.flatMap(::properties)
            else -> emptyList()
        }
        val definition=catalog.definitions.getValue(DefinitionRef("dubl-3.69","feat_80a24decd57bf883"))
        val fortitude=properties(definition.requirements).single { it.propertyId=="FORTITUDE" }
        assertEquals(1,fortitude.operand)
        val character=DublCharacter("fortitude",attributes=AttributeId.entries.associateWith {
            AttributeValue(when(it) { AttributeId.CONSTITUTION->2;AttributeId.WILL->1;else->0 })
        },development=mapOf(DevelopmentEffectIds.STALWART to OwnedDevelopment(2)))
        val context=AcquisitionContext(character,emptyList(),emptySet(),RequirementNode.CreationPhase.Phase.Creating,emptyList(),catalog)
        val proof=evaluate(fortitude,context)
        assertEquals(RequirementValue.Number(5),proof.actualValue)
        assertEquals(RequirementStatus.Satisfied,proof.status)
        assertEquals("Стойкость",AcquisitionLabels(catalog).requirement(fortitude))
    }

    @Test fun actual_organization_picker_reuses_saved_identity_across_access_and_feature() {
        val access=DefinitionRef("dubl-3.69","access_b832538f3a803048")
        val feature=catalog.definitions.getValue(DefinitionRef("dubl-3.69","feat_3acde4a62c69d01d"))
        val phase=RequirementNode.CreationPhase.Phase.Creating
        val stored=StoredCharacter(DublCharacter("contacts"),CharacterSheetExtras(),listOf(AcquisitionInstance("contacts-access",access,1,
            mapOf("organizationId" to "saved-organization", "organizationName" to "Братство"),null,AcquisitionOrigin.New)),
            Ledger(Opening("opening","native-contacts",Amount(100,3),phase,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
        val field=acquisitionParameterFields(feature,stored,catalog,1).single { it.key=="organizationId" }
        assertEquals("organizationName",field.displayNameKey)
        assertNotNull(field.identityDomain)
        assertEquals("Братство",field.options.single { it.id=="saved-organization" }.label)
        val selected=AcquisitionNamedParameters.selectOption(field,"saved-organization",emptyMap())
        assertEquals(mapOf("organizationId" to "saved-organization","organizationName" to "Братство"),selected)
        assertTrue(AcquisitionNamedParameters.complete(listOf(field),selected))
        assertFalse(AcquisitionNamedParameters.complete(listOf(field),selected-("organizationName")))
        assertEquals("Связи (Братство)",AcquisitionLabels(catalog).acquisition(feature.ref,selected))
    }

    @Test fun actual_named_development_instances_remain_independently_visible_after_reopen() {
        val definition=catalog.definitions.getValue(DefinitionRef("dubl-3.69","feat_2334f8b24105194c"))
        val phase=RequirementNode.CreationPhase.Phase.Creating
        val choices=definition.parameterDefinitions.single { it.key=="weaponId" }.choices.take(2)
        val instances=choices.mapIndexed { index,choice -> AcquisitionInstance("weapon-$index",definition.ref,index+1,
            choice.parameterValues+mapOf("weaponId" to choice.id,"weaponName" to choice.label),null,AcquisitionOrigin.New) }
        val original=StoredCharacter(DublCharacter("weapons"),CharacterSheetExtras(),instances,
            Ledger(Opening("opening","native-weapons",Amount(100,3),phase,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
        val reopened=assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(original))).state
        assertTrue(reopened.character.development.isEmpty())
        val view=canonicalView(AcquisitionScope("scope","weapons",0,0,0),reopened,catalog,AcquisitionCapabilities(true,false,true))
        val shown=view.ownedDevelopmentInstances(unprojectedOnly=true)
        assertEquals(listOf("weapon-0","weapon-1"),shown.map { it.instanceId })
        assertEquals(listOf(1,2),shown.map { it.rank })
        assertEquals(instances.map { AcquisitionLabels(catalog).acquisition(it.definition,it.parameters) },shown.map { it.label })
        assertEquals(instances.map { it.parameters },shown.map { it.parameters })
    }
}
