package com.furybook.web

import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterNoteDataCodec
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.ConditionLocalDataCodec
import com.furybook.dubl.model.SheetGroupingRules

private object BrowserSessionMemory {
    var snapshotRaw: String? = null
    var extrasRaw: String = "{\"version\":1,\"characters\":{}}"
    var chiEnabled: Boolean = false

    fun clear() {
        snapshotRaw = null
        extrasRaw = "{\"version\":1,\"characters\":{}}"
        chiEnabled = false
    }
}

fun clearBrowserSessionData() = BrowserSessionMemory.clear()

fun webUuid(): String = js("crypto.randomUUID()") as String

class BrowserCharacterStore : CharacterStore {
    override fun load() = BrowserSessionMemory.snapshotRaw
        ?.let { raw -> runCatching { SnapshotCodec.decode(raw, ::webUuid) }.getOrNull() }
        ?: SnapshotCodec.fresh(::webUuid)

    override fun save(snapshot: com.furybook.dubl.model.AppSnapshot) {
        BrowserSessionMemory.snapshotRaw = SnapshotCodec.encode(snapshot)
    }

    fun hasStoredState(): Boolean = BrowserSessionMemory.snapshotRaw != null

    fun raw(): String = BrowserSessionMemory.snapshotRaw
        ?: SnapshotCodec.encode(SnapshotCodec.fresh(::webUuid)).also { BrowserSessionMemory.snapshotRaw = it }

    fun replaceRaw(raw: String) {
        SnapshotCodec.decode(raw, ::webUuid)
        BrowserSessionMemory.snapshotRaw = raw
    }
}

class BrowserCharacterExtrasStore : CharacterExtrasStore {
    private var state: MutableMap<String, CharacterSheetExtras> = decode(BrowserSessionMemory.extrasRaw).toMutableMap()

    override fun load(characterId: String): CharacterSheetExtras = state[characterId] ?: CharacterSheetExtras()

    override fun save(characterId: String, extras: CharacterSheetExtras) {
        state[characterId] = extras
        persist()
    }

    override fun delete(characterId: String) {
        if (state.remove(characterId) != null) persist()
    }

    fun raw(): String = encode(state)

    fun replaceRaw(raw: String) {
        state = decode(raw).toMutableMap()
        BrowserSessionMemory.extrasRaw = encode(state)
    }

    private fun persist() {
        BrowserSessionMemory.extrasRaw = encode(state)
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
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val root = parseObject(raw)
            val characters = root.objects["characters"] ?: return@runCatching emptyMap()
            characters.objects.mapValues { (_, value) ->
                CharacterSheetExtras(
                    portraitUri = value.strings["portraitUri"],
                    activeConditions = value.arrays["conditions"].orEmpty().mapNotNull { n -> CharacterConditionId.entries.firstOrNull { it.name == n } }.toSet(),
                    hiddenResourceIds = value.arrays["hiddenResources"].orEmpty().mapNotNull { n -> CharacterSheetResourceId.entries.firstOrNull { it.name == n } }.toSet(),
                    preferredSkillAttributes = value.objects["preferredAttributes"]?.strings.orEmpty().mapNotNull { (skillId, attr) ->
                        AttributeId.entries.firstOrNull { it.name == attr }?.let { skillId to it }
                    }.toMap(),
                    skillGroups = SheetGroupingRules.decode(value.strings["skillGroups"]),
                    developmentGroups = SheetGroupingRules.decode(value.strings["developmentGroups"]),
                    conditionOverrides = ConditionLocalDataCodec.decodeOverrides(value.strings["conditionOverrides"]),
                    customConditions = ConditionLocalDataCodec.decodeCustom(value.strings["customConditions"]),
                    notes = value.strings["notes"].orEmpty(),
                    noteEntries = CharacterNoteDataCodec.decode(value.strings["noteEntries"]),
                )
            }
        }.getOrDefault(emptyMap())
    }

    private data class Obj(
        val strings: MutableMap<String, String?> = linkedMapOf(),
        val arrays: MutableMap<String, List<String>> = linkedMapOf(),
        val objects: MutableMap<String, Obj> = linkedMapOf(),
    )

    private fun parseObject(raw: String): Obj {
        var i = 0
        fun skip() { while (i < raw.length && raw[i].isWhitespace()) i++ }
        fun readString(): String {
            require(raw[i++] == '"')
            val out = StringBuilder()
            while (i < raw.length) {
                val c = raw[i++]
                if (c == '"') return out.toString()
                if (c == '\\') {
                    val e = raw[i++]
                    out.append(when (e) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> e })
                } else out.append(c)
            }
            error("unterminated string")
        }
        fun parseArray(): List<String> {
            require(raw[i++] == '['); skip()
            val values = mutableListOf<String>()
            if (raw.getOrNull(i) == ']') { i++; return values }
            while (true) {
                skip(); values += readString(); skip()
                when (raw[i++]) { ']' -> return values; ',' -> Unit; else -> error("array") }
            }
        }
        lateinit var parse: () -> Obj
        parse = {
            skip(); require(raw[i++] == '{'); skip()
            val result = Obj()
            if (raw.getOrNull(i) == '}') { i++; result } else {
                while (true) {
                    skip(); val key = readString(); skip(); require(raw[i++] == ':'); skip()
                    when (raw.getOrNull(i)) {
                        '"' -> result.strings[key] = readString()
                        '[' -> result.arrays[key] = parseArray()
                        '{' -> result.objects[key] = parse()
                        'n' -> { require(raw.substring(i, (i + 4).coerceAtMost(raw.length)) == "null"); i += 4; result.strings[key] = null }
                        else -> { while (i < raw.length && raw[i] !in charArrayOf(',', '}')) i++ }
                    }
                    skip(); when (raw[i++]) { '}' -> break; ',' -> Unit; else -> error("object") }
                }
                result
            }
        }
        return parse()
    }

    private fun stringArray(values: Iterable<String>): String = values.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::quoted)
    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '\\' -> append("\\\\")
                c == '"' -> append("\\\"")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> append(c)
            }
        }
        append('"')
    }
}

object BrowserPackState {
    var chiEnabled: Boolean
        get() = BrowserSessionMemory.chiEnabled
        set(value) { BrowserSessionMemory.chiEnabled = value }
}
