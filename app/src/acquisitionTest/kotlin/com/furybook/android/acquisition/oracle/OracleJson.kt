package com.furybook.android.acquisition.oracle

import com.furybook.android.data.AndroidRawJson

/** Test-only structural bridge. Numbers remain literal strings, including Long extremes.
 * Uses the app's JSON tokenizer, not a Shared internal/friend implementation or rule calculator.
 */
internal sealed interface JsonValue {
    data class Obj(val values: Map<String, JsonValue>) : JsonValue
    data class Arr(val values: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    data class Num(val raw: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}
internal class JsonReader(private val source: String) {
    fun read(): JsonValue {
        val value = source.trim()
        return when {
            value.startsWith("{") -> JsonValue.Obj(AndroidRawJson.fields(value).mapValues { JsonReader(it.value).read() })
            value.startsWith("[") -> JsonValue.Arr(AndroidRawJson.array(value).map { JsonReader(it).read() })
            value.startsWith("\"") -> JsonValue.Str(AndroidRawJson.string(value))
            value == "null" -> JsonValue.Null
            value == "true" -> JsonValue.Bool(true)
            value == "false" -> JsonValue.Bool(false)
            else -> { require(value.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))); JsonValue.Num(value) }
        }
    }
}
internal fun jsonStringify(value: JsonValue): String = when (value) {
    is JsonValue.Obj -> AndroidRawJson.obj(value.values.mapValues { jsonStringify(it.value) })
    is JsonValue.Arr -> value.values.joinToString(",", "[", "]", transform = ::jsonStringify)
    is JsonValue.Str -> AndroidRawJson.quote(value.value)
    is JsonValue.Num -> value.raw
    is JsonValue.Bool -> value.value.toString()
    JsonValue.Null -> "null"
}
