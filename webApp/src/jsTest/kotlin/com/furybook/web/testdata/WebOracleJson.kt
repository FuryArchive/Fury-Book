package com.furybook.web.testdata

import com.furybook.dubl.acquisition.canonicalJson

/** Test JSON boundary only; no price or acquisition implementation. */
internal object WebOracleRawJson {
    fun quote(text: String): String = buildString {
        append('"')
        text.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 32 || c.code in 0xD800..0xDFFF) append("\\u").append(c.code.toString(16).padStart(4,'0')) else append(c)
        } }
        append('"')
    }
    fun obj(fields: Map<String, String>): String = canonicalJson(fields.entries.joinToString(",", "{", "}") { quote(it.key) + ":" + it.value })
    fun array(raw: String): List<String> = slices(canonicalJson(raw), false).map { it.second }
    fun fields(raw: String): Map<String, String> = slices(canonicalJson(raw), true).associate { string(it.first) to it.second }
    fun string(raw: String): String {
        val text=canonicalJson(raw); require(text.first()=='"' && text.last()=='"')
        return buildString {
            var i=1
            while(i<text.lastIndex) {
                val c=text[i++]
                if(c!='\\') append(c) else when(val escaped=text[i++]) {
                    '"','\\','/' -> append(escaped); 'b' -> append('\b'); 'f' -> append('\u000c'); 'n' -> append('\n'); 'r' -> append('\r'); 't' -> append('\t')
                    'u' -> { append(text.substring(i,i+4).toInt(16).toChar()); i+=4 }
                    else -> error("Invalid JSON string")
                }
            }
        }
    }
    private fun slices(text:String,objectMode:Boolean):List<Pair<String,String>> {
        require(text.first()==(if(objectMode)'{' else '[') && text.last()==(if(objectMode)'}' else ']'))
        fun end(start:Int):Int {
            var i=start; var quoted=false; var escaped=false; var depth=0
            while(i<text.lastIndex) {
                val c=text[i]
                if(quoted) { if(!escaped && c=='"') quoted=false; escaped=!escaped && c=='\\' }
                else when(c) { '"' -> quoted=true; '{','[' -> depth++; '}',']' -> { if(depth==0)return i; depth-- }; ',',':' -> if(depth==0)return i }
                i++
            }
            return i
        }
        var i=1; val result=mutableListOf<Pair<String,String>>()
        while(i<text.lastIndex) {
            val key=if(objectMode) { val e=end(i); text.substring(i,e).also { require(text[e]==':');i=e+1 } } else ""
            val e=end(i); result+=key to text.substring(i,e); i=e+1
        }
        return result
    }
}



/** Test-only structural bridge. Numbers remain literal strings, including Long extremes.
 * Uses a test-only structural tokenizer, not a Shared internal/friend implementation or rule calculator.
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
            value.startsWith("{") -> JsonValue.Obj(WebOracleRawJson.fields(value).mapValues { JsonReader(it.value).read() })
            value.startsWith("[") -> JsonValue.Arr(WebOracleRawJson.array(value).map { JsonReader(it).read() })
            value.startsWith("\"") -> JsonValue.Str(WebOracleRawJson.string(value))
            value == "null" -> JsonValue.Null
            value == "true" -> JsonValue.Bool(true)
            value == "false" -> JsonValue.Bool(false)
            else -> { require(value.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))); JsonValue.Num(value) }
        }
    }
}
internal fun jsonStringify(value: JsonValue): String = when (value) {
    is JsonValue.Obj -> WebOracleRawJson.obj(value.values.mapValues { jsonStringify(it.value) })
    is JsonValue.Arr -> value.values.joinToString(",", "[", "]", transform = ::jsonStringify)
    is JsonValue.Str -> WebOracleRawJson.quote(value.value)
    is JsonValue.Num -> value.raw
    is JsonValue.Bool -> value.value.toString()
    JsonValue.Null -> "null"
}