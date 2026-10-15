package com.furybook.dubl.acquisition

import com.furybook.dubl.model.DublCharacter
import kotlin.test.*

class AcquisitionParameterMetadataTest {
    private val source = RuleSourceRef("current-core", "native-revision", "Each performance has its own ranks")

    @Test fun parameter_metadata_owns_choices_and_exclusions_before_and_after_context_capture() {
        val bindings=mutableMapOf("typeId" to "performance")
        val choices = mutableListOf(ParameterChoice("dance", "Танцы", source,bindings))
        val exclusions = mutableListOf(ParameterExcludedLabel("Вокал", source))
        val parameter = ParameterDefinition("specialization", ParameterDomain.UserDefinedIds, choices, exclusions)
        val ref = DefinitionRef("dubl-3.69", "performance_template")
        val definition = AcquisitionDefinition(ref, AcquisitionKind.SKILL, 10, RepeatPolicy.DistinctParameter(setOf("specialization")),
            RequirementNode.AllOf("none", source, emptyList(), NoRequirementsEvidence(source, "No additional admission")),
            PriceRule.PerRank(10, 0, source), SourceStatus.Confirmed, "Исполнение (Любое)", source, listOf(parameter))
        val context = AcquisitionContext(DublCharacter("hero"), emptyList(), emptySet(), RequirementNode.CreationPhase.Phase.Creating,
            emptyList(), AcquisitionCatalog(mapOf(ref to definition), "catalog", "rules"))
        choices.clear()
        exclusions.clear()
        bindings.clear()
        for (captured in listOf(parameter, context.catalog.definitions.getValue(ref).parameterDefinitions.single())) {
            assertEquals(listOf("dance"), captured.choices.map { it.id })
            assertEquals(listOf("Вокал"), captured.excludedLabels.map { it.label })
            assertEquals(mapOf("typeId" to "performance"),captured.choices.single().parameterValues)
            assertFalse(captured.choices.single().parameterValues is MutableMap<*,*>)
            assertFalse(captured.choices is MutableList<*>)
            assertFalse(captured.excludedLabels is MutableList<*>)
        }
    }

    @Test fun ordered_owned_selection_captures_the_source_definition_set() {
        val ref=DefinitionRef("dubl-3.69","martial_technique_c4972b9f356e9d3d")
        val technique=DefinitionRef("dubl-3.69","feat_7767abfc9b7aed0c")
        val definitions=mutableSetOf(technique)
        val selection=ParameterSelectionSource.OrderedOwnedInstances(definitions,1,true)
        val parameter=ParameterDefinition("comboSequence",ParameterDomain.UserDefinedIds,selectionSource=selection,displayNameKey="comboSequenceNames")
        val definition=AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,3,RepeatPolicy.DistinctParameter(setOf("comboSequence")),
            RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"No additional admission")),
            PriceRule.PerRank(10,0,source),SourceStatus.Confirmed,"Комбо",source,listOf(parameter))
        val context=AcquisitionContext(DublCharacter("hero"),emptyList(),emptySet(),RequirementNode.CreationPhase.Phase.Creating,
            emptyList(),AcquisitionCatalog(mapOf(ref to definition),"catalog","rules"))
        definitions.clear()
        val captured=assertIs<ParameterSelectionSource.OrderedOwnedInstances>(context.catalog.definitions.getValue(ref).parameterDefinitions.single().selectionSource)
        assertEquals(setOf(technique),captured.definitions)
        assertFalse(captured.definitions is MutableSet<*>)
        assertEquals(4,captured.lengthAt(3))
    }
}
