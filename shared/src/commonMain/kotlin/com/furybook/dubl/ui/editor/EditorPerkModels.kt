package com.furybook.dubl.ui.editor

import com.furybook.content.FcpUiComponent
import com.furybook.content.FcpUiSurface
import com.furybook.dubl.content.DublUiFeature
import com.furybook.dubl.content.forFeature
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.CharacterEconomyBreakdown
import com.furybook.dubl.model.DevelopmentAcquisitionPlan
import com.furybook.dubl.model.DevelopmentAcquisitionPlanner
import com.furybook.dubl.model.DevelopmentAcquisitionRequest
import com.furybook.dubl.model.DevelopmentAcquisitionStep
import com.furybook.dubl.model.DevelopmentAvailability
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentEntryKind
import com.furybook.dubl.model.DevelopmentProgress
import com.furybook.dubl.model.DevelopmentRules
import com.furybook.dubl.model.DevelopmentScreenIndex
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.RequirementStatus
import com.furybook.dubl.model.developmentNormalize
import com.furybook.dubl.ui.sheet.sheetChiEconomyMounted

/** The rules' "creation is over" requirement check carries this phrase (DevelopmentRules, «только при создании»). */
internal const val CREATION_CLOSED_MARK = "создание уже завершено"

enum class EditorPerkStatus(val label: String) {
    AVAILABLE("Доступно"),
    ALMOST("Почти"),
    LOW_XP("Мало XP"),
    REQUIREMENTS("Требования"),
    CREATION_ONLY("Только при создании"),
    OWNED("Взят"),
    UPGRADABLE("Взят"),
}

data class EditorPerkCard(
    val entryId: String,
    val name: String,
    val category: String,
    val costText: String,
    val status: EditorPerkStatus,
    val pill: String,
    val warn: Boolean,
    val depth: Int,
    val rank: Int,
    val maxRank: Int,
    /** XP of the next step (chain included for ALMOST); orders Развитие lists. */
    val nextCost: Int = 0,
    val benefit: String = "",
)

data class EditorPerkGroup(val title: String, val cards: List<EditorPerkCard>)

data class EditorRequirementLine(val text: String, val status: RequirementStatus)

/** One step of a chain; [isTarget] marks the perk itself, the rest are prerequisites bought first. */
data class EditorChainStep(val label: String, val costText: String, val isTarget: Boolean = false)

data class EditorChoice(val id: String, val label: String, val options: List<String>, val selected: Int)

sealed interface EditorPerkAction {
    /** Through the acquisition planner: costs, prerequisites and budget checked by the rules. */
    data class Acquire(val request: DevelopmentAcquisitionRequest) : EditorPerkAction
    /** A plain rank change, as the old Навыки screen does for manual checks, forced takes and removal. */
    data class SetRank(val rank: Int, val optionIndex: Int) : EditorPerkAction
}

data class EditorPerkDetail(
    val entryId: String,
    val name: String,
    val category: String,
    val kindLabel: String,
    val costText: String,
    val status: EditorPerkStatus,
    val pill: String,
    val rank: Int,
    val maxRank: Int,
    val benefit: String,
    val benefitMissing: Boolean,
    val requirements: List<EditorRequirementLine>,
    val unlocks: List<String>,
    val chain: List<EditorChainStep>,
    val chainCost: String,
    val choices: List<EditorChoice>,
    val action: EditorPerkAction?,
    val actionLabel: String?,
    val blockReason: String?,
    val forceAction: EditorPerkAction.SetRank?,
    val forceLabel: String?,
    /** Failing / manual checks to list in the forced-take confirmation. */
    val forceChecks: List<String>,
    val removeAction: EditorPerkAction.SetRank?,
    val removeLabel: String?,
)

/** One character state's rules, planner and index, shared by every perk model so the planner runs once per entry. */
class EditorPerkContext(host: DublEditorHost) {
    val character = host.active
    val catalog = host.developmentCatalog
    val economy: CharacterEconomyBreakdown = CharacterEconomy.breakdown(character, catalog, includeChi = sheetChiEconomyMounted(host))
    val showsChi: Boolean = editorShowsChi(host)
    private val progress = DevelopmentProgress(character.development)
    val rules = DevelopmentRules(character, catalog, progress)
    val planner = DevelopmentAcquisitionPlanner(character, catalog)
    val index = DevelopmentScreenIndex(catalog)
    private val statusMemo = mutableMapOf<String, Evaluation>()

