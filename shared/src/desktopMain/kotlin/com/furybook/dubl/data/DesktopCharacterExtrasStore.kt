package com.furybook.dubl.data

import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.ConditionLocalDataCodec
import com.furybook.dubl.model.CharacterNoteDataCodec
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.SheetGroup
import com.furybook.dubl.model.SheetGroupingRules
import com.furybook.core.json.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DesktopCharacterExtrasStore(
    private val file: Path = DesktopCharacterStore.defaultDataDirectory().resolve("sheet-extras.json"),
) : CharacterExtrasStore {
    private var cache: MutableMap<String, CharacterSheetExtras>? = null

    override fun load(characterId: String): CharacterSheetExtras = ensureLoaded()[characterId] ?: CharacterSheetExtras()

    override fun save(characterId: String, extras: CharacterSheetExtras) {
        val state = ensureLoaded()
        val next = state.toMutableMap().apply { put(characterId, extras) }
        persist(next)
        cache = next
    }

    override fun delete(characterId: String) {
        val state = ensureLoaded()
        if (characterId in state) {
            val next = state.toMutableMap().apply { remove(characterId) }
            persist(next)
            cache = next
        }
    }

    private fun ensureLoaded(): MutableMap<String, CharacterSheetExtras> {
        cache?.let { return it }
        val loaded = if (Files.notExists(file)) mutableMapOf() else runCatching {
            decode(Files.readString(file, StandardCharsets.UTF_8)).toMutableMap()
        }.getOrThrow()
        cache = loaded
        return loaded
    }

    private fun persist(state: Map<String, CharacterSheetExtras>) {
        Files.createDirectories(file.parent)
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temp, encode(state), StandardCharsets.UTF_8)
        runCatching {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encode(state: Map<String, CharacterSheetExtras>): String = buildString {
        append("{\"version\":1,\"characters\":{")
        state.entries.forEachIndexed { index, (id, extras) ->
            if (index > 0) append(',')
            append(quoted(id)).append(':').append('{')
            append("\"portraitUri\":").append(extras.portraitUri?.let(::quoted) ?: "null")
            append(",\"conditions\":").append(stringArray(extras.activeConditions.map { it.name }))
            append(",\"hiddenResources\":").append(stringArray(extras.hiddenResourceIds.map { it.name }))
            append(",\"preferredAttributes\":{")
            extras.preferredSkillAttributes.entries.forEachIndexed { i, entry ->
                if (i > 0) append(',')
                append(quoted(entry.key)).append(':').append(quoted(entry.value.name))
            }
            append('}')
            append(",\"skillGroups\":").append(quoted(SheetGroupingRules.encode(extras.skillGroups)))
            append(",\"developmentGroups\":").append(quoted(SheetGroupingRules.encode(extras.developmentGroups)))
            append(",\"conditionOverrides\":").append(quoted(ConditionLocalDataCodec.encodeOverrides(extras.conditionOverrides)))
            append(",\"customConditions\":").append(quoted(ConditionLocalDataCodec.encodeCustom(extras.customConditions)))
            append(",\"notes\":").append(quoted(extras.notes))
            append(",\"noteEntries\":").append(quoted(CharacterNoteDataCodec.encode(extras.noteEntries)))
            append('}')
        }
        append("}}")
    }

    private fun decode(raw: String): Map<String, CharacterSheetExtras> {
        // Unknown fields/enum values are refused rather than stripped during a migrated save.
        val normalized = normalizeLegacyControls(raw)
        val json = JsonReader(normalized, strict = true).read() as JsonValue.Obj
        require(json.values.keys == setOf("version", "characters")) { "Unknown legacy extras fields" }
        if (json.int("version") != 1) throw UnsupportedDesktopData("Unsupported sheet-extras version")
        val charactersJson = json.values["characters"] as? JsonValue.Obj ?: error("Expected extras characters object")
        val fields = setOf("portraitUri", "conditions", "hiddenResources", "preferredAttributes", "skillGroups", "developmentGroups", "conditionOverrides", "customConditions", "notes", "noteEntries")
        charactersJson.values.forEach { (id, value) ->
            require(id.isNotBlank())
            val row = value as? JsonValue.Obj ?: error("Expected character extras object")
            require(row.values.keys.all { it in fields }) { "Unknown character extras field" }
            row.values.forEach { (key, item) -> when (key) {
                "conditions" -> require((item as JsonValue.Arr).values.all { it is JsonValue.Str && CharacterConditionId.entries.any { e -> e.name == it.value } }) { "Unknown condition" }
                "hiddenResources" -> require((item as JsonValue.Arr).values.all { it is JsonValue.Str && CharacterSheetResourceId.entries.any { e -> e.name == it.value } }) { "Unknown resource" }
                "preferredAttributes" -> require((item as JsonValue.Obj).values.values.all { it is JsonValue.Str && AttributeId.entries.any { e -> e.name == it.value } }) { "Unknown attribute" }
                "portraitUri" -> require(item is JsonValue.Str || item == JsonValue.Null)
                else -> require(item is JsonValue.Str) { "Expected encoded extras string" }
            } }
        }
        val root = parseDesktopObject(normalized)
        val characters = root.objects["characters"] ?: return emptyMap()
        return characters.objects.mapValues { (_, objectValue) ->
            val extras = CharacterSheetExtras(
                portraitUri = objectValue.strings["portraitUri"],
                activeConditions = objectValue.arrays["conditions"].orEmpty().mapNotNull { name -> CharacterConditionId.entries.firstOrNull { it.name == name } }.toSet(),
                hiddenResourceIds = objectValue.arrays["hiddenResources"].orEmpty().mapNotNull { name -> CharacterSheetResourceId.entries.firstOrNull { it.name == name } }.toSet(),
                preferredSkillAttributes = objectValue.objects["preferredAttributes"]?.strings.orEmpty().mapNotNull { (skillId, attrName) ->
                    AttributeId.entries.firstOrNull { it.name == attrName }?.let { skillId to it }
                }.toMap(),
                skillGroups = SheetGroupingRules.decode(objectValue.strings["skillGroups"]),
                developmentGroups = SheetGroupingRules.decode(objectValue.strings["developmentGroups"]),
                conditionOverrides = ConditionLocalDataCodec.decodeOverrides(objectValue.strings["conditionOverrides"]),
                customConditions = ConditionLocalDataCodec.decodeCustom(objectValue.strings["customConditions"]),
                notes = objectValue.strings["notes"].orEmpty(),
                noteEntries = CharacterNoteDataCodec.decode(objectValue.strings["noteEntries"]),
            )
            mapOf(
                "skillGroups" to SheetGroupingRules.encode(extras.skillGroups),
                "developmentGroups" to SheetGroupingRules.encode(extras.developmentGroups),
                "conditionOverrides" to ConditionLocalDataCodec.encodeOverrides(extras.conditionOverrides),
                "customConditions" to ConditionLocalDataCodec.encodeCustom(extras.customConditions),
                "noteEntries" to CharacterNoteDataCodec.encode(extras.noteEntries),
            ).forEach { (field, encoded) ->
                require(objectValue.strings[field].orEmpty() == encoded) { "Legacy extras $field would lose values" }
            }
            extras
        }
    }

    private data class DesktopObject(
        val strings: MutableMap<String, String?> = linkedMapOf(),
        val arrays: MutableMap<String, List<String>> = linkedMapOf(),
        val objects: MutableMap<String, DesktopObject> = linkedMapOf(),
    )

    /** Older desktop writers emitted grouping separators literally inside strings. Normalize only
     * that established compatibility encoding for parsing; the original source is preserved verbatim.
     */
    private fun normalizeLegacyControls(raw: String): String = buildString {
        var inString = false
        var escaped = false
        raw.forEach { ch ->
            if (inString && ch.code in 0..31) append("\\u").append(ch.code.toString(16).padStart(4, '0')) else append(ch)
            if (!escaped && ch == '"') inString = !inString
            escaped = inString && !escaped && ch == '\\'
        }
    }

    private fun parseDesktopObject(raw: String): DesktopObject {
        fun convert(value: JsonValue.Obj): DesktopObject = DesktopObject().apply {
            value.values.forEach { (key, item) -> when (item) {
                is JsonValue.Str -> strings[key] = item.value
                JsonValue.Null -> strings[key] = null
                is JsonValue.Arr -> arrays[key] = item.values.map { (it as JsonValue.Str).value }
                is JsonValue.Obj -> objects[key] = convert(item)
                else -> Unit // Root version was validated above.
            } }
        }
        return convert(JsonReader(raw, strict = true).read() as JsonValue.Obj)
    }

    companion object {
        internal fun decodeLegacy(raw: String): Map<String, CharacterSheetExtras> =
            DesktopCharacterExtrasStore(Path.of("unused-extras-codec")).decode(raw)
    }

    private fun stringArray(values: Iterable<String>): String = values.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::quoted)
    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { c -> when (c) { '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (c.code < 32) append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c) } }
        append('"')
    }
}
