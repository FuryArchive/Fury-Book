package com.furybook.dubl.ui.editor

import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.resolvedSkills

/** One line of «Изменения за этот заход»; [xpSpent] > 0 means free XP went down. */
data class EditorChangeLine(val text: String, val xpSpent: Int)

/** [netSpent]: how much free XP went down; [spent]: how much more XP is spent on the build (GM XP is neither). */
data class EditorChanges(val lines: List<EditorChangeLine>, val netSpent: Int, val spent: Int = 0)

/**
 * What changed since the editor opened, one line per field. Each line's XP is what the economy says
 * that one change costs on top of [before]: no cost table lives here.
 */
fun editorChanges(
    before: CharacterRestorePoint,
    after: CharacterRestorePoint,
    catalog: DevelopmentCatalog,
    includeChi: Boolean,
    effectNames: Map<String, String> = emptyMap(),
): EditorChanges {
    val b = before.character
    val a = after.character
    val baseSpent = CharacterEconomy.breakdown(b, catalog, includeChi).spentXp
    fun cost(modified: DublCharacter): Int = CharacterEconomy.breakdown(modified, catalog, includeChi).spentXp - baseSpent
    val lines = mutableListOf<EditorChangeLine>()
    fun add(text: String, xp: Int = 0) { lines += EditorChangeLine(text, xp) }

    if (a.name != b.name) add("Имя: ${a.name}")
    if (a.concept != b.concept) add("Концепция изменена")
    if (before.extras.portraitUri != after.extras.portraitUri) add("Портрет изменён")
    if (a.size != b.size) add("Размер ${b.size} → ${a.size}")
    if (a.legs != b.legs) add("Ноги ${b.legs} → ${a.legs}")
    if (a.manaEnabled != b.manaEnabled) add(if (a.manaEnabled) "Мана включена" else "Мана выключена")
    if (a.experience != b.experience) add("Опыт ${b.experience} → ${a.experience}", b.experience - a.experience)
    if (a.creationExperience != b.creationExperience) add("Опыт создания ${b.creationExperience} → ${a.creationExperience}")
    if (a.xpAdjustment != b.xpAdjustment) add("Поправка опыта: ${a.xpAdjustment}")
    if (a.abilityPointsOverride != b.abilityPointsOverride) add("Очки способностей: ${a.abilityPointsOverride ?: "авто"}")

    AttributeId.entries.forEach { id ->
        val from = b.attributes[id]?.base ?: 0
        val to = a.attributes[id]?.base ?: 0
        if (from != to) add("${id.title} $from → $to", cost(b.copy(attributes = b.attributes + (id to (a.attributes[id] ?: return@forEach)))))
    }

    val resolved = (b.resolvedSkills(includeHidden = true) + a.resolvedSkills(includeHidden = true)).associateBy { it.id }
    val names = resolved.mapValues { it.value.name }
    (b.skills.keys + a.skills.keys).forEach { id ->
        val from = b.skills[id]?.rank ?: 0
        val to = a.skills[id]?.rank ?: 0
        val dynamic = resolved[id]?.isDynamic ?: true
        // A rulebook skill gets a saved entry the first time a setting changes: that is a setting, not a new skill.
        val unsaved = CharacterSkill(id = id, definitionId = id)
        val settingsBefore = (b.skills[id] ?: unsaved).copy(rank = 0)
        val settingsAfter = (a.skills[id] ?: unsaved).copy(rank = 0)
        when {
            dynamic && id !in b.skills -> add("+ ${names[id] ?: id}")
            dynamic && id !in a.skills -> add("− ${names[id] ?: id}")
            settingsBefore != settingsAfter -> add("${names[id] ?: id}: настройки изменены")
        }
        if (from == to) return@forEach
        val modified = a.skills[id]?.let { b.copy(skills = b.skills + (id to it)) } ?: b.copy(skills = b.skills - id)
        add("${names[id] ?: id} $from → $to", cost(modified))
    }

    (b.development.keys + a.development.keys).forEach { id ->
        val from = b.development[id]?.rank ?: 0
        val to = a.development[id]?.rank ?: 0
        val name = catalog.byId(id)?.name ?: id
        if (from == to) {
            if (b.development[id]?.optionIndex != a.development[id]?.optionIndex) add("$name: другой вариант")
            return@forEach
        }
        val text = when {
            from == 0 -> "+ $name"
            to == 0 -> "− $name"
            else -> "$name: ранг $to"
        }
        val modified = a.development[id]?.let { b.copy(development = b.development + (id to it)) } ?: b.copy(development = b.development - id)
        add(text, cost(modified))
    }

    (a.hiddenSkillIds - b.hiddenSkillIds).forEach { add("Скрыто: ${names[it] ?: it}") }
    (b.hiddenSkillIds - a.hiddenSkillIds).forEach { add("Возвращено: ${names[it] ?: it}") }
    (a.disabledSkillEffectIds - b.disabledSkillEffectIds).forEach { add("Автоматизация: ${effectNames[it] ?: it} выкл.") }
    (b.disabledSkillEffectIds - a.disabledSkillEffectIds).forEach { add("Автоматизация: ${effectNames[it] ?: it} вкл.") }

    if (a.creationComplete != b.creationComplete) add(if (a.creationComplete) "Создание завершено" else "Создание открыто снова")
    // Whatever else differs is still reverted by «Отменить всё», so the list (and the button) must not look empty.
    if (lines.isEmpty() && before != after) add("Другие изменения")

    val beforeEconomy = CharacterEconomy.breakdown(b, catalog, includeChi)
    val afterEconomy = CharacterEconomy.breakdown(a, catalog, includeChi)
    return EditorChanges(lines, beforeEconomy.remainingXp - afterEconomy.remainingXp, afterEconomy.spentXp - beforeEconomy.spentXp)
}

fun editorNetText(spent: Int, freeDelta: Int): String {
    val spentText = if (spent > 0) "Потрачено $spent XP" else "Возвращено ${-spent} XP"
    val freeText = "свободно " + (if (freeDelta > 0) "+" else "") + freeDelta
    return when {
        spent == 0 && freeDelta == 0 -> "Опыт не изменился"
        spent == 0 -> "Свободно " + (if (freeDelta > 0) "+" else "") + "$freeDelta XP"
        freeDelta == -spent -> spentText
        else -> "$spentText · $freeText"
    }
}

fun editorNetText(changes: EditorChanges): String = editorNetText(changes.spent, -changes.netSpent)

fun editorXpText(spent: Int): String = when {
    spent > 0 -> "−$spent XP"
    spent < 0 -> "+${-spent} XP"
    else -> ""
}
