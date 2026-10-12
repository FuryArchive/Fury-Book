package com.furybook.dubl.data

import com.furybook.core.json.*
import com.furybook.dubl.model.DevelopmentCostType

/**
 * Validate the supported legacy shape before the historical compatibility decoder normalizes it.
 * Missing optional fields still use that schema's defaults; present malformed money/ownership
 * never becomes a zero or an absent holding. This does not reprice or change legacy normalization.
 */
internal fun validateLegacySnapshot(root: JsonValue.Obj, schema: Int) {
    require(schema in 1..SnapshotCodec.SCHEMA) { "Unsupported legacy schema" }
    val characters = root.rows("characters", required = true)
    require(characters.isNotEmpty()) { "Empty legacy snapshot" }
    uniqueIds(characters, "id", required = true)
    root.textIfPresent("activeCharacterId") // A missing/stale selection still selects the first row.
    characters.forEach { character ->
        character.ints("experience", "creationExperience", "xpAdjustment", "chiBonusRanks")
        character.integer("abilityPointsOverride", nullable = true)
        character.booleans("creationComplete", "manaEnabled", "chiEnabled")
        character.objectIfPresent("attributes")?.values?.forEach { (id, value) ->
            val attribute = value as? JsonValue.Obj ?: error("Invalid legacy attribute: $id")
            attribute.ints("base", "bonus")
        }
        val skills = character.rows("skills")
        uniqueIds(skills, "id", required = true)
        skills.forEach { skill ->
            skill.ints("rank", "modifier")
            skill.textIfPresent("definitionId", nullable = true)
        }
        val development = character.rows("development")
        uniqueIds(development, "id", required = true, trim = true)
        development.forEach { it.ints("rank", "option") }
        for (key in listOf("developmentOverrides", "customDevelopmentEntries")) {
            val entries = character.rows(key)
            uniqueIds(entries, "id", required = true, trim = true)
            entries.forEach { entry ->
                entry.ints("cost", "maxRank")
                entry.textIfPresent("costType")?.let { type ->
                    require(DevelopmentCostType.entries.any { it.name == type }) { "Invalid legacy costType" }
                }
                entry.booleans("incomplete", "repeatable", "perfectRoot")
                entry.rows("abilityOptions").forEach { it.ints("value") }
            }
        }
        character.objectIfPresent("magic")?.let { magic ->
            magic.ints("manaRank", "power")
            magic.rows("schools").forEach { school ->
                school.textIfPresent("name")
                school.ints("rank", "level") // The documented old level alias remains valid.
            }
            val spells = magic.rows("spells")
            uniqueIds(spells, "uid", required = false)
            spells.forEach { spell ->
                spell.ints("cost")
                spell.integer("xpOverride", nullable = true)
                spell.textIfPresent("catalogId", nullable = true)
                spell.booleans("learned", "custom", "incomplete")
            }
        }
        character.objectIfPresent("gear")?.let { gear ->
            gear.booleans("loadAutomatic")
            gear.finiteNumber("loadManual")
            val items = gear.rows("items")
            uniqueIds(items, "uid", required = false)
            items.forEach { item ->
                item.ints("quantity", "qty") // Keep the supported old quantity alias.
                item.finiteNumber("load")
                item.booleans("carried", "custom")
                item.textIfPresent("catalogId", nullable = true)
            }
        }
        val resources = character.rows("customResources")
        uniqueIds(resources, "uid", required = false)
        resources.forEach { it.ints("current", "maximum") }
    }
}

private fun JsonValue.Obj.rows(key: String, required: Boolean = false): List<JsonValue.Obj> {
    if (!has(key) && !required) return emptyList()
    val array = values[key] as? JsonValue.Arr ?: error("Invalid legacy array: $key")
    return array.values.map { it as? JsonValue.Obj ?: error("Invalid legacy row: $key") }
}
private fun JsonValue.Obj.objectIfPresent(key: String): JsonValue.Obj? =
    if (!has(key)) null else values[key] as? JsonValue.Obj ?: error("Invalid legacy object: $key")

private fun JsonValue.Obj.textIfPresent(key: String, nullable: Boolean = false): String? {
    if (!has(key) || (nullable && values[key] == JsonValue.Null)) return null
    return (values[key] as? JsonValue.Str)?.value ?: error("Invalid legacy text: $key")
}
private fun uniqueIds(rows: List<JsonValue.Obj>, key: String, required: Boolean, trim: Boolean = false) {
    val ids = rows.mapNotNull { row ->
        val id = row.textIfPresent(key, nullable = !required)?.let { if (trim) it.trim() else it }
        if (required) require(!id.isNullOrBlank()) { "Missing legacy identity: $key" }
        // Historical nested UID generation (including blank UIDs) is supported, never primary IDs.
        id?.takeIf(String::isNotBlank)
    }
    require(ids.distinct().size == ids.size) { "Duplicate legacy identity: $key" }
}
private fun JsonValue.Obj.ints(vararg keys: String) = keys.forEach { integer(it) }
private fun JsonValue.Obj.integer(key: String, nullable: Boolean = false) {
    if (!has(key) || (nullable && values[key] == JsonValue.Null)) return
    val valid = when (val value = values[key]) {
        is JsonValue.Str -> value.value.toIntOrNull() != null // Existing numeric-string compatibility.
        is JsonValue.Num -> exactInteger(value.raw) != null
        else -> false
    }
    require(valid) { "Invalid legacy integer: $key" }
}
/** Decimal/exponent spellings are allowed only if mathematically integral and representable.
 * Never round a fractional monetary/rank token through Double, including underflow to zero. */
private fun exactInteger(raw: String): Int? {
    val match = Regex("(-?)([0-9]+)(?:\\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?").matchEntire(raw) ?: return null
    val fraction = match.groupValues[3]
    val digits = (match.groupValues[2] + fraction).trimStart('0')
    if (digits.isEmpty()) return 0
    val exponent = match.groupValues[4].let { if (it.isEmpty()) 0 else it.toIntOrNull() ?: return null }
    val shift = exponent.toLong() - fraction.length
    val integer = if (shift >= 0) {
        if (digits.length + shift > 10) return null
        digits + "0".repeat(shift.toInt())
    } else {
        if (-shift >= digits.length) return null
        val split = digits.length + shift.toInt()
        if (digits.substring(split).any { it != '0' }) return null
        digits.substring(0, split)
    }
    return (match.groupValues[1] + integer).toIntOrNull()
}
private fun JsonValue.Obj.booleans(vararg keys: String) = keys.forEach { key ->
    if (has(key)) require(when (val value = values[key]) {
        is JsonValue.Bool -> true
        is JsonValue.Str -> value.value.toBooleanStrictOrNull() != null
        else -> false
    }) { "Invalid legacy boolean: $key" }
}
private fun JsonValue.Obj.finiteNumber(key: String) {
    if (!has(key)) return
    val number = when (val value = values[key]) {
        is JsonValue.Num -> value.raw.toDoubleOrNull()
        is JsonValue.Str -> value.value.toDoubleOrNull()
        else -> null
    }
    require(number != null && number.isFinite()) { "Invalid legacy number: $key" }
}
