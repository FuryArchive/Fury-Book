package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.data.validateLegacySnapshot
import com.furybook.dubl.model.*

private class LegacyMappingEntry(override val key:String,override val value:DefinitionRef):Map.Entry<String,DefinitionRef> {
    override fun equals(other:Any?):Boolean = other is Map.Entry<*,*> && key==other.key && value==other.value
    override fun hashCode():Int = key.hashCode() xor value.hashCode()
}

/** A captured absent extras file is distinct from an origin with no extras-source support. */
data class LegacyExtrasSource(val raw: String?)

/** Exact source plus explicit old active pack order. No device-local path, time or random identity. */
class LegacyOrigin(
    val characterId: String,
    val schema: Int,
    val sourceRevision: String,
    val raw: String,
    activePackIds: List<String>,
    ownershipMappings: Map<String, DefinitionRef> = emptyMap(),
    val extrasSource: LegacyExtrasSource? = null,
) {
    val activePackIds: List<String> = EnvelopeList(activePackIds)
    val ownershipMappings: Map<String, DefinitionRef> = object : AbstractMap<String, DefinitionRef>() {
        private val captured = ownershipMappings.toList()
        override val entries: Set<Map.Entry<String, DefinitionRef>> get() = captured.map {
            LegacyMappingEntry(it.first,it.second)
        }.toSet()
    }
    val sourceFingerprint: String get() = legacySourceFingerprint(if (extrasSource == null) raw else
        listOf("characters.json", raw, "sheet-extras.json", if (extrasSource.raw == null) "absent" else "present", extrasSource.raw.orEmpty())
            .joinToString("") { "${it.length}:$it" })
    fun copy(characterId:String=this.characterId,schema:Int=this.schema,sourceRevision:String=this.sourceRevision,raw:String=this.raw,
        activePackIds:List<String> = this.activePackIds,ownershipMappings:Map<String,DefinitionRef> = this.ownershipMappings,extrasSource:LegacyExtrasSource?=this.extrasSource) =
        LegacyOrigin(characterId,schema,sourceRevision,raw,activePackIds,ownershipMappings,extrasSource)
    override fun equals(other:Any?):Boolean=other is LegacyOrigin && characterId==other.characterId && schema==other.schema &&
        sourceRevision==other.sourceRevision && raw==other.raw && activePackIds==other.activePackIds && ownershipMappings==other.ownershipMappings && extrasSource==other.extrasSource
    override fun hashCode():Int=listOf(characterId,schema,sourceRevision,raw,activePackIds,ownershipMappings,extrasSource).hashCode()
}

/** Original positive ownership evidence. Scope and fingerprint belong to the enclosing origin.
 * Null candidate means the old record lacks a definition ID; display names never fill this field.
 */
data class LegacyOwnershipRecord(
    val ownershipKey: String,
    val kind: AcquisitionKind,
    val rank: Int,
    val candidateEntryId: String?,
    val custom: Boolean,
)

class LegacyMigrationProvenance(
    val origin: LegacyOrigin,
    val calculatorBaseline: String,
    /** Null means unavailable: opening was explicitly confirmed, not calculated. */
    val catalogIdentity: String?,
    val historyUnknown: Boolean = true,
    ownershipRecords: List<LegacyOwnershipRecord> = emptyList(),
) {
    val ownershipRecords: List<LegacyOwnershipRecord> = EnvelopeList(ownershipRecords)
    fun copy(origin:LegacyOrigin=this.origin,calculatorBaseline:String=this.calculatorBaseline,catalogIdentity:String?=this.catalogIdentity,
        historyUnknown:Boolean=this.historyUnknown,ownershipRecords:List<LegacyOwnershipRecord> = this.ownershipRecords) =
        LegacyMigrationProvenance(origin,calculatorBaseline,catalogIdentity,historyUnknown,ownershipRecords)
    override fun equals(other:Any?):Boolean=other is LegacyMigrationProvenance && origin==other.origin && calculatorBaseline==other.calculatorBaseline &&
        catalogIdentity==other.catalogIdentity && historyUnknown==other.historyUnknown && ownershipRecords==other.ownershipRecords
    override fun hashCode():Int=listOf(origin,calculatorBaseline,catalogIdentity,historyUnknown,ownershipRecords).hashCode()
}

/** Derived status: resolving a holding adds an exactly scoped canonical Legacy instance, never
 * rewrites original evidence or charges again. T11 must retain operation-backed removal markers
 * when implementing later explicit removal/Undo, so removal is not confused with never-mapped data.
 */
fun unresolvedLegacyOwnership(state:StoredCharacter,catalog:AcquisitionCatalog):List<LegacyOwnershipRecord> {
    val provenance=state.ledger.opening.legacyMigration ?: return emptyList()
    val source=provenance.origin
    val fingerprint=source.sourceFingerprint
    val scoped=state.instances.filter { instance ->
        val origin=instance.origin as? AcquisitionOrigin.Legacy
        origin!=null && origin.characterId==source.characterId && origin.schema==source.schema.toString() &&
            origin.sourceRevision==source.sourceRevision && origin.sourceFingerprint==fingerprint &&
            instance.instanceId==legacyInstanceId(instance.definition,origin)
    }.groupBy { (it.origin as AcquisitionOrigin.Legacy).ownershipKey }
    val removed = activeLegacyRemovalKeys(state)
    return provenance.ownershipRecords.filter { record -> !record.custom && record.ownershipKey !in removed && scoped[record.ownershipKey].orEmpty().none { instance ->
        catalog.definitions[instance.definition]?.kind==record.kind &&
            (record.candidateEntryId==null || record.candidateEntryId==instance.definition.entryId)
    } }

}
internal fun legacyAcquisitionIssue(definition:AcquisitionDefinition,state:StoredCharacter,catalog:AcquisitionCatalog):AcquisitionIssue? =
    unresolvedLegacyOwnership(state,catalog).firstOrNull { record -> record.kind==definition.kind &&
        (record.candidateEntryId==null || record.candidateEntryId==definition.ref.entryId) }?.let {
        AcquisitionIssue(RequirementStatus.ChoiceRequired,"Resolve legacy ownership ${it.ownershipKey} before acquiring this definition",definition.ref)
    }

internal fun legacyOwnershipRecords(c:DublCharacter):List<LegacyOwnershipRecord> = buildList {
    fun addRecord(key:String,kind:AcquisitionKind,rank:Int,candidate:String?,custom:Boolean=false) {
        if(rank>0)add(LegacyOwnershipRecord(key,kind,rank,candidate?.takeIf(String::isNotBlank),custom))
    }
    c.development.entries.sortedBy { it.key }.forEach { (id,owned) -> addRecord("development:$id",AcquisitionKind.DEVELOPMENT,owned.rank,id,
        c.customDevelopmentEntries.any {it.id==id}) }
    c.skills.entries.sortedBy { it.key }.forEach { (key,skill) -> addRecord("skill:$key",AcquisitionKind.SKILL,skill.rank,skill.definitionId) }
    addRecord("mana",AcquisitionKind.MANA,c.magic.manaRank,"mana:rank")
    addRecord("chi-bonus",AcquisitionKind.CHI_BONUS,c.chiBonusRanks,"chi:bonus-ranks")
    c.magic.schools.forEach { addRecord(legacySchoolOwnershipKey(it.name),AcquisitionKind.SCHOOL,it.rank,null) }
    c.magic.spells.filter {it.learned}.forEach { addRecord("spell:${it.uid}",AcquisitionKind.SPELL,1,it.catalogId,it.custom) }
}

/** UTF-16BE code-unit hashing retains exact String data, even lone surrogate units. */
fun legacySourceFingerprint(raw:String):String {
    val bytes=ByteArray(raw.length*2)
    raw.forEachIndexed { index,c -> bytes[index*2]=(c.code ushr 8).toByte();bytes[index*2+1]=c.code.toByte() }
    return "sha256-utf16be:"+legacySha256(bytes)
}
internal fun legacyMigrationId(origin:LegacyOrigin):String = "legacy-v11:"+legacySourceFingerprint(
    listOf(origin.characterId,origin.schema.toString(),origin.sourceRevision,origin.sourceFingerprint).joinToString(""){ "${it.length}:$it" })

class PendingMigration internal constructor(
    val origin: LegacyOrigin,
    val character: DublCharacter,
    val extras: CharacterSheetExtras,
    instances: List<AcquisitionInstance>,
    unresolvedOwnershipKeys: List<String>,
    val reason: String,
) {
    val instances:List<AcquisitionInstance> = EnvelopeList(instances)
    val unresolvedOwnershipKeys:List<String> = EnvelopeList(unresolvedOwnershipKeys)
}
sealed interface MigrationResult {
    data class Ready(val state:StoredCharacter):MigrationResult
    data class NeedsBalanceConfirmation(val pending:PendingMigration):MigrationResult
    data class Unsupported(val raw:String,val reason:String):MigrationResult
    data class Corrupt(val raw:String,val reason:String):MigrationResult
}
enum class LegacyMigrationComparison { SameOrigin, Conflict }
/** Recognition only: never sums openings or resolves competing revisions/balances. */
fun compareLegacyMigrations(left:StoredCharacter,right:StoredCharacter):LegacyMigrationComparison =
    if(left.character.id==right.character.id && left.ledger.opening==right.ledger.opening && left.instances==right.instances &&
        left.revision==right.revision && left.ledger==right.ledger && left.character==right.character &&
        left.extras==right.extras && left.receipts==right.receipts && left.creationPhase==right.creationPhase) LegacyMigrationComparison.SameOrigin else LegacyMigrationComparison.Conflict

fun prepareLegacy(raw:String,origin:LegacyOrigin,legacyCatalog:LegacyCatalogSnapshot?,extras:CharacterSheetExtras=CharacterSheetExtras()):MigrationResult {
    // Exact canonical data is already migrated. Never route it through legacy normalization.
    when(val loaded=decodeEnvelope(raw)) {
        is LoadResult.Loaded -> return MigrationResult.Ready(loaded.state)
        is LoadResult.Unsupported -> return MigrationResult.Unsupported(raw,loaded.reason)
        is LoadResult.Corrupt -> return MigrationResult.Corrupt(raw,loaded.reason)
        is LoadResult.LegacyImportConflict -> return MigrationResult.Corrupt(raw,loaded.reason)
        is LoadResult.Missing -> return MigrationResult.Corrupt(raw,"Missing legacy record")
        is LoadResult.MigrationRequired -> Unit
    }
    return try {
        require(raw==origin.raw) { "Origin backup does not match exact input" }
        // Public legacy normalization, with stable IDs for old nested records lacking identity.
        val character=validateLegacyOrigin(origin)
        val catalog=legacyCatalog?.takeIf { it.activePackIds==origin.activePackIds }
        val (instances,unresolved)=legacyInstances(character,origin,catalog)
        val pending=PendingMigration(origin,character,extras,instances,unresolved,"Exact old catalog/composition is unavailable; confirm the known XP/AP balance")
        if(catalog==null) MigrationResult.NeedsBalanceConfirmation(pending)
        else MigrationResult.Ready(migrated(pending,LegacyEconomyV11.balance(character,catalog),OpeningProvenance.LegacyCalculated,null,catalog.identity))
    } catch(failure:Exception) { MigrationResult.Corrupt(raw,failure.message ?: "Invalid legacy data") }
}

