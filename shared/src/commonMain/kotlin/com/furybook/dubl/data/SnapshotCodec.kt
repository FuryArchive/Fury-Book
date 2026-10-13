package com.furybook.dubl.data

import com.furybook.core.json.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.DevelopmentCostType
import com.furybook.dubl.model.AbilityOption
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.AttributeValue
import com.furybook.dubl.model.CharacterGear
import com.furybook.dubl.model.CharacterMagic
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.CustomResource
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.DublRuleset
import com.furybook.dubl.model.RulesetRef
import com.furybook.dubl.model.GearItem
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicSchool
import com.furybook.dubl.model.OwnedDevelopment
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.model.defaultAttributes

object SnapshotCodec {
    const val SCHEMA = 11

    fun fresh(idFactory: () -> String): AppSnapshot {
        val character = DublCharacter(id = idFactory(), name = "Новый персонаж")
        return AppSnapshot(listOf(character), character.id)
    }

    fun encode(snapshot: AppSnapshot): String = jsonStringify(
        jsonObject(
            "schema" to jsonNumber(SCHEMA),
            "activeCharacterId" to jsonString(snapshot.activeCharacterId),
            "characters" to jsonArray(snapshot.characters.map { encodeCharacter(it) }),
        ),
    )

    /** Acquisition-owned strict projection; the ordinary legacy encoder remains unchanged. */
    internal fun encodeAcquisitionProjection(character:DublCharacter):JsonValue.Obj = jsonObject(
        "schema" to jsonNumber(SCHEMA), "activeCharacterId" to jsonString(character.id),
        "characters" to jsonArray(listOf(encodeCharacter(character, acquisition=true))),
    )

    fun decode(raw: String, idFactory: () -> String): AppSnapshot {
        val root = parseRoot(raw)
        require(!root.has("formatVersion") && !root.has("format")) { "Versioned envelopes require explicit format routing" }
        val schema = root.int("schema", 1)
        require(schema in 1..SCHEMA) { "Unsupported snapshot schema" }
        val characters = root.array("characters").mapNotNull { item ->
            item.asObject()?.let { decodeCharacter(it, schema, idFactory) }
        }
        if (characters.isEmpty()) return fresh(idFactory)
        val requested = root.string("activeCharacterId")
        val active = characters.firstOrNull { it.id == requested }?.id ?: characters.first().id
        return AppSnapshot(characters, active)
    }

    /** Envelope-only projection parser. It preserves manual values and never supplies a fresh
     * character. The envelope codec strictly round-trips this projection to reject omitted,
     * malformed or unknown fields. Public legacy decoding retains its historical normalization.
     */
    internal fun decodeEnvelopeProjection(root: JsonValue.Obj): DublCharacter {
        require(root.int("schema", -1) == SCHEMA) { "Unsupported character projection" }
        val character = root.array("characters").single().asObject()
            ?: throw IllegalArgumentException("Expected character projection")
        return decodeCharacter(character, SCHEMA, { error("Missing stored identity") }, preserveValues = true)
    }

