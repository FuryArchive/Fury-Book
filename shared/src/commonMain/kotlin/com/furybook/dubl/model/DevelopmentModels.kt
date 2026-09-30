package com.furybook.dubl.model

enum class DevelopmentCostType {
    XP,
    ABILITY,
}

data class AbilityOption(
    val source: String,
    val value: Int,
)

data class DevelopmentEntry(
    val id: String,
    val name: String,
    val section: String,
    val category: String,
    val cost: Int,
    val costType: DevelopmentCostType,
    val maxRank: Int,
    val requirements: String,
    val benefit: String,
    val notes: String,
    val tags: List<String>,
    val accessId: String?,
    val abilityOptions: List<AbilityOption>,
    val incomplete: Boolean,
    val repeatable: Boolean,
    val perfectRoot: Boolean,
    val mechanicsConflict: String,
    val conflictNote: String,
) {
    val isAbility: Boolean get() = costType == DevelopmentCostType.ABILITY

    val isMartialArt: Boolean
        get() = developmentNormalize(section) == "боевые искусства" ||
            tags.any { developmentNormalize(it) == "боевые искусства" }

    /**
     * Special development is intentionally broader than accessId != null.
     * Some rulebook branches (perfect abilities / animagia / entropy) are XP-only
     * and therefore have no separate ability-point access record, but still belong
     * to the special-branch catalogue rather than the ordinary feat list.
     */
    val isChiDevelopment: Boolean
        get() = developmentNormalize(section) == "ци" || tags.any { developmentNormalize(it) == "ци" }

    val isSpecialDevelopment: Boolean
        get() = !isMartialArt && !isChiDevelopment && (isAbility || accessId != null || developmentNormalize(section) == "ветки способностей")

    val isRegularDevelopment: Boolean
        get() = !isMartialArt && !isChiDevelopment && !isSpecialDevelopment && costType == DevelopmentCostType.XP
}


fun DevelopmentEntry.normalizedLocalCopy(forcedId: String = id): DevelopmentEntry = copy(
    id = forcedId.trim(),
    name = name.trim().replace(Regex("\\s+"), " ").ifBlank { "Без названия" },
    section = section.trim(),
    category = category.trim(),
    cost = cost.coerceAtLeast(0),
    maxRank = maxRank.coerceAtLeast(1),
    requirements = requirements.trim(),
    benefit = benefit.trim(),
    notes = notes.trim(),
    tags = tags.map(String::trim).filter(String::isNotBlank).distinct(),
    accessId = accessId?.trim()?.takeIf(String::isNotBlank),
    abilityOptions = abilityOptions.map { option ->
        option.copy(source = option.source.trim(), value = option.value.coerceAtLeast(0))
    },
    mechanicsConflict = mechanicsConflict.trim(),
    conflictNote = conflictNote.trim(),
)

fun DublCharacter.effectiveDevelopmentCatalog(canonical: DevelopmentCatalog): DevelopmentCatalog {
    val canonicalIds = canonical.entries.mapTo(linkedSetOf()) { it.id }
    val overridden = canonical.entries.map { entry ->
        developmentOverrides[entry.id]?.normalizedLocalCopy(entry.id) ?: entry
    }
    val custom = customDevelopmentEntries
        .map { it.normalizedLocalCopy() }
        .filter { it.id.isNotBlank() && it.id !in canonicalIds }
        .distinctBy { it.id }
    return DevelopmentCatalog(
        version = if (developmentOverrides.isEmpty() && custom.isEmpty()) canonical.version else canonical.version + "+local",
        entries = overridden + custom,
    )
}

fun DevelopmentCatalog.withoutChiContent(): DevelopmentCatalog = DevelopmentCatalog(
    version = version + "+no-chi",
    entries = entries.filterNot { it.isChiDevelopment },
)

fun DevelopmentCatalog.withoutEntries(entryIds: Set<String>): DevelopmentCatalog {
    if (entryIds.isEmpty()) return this
    return DevelopmentCatalog(
        version = version + "+filtered",
        entries = entries.filterNot { it.id in entryIds },
    )
}

data class OwnedDevelopment(
    val rank: Int = 0,
    val optionIndex: Int = 0,
)

