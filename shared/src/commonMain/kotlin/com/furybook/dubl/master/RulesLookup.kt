package com.furybook.dubl.master

import com.furybook.dubl.model.ConditionCatalog
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.MagicEquipmentCatalog
import com.furybook.dubl.model.SkillDefinition
import com.furybook.dubl.ui.sheet.gearDescriptionExtra

enum class LookupType(val title: String) { PERK("Навыки"), SPELL("Заклинания"), GEAR("Снаряжение"), CONDITION("Состояния"), SKILL("Умения") }

/** One Справочник result: [facts] are the short «Поле: значение» lines, [text] the rule text. */
data class LookupEntry(
    val id: String,
    val type: LookupType,
    val name: String,
    val subtitle: String,
    val text: String,
    val facts: List<Pair<String, String>> = emptyList(),
)

private const val MAX_RESULTS = 200

/** Every rulebook entry the device has loaded, as Справочник cards. Built once per catalog set. */
fun lookupEntries(
    development: DevelopmentCatalog,
    magic: MagicEquipmentCatalog,
    conditions: ConditionCatalog,
    skills: List<SkillDefinition>,
): List<LookupEntry> = buildList {
    development.entries.forEach { perk ->
        add(
            LookupEntry(
                id = perk.id, type = LookupType.PERK, name = perk.name,
                subtitle = listOf(perk.category, if (perk.cost > 0) "${perk.cost} XP" else "", if (perk.maxRank > 1) "до ${perk.maxRank} рангов" else "")
                    .filter(String::isNotBlank).joinToString(" · "),
                text = listOf(perk.benefit, perk.notes).filter(String::isNotBlank).joinToString("\n\n"),
                facts = listOf("Требования" to perk.requirements).filter { it.second.isNotBlank() },
            ),
        )
    }
    magic.spells.forEach { spell ->
        add(
            LookupEntry(
                id = spell.id, type = LookupType.SPELL, name = spell.name,
                subtitle = listOf(spell.school, "${spell.manaText.trim().ifBlank { spell.cost.toString() }} маны").filter(String::isNotBlank).joinToString(" · "),
                text = listOf(spell.description, spell.enhancement.takeIf(String::isNotBlank)?.let { "Усиление: $it" }.orEmpty())
                    .filter(String::isNotBlank).joinToString("\n\n"),
                facts = listOf("Время" to spell.time, "Дистанция" to spell.range, "Область" to spell.area, "Проверка" to spell.action, "Длительность" to spell.duration)
                    .filter { it.second.isNotBlank() },
            ),
        )
    }
    magic.gear.forEach { item ->
        add(
            LookupEntry(
                id = item.id, type = LookupType.GEAR, name = item.name, subtitle = item.category,
                text = gearDescriptionExtra(item.description, item.fields),
                facts = item.fields.entries.filter { it.value.isNotBlank() }.map { it.key to it.value },
            ),
        )
    }
    conditions.conditions.forEach { add(LookupEntry(it.id, LookupType.CONDITION, it.name, "Состояние", it.description)) }
    skills.forEach { skill ->
        add(
            LookupEntry(
                id = skill.id, type = LookupType.SKILL, name = skill.name, subtitle = skill.category.title,
                text = skill.description,
                facts = listOf("Без обучения" to skill.untrained.label, "Автоуспех 6" to skill.auto6, "Автоуспех 12" to skill.auto12).filter { it.second.isNotBlank() },
            ),
        )
    }
}

/** Name matches first, then text matches; [type] narrows to one kind; at most 200 results. */
fun searchLookup(entries: List<LookupEntry>, query: String, type: LookupType?): List<LookupEntry> {
    val pool = if (type == null) entries else entries.filter { it.type == type }
    val key = lookupKey(query.trim())
    if (key.isEmpty()) return pool.take(MAX_RESULTS)
    val byName = pool.filter { key in lookupKey(it.name) }
    val byText = pool.filter { it !in byName && (key in lookupKey(it.text) || it.facts.any { f -> key in lookupKey(f.second) }) }
    return (byName + byText).take(MAX_RESULTS)
}
