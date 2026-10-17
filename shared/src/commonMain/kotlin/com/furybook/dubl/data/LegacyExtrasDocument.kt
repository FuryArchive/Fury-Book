package com.furybook.dubl.data

import com.furybook.core.json.*
import com.furybook.dubl.model.*

/** Reading a legacy sheet does not require financial migration. Retain unrecognized UI data
 * alongside the known projection so ordinary edits cannot erase it.
 */
class LegacyExtrasDocument private constructor(private val root: JsonValue.Obj) {
    private val characters get() = root.values["characters"] as JsonValue.Obj

    fun load(characterId: String): CharacterSheetExtras =
        (characters.values[characterId] as? JsonValue.Obj)?.let(::project) ?: CharacterSheetExtras()

    fun encode(): String = jsonStringify(root)

    fun withExtras(characterId: String, extras: CharacterSheetExtras): LegacyExtrasDocument {
        requireSupportedVersion()
        require(characterId.isNotBlank())
        val original = characters.values[characterId] as? JsonValue.Obj
        val before = encodeExtras(original?.let(::project) ?: CharacterSheetExtras())
        val after = encodeExtras(extras)
        val merged = original?.values.orEmpty().toMutableMap()
        after.values.forEach { (field, value) ->
            // Even opaque values inside known fields survive until the player edits that field.
            if (original == null || before.values[field] != value) {
                merged[field] = preserveUnrecognizedValues(field, original?.values?.get(field), value)
            }
        }
        return withCharacters(characters.values + (characterId to JsonValue.Obj(merged)))
    }

    fun withoutCharacter(characterId: String): LegacyExtrasDocument {
        requireSupportedVersion()
        return withCharacters(characters.values - characterId)
    }

    /** Canonical financial migration must represent every value, independently of viewing. */
    fun forMigration(): Map<String, CharacterSheetExtras> {
        requireSupportedVersion()
        require(root.values.keys == setOf("version", "characters")) { "Unknown legacy extras fields" }
        return characters.values.mapValues { (id, value) ->
            require(id.isNotBlank()) { "Invalid extras character identity" }
            val row = value as? JsonValue.Obj ?: error("Expected character extras object")
            val unknown = row.values.keys - fields
            require(unknown.isEmpty()) { "Unrecognized sheet fields require migration review: ${unknown.joinToString()}" }
            row.values.forEach { (field, item) -> when (field) {
                "portraitUri" -> require(item is JsonValue.Str || item == JsonValue.Null) { "Invalid portrait URI" }
                "conditions" -> validateSet(item, CharacterConditionId.entries.map { it.name }.toSet())
                "hiddenResources" -> validateSet(item, CharacterSheetResourceId.entries.map { it.name }.toSet())
                "preferredAttributes" -> {
                    val values = item as? JsonValue.Obj ?: error("Expected preferred attributes object")
                    require(values.values.values.all { it is JsonValue.Str && AttributeId.entries.any { a -> a.name == it.value } }) { "Unknown preferred attribute" }
                }
                else -> require(item is JsonValue.Str) { "Expected encoded extras string: $field" }
            } }
            val projected = project(row)
            val encoded = encodeExtras(projected)
            for (field in encodedFields) {
                require(row.values[field]?.asString().orEmpty() == encoded.values[field]?.asString().orEmpty()) {
                    "Legacy extras $field would lose values"
                }
            }
            projected
        }
    }

    private fun requireSupportedVersion() {
        if (root.values["version"] != JsonValue.Num("1")) throw UnsupportedLegacyExtrasVersion()
    }

    private fun withCharacters(values: Map<String, JsonValue>) =
        LegacyExtrasDocument(JsonValue.Obj(root.values + ("characters" to JsonValue.Obj(values))))

    companion object {
        fun empty(): LegacyExtrasDocument = decode("{\"version\":1,\"characters\":{}}")

        fun decode(raw: String): LegacyExtrasDocument {
            val root = JsonReader(normalizeLegacyControls(raw), strict = true).read() as? JsonValue.Obj
                ?: error("Expected extras object")
            require(root.values["characters"] is JsonValue.Obj) { "Expected extras characters object" }
            return LegacyExtrasDocument(root)
        }

        private val fields = setOf("portraitUri", "conditions", "hiddenResources", "preferredAttributes",
            "skillGroups", "developmentGroups", "conditionOverrides", "customConditions", "notes", "noteEntries")
        private val encodedFields = setOf("skillGroups", "developmentGroups", "conditionOverrides", "customConditions", "noteEntries")

        private fun project(row: JsonValue.Obj): CharacterSheetExtras {
            fun text(field: String) = row.values[field]?.asString()
            fun strings(field: String) = (row.values[field] as? JsonValue.Arr)?.values.orEmpty().mapNotNull { it.asString() }
            return CharacterSheetExtras(
                portraitUri = text("portraitUri"),
                activeConditions = strings("conditions").mapNotNull { name -> CharacterConditionId.entries.firstOrNull { it.name == name } }.toSet(),
                hiddenResourceIds = strings("hiddenResources").mapNotNull { name -> CharacterSheetResourceId.entries.firstOrNull { it.name == name } }.toSet(),
                preferredSkillAttributes = (row.values["preferredAttributes"] as? JsonValue.Obj)?.values.orEmpty().mapNotNull { (id, value) ->
                    AttributeId.entries.firstOrNull { it.name == value.asString() }?.let { id to it }
                }.toMap(),
                skillGroups = SheetGroupingRules.decode(text("skillGroups")),
                developmentGroups = SheetGroupingRules.decode(text("developmentGroups")),
                conditionOverrides = ConditionLocalDataCodec.decodeOverrides(text("conditionOverrides")),
                customConditions = ConditionLocalDataCodec.decodeCustom(text("customConditions")),
                notes = text("notes").orEmpty(),
                noteEntries = CharacterNoteDataCodec.decode(text("noteEntries")),
            )
        }

        private fun encodeExtras(extras: CharacterSheetExtras) = jsonObject(
            "portraitUri" to (extras.portraitUri?.let(::jsonString) ?: JsonValue.Null),
            "conditions" to jsonArray(extras.activeConditions.map { jsonString(it.name) }),
            "hiddenResources" to jsonArray(extras.hiddenResourceIds.map { jsonString(it.name) }),
            "preferredAttributes" to JsonValue.Obj(extras.preferredSkillAttributes.mapValues { jsonString(it.value.name) }),
            "skillGroups" to jsonString(SheetGroupingRules.encode(extras.skillGroups)),
            "developmentGroups" to jsonString(SheetGroupingRules.encode(extras.developmentGroups)),
            "conditionOverrides" to jsonString(ConditionLocalDataCodec.encodeOverrides(extras.conditionOverrides)),
            "customConditions" to jsonString(ConditionLocalDataCodec.encodeCustom(extras.customConditions)),
            "notes" to jsonString(extras.notes),
            "noteEntries" to jsonString(CharacterNoteDataCodec.encode(extras.noteEntries)),
        )

        private fun preserveUnrecognizedValues(field: String, original: JsonValue?, edited: JsonValue): JsonValue {
            val known = when (field) {
                "conditions" -> CharacterConditionId.entries.map { it.name }.toSet()
                "hiddenResources" -> CharacterSheetResourceId.entries.map { it.name }.toSet()
                else -> null
            }
            if (known != null && original is JsonValue.Arr && edited is JsonValue.Arr) {
                return JsonValue.Arr(edited.values + original.values.filter { it.asString() !in known })
            }
            if (field == "preferredAttributes" && original is JsonValue.Obj && edited is JsonValue.Obj) {
                val unknown = original.values.filterValues { value -> AttributeId.entries.none { it.name == value.asString() } }
                return JsonValue.Obj(unknown + edited.values)
            }
            return edited
        }

        private fun validateSet(value: JsonValue, known: Set<String>) {
            val values = (value as? JsonValue.Arr)?.values ?: error("Expected extras array")
            require(values.all { it is JsonValue.Str && it.value in known }) { "Unknown extras set value" }
            require(values.distinct().size == values.size) { "Duplicate extras set value" }
        }

        /** Old Desktop writers put grouping separators directly inside quoted strings. */
        private fun normalizeLegacyControls(raw: String): String = buildString {
            var inString = false
            var escaped = false
            raw.forEach { ch ->
                if (inString && ch.code in 0..31) append("\\u").append(ch.code.toString(16).padStart(4, '0')) else append(ch)
                if (!escaped && ch == '"') inString = !inString
                escaped = inString && !escaped && ch == '\\'
            }
        }
    }
}

class UnsupportedLegacyExtrasVersion : IllegalArgumentException("Unsupported sheet-extras version")
