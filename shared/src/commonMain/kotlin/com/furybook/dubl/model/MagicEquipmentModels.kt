package com.furybook.dubl.model

import kotlin.math.max

data class MagicSchool(
    val name: String = "Школа",
    val rank: Int = 0,
    val note: String = "",
)

data class KnownSpell(
    val uid: String,
    val catalogId: String? = null,
    val name: String = "Заклинание",
    val school: String = "",
    val cost: Int = 0,
    val manaText: String = "",
    val time: String = "",
    val range: String = "",
    val area: String = "",
    val action: String = "",
    val duration: String = "",
    val description: String = "",
    val enhancement: String = "",
    val learned: Boolean = true,
    val xpOverride: Int? = null,
    val incomplete: Boolean = false,
    val conflictNote: String = "",
    val custom: Boolean = false,
)

data class CharacterMagic(
    val manaRank: Int = 0,
    val power: Int = 0,
    val schools: List<MagicSchool> = emptyList(),
    val spells: List<KnownSpell> = emptyList(),
)

object MagicSchoolCatalog {
    val schools: List<String> = listOf(
        "Боевая магия",
        "Воплощение",
        "Друид",
        "Магия крови",
        "Молитва",
        "Некромантия",
        "Ограждение",
        "Призыв",
        "Природа",
        "Прорицание",
        "Разрушение",
        "Разум",
        "Трансмутация",
    )

    private val aliases = mapOf(
        "ограждения" to "Ограждение",
        "молитвы" to "Молитва",
        "некромант" to "Некромантия",
        "друидичество" to "Друид",
        "друидическая магия" to "Друид",
        "боевая" to "Боевая магия",
    )

    fun canonicalizeOrNull(value: String): String? {
        val clean = value.trim().lowercase().replace('ё', 'е')
        if (clean.isBlank() || clean == "-") return null
        aliases[clean]?.let { return it }
        return schools.firstOrNull { it.lowercase().replace('ё', 'е') == clean }
    }

    fun canonicalize(value: String): String = canonicalizeOrNull(value) ?: value.trim()

    fun parseSchools(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val expanded = raw
            .replace("(", " / ")
            .replace(")", " ")
            .replace(Regex("/+"), " / ")
        return expanded
            .split('/')
            .mapNotNull { canonicalizeOrNull(it) }
            .distinct()
            .sortedBy(::sortIndex)
    }

    fun sortIndex(name: String): Int {
        val canonical = canonicalizeOrNull(name) ?: return schools.size + 1
        return schools.indexOf(canonical).takeIf { it >= 0 } ?: schools.size + 1
    }
}

data class SpellUsability(
    val usable: Boolean,
    val requiredPower: Int,
    val schools: List<String>,
    val qualifyingSchool: String? = null,
)

data class GearItem(
    val uid: String,
    val catalogId: String? = null,
    val name: String = "Предмет",
    val quantity: Int = 1,
    val load: Double = 0.0,
    val carried: Boolean = true,
    val description: String = "",
    val category: String = "Снаряжение",
    val section: String = "Предметы",
    val fields: Map<String, String> = emptyMap(),
    val custom: Boolean = false,
    internal val acquisitionLoadToken: com.furybook.dubl.acquisition.AcquisitionDoubleToken? = null,
) {
    // Derived lexical evidence is invisible to ordinary model equality and legacy serialization.
    override fun equals(other:Any?):Boolean = other is GearItem && uid==other.uid && catalogId==other.catalogId &&
        name==other.name && quantity==other.quantity && load.toBits()==other.load.toBits() && carried==other.carried &&
        description==other.description && category==other.category && section==other.section && fields==other.fields && custom==other.custom
    override fun hashCode():Int {
        var result=uid.hashCode()
        for(hash in listOf(catalogId?.hashCode() ?: 0,name.hashCode(),quantity,load.hashCode(),carried.hashCode(),description.hashCode(),category.hashCode(),section.hashCode(),fields.hashCode(),custom.hashCode())) result=31*result+hash
        return result
    }
}

