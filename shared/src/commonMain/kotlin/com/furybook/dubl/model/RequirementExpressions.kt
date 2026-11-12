package com.furybook.dubl.model

/** Shared, deterministic skill selectors for prerequisite checks and acquisition plans. */
internal enum class RequirementSkillFamily(val title: String, val templateId: String, val pattern: String) {
    KNOWLEDGE("Знание", "knowledge_template", "знани[еяй]"),
    CRAFT("Ремесло", "craft_template", "ремесл[оа]?"),
    PERFORMANCE("Исполнение", "performance_template", "исполнени[еяй]"),
    PROFESSION("Профессия", "profession_template", "професси[яий]"),
}

/** Explicit book/catalog spelling variants; never fuzzy-match unrelated techniques. */
internal fun developmentCatalogAlias(normalizedName: String): String? = when (normalizedName) {
    "джит кун до" -> "Джит Кун-До"
    "дэрвиш" -> "Танец ветра (Дэрвиш)"
    "открытая ладонь" -> "Школа Открытой Ладони"
    else -> null
}

private val requirementRank = Regex("^(.+?)\\s*:?\\s*(\\d+)(?:\\s*ранг(?:а|ов)?)?$", RegexOption.IGNORE_CASE)
private val anySpecialization = setOf("любое", "любая", "любой", "любые", "любых")

