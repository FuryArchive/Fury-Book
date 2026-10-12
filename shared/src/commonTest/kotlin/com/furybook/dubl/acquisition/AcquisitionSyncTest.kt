package com.furybook.dubl.acquisition

import com.furybook.core.cloud.FuryCloudTransport
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AcquisitionSyncTest {
    private fun runTest(block: suspend () -> Unit) = runBlocking { block() }
    private val capabilities = AcquisitionCapabilities(true, true, true)
    private val source = RuleSourceRef("sync-fixture", "1", "Explicit test price")
    private fun catalog() = AcquisitionCatalog(listOf("twenty" to 20L, "eighty" to 80L, "seventy" to 70L, "ten" to 10L).associate { (id, price) ->
        val ref = DefinitionRef("test", id)
        ref to AcquisitionDefinition(ref, AcquisitionKind.DEVELOPMENT, 1, RepeatPolicy.Single,
            RequirementNode.AllOf("none", source, emptyList(), NoRequirementsEvidence(source, "None")),
            PriceRule.Fixed(price, 0, source), SourceStatus.Confirmed, id, source)
    }, "c1", "r1")
    private fun state(id: String = "hero") = StoredCharacter(com.furybook.dubl.model.DublCharacter(id),
        com.furybook.dubl.model.CharacterSheetExtras(), emptyList(), Ledger(Opening("new:hero", "funding", Amount(100, 0),
            RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.NewCharacter), emptyList()),
        emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
    private fun store(state: StoredCharacter = state()) = InMemoryAcquisitionStore(mapOf(state.character.id to encodeEnvelope(state)))
    private suspend fun load(store: AcquisitionStore, id: String = "hero") = assertIs<LoadResult.Loaded>(store.load(id)).state
    private suspend fun buy(store: AcquisitionStore, price: String, op: String, id: String = "hero") {
        val service = AcquisitionService(store, ::catalog, { "$id:$op" })
        val request = AcquisitionRequest(id, listOf(AcquisitionTarget(DefinitionRef("test", price), targetRank = 1)))
        val plan = service.preview(request)
        assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition(op, id, request, plan.baseRevision, plan.previewToken)))
    }
    private class Archive : AcquisitionConflictArchive {
        val saved = mutableListOf<ArchivedAcquisitionConflict>()
        var failure = false
        override suspend fun preserve(conflict: ArchivedAcquisitionConflict): CommitResult {
            if (failure) return CommitResult.StorageFailure("archive unavailable")
            if (conflict !in saved) saved += conflict
            return CommitResult.Committed
        }
    }
    /** Contract fixture: replaces exact paid envelopes and caches ack; never calls gameplay code. */
    private class Server : AcquisitionSyncTransport {
        val revisions=mutableMapOf<String,Long>()
        val canonicals=mutableMapOf<String,String>()
        var revision:Long
            get()=revisions["hero"] ?: 0
            set(value) { revisions["hero"]=value }
        var canonical:String?
            get()=canonicals["hero"]
            set(value) { if(value!=null) canonicals["hero"]=value else canonicals.remove("hero") }
        val seen = mutableMapOf<Pair<String, String>, Pair<String, AcquisitionPushResult.Acknowledged>>()
        var loseReply = false
        var beforeReply: (suspend () -> Unit)? = null
        var resolutionCalls = 0
        override suspend fun push(request: AcquisitionPush): AcquisitionPushResult {
            val key = request.characterId to request.operationId
            seen[key]?.let { (payload, ack) -> return if (payload == request.payloadCanonical) ack else AcquisitionPushResult.IdentityConflict("identity") }
            val current=revisions[request.characterId] ?: 0
            if (request.parentServerRevision != current) return AcquisitionPushResult.Conflict(current, assertNotNull(canonicals[request.characterId]))
            val accepted=current+1
            revisions[request.characterId]=accepted
            canonicals[request.characterId]=request.payloadCanonical
            val ack = AcquisitionPushResult.Acknowledged(request.operationId, request.payloadCanonical, accepted)
            seen[key] = request.payloadCanonical to ack
            beforeReply?.also { beforeReply = null }?.invoke()
            if (loseReply) { loseReply = false; error("timeout after acceptance") }
            return ack
        }
        override suspend fun resolve(request: AcquisitionResolution): AcquisitionPushResult {
            resolutionCalls++
            if (request.expectedServerRevision != revision) return AcquisitionPushResult.Conflict(revision, assertNotNull(canonical))
            revision++
            canonical = request.selectedCanonical
            return AcquisitionPushResult.Acknowledged(request.operationId, request.selectedCanonical, revision)
        }
    }
    private fun sync(store: AcquisitionStore, server: AcquisitionSyncTransport, archive: Archive = Archive(), caps: AcquisitionCapabilities = capabilities) =
        AcquisitionSyncProtocol(store, server, archive, caps, { "resolution" })

    @Test fun lost_response_retries_same_receipt_without_repaying() = runTest {
        val store = store(); buy(store, "twenty", "op1")
        val server = Server().also { it.loseReply = true }
        assertIs<SyncEnvelopeResult.Retryable>(sync(store, server).syncPending("hero"))
        assertEquals(1, load(store).outbox.size)
        val before = load(store)
        assertIs<SyncEnvelopeResult.Synced>(sync(store, server).syncPending("hero"))
        val after = load(store)
        assertEquals(Amount(80, 0), balance(after.ledger)); assertEquals(before.receipts, after.receipts)
        assertEquals(before.revision, after.revision); assertTrue(after.outbox.isEmpty()); assertEquals(1L, server.revision)
        assertIs<UndoEligibility.Available>(AcquisitionUndo(store).eligible("hero"))
    }
    @Test fun ack_races_new_purchase_preserves_price_and_undo() = runTest {
        val store = store(); buy(store, "twenty", "op1")
        val server = Server().also { it.beforeReply = { buy(store, "ten", "op2") } }
        assertIs<SyncEnvelopeResult.Synced>(sync(store, server).syncPending("hero"))
        val after = load(store)
        assertEquals(listOf("op2"), after.outbox.map { it.operationId }); assertEquals(1L, after.outbox.single().parentServerRevision)
        assertEquals(Amount(70, 0), balance(after.ledger)); assertEquals(Amount(10, 0), after.receipts.last().result.cost)
        assertEquals("op2", assertIs<UndoEligibility.Available>(AcquisitionUndo(store).eligible("hero")).operationId)
        assertEquals(2L, after.revision)
        assertIs<SyncEnvelopeResult.Synced>(sync(store, server).syncPending("hero"))
        assertEquals(Amount(70, 0), balance(load(store).ledger)); assertEquals(2L, server.revision)
    }
    @Test fun branches_conflict_preserve_both_then_choose_server_and_repreview() = runTest {
        val local = store(); val remote = store(); buy(local, "eighty", "local"); buy(remote, "seventy", "remote")
        val server = Server(); sync(remote, server).syncPending("hero")
        val archive = Archive(); val protocol = sync(local, server, archive)
        assertIs<SyncEnvelopeResult.Conflict>(protocol.syncPending("hero"))
        assertEquals(Amount(20, 0), balance(load(local).ledger)); assertEquals(1, load(local).outbox.size)
        assertIs<SyncEnvelopeResult.Synced>(protocol.resolveConflict("hero", LocalOrServer.Server, 1))
        assertEquals(1, archive.saved.size)
        assertEquals(Amount(20, 0), balance(decodeSyncEnvelope(archive.saved.single().localCanonical).ledger))
        assertEquals(Amount(30, 0), balance(decodeSyncEnvelope(archive.saved.single().serverCanonical).ledger))
        val chosen = load(local); assertEquals(Amount(30, 0), balance(chosen.ledger)); assertTrue(chosen.outbox.isEmpty())
        assertEquals(2L, chosen.revision); assertIs<UndoEligibility.Unavailable>(AcquisitionUndo(local).eligible("hero"))
        assertFalse(chosen.receipts.any { it.operationId == "local" })
        val preview = AcquisitionService(local, ::catalog, { "fresh" }).preview(AcquisitionRequest("hero", listOf(AcquisitionTarget(DefinitionRef("test", "eighty"), targetRank=1))))
        assertEquals(Amount(-50, 0), preview.remaining); assertFalse(preview.executable)
        assertEquals(Amount(30, 0), balance(load(local).ledger))
    }
    @Test fun archive_failure_and_changed_server_revision_never_accept_branch() = runTest {
        val local = store(); val remote = store(); buy(local, "eighty", "local"); buy(remote, "seventy", "remote")
        val server = Server(); sync(remote, server).syncPending("hero")
        val archive = Archive(); val protocol = sync(local, server, archive)
        protocol.syncPending("hero"); val before = load(local)
        archive.failure = true
        assertIs<SyncEnvelopeResult.StorageFailure>(protocol.resolveConflict("hero", LocalOrServer.Local, 1)); assertEquals(0, server.resolutionCalls)
        assertEquals(before, load(local))
        archive.failure = false; server.revision = 2
        assertIs<SyncEnvelopeResult.Conflict>(protocol.resolveConflict("hero", LocalOrServer.Server, 1))
        assertEquals(Amount(20, 0), balance(load(local).ledger)); assertEquals(1, load(local).outbox.size)
    }
    @Test fun auth_timeout_capability_and_identity_failure_keep_outbox() = runTest {
        val store = store(); buy(store, "twenty", "op1"); val before = load(store)
        var calls = 0
        val transport = object : AcquisitionSyncTransport {
            override suspend fun push(request: AcquisitionPush): AcquisitionPushResult { calls++; error("auth timeout") }
            override suspend fun resolve(request: AcquisitionResolution): AcquisitionPushResult = error("unexpected")
        }
        assertIs<SyncEnvelopeResult.Disabled>(sync(store, transport, caps=AcquisitionCapabilities(true,false,true)).syncPending("hero")); assertEquals(0,calls)
        assertIs<SyncEnvelopeResult.Retryable>(sync(store,transport).syncPending("hero")); assertEquals(before,load(store))
        val bad = object : AcquisitionSyncTransport by transport {
            override suspend fun push(request: AcquisitionPush) = AcquisitionPushResult.Acknowledged(request.operationId, "{}", 1)
        }
        assertIs<SyncEnvelopeResult.IdentityConflict>(sync(store,bad).syncPending("hero")); assertEquals(before,load(store))
    }
    @Test fun copy_preserves_exact_history_drops_outbox_and_imports_once_under_new_scope() = runTest {
        val store = store(); buy(store, "twenty", "op1"); val original=load(store)
        val copied=copyAcquisitionCharacter(original,"copy","import1")
        assertEquals(original.ledger.opening,copied.ledger.opening); assertEquals(original.ledger.entries,copied.ledger.entries.dropLast(1))
        assertEquals(original.receipts,copied.receipts.dropLast(1)); assertEquals(listOf("import1"),copied.outbox.map { it.operationId })
        assertEquals(Amount(80,0),balance(copied.ledger)); assertEquals(0L,copied.syncMetadata.serverRevision)
        assertEquals(CommitResult.Committed,store.compareAndSet("copy",-1,copied))
        buy(store,"ten","op1-new","copy"); assertEquals(Amount(70,0),balance(load(store,"copy").ledger)); assertEquals(original,load(store))
        val next=copyAcquisitionCharacter(load(store,"copy"),"copy2","import2")
        assertEquals(CommitResult.Committed,store.compareAndSet("copy2",-1,next))
        assertEquals(Amount(70,0),balance(load(store,"copy2").ledger)); assertEquals(2,next.copySources.size)
        next.copySources.forEach { assertFalse(it.sourceCanonical.contains("payloadCanonical")); assertFalse(it.sourceCanonical.contains("\"copySources\"")) }
        val encoded=encodeEnvelope(next)
        assertIs<LoadResult.Corrupt>(decodeEnvelope(encoded.replace("\"sourceCharacterId\":\"hero\"","\"sourceCharacterId\":\"forged\"")))
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(next.copy(ledger=next.ledger.copy(opening=next.ledger.opening.copy(balance=Amount(999,0))))) }
    }
    @Test fun shorter_remote_branch_advances_revision_and_future_purchase_can_undo() = runTest {
        val local=store();val remote=store();buy(local,"twenty","op1");buy(local,"ten","op2");buy(local,"seventy","op3")
        buy(remote,"seventy","remote");val server=Server();sync(remote,server).syncPending("hero")
        val protocol=sync(local,server);protocol.syncPending("hero")
        assertIs<SyncEnvelopeResult.Synced>(protocol.resolveConflict("hero",LocalOrServer.Server,1))
        val chosen=load(local);assertEquals(4L,chosen.revision);assertEquals(Amount(30,0),balance(chosen.ledger))
        assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(chosen)))
        val boundary=chosen.receipts.last()
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(chosen.copy(receipts=chosen.receipts.dropLast(1)+boundary.copy(beforeRevision=1,afterRevision=2),revision=2)) }
        val marker=chosen.ledger.entries.last()
        val counterfeit=chosen.copy(character=chosen.character.copy(name="counterfeit"),outbox=emptyList())
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(counterfeit) }
        assertFailsWith<IllegalArgumentException> { encodeSyncEnvelope(counterfeit) }
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(chosen.copy(ledger=chosen.ledger.copy(entries=chosen.ledger.entries.dropLast(1)+marker.copy(evidenceCanonical=marker.evidenceCanonical.replace("sync-adoption-v1","forged"))))) }
        buy(local,"ten","fresh");assertEquals(5L,load(local).revision)
        assertEquals("fresh",assertIs<UndoEligibility.Available>(AcquisitionUndo(local).eligible("hero")).operationId)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(local).undo("hero","undo-fresh",5))
        assertEquals(Amount(30,0),balance(load(local).ledger))
    }
    @Test fun copy_legacy_provenance_and_receipt_prefix_cannot_be_forged() = runTest {
        val character=com.furybook.dubl.model.DublCharacter("hero",development=mapOf("old" to com.furybook.dubl.model.OwnedDevelopment(1)))
        val raw=com.furybook.dubl.data.SnapshotCodec.encode(com.furybook.dubl.model.AppSnapshot(listOf(character),"hero"))
        val origin=LegacyOrigin("hero",11,"7",raw,listOf("dubl-3.69"))
        val migrated=confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin,null)).pending,Amount(100,0),"Known balance")
        val legacy=AcquisitionOrigin.Legacy("hero","11","7",origin.sourceFingerprint,"development:old")
        val ref=DefinitionRef("test","old")
        val held=migrated.copy(instances=listOf(AcquisitionInstance(legacyInstanceId(ref,legacy),ref,1,emptyMap(),null,legacy)))
        val store=store(held);buy(store,"twenty","op1")
        val original=load(store);val copied=copyAcquisitionCharacter(original,"copy","import")
        assertEquals(original.ledger.opening,copied.ledger.opening);assertEquals(original.instances.first().origin,copied.instances.first().origin)
        assertEquals(CommitResult.Committed,store.compareAndSet("copy",-1,copied))
        assertEquals(copied,load(store,"copy"))
        val old=copied.receipts.first()
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(copied.copy(receipts=listOf(old.copy(requestCanonical=old.requestCanonical.replace("twenty","forged")))+copied.receipts.drop(1))) }
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(copied.copy(instances=copied.instances.map { if(it.origin is AcquisitionOrigin.Legacy) it.copy(origin=legacy.copy(characterId="forged")) else it })) }
    }
    @Test fun copied_unresolved_legacy_holding_requires_exact_fresh_destination_confirmation() = runTest {
        val character=com.furybook.dubl.model.DublCharacter("hero",development=mapOf("old" to com.furybook.dubl.model.OwnedDevelopment(1)))
        val raw=com.furybook.dubl.data.SnapshotCodec.encode(com.furybook.dubl.model.AppSnapshot(listOf(character),"hero"))
        val origin=LegacyOrigin("hero",11,"7",raw,listOf("dubl-3.69"))
        val initial=confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin,null)).pending,Amount(100,0),"Known balance")
        val ref=DefinitionRef("test","old")
        val legacy=AcquisitionOrigin.Legacy("hero","11","7",origin.sourceFingerprint,"development:old")
        val instanceId=legacyInstanceId(ref,legacy)
        fun confirmedCatalog(domain:ParameterDomain)=AcquisitionCatalog(mapOf(ref to AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,3,RepeatPolicy.Single,
            RequirementNode.AllOf("none",source,emptyList(),NoRequirementsEvidence(source,"None")),PriceRule.PerRank(20,0,source),SourceStatus.Confirmed,"Old",source,
            parameterDefinitions=listOf(ParameterDefinition("element",domain)))),"fixture","fixture")
        val proven=confirmedCatalog(ParameterDomain.StableIds(setOf("fire")))
        fun command(id:String,revision:Long,op:String)=CanonicalMutation(op,id,revision,MutationPayload.ConfirmLegacyParameter(instanceId,mapOf("element" to "fire"),
            LegacyOwnershipResolution("development:old",ref,1)),"Explicit original holding and parameter")
        val sourceProbe=store(initial)
        assertIs<MutationResult.Applied>(CharacterMutationCoordinator(sourceProbe,{proven},{"unused"}).mutate(command("hero",0,"confirm-source")))
        val originalStore=store(initial)
        val copied=copyAcquisitionCharacter(initial,"copy","import")
        assertEquals(CommitResult.Committed,originalStore.compareAndSet("copy",-1,copied))
        val applied=assertIs<MutationResult.Applied>(CharacterMutationCoordinator(originalStore,{proven},{"unused"}).mutate(command("copy",1,"confirm-copy")))
        assertEquals(Amount.ZERO,applied.receipt.result.cost)
        val saved=load(originalStore,"copy");assertEquals(Amount(100,0),balance(saved.ledger));assertEquals(legacy,saved.instances.single().origin)
        assertEquals(1,saved.instances.single().rank);assertEquals(1,saved.instances.size);assertEquals(initial,load(originalStore))
        assertEquals(saved,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(saved))).state)
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(saved.copy(instances=listOf(saved.instances.single().copy(rank=2)))) }
        val mutation=CharacterMutationCoordinator(originalStore,{proven},{"unused"})
        assertIs<MutationResult.Applied>(mutation.mutate(CanonicalMutation("note-grant","copy",saved.revision,MutationPayload.Grant(Amount.ZERO),"Explicit zero grant")))
        val afterGrant=load(originalStore,"copy")
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(afterGrant.copy(instances=listOf(afterGrant.instances.single().copy(rank=2)),outbox=emptyList())) }
        val acquisition=AcquisitionService(originalStore,{proven},{"unused"})
        val request=AcquisitionRequest("copy",listOf(AcquisitionTarget(ref,instanceId,2,mapOf("element" to "fire"))))
        val preview=acquisition.preview(request)
        val attempt=acquisition.apply(ConfirmAcquisition("advance-copy","copy",request,preview.baseRevision,preview.previewToken))
        val purchase=assertIs<ApplyResult.Applied>(attempt,attempt.toString())
        assertEquals(Amount(20,0),purchase.receipt.result.cost)
        val advanced=load(originalStore,"copy");assertEquals(2,advanced.instances.single().rank);assertEquals(Amount(80,0),balance(advanced.ledger))
        assertEquals(legacy,advanced.instances.single().origin);assertEquals(initial,load(originalStore))
        assertEquals(advanced,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(advanced))).state)
        val unresolved=confirmedCatalog(ParameterDomain.Unresolved("Unknown source domain"))
        for(state in listOf(initial,copied)) {
            val target=store(state)
            val blocked=assertIs<MutationResult.Blocked>(CharacterMutationCoordinator(target,{unresolved},{"unused"}).mutate(command(state.character.id,state.revision,"blocked")))
            assertTrue(blocked.reason.contains("Unknown source domain"));assertEquals(state,load(target,state.character.id))
        }
    }
    @Test fun transport_orders_saved_prefixes_even_when_valid_outbox_bytes_are_reordered() = runTest {
        val store=store();buy(store,"twenty","op1");buy(store,"ten","op2")
        val state=load(store)
        assertEquals(CommitResult.Committed,store.compareAndSet("hero",state.storageRevision,state.copy(storageRevision=state.storageRevision+1,outbox=state.outbox.asReversed())))
        val server=Server()
        assertIs<SyncEnvelopeResult.Synced>(sync(store,server).syncPending("hero"))
        assertEquals(Amount(70,0),balance(decodeSyncEnvelope(assertNotNull(server.canonical)).ledger))
        assertEquals(listOf("op1","op2"),server.seen.keys.map { it.second });assertTrue(load(store).outbox.isEmpty())
    }
    @Test fun operation_identity_conflict_keeps_local_queue_and_copy_receipts_are_character_scoped() = runTest {
        val originalStore=store();buy(originalStore,"twenty","op1")
        val original=load(originalStore);val server=Server()
        assertIs<SyncEnvelopeResult.Synced>(sync(originalStore,server).syncPending("hero"))
        val divergent=store();buy(divergent,"ten","op1");val before=load(divergent)
        assertIs<SyncEnvelopeResult.IdentityConflict>(sync(divergent,server).syncPending("hero"));assertEquals(before,load(divergent))
        for(id in listOf("copy","copy2")) {
            val copied=copyAcquisitionCharacter(original,id,"register")
            val copyStore=store(copied)
            assertIs<SyncEnvelopeResult.Synced>(sync(copyStore,server).syncPending(id))
            assertEquals(Amount(80,0),balance(load(copyStore,id).ledger));assertTrue(load(copyStore,id).outbox.isEmpty())
            buy(copyStore,"ten","new10",id)
            assertIs<SyncEnvelopeResult.Synced>(sync(copyStore,server).syncPending(id))
            assertEquals(Amount(70,0),balance(load(copyStore,id).ledger))
        }
        assertEquals(Amount(80,0),balance(load(originalStore).ledger))
        assertEquals(setOf("hero" to "op1","copy" to "register","copy" to "new10","copy2" to "register","copy2" to "new10"),server.seen.keys)
    }
    @Test fun native_legacy_writes_exclude_migrated_characters_and_force_refuses_them() = runTest {
        val migrated=store();buy(migrated,"twenty","op1")
        val legacy=com.furybook.dubl.model.DublCharacter("legacy")
        var body=""
        val rpc=object:FuryCloudTransport { override suspend fun rpc(function:String,jsonBody:String):String {
            body=jsonBody
            return "{\"profile\":{},\"state\":{},\"characters\":[]}"
        } }
        val api=com.furybook.core.cloud.FuryNativeCloudApi(rpc,migrated)
        api.link(com.furybook.dubl.model.AppSnapshot(listOf(load(migrated).character,legacy),"hero"),emptyMap(),emptySet(),"test")
        assertTrue(body.contains("legacy"));assertFalse(body.contains("hero"));assertFalse(body.contains("op1"))
        val conflict=com.furybook.core.cloud.NativeSyncConflict("hero",1,null,null,null,null,false)
        assertFailsWith<IllegalArgumentException> { api.force(conflict,"{}","{}","test") }
    }
    @Test fun native_bootstrap_preserves_migrated_projection_in_mixed_legacy_result() = runTest {
        val acquisition=store();buy(acquisition,"twenty","op1")
        val hero=load(acquisition);val legacy=com.furybook.dubl.model.DublCharacter("legacy",name="old")
        val characters=com.furybook.dubl.data.InMemoryCharacterStore(com.furybook.dubl.model.AppSnapshot(listOf(hero.character,legacy),"hero"))
        val extras=object:com.furybook.dubl.data.CharacterExtrasStore {
            val values=mutableMapOf("hero" to hero.extras)
            override fun load(characterId:String)=values[characterId] ?: com.furybook.dubl.model.CharacterSheetExtras()
            override fun save(characterId:String,extras:com.furybook.dubl.model.CharacterSheetExtras) { values[characterId]=extras }
            override fun delete(characterId:String) { values.remove(characterId) }
        }
        val metadata=object:com.furybook.core.cloud.NativeCloudMetadataStore {
            var state=com.furybook.core.cloud.NativeCloudMetadata(linkedUserId="user",dirtyIds=setOf("hero","legacy"))
            override fun load()=state
            override fun save(metadata:com.furybook.core.cloud.NativeCloudMetadata) { state=metadata }
            override fun clear() { state=com.furybook.core.cloud.NativeCloudMetadata() }
        }
        val updated=legacy.copy(name="remote")
        val snapshot=com.furybook.dubl.model.AppSnapshot(listOf(hero.character.copy(name="must not overwrite"),updated),"legacy")
        val remote=com.furybook.core.json.jsonObject("profile" to com.furybook.core.json.jsonObject(),"state" to com.furybook.core.json.jsonObject(),
            "characters" to com.furybook.core.json.jsonArray(snapshot.characters.map { character -> com.furybook.core.json.jsonObject(
                "id" to com.furybook.core.json.jsonString(character.id),"data" to com.furybook.core.json.JsonReader(assertNotNull(com.furybook.core.cloud.CloudProtocol.characterJson(snapshot,character.id))).read(),
                "extras" to com.furybook.core.json.jsonObject(),"revision" to com.furybook.core.json.jsonNumber(1)) }))
        val rpc=object:FuryCloudTransport { override suspend fun rpc(function:String,jsonBody:String)=com.furybook.core.json.jsonStringify(
            com.furybook.core.json.jsonObject("bootstrap" to remote,"conflicts" to com.furybook.core.json.jsonArray(emptyList()))) }
        val coordinator=com.furybook.core.cloud.NativeCloudSyncCoordinator("user",characters,extras,metadata,
            com.furybook.core.cloud.FuryNativeCloudApi(rpc,acquisition),{"fresh"},"test",acquisition)
        assertIs<com.furybook.core.cloud.NativeSyncOutcome.Synced>(coordinator.syncNow(emptySet()))
        assertEquals(hero.character,characters.load().characters.single { it.id=="hero" })
        assertEquals(updated,characters.load().characters.single { it.id=="legacy" });assertEquals(hero.extras,extras.load("hero"))
        assertEquals(hero,load(acquisition))
    }
    @Test fun sync_wire_has_no_local_metadata_and_push_cannot_impersonate_resolution() = runTest {
        val store=store();buy(store,"twenty","op1")
        val payload=load(store).outbox.single().payloadCanonical
        assertEquals(Amount(80,0),balance(decodeSyncEnvelope(payload).ledger));assertFalse(payload.contains("outbox"));assertFalse(payload.contains("storageRevision"))
        val request=AcquisitionSyncWire.encodePush(AcquisitionPush("hero","op1",0,payload))
        val root=com.furybook.core.json.JsonReader(request).read() as com.furybook.core.json.JsonValue.Obj
        assertEquals(com.furybook.core.json.JsonValue.Null,root.values["p_resolution"])
        assertFalse("p_choice" in root.values);assertFalse("p_archive_id" in root.values)
        var calls=0
        val rpc=object:FuryCloudTransport { override suspend fun rpc(function:String,jsonBody:String):String { calls++;error("must remain gated") } }
        val transport=AcquisitionRpcTransport(rpc,AcquisitionCapabilities(true,false,true))
        assertFailsWith<IllegalStateException> { transport.push(AcquisitionPush("hero","op1",0,payload)) };assertEquals(0,calls)
    }
}