data class CharacterGear(
    val loadAutomatic: Boolean = true,
    val loadManual: Double = 0.0,
    val items: List<GearItem> = emptyList(),
    internal val acquisitionLoadToken: com.furybook.dubl.acquisition.AcquisitionDoubleToken? = null,
) {
    override fun equals(other:Any?):Boolean=other is CharacterGear && loadAutomatic==other.loadAutomatic && loadManual.toBits()==other.loadManual.toBits() && items==other.items
    override fun hashCode():Int=(31*loadAutomatic.hashCode()+loadManual.hashCode())*31+items.hashCode()
}

data class SpellCatalogEntry(
    val id: String,
    val name: String,
    val school: String,
    val cost: Int,
    val manaText: String,
    val time: String,
    val range: String,
    val area: String,
    val action: String,
    val duration: String,
    val description: String,
    val enhancement: String,
    val incomplete: Boolean,
    val conflictNote: String,
)

data class GearCatalogEntry(
    val id: String,
    val name: String,
    val category: String,
    val section: String,
    val fields: Map<String, String>,
    val description: String,
)

data class MagicEquipmentCatalog(
    val version: String,
    val spells: List<SpellCatalogEntry>,
    val gear: List<GearCatalogEntry>,
)

data class BurdenState(
    val title: String,
    val penalty: Int,
)

object MagicEquipmentRules {
    const val BASE_MANA_ENTRY_ID = "feat_bf7b04eb68961725"
    const val INCREASED_MANA_ENTRY_ID = "feat_fd7bee8198e57574"
    const val MEDITATION_ENTRY_ID = "feat_f6f37c05cf7c5257"

    private val manaTable = arrayOf(
        intArrayOf(1, 2, 3, 4, 5),
        intArrayOf(2, 4, 6, 8, 10),
        intArrayOf(3, 6, 9, 12, 15),
        intArrayOf(4, 8, 12, 16, 20),
        intArrayOf(5, 10, 15, 20, 25),
        intArrayOf(6, 13, 19, 33, 45),
        intArrayOf(7, 16, 25, 46, 65),
        intArrayOf(8, 19, 31, 59, 85),
        intArrayOf(9, 22, 37, 72, 105),
        intArrayOf(10, 25, 43, 85, 125),
        intArrayOf(11, 30, 53, 107, 155),
        intArrayOf(12, 35, 63, 129, 185),
        intArrayOf(13, 40, 73, 151, 215),
        intArrayOf(14, 45, 83, 173, 245),
        intArrayOf(15, 50, 93, 195, 275),
        intArrayOf(16, 56, 106, 226, 320),
        intArrayOf(17, 62, 119, 257, 365),
        intArrayOf(18, 68, 132, 288, 410),
        intArrayOf(19, 74, 145, 319, 455),
        intArrayOf(20, 80, 160, 350, 500),
    )

    fun manaMaximum(character: DublCharacter): Int {
        val rank = character.magic.manaRank.coerceIn(0, 5)
        val power = highestMagicPower(character)
        if (rank <= 0 || power <= 0) return 0
        val basePower = power.coerceAtMost(20)
        var value = manaTable[basePower - 1][rank - 1]
        if (power > 20) {
            value += (power - 20) * intArrayOf(2, 7, 17, 40, 55)[rank - 1]
        }
        val increasedManaRank = character.development[INCREASED_MANA_ENTRY_ID]?.rank ?: 0
        return max(0, value + increasedManaRank * rank)
    }

    fun highestMagicPower(character: DublCharacter): Int {
        val schoolPower = character.magic.schools
            .filter { MagicSchoolCatalog.canonicalizeOrNull(it.name) != null }
            .maxOfOrNull { it.rank.coerceAtLeast(0) } ?: 0
        return if (schoolPower > 0) schoolPower else character.magic.power.coerceAtLeast(0)
    }

    fun magicSchoolRankXp(rank: Int): Int = rank.coerceAtLeast(0) * 25

    fun magicSchoolPowerXp(character: DublCharacter): Int = character.magic.schools
        .filter { MagicSchoolCatalog.canonicalizeOrNull(it.name) != null }
        .sumOf { magicSchoolRankXp(it.rank) }

