package com.furybook.dubl.model

const val MAGIC_SCHOOL_SHEET_ID_PREFIX: String = "magic-school:"

fun isMagicSchoolSheetEntryId(id: String): Boolean = id.startsWith(MAGIC_SCHOOL_SHEET_ID_PREFIX)

fun DublCharacter.characterSheetDevelopmentSections(
    catalog: DevelopmentCatalog,
): List<DevelopmentSheetSection> {
    val base = DevelopmentRules(this, catalog, DevelopmentProgress(development))
        .ownedSheetSections()
        .toMutableList()
    val schools = magic.schools
        .filter { it.rank > 0 && MagicSchoolCatalog.canonicalizeOrNull(it.name) != null }
        .sortedWith(compareBy<MagicSchool> { MagicSchoolCatalog.sortIndex(it.name) }.thenBy { it.name })
        .map { school ->
            val canonical = MagicSchoolCatalog.canonicalize(school.name)
            DevelopmentSheetItem(
                entry = DevelopmentEntry(
                    id = MAGIC_SCHOOL_SHEET_ID_PREFIX + developmentNormalize(canonical).replace(' ', '-'),
                    name = canonical,
                    section = "Магия",
                    category = "Школы магии",
                    cost = 25,
                    costType = DevelopmentCostType.XP,
                    maxRank = maxOf(1, school.rank),
                    requirements = "",
                    benefit = "Сила магии школы: ${school.rank}",
                    notes = school.note,
                    tags = listOf("Школа магии", "Спец. навык"),
                    accessId = null,
                    abilityOptions = emptyList(),
                    incomplete = false,
                    repeatable = true,
                    perfectRoot = false,
                    mechanicsConflict = "",
                    conflictNote = "",
                ),
                rank = school.rank,
                optionIndex = 0,
                depth = 0,
            )
        }

    if (schools.isEmpty()) return base

    val specialIndex = base.indexOfFirst { it.type == DevelopmentSheetSectionType.SPECIAL }
    if (specialIndex >= 0) {
        val special = base[specialIndex]
        base[specialIndex] = special.copy(items = special.items + schools)
    } else {
        base += DevelopmentSheetSection(DevelopmentSheetSectionType.SPECIAL, schools)
    }
    return base
}


fun ensureMagicSchoolsInSpecialGroup(
    saved: List<SheetGroup>,
    defaults: List<SheetGroup>,
    validItemIds: List<String>,
): List<SheetGroup> {
    if (saved.isEmpty()) return saved
    val assigned = saved.flatMapTo(mutableSetOf()) { it.itemIds }
    val missingMagicSchools = validItemIds.filter { isMagicSchoolSheetEntryId(it) && it !in assigned }
    if (missingMagicSchools.isEmpty()) return saved

    val defaultSpecial = defaults.firstOrNull { it.title == "Спец. навыки" }
        ?: return saved
    val result = saved.toMutableList()
    val index = result.indexOfFirst { it.id == defaultSpecial.id }
    if (index >= 0) {
        result[index] = result[index].copy(itemIds = result[index].itemIds + missingMagicSchools)
    } else {
        result += defaultSpecial.copy(itemIds = missingMagicSchools)
    }
    return result
}
