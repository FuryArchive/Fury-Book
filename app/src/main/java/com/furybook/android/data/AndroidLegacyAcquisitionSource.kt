package com.furybook.android.data

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.concurrent.withLock

/** A synchronous legacy caller never waits behind acquisition IO or changes a captured backup. */
class AndroidLegacyWriteGate {
    private val lock = ReentrantLock()
    @Volatile var readOnly: Boolean = false
        private set
    fun protect() { readOnly = true }
    fun checkWritable() { check(!readOnly && !lock.isLocked) { "Existing acquisition data is read-only; use a confirmed canonical command" } }
    fun <T> legacyWrite(action: () -> T): T {
        check(!readOnly && lock.tryLock()) { "Legacy write is blocked by acquisition storage/recovery" }
        try { check(!readOnly); return action() } finally { lock.unlock() }
    }
    internal fun <T> captured(action: () -> T): T = lock.withLock(action)
}

class AndroidSourceUnavailable(val unsupported: Boolean, message: String, val rawFiles: Map<String, ByteArray>) : IllegalStateException(message)

/** Exact source capture, not the framework's permissive cache or a regenerated snapshot. */
class AndroidLegacyCapture internal constructor(
    val snapshotRaw: String?, internal val stateEntries: Map<String, AndroidPreferenceValue>, internal val extrasEntries: Map<String, AndroidPreferenceValue>,
    val rawFiles: Map<String, ByteArray>,
) {
    fun extras(characterId: String): CharacterSheetExtras = decodeAndroidLegacyExtras(extrasEntries, characterId)
    fun selected(raw: String, characterId: String): String {
        val root = AndroidRawJson.fields(raw)
        val rows = AndroidRawJson.array(root.getValue("characters"))
        val ids = rows.map { AndroidRawJson.string(AndroidRawJson.fields(it).getValue("id")) }
        require(ids.all(String::isNotBlank) && ids.distinct().size == ids.size) { "Invalid legacy identities" }
        require(ids.none { it != characterId && (it.startsWith("$characterId.") || characterId.startsWith("$it.")) }) { "Ambiguous legacy extras prefix" }
        val row = rows.single { AndroidRawJson.string(AndroidRawJson.fields(it).getValue("id")) == characterId }
        return AndroidRawJson.obj(mapOf("schema" to root.getValue("schema"), "character" to row,
            "extras" to preferenceObject(extrasEntries.filterKeys { it.startsWith("character.$characterId.") })))
    }
    fun origin(characterId: String, composition: List<String>): LegacyOrigin {
        val raw = requireNotNull(snapshotRaw) { "Missing legacy snapshot" }
        val selected = selected(raw, characterId)
        val root = AndroidRawJson.fields(raw)
        val bundle = AndroidRawJson.obj(mapOf(
            "format" to AndroidRawJson.quote("android-preferences-source-v1"), "characterId" to AndroidRawJson.quote(characterId),
            "selected" to AndroidRawJson.quote(selected), "stateEntries" to preferenceObject(stateEntries), "extrasEntries" to preferenceObject(extrasEntries),
            "files" to AndroidRawJson.obj(SOURCE_NAMES.associateWith { name -> AndroidRawJson.obj(mapOf(
                "present" to (name in rawFiles).toString(), "bytes" to (rawFiles[name]?.let { AndroidRawJson.quote(Base64.getEncoder().encodeToString(it)) } ?: "null"))) }),
        ))
        return LegacyOrigin(characterId, root.getValue("schema").toInt(), legacySourceFingerprint(selected), raw, composition, extrasSource = LegacyExtrasSource(bundle))
    }
}

private val SOURCE_NAMES = listOf("dubl_native_state.xml", "dubl_native_state.xml.bak", "dubl_character_sheet_extras.xml", "dubl_character_sheet_extras.xml.bak")

/** Bounded adapter for exactly the two existing Android preference maps. Never consumes .bak. */
class AndroidLegacyAcquisitionSource(
    private val preferencesDirectory: Path,
    val gate: AndroidLegacyWriteGate = AndroidLegacyWriteGate(),
) {
    /** Installed only after an initial disk capture, before the framework can normalize anything. */
    internal var memoryReader: (() -> Pair<Map<String, *>, Map<String, *>>)? = null

    fun rawFiles(): Map<String, ByteArray> {
        if (Files.exists(preferencesDirectory, NOFOLLOW_LINKS)) require(!Files.isSymbolicLink(preferencesDirectory) && Files.isDirectory(preferencesDirectory, NOFOLLOW_LINKS)) { "Unsupported preferences directory" }
        return SOURCE_NAMES.mapNotNull { name ->
        val path = preferencesDirectory.resolve(name)
        if (Files.notExists(path, NOFOLLOW_LINKS)) null else {
            require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Unsupported preference source path" }
            require(Files.size(path) <= MAX_BYTES) { "Preference source is too large" }
            name to Files.readAllBytes(path)
        }
    }.toMap()
    }

    fun capture(): AndroidLegacyCapture = gate.captured {
        val files = rawFiles()
        try {
            require(files.keys.none { it.endsWith(".bak") }) { "Preference backup exists; preserve both branches for recovery" }
            val state = files["dubl_native_state.xml"]?.let(::readPreferenceMap).orEmpty()
            val extras = files["dubl_character_sheet_extras.xml"]?.let(::readPreferenceMap).orEmpty()
            val after = rawFiles()
            require(files.keys == after.keys && files.all { (key, bytes) -> bytes.contentEquals(after.getValue(key)) }) { "Preference source changed while reading" }
            memoryReader?.invoke()?.let { (memoryState, memoryExtras) ->
                require(state == typedPreferences(memoryState) && extras == typedPreferences(memoryExtras)) { "Preference memory/disk disagreement; prepare again after pending writes settle" }
            }
            val rawValue = state["state_v1"]
            val raw = rawValue?.let { preferenceString(it) }
            require(raw != null || (state.isEmpty() && extras.isEmpty())) { "Existing preferences lack a valid state_v1; recovery required" }
            if (raw != null) when (val decoded = decodeEnvelope(raw)) {
                is LoadResult.MigrationRequired -> Unit
                is LoadResult.Unsupported -> throw AndroidSourceUnavailable(true, decoded.reason, files)
                is LoadResult.Corrupt -> throw AndroidSourceUnavailable(false, decoded.reason, files)
                else -> error("Versioned acquisition data cannot reside in state_v1")
            }
            AndroidLegacyCapture(raw, state.toMap(), extras.toMap(), files)
        } catch (failure: AndroidSourceUnavailable) { throw failure }
        catch (failure: Exception) { throw AndroidSourceUnavailable(false, failure.message ?: "Invalid preference source", files) }
    }

    internal fun capturedOrigin(origin: LegacyOrigin): AndroidLegacyCapture {
        val bundle = AndroidRawJson.fields(requireNotNull(origin.extrasSource?.raw))
        require(bundle.keys == setOf("format", "characterId", "selected", "stateEntries", "extrasEntries", "files"))
        require(AndroidRawJson.string(bundle.getValue("format")) == "android-preferences-source-v1" && AndroidRawJson.string(bundle.getValue("characterId")) == origin.characterId)
        val fileFields=AndroidRawJson.fields(bundle.getValue("files")); require(fileFields.keys==SOURCE_NAMES.toSet())
        val files=fileFields.mapNotNull { (name,value) ->
            val row=AndroidRawJson.fields(value);require(row.keys==setOf("present","bytes"))
            when(row.getValue("present")) {
                "true" -> name to Base64.getDecoder().decode(AndroidRawJson.string(row.getValue("bytes")))
                "false" -> { require(row.getValue("bytes")=="null");null }
                else -> error("Invalid source presence")
            }
        }.toMap()
        require(files.keys.none { it.endsWith(".bak") }) { "Migration backup is unsettled" }
        val state=files["dubl_native_state.xml"]?.let(::readPreferenceMap).orEmpty()
        val extras=files["dubl_character_sheet_extras.xml"]?.let(::readPreferenceMap).orEmpty()
        require(preferenceObject(state)==bundle.getValue("stateEntries") && preferenceObject(extras)==bundle.getValue("extrasEntries")) { "Backup typed entries differ from original XML" }
        require(state["state_v1"]?.string()==origin.raw) { "Origin differs from state_v1 backup" }
        val captured=AndroidLegacyCapture(origin.raw,state,extras,files)
        val selected=captured.selected(origin.raw,origin.characterId)
        require(selected==AndroidRawJson.string(bundle.getValue("selected")) && legacySourceFingerprint(selected)==origin.sourceRevision &&
            AndroidRawJson.fields(origin.raw).getValue("schema").toInt()==origin.schema) { "Origin selected evidence differs" }
        return captured
    }
    fun unchanged(origin: LegacyOrigin): Boolean {
        val previous=capturedOrigin(origin).selected(origin.raw,origin.characterId)
        val captured=capture()
        return captured.snapshotRaw?.let { captured.selected(it,origin.characterId)==previous }==true
    }

    companion object { private const val MAX_BYTES = 16L * 1024 * 1024 }
}

/** Shared canonicalJson validates syntax/duplicate keys; this helper only selects raw validated slices.
 * It never parses a financial/equipment number through Double or supplies a second JSON policy. */
internal object AndroidRawJson {
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
internal data class AndroidPreferenceValue(val kind:String,val valueJson:String) {
    fun string():String { require(kind=="string") { "Expected preference String" }; return AndroidRawJson.string(valueJson) }
    fun strings():List<String> { require(kind=="set"); return AndroidRawJson.array(valueJson).map(AndroidRawJson::string) }
    fun json():String=AndroidRawJson.obj(mapOf("type" to AndroidRawJson.quote(kind),"value" to valueJson))
}
private fun preferenceObject(values:Map<String,AndroidPreferenceValue>):String = AndroidRawJson.obj(values.mapValues { it.value.json() })
internal fun typedPreferences(values: Map<String, *>): Map<String, AndroidPreferenceValue> = values.mapValues { (_, value) -> when (value) {
    is String -> AndroidPreferenceValue("string",AndroidRawJson.quote(value))
    is Boolean -> AndroidPreferenceValue("boolean",value.toString())
    is Int -> AndroidPreferenceValue("int",AndroidRawJson.quote(value.toString()))
    is Long -> AndroidPreferenceValue("long",AndroidRawJson.quote(value.toString()))
    is Float -> AndroidPreferenceValue("float",AndroidRawJson.quote(value.toString()))
    is Set<*> -> { require(value.all { it is String }); AndroidPreferenceValue("set",value.map { it as String }.sorted().joinToString(",","[","]",transform=AndroidRawJson::quote)) }
    else -> error("Unsupported preference value type")
} }
private fun preferenceString(value: AndroidPreferenceValue): String = value.string()

internal fun readPreferenceMap(bytes: ByteArray): Map<String, AndroidPreferenceValue> {
    val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
    require(!text.contains("<!")) { "Unsupported XML declaration/entity/comment" }
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false; isExpandEntityReferences = false }
    val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(escapeLegacyXmlControls(text).toByteArray(StandardCharsets.UTF_8)))
    val root = document.documentElement
    require(root.tagName == "map" && root.attributes.length == 0) { "Expected preference map" }
    fun children(element: Element): List<Element> = (0 until element.childNodes.length).mapNotNull { index ->
        val node = element.childNodes.item(index)
        if (node.nodeType == Node.ELEMENT_NODE) node as Element else {
            require(node.nodeType == Node.TEXT_NODE && node.textContent.isBlank()) { "Unexpected preference XML node" }; null
        }
    }
    val result = linkedMapOf<String, AndroidPreferenceValue>()
    for (node in children(root)) {
        require(node.hasAttribute("name") && node.getAttribute("name").isNotEmpty()) { "Missing preference key" }
        val name = restoreLegacyXmlControls(node.getAttribute("name")); require(name !in result) { "Duplicate preference key" }
        val kind = node.tagName
        val value: String = when (kind) {
            "string" -> { require(node.attributes.length == 1 && (0 until node.childNodes.length).all { node.childNodes.item(it).nodeType in listOf(Node.TEXT_NODE, Node.CDATA_SECTION_NODE) }); AndroidRawJson.quote(restoreLegacyXmlControls(node.textContent)) }
            "set" -> { require(node.attributes.length == 1); val strings = children(node).map { child ->
                require(child.tagName == "string" && child.attributes.length == 0 && (0 until child.childNodes.length).all { child.childNodes.item(it).nodeType in listOf(Node.TEXT_NODE, Node.CDATA_SECTION_NODE) }); restoreLegacyXmlControls(child.textContent)
            }; require(strings.distinct().size == strings.size); strings.sorted().joinToString(",","[","]",transform=AndroidRawJson::quote) }
            "int", "long", "float", "boolean" -> {
                require(node.attributes.length == 2 && node.hasAttribute("value") && children(node).isEmpty())
                val raw = node.getAttribute("value")
                when (kind) {
                    "int" -> { require(raw.toIntOrNull() != null); AndroidRawJson.quote(raw.toInt().toString()) }
                    "long" -> { require(raw.toLongOrNull() != null); AndroidRawJson.quote(raw.toLong().toString()) }
                    "float" -> { require(raw.toFloatOrNull() != null); AndroidRawJson.quote(raw.toFloat().toString()) }
                    else -> { require(raw in listOf("true", "false")); raw }
                }
            }
            else -> error("Unsupported preference XML type: $kind")
        }
        result[name] = AndroidPreferenceValue(kind, value)
    }
    return result
}