internal fun developmentManaRequirementRank(text: String): Int? {
    val match = Regex(
        "^(?:Базовый\\s+)?Запас маны(?:\\s*\\(?\\s*(IV|V|III|II|I|\\d+)\\s*\\)?)?",
        RegexOption.IGNORE_CASE,
    ).find(text.trim()) ?: return null
    val token = match.groupValues.getOrNull(1).orEmpty().uppercase()
    return when (token) {
        "" -> 1
        "I" -> 1
        "II" -> 2
        "III" -> 3
        "IV" -> 4
        "V" -> 5
        else -> token.toIntOrNull()
    }
}

internal fun DublCharacter.effectiveManaRankForDevelopment(): Int =
    maxOf(
        magic.manaRank,
        development[MagicEquipmentRules.BASE_MANA_ENTRY_ID]?.rank ?: 0,
    ).coerceIn(0, 5)

data class DevelopmentProgress(
    val owned: Map<String, OwnedDevelopment> = emptyMap(),
) {
    fun rank(entryId: String): Int = owned[entryId]?.rank ?: 0
    fun optionIndex(entryId: String): Int = owned[entryId]?.optionIndex ?: 0

    fun withRank(entry: DevelopmentEntry, rank: Int, optionIndex: Int = optionIndex(entry.id)): DevelopmentProgress {
        val normalizedRank = rank.coerceIn(0, entry.maxRank.coerceAtLeast(1))
        val next = owned.toMutableMap()
        if (normalizedRank == 0) {
            next.remove(entry.id)
        } else {
            next[entry.id] = OwnedDevelopment(
                rank = normalizedRank,
                optionIndex = optionIndex.coerceIn(0, (entry.abilityOptions.size - 1).coerceAtLeast(0)),
            )
        }
        return copy(owned = next)
    }
}

enum class RequirementStatus {
    OK,
    FAIL,
    MANUAL,
}

data class RequirementCheck(
    val status: RequirementStatus,
    val text: String,
    val targetEntryId: String? = null,
)

data class DevelopmentAvailability(
    val checks: List<RequirementCheck>,
    val currentRank: Int,
    val maxRank: Int,
    val abilityCost: Int,
    val canIncrease: Boolean,
    val canForceIncrease: Boolean,
    val reason: String,
)

enum class DevelopmentSheetSectionType {
    REGULAR,
    SPECIAL,
    MARTIAL_ARTS,
    CHI,
}

enum class DevelopmentSheetItemSource {
    DEVELOPMENT,
    MAGIC_SCHOOL,
}

data class DevelopmentSheetItem(
    val entry: DevelopmentEntry,
    val rank: Int,
    val optionIndex: Int,
    val depth: Int,
    val parentId: String? = null,
    val source: DevelopmentSheetItemSource = DevelopmentSheetItemSource.DEVELOPMENT,
)

data class DevelopmentSheetSection(
    val type: DevelopmentSheetSectionType,
    val items: List<DevelopmentSheetItem>,
)

class DevelopmentCatalog(
    val version: String,
    val entries: List<DevelopmentEntry>,
) {
    private val byIdMap = entries.associateBy { it.id }
    private val byNameMap = entries.groupBy { developmentAlias(it.name) }

    fun byId(id: String?): DevelopmentEntry? = id?.let(byIdMap::get)

    fun matchingName(name: String): List<DevelopmentEntry> {
        val normalized = developmentAlias(name)
        val exact = byNameMap[normalized].orEmpty()
        if (exact.isNotEmpty()) return exact
        return entries.filter { entry ->
            entry.name.split(" / ").any { developmentAlias(it) == normalized }
        }
    }

    fun childrenOf(entryId: String): List<DevelopmentEntry> = entries
        .filter { it.accessId == entryId }
        .sortedBy { developmentNormalize(it.name) }
}

private val DEVELOPMENT_ALIASES = mapOf(
    "ассасин" to "ассассин",
    "боевой крик" to "боевые крики",
    "мастер ловушек" to "капканщик / мастер ловушек",
    "ул. инициатива" to "улучшенная инициатива",
    "инженерное дело" to "инженерное дело (ремонт)",
    "тело" to "телосложение",
    "рукопашный" to "рукопашный бой",
    "рукопашное" to "рукопашный бой",
    "холодное" to "холодное оружие",
)

