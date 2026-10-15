package com.furybook.dubl.acquisition

/** Reverse navigation hints only. An edge never establishes admission, ownership or a price.
 * References come exclusively from typed requirements, never from descriptive source text.
 */
class AcquisitionDependencyHints(catalog: AcquisitionCatalog) {
    private val reverse: Map<DefinitionRef, List<DefinitionRef>>

    init {
        val edges = linkedMapOf<DefinitionRef, MutableSet<DefinitionRef>>()
        for (dependent in catalog.definitions.values) {
            fun add(reference: DefinitionRef) {
                if (reference in catalog.definitions && reference != dependent.ref)
                    edges.getOrPut(reference) { linkedSetOf() }.add(dependent.ref)
            }
            fun visit(node: RequirementNode) {
                when (node) {
                    is RequirementNode.AllOf -> node.children.forEach(::visit)
                    is RequirementNode.AnyOf -> node.children.forEach(::visit)
                    is RequirementNode.DistinctCount -> visit(node.predicate)
                    is RequirementNode.SelectedInstance -> visit(node.predicate)
                    is RequirementNode.OwnedRank -> add(DefinitionRef(node.packId, node.entryId))
                    is RequirementNode.Specialization -> add(DefinitionRef(node.packId, node.entryId))
                    is RequirementNode.BranchAccess -> if (node.operand) add(DefinitionRef(node.packId, node.branchId))
                    is RequirementNode.LearnedSpell -> if (node.operand) add(DefinitionRef(node.packId, node.entryId))
                    is RequirementNode.SkillFamily -> {
                        val exact = DefinitionRef(node.packId, node.familyId)
                        if (exact in catalog.definitions) add(exact)
                        else catalog.definitions.values.filter {
                            it.ref.packId == node.packId && node.familyId in it.familyIds
                        }.forEach { add(it.ref) }
                    }
                    // A prohibition is not something this acquisition unlocks.
                    is RequirementNode.Exclusive, is RequirementNode.Attribute, is RequirementNode.NumericProperty,
                    is RequirementNode.CreationPhase, is RequirementNode.ExternalEvidence,
                    is RequirementNode.Unresolved -> Unit
                }
            }
            visit(dependent.requirements)
        }
        val order = compareBy<DefinitionRef>({ catalog.definitions[it]?.name.orEmpty() }, { it.packId }, { it.entryId })
        reverse = edges.mapValues { (_, values) -> EnvelopeList(values.sortedWith(order)) }
    }

    fun dependents(definition: DefinitionRef): List<DefinitionRef> = reverse[definition].orEmpty()
    fun counts(): Map<DefinitionRef, Int> = reverse.mapValues { it.value.size }
}

/** Caller-owned, single presentation preparation lane. Reuse only the same authority snapshot. */
class AcquisitionDependencyHintCache {
    private var catalog: AcquisitionCatalog? = null
    private var index: AcquisitionDependencyHints? = null
    fun get(value: AcquisitionCatalog): AcquisitionDependencyHints {
        val previous = catalog
        if (previous?.catalogRevision == value.catalogRevision && previous.resolutionRevision == value.resolutionRevision &&
            previous.sourceRuleRevision == value.sourceRuleRevision && previous.definitions == value.definitions)
            return checkNotNull(index)
        return AcquisitionDependencyHints(value).also { catalog = value; index = it }
    }
}