    private fun encodeCharacter(character: DublCharacter, acquisition:Boolean=false): JsonValue.Obj = jsonObject(
        "id" to jsonString(character.id),
        "ruleset" to jsonObject(
            "id" to jsonString(character.ruleset.id),
            "version" to jsonString(character.ruleset.version),
        ),
        "name" to jsonString(character.name),
        "concept" to jsonString(character.concept),
        "experience" to jsonNumber(character.experience),
        "creationExperience" to jsonNumber(character.creationExperience),
        "creationComplete" to jsonBoolean(character.creationComplete),
        "xpAdjustment" to jsonNumber(character.xpAdjustment),
        "abilityPointsOverride" to character.abilityPointsOverride?.let(::jsonNumber),
        "size" to jsonNumber(character.size),
        "legs" to jsonNumber(character.legs),
        "hpCurrent" to jsonNumber(character.hpCurrent),
        "enduranceCurrent" to jsonNumber(character.enduranceCurrent),
        "manaEnabled" to jsonBoolean(character.manaEnabled),
        "manaCurrent" to jsonNumber(character.manaCurrent),
        "manaMaximum" to jsonNumber(character.manaMaximum),
        "chiEnabled" to jsonBoolean(character.chiEnabled),
        "chiCurrent" to jsonNumber(character.chiCurrent),
        "chiBonusRanks" to jsonNumber(character.chiBonusRanks),
        "healthMaximumOverride" to character.healthMaximumOverride?.let(::jsonNumber),
        "enduranceMaximumOverride" to character.enduranceMaximumOverride?.let(::jsonNumber),
        "manaMaximumOverride" to character.manaMaximumOverride?.let(::jsonNumber),
        "customResources" to jsonArray(character.customResources.map { resource ->
            jsonObject(
                "uid" to jsonString(resource.uid),
                "name" to jsonString(resource.name),
                "current" to jsonNumber(resource.current),
                "maximum" to jsonNumber(resource.maximum),
            )
        }),
        "attributes" to JsonValue.Obj(linkedMapOf<String, JsonValue>().apply {
            character.attributes.forEach { (id, value) ->
                put(id.name, jsonObject("base" to jsonNumber(value.base), "bonus" to jsonNumber(value.bonus)))
            }
        }),
        "skills" to jsonArray(character.skills.values.map(::encodeSkill)),
        "hiddenSkillIds" to jsonArray(character.hiddenSkillIds.map(::jsonString)),
        "disabledSkillEffectIds" to jsonArray(character.disabledSkillEffectIds.map(::jsonString)),
        "development" to jsonArray(character.development.map { (id, owned) ->
            jsonObject(
                "id" to jsonString(id),
                "rank" to jsonNumber(owned.rank),
                "option" to jsonNumber(owned.optionIndex),
            )
        }),
        "developmentOverrides" to jsonArray(character.developmentOverrides.values.map(::encodeDevelopmentEntry)),
        "customDevelopmentEntries" to jsonArray(character.customDevelopmentEntries.map(::encodeDevelopmentEntry)),
        "magic" to encodeMagic(character.magic),
        "gear" to encodeGear(character.gear,acquisition),
    )

    private fun encodeSkill(skill: CharacterSkill): JsonValue.Obj = jsonObject(
        "id" to jsonString(skill.id),
        "definitionId" to skill.definitionId?.let(::jsonString),
        "name" to jsonString(skill.name),
        "description" to jsonString(skill.description),
        "rank" to jsonNumber(skill.rank),
        "attributes" to jsonArray(skill.attributes.map { jsonString(it.name) }),
        "modifier" to jsonNumber(skill.modifier),
        "formulaNote" to jsonString(skill.formulaNote),
        "categoryOverride" to skill.categoryOverride?.name?.let(::jsonString),
        "untrainedOverride" to skill.untrainedOverride?.name?.let(::jsonString),
        "auto6Override" to skill.auto6Override?.let(::jsonString),
        "auto12Override" to skill.auto12Override?.let(::jsonString),
    )

    private fun encodeDevelopmentEntry(entry: DevelopmentEntry): JsonValue.Obj = jsonObject(
        "id" to jsonString(entry.id),
        "name" to jsonString(entry.name),
        "section" to jsonString(entry.section),
        "category" to jsonString(entry.category),
        "cost" to jsonNumber(entry.cost),
        "costType" to jsonString(entry.costType.name),
        "maxRank" to jsonNumber(entry.maxRank),
        "requirements" to jsonString(entry.requirements),
        "benefit" to jsonString(entry.benefit),
        "notes" to jsonString(entry.notes),
        "tags" to jsonArray(entry.tags.map(::jsonString)),
        "accessId" to entry.accessId?.let(::jsonString),
        "abilityOptions" to jsonArray(entry.abilityOptions.map { option ->
            jsonObject("source" to jsonString(option.source), "value" to jsonNumber(option.value))
        }),
        "incomplete" to jsonBoolean(entry.incomplete),
        "repeatable" to jsonBoolean(entry.repeatable),
        "perfectRoot" to jsonBoolean(entry.perfectRoot),
        "mechanicsConflict" to jsonString(entry.mechanicsConflict),
        "conflictNote" to jsonString(entry.conflictNote),
    )