internal fun developmentRequirementText(source: String): String = source
    .replace('\u00a0', ' ')
    .replace(Regex("\\s+"), " ")
    .trim(' ', '.', ';', ',')
    .replaceFirst(Regex("^одно\\s+из\\s+умений\\s*:\\s*", RegexOption.IGNORE_CASE), "")
    .replaceFirst(Regex("^(?:умение|навык)\\s+", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s*\\(навык\\)(?=\\s*(?:\\d+(?:\\s*ранг(?:а|ов)?)?)?$)", RegexOption.IGNORE_CASE), "")
    .trim()

/** Split only outside parentheses; a specialization may itself contain a conjunction. */
internal fun developmentRequirementAlternatives(source: String): List<String>? {
    val text = developmentRequirementText(source)
    val separator = Regex("\\s+(?:или|и\\s*/\\s*или)\\s+", RegexOption.IGNORE_CASE)
    val parts = mutableListOf<String>()
    var start = 0
    var depth = 0
    var cursor = 0
    separator.findAll(text).forEach { match ->
        while (cursor < match.range.first) {
            when (text[cursor++]) { '(' -> depth++; ')' -> depth-- }
        }
        if (depth == 0) {
            parts += text.substring(start, match.range.first).trim()
            start = match.range.last + 1
        }
    }
    if (parts.isEmpty()) return null
    parts += text.substring(start).trim()
    if (parts.any(String::isBlank)) return null
    val trailingRank = requirementRank.matchEntire(parts.last())?.groupValues?.get(2)
    return parts.map { raw ->
        val part = developmentRequirementText(raw)
        val attribute = AttributeId.entries.any {
            developmentAlias(it.title) == developmentAlias(part) || developmentAlias(it.shortTitle) == developmentAlias(part)
        }
        // "Медитация (навык) или Внимательность 4" does NOT require Meditation rank 4.
        // Inherit a shared trailing rank only for skills/attributes, never for a feat.
        if (trailingRank != null && requirementRank.matchEntire(part) == null &&
            (attribute || parseDevelopmentSkillRequirement(part) != null)
        ) "$part $trailingRank" else part
    }
}

private fun familyName(name: String): Pair<RequirementSkillFamily, String>? {
    val normalized = developmentNormalize(name)
    for (family in RequirementSkillFamily.entries) {
        val match = Regex("^${family.pattern}(?:\\s*\\(([^()]*)\\)|\\s*:\\s*(.+))?$")
            .matchEntire(normalized) ?: continue
        return family to match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
    }
    return null
}

private fun skillFamily(skill: ResolvedSkill): RequirementSkillFamily? {
    // Stable definition identity wins over cosmetic renaming and UI category overrides.
    val definition = skill.definition
    if (definition != null) {
        return RequirementSkillFamily.entries.firstOrNull { it.templateId == definition.id }
            ?: familyName(definition.name)?.first
    }
    return familyName(skill.name)?.first
}

private fun specializationName(skill: ResolvedSkill): String {
    // A built-in specialization retains its canonical identity after a display-name edit.
    // Template instances, in contrast, define their own specialization through their name.
    val name = skill.definition?.takeUnless { it.template }?.name ?: skill.name
    return familyName(name)?.second ?: developmentNormalize(name)
}

private fun skillRequirementIdentity(skill: ResolvedSkill): String {
    val family = skillFamily(skill)
    return if (family != null) "${family.name}:${specializationName(skill)}"
    else skill.definition?.id ?: developmentAlias(skill.name)
}

internal data class DevelopmentSkillRequirement(
    val source: String,
    val minimumRank: Int,
    val minimumCount: Int = 1,
    val family: RequirementSkillFamily? = null,
    val specializations: List<String> = emptyList(),
    val definitionIds: Set<String> = emptySet(),
    val anySkill: Boolean = false,
) {
    private fun matches(skill: ResolvedSkill): Boolean {
        if (family != null) {
            if (skillFamily(skill) != family) return false
            val specialization = specializationName(skill)
            if (specialization.isBlank() || specialization in anySpecialization) return false
            return specializations.isEmpty() || specialization.split('/').any { part ->
                developmentNormalize(part) in specializations.map(::developmentNormalize)
            }
        }
        if (anySkill) return true
        if (skill.definition?.id in definitionIds || skill.id in definitionIds) return true
        return definitionIds.any { id ->
            developmentAlias(SkillCatalog.definition(id)?.name.orEmpty()) == developmentAlias(skill.name)
        }
    }

    fun candidates(character: DublCharacter, includeMissingNamed: Boolean = false): List<ResolvedSkill> {
        val existing = character.resolvedSkills(includeHidden = true).filter(::matches)
        val proposed = mutableListOf<ResolvedSkill>()
        // A named requirement supplies the specialization. A generic count never invents one.
        if (includeMissingNamed && family != null && specializations.isNotEmpty()) {
            val template = SkillCatalog.definition(family.templateId)
            if (template != null) {
                for (specialization in specializations) {
                    val selector = copy(specializations = listOf(specialization))
                    if (existing.any(selector::matches)) continue
                    val name = "${family.title} ($specialization)"
                    val stem = "prerequisite:${family.templateId}:${developmentNormalize(specialization)}"
                    var id = stem
                    var suffix = 1
                    while (id in character.skills || proposed.any { it.id == id }) id = "$stem:${suffix++}"
                    proposed += ResolvedSkill(id, template, CharacterSkill(id = id, definitionId = template.id, name = name))
                }
            }
        }
        // Do not collapse different specializations to their shared template ID, nor count
        // duplicate copies of the same specialization twice. Keep the highest trained copy.
        return (existing + proposed).sortedByDescending { it.rank }
            .distinctBy(::skillRequirementIdentity).sortedBy { it.id }
    }

    fun check(character: DublCharacter): RequirementCheck {
        val skills = candidates(character)
        val count = skills.count { it.rank >= minimumRank }
        val detail = if (minimumCount > 1) {
            "$source: $count / $minimumCount"
        } else {
            "$source: ранг ${skills.maxOfOrNull { it.rank } ?: 0} / $minimumRank"
        }
        return RequirementCheck(if (count >= minimumCount) RequirementStatus.OK else RequirementStatus.FAIL, detail)
    }
}

internal fun parseDevelopmentSkillRequirement(source: String): DevelopmentSkillRequirement? {
    var text = developmentRequirementText(source)
    // The book uses both "Знание (Любое) 4" and "Знание 4 (Любое)".
    Regex("^(.+?)\\s+(\\d+)\\s*\\((любое|любая|любой)\\)$", RegexOption.IGNORE_CASE)
        .matchEntire(text)?.let { text = "${it.groupValues[1]} (${it.groupValues[3]}) ${it.groupValues[2]}" }

    var count = 1
    var need = 1
    var name = text
    val counted = Regex(
        "^(?:(?:любые|любых)\\s+)?(два|две|три|четыре|пять|\\d+)\\s+(?:и\\s+более\\s+)?(.+?)\\s+(\\d+)(?:\\s*ранг(?:а|ов)?)?$",
        RegexOption.IGNORE_CASE,
    ).matchEntire(text)
    if (counted != null) {
        count = when (counted.groupValues[1].lowercase()) {
            "два", "две" -> 2
            "три" -> 3
            "четыре" -> 4
            "пять" -> 5
            else -> counted.groupValues[1].toIntOrNull() ?: return null
        }
        name = counted.groupValues[2].trim()
        need = counted.groupValues[3].toIntOrNull() ?: return null
    } else {
        requirementRank.matchEntire(text)?.let {
            name = it.groupValues[1].trim()
            need = it.groupValues[2].toIntOrNull() ?: return null
        }
    }
    if (count < 1) return null
    val normalized = developmentAlias(name)
    if (normalized in setOf("любое умение", "умения", "умений")) {
        return DevelopmentSkillRequirement(source, need, count, anySkill = true)
    }
    if (normalized == "любое умение ближнего боя") {
        return DevelopmentSkillRequirement(source, need, count, definitionIds = setOf("melee_weapon", "unarmed"))
    }
    familyName(name)?.let { (family, specialization) ->
        val choices = if (specialization.isBlank() || specialization in anySpecialization) emptyList()
        else {
            // Preserve display capitalization from the requirement, not the normalized matcher.
            val display = name.substringAfter('(', name.substringAfter(':', specialization)).substringBeforeLast(')')
            display.split('/').map(String::trim).filter(String::isNotBlank).distinctBy(::developmentNormalize)
        }
        return DevelopmentSkillRequirement(source, need, count, family, choices)
    }
    val definitions = SkillCatalog.builtIns.filter { developmentAlias(it.name) == normalized }
    if (definitions.isNotEmpty()) {
        return DevelopmentSkillRequirement(source, need, count, definitionIds = definitions.mapTo(linkedSetOf()) { it.id })
    }
    return null
}