internal fun decodeAndroidLegacyExtras(entries: Map<String, AndroidPreferenceValue>, characterId: String): CharacterSheetExtras {
    val prefix = "character.$characterId."
    val keys = entries.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    val known = setOf("portrait", "conditions", "hidden_resources", "preferred_skill_attributes", "skill_groups", "development_groups", "condition_overrides", "custom_conditions", "notes", "note_entries", "favorite_skill_ids", "favorites")
    require(keys.all { it in known }) { "Unknown legacy extras fields; original bytes retained" }
    fun string(key: String): String? = entries[prefix + key]?.let(::preferenceString)
    fun strings(key: String): List<String> = entries[prefix + key]?.strings().orEmpty()
    val preferred = strings("preferred_skill_attributes").map { raw ->
        val split = raw.lastIndexOf("::"); require(split > 0); raw.substring(0, split) to AttributeId.valueOf(raw.substring(split + 2))
    }; require(preferred.map { it.first }.distinct().size == preferred.size)
    val result = CharacterSheetExtras(string("portrait"), strings("conditions").map(CharacterConditionId::valueOf).toSet(),
        strings("hidden_resources").map(CharacterSheetResourceId::valueOf).toSet(), preferred.toMap(),
        SheetGroupingRules.decode(string("skill_groups")), SheetGroupingRules.decode(string("development_groups")),
        ConditionLocalDataCodec.decodeOverrides(string("condition_overrides")), ConditionLocalDataCodec.decodeCustom(string("custom_conditions")),
        string("notes").orEmpty(), CharacterNoteDataCodec.decode(string("note_entries")))
    mapOf("skill_groups" to SheetGroupingRules.encode(result.skillGroups), "development_groups" to SheetGroupingRules.encode(result.developmentGroups),
        "condition_overrides" to ConditionLocalDataCodec.encodeOverrides(result.conditionOverrides), "custom_conditions" to ConditionLocalDataCodec.encodeCustom(result.customConditions),
        "note_entries" to CharacterNoteDataCodec.encode(result.noteEntries)).forEach { (key, encoded) ->
        require(string(key).orEmpty() == encoded) { "Legacy extras $key cannot be represented without loss" }
    }
    return result
}

/** FastXmlSerializer's numeric control references occur in existing grouping/note String values.
 * DOM/XML1.0 needs a reversible parse-only escape. Original bytes are always retained/exported. */
private fun escapeLegacyXmlControls(text:String):String {
    val marker='\uE000'
    val escaped=text.replace(marker.toString(),"$marker$marker")
    return Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(escaped) { match ->
        val raw=match.groupValues[1]
        val number=if(raw.startsWith('x')) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
        when {
            number==marker.code -> "$marker$marker"
            number!=null && number in 0..31 && number !in listOf(9,10,13) -> marker+number.toString(16).padStart(4,'0')
            else -> match.value
        }
    }
}
private fun restoreLegacyXmlControls(text:String):String = buildString {
    var i=0
    while(i<text.length) {
        val c=text[i++]
        if(c!='\uE000') append(c) else if(i<text.length && text[i]=='\uE000') {append(c);i++}
        else { require(i+4<=text.length); val number=text.substring(i,i+4).toInt(16);require(number in 0..31 && number !in listOf(9,10,13));append(number.toChar());i+=4 }
    }
}
