package com.furybook.core.json

/**
 * Tiny platform-neutral JSON reader used by Fury Book core formats and ruleset payloads.
 *
 * It deliberately supports only JSON primitives/arrays/objects and has no platform
 * dependencies, keeping manifest/catalog parsing available to Android, Desktop,
 * tests, and any future frontend without introducing a second interpretation layer.
 */
internal sealed interface JsonValue {
    data class Obj(val values: Map<String, JsonValue>) : JsonValue
    data class Arr(val values: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

internal class JsonReader(private val source: String, private val strict: Boolean = false) {
    private var depth = 0
    private var index = 0

    fun read(): JsonValue {
        val value = readValue()
        skipWhitespace()
        require(index == source.length) { "Unexpected trailing JSON at $index" }
        return value
    }

    private fun readValue(): JsonValue {
        skipWhitespace()
        require(index < source.length) { "Unexpected end of JSON" }
        if (strict) require(++depth <= 128) { "JSON nesting limit exceeded" }
        val value = when (source[index]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> JsonValue.Str(readString())
            't' -> { expect("true"); JsonValue.Bool(true) }
            'f' -> { expect("false"); JsonValue.Bool(false) }
            'n' -> { expect("null"); JsonValue.Null }
            '-', in '0'..'9' -> readNumber()
            else -> error("Unexpected JSON character '${source[index]}' at $index")
        }
        if (strict) depth--
        return value
    }

    private fun readObject(): JsonValue.Obj {
        require(source[index++] == '{')
        skipWhitespace()
        if (peek('}')) {
            index++
            return JsonValue.Obj(emptyMap())
        }
        val values = linkedMapOf<String, JsonValue>()
        while (true) {
            skipWhitespace()
            require(source.getOrNull(index) == '"') { "Expected object key at $index" }
            val key = readString()
            skipWhitespace()
            require(source.getOrNull(index) == ':') { "Expected ':' after '$key'" }
            index++
            if (strict) require(key !in values) { "Duplicate JSON key: $key" }
            values[key] = readValue()
            skipWhitespace()
            when (source.getOrNull(index)) {
                ',' -> index++
                '}' -> { index++; return JsonValue.Obj(values) }
                else -> error("Expected ',' or '}' at $index")
            }
        }
    }

    private fun readArray(): JsonValue.Arr {
        require(source[index++] == '[')
        skipWhitespace()
        if (peek(']')) {
            index++
            return JsonValue.Arr(emptyList())
        }
        val values = mutableListOf<JsonValue>()
        while (true) {
            values += readValue()
            skipWhitespace()
            when (source.getOrNull(index)) {
                ',' -> index++
                ']' -> { index++; return JsonValue.Arr(values) }
                else -> error("Expected ',' or ']' at $index")
            }
        }
    }

    private fun readString(): String {
        require(source[index++] == '"')
        val out = StringBuilder()
        while (index < source.length) {
            val ch = source[index++]
            when (ch) {
                '"' -> return out.toString()
                '\\' -> {
                    require(index < source.length) { "Bad JSON escape" }
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            require(index + 4 <= source.length) { "Bad unicode escape" }
                            val code = source.substring(index, index + 4).toInt(16)
                            out.append(code.toChar())
                            index += 4
                        }
                        else -> error("Unsupported JSON escape \\$escaped")
                    }
                }
                else -> {
                    if (strict) require(ch.code >= 0x20) { "Unescaped JSON control character" }
                    out.append(ch)
                }
            }
        }
        error("Unterminated JSON string")
    }