    fun visibleMagicSchools(character: DublCharacter, hideUnlearned: Boolean): List<String> =
        if (!hideUnlearned) {
            MagicSchoolCatalog.schools
        } else {
            MagicSchoolCatalog.schools.filter { schoolPower(character, it) > 0 }
        }

    fun schoolPower(character: DublCharacter, schoolName: String): Int {
        val canonical = MagicSchoolCatalog.canonicalizeOrNull(schoolName) ?: return 0
        return character.magic.schools
            .filter { MagicSchoolCatalog.canonicalizeOrNull(it.name) == canonical }
            .maxOfOrNull { it.rank.coerceAtLeast(0) } ?: 0
    }

    fun spellUsability(character: DublCharacter, spell: KnownSpell): SpellUsability {
        val schools = MagicSchoolCatalog.parseSchools(spell.school)
        val required = spell.cost.coerceAtLeast(0)
        if (required == 0) return SpellUsability(true, 0, schools, schools.firstOrNull())
        val qualifying = schools.firstOrNull { schoolPower(character, it) >= required }
        return SpellUsability(
            usable = qualifying != null,
            requiredPower = required,
            schools = schools,
            qualifyingSchool = qualifying,
        )
    }

    fun spellUsability(character: DublCharacter, spell: SpellCatalogEntry): SpellUsability =
        spellUsability(
            character,
            KnownSpell(uid = spell.id, name = spell.name, school = spell.school, cost = spell.cost),
        )

    fun manaRecoveryPerRound(character: DublCharacter): Int =
        1 + (character.development[MEDITATION_ENTRY_ID]?.rank ?: 0)

    fun learnXpCost(manaCost: Int): Int? = when (manaCost) {
        in 0..3 -> 10
        in 4..8 -> 20
        in 9..12 -> 30
        in 13..16 -> 40
        in 17..20 -> 50
        else -> null
    }

    fun learnedSpellXp(character: DublCharacter): Int = character.magic.spells
        .filter { it.learned }
        .sumOf { spell ->
            if (spell.incomplete && spell.xpOverride == null) 0
            else spell.xpOverride ?: learnXpCost(spell.cost) ?: 0
        }

    fun manaRankXp(character: DublCharacter): Int = character.magic.manaRank.coerceIn(0, 5) * 100


    fun catalogGearLoad(entry: GearCatalogEntry): Double {
        val raw = entry.fields["Вес"]
            ?: entry.fields["Вес, кг"]
            ?: return 0.0
        val match = Regex("""\d+(?:[.,]\d+)?""").find(raw) ?: return 0.0
        return match.value.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }

    fun equipmentLoad(character: DublCharacter): Double {
        if (!character.gear.loadAutomatic) return character.gear.loadManual.coerceAtLeast(0.0)
        return character.gear.items
            .asSequence()
            .filter { it.carried }
            .sumOf { it.load.coerceAtLeast(0.0) * it.quantity.coerceAtLeast(1) }
    }

    fun equipmentCapacity(character: DublCharacter): Int {
        val base = (character.strength + character.constitution).coerceAtLeast(0)
        val haulerRank = character.developmentRank(DevelopmentEffectIds.HAULER)
        return (base * (100 + haulerRank * 10)) / 100
    }

    fun burden(character: DublCharacter): BurdenState {
        val load = equipmentLoad(character)
        val capacity = equipmentCapacity(character).toDouble()
        return when {
            load <= 0.0 || (capacity > 0.0 && load < capacity) -> BurdenState("Нет нагрузки", 0)
            capacity <= 0.0 -> BurdenState("Нагрузка при нулевой вместимости", -4)
            load <= capacity * 1.5 -> BurdenState("Лёгкая нагрузка", -1)
            load <= capacity * 2.0 -> BurdenState("Средняя нагрузка", -2)
            load <= capacity * 3.0 -> BurdenState("Тяжёлая нагрузка", -4)
            else -> BurdenState("Свыше таблицы нагрузки", -4)
        }
    }
}
