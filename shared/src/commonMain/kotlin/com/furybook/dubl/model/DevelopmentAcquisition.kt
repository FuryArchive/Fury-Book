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

    data class ManaRank(
        val fromRank: Int,
        val toRank: Int,
        override val xpCost: Int,
    ) : DevelopmentAcquisitionStep {
        override val label: String = "Запас маны"
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

class DevelopmentAcquisitionPlanner(
    private val baseCharacter: DublCharacter,
    private val catalog: DevelopmentCatalog,
) {
    private data class State(
        val character: DublCharacter,
        val steps: List<DevelopmentAcquisitionStep> = emptyList(),
        val choices: List<DevelopmentAcquisitionChoice> = emptyList(),
        val unresolved: List<String> = emptyList(),
        val stack: Set<String> = emptySet(),
    )

    private val unlockRules = DevelopmentRules(baseCharacter, catalog, DevelopmentProgress(baseCharacter.development))
    private val unlocksIndex: Map<String, List<DevelopmentEntry>> by lazy {
        val reverse = linkedMapOf<String, MutableList<DevelopmentEntry>>()
        catalog.entries.forEach { candidate ->
            val dependencies = linkedSetOf<String>()
            candidate.accessId?.let(dependencies::add)
            unlockRules.requirements(candidate).mapNotNullTo(dependencies) { it.targetEntryId }
            dependencies
                .asSequence()
                .filter { it != candidate.id }
                .forEach { dependencyId ->
                    reverse.getOrPut(dependencyId) { mutableListOf() } += candidate
                }
        }
        reverse.mapValues { (_, entries) ->
            entries
                .distinctBy { it.id }
                .sortedBy { developmentNormalize(it.name) }
        }
    }

    private fun resolvedTargetOption(entry: DevelopmentEntry, requested: Int?): Int =
        requested ?: cheapestAbilityOption(entry)

    fun plan(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionPlan {
        var state = State(baseCharacter)
        request.targets.forEachIndexed { index, target ->
            val entry = catalog.byId(target.entryId)
            if (entry == null) {
                state = state.copy(unresolved = state.unresolved + "Неизвестная запись: ${target.entryId}")
                return@forEachIndexed
            }
            val currentRank = state.character.developmentRank(entry.id)
            val desiredRank = when {
                !target.includeTarget -> currentRank
                target.targetRank != null -> target.targetRank
                else -> currentRank + 1
            }.coerceIn(0, entry.maxRank.coerceAtLeast(1))

            state = satisfyEntryRequirements(
                state = state,
                entry = entry,
                request = request,
                path = "target-$index-${entry.id}",
            )
            if (target.includeTarget && desiredRank > state.character.developmentRank(entry.id)) {
                state = addDevelopmentRank(
                    state,
                    entry,
                    desiredRank,
                    resolvedTargetOption(entry, target.optionIndex),
                )
            }
        }

        val compact = compactSteps(state.steps)
        val xpCost = compact.sumOf { it.xpCost }
        val abilityCost = compact.sumOf { it.abilityCost }
        val economy = CharacterEconomy.breakdown(baseCharacter, catalog)
        val xpRemainingAfter = economy.remainingXp - xpCost
        val abilityRemainingAfter = economy.abilityPointsRemaining - abilityCost
        val canAfford = xpRemainingAfter >= 0 && abilityRemainingAfter >= 0
        val unresolved = state.unresolved.distinct()
        val canApply = compact.isNotEmpty() && unresolved.isEmpty() && (!request.enforceBudget || canAfford)
        return DevelopmentAcquisitionPlan(
            request = request,
            steps = compact,
            choices = state.choices.distinctBy { it.id },
            unresolvedRequirements = unresolved,
            xpCost = xpCost,
            abilityCost = abilityCost,
            xpRemainingAfter = xpRemainingAfter,
            abilityRemainingAfter = abilityRemainingAfter,
            canAfford = canAfford,
            canApply = canApply,
            projectedCharacter = state.character,
        )
    }

    fun unlocks(entryId: String): List<DevelopmentEntry> = unlocksIndex[entryId].orEmpty()

    fun unlockCounts(): Map<String, Int> = unlocksIndex.mapValues { (_, entries) -> entries.size }

    private fun satisfyEntryRequirements(
        state: State,
        entry: DevelopmentEntry,
        request: DevelopmentAcquisitionRequest,
        path: String,
    ): State {
        if (entry.id in state.stack) {
            return state.copy(unresolved = state.unresolved + "${entry.name}: цикл требований")
        }
        var next = state.copy(stack = state.stack + entry.id)

        entry.accessId?.let { accessId ->
            val access = catalog.byId(accessId)
            next = if (access == null) {
                next.copy(unresolved = next.unresolved + "${entry.name}: неизвестная ветка доступа")
            } else {
                satisfyAndAcquireDevelopment(next, access, 1, cheapestAbilityOption(access), request, "$path-access")
            }
        }

        if (entry.perfectRoot) {
            val conflicting = next.character.development.keys
                .mapNotNull(catalog::byId)
                .firstOrNull { it.perfectRoot && it.id != entry.id && next.character.developmentRank(it.id) > 0 }
            if (conflicting != null) {
                next = next.copy(unresolved = next.unresolved + "Только одна совершенная способность; уже выбрана: ${conflicting.name}")
            }
        }

        val raw = entry.requirements.trim(' ', '.', ',', ';')
        if (raw.isNotBlank() && raw != "-" && raw != "—") {
            requirementParts(raw).forEachIndexed { index, atom ->
                next = satisfyAtom(next, atom, entry, request, "$path-r$index")
            }
        }

        if (entry.mechanicsConflict.isNotBlank()) {
            next = next.copy(unresolved = next.unresolved + entry.mechanicsConflict)
        }
        if (entry.incomplete) {
            next = next.copy(unresolved = next.unresolved + "${entry.name}: запись книги не завершена")
        }
        return next.copy(stack = state.stack)
    }

    private fun satisfyAtom(
        state: State,
        source: String,
        owner: DevelopmentEntry,
        request: DevelopmentAcquisitionRequest,
        path: String,
    ): State {
        val text = source.trim(' ', '.', ';')
        if (text.isBlank() || text == "-" || text == "—") return state

        if (Regex("только\\s+при\\s+создании|при\\s+создании\\s+персонажа", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return if (!state.character.creationComplete || state.character.developmentRank(owner.id) > 0) state else {
                state.copy(unresolved = state.unresolved + "$text — создание уже завершено")
            }
        }
        if (Regex("на усмотрение|согласован", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return state.copy(unresolved = state.unresolved + text)
        }

        martialArtsAlternatives(text)?.let { alternatives ->
            if (alternatives.isEmpty()) return state.copy(unresolved = state.unresolved + text)
            return chooseAlternative(state, text, alternatives, owner, request, path)
        }

        if (Regex("\\sили\\s", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            val parts = text.split(Regex("\\s+или\\s+", RegexOption.IGNORE_CASE))
            val trailingRank = Regex("\\s(\\d+)$").find(parts.last())?.groupValues?.getOrNull(1)
            val expanded = if (trailingRank != null) {
                parts.map { part -> if (Regex("\\d+$").containsMatchIn(part.trim())) part else "$part $trailingRank" }
            } else parts
            return chooseAlternative(state, text, expanded, owner, request, path)
        }

        manaRequirementRankOrNull(text)?.let { needMana ->
            val current = state.character.magic.manaRank
            if (current >= needMana) return state
            if (needMana !in 1..5) {
                return state.copy(unresolved = state.unresolved + "$text — допустимый ранг запаса маны 1–5")
            }
            if (state.character.creationComplete) {
                return state.copy(unresolved = state.unresolved + "$text — базовый запас маны можно повышать только при создании персонажа")
            }
            val nextCharacter = state.character.copy(
                magic = state.character.magic.copy(manaRank = needMana),
                manaEnabled = true,
            )
            return state.copy(
                character = nextCharacter,
                steps = state.steps + DevelopmentAcquisitionStep.ManaRank(
                    fromRank = current,
                    toRank = needMana,
                    xpCost = (needMana - current) * 100,
                ),
            )
        }

        if (developmentNormalize(text).startsWith("заклинание:") ||
            Regex("^Знать\\s*\\d+\\s*заклинани", RegexOption.IGNORE_CASE).containsMatchIn(text) ||
            Regex("^Любые (два|три) боевых крика", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) {
            val check = DevelopmentRules(state.character, catalog, DevelopmentProgress(state.character.development))
                .requirements(owner)
                .firstOrNull { developmentNormalize(it.text).contains(developmentNormalize(text.substringBefore(':'))) }
            return if (check?.status == RequirementStatus.OK) state else state.copy(unresolved = state.unresolved + text)
        }

        val numeric = Regex("^(.+?)\\s*:?\\s*(\\d+)(?:\\s*ранг(?:а|ов)?)?$", RegexOption.IGNORE_CASE).matchEntire(text)
        if (numeric != null) {
            val name = numeric.groupValues[1].trim()
            val need = numeric.groupValues[2].toIntOrNull() ?: 1
            satisfyAttribute(state, name, need)?.let { return it }
            satisfySkill(state, name, need)?.let { return it }
            satisfyDevelopmentByName(state, name, need, request, path)?.let { return it }

            val status = DevelopmentRules(state.character, catalog, DevelopmentProgress(state.character.development))
                .requirements(owner)
                .firstOrNull { developmentNormalize(it.text).startsWith(developmentNormalize(name)) }
            if (status?.status == RequirementStatus.OK) return state
            return state.copy(unresolved = state.unresolved + text)
        }

        satisfySkill(state, text, 1)?.let { return it }
        satisfyDevelopmentByName(state, text, 1, request, path)?.let { return it }

        val status = DevelopmentRules(state.character, catalog, DevelopmentProgress(state.character.development))
            .requirements(owner)
            .firstOrNull { developmentNormalize(it.text).contains(developmentNormalize(text)) }
        return if (status?.status == RequirementStatus.OK) state else state.copy(unresolved = state.unresolved + text)
    }

    private fun chooseAlternative(
        state: State,
        label: String,
        alternatives: List<String>,
        owner: DevelopmentEntry,
        request: DevelopmentAcquisitionRequest,
        path: String,
    ): State {
        val choiceId = "$path:${developmentNormalize(label)}"
        val candidates = alternatives.mapIndexed { index, alternative ->
            index to satisfyAtom(state, alternative, owner, request, "$path-o$index")
        }
        val options = candidates.mapIndexed { index, (_, candidate) ->
            DevelopmentAcquisitionChoiceOption(
                label = alternatives[index].trim(),
                xpCost = candidate.steps.drop(state.steps.size).sumOf { it.xpCost },
                abilityCost = candidate.steps.drop(state.steps.size).sumOf { it.abilityCost },
                unresolvedCount = (candidate.unresolved.size - state.unresolved.size).coerceAtLeast(0),
            )
        }
        val requestedIndex = request.choiceSelections[choiceId]
        val selectedIndex = requestedIndex?.coerceIn(0, candidates.lastIndex) ?: run {
            candidates.indices
                .filter { options[it].unresolvedCount == 0 }
                .minWithOrNull(compareBy<Int>({ options[it].abilityCost }, { options[it].xpCost }))
                ?: candidates.indices.minWithOrNull(compareBy<Int>({ options[it].unresolvedCount }, { options[it].abilityCost }, { options[it].xpCost }))
                ?: 0
        }
        val selected = candidates[selectedIndex].second
        val choice = DevelopmentAcquisitionChoice(
            id = choiceId,
            label = label,
            options = options,
            selectedIndex = selectedIndex,
        )
        return selected.copy(choices = state.choices + choice + selected.choices.drop(state.choices.size))
    }

    private fun satisfyAttribute(state: State, name: String, need: Int): State? {
        val normalized = developmentAlias(name)
        val attribute = AttributeId.entries.firstOrNull {
            developmentAlias(it.title) == normalized || developmentAlias(it.shortTitle) == normalized
        } ?: return null
        val actual = state.character.attribute(attribute)
        if (actual >= need) return state
        val base = state.character.attributes.getValue(attribute).base
        val requiredBase = base + (need - actual)
        if (requiredBase !in -5..10) {
            return state.copy(unresolved = state.unresolved + "$name $need: значение характеристики вне допустимого диапазона")
        }
        val fromCost = CharacterEconomy.attributeCost(base)
        val toCost = CharacterEconomy.attributeCost(requiredBase)
        if (fromCost == null || toCost == null) {
            return state.copy(unresolved = state.unresolved + "$name $need: стоимость характеристики не определена")
        }
        val updatedAttributes = state.character.attributes + (attribute to state.character.attributes.getValue(attribute).copy(base = requiredBase))
        val updatedCharacter = state.character.copy(attributes = updatedAttributes)
        return state.copy(
            character = updatedCharacter,
            steps = state.steps + DevelopmentAcquisitionStep.Attribute(
                attribute = attribute,
                fromValue = actual,
                toValue = updatedCharacter.attribute(attribute),
                fromBase = base,
                toBase = requiredBase,
                xpCost = toCost - fromCost,
            ),
        )
    }

    private fun satisfySkill(state: State, name: String, need: Int): State? {
        val normalized = developmentAlias(name)
        val all = state.character.resolvedSkills(includeHidden = true)
        val exact = all.filter { developmentAlias(it.name) == normalized }
        val matches = if (exact.isNotEmpty()) exact else all.filter { skill ->
            val skillName = developmentAlias(skill.name)
            normalized.length >= 4 && (skillName.startsWith(normalized) || normalized.startsWith(skillName))
        }.takeIf { candidates -> candidates.map { developmentAlias(it.name) }.distinct().size == 1 }.orEmpty()
        val skill = matches.maxByOrNull { it.rank } ?: return null
        if (skill.rank >= need) return state
        if (need > 10) return state.copy(unresolved = state.unresolved + "$name $need: максимальный ранг умения 10")
        val from = state.character.skillXpCostForRank(skill.rank)
        val to = state.character.skillXpCostForRank(need)
        val currentState = state.character.skills[skill.id] ?: CharacterSkill(id = skill.id, definitionId = skill.definition?.id ?: skill.id)
        val nextCharacter = state.character.copy(
            skills = state.character.skills + (skill.id to currentState.copy(rank = need)),
        )
        return state.copy(
            character = nextCharacter,
            steps = state.steps + DevelopmentAcquisitionStep.Skill(
                skillId = skill.id,
                name = skill.name,
                fromRank = skill.rank,
                toRank = need,
                xpCost = to - from,
            ),
        )
    }

    private fun satisfyDevelopmentByName(
        state: State,
        name: String,
        need: Int,
        request: DevelopmentAcquisitionRequest,
        path: String,
    ): State? {
        val known = catalog.matchingName(name)
        val preferred = known.filterNot { it.isAbility }.ifEmpty { known }
        val entry = preferred.firstOrNull() ?: return null
        if (need > entry.maxRank.coerceAtLeast(1)) {
            return state.copy(unresolved = state.unresolved + "$name $need: требование выше максимального ранга")
        }
        return satisfyAndAcquireDevelopment(state, entry, need, cheapestAbilityOption(entry), request, path)
    }

    private fun satisfyAndAcquireDevelopment(
        state: State,
        entry: DevelopmentEntry,
        rank: Int,
        optionIndex: Int,
        request: DevelopmentAcquisitionRequest,
        path: String,
    ): State {
        if (state.character.developmentRank(entry.id) >= rank) return state
        val withRequirements = satisfyEntryRequirements(state, entry, request, path)
        return addDevelopmentRank(withRequirements, entry, rank, optionIndex)
    }

    private fun addDevelopmentRank(
        state: State,
        entry: DevelopmentEntry,
        targetRank: Int,
        optionIndex: Int,
    ): State {
        val current = state.character.developmentRank(entry.id)
        val toRank = targetRank.coerceIn(current, entry.maxRank.coerceAtLeast(1))
        if (toRank <= current) return state
        val resolvedOption = if (entry.isAbility) optionIndex.coerceIn(0, (entry.abilityOptions.size - 1).coerceAtLeast(0)) else 0
        val xp = if (entry.isAbility) 0 else entry.cost.coerceAtLeast(0) * (toRank - current)
        val ap = if (entry.isAbility) {
            val perRank = if (entry.abilityOptions.isNotEmpty()) entry.abilityOptions[resolvedOption].value.coerceAtLeast(0) else entry.cost.coerceAtLeast(0)
            perRank * (toRank - current)
        } else 0
        val nextDevelopment = state.character.development + (entry.id to OwnedDevelopment(toRank, resolvedOption))
        return state.copy(
            character = state.character.copy(development = nextDevelopment),
            steps = state.steps + DevelopmentAcquisitionStep.Development(
                entryId = entry.id,
                name = entry.name,
                fromRank = current,
                toRank = toRank,
                optionIndex = resolvedOption,
                xpCost = xp,
                abilityCost = ap,
            ),
        )
    }

    private fun cheapestAbilityOption(entry: DevelopmentEntry): Int {
        if (!entry.isAbility || entry.abilityOptions.isEmpty()) return 0
        return entry.abilityOptions.indices.minByOrNull { entry.abilityOptions[it].value } ?: 0
    }

    private fun requirementParts(raw: String): List<String> = raw
        .split(Regex("[;\\n]+"))
        .flatMap { segment ->
            val clean = segment.trim()
            if (Regex("^Боевые\\s+искусства\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
                listOf(clean)
            } else {
                clean.split(Regex(",+(?![^()]*\\))"))
            }
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun martialArtsAlternatives(text: String): List<String>? {
        val match = Regex(
            "^Боевые\\s+искусства\\s*(?:\\(([^)]+)\\)|:\\s*(.+))$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(text) ?: return null
        val raw = match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
        val names = raw.split(Regex("\\s*,\\s*|\\s+или\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim(' ', '.', ',') }
            .filter { it.isNotBlank() && developmentNormalize(it) != "любое" }
        return names.mapNotNull { name ->
            catalog.matchingName(name).firstOrNull { candidate ->
                candidate.isMartialArt && candidate.tags.any { developmentNormalize(it) == "боевой стиль" }
            }?.name
        }.distinct()
    }

    private fun compactSteps(steps: List<DevelopmentAcquisitionStep>): List<DevelopmentAcquisitionStep> {
        val result = mutableListOf<DevelopmentAcquisitionStep>()
        steps.forEach { step ->
            val index = result.indexOfFirst { existing ->
                when {
                    existing is DevelopmentAcquisitionStep.Attribute && step is DevelopmentAcquisitionStep.Attribute -> existing.attribute == step.attribute
                    existing is DevelopmentAcquisitionStep.Skill && step is DevelopmentAcquisitionStep.Skill -> existing.skillId == step.skillId
                    existing is DevelopmentAcquisitionStep.ManaRank && step is DevelopmentAcquisitionStep.ManaRank -> true
                    existing is DevelopmentAcquisitionStep.Development && step is DevelopmentAcquisitionStep.Development -> existing.entryId == step.entryId
                    else -> false
                }
            }
            if (index < 0) {
                result += step
            } else {
                val existing = result[index]
                result[index] = when {
                    existing is DevelopmentAcquisitionStep.Attribute && step is DevelopmentAcquisitionStep.Attribute -> existing.copy(
                        toValue = step.toValue,
                        toBase = step.toBase,
                        xpCost = existing.xpCost + step.xpCost,
                    )
                    existing is DevelopmentAcquisitionStep.Skill && step is DevelopmentAcquisitionStep.Skill -> existing.copy(
                        toRank = step.toRank,
                        xpCost = existing.xpCost + step.xpCost,
                    )
                    existing is DevelopmentAcquisitionStep.ManaRank && step is DevelopmentAcquisitionStep.ManaRank -> existing.copy(
                        toRank = step.toRank,
                        xpCost = existing.xpCost + step.xpCost,
                    )
                    existing is DevelopmentAcquisitionStep.Development && step is DevelopmentAcquisitionStep.Development -> existing.copy(
                        toRank = step.toRank,
                        optionIndex = step.optionIndex,
                        xpCost = existing.xpCost + step.xpCost,
                        abilityCost = existing.abilityCost + step.abilityCost,
                    )
                    else -> step
                }
            }
        }
        return result
    }
}