fun confirmLegacyBalance(pending:PendingMigration,balance:Amount,reason:String):StoredCharacter {
    require(reason.isNotBlank()) { "Explicit balance confirmation requires a reason" }
    return migrated(pending,balance,OpeningProvenance.UserConfirmed,reason,null)
}
private fun migrated(pending:PendingMigration,balance:Amount,provenance:OpeningProvenance,reason:String?,catalogIdentity:String?):StoredCharacter {
    val phase=if(pending.character.creationComplete) RequirementNode.CreationPhase.Phase.Finalized else RequirementNode.CreationPhase.Phase.Creating
    val opening=Opening(legacyMigrationId(pending.origin),pending.origin.sourceFingerprint,balance,phase,provenance,reason,
        LegacyMigrationProvenance(pending.origin,LEGACY_ECONOMY_BASELINE,catalogIdentity,ownershipRecords=legacyOwnershipRecords(pending.character)))
    val state=StoredCharacter(pending.character,pending.extras,pending.instances,Ledger(opening,emptyList()),emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    encodeEnvelope(state) // Fail closed before returning a result that cannot preserve its backup.
    return state
}

internal fun validateLegacyOrigin(origin:LegacyOrigin):DublCharacter {
    require(origin.characterId.isNotBlank() && origin.sourceRevision.isNotBlank() && origin.schema in 1..11)
    require(origin.activePackIds.isNotEmpty() && origin.activePackIds.all(String::isNotBlank) && origin.activePackIds.distinct().size==origin.activePackIds.size)
    val root=JsonReader(origin.raw,strict=true).read() as JsonValue.Obj
    require(!root.has("formatVersion") && root.int("schema")==origin.schema)
    validateLegacySnapshot(root, origin.schema)
    require(root.array("characters").count { it.asObject()?.string("id")==origin.characterId }==1)
    var sequence=0
    val character=SnapshotCodec.decode(origin.raw) { "legacy-generated:"+legacyMigrationId(origin)+":"+(sequence++).toString() }
        .characters.single { it.id==origin.characterId }
    require(character.ruleset==DublRuleset.reference) { "Wrong legacy ruleset" }
    val schoolKeys=character.magic.schools.filter { it.rank>0 }.map { legacySchoolOwnershipKey(it.name) }.toSet()
    require(origin.ownershipMappings.all { (key,ref) -> key in schoolKeys && ref in schoolRefs }) { "Invalid explicit legacy school mapping" }
    require(origin.ownershipMappings.values.distinct().size==origin.ownershipMappings.size) { "Ambiguous duplicate school mapping" }
    return character
}

/** School records have no IDs. Only an explicit caller mapping can establish a canonical school. */
private val schoolRefs = setOf("боевая магия","воплощение","друид","магия крови","молитва","некромантия","ограждение",
    "призыв","природа","прорицание","разрушение","разум","трансмутация").map { DefinitionRef("dubl-3.69","magic-school:$it") }.toSet()
internal fun isLegacySchoolRef(ref:DefinitionRef):Boolean = ref in schoolRefs
fun legacySchoolOwnershipKey(name:String):String = "school:${name.length}:$name"

private fun legacyInstances(c:DublCharacter,origin:LegacyOrigin,catalog:LegacyCatalogSnapshot?):Pair<List<AcquisitionInstance>,List<String>> {
    val instances=mutableListOf<AcquisitionInstance>();val unresolved=mutableListOf<String>()
    val consumedMappings=mutableSetOf<String>()
    fun add(key:String,rank:Int,ref:DefinitionRef?) {
        if(rank<=0)return
        if(ref==null) {unresolved+=key;return}
        val provenance=AcquisitionOrigin.Legacy(origin.characterId,origin.schema.toString(),origin.sourceRevision,origin.sourceFingerprint,key)
        instances+=AcquisitionInstance(legacyInstanceId(ref,provenance),ref,rank,emptyMap(),null,provenance)
    }
    c.development.entries.sortedBy { it.key }.forEach { (id,owned) ->
        if(id!="feat_bf7b04eb68961725") add("development:$id",owned.rank,catalog?.developments?.get(id)?.ref)
    }
    c.skills.entries.sortedBy { it.key }.forEach { (key,skill) ->
        // An explicit source definitionId is authoritative. A renamed display label grants nothing.
        val id=skill.definitionId ?: key.takeIf { catalog!=null && it==skill.id && it in catalog.builtInSkillIds }
        add("skill:$key",skill.rank,id?.takeIf { catalog!=null && it in catalog.skillIds }?.let { DefinitionRef("dubl-3.69",it) })
    }
    add("mana",c.magic.manaRank,DefinitionRef("dubl-3.69","mana:rank"))
    if("dubl-chi-3.69" in origin.activePackIds) add("chi-bonus",c.chiBonusRanks,DefinitionRef("dubl-chi-3.69","chi:bonus-ranks"))
    c.magic.schools.forEach { school ->
        val key=legacySchoolOwnershipKey(school.name);val ref=origin.ownershipMappings[key]
        if(ref!=null){ require(ref in schoolRefs) { "Explicit school mapping is outside the canonical domain" };consumedMappings+=key }
        add(key,school.rank,ref)
    }
    c.magic.spells.filter { it.learned }.forEach { spell ->
        val id=spell.catalogId
        val ref=if(!spell.custom && id!=null && catalog!=null && id in catalog.spellIds) DefinitionRef("dubl-3.69",id) else null
        add("spell:${spell.uid}",1,ref)
    }
    require(origin.ownershipMappings.keys==consumedMappings) { "Unused or unsupported explicit ownership mapping" }
    require(instances.map { it.instanceId }.distinct().size==instances.size)
    return instances to unresolved
}