    internal class Evaluation(
        val status: EditorPerkStatus,
        val rank: Int,
        val option: Int,
        val availability: DevelopmentAvailability,
        val plan: DevelopmentAcquisitionPlan?,
        val directXp: Int,
        val directOs: Int,
    ) {
        val nextCost: Int get() = plan?.xpCost ?: directXp
    }

    fun rank(entry: DevelopmentEntry): Int = progress.rank(entry.id)

    /**
     * Free XP covers [xp], and free ОС covers [os] when the step costs any. The rules call the ОС budget a
     * recommendation, so a build already over it can still take XP-only steps.
     */
    fun affordable(xp: Int, os: Int): Boolean = xp <= economy.remainingXp && (os <= 0 || os <= economy.abilityPointsRemaining)

    fun defaultOption(entry: DevelopmentEntry): Int = when {
        progress.rank(entry.id) > 0 -> progress.optionIndex(entry.id)
        !entry.isAbility || entry.abilityOptions.isEmpty() -> 0
        else -> entry.abilityOptions.indices.minByOrNull { entry.abilityOptions[it].value } ?: 0
    }

    internal fun evaluate(entry: DevelopmentEntry, choices: Map<String, Int> = emptyMap(), optionIndex: Int? = null): Evaluation {
        val memoable = choices.isEmpty() && optionIndex == null
        if (memoable) statusMemo[entry.id]?.let { return it }
        val rank = rank(entry)
        val max = entry.maxRank.coerceAtLeast(1)
        val option = optionIndex ?: defaultOption(entry)
        val availability = rules.availability(entry, option)
        val directXp = if (entry.isAbility) 0 else entry.cost.coerceAtLeast(0)
        val directOs = rules.abilityCost(entry, option)
        val failed = availability.checks.any { it.status == RequirementStatus.FAIL }
        val creationClosed = availability.checks.any { it.status == RequirementStatus.FAIL && CREATION_CLOSED_MARK in it.text }
        var plan: DevelopmentAcquisitionPlan? = null
        val status = when {
            rank >= max -> EditorPerkStatus.OWNED
            creationClosed -> EditorPerkStatus.CREATION_ONLY
            entry.incomplete -> EditorPerkStatus.REQUIREMENTS
            !failed -> if (affordable(directXp, directOs)) {
                if (rank > 0) EditorPerkStatus.UPGRADABLE else EditorPerkStatus.AVAILABLE
            } else EditorPerkStatus.LOW_XP
            else -> {
                plan = planner.plan(
                    DevelopmentAcquisitionRequest.single(entry.id, targetRank = rank + 1, optionIndex = option, choiceSelections = choices, enforceBudget = false),
                )
                val prerequisites = plan.steps.count { !(it is DevelopmentAcquisitionStep.Development && it.entryId == entry.id) }
                when {
                    plan.unresolvedRequirements.isNotEmpty() -> EditorPerkStatus.REQUIREMENTS
                    prerequisites == 1 -> EditorPerkStatus.ALMOST
                    else -> EditorPerkStatus.REQUIREMENTS
                }
            }
        }
        return Evaluation(status, rank, option, availability, plan, directXp, directOs).also { if (memoable) statusMemo[entry.id] = it }
    }

    internal fun card(entry: DevelopmentEntry, depth: Int): EditorPerkCard {
        val eval = evaluate(entry)
        val warn = eval.rank > 0 && eval.availability.checks.any { it.status == RequirementStatus.FAIL && CREATION_CLOSED_MARK !in it.text }
        return EditorPerkCard(
            entryId = entry.id,
            name = entry.name,
            category = entry.category,
            costText = editorPerkCostText(entry, eval.directOs),
            status = eval.status,
            pill = editorPerkPill(eval.status, eval.rank, entry.maxRank.coerceAtLeast(1)),
            warn = warn,
            depth = depth,
            rank = eval.rank,
            maxRank = entry.maxRank.coerceAtLeast(1),
            nextCost = eval.nextCost,
            benefit = entry.benefit,
        )
    }
}