    private fun encodeMagic(magic: CharacterMagic): JsonValue.Obj = jsonObject(
        "manaRank" to jsonNumber(magic.manaRank),
        "power" to jsonNumber(magic.power),
        "schools" to jsonArray(magic.schools.map { school ->
            jsonObject(
                "name" to jsonString(school.name),
                "rank" to jsonNumber(school.rank),
                "note" to jsonString(school.note),
            )
        }),
        "spells" to jsonArray(magic.spells.map { spell ->
            jsonObject(
                "uid" to jsonString(spell.uid),
                "catalogId" to spell.catalogId?.let(::jsonString),
                "name" to jsonString(spell.name),
                "school" to jsonString(spell.school),
                "cost" to jsonNumber(spell.cost),
                "manaText" to jsonString(spell.manaText),
                "time" to jsonString(spell.time),
                "range" to jsonString(spell.range),
                "area" to jsonString(spell.area),
                "action" to jsonString(spell.action),
                "duration" to jsonString(spell.duration),
                "description" to jsonString(spell.description),
                "enhancement" to jsonString(spell.enhancement),
                "learned" to jsonBoolean(spell.learned),
                "xpOverride" to spell.xpOverride?.let(::jsonNumber),
                "incomplete" to jsonBoolean(spell.incomplete),
                "conflictNote" to jsonString(spell.conflictNote),
                "custom" to jsonBoolean(spell.custom),
            )
        }),
    )

    private fun encodeGear(gear: CharacterGear, acquisition:Boolean=false): JsonValue.Obj = jsonObject(
        "loadAutomatic" to jsonBoolean(gear.loadAutomatic),
        "loadManual" to if(acquisition) JsonValue.Num(acquisitionDoubleText(gear.loadManual,gear.acquisitionLoadToken,AcquisitionDoubleLeaf.ManualLoad)) else jsonNumber(gear.loadManual),
        "items" to jsonArray(gear.items.map { item ->
            jsonObject(
                "uid" to jsonString(item.uid),
                "catalogId" to item.catalogId?.let(::jsonString),
                "name" to jsonString(item.name),
                "quantity" to jsonNumber(item.quantity),
                "load" to if(acquisition) JsonValue.Num(acquisitionDoubleText(item.load,item.acquisitionLoadToken,AcquisitionDoubleLeaf.ItemLoad,item.uid)) else jsonNumber(item.load),
                "carried" to jsonBoolean(item.carried),
                "description" to jsonString(item.description),
                "category" to jsonString(item.category),
                "section" to jsonString(item.section),
                "custom" to jsonBoolean(item.custom),
                "fields" to JsonValue.Obj(item.fields.mapValues { jsonString(it.value) }),
            )
        }),
    )

    private fun decodeCharacter(root: JsonValue.Obj, schema: Int, idFactory: () -> String, preserveValues: Boolean = false): DublCharacter {
        val attributes = defaultAttributes().toMutableMap()
        val jsonAttributes = root.objectValue("attributes")
        AttributeId.entries.forEach { id ->
            jsonAttributes?.objectValue(id.name)?.let { value ->
                attributes[id] = AttributeValue(value.int("base", 0), value.int("bonus", 0))
            }
        }

        val skills = linkedMapOf<String, CharacterSkill>()
        root.array("skills").forEach { item ->
            item.asObject()?.let { decodeSkill(it, preserveValues) }?.let { skills[it.id] = it }
        }
        val hidden = root.array("hiddenSkillIds").mapNotNull { it.asString()?.takeIf(String::isNotBlank) }.toCollection(linkedSetOf())
        val disabledSkillEffectIds = root.array("disabledSkillEffectIds")
            .mapNotNull { it.asString()?.trim()?.takeIf(String::isNotBlank) }
            .toCollection(linkedSetOf())
        val development = linkedMapOf<String, OwnedDevelopment>()
        root.array("development").forEach { value ->
            val item = value.asObject() ?: return@forEach
            val id = item.string("id").trim()
            val rank = item.int("rank", 0)
            if (id.isNotBlank() && (preserveValues || rank > 0)) development[id] = OwnedDevelopment(rank, item.int("option", 0).let { if (preserveValues) it else it.coerceAtLeast(0) })
        }
        val developmentOverrides = root.array("developmentOverrides").mapNotNull { value ->
            value.asObject()?.let { decodeDevelopmentEntry(it, preserveValues) }
        }.associateBy { it.id }
        val customDevelopmentEntries = root.array("customDevelopmentEntries").mapNotNull { value ->
            value.asObject()?.let { decodeDevelopmentEntry(it, preserveValues) }
        }.distinctBy { it.id }
        val customResources = root.array("customResources").mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            CustomResource(
                uid = item.string("uid").ifBlank(idFactory),
                name = item.string("name", "Ресурс"),
                current = item.int("current", 0),
                maximum = item.int("maximum", 0),
            )
        }
        val experience = root.int("experience", 0).let { if (preserveValues) it else it.coerceAtLeast(0) }
        val creationExperience = if (root.has("creationExperience")) root.int("creationExperience", experience).let { if (preserveValues) it else it.coerceAtLeast(0) } else experience
        val creationComplete = if (root.has("creationComplete")) root.bool("creationComplete", false) else schema < 5