    private fun readNumber(): JsonValue.Num {
        val start = index
        if (source[index] == '-') index++
        while (source.getOrNull(index)?.isDigit() == true) index++
        if (source.getOrNull(index) == '.') {
            index++
            while (source.getOrNull(index)?.isDigit() == true) index++
        }
        if (source.getOrNull(index) == 'e' || source.getOrNull(index) == 'E') {
            index++
            if (source.getOrNull(index) == '+' || source.getOrNull(index) == '-') index++
            while (source.getOrNull(index)?.isDigit() == true) index++
        }
        val raw = source.substring(start, index)
        if (strict) require(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").matches(raw)) { "Invalid JSON number" }
        return JsonValue.Num(raw)
    }

    private fun expect(value: String) {
        require(source.regionMatches(index, value, 0, value.length)) { "Expected '$value' at $index" }
        index += value.length
    }

    private fun skipWhitespace() {
        while (source.getOrNull(index)?.let { if (strict) it in " \t\r\n" else it.isWhitespace() } == true) index++
    }

    private fun peek(ch: Char): Boolean = source.getOrNull(index) == ch
}

internal fun parseRoot(raw: String): JsonValue.Obj = JsonReader(raw).read() as? JsonValue.Obj
    ?: error("Catalog root must be an object")

internal fun JsonValue.Obj.string(key: String, default: String = ""): String = when (val value = values[key]) {
    is JsonValue.Str -> value.value
    is JsonValue.Num -> value.raw
    is JsonValue.Bool -> value.value.toString()
    else -> default
}

internal fun JsonValue.Obj.int(key: String, default: Int = 0): Int = when (val value = values[key]) {
    is JsonValue.Num -> value.raw.toDoubleOrNull()?.toInt() ?: default
    is JsonValue.Str -> value.value.toIntOrNull() ?: default
    else -> default
}

internal fun JsonValue.Obj.bool(key: String, default: Boolean = false): Boolean = when (val value = values[key]) {
    is JsonValue.Bool -> value.value
    is JsonValue.Str -> value.value.toBooleanStrictOrNull() ?: default
    else -> default
}

internal fun JsonValue.Obj.array(key: String): List<JsonValue> = (values[key] as? JsonValue.Arr)?.values.orEmpty()
internal fun JsonValue.asObject(): JsonValue.Obj? = this as? JsonValue.Obj
internal fun JsonValue.asString(): String? = when (this) {
    is JsonValue.Str -> value
    is JsonValue.Num -> raw
    is JsonValue.Bool -> value.toString()
    else -> null
}
internal fun JsonValue.Obj.stringList(key: String): List<String> = array(key).mapNotNull { it.asString()?.trim()?.takeIf(String::isNotEmpty) }
internal fun JsonValue.Obj.stringMap(key: String): Map<String, String> =
    ((values[key] as? JsonValue.Obj)?.values.orEmpty()).mapValues { (_, value) -> value.asString().orEmpty() }

internal fun JsonValue.Obj.double(key: String, default: Double = 0.0): Double = when (val value = values[key]) {
    is JsonValue.Num -> value.raw.toDoubleOrNull() ?: default
    is JsonValue.Str -> value.value.toDoubleOrNull() ?: default
    else -> default
}

internal fun JsonValue.Obj.objectValue(key: String): JsonValue.Obj? = values[key] as? JsonValue.Obj
internal fun JsonValue.Obj.has(key: String): Boolean = key in values
internal fun JsonValue.Obj.isNull(key: String): Boolean = values[key] == JsonValue.Null

internal fun jsonStringify(value: JsonValue): String = when (value) {
    is JsonValue.Obj -> value.values.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, item) ->
        "\"${jsonEscape(key)}\":" + jsonStringify(item)
    }
    is JsonValue.Arr -> value.values.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::jsonStringify)
    is JsonValue.Str -> "\"${jsonEscape(value.value)}\""
    is JsonValue.Num -> value.raw
    is JsonValue.Bool -> if (value.value) "true" else "false"
    JsonValue.Null -> "null"
}

private fun jsonEscape(value: String): String = buildString {
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '\"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u").append(ch.code.toString(16).padStart(4, '0')) else append(ch)
        }
    }
}

internal fun jsonObject(vararg pairs: Pair<String, JsonValue?>): JsonValue.Obj = JsonValue.Obj(
    linkedMapOf<String, JsonValue>().apply { pairs.forEach { (key, value) -> if (value != null) put(key, value) } }
)
internal fun jsonArray(values: Iterable<JsonValue>): JsonValue.Arr = JsonValue.Arr(values.toList())
internal fun jsonString(value: String): JsonValue.Str = JsonValue.Str(value)
internal fun jsonNumber(value: Int): JsonValue.Num = JsonValue.Num(value.toString())
internal fun jsonNumber(value: Double): JsonValue.Num = JsonValue.Num(if (value.isFinite()) value.toString() else "0.0")
internal fun jsonBoolean(value: Boolean): JsonValue.Bool = JsonValue.Bool(value)