fun editorShowsChi(host: DublEditorHost): Boolean =
    host.uiMounts(FcpUiSurface.DEVELOPMENT_TABS, FcpUiComponent.DEVELOPMENT_BROWSER).forFeature(DublUiFeature.CHI) != null

fun editorPerkStatus(host: DublEditorHost, entry: DevelopmentEntry): EditorPerkStatus = EditorPerkContext(host).evaluate(entry).status

internal fun editorPerkCostText(entry: DevelopmentEntry, abilityCost: Int): String =
    if (entry.isAbility) "$abilityCost ОС" else "${entry.cost} XP" + if (entry.maxRank > 1) "/ранг" else ""

internal fun editorPerkPill(status: EditorPerkStatus, rank: Int, maxRank: Int): String = when {
    rank > 0 -> if (maxRank > 1) "Взят · $rank/$maxRank" else "Взят"
    else -> status.label
}

private val OWNED_KIND_TITLES = mapOf(
    DevelopmentEntryKind.REGULAR to "Обычные навыки",
    DevelopmentEntryKind.SPECIAL to "Особые способности",
    DevelopmentEntryKind.MARTIAL to "Боевые искусства",
    DevelopmentEntryKind.CHI to "ЦИ",
)

private fun EditorPerkKind.indexKind(): DevelopmentEntryKind? = when (this) {
    EditorPerkKind.REGULAR -> DevelopmentEntryKind.REGULAR
    EditorPerkKind.SPECIAL -> DevelopmentEntryKind.SPECIAL
    EditorPerkKind.MARTIAL -> DevelopmentEntryKind.MARTIAL
    EditorPerkKind.CHI -> DevelopmentEntryKind.CHI
    EditorPerkKind.OWNED -> null
}

private fun EditorPerkStatus.matches(filter: EditorPerkFilter): Boolean = when (filter) {
    EditorPerkFilter.ALL -> true
    EditorPerkFilter.AVAILABLE -> this == EditorPerkStatus.AVAILABLE || this == EditorPerkStatus.UPGRADABLE
    EditorPerkFilter.ALMOST -> this == EditorPerkStatus.ALMOST
}

/** Cards of one kind tab, before the filter: base mana and (pack off) chi never appear. */
private fun EditorPerkContext.cardsFor(kind: EditorPerkKind, query: String): List<Pair<String, EditorPerkCard>> {
    val needle = developmentNormalize(query)
    val indexKind = kind.indexKind()
    return index.all.asSequence()
        .filter { it.entry.id != MagicEquipmentRules.BASE_MANA_ENTRY_ID }
        .filter { showsChi || it.kind != DevelopmentEntryKind.CHI }
        .filter { if (indexKind == null) rank(it.entry) > 0 else it.kind == indexKind }
        .filter { needle.isBlank() || needle in it.searchText }
        .sortedWith(compareBy({ developmentNormalize(it.groupName) }, { if (it.entry.isAbility) 0 else 1 }, { developmentNormalize(it.entry.name) }))
        .map { indexed ->
            val group = if (indexKind == null) OWNED_KIND_TITLES.getValue(indexed.kind) else indexed.groupName
            val depth = if (indexed.kind == DevelopmentEntryKind.SPECIAL && !indexed.entry.isAbility && indexed.entry.accessId != null) 1 else 0
            group to card(indexed.entry, depth)
        }
        .toList()
}

fun editorPerkGroups(host: DublEditorHost, kind: EditorPerkKind, filter: EditorPerkFilter, query: String, keep: String? = null): List<EditorPerkGroup> =
    EditorPerkContext(host).perkGroups(kind, filter, query, keep)