        val rulesetRoot = root.objectValue("ruleset")
        val ruleset = if (rulesetRoot == null) {
            DublRuleset.reference
        } else {
            RulesetRef(
                id = rulesetRoot.string("id", DublRuleset.ID).ifBlank { DublRuleset.ID },
                version = rulesetRoot.string("version", DublRuleset.VERSION).ifBlank { DublRuleset.VERSION },
            )
        }

        return DublCharacter(
            id = root.string("id").ifBlank(idFactory),
            ruleset = ruleset,
            name = root.string("name", "Новый персонаж"),
            concept = root.string("concept"),
            experience = experience,
            creationExperience = creationExperience,
            creationComplete = creationComplete,
            xpAdjustment = root.int("xpAdjustment", 0),
            abilityPointsOverride = root.intOrNull("abilityPointsOverride")?.let { if (preserveValues) it else it.coerceAtLeast(0) },
            size = root.int("size", 5),
            legs = root.int("legs", 2),
            attributes = attributes,
            hpCurrent = root.int("hpCurrent", 0),
            enduranceCurrent = root.int("enduranceCurrent", 3),
            manaEnabled = root.bool("manaEnabled", false),
            manaCurrent = root.int("manaCurrent", 0),
            manaMaximum = root.int("manaMaximum", 0),
            chiEnabled = root.bool("chiEnabled", false),
            chiCurrent = root.int("chiCurrent", 0),
            chiBonusRanks = root.int("chiBonusRanks", 0),
            healthMaximumOverride = root.intOrNull("healthMaximumOverride"),
            enduranceMaximumOverride = root.intOrNull("enduranceMaximumOverride"),
            manaMaximumOverride = root.intOrNull("manaMaximumOverride"),
            customResources = customResources,
            skills = skills,
            hiddenSkillIds = hidden,
            disabledSkillEffectIds = disabledSkillEffectIds,
            development = development,
            developmentOverrides = developmentOverrides,
            customDevelopmentEntries = customDevelopmentEntries,
            magic = decodeMagic(root.objectValue("magic"), idFactory),
            gear = decodeGear(root.objectValue("gear"), idFactory, preserveValues),
        ).let { if (preserveValues) it else it.normalized() }
    }

    private fun decodeDevelopmentEntry(root: JsonValue.Obj, preserveValues: Boolean): DevelopmentEntry? {
        val id = root.string("id").trim().takeIf(String::isNotBlank) ?: return null
        val costType = DevelopmentCostType.entries.firstOrNull { it.name == root.string("costType") } ?: DevelopmentCostType.XP
        val options = root.array("abilityOptions").mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            AbilityOption(source = item.string("source"), value = item.int("value", 0).let { if (preserveValues) it else it.coerceAtLeast(0) })
        }
        return DevelopmentEntry(
            id = id,
            name = root.string("name", "Без названия"),
            section = root.string("section"),
            category = root.string("category"),
            cost = root.int("cost", 0).let { if (preserveValues) it else it.coerceAtLeast(0) },
            costType = costType,
            maxRank = root.int("maxRank", 1).let { if (preserveValues) it else it.coerceAtLeast(1) },
            requirements = root.string("requirements"),
            benefit = root.string("benefit"),
            notes = root.string("notes"),
            tags = root.array("tags").mapNotNull { it.asString() },
            accessId = root.string("accessId").trim().takeIf(String::isNotBlank),
            abilityOptions = options,
            incomplete = root.bool("incomplete", false),
            repeatable = root.bool("repeatable", false),
            perfectRoot = root.bool("perfectRoot", false),
            mechanicsConflict = root.string("mechanicsConflict"),
            conflictNote = root.string("conflictNote"),
        )
    }

    private fun decodeSkill(root: JsonValue.Obj, preserveValues: Boolean): CharacterSkill? {
        val id = root.string("id").ifBlank { return null }
        val attrs = root.array("attributes").mapNotNull { value ->
            val raw = value.asString() ?: return@mapNotNull null
            AttributeId.entries.firstOrNull { it.name == raw }
        }.let { if (preserveValues) it else it.distinct() }
        val categoryOverride = root.string("categoryOverride").takeIf(String::isNotBlank)?.let { raw ->
            com.furybook.dubl.model.SkillCategory.entries.firstOrNull { it.name == raw }
        }
        val untrained = root.string("untrainedOverride").takeIf(String::isNotBlank)?.let { raw ->
            UntrainedRule.entries.firstOrNull { it.name == raw }
        }
        return CharacterSkill(
            id = id,
            definitionId = root.string("definitionId").takeIf(String::isNotBlank),
            name = root.string("name"),
            description = root.string("description"),
            rank = root.int("rank", 0),
            attributes = attrs,
            modifier = root.int("modifier", 0),
            formulaNote = root.string("formulaNote"),
            categoryOverride = categoryOverride,
            untrainedOverride = untrained,
            auto6Override = root.string("auto6Override").takeIf(String::isNotBlank),
            auto12Override = root.string("auto12Override").takeIf(String::isNotBlank),
        )
    }

    private fun decodeMagic(root: JsonValue.Obj?, idFactory: () -> String): CharacterMagic {
        if (root == null) return CharacterMagic()
        val schools = root.array("schools").mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            MagicSchool(item.string("name", "Школа"), item.int("rank", item.int("level", 0)), item.string("note"))
        }
        val spells = root.array("spells").mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            KnownSpell(
                uid = item.string("uid").ifBlank(idFactory),
                catalogId = item.string("catalogId").takeIf(String::isNotBlank),
                name = item.string("name", "Заклинание"),
                school = item.string("school"),
                cost = item.int("cost", 0),
                manaText = item.string("manaText"),
                time = item.string("time"),
                range = item.string("range"),
                area = item.string("area"),
                action = item.string("action"),
                duration = item.string("duration"),
                description = item.string("description"),
                enhancement = item.string("enhancement"),
                learned = item.bool("learned", true),
                xpOverride = item.intOrNull("xpOverride"),
                incomplete = item.bool("incomplete", false),
                conflictNote = item.string("conflictNote"),
                custom = item.bool("custom", false),
            )
        }
        return CharacterMagic(root.int("manaRank", 0), root.int("power", 0), schools, spells)
    }

    private fun decodeGear(root: JsonValue.Obj?, idFactory: () -> String, acquisition:Boolean=false): CharacterGear {
        if (root == null) return CharacterGear()
        val items = root.array("items").mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            GearItem(
                uid = item.string("uid").ifBlank(idFactory),
                catalogId = item.string("catalogId").takeIf(String::isNotBlank),
                name = item.string("name", "Предмет"),
                quantity = item.int("quantity", item.int("qty", 1)),
                load = item.double("load", 0.0),
                carried = item.bool("carried", true),
                description = item.string("description"),
                category = item.string("category", "Снаряжение"),
                section = item.string("section", "Предметы"),
                fields = item.stringMap("fields"),
                custom = item.bool("custom", false),
                acquisitionLoadToken = if(acquisition) AcquisitionDoubleToken.capture((item.values.getValue("load") as JsonValue.Num).raw,item.double("load",0.0),AcquisitionDoubleLeaf.ItemLoad,item.string("uid")) else null,
            )
        }
        val manual=root.double("loadManual",0.0)
        return CharacterGear(root.bool("loadAutomatic",true),manual,items,
            if(acquisition) AcquisitionDoubleToken.capture((root.values.getValue("loadManual") as JsonValue.Num).raw,manual,AcquisitionDoubleLeaf.ManualLoad) else null)
    }
}

private fun JsonValue.Obj.intOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val value = values[key]) {
        is JsonValue.Num -> value.raw.toDoubleOrNull()?.toInt()
        is JsonValue.Str -> value.value.toIntOrNull()
        else -> null
    }
}
