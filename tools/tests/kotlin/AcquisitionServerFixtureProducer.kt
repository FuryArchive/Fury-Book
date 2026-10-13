import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import com.furybook.dubl.data.SnapshotCodec
import kotlinx.coroutines.runBlocking

/** Synthetic SQL fixtures generated through the actual public T15 producer, never hand-built proofs. */
private class FixtureStore(initial: StoredCharacter) : AcquisitionStore {
    var state = initial
    override suspend fun load(characterId: String): LoadResult =
        if (characterId == state.character.id) LoadResult.Loaded(state) else LoadResult.Missing(characterId)
    override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
        if (characterId != state.character.id || expectedStorageRevision != state.storageRevision || next.storageRevision != expectedStorageRevision + 1)
            return CommitResult.Conflict
        encodeEnvelope(next)
        state = next
        return CommitResult.Committed
    }
}
// Test-only historical-v1 conversion; the real public codec independently validates the result.
private fun quote(value: String): String = "\""+value.map { c -> when(c) {
    '"' -> "\\\""; '\\' -> "\\\\"; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"; '\b' -> "\\b"; '\u000C' -> "\\f"
    else -> if(c.code<32 || c.isSurrogate()) "\\u"+c.code.toString(16).padStart(4,'0').let { if(c.isSurrogate())it.uppercase()else it } else c.toString()
} }.joinToString("")+"\""
private var fixOnly = false
private fun fixture(name: String, body: String, canonical: String) {
    if (fixOnly && !name.startsWith("fix1-")) return
    check(!body.contains("\$dto\$") && !canonical.contains("\$wire\$"))
    println("INSERT INTO acquisition_fixtures VALUES ('$name', \$dto\$$body\$dto\$::jsonb, \$wire\$$canonical\$wire\$);")
}
private fun push(name: String, state: StoredCharacter, expected: Long) {
    val raw = encodeSyncEnvelope(state)
    val request = AcquisitionPush(state.character.id, state.ledger.entries.last().operationId, expected, raw)
    fixture(name, AcquisitionSyncWire.encodePush(request), raw)
}
private suspend fun grant(state: StoredCharacter, id: String, amount: Long): StoredCharacter {
    val store = FixtureStore(state)
    val mutations = CharacterMutationCoordinator(store, { AcquisitionCatalog(emptyMap(), "synthetic-catalog", "synthetic-resolution") }, { "unused" })
    check(mutations.mutate(CanonicalMutation(id, state.character.id, state.revision, MutationPayload.Grant(Amount(amount, 0)), "Synthetic accounting fixture")) is MutationResult.Applied)
    return store.state
}
private suspend fun resolution(name: String, local: StoredCharacter, server: StoredCharacter, expected: Long, choice: LocalOrServer, capture: (AcquisitionResolution) -> Unit = {}): StoredCharacter {
    val remote = encodeSyncEnvelope(server)
    val store = FixtureStore(local.copy(syncMetadata = SyncMetadata(expected, remote)))
    var captured: AcquisitionResolution? = null
    val transport = object : AcquisitionSyncTransport {
        override suspend fun push(request: AcquisitionPush): AcquisitionPushResult = error("Resolution uses dedicated request")
        override suspend fun resolve(request: AcquisitionResolution): AcquisitionPushResult {
            captured = request
            return AcquisitionPushResult.Acknowledged(request.operationId, request.selectedCanonical, expected + 1)
        }
    }
    val archive = object : AcquisitionConflictArchive {
        override suspend fun preserve(conflict: ArchivedAcquisitionConflict): CommitResult {
            check(conflict.localCanonical == encodeSyncEnvelope(local) && conflict.serverCanonical == remote)
            return CommitResult.Committed
        }
    }
    val protocol = AcquisitionSyncProtocol(store, transport, archive, AcquisitionCapabilities(true, true, true), { name })
    check(protocol.resolveConflict(local.character.id, choice, expected) is SyncEnvelopeResult.Synced)
    val request = requireNotNull(captured)
    capture(request)
    fixture(name, AcquisitionSyncWire.encodeResolution(request), request.selectedCanonical)
    return store.state
}
private suspend fun verifyRefusalRetainsQueue(state:StoredCharacter,reasons:List<String> = listOf("Wire payload exceeds bounded server limit","Wire nesting limit exceeded","Ledger/receipt completeness mismatch","Invalid/excessive proof pool","unsupported Unicode escape sequence")) {
    val original=encodeEnvelope(state)
    for(reason in reasons) {
        val store=FixtureStore(state)
        val transport=object:AcquisitionSyncTransport {
            override suspend fun push(request:AcquisitionPush):AcquisitionPushResult {
                check(request.payloadCanonical==state.outbox.single().payloadCanonical)
                AcquisitionSyncWire.encodePush(request)
                return AcquisitionPushResult.IdentityConflict(reason)
            }
            override suspend fun resolve(request:AcquisitionResolution):AcquisitionPushResult=error("Not a resolution")
        }
        val archive=object:AcquisitionConflictArchive { override suspend fun preserve(conflict:ArchivedAcquisitionConflict):CommitResult=error("No conflict archive expected") }
        val protocol=AcquisitionSyncProtocol(store,transport,archive,AcquisitionCapabilities(true,true,true),{"unused"})
        repeat(2) {
            check(protocol.syncPending(state.character.id)==SyncEnvelopeResult.IdentityConflict(reason))
            check(encodeEnvelope(store.state)==original && store.state.outbox==state.outbox)
        }
    }
    System.err.println("PUBLIC_REFUSAL_RETRY_QUEUE_AND_RAW_PRESERVATION_PASS ${reasons.size} reasons x 2 retries")
}
private suspend fun fix2Fixtures(ackFile:String?=null,refusalFile:String?=null) {
    println("-- Public legacy compatibility fixtures; original raw backups and fingerprints are retained.")
    suspend fun migrated(name:String,fields:String,otherRows:List<String> = emptyList(),sourceRevision:String="fix2-source",literalSurrogate:Boolean=false,ownershipMappings:Map<String,DefinitionRef> = emptyMap()):StoredCharacter {
        var raw="{\"schema\":11,\"activeCharacterId\":"+quote(name)+",\"characters\":[{\"id\":"+quote(name)+","+fields+"}"+(if(otherRows.isEmpty()) "" else ","+otherRows.joinToString(","))+"]}"
        if(literalSurrogate) raw=raw.replace("\\uD800","\uD800")
        val origin=LegacyOrigin(name,11,sourceRevision,raw,listOf("dubl-3.69"),ownershipMappings)
        val prepared=prepareLegacy(raw,origin,null)
        check(prepared is MigrationResult.NeedsBalanceConfirmation) { "Public legacy fixture refused $name: $prepared" }
        return confirmLegacyBalance(prepared.pending,Amount(21,4),"Explicit funding, original history unknown")
    }
    suspend fun emit(name:String,fields:String,otherRows:List<String> = emptyList(),literalSurrogate:Boolean=false) {
        val state=grant(migrated(name,fields,otherRows,literalSurrogate=literalSurrogate),name+"-op",0)
        val wire=encodeSyncEnvelope(state);check(encodeSyncEnvelope(decodeSyncEnvelope(wire))==wire)
        push(name,state,0)
    }
    emit("fix2-ascii-control","\"experience\":\"+0001\",\"xpAdjustment\":\"-0\"")
    emit("fix2-arabic","\"experience\":\"١\"")
    val zeros=(0..65535).filter { Character.digit(it.toChar(),10)==0 }
    check(zeros.all { z -> (0..9).all { Character.digit((z+it).toChar(),10)==it } })
    val digitRows=zeros.mapIndexed { index,z ->
        val digits=(0..9).map { (z+it).toChar() }.joinToString("")
        check(digits.toIntOrNull()==123456789)
        "{\"id\":\"digit-$index\",\"experience\":"+quote(digits)+"}"
    }
    System.err.println("PUBLIC_LEGACY_BMP_DECIMAL_DIGITS ${zeros.size*10} units; zero starts: ${zeros.joinToString(",")}")
    emit("fix2-digits","\"experience\":\"١\"",digitRows)
    val boundaries=listOf("+000٢١٤٧٤٨٣٦٤٧","-२१४७४८३६४८","-０","+0","000000000000000000000000000001","0١2३")
    check(boundaries.map { it.toIntOrNull() }==listOf(Int.MAX_VALUE,Int.MIN_VALUE,0,0,1,123))
    emit("fix2-boundaries","\"experience\":\"+０\",\"xpAdjustment\":\"-٢١٤٧٤٨٣٦٤٨\"",boundaries.mapIndexed { i,v -> "{\"id\":\"boundary-$i\",\"experience\":"+quote(v)+"}" }+"{\"id\":\"integral-number\",\"experience\":1.00E+0,\"xpAdjustment\":-0}")
    fun schools(values:List<Pair<String,Int>>)="\"magic\":{\"schools\":["+values.joinToString(",") { (label,rank) -> "{\"name\":"+quote(label)+",\"rank\":$rank}" }+"]}"
    emit("fix2-school-known",schools(listOf(" боевая " to 1,"Боевая магия" to 2,"молитвы" to 1,"İ" to 1,"i\u0307" to 2,"ΟΣ" to 1,"ος" to 2,"𐐀" to 1,"𐐨" to 2)))
    for((suffix,label) in listOf("nul" to "Null\u0000School","high" to "High\uD800School","low" to "Low\uDC00School")) emit("fix2-school-$suffix",schools(listOf(label to 1)))
    emit("fix2-school-merge",schools(listOf(" боевая " to 2,"Боевая магия" to 3,"İ" to 1,"i\u0307" to 3,"ΟΣ" to 2,"ος" to 2,"Unknown\u0000SCHOOL" to 1,"unknown\u0000school" to 4,"Case\uD800Tie" to 3,"case\uD800tie" to 3)))
    emit("fix2-school-context",schools(listOf("ΑΣ\u0000Α" to 1,"ας\u0000α" to 2,"ΑΣ\uD800Α" to 3,"ας\uD800α" to 3,"𐐀" to 1,"𐐨" to 2,"😀 school" to 1,"-" to 1," \u2003 " to 1)))
    emit("fix2-school-literal",schools(listOf("Literal\uD800School" to 1)),literalSurrogate=true)
    val mappedName="fix2-school-mapped";val mappedLabel="Mapped\u0000\uD800School"
    val mapped=migrated(mappedName,schools(listOf(mappedLabel to 1)),ownershipMappings=mapOf(legacySchoolOwnershipKey(mappedLabel) to DefinitionRef("dubl-3.69","magic-school:боевая магия")))
    val mappedState=grant(mapped,mappedName+"-op",0)
    val mappedWire=encodeSyncEnvelope(mappedState);check(encodeSyncEnvelope(decodeSyncEnvelope(mappedWire))==mappedWire)
    push(mappedName,mappedState,0)
    emit("fix2-identities","\"development\":[{\"id\":"+quote("Dev\u0000\uD800")+",\"rank\":1}],\"skills\":[{\"id\":"+quote("Skill\uDC00")+",\"definitionId\":"+quote("Definition\u0000\uD800")+",\"rank\":1}],\"magic\":{\"spells\":[{\"uid\":"+quote("Spell\u0000\uD800")+",\"catalogId\":"+quote("Catalog\uDC00")+",\"learned\":true}]}")
    emit("fix2-generated","\"customResources\":[{\"uid\":"+quote("Resource\u0000\uD800")+"},{\"name\":\"Generated\"}],\"magic\":{\"spells\":[{\"learned\":true}]},\"gear\":{\"items\":[{}]}",listOf("{\"id\":\"prior-ids\",\"customResources\":[{}],\"magic\":{\"spells\":[{}]},\"gear\":{\"items\":[{}]}}"))
    val name="fix2-confirmed-identities";val entry="Owned\u0000\uD800";val sourceRevision="Revision\u0000\uDC00"
    val state=migrated(name,"\"development\":[{\"id\":"+quote(entry)+",\"rank\":1}]",sourceRevision=sourceRevision)
    val source=RuleSourceRef("synthetic-fixture","1","Synthetic compatibility fixture, never a product oracle")
    val ref=DefinitionRef("synthetic",entry);val parameter="Parameter\u0000\uD800";val value="Value\u0000\uDC00"
    val definition=AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,1,RepeatPolicy.Single,RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None")),PriceRule.Fixed(1,0,source),SourceStatus.Confirmed,"Fixture",source,listOf(ParameterDefinition(parameter,ParameterDomain.UserDefinedIds)))
    val store=FixtureStore(state);val catalog=AcquisitionCatalog(mapOf(ref to definition),"synthetic-catalog","synthetic-resolution")
    val origin=AcquisitionOrigin.Legacy(name,"11",sourceRevision,state.ledger.opening.sourceFingerprint,"development:$entry")
    val instanceId=legacyInstanceId(ref,origin)
    val mutation=CharacterMutationCoordinator(store,{catalog},{"unused"})
    check(mutation.mutate(CanonicalMutation(name+"-op",name,0,MutationPayload.ConfirmLegacyParameter(instanceId,mapOf(parameter to value),LegacyOwnershipResolution("development:$entry",ref,1)),"Explicit original holding confirmation")) is MutationResult.Applied)
    push(name,store.state,0)
    val copied=copyAcquisitionCharacter(store.state,"fix2-confirmed-copy","fix2-copy-import")
    push("fix2-confirmed-copy",copied,0)
    val copied2=copyAcquisitionCharacter(copied,"fix2-confirmed-copy2","fix2-copy2-import")
    push("fix2-confirmed-copy2",copied2,0)
    if(ackFile!=null) {
        val ack=AcquisitionSyncWire.decodeResult(java.io.File(ackFile).readText()) as AcquisitionPushResult.Acknowledged
        val wire=encodeSyncEnvelope(copied2)
        check(ack.payloadCanonical==wire && ack.operationId==copied2.ledger.entries.last().operationId && ack.serverRevision==1L)
        val ackStore=FixtureStore(copied2)
        val transport=object:AcquisitionSyncTransport {
            override suspend fun push(request:AcquisitionPush):AcquisitionPushResult {
                check(request.payloadCanonical==wire && request.operationId==ack.operationId && request.parentServerRevision==0L)
                AcquisitionSyncWire.encodePush(request);return ack
            }
            override suspend fun resolve(request:AcquisitionResolution):AcquisitionPushResult=error("Not a resolution")
        }
        val archive=object:AcquisitionConflictArchive { override suspend fun preserve(conflict:ArchivedAcquisitionConflict):CommitResult=error("No conflict expected") }
        val protocol=AcquisitionSyncProtocol(ackStore,transport,archive,AcquisitionCapabilities(true,true,true),{"unused"})
        check(protocol.syncPending(copied2.character.id) is SyncEnvelopeResult.Synced)
        check(ackStore.state.outbox.isEmpty() && encodeSyncEnvelope(ackStore.state)==wire && ackStore.state.syncMetadata.serverRevision==1L)
        System.err.println("PUBLIC_OBSERVED_SERVER_ACK_BYTE_EXACT_QUEUE_CONSUMPTION_PASS")
        if(refusalFile!=null) verifyRefusalRetainsQueue(copied2,java.io.File(refusalFile).readLines().filter {it.isNotBlank()}.distinct())
    }
}
/** Separately named current T14 producer corpus; historical fixture files remain immutable. */
private suspend fun t14ExactDecimalFixtures(ackFile:String?) {
    val cases=listOf("zero" to 0.0,"negative-zero" to -0.0,"fraction" to 0.1,"tiny" to 1.0E-10,
        "minimum" to Double.MIN_VALUE,"subnormal" to Double.fromBits(2),"maximum" to Double.MAX_VALUE,"large" to 1.0E20)
    val states=cases.associate { (name,value) ->
        val id="t14-fresh-"+name
        val initial=StoredCharacter(DublCharacter(id,name="Fresh exact token; lone \uD800; 😀",gear=CharacterGear(loadManual=value,items=listOf(GearItem("item",load=value)))),
            CharacterSheetExtras(),emptyList(),Ledger(Opening("opening","source",Amount(100,0),RequirementNode.CreationPhase.Phase.Creating,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,RequirementNode.CreationPhase.Phase.Creating)
        id to grant(initial,id+"-grant",0)
    }
    if(ackFile==null) {
        println("-- Separate current fresh producer corpus; do not replace historical expectations.")
        println("CREATE TEMP TABLE acquisition_fixtures (name text PRIMARY KEY, body jsonb NOT NULL, canonical text NOT NULL);")
        println("GRANT SELECT ON acquisition_fixtures TO authenticated;")
        states.forEach { (id,state) -> check(encodeSyncEnvelope(decodeSyncEnvelope(encodeSyncEnvelope(state)))==encodeSyncEnvelope(state));push(id,state,0) }
    } else {
        val replies=java.io.File(ackFile).readLines().filter { it.startsWith("{") }.map { AcquisitionSyncWire.decodeResult(it) as AcquisitionPushResult.Acknowledged }
        check(replies.size==states.size)
        for((id,state) in states) {
            val ack=replies.single { it.payloadCanonical==encodeSyncEnvelope(state) }
            check(ack.operationId==id+"-grant" && ack.serverRevision==1L)
            val store=FixtureStore(state)
            val transport=object:AcquisitionSyncTransport {
                override suspend fun push(request:AcquisitionPush):AcquisitionPushResult { check(request.payloadCanonical==ack.payloadCanonical && request.operationId==ack.operationId);return ack }
                override suspend fun resolve(request:AcquisitionResolution):AcquisitionPushResult=error("No resolution expected")
            }
            val archive=object:AcquisitionConflictArchive { override suspend fun preserve(conflict:ArchivedAcquisitionConflict):CommitResult=error("No archive expected") }
            val protocol=AcquisitionSyncProtocol(store,transport,archive,AcquisitionCapabilities(true,true,true),{"unused"})
            check(protocol.syncPending(id)==SyncEnvelopeResult.Synced(1,0))
            check(store.state.outbox.isEmpty() && store.state.syncMetadata.serverRevision==1L && encodeSyncEnvelope(store.state)==ack.payloadCanonical)
            println("T14_ACTUAL_SQL_ACK_PUBLIC_PROTOCOL_PASS|"+id)
        }
    }
}
fun main(args: Array<String>) = runBlocking {
    if(args.firstOrNull()=="--t14-exact-decimal") { t14ExactDecimalFixtures(args.getOrNull(1));return@runBlocking }
    if (args.firstOrNull() == "--fix-2") { fix2Fixtures(args.getOrNull(1),args.getOrNull(2)); return@runBlocking }
    if (args.firstOrNull() == "--check-admission") {
        val file = java.io.File(args[1])
        var count = 0
        for (row in file.readLines().filter { it.startsWith("INSERT INTO acquisition_fixtures VALUES (") }) {
            val name = row.substringAfter("VALUES ('").substringBefore("'")
            val raw = row.substringAfter("\$wire\$").substringBefore("\$wire\$")
            val failed = runCatching { decodeSyncEnvelope(raw) }.exceptionOrNull()
            check(failed != null) { "Hostile admission fixture unexpectedly accepted: $name" }
            val response = canonicalJson("{\"status\":\"ack\",\"operationId\":\"probe\",\"payloadCanonical\":"+quote(raw)+",\"serverRevision\":\"1\"}")
            check(runCatching { AcquisitionSyncWire.decodeResult(response) }.isFailure)
            println("PUBLIC_ADMISSION_REJECT $name: ${failed.message}"); count++
        }
        check(count > 0); println("PUBLIC_ADMISSION_NEGATIVES_PASS $count")
        return@runBlocking
    }
    fixOnly = args.firstOrNull() == "--fix-1"
    println("-- Generated by tools/tests/kotlin/AcquisitionServerFixtureProducer.kt on accepted T15 public APIs.")
    if (!fixOnly) {
        println("CREATE TEMP TABLE acquisition_fixtures (name text PRIMARY KEY, body jsonb NOT NULL, canonical text NOT NULL);")
        println("GRANT SELECT ON acquisition_fixtures TO authenticated;")
    }
    val initial = StoredCharacter(DublCharacter("hero", name = "SQL fixture"),
        CharacterSheetExtras(noteEntries = listOf(CharacterNote("note", "Unicode 😀", "Keep notes; \\ and \n"))),
        emptyList(), Ledger(Opening("synthetic-opening", "synthetic-funding", Amount(100, 0),
            RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.NewCharacter), emptyList()),
        emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
    val op1 = grant(initial, "op1", 0)
    if (args.firstOrNull() == "--check-refusals") {
        val reasons=java.io.File(args[1]).readLines().filter { it.isNotBlank() }.distinct()
        check(reasons.isNotEmpty()); verifyRefusalRetainsQueue(op1,reasons); return@runBlocking
    }
    push("op1", op1, 0)
    verifyRefusalRetainsQueue(op1)
    push("op1-reused-valid", grant(initial, "op1", 1), 0)
    val acknowledged = op1.copy(outbox = emptyList(), syncMetadata = SyncMetadata(1))
    val local = grant(acknowledged, "op2", -20)
    push("op2", local, 1)
    val remote = grant(acknowledged, "server2", -10)
    push("server2", remote, 1)
    val adopted = resolution("choose-local", local, remote, 2, LocalOrServer.Local)
    var capturedServer: AcquisitionResolution? = null
    val chosenServer = resolution("choose-server", local, remote, 2, LocalOrServer.Server) { capturedServer=it }
    val capturedArchive = requireNotNull(capturedServer).archiveId
    val inlineProof = canonicalJson("{\"schema\":\"sync-adoption-v1\",\"selectedCanonical\":"+quote(encodeSyncEnvelope(remote))+
        ",\"displacedCanonical\":"+quote(encodeSyncEnvelope(local))+",\"serverCanonical\":"+quote(encodeSyncEnvelope(remote))+
        ",\"expectedServerRevision\":\"2\",\"choice\":\"Server\",\"archiveId\":"+quote(capturedArchive)+"}")
    val oldInline = chosenServer.copy(ledger=chosenServer.ledger.copy(entries=chosenServer.ledger.entries.dropLast(1)+chosenServer.ledger.entries.last().copy(evidenceCanonical=inlineProof)),syncProofs=emptyList())
    val oldRaw=encodeSyncEnvelope(oldInline)
    fixture("choose-server-v1",AcquisitionSyncWire.encodeResolution(AcquisitionResolution("hero","choose-server",2,LocalOrServer.Server,
        capturedArchive,oldRaw)),oldRaw)
    val afterAdoption = grant(adopted, "after-adoption", 0)
    push("after-adoption", afterAdoption, 3)
    val remoteAfterAdoption = grant(adopted, "remote-after-adoption", 0)
    push("remote-after-adoption", remoteAfterAdoption, 3)
    resolution("second-choice", afterAdoption, remoteAfterAdoption, 4, LocalOrServer.Server)
    val copied = copyAcquisitionCharacter(adopted, "copy", "copy-import")
    push("copy", copied, 0)
    push("copy2", copyAcquisitionCharacter(copied, "copy2", "copy2-import"), 0)
    val undoStore = FixtureStore(local)
    check(AcquisitionUndo(undoStore).undo("hero", "undo2", local.revision) is ApplyResult.Applied)
    push("undo2", undoStore.state, 2)
    val lexical = initial.copy(character = initial.character.copy(id = "lexical", name = "Lone \uD800; pair 😀",
        gear = CharacterGear(loadAutomatic = false, loadManual = -0.0, items = listOf(GearItem("gear", name = "tiny", load = 1.0E-10)))))
    push("lexical", grant(lexical, "lexical-op", 0), 0)
    val structuredExtras=initial.copy(character=initial.character.copy(id="extras-structured"),extras=CharacterSheetExtras(
        activeConditions=setOf(CharacterConditionId.BLINDNESS),hiddenResourceIds=setOf(CharacterSheetResourceId.CHI),
        preferredSkillAttributes=mapOf("skill" to AttributeId.INTELLIGENCE),
        skillGroups=listOf(SheetGroup("group😀","Group \u0000",listOf("skill"),true)),
        developmentGroups=listOf(SheetGroup("development","Development",emptyList(),false)),
        conditionOverrides=mapOf(CharacterConditionId.BLINDNESS to ConditionLocalOverride("Override 😀 \u0000","Description\n")),
        customConditions=listOf(CustomCondition("custom","Custom 😀 \u0000","Description\t",true))))
    push("extras-structured",grant(structuredExtras,"extras-structured-op",0),0)
    val controls = initial.copy(character=initial.character.copy(id="controls",name="Control \u0000 \u001F \t; lone low \uDC00",
        gear=CharacterGear(items=listOf(GearItem("keys",fields=mapOf("😀" to "\u0000", "\uE000" to "\uD800"))))),
        extras=initial.extras.copy(notes="NUL \u0000; newline \n; tab \t"))
    push("controls",grant(controls,"controls-op",0),0)
    suspend fun mutationFixture(name:String,payload:MutationPayload) {
        val state=initial.copy(character=initial.character.copy(id=name))
        val store=FixtureStore(state)
        val coordinator=CharacterMutationCoordinator(store,{AcquisitionCatalog(emptyMap(),"synthetic-catalog","synthetic-resolution")},{"unused"})
        check(coordinator.mutate(CanonicalMutation(name+"-op",name,0,payload,"Synthetic explicit correction")) is MutationResult.Applied)
        push(name,store.state,0)
    }
    mutationFixture("manual",MutationPayload.ManualCorrection(CharacterPatch(initial.character.copy(id="manual",name="Edited")),Amount(-1,0)))
    mutationFixture("nonfinancial",MutationPayload.NonFinancial(CharacterPatch(initial.character.copy(id="nonfinancial",name="Edited"))))
    mutationFixture("complete",MutationPayload.CompleteCreation)
    val source=RuleSourceRef("synthetic-fixture","1","Synthetic test price, never a product oracle")
    val ref=DefinitionRef("synthetic","purchase")
    val definition=AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,1,RepeatPolicy.Single,
        RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None")),PriceRule.Fixed(1,0,source),SourceStatus.Confirmed,"Fixture",source)
    val catalog=AcquisitionCatalog(mapOf(ref to definition),"synthetic-catalog","synthetic-resolution")
    for(name in listOf("purchase","empty-purchase")) {
        val state=initial.copy(character=initial.character.copy(id=name))
        val store=FixtureStore(state);val service=AcquisitionService(store,{catalog},{"synthetic-instance"})
        val request=AcquisitionRequest(name,if(name=="purchase")listOf(AcquisitionTarget(ref,targetRank=1))else emptyList())
        val preview=service.preview(request);check(preview.executable)
        check(service.apply(ConfirmAcquisition(name+"-op",name,request,0,preview.previewToken)) is ApplyResult.Applied)
        push(name,store.state,0)
    }
    val undoInitial=initial.copy(character=initial.character.copy(id="undo"))
    val undoOriginal=grant(undoInitial,"undo-grant",-1)
    push("undo-original",undoOriginal,0)
    val targetUndo=FixtureStore(undoOriginal.copy(outbox=emptyList(),syncMetadata=SyncMetadata(1)))
    check(AcquisitionUndo(targetUndo).undo("undo","undo-op",1) is ApplyResult.Applied)
    push("undo",targetUndo.state,1)
    for(name in listOf("legacy-migrated","legacy-extras","legacy-confirm")) {
        val ch=DublCharacter(name,experience=100,development=if(name=="legacy-confirm")mapOf("purchase" to OwnedDevelopment(1))else emptyMap())
        val raw=" \n"+SnapshotCodec.encode(AppSnapshot(listOf(ch),name))+"\n"
        val origin=LegacyOrigin(name,11,"1",raw,listOf("dubl-3.69"),extrasSource=if(name=="legacy-extras")LegacyExtrasSource("{\"notes\":\"synthetic\"}")else null)
        val pending=(prepareLegacy(raw,origin,null) as MigrationResult.NeedsBalanceConfirmation).pending
        val state=confirmLegacyBalance(pending,Amount(100,0),"Synthetic known opening, history unknown")
        if(name=="legacy-confirm") {
            val target=FixtureStore(state)
            val origin=AcquisitionOrigin.Legacy(name,"11","1",state.ledger.opening.sourceFingerprint,"development:purchase")
            val instanceId=legacyInstanceId(ref,origin)
            val coordinator=CharacterMutationCoordinator(target,{catalog},{"unused"})
            check(coordinator.mutate(CanonicalMutation(name+"-op",name,0,MutationPayload.ConfirmLegacyParameter(instanceId,emptyMap(),LegacyOwnershipResolution("development:purchase",ref,1)),"Synthetic holding confirmation")) is MutationResult.Applied)
            push(name,target.state,1)
            if (fixOnly) {
                val copy=copyAcquisitionCharacter(target.state,"fix1-legacy-copy","fix1-copy-import")
                push("fix1-legacy-copy",copy,0)
                push("fix1-legacy-copy2",copyAcquisitionCharacter(copy,"fix1-legacy-copy2","fix1-copy2-import"),0)
                val paidStore=FixtureStore(copy.copy(outbox=emptyList(),syncMetadata=SyncMetadata(1)))
                val paidCatalog=AcquisitionCatalog(mapOf(ref to definition.copy(maxRank=3,priceRule=PriceRule.PerRank(1,0,source))),"synthetic-catalog","synthetic-resolution")
                val paidService=AcquisitionService(paidStore,{paidCatalog},{"unused"})
                val paidRequest=AcquisitionRequest(copy.character.id,listOf(AcquisitionTarget(ref,instanceId,targetRank=2)))
                val paidPreview=paidService.preview(paidRequest); check(paidPreview.executable) { "Synthetic copied rank fixture blocked: ${paidPreview.issues.map { it.reason }}" }
                check(paidService.apply(ConfirmAcquisition("fix1-paid",copy.character.id,paidRequest,copy.revision,paidPreview.previewToken)) is ApplyResult.Applied)
                push("fix1-legacy-paid",paidStore.state,1)
                val paidUndo=FixtureStore(paidStore.state.copy(outbox=emptyList(),syncMetadata=SyncMetadata(2)))
                check(AcquisitionUndo(paidUndo).undo(copy.character.id,"fix1-paid-undo",paidUndo.state.revision) is ApplyResult.Applied)
                push("fix1-legacy-paid-undo",paidUndo.state,2)
                val removal=FixtureStore(copy.copy(outbox=emptyList(),syncMetadata=SyncMetadata(1)))
                val edit=CharacterMutationCoordinator(removal,{catalog},{"unused"})
                check(edit.mutate(CanonicalMutation("fix1-remove",copy.character.id,copy.revision,
                    MutationPayload.ManualCorrection(CharacterPatch.fromCanonicalState(removal.state,instances=emptyList()),Amount.ZERO),"Explicit copied holding removal")) is MutationResult.Applied)
                push("fix1-legacy-remove",removal.state,1)
                val removed=removal.state.copy(outbox=emptyList(),syncMetadata=SyncMetadata(2))
                val restore=FixtureStore(removed)
                check(AcquisitionUndo(restore).undo(copy.character.id,"fix1-restore",removed.revision) is ApplyResult.Applied)
                push("fix1-legacy-restore",restore.state,2)
                val unresolvedCopy=copyAcquisitionCharacter(state,"fix1-copied-confirm","fix1-confirm-import")
                push("fix1-copied-unresolved",unresolvedCopy,0)
                val confirmation=FixtureStore(unresolvedCopy.copy(outbox=emptyList(),syncMetadata=SyncMetadata(1)))
                val confirm=CharacterMutationCoordinator(confirmation,{catalog},{"unused"})
                check(confirm.mutate(CanonicalMutation("fix1-copied-confirm-op",unresolvedCopy.character.id,unresolvedCopy.revision,
                    MutationPayload.ConfirmLegacyParameter(instanceId,emptyMap(),LegacyOwnershipResolution("development:purchase",ref,1)),"Explicit copied holding confirmation")) is MutationResult.Applied)
                push("fix1-copied-confirm",confirmation.state,1)
            }
        } else push(name,grant(state,name+"-op",0),1)
    }
    if (fixOnly) {
        val entry=DevelopmentEntry("entry","Manual","","",-3,DevelopmentCostType.XP,-2,"","","",listOf("tag","tag"),null,
            listOf(AbilityOption("Manual",-1)),false,false,false,"","")
        val manual=initial.copy(ledger=initial.ledger.copy(opening=initial.ledger.opening.copy(reason="Reason \u0000 lone \uD800 pair 😀")),character=initial.character.copy(id="fix1-manual",experience=Int.MIN_VALUE,hpCurrent=Int.MAX_VALUE,
            skills=linkedMapOf(" padded " to CharacterSkill(" padded ",rank=-4,attributes=listOf(AttributeId.WILL,AttributeId.WILL))),
            hiddenSkillIds=linkedSetOf(" z ","a"),disabledSkillEffectIds=linkedSetOf("z","a"),
            development=linkedMapOf("manual" to OwnedDevelopment(-3,-2)),developmentOverrides=linkedMapOf("entry" to entry),customDevelopmentEntries=listOf(entry),
            customResources=listOf(CustomResource("resource",current=-1,maximum=-2)),
            gear=CharacterGear(loadAutomatic=false,loadManual=-0.0,items=listOf(GearItem("item",load=1.0E-10)))))
        val state=grant(manual,"fix1-manual-op",0); decodeSyncEnvelope(encodeSyncEnvelope(state)); push("fix1-manual",state,0)
        val oldRaw="""{"schema":1,"characters":[{"id":"fix1-old","ruleset":{"id":"\u2003","version":" "},"skills":[{"id":"z","definitionId":"skill-z","rank":"12"},{"id":"a","rank":2}],"development":[{"id":" z ","rank":"3"},{"id":"a","rank":2},{"id":"feat_bf7b04eb68961725","rank":4}],"customDevelopmentEntries":[{"id":"a"}],"chiBonusRanks":12,"magic":{"schools":[{"name":" боевая ","level":2},{"name":"Боевая магия","rank":3}],"spells":[{"uid":"spell","learned":"true","catalogId":"spell-id"},{"learned":true,"custom":true}]}}]}"""
        val oldOrigin=LegacyOrigin("fix1-old",1,"old-revision",oldRaw,listOf("dubl-3.69","dubl-chi-3.69"),
            mapOf(legacySchoolOwnershipKey("Боевая магия") to DefinitionRef("dubl-3.69","magic-school:боевая магия")))
        val result=prepareLegacy(oldRaw,oldOrigin,null)
        check(result is MigrationResult.NeedsBalanceConfirmation) { "Legacy fixture preparation failed: $result" }
        val pending=result.pending
        val oldState=confirmLegacyBalance(pending,Amount(13,7),"Retain unknown history \u0000 lone \uDC00 pair 😀")
        push("fix1-old",grant(oldState,"fix1-old-op",0),1)
    }
}
