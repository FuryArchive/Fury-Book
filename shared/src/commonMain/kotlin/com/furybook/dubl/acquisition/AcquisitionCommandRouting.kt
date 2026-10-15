package com.furybook.dubl.acquisition

import com.furybook.dubl.model.AttributeId

/** Identity-only adapters. Target ranks and stable selections are captured before suspension. */
sealed interface AcquisitionIntent {
    data class Target(val kind: AcquisitionKind, val target: AcquisitionTarget) : AcquisitionIntent
    data class BulkTargets(val request: AcquisitionRequest) : AcquisitionIntent
    companion object {
        fun attribute(id: AttributeId, rank: Int) = Target(AcquisitionKind.ATTRIBUTE, AcquisitionTarget(DefinitionRef("dubl-3.69", "attribute:${id.name}"), targetRank=rank))
        fun skill(target: AcquisitionTarget) = Target(AcquisitionKind.SKILL, target)
        fun development(target: AcquisitionTarget) = Target(AcquisitionKind.DEVELOPMENT, target)
        fun mana(rank: Int) = Target(AcquisitionKind.MANA, AcquisitionTarget(DefinitionRef("dubl-3.69", "mana:rank"), targetRank=rank))
        fun school(target: AcquisitionTarget) = Target(AcquisitionKind.SCHOOL, target)
        fun learnSpell(target: AcquisitionTarget) = Target(AcquisitionKind.SPELL, target)
        fun chi(rank: Int) = Target(AcquisitionKind.CHI_BONUS, AcquisitionTarget(DefinitionRef("dubl-chi-3.69", "chi:bonus-ranks"), targetRank=rank))
    }
}
sealed interface AcquisitionRouteResult {
    data class QuoteRequest(val request: AcquisitionRequest) : AcquisitionRouteResult
    data class NeedsExplicitSelection(val message: String, val candidates: List<String>) : AcquisitionRouteResult
    data class ManualReviewRequired(val message: String = "Уменьшение требует явной суммы и причины коррекции") : AcquisitionRouteResult
    data class Blocked(val message: String) : AcquisitionRouteResult
}
object AcquisitionCommandRouting {
    fun request(intent: AcquisitionIntent, scope: AcquisitionScope, state: StoredCharacter, catalog: AcquisitionCatalog): AcquisitionRouteResult {
        if (state.character.id != scope.characterId) return AcquisitionRouteResult.Blocked("Выбран другой персонаж")
        val request = when (intent) {
            is AcquisitionIntent.Target -> {
                if (catalog.definitions[intent.target.definition]?.kind != intent.kind)
                    return AcquisitionRouteResult.Blocked("Запись недоступна или относится к другому виду покупки")
                AcquisitionRequest(scope.characterId, listOf(intent.target))
            }
            is AcquisitionIntent.BulkTargets -> intent.request
        }
        if (request.characterId != scope.characterId) return AcquisitionRouteResult.Blocked("Запрос относится к другому персонажу")
        for (target in request.targets) {
            val definition = catalog.definitions[target.definition] ?: return AcquisitionRouteResult.Blocked("Запись недоступного пакета")
            val mana = manaReserveDefinition(target.definition, catalog)
            val owned = if (mana != null) manaReserveInstances(state.instances) else state.instances.filter { it.definition == target.definition }
            val instance = target.instanceId?.let { id -> state.instances.singleOrNull { it.instanceId == id } }
            if (instance != null && instance.definition != target.definition && !(mana != null && isManaReserveRef(instance.definition)))
                return AcquisitionRouteResult.Blocked("Экземпляр относится к другой записи")
            if (target.targetRank < 0) return AcquisitionRouteResult.ManualReviewRequired()
            // No current rank exists until a repeatable skill's instance has been explicitly selected.
            if (mana == null && target.instanceId == null && (owned.size > 1 || definition.repeatPolicy != RepeatPolicy.Single))
                return AcquisitionRouteResult.NeedsExplicitSelection("Выберите существующий или новый экземпляр", EnvelopeList(owned.map { it.instanceId }))
            val current = when (definition.kind) {
                AcquisitionKind.ATTRIBUTE -> AttributeId.entries.singleOrNull { "attribute:${it.name}" == definition.ref.entryId }?.let { state.character.attributes[it]?.base } ?: 0
                else -> if (mana != null) manaReserveRank(state.character, state.instances) else instance?.rank ?: if(target.instanceId == null) owned.singleOrNull()?.rank ?: 0 else 0
            }
            if (target.targetRank < current) return AcquisitionRouteResult.ManualReviewRequired()
        }
        return AcquisitionRouteResult.QuoteRequest(request)
    }
}