/** [keep]: the open perk stays listed even once it no longer matches the filter (just taken, removed). */
fun EditorPerkContext.perkGroups(kind: EditorPerkKind, filter: EditorPerkFilter, query: String, keep: String? = null): List<EditorPerkGroup> =
    cardsFor(kind, query).filter { it.second.entryId == keep || it.second.status.matches(filter) }
        .groupBy({ it.first }, { it.second })
        .map { (title, cards) -> EditorPerkGroup(title, cards) }

fun editorPerkCounts(host: DublEditorHost, kind: EditorPerkKind): Map<EditorPerkFilter, Int> = EditorPerkContext(host).perkCounts(kind)

fun EditorPerkContext.perkCounts(kind: EditorPerkKind): Map<EditorPerkFilter, Int> {
    val cards = cardsFor(kind, "").map { it.second }
    return EditorPerkFilter.entries.associateWith { filter -> cards.count { it.status.matches(filter) } }
}

/** All cards of every kind (no owned tab), for the Развитие lists. */
internal fun EditorPerkContext.allCards(): List<EditorPerkCard> =
    listOf(EditorPerkKind.REGULAR, EditorPerkKind.SPECIAL, EditorPerkKind.MARTIAL, EditorPerkKind.CHI).flatMap { kind -> cardsFor(kind, "").map { it.second } }

/**
 * The planner's own budget check also refuses XP-only steps once ОС is overspent; the editor checks the
 * budget itself ([EditorPerkContext.affordable]) before offering the action, so the request skips it.
 */
private fun editorRequest(entryId: String, rank: Int, option: Int, choices: Map<String, Int>) =
    DevelopmentAcquisitionRequest.single(entryId, targetRank = rank, optionIndex = option, choiceSelections = choices, enforceBudget = false)

private fun stepLabel(step: DevelopmentAcquisitionStep): String = when (step) {
    is DevelopmentAcquisitionStep.Attribute -> "${step.attribute.title} ${step.fromBase} → ${step.toBase}"
    is DevelopmentAcquisitionStep.Skill -> "${step.name} ${step.fromRank} → ${step.toRank}"
    is DevelopmentAcquisitionStep.Development -> if (step.toRank > 1) "${step.name} — ранг ${step.toRank}" else step.name
}

private fun overspentReason(remaining: Int) = "Перерасход опыта: свободно $remaining XP. Верните часть опыта или добавьте XP на «Основе»."

private fun costLabel(xp: Int, os: Int): String = listOfNotNull(xp.takeIf { it > 0 }?.let { "$it XP" }, os.takeIf { it > 0 }?.let { "$it ОС" })
    .joinToString(" + ").ifEmpty { "0 XP" }

fun editorPerkDetail(host: DublEditorHost, entryId: String, choiceSelections: Map<String, Int>, optionIndex: Int?): EditorPerkDetail? =
    EditorPerkContext(host).perkDetail(entryId, choiceSelections, optionIndex)

