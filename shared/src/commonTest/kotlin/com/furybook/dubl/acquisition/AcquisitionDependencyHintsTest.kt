package com.furybook.dubl.acquisition

import kotlin.test.*

class AcquisitionDependencyHintsTest {
    private val source = RuleSourceRef("display-fixture", "1", "Explicit synthetic dependencies")
    private fun noRequirements() = RequirementNode.AllOf("none", source, emptyList(), NoRequirementsEvidence(source, "None"))
    private fun definition(ref: DefinitionRef, requirement: RequirementNode = noRequirements()) = AcquisitionDefinition(
        ref, AcquisitionKind.DEVELOPMENT, 3, RepeatPolicy.Single, requirement, PriceRule.Fixed(20, 0, source),
        SourceStatus.Confirmed, "Одинаковое имя", source)

    @Test fun typed_dependencies_are_pack_aware_deduplicated_and_not_eligibility() {
        val access = DefinitionRef("core", "access")
        val dependent = DefinitionRef("core", "dependent")
        val sameId = DefinitionRef("addon", "access")
        val rival = DefinitionRef("core", "rival")
        val requirement = RequirementNode.AllOf("both", source, listOf(
            RequirementNode.BranchAccess("branch", source, access.packId, access.entryId),
            RequirementNode.OwnedRank("rank", source, access.packId, access.entryId, ComparisonOperator.GE, 1)))
        val catalog = AcquisitionCatalog(listOf(definition(access), definition(sameId), definition(dependent, requirement),
            definition(rival, RequirementNode.Exclusive("exclusive", source,
                RequirementNode.OwnedRank("opponent", source, access.packId, access.entryId, ComparisonOperator.GE, 1))))
            .associateBy { it.ref }, "catalog", "rules")
        val hints = AcquisitionDependencyHints(catalog)
        assertEquals(listOf(dependent), hints.dependents(access))
        assertTrue(hints.dependents(sameId).isEmpty())
        assertEquals(mapOf(access to 1), hints.counts())
        val cache = AcquisitionDependencyHintCache()
        val cached = cache.get(catalog)
        assertSame(cached, cache.get(catalog.copy()))
        assertNotSame(cached, cache.get(catalog.copy(catalogRevision = "next")))
        assertEquals(PriceRule.Fixed(20, 0, source), catalog.definitions.getValue(access).priceRule)
    }

    @Test fun nested_alternatives_and_family_members_are_hints_in_stable_order() {
        val skill = DefinitionRef("core", "skill")
        val first = DefinitionRef("a", "feat")
        val second = DefinitionRef("b", "feat")
        val family = RequirementNode.SkillFamily("family", source, "core", "weapons", ComparisonOperator.GE, 3)
        val catalog = AcquisitionCatalog(listOf(
            definition(second, RequirementNode.AnyOf("or", source, listOf(family, noRequirements()))),
            definition(skill).copy(kind = AcquisitionKind.SKILL, familyIds = setOf("weapons")),
            definition(first, family)).associateBy { it.ref }, "catalog", "rules")
        assertEquals(listOf(first, second), AcquisitionDependencyHints(catalog).dependents(skill))
    }
}
