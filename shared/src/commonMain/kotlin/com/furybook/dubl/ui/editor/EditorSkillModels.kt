package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.SkillCalculation
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.skillCalculationForRoll
import com.furybook.dubl.model.skillCalculationOptions

/** One allowed attribute's bonus: how it adds up, or why the skill can't be rolled with it. */
data class EditorBonusLine(val attribute: AttributeId, val title: String, val breakdown: String, val total: Int?, val unavailableReason: String)

/** A rules-automation effect that touches this skill; [enabled] false means switched off for this character. */
data class EditorSkillEffect(val id: String, val source: String, val text: String, val enabled: Boolean)

/** Everything the skill panel (editor) and «Об умении» (sheet) show; all numbers come from the skill model. */
data class EditorSkillDetail(
    val skillId: String,
    val name: String,
    /** «Своё» / «Специализация» for skills the player added; null for rulebook skills. */
    val origin: String?,
    val description: String,
    val headline: String,
    val rank: Int,
    val bonus: List<EditorBonusLine>,
    val attributes: List<AttributeId>,
    val modifier: Int,
    val formulaNote: String,
    val localName: String,
    val localDescription: String,
    val category: SkillCategory,
    val untrained: UntrainedRule,
    val auto6: String,
    val auto12: String,
    val effects: List<EditorSkillEffect>,
    /** «К рулбуку» only for rulebook skills: a custom skill's "overrides" are its real definition. */
    val canResetToBook: Boolean,
    /** «Удалить» only for skills the player added. */
    val canDelete: Boolean,
    val hidden: Boolean,
)

fun editorSkillDetail(host: DublEditorHost, skillId: String): EditorSkillDetail? {
    val character = host.active
    val skill = character.resolveSkill(skillId) ?: return null
    val stock = skill.stockAttribute
    val rules = SkillEffectRules(character, host.developmentCatalog, host.skillEffectCatalog)
    val effects = rules.configuredForSkill(skill)
    // Automatic effects (Атлетичность +2…) are part of the bonus the list and the sheet show; say so here too.
    val automatic = rules.forSkill(skill).automaticContributions.map { it.label to it.value }
    val stockTotal = character.skillCalculationForRoll(skill, stock).total?.plus(automatic.sumOf { it.second })
    return EditorSkillDetail(
        skillId = skill.id,
        name = skill.name,
        origin = when {
            !skill.isDynamic -> null
            skill.state.definitionId != null && skill.definition?.template == true -> "Специализация"
            else -> "Своё"
        },
        description = skill.description,
        headline = "${stock.shortTitle} · ранг ${skill.rank} · ${stockTotal?.let(::editorSigned) ?: "—"}",
        rank = skill.rank,
        bonus = character.skillCalculationOptions(skill).map { (attribute, calculation) -> bonusLine(attribute, calculation, automatic) },
        attributes = skill.attributes,
        modifier = skill.modifier,
        formulaNote = skill.formulaNote,
        localName = skill.name,
        localDescription = skill.description,
        category = skill.category,
        untrained = skill.untrained,
        auto6 = skill.auto6,
        auto12 = skill.auto12,
        effects = effects.map { EditorSkillEffect(it.id, it.sourceName, it.effectText, it.id !in character.disabledSkillEffectIds) },
        canResetToBook = skill.isBuiltIn,
        canDelete = skill.isDynamic,
        hidden = skill.id in character.hiddenSkillIds,
    )
}

private fun bonusLine(attribute: AttributeId, calculation: SkillCalculation, automatic: List<Pair<String, Int>>) = EditorBonusLine(
    attribute = attribute,
    title = attribute.title,
    breakdown = (calculation.contributions.map { it.label to it.value } + if (calculation.total != null) automatic else emptyList())
        .joinToString(" + ") { (label, value) -> "$label ${editorSigned(value)}" },
    total = calculation.total?.plus(automatic.sumOf { it.second }),
    unavailableReason = calculation.unavailableReason,
)

/**
 * Allowed-attribute chips: adding appends, removing keeps the order, the last one can't be removed. The
 * first attribute stays primary unless it is the one removed (a custom skill then moves group).
 */
fun editorToggledAttributes(current: List<AttributeId>, attribute: AttributeId): List<AttributeId> = when {
    attribute !in current -> current + attribute
    current.size == 1 -> current
    else -> current - attribute
}

/** Permanent skill modifier range: the same ±99 the old Умения tab and the session accept. */
val EDITOR_SKILL_MODIFIER_RANGE = -99..99

internal fun editorSigned(value: Int): String = if (value >= 0) "+$value" else value.toString()