fun EditorPerkContext.perkDetail(entryId: String, choiceSelections: Map<String, Int>, optionIndex: Int?): EditorPerkDetail? {
    val entry = catalog.byId(entryId) ?: return null
    val eval = evaluate(entry, choiceSelections, optionIndex)
    val rank = eval.rank
    val max = entry.maxRank.coerceAtLeast(1)
    val target = rank + 1
    val plan = eval.plan
    val prerequisites = plan?.steps?.filterNot { it is DevelopmentAcquisitionStep.Development && it.entryId == entry.id }.orEmpty()
    val chain = if (plan != null && plan.unresolvedRequirements.isEmpty() && prerequisites.isNotEmpty()) plan.steps else emptyList()
    val verb = if (rank > 0) "Повысить до ранга $target" else "Взять"
    val manual = eval.availability.checks.any { it.status == RequirementStatus.MANUAL }

    var action: EditorPerkAction? = null
    var actionLabel: String? = null
    var block: String? = null
    when (eval.status) {
        EditorPerkStatus.OWNED -> Unit
        EditorPerkStatus.CREATION_ONLY -> block = "Только при создании персонажа — создание уже завершено."
        EditorPerkStatus.AVAILABLE, EditorPerkStatus.UPGRADABLE -> {
            action = if (manual) EditorPerkAction.SetRank(target, eval.option)
            else EditorPerkAction.Acquire(editorRequest(entry.id, target, eval.option, emptyMap()))
            actionLabel = "$verb · ${costLabel(eval.directXp, eval.directOs)}"
        }
        EditorPerkStatus.LOW_XP -> block = when {
            economy.remainingXp < 0 -> overspentReason(economy.remainingXp)
            eval.directXp > economy.remainingXp -> "Не хватает опыта: нужно ${eval.directXp} XP, свободно ${economy.remainingXp}."
            else -> "Не хватает очков способностей."
        }
        EditorPerkStatus.ALMOST, EditorPerkStatus.REQUIREMENTS -> when {
            entry.incomplete -> block = "Запись книги не завершена."
            plan == null || plan.unresolvedRequirements.isNotEmpty() -> block = "Не все требования можно закрыть автоматически."
            economy.remainingXp < 0 && plan.xpCost > 0 -> block = overspentReason(economy.remainingXp)
            plan.xpCost > economy.remainingXp -> block = "Не хватает опыта: нужно ${plan.xpCost} XP, свободно ${economy.remainingXp}."
            !affordable(plan.xpCost, plan.abilityCost) -> block = "Не хватает очков способностей."
            else -> {
                action = EditorPerkAction.Acquire(editorRequest(entry.id, target, eval.option, choiceSelections))
                actionLabel = "$verb с цепочкой · ${costLabel(plan.xpCost, plan.abilityCost)}"
            }
        }
    }
    val canForce = action == null && rank < max
    val choices = buildList {
        if (entry.isAbility && entry.abilityOptions.size > 1) {
            add(EditorChoice("option", "Источник ОС", entry.abilityOptions.map { "${it.source} · ${it.value} ОС" }, eval.option))
        }
        plan?.choices?.forEach { choice ->
            add(EditorChoice(choice.id, choice.label, choice.options.map { "${it.label} · ${costLabel(it.xpCost, it.abilityCost)}" }, choiceSelections[choice.id] ?: choice.selectedIndex))
        }
    }
    val benefitMissing = entry.benefit.isBlank()
    return EditorPerkDetail(
        entryId = entry.id,
        name = entry.name,
        category = entry.category,
        kindLabel = when {
            entry.isMartialArt -> "Боевое искусство"
            entry.isChiDevelopment -> "ЦИ"
            entry.isSpecialDevelopment -> "Особая способность"
            else -> "Обычный навык"
        },
        costText = editorPerkCostText(entry, eval.directOs),
        status = eval.status,
        pill = editorPerkPill(eval.status, rank, max),
        rank = rank,
        maxRank = max,
        benefit = when {
            !benefitMissing -> entry.benefit
            entry.isAbility -> "Открывает ветку способностей."
            else -> "Нет описания в правилах — см. требования."
        },
        benefitMissing = benefitMissing,
        requirements = eval.availability.checks.map { EditorRequirementLine(it.text, it.status) },
        unlocks = planner.unlocks(entry.id).map { it.name },
        chain = chain.map {
            EditorChainStep(stepLabel(it), costLabel(it.xpCost, it.abilityCost), it is DevelopmentAcquisitionStep.Development && it.entryId == entry.id)
        },
        chainCost = if (chain.isEmpty()) "" else costLabel(plan!!.xpCost, plan.abilityCost),
        choices = choices,
        action = action,
        actionLabel = actionLabel,
        blockReason = block,
        forceAction = if (canForce) EditorPerkAction.SetRank(target, eval.option) else null,
        forceLabel = if (canForce) (if (entry.isAbility) "Открыть всё равно" else "Взять всё равно") else null,
        forceChecks = if (canForce) eval.availability.checks.filter { it.status != RequirementStatus.OK }.map { it.text } + listOfNotNull(block) else emptyList(),
        removeAction = if (rank > 0) EditorPerkAction.SetRank(rank - 1, eval.option) else null,
        removeLabel = if (rank > 0) (if (rank > 1) "Убрать ранг" else "Убрать") + (if (eval.directXp > 0) " (+${eval.directXp} XP)" else "") else null,
    )
}
