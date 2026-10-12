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
private fun fixture(name: String, body: String, canonical: String) {
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
private suspend fun verifyRefusalRetainsQueue(state:StoredCharacter) {
    val original=encodeEnvelope(state)
    for(reason in listOf("Wire payload exceeds bounded server limit","Wire nesting limit exceeded","Ledger/receipt completeness mismatch","Invalid/excessive proof pool","unsupported Unicode escape sequence")) {
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
    System.err.println("PUBLIC_REFUSAL_RETRY_QUEUE_AND_RAW_PRESERVATION_PASS 5 reasons x 2 retries")
}
fun main() = runBlocking {
    println("-- Generated by tools/tests/kotlin/AcquisitionServerFixtureProducer.kt on accepted T15 public APIs.")
    println("CREATE TEMP TABLE acquisition_fixtures (name text PRIMARY KEY, body jsonb NOT NULL, canonical text NOT NULL);")
    println("GRANT SELECT ON acquisition_fixtures TO authenticated;")
    val initial = StoredCharacter(DublCharacter("hero", name = "SQL fixture"),
        CharacterSheetExtras(noteEntries = listOf(CharacterNote("note", "Unicode 😀", "Keep notes; \\ and \n"))),
        emptyList(), Ledger(Opening("synthetic-opening", "synthetic-funding", Amount(100, 0),
            RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.NewCharacter), emptyList()),
        emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
    val op1 = grant(initial, "op1", 0)
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
        } else push(name,grant(state,name+"-op",0),1)
    }
}