fun developmentNormalize(value: String): String = value
    .lowercase()
    .replace('ё', 'е')
    .replace(Regex("\\s+"), " ")
    .trim(' ', '.', ',', ':', ';')

fun developmentAlias(value: String): String {
    val normalized = developmentNormalize(value)
    return DEVELOPMENT_ALIASES[normalized] ?: normalized
}

class DevelopmentRules(
    private val character: DublCharacter,
    private val catalog: DevelopmentCatalog,
    private val progress: DevelopmentProgress,
) {
    private val requirementsMemo = mutableMapOf<String, List<RequirementCheck>>()
    private val allSkills by lazy { character.resolvedSkills(includeHidden = true) }

    fun requirements(entry: DevelopmentEntry): List<RequirementCheck> =
        requirementsInternal(entry, emptySet())

    fun ownedSheetSections(): List<DevelopmentSheetSection> {
        val ownedEntries = progress.owned.entries.mapNotNull { (id, owned) ->
            catalog.byId(id)?.takeIf { owned.rank > 0 }?.let { it to owned }
        }

        fun buildSection(
            type: DevelopmentSheetSectionType,
            source: List<Pair<DevelopmentEntry, OwnedDevelopment>>,
        ): DevelopmentSheetSection? {
            if (source.isEmpty()) return null
            val sourceById = source.associateBy { it.first.id }
            val ids = sourceById.keys
            val parentById = source.associate { (entry, _) ->
                val parent = entry.accessId?.takeIf { it in ids }
                    ?: requirements(entry).asSequence()
                        .mapNotNull { it.targetEntryId }
                        .firstOrNull { it != entry.id && it in ids }
                entry.id to parent
            }
            val children = source.groupBy { (entry, _) -> parentById[entry.id] }
            val result = mutableListOf<DevelopmentSheetItem>()
            val visited = mutableSetOf<String>()

            fun append(entry: DevelopmentEntry, owned: OwnedDevelopment, depth: Int) {
                if (!visited.add(entry.id)) return
                result += DevelopmentSheetItem(
                    entry = entry,
                    rank = owned.rank,
                    optionIndex = owned.optionIndex,
                    depth = depth,
                    parentId = parentById[entry.id],
                )
                children[entry.id].orEmpty()
                    .sortedBy { developmentNormalize(it.first.name) }
                    .forEach { (childEntry, childOwned) -> append(childEntry, childOwned, depth + 1) }
            }

            source.filter { (entry, _) -> parentById[entry.id] == null }
                .sortedBy { developmentNormalize(it.first.name) }
                .forEach { (entry, owned) -> append(entry, owned, 0) }

            source.filterNot { (entry, _) -> entry.id in visited }
                .sortedBy { developmentNormalize(it.first.name) }
                .forEach { (entry, owned) -> append(entry, owned, 0) }

            return DevelopmentSheetSection(type, result)
        }

        val regular = ownedEntries.filter { (entry, _) -> entry.isRegularDevelopment }
        val special = ownedEntries.filter { (entry, _) -> entry.isSpecialDevelopment }
        val martial = ownedEntries.filter { (entry, _) -> entry.isMartialArt }
        val chi = ownedEntries.filter { (entry, _) -> entry.isChiDevelopment }

        val magicSchoolItems = character.magic.schools
            .mapNotNull { school ->
                val canonical = MagicSchoolCatalog.canonicalizeOrNull(school.name) ?: return@mapNotNull null
                canonical to school
            }
            .groupBy({ it.first }, { it.second })
            .map { (canonical, copies) ->
                val rank = copies.maxOf { it.rank.coerceAtLeast(0) }
                val note = copies.firstOrNull { it.note.isNotBlank() }?.note.orEmpty()
                canonical to DevelopmentSheetItem(
                    entry = DevelopmentEntry(
                        id = "magic-school:${developmentNormalize(canonical)}",
                        name = canonical,
                        section = "Ветки способностей",
                        category = "Школы магии",
                        cost = 25,
                        costType = DevelopmentCostType.XP,
                        maxRank = rank.coerceAtLeast(1),
                        requirements = "-",
                        benefit = "Школа магии · Сила магии $rank",
                        notes = note,
                        tags = listOf("Школа магии"),
                        accessId = null,
                        abilityOptions = emptyList(),
                        incomplete = false,
                        repeatable = false,
                        perfectRoot = false,
                        mechanicsConflict = "",
                        conflictNote = "",
                    ),
                    rank = rank,
                    optionIndex = 0,
                    depth = 0,
                    source = DevelopmentSheetItemSource.MAGIC_SCHOOL,
                )
            }
            .filter { (_, item) -> item.rank > 0 }
            .sortedBy { (canonical, _) -> MagicSchoolCatalog.sortIndex(canonical) }
            .map { it.second }

        val specialSection = buildSection(DevelopmentSheetSectionType.SPECIAL, special)
        val combinedSpecial = (specialSection?.items.orEmpty() + magicSchoolItems).takeIf { it.isNotEmpty() }?.let {
            DevelopmentSheetSection(DevelopmentSheetSectionType.SPECIAL, it)
        }

        return listOfNotNull(
            buildSection(DevelopmentSheetSectionType.REGULAR, regular),
            combinedSpecial,
            buildSection(DevelopmentSheetSectionType.MARTIAL_ARTS, martial),
            buildSection(DevelopmentSheetSectionType.CHI, chi),
        )
    }

    fun abilityPointsSpent(): Int = progress.owned.entries.sumOf { (id, owned) ->
        val entry = catalog.byId(id) ?: return@sumOf 0
        if (!entry.isAbility) return@sumOf 0
        abilityCost(entry, owned.optionIndex) * owned.rank
    }

    fun abilityPointsBudget(): Int = character.abilityPoints

    fun abilityPointsAvailable(): Int = abilityPointsBudget() - abilityPointsSpent()

    fun xpSpentOnDevelopment(): Int = progress.owned.entries.sumOf { (id, owned) ->
        if (id == MagicEquipmentRules.BASE_MANA_ENTRY_ID) return@sumOf 0
        val entry = catalog.byId(id) ?: return@sumOf 0
        if (entry.isAbility) 0 else entry.cost.coerceAtLeast(0) * owned.rank
    }

    fun abilityCost(entry: DevelopmentEntry, optionIndex: Int): Int {
        if (!entry.isAbility) return 0
        if (entry.abilityOptions.isNotEmpty()) {
            return entry.abilityOptions[optionIndex.coerceIn(0, entry.abilityOptions.lastIndex)].value.coerceAtLeast(0)
        }
        return entry.cost.coerceAtLeast(0)
    }

    fun availability(entry: DevelopmentEntry, optionIndex: Int = progress.optionIndex(entry.id)): DevelopmentAvailability {
        val currentRank = progress.rank(entry.id)
        val checks = requirements(entry)
        val abilityCost = abilityCost(entry, optionIndex)
        val failed = checks.any { it.status == RequirementStatus.FAIL }
        val manual = checks.any { it.status == RequirementStatus.MANUAL }
        val maxed = currentRank >= entry.maxRank.coerceAtLeast(1)
        // The rulebook phrases the OS budget as a recommendation, not a hard limit.
        // Overspending is surfaced by the budget summary but does not invalidate a GM-approved build.
        val canIncrease = !entry.incomplete && !failed && !manual && !maxed
        val canForceIncrease = !maxed && (entry.incomplete || failed || manual)
        val reason = when {
            entry.incomplete -> "Запись книги не завершена"
            maxed -> "Максимальный ранг"
            failed -> "Не выполнены требования"
            manual -> "Требуется ручная проверка"
            else -> "Можно получить"
        }
        return DevelopmentAvailability(
            checks = checks,
            currentRank = currentRank,
            maxRank = entry.maxRank.coerceAtLeast(1),
            abilityCost = abilityCost,
            canIncrease = canIncrease,
            canForceIncrease = canForceIncrease,
            reason = reason,
        )
    }

    fun featureRank(name: String): Int {
        val featureAlias = developmentAlias(name)
        if (
            featureAlias == developmentAlias("Базовый запас маны") ||
            featureAlias == developmentAlias("Запас маны")
        ) {
            return character.effectiveManaRankForDevelopment()
        }
        val known = catalog.matchingName(name)
        val preferred = known.filterNot { it.isAbility }.ifEmpty { known }
        return preferred.maxOfOrNull { progress.rank(it.id) } ?: 0
    }

    private fun requirementsInternal(entry: DevelopmentEntry, seen: Set<String>): List<RequirementCheck> {
        if (entry.id !in seen) {
            requirementsMemo[entry.id]?.let { return it }
        }

        val checks = mutableListOf<RequirementCheck>()
        val nextSeen = seen + entry.id

        entry.accessId?.let { accessId ->
            val access = catalog.byId(accessId)
            if (access == null) {
                checks += RequirementCheck(RequirementStatus.MANUAL, "Неизвестная ветка доступа")
            } else {
                val owned = progress.rank(access.id) > 0
                val status = if (!owned) {
                    RequirementStatus.FAIL
                } else if (access.id in seen) {
                    RequirementStatus.MANUAL
                } else {
                    val previous = requirementsInternal(access, nextSeen)
                    when {
                        previous.any { it.status == RequirementStatus.FAIL } -> RequirementStatus.FAIL
                        previous.any { it.status == RequirementStatus.MANUAL } -> RequirementStatus.MANUAL
                        else -> RequirementStatus.OK
                    }
                }
                checks += RequirementCheck(
                    status,
                    "Доступ к ветке: ${access.name}",
                    access.id,
                )
            }
        }

        if (entry.perfectRoot) {
            val other = progress.owned.keys
                .mapNotNull(catalog::byId)
                .filter { it.perfectRoot && it.id != entry.id && progress.rank(it.id) > 0 }
            if (other.isNotEmpty()) {
                checks += RequirementCheck(
                    RequirementStatus.FAIL,
                    "Только одна совершенная способность; уже выбрана: ${other.joinToString { it.name }}",
                    other.first().id,
                )
            }
        }

        val raw = entry.requirements.trim(' ', '.', ',', ';')
        if (raw.isNotBlank() && raw != "-" && raw != "—") {
            requirementParts(raw).forEach { part ->
                checks += checkAtom(part, entry, nextSeen)
            }
        }

        if (entry.mechanicsConflict.isNotBlank()) {
            checks += RequirementCheck(RequirementStatus.MANUAL, entry.mechanicsConflict)
        }
        if (entry.incomplete) {
            checks += RequirementCheck(
                RequirementStatus.MANUAL,
                "В исходной записи не завершены механика или реквизиты",
            )
        }

        val result = checks.ifEmpty {
            listOf(RequirementCheck(RequirementStatus.OK, "Без требований"))
        }
        if (entry.id !in seen) requirementsMemo[entry.id] = result
        return result
    }

    private fun checkAtom(
        source: String,
        entry: DevelopmentEntry,
        seen: Set<String>,
    ): RequirementCheck {
        val text = source.trim(' ', '.', ';')
        if (text.isBlank() || text == "-" || text == "—") {
            return RequirementCheck(RequirementStatus.OK, "Без требований")
        }

        if (Regex("только\\s+при\\s+создании|при\\s+создании\\s+персонажа", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            val alreadyOwned = progress.rank(entry.id) > 0
            return RequirementCheck(
                if (!character.creationComplete || alreadyOwned) RequirementStatus.OK else RequirementStatus.FAIL,
                if (character.creationComplete && !alreadyOwned) "$text — создание уже завершено" else text,
            )
        }
        if (Regex("на усмотрение|согласован", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return RequirementCheck(RequirementStatus.MANUAL, text)
        }

        martialArtsRequirement(text, seen)?.let { return it }

        if (Regex("\\sили\\s", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            val parts = text.split(Regex("\\s+или\\s+", RegexOption.IGNORE_CASE))
            val trailingRank = Regex("\\s(\\d+)$").find(parts.last())?.groupValues?.getOrNull(1)
            val expanded = if (trailingRank != null) {
                parts.map { part ->
                    if (Regex("\\d+$").containsMatchIn(part.trim())) part else "$part $trailingRank"
                }
            } else {
                parts
            }
            val alternatives = expanded.map { checkAtom(it, entry, seen) }
            val status = when {
                alternatives.any { it.status == RequirementStatus.OK } -> RequirementStatus.OK
                alternatives.any { it.status == RequirementStatus.MANUAL } -> RequirementStatus.MANUAL
                else -> RequirementStatus.FAIL
            }
            return RequirementCheck(
                status = status,
                text = alternatives.joinToString(" или ") { it.text },
                targetEntryId = alternatives.firstOrNull { it.status == RequirementStatus.OK }?.targetEntryId
                    ?: alternatives.firstOrNull()?.targetEntryId,
            )
        }

        developmentManaRequirementRank(text)?.let { needMana ->
            return valueCheck(
                "Базовый запас маны",
                character.effectiveManaRankForDevelopment(),
                needMana,
            )
        }
        if (developmentNormalize(text).startsWith("заклинание:")) {
            val requested = text.substringAfter(':').trim()
            val learned = character.magic.spells.any { it.learned && developmentAlias(it.name) == developmentAlias(requested) }
            return RequirementCheck(
                if (learned) RequirementStatus.OK else RequirementStatus.FAIL,
                if (learned) "Заклинание: $requested" else "Заклинание: $requested — не изучено",
            )
        }
        val spellCountRequirement = Regex("^Знать\\s*(\\d+)\\s*заклинани", RegexOption.IGNORE_CASE).find(text)
        if (spellCountRequirement != null) {
            val needSpells = spellCountRequirement.groupValues[1].toIntOrNull() ?: 0
            val haveSpells = character.magic.spells.count { it.learned }
            return valueCheck("Изученные заклинания", haveSpells, needSpells)
        }

        val numeric = Regex("^(.+?)\\s*:?\\s*(\\d+)(?:\\s*ранг(?:а|ов)?)?$", RegexOption.IGNORE_CASE)
            .matchEntire(text)
        var featureName = developmentAlias(text)
        var need = 1
        if (numeric != null) {
            val originalName = numeric.groupValues[1].trim()
            featureName = developmentAlias(originalName)
            need = numeric.groupValues[2].toIntOrNull() ?: 1
            numericRequirement(originalName, featureName, need)?.let { return it }
        } else {
            skillRequirement(text, 1)?.let { return it }
        }

        val known = catalog.matchingName(featureName)
        val preferred = known.filterNot { it.isAbility }.ifEmpty { known }
        if (preferred.isNotEmpty()) {
            val maximum = preferred.maxOf { it.maxRank.coerceAtLeast(1) }
            if (need > maximum) {
                return RequirementCheck(
                    RequirementStatus.MANUAL,
                    "$text: требование выше максимального ранга в книге",
                    preferred.first().id,
                )
            }
            val owned = preferred.filter { progress.rank(it.id) >= need }
            if (owned.isEmpty()) {
                return RequirementCheck(
                    RequirementStatus.FAIL,
                    "$text: не изучено",
                    preferred.first().id,
                )
            }
            for (candidate in owned) {
                if (candidate.id in seen) {
                    return RequirementCheck(RequirementStatus.MANUAL, "$text: цикл требований", candidate.id)
                }
                val previous = requirementsInternal(candidate, seen + candidate.id)
                if (previous.all { it.status == RequirementStatus.OK }) {
                    return RequirementCheck(RequirementStatus.OK, text, candidate.id)
                }
            }
            val previousStatuses = owned.flatMap { requirementsInternal(it, seen + it.id) }
            val status = if (previousStatuses.any { it.status == RequirementStatus.FAIL }) {
                RequirementStatus.FAIL
            } else {
                RequirementStatus.MANUAL
            }
            return RequirementCheck(
                status,
                "$text: проверьте требования предшествующего навыка",
                owned.first().id,
            )
        }

        val battleCryMatch = Regex("^Любые (два|три) боевых крика", RegexOption.IGNORE_CASE).find(text)
        if (battleCryMatch != null) {
            val needCries = if (battleCryMatch.groupValues[1].lowercase() == "два") 2 else 3
            val have = progress.owned.keys.count { id ->
                val owned = progress.rank(id) > 0
                val feat = catalog.byId(id)
                owned && feat?.tags?.any { it == "Боевой крик" } == true
            }
            return RequirementCheck(
                if (have >= needCries) RequirementStatus.OK else RequirementStatus.FAIL,
                "Боевые крики: $have / $needCries",
            )
        }

        return RequirementCheck(RequirementStatus.MANUAL, text)
    }

    private fun requirementParts(raw: String): List<String> = raw
        .split(Regex("[;\\n]+"))
        .flatMap { segment ->
            val clean = segment.trim()
            if (Regex("^Боевые\\s+искусства\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
                splitMartialColonSegment(clean)
            } else {
                clean.split(Regex(",+(?![^()]*\\))"))
            }
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun splitMartialColonSegment(segment: String): List<String> {
        val prefix = segment.substringBefore(':').trim()
        val body = segment.substringAfter(':').trim()
        val commaParts = body.split(Regex(",+(?![^()]*\\))"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (commaParts.size <= 1) return listOf(segment)

        val martialParts = mutableListOf<String>()
        val trailingRequirements = mutableListOf<String>()
        var martialListClosed = false

        commaParts.forEach { rawPart ->
            val cleanPart = rawPart.trim(' ', '.', ';')
            val withoutLeadingOr = cleanPart.replaceFirst(Regex("^или\\s+", RegexOption.IGNORE_CASE), "").trim()
            val knownNonMartial = catalog.matchingName(withoutLeadingOr).any { !it.isMartialArt }
            val numericRequirement = Regex(".*\\s\\d+(?:\\s*ранг(?:а|ов)?)?$", RegexOption.IGNORE_CASE).matches(withoutLeadingOr)
            val knownSkill = allSkills.any { developmentAlias(it.name) == developmentAlias(withoutLeadingOr) }
            val shouldBeTrailing = martialListClosed || knownNonMartial || numericRequirement || knownSkill

            if (shouldBeTrailing) {
                martialListClosed = true
                trailingRequirements += cleanPart
            } else {
                martialParts += cleanPart
                if (Regex("\\s+или\\s+", RegexOption.IGNORE_CASE).containsMatchIn(cleanPart)) {
                    martialListClosed = true
                }
            }
        }

        if (trailingRequirements.isEmpty() || martialParts.isEmpty()) return listOf(segment)
        return listOf("$prefix: ${martialParts.joinToString(", ")}") + trailingRequirements
    }

    private fun martialArtsRequirement(text: String, seen: Set<String>): RequirementCheck? {
        val match = Regex(
            "^Боевые\\s+искусства\\s*(?:\\(([^)]+)\\)|:\\s*(.+))$",
            RegexOption.IGNORE_CASE,
        ).matchEntire(text) ?: return null

        val rawStyles = match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
        val requested = rawStyles
            .split(Regex("\\s*,\\s*|\\s+или\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim(' ', '.', ',') }
            .filter { it.isNotBlank() }

        val ownedStyles = catalog.entries.filter { candidate ->
            candidate.isMartialArt &&
                candidate.tags.any { developmentNormalize(it) == "боевой стиль" } &&
                progress.rank(candidate.id) > 0
        }

        if (requested.any { developmentNormalize(it) == "любое" }) {
            val validOwned = ownedStyles.firstOrNull { candidate ->
                candidate.id !in seen && requirementsInternal(candidate, seen + candidate.id)
                    .all { it.status == RequirementStatus.OK }
            }
            return RequirementCheck(
                status = if (validOwned != null) RequirementStatus.OK else RequirementStatus.FAIL,
                text = if (validOwned != null) {
                    "Боевые искусства (любое): ${validOwned.name}"
                } else {
                    "Боевые искусства (любое): не изучено"
                },
                targetEntryId = validOwned?.id,
            )
        }

        val knownStyles = requested.flatMap { styleName ->
            catalog.matchingName(styleName).filter { candidate ->
                candidate.isMartialArt && candidate.tags.any { developmentNormalize(it) == "боевой стиль" }
            }
        }.distinctBy { it.id }

        if (knownStyles.isEmpty()) {
            return RequirementCheck(RequirementStatus.MANUAL, text)
        }

        val validOwned = knownStyles.firstOrNull { candidate ->
            progress.rank(candidate.id) > 0 &&
                candidate.id !in seen &&
                requirementsInternal(candidate, seen + candidate.id).all { it.status == RequirementStatus.OK }
        }
        if (validOwned != null) {
            return RequirementCheck(RequirementStatus.OK, text, validOwned.id)
        }

        val ownedButInvalid = knownStyles.firstOrNull { progress.rank(it.id) > 0 }
        if (ownedButInvalid != null) {
            val previous = requirementsInternal(ownedButInvalid, seen + ownedButInvalid.id)
            val status = if (previous.any { it.status == RequirementStatus.FAIL }) {
                RequirementStatus.FAIL
            } else {
                RequirementStatus.MANUAL
            }
            return RequirementCheck(
                status,
                "$text: проверьте требования выбранного боевого стиля",
                ownedButInvalid.id,
            )
        }

        return RequirementCheck(
            RequirementStatus.FAIL,
            "$text: ни один подходящий стиль не изучен",
            knownStyles.first().id,
        )
    }

    private fun numericRequirement(
        originalName: String,
        normalizedName: String,
        need: Int,
    ): RequirementCheck? {
        val attribute = AttributeId.entries.firstOrNull {
            developmentAlias(it.title) == normalizedName || developmentAlias(it.shortTitle) == normalizedName
        }
        if (attribute != null) {
            val actual = character.attribute(attribute)
            return valueCheck(originalName, actual, need)
        }

        when (normalizedName) {
            "стойкость" -> return valueCheck(originalName, character.fortitude + featureRank("Стойкий"), need)
            "любая характеристика" -> return valueCheck(originalName, AttributeId.entries.maxOf(character::attribute), need)
            "любые две характеристики" -> {
                val values = AttributeId.entries.map(character::attribute).sortedDescending()
                return valueCheck(originalName, values.getOrElse(1) { 0 }, need)
            }
            "любое умение" -> return valueCheck(originalName, allSkills.maxOfOrNull { it.rank } ?: 0, need)
            "любое умение ближнего боя" -> {
                val actual = maxOf(skillRank("Холодное оружие"), skillRank("Рукопашный бой"))
                return valueCheck(originalName, actual, need)
            }
            "сила магии", "сила заклинаний" -> return valueCheck(originalName, MagicEquipmentRules.highestMagicPower(character), need)
        }

        if (normalizedName.startsWith("любые два умения")) {
            val ranks = allSkills.map { it.rank }.sortedDescending()
            return valueCheck(originalName, ranks.getOrElse(1) { 0 }, need)
        }

        skillRequirement(originalName, need)?.let { return it }

        val family = when {
            Regex("^знани[ея](\\s*\\(любое\\))?$", RegexOption.IGNORE_CASE).matches(originalName) -> "знание"
            Regex("^ремесло(\\s*\\(любое\\))?$", RegexOption.IGNORE_CASE).matches(originalName) -> "ремесло"
            Regex("^исполнение(\\s*\\(любое\\))?$", RegexOption.IGNORE_CASE).matches(originalName) -> "исполнение"
            else -> null
        }
        if (family != null) {
            val actual = allSkills
                .filter { developmentNormalize(it.name).startsWith(family) }
                .maxOfOrNull { it.rank } ?: 0
            return valueCheck(originalName, actual, need)
        }

        return null
    }

    private fun skillRequirement(name: String, need: Int): RequirementCheck? {
        val normalized = developmentAlias(name)
        val exact = allSkills.filter { developmentAlias(it.name) == normalized }
        if (exact.isNotEmpty()) {
            return valueCheck(name, exact.maxOf { it.rank }, need)
        }

        // The rulebook frequently shortens combat-skill names in requirements
        // (e.g. "Рукопашный" / "Холодное"). Accept a unique prefix,
        // but never guess when more than one skill could match.
        val prefixMatches = allSkills.filter { skill ->
            val skillName = developmentAlias(skill.name)
            normalized.length >= 4 && (skillName.startsWith(normalized) || normalized.startsWith(skillName))
        }
        if (prefixMatches.map { developmentAlias(it.name) }.distinct().size == 1 && prefixMatches.isNotEmpty()) {
            return valueCheck(name, prefixMatches.maxOf { it.rank }, need)
        }
        return null
    }

    private fun skillRank(name: String): Int = allSkills
        .filter { developmentAlias(it.name) == developmentAlias(name) }
        .maxOfOrNull { it.rank } ?: 0

    private fun valueCheck(label: String, actual: Int, need: Int): RequirementCheck = RequirementCheck(
        status = if (actual >= need) RequirementStatus.OK else RequirementStatus.FAIL,
        text = "$label: $actual / $need",
    )
}
