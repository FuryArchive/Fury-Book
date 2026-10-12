package com.furybook.dubl.model

data class DevelopmentAcquisitionTarget(
    val entryId: String,
    val includeTarget: Boolean = true,
    val targetRank: Int? = null,
    val optionIndex: Int? = null,
)

data class DevelopmentAcquisitionRequest(
    val targets: List<DevelopmentAcquisitionTarget>,
    val choiceSelections: Map<String, Int> = emptyMap(),
    val enforceBudget: Boolean = true,
) {
    companion object {
        fun single(
            entryId: String,
            includeTarget: Boolean = true,
            targetRank: Int? = null,
            optionIndex: Int? = null,
            choiceSelections: Map<String, Int> = emptyMap(),
            enforceBudget: Boolean = true,
        ): DevelopmentAcquisitionRequest = DevelopmentAcquisitionRequest(
            targets = listOf(
                DevelopmentAcquisitionTarget(
                    entryId = entryId,
                    includeTarget = includeTarget,
                    targetRank = targetRank,
                    optionIndex = optionIndex,
                ),
            ),
            choiceSelections = choiceSelections,
            enforceBudget = enforceBudget,
        )
    }
}

sealed interface DevelopmentAcquisitionStep {
    val label: String
    val xpCost: Int
    val abilityCost: Int

    data class Attribute(
        val attribute: AttributeId,
        val fromValue: Int,
        val toValue: Int,
        val fromBase: Int,
        val toBase: Int,
        override val xpCost: Int,
    ) : DevelopmentAcquisitionStep {
        override val label: String = attribute.title
        override val abilityCost: Int = 0
    }

    data class Skill(
        val skillId: String,
        val name: String,
        val fromRank: Int,
        val toRank: Int,
        override val xpCost: Int,
    ) : DevelopmentAcquisitionStep {
        override val label: String = name
        override val abilityCost: Int = 0
    }

    data class Development(
        val entryId: String,
        val name: String,
        val fromRank: Int,
        val toRank: Int,
        val optionIndex: Int,
        override val xpCost: Int,
        override val abilityCost: Int,
    ) : DevelopmentAcquisitionStep {
        override val label: String = name
    }
}

data class DevelopmentAcquisitionChoiceOption(
    val label: String,
    val xpCost: Int,
    val abilityCost: Int,
    val unresolvedCount: Int,
)

data class DevelopmentAcquisitionChoice(
    val id: String,
    val label: String,
    val options: List<DevelopmentAcquisitionChoiceOption>,
    val selectedIndex: Int,
)

data class DevelopmentAcquisitionPlan(
    val request: DevelopmentAcquisitionRequest,
    val steps: List<DevelopmentAcquisitionStep>,
    val choices: List<DevelopmentAcquisitionChoice>,
    val unresolvedRequirements: List<String>,
    val xpCost: Int,
    val abilityCost: Int,
    val xpRemainingAfter: Int,
    val abilityRemainingAfter: Int,
    val canAfford: Boolean,
    val canApply: Boolean,
    val projectedCharacter: DublCharacter,
)

data class DevelopmentAcquisitionResult(
    val applied: Boolean,
    val plan: DevelopmentAcquisitionPlan,
    val message: String = "",
)

/** Staged compatibility boundary. Old callers cannot supply canonical finance or stable choices. */
class DevelopmentAcquisitionPlanner(
    private val baseCharacter: DublCharacter,
    private val catalog: DevelopmentCatalog,
) {
    fun plan(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionPlan = DevelopmentAcquisitionPlan(
        request, emptyList(), emptyList(),
        listOf("Canonical StoredCharacter, AcquisitionCatalog and stable acquisition selections are required"),
        0, 0, 0, 0, false, false, baseCharacter,
    )

    /** Typed callers share exactly the canonical planner; numeric legacy choices are never converted. */
    fun plan(request: com.furybook.dubl.acquisition.AcquisitionRequest,
        state: com.furybook.dubl.acquisition.StoredCharacter,
        catalog: com.furybook.dubl.acquisition.AcquisitionCatalog): com.furybook.dubl.acquisition.AcquisitionPlan =
        com.furybook.dubl.acquisition.plan(request, state, catalog)

    /** Display navigation only. No parsing, eligibility or purchase semantics are inferred here. */
    fun unlocks(entryId: String): List<DevelopmentEntry> = catalog.entries.filter { it.accessId == entryId }
    fun unlockCounts(): Map<String, Int> = catalog.entries.mapNotNull { it.accessId }.distinct().associateWith { unlocks(it).size }
}
