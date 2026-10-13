package com.furybook.desktop

import com.furybook.content.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

/** Isolated synthetic UI arithmetic. Real Block remains unchanged and separately source-blocked. */
internal class AcquisitionUiFixtures(val directory: Path = Files.createTempDirectory("t18b-ui-"), val writable: Boolean = true,
    val fault: (DesktopAcquisitionWriteStage) -> Unit = {}, val allDefinitions:Boolean=false,
    val seed:(StoredCharacter)->StoredCharacter={it}) {
    companion object { const val SYNTHETIC_ID = "ui-synthetic-20"; const val REAL_BLOCK_ID = "feat_fcdd74622d21435d" }
    val store = DesktopAcquisitionStore(directory, fault)
    private val source = FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }
    private val core = DublFcp.open(source)
    private val evidence = RuleSourceRef("SYNTHETIC_RULE", "t18b", "Synthetic fixed20 UI fixture, not DUBL source acceptance")
    private val ref = DefinitionRef(DublFcp.PACK_ID, SYNTHETIC_ID)
    private val definition = AcquisitionDefinition(ref, AcquisitionKind.DEVELOPMENT, 1, RepeatPolicy.Single,
        RequirementNode.AllOf("none", evidence, emptyList(), NoRequirementsEvidence(evidence, "Synthetic no requirements")),
        PriceRule.Fixed(20, 0, evidence), SourceStatus.Confirmed, "Проверочный навык (SYNTHETIC_RULE)", evidence)
    var catalog = core.loadAcquisition().catalog.let { it.copy(definitions = it.definitions + (ref to definition), catalogRevision = it.catalogRevision + ":ui") }
    private val original = core.loadDevelopment()
    private val display = original.entries.first().copy(id = SYNTHETIC_ID, name = definition.name, section = "Общие",
        category = "Проверка интерфейса", cost = 20, costType = DevelopmentCostType.XP, maxRank = 1,
        requirements = "", benefit = "Только проверочный пример", notes = "SYNTHETIC_RULE", tags = emptyList(),
        accessId = null, abilityOptions = emptyList(), incomplete = false, repeatable = false, perfectRoot = false,
        mechanicsConflict = "", conflictNote = "")
    val development = DevelopmentCatalog("t18b-ui", listOf(display) + original.entries.filter { allDefinitions || it.id == REAL_BLOCK_ID })
    init { runBlocking { for (id in listOf("ui-A", "ui-B")) if (store.load(id) is LoadResult.Missing) {
        val character = DublCharacter(id, name = if (id == "ui-A") "Синтетический A" else "Синтетический B",
            attributes = DublCharacter(id).attributes + (AttributeId.DEXTERITY to AttributeValue(3)) + (AttributeId.SPEED to AttributeValue(2)),
            skills = mapOf("unarmed" to CharacterSkill("unarmed", definitionId = "unarmed", rank = 3)))
        val state = StoredCharacter(character, CharacterSheetExtras(), listOf(AcquisitionInstance("unarmed", DefinitionRef(DublFcp.PACK_ID, "unarmed"), 3, emptyMap(), null, AcquisitionOrigin.New)),
            Ledger(Opening("ui:$id", "SYNTHETIC_RULE", Amount(100, 2), RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.NewCharacter), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
        check(store.compareAndSet(id, -1, seed(state)) == CommitResult.Committed)
    } } }
    fun app(catalogReader:()->AcquisitionCatalog={catalog},beforeLegacyRead:()->Unit={}): DesktopAppState {
        val states = runBlocking { store.characterIds().associateWith { (store.load(it) as LoadResult.Loaded).state } }
        val characters = object : CharacterStore {
            override fun load():AppSnapshot { beforeLegacyRead();return AppSnapshot(states.values.map { it.character }, "ui-A") }
            override fun save(snapshot: AppSnapshot) = error("No legacy writes")
        }
        val extras = object : CharacterExtrasStore {
            override fun load(characterId: String) = states.getValue(characterId).extras
            override fun save(characterId: String, extras: CharacterSheetExtras) = error("No legacy extras")
        }
        return DesktopAppState(characters, extras, writable, store, catalogReader, development)
    }
    fun appFromOriginal(original:StoredCharacter):DesktopAppState {
        val characters=object:CharacterStore {
            override fun load()=AppSnapshot(listOf(original.character),original.character.id)
            override fun save(snapshot:AppSnapshot)=error("No legacy writes")
        }
        val extras=object:CharacterExtrasStore {
            override fun load(characterId:String)=original.extras
            override fun save(characterId:String,extras:CharacterSheetExtras)=error("No legacy extras")
        }
        return DesktopAppState(characters,extras,writable,store,{catalog},development)
    }
    fun enableChi(state:DesktopAppState) {
        catalog=loadAcquisitionCatalog(listOf("dubl-3.69","dubl-chi-3.69").map { FcpContentPack.load("fcp/$it",source) },true).catalog
        state.setChiPackActive(true)
    }
    fun saved(id: String = "ui-A") = runBlocking { (store.load(id) as LoadResult.Loaded).state }
    fun raw(id: String = "ui-A") = Files.readString(store.envelopePath(id))
}

internal object DesktopOracleRawJson {
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
            value.startsWith("{") -> JsonValue.Obj(DesktopOracleRawJson.fields(value).mapValues { JsonReader(it.value).read() })
            value.startsWith("[") -> JsonValue.Arr(DesktopOracleRawJson.array(value).map { JsonReader(it).read() })
            value.startsWith("\"") -> JsonValue.Str(DesktopOracleRawJson.string(value))
            value == "null" -> JsonValue.Null
            value == "true" -> JsonValue.Bool(true)
            value == "false" -> JsonValue.Bool(false)
            else -> { require(value.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))); JsonValue.Num(value) }
        }
    }
}
internal fun jsonStringify(value: JsonValue): String = when (value) {
    is JsonValue.Obj -> DesktopOracleRawJson.obj(value.values.mapValues { jsonStringify(it.value) })
    is JsonValue.Arr -> value.values.joinToString(",", "[", "]", transform = ::jsonStringify)
    is JsonValue.Str -> DesktopOracleRawJson.quote(value.value)
    is JsonValue.Num -> value.raw
    is JsonValue.Bool -> value.value.toString()
    JsonValue.Null -> "null"
}