package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class AcquisitionServiceTest {
    private val source = RuleSourceRef("service-fixture", "1", "Explicit admission and price")
    private fun ref(id: String) = DefinitionRef("test", id)
    private fun none() = RequirementNode.AllOf("none", source, emptyList(), NoRequirementsEvidence(source, "No prerequisites"))
    private fun definition(id: String = "target", price: Long = 20, need: RequirementNode = none(), kind: AcquisitionKind = AcquisitionKind.DEVELOPMENT) =
        AcquisitionDefinition(ref(id), kind, 1, RepeatPolicy.Single, need, PriceRule.Fixed(price, 0, source), SourceStatus.Confirmed, id, source)
    private fun catalog(vararg definitions: AcquisitionDefinition, version: String = "c1") = AcquisitionCatalog(definitions.associateBy { it.ref }, version, "r1")
    private fun state(id: String = "hero") = StoredCharacter(DublCharacter(id), CharacterSheetExtras(), emptyList(),
        Ledger(Opening("new:$id", "funding", Amount(100, 0), RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.NewCharacter), emptyList()),
        emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
    private fun store(state: StoredCharacter = state()) = InMemoryAcquisitionStore(mapOf(state.character.id to encodeEnvelope(state)))
    private fun request(id: String = "target", characterId: String = "hero") = AcquisitionRequest(characterId, listOf(AcquisitionTarget(ref(id), targetRank = 1)))
    private fun service(store: AcquisitionStore, catalog: AcquisitionCatalog = catalog(definition())) = AcquisitionService(store, { catalog }, { "generated" })
    private suspend fun command(service: AcquisitionService, id: String = "op1", request: AcquisitionRequest = request()): ConfirmAcquisition {
        val preview = service.preview(request)
        return ConfirmAcquisition(id, request.characterId, request, preview.baseRevision, preview.previewToken)
    }
    private suspend fun loaded(store: AcquisitionStore) = assertIs<LoadResult.Loaded>(store.load("hero")).state
    private fun run(block: suspend () -> Unit) {
        var completion: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result }
        })
        checkNotNull(completion) { "Uncompleted test coroutine" }.getOrThrow()
    }

    @Test fun double_tap_one_receipt() = run {
        val store = store(); val service = service(store); val cmd = command(service)
        val first = assertIs<ApplyResult.Applied>(service.apply(cmd))
        val second = assertIs<ApplyResult.AlreadyApplied>(service(store).apply(cmd))
        assertEquals(first.receipt, second.receipt)
        val saved = loaded(store)
        assertEquals(Amount(80, 0), balance(saved.ledger)); assertEquals(1, saved.instances.size)
        assertEquals(1, saved.ledger.entries.size); assertEquals(1, saved.receipts.size); assertEquals(1, saved.outbox.size)
        assertEquals(1L, saved.revision); assertEquals(encodeSyncEnvelope(saved), saved.outbox.single().payloadCanonical)
    }

    @Test fun stale_cheaper_still_reconfirm() = run {
        val store = store(); var catalog = catalog(definition())
        var calls = 0
        val service = AcquisitionService(store, { calls++; catalog }, { "id" })
        val cmd = command(service)
        catalog = catalog(definition(price = 15), version = "c2")
        val stale = assertIs<ApplyResult.Stale>(service.apply(cmd))
        assertEquals(Amount(15, 0), stale.newPlan.cost); assertEquals(2, calls)
        assertEquals(state(), loaded(store))
    }

    @Test fun save_failure_no_publish() = run {
        val backing = store()
        val faulty = object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter) = CommitResult.StorageFailure("disk full")
        }
        val service = service(faulty)
        assertIs<ApplyResult.StorageFailure>(service.apply(command(service)))
        assertEquals(state(), loaded(backing))
    }

    @Test fun lost_reply_after_commit() = run {
        val backing = store()
        val faulty = object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                assertEquals(CommitResult.Committed, backing.compareAndSet(characterId, expectedStorageRevision, next))
                throw IllegalStateException("lost reply")
            }
        }
        val service = service(faulty); val cmd = command(service)
        assertIs<ApplyResult.StorageFailure>(service.apply(cmd))
        assertIs<ApplyResult.AlreadyApplied>(service(backing).apply(cmd))
        assertEquals(Amount(80, 0), balance(loaded(backing).ledger)); assertEquals(1, loaded(backing).receipts.size)
    }

    @Test fun wrong_character_token_and_reused_id_conflict() = run {
        val a = service(store()); val cmd = command(a)
        val bStore = store(state("other")); val b = service(bStore)
        assertIs<ApplyResult.IdentityConflict>(b.apply(cmd.copy(characterId = "other", request = request(characterId = "other"))))
        assertEquals(state("other"), assertIs<LoadResult.Loaded>(bStore.load("other")).state)
        assertIs<ApplyResult.Applied>(a.apply(cmd))
        assertIs<ApplyResult.IdentityConflict>(a.apply(cmd.copy(request = request("different"))))
        assertIs<ApplyResult.IdentityConflict>(a.apply(cmd.copy(previewToken = "{}")))
    }

    @Test fun metadata_conflict_retries_same_plan_but_gameplay_conflict_is_stale() = run {
        val backing = store(); var attempted = false
        val racing = object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                if (!attempted) {
                    attempted = true
                    val old = loaded(backing)
                    assertEquals(CommitResult.Committed, backing.compareAndSet(characterId, old.storageRevision,
                        old.copy(storageRevision = old.storageRevision + 1, syncMetadata = SyncMetadata(9))))
                }
                return backing.compareAndSet(characterId, expectedStorageRevision, next)
            }
        }
        val service = service(racing)
        assertIs<ApplyResult.Applied>(service.apply(command(service)))
        val saved = loaded(backing)
        assertEquals(2L, saved.storageRevision); assertEquals(1L, saved.revision)
        assertEquals(9L, saved.outbox.single().parentServerRevision); assertEquals(Amount(80,0), balance(saved.ledger))
    }

    @Test fun concurrent_revision_seven_only_one_purchase() = run {
        val backing = store(); val setup = service(backing)
        repeat(7) { n ->
            val empty = AcquisitionRequest("hero", emptyList())
            assertIs<ApplyResult.Applied>(setup.apply(command(setup, "empty$n", empty)))
        }
        var arrivals = 0; var commits = 0
        val both = CompletableDeferred<Unit>()
        val racing = object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                arrivals++; if (arrivals == 2) both.complete(Unit)
                both.await()
                return backing.compareAndSet(characterId, expectedStorageRevision, next).also { if (it == CommitResult.Committed) commits++ }
            }
        }
        val one = service(racing); val two = service(racing)
        val c1 = command(one); val c2 = command(two, "op2")
        coroutineScope {
            val first = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { one.apply(c1) }
            val second = async(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { two.apply(c2) }
            val outcomes = listOf(first.await(), second.await())
            assertEquals(1, outcomes.count { it is ApplyResult.Applied }); assertEquals(1, outcomes.count { it is ApplyResult.Stale })
        }
        assertEquals(1, commits); assertEquals(8L, loaded(backing).revision)
        assertEquals(Amount(80,0), balance(loaded(backing).ledger))
    }

    @Test fun no_op_is_durable_nonfinancial_never_paid_purchase() = run {
        val backing = store(); val service = service(backing)
        assertIs<ApplyResult.Applied>(service.apply(command(service)))
        val cmd = command(service, "noop")
        assertIs<ApplyResult.Applied>(service.apply(cmd)); assertIs<ApplyResult.AlreadyApplied>(service.apply(cmd))
        val saved = loaded(backing); val entry = saved.ledger.entries.last()
        assertEquals(LedgerEntryType.NonFinancial, entry.type); assertEquals(Amount.ZERO, entry.amountDelta)
        assertTrue(entry.changes.isEmpty()); assertEquals(2L, saved.revision); assertEquals(Amount(80,0), balance(saved.ledger))
        assertIs<ApplyResult.IdentityConflict>(service.apply(cmd.copy(request = request("other"))))
    }

    @Test fun postcondition_failure_atomic_and_forged_token_cannot_skip_and_tail() = run {
        val need = RequirementNode.AllOf("and", source, listOf(none(), RequirementNode.Unresolved("combat-tail", source, "Unconfirmed tail")))
        val backing = store(); val blocked = service(backing, catalog(definition(need = need)))
        val cmd = command(blocked)
        assertIs<ApplyResult.Blocked>(blocked.apply(cmd))
        val legal = service(backing); val forged = command(legal)
        assertIs<ApplyResult.Stale>(blocked.apply(forged))
        assertEquals(state(), loaded(backing))
    }

    @Test fun learned_spell_presentation_and_source_evidence_survive_reload() = run {
        val learned = RequirementNode.LearnedSpell("spell-needed", source, "test", "spell")
        val c = catalog(definition("spell", 15, kind = AcquisitionKind.SPELL), definition(need = learned))
        val original = state().copy(character = state().character.copy(magic = CharacterMagic(spells = listOf(KnownSpell("old-spell", "spell", learned = false))),
            skills = mapOf("manual" to CharacterSkill("manual", rank=12))))
        val backing = store(original); val service = service(backing, c)
        val receipt = assertIs<ApplyResult.Applied>(service.apply(command(service))).receipt
        assertEquals(Amount(35,0), receipt.result.cost)
        val saved = loaded(backing)
        assertEquals(Amount(65,0), balance(saved.ledger)); assertEquals(12, saved.character.skills.getValue("manual").rank)
        assertTrue(ref("spell") in acquisitionContext(saved,c).learnedSpellIds)
        assertEquals(1, saved.character.magic.spells.size); assertTrue(saved.character.magic.spells.single().learned)
        assertEquals(setOf(ref("spell"),ref("target")), saved.instances.map { it.definition }.toSet())
        val evidence = saved.ledger.entries.single().evidenceCanonical
        assertTrue(evidence.contains("service-fixture")); assertTrue(evidence.contains("Creating")); assertTrue(evidence.contains("prices"))
    }

    @Test fun unavailable_load_preserves_state_and_never_creates_character() = run {
        for (result in listOf(LoadResult.Missing("hero"), LoadResult.MigrationRequired("old"), LoadResult.Unsupported("future","version"), LoadResult.Corrupt("broken","bad"))) {
            val store = object : AcquisitionStore {
                override suspend fun load(characterId: String) = result
                override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult = error("Must not write")
            }
            val service = service(store)
            try { service.preview(request()); fail("Expected unavailable preview") } catch (e: AcquisitionPreviewUnavailable) { assertEquals(result,e.loadResult) }
            assertIs<ApplyResult.Blocked>(service.apply(ConfirmAcquisition("op", "hero", request(), 0, "{}")))
        }
    }
    @Test fun final_checks_protect_existing_exclusions_but_preserve_unrelated_manual_violations() = run {
        val conflict = RequirementNode.Exclusive("exclude-target", source,
            RequirementNode.OwnedRank("target-held", source, "test", "target", ComparisonOperator.GE, 1))
        val unknown = RequirementNode.Unresolved("old-unknown",source,"Old manual exception")
        val original = state().copy(instances = listOf(AcquisitionInstance("old", ref("old"),1,emptyMap(),null,AcquisitionOrigin.New)))
        for (need in listOf(conflict, RequirementNode.AllOf("old-and",source,listOf(unknown,conflict)))) {
            val backing = store(original); val service = service(backing,catalog(definition(),definition("old",need=need)))
            assertIs<ApplyResult.Blocked>(service.apply(command(service)))
            assertEquals(original,loaded(backing))
        }
        val backing = store(original); val service = service(backing,catalog(definition(),definition("old",need=unknown)))
        assertIs<ApplyResult.Applied>(service.apply(command(service)))
        assertTrue(loaded(backing).instances.any { it.instanceId == "old" })
        assertEquals(Amount(80,0),balance(loaded(backing).ledger))
    }

    @Test fun metadata_retry_rechecks_catalog_and_never_accepts_new_price() = run {
        val backing = store(); var c = catalog(definition())
        val racing = object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId: String, expectedStorageRevision: Long, next: StoredCharacter): CommitResult {
                val current = loaded(backing)
                assertEquals(CommitResult.Committed,backing.compareAndSet(characterId,current.storageRevision,current.copy(storageRevision=current.storageRevision+1)))
                c = catalog(definition(price=15), version="changed")
                return CommitResult.Conflict
            }
        }
        val service = AcquisitionService(racing,{ c }, { "id" })
        assertIs<ApplyResult.Stale>(service.apply(command(service)))
        assertEquals(Amount(100,0),balance(loaded(backing).ledger)); assertTrue(loaded(backing).receipts.isEmpty())
    }

    @Test fun retries_of_undone_receipt_check_original_payload_before_status() = run {
        val backing = store(); val service = service(backing); val cmd = command(service)
        val receipt = assertIs<ApplyResult.Applied>(service.apply(cmd)).receipt
        val saved = loaded(backing)
        val reverse = LedgerEntry("undo",LedgerEntryType.Undo,emptyList(),Amount(20,0),RuleVersions("c1","r1"),
            reversesId="op1",creationPhase=saved.creationPhase)
        val undoReceipt = Receipt("undo","{\"characterId\":\"hero\"}",SavedAcquisitionResult(Amount(-20,0),Amount(100,0),emptyList()),ReceiptStatus.Applied,1,2)
        val undone = saved.copy(character=state().character,instances=emptyList(),ledger=saved.ledger.copy(entries=saved.ledger.entries+reverse),
            receipts=listOf(receipt.copy(status=ReceiptStatus.Undone),undoReceipt),outbox=emptyList(),revision=2,storageRevision=2)
        assertEquals(CommitResult.Committed,backing.compareAndSet("hero",1,undone))
        assertIs<ApplyResult.AlreadyUndone>(service.apply(cmd))
        assertIs<ApplyResult.IdentityConflict>(service.apply(cmd.copy(request=request("other"))))
        assertEquals(undone,loaded(backing))
    }

    @Test fun external_confirmation_and_conditional_price_evidence_are_durable() = run {
        val permission = RequirementNode.ExternalEvidence("permission",source,"training","hero")
        val phase = RequirementNode.CreationPhase("creating",source,RequirementNode.CreationPhase.Phase.Creating)
        val d = definition(need=permission).copy(priceRule=PriceRule.Conditional(phase,PriceRule.Fixed(15,0,source),PriceRule.Fixed(20,0,source),source))
        val confirmation = RequirementWitness.External("training","hero","confirmed-by-player",source)
        val req = AcquisitionRequest("hero",request().targets,externalConfirmations=listOf(confirmation))
        val backing = store(); val service = service(backing,catalog(d))
        assertIs<ApplyResult.Applied>(service.apply(command(service,request=req)))
        val saved = loaded(backing)
        assertEquals(Amount(85,0),balance(saved.ledger)); assertEquals(listOf(confirmation),retainedAcquisitionConfirmations(saved))
        assertTrue(saved.ledger.entries.single().evidenceCanonical.contains("discountEvidence"))
        assertTrue(saved.ledger.entries.single().evidenceCanonical.contains("creating"))
        assertTrue(service.preview(request()).executable)
    }

    @Test fun canonical_facade_never_publishes_into_legacy_sessions() = run {
        val backing = store(); val service = service(backing)
        val legacy = com.furybook.dubl.data.InMemoryCharacterStore(AppSnapshot(listOf(DublCharacter("legacy")),"legacy"))
        val extras = object : com.furybook.dubl.data.CharacterExtrasStore {
            override fun load(characterId: String) = CharacterSheetExtras()
            override fun save(characterId: String, extras: CharacterSheetExtras) = error("Unexpected legacy extras write")
        }
        val app = com.furybook.dubl.application.DublApplication(legacy,extras,{ "legacy-id" },acquisitionService=service)
        val before = legacy.load()
        assertEquals("generated",service.newOperationId())
        val preview = app.development.previewAcquisition(request())
        assertEquals(Amount(20,0),preview.cost)
        assertIs<ApplyResult.Applied>(app.development.confirmAcquisition(ConfirmAcquisition("facade", "hero",request(),preview.baseRevision,preview.previewToken)))
        assertEquals(before,legacy.load()); assertEquals("legacy",app.active.id); assertFalse(app.canUndo)
        assertEquals(Amount(80,0),balance(loaded(backing).ledger))
    }

    @Test fun presentation_identity_collision_is_atomic() = run {
        val original = state().copy(character=state().character.copy(skills=mapOf("explicit" to CharacterSkill("explicit","unrelated","Manual",rank=12))))
        val backing=store(original); val service=service(backing,catalog(definition(kind=AcquisitionKind.SKILL)))
        val req=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref("target"),"explicit",1)))
        assertIs<ApplyResult.Blocked>(service.apply(command(service,request=req)))
        assertEquals(original,loaded(backing))
    }

    @Test fun same_service_serializes_same_character_without_blocking_another() = run {
        val backing=InMemoryAcquisitionStore(mapOf("hero" to encodeEnvelope(state()),"other" to encodeEnvelope(state("other"))))
        val release=CompletableDeferred<Unit>(); var heroCommits=0
        val gated=object : AcquisitionStore by backing {
            override suspend fun compareAndSet(characterId:String,expectedStorageRevision:Long,next:StoredCharacter):CommitResult {
                if(characterId=="hero") { heroCommits++; release.await() }
                return backing.compareAndSet(characterId,expectedStorageRevision,next)
            }
        }
        val service=service(gated); val cmd=command(service)
        val other=command(service,"other-op",request(characterId="other"))
        coroutineScope {
            val first=async(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED) { service.apply(cmd) }
            val duplicate=async(Dispatchers.Unconfined,start=CoroutineStart.UNDISPATCHED) { service.apply(cmd) }
            assertEquals(1,heroCommits)
            assertIs<ApplyResult.Applied>(service.apply(other))
            release.complete(Unit)
            assertIs<ApplyResult.Applied>(first.await()); assertIs<ApplyResult.AlreadyApplied>(duplicate.await())
        }
        assertEquals(1,heroCommits); assertEquals(Amount(80,0),balance(loaded(backing).ledger))
    }

    @Test fun inverse_evidence_preserves_absent_unlearned_and_rank_zero_presentations() = run {
        for (existing in listOf(false,true)) {
            val before = state().copy(character=state().character.copy(
                magic=CharacterMagic(spells=if(existing) listOf(KnownSpell("old-spell","spell",learned=false,description="retained")) else emptyList()),
                development=if(existing) mapOf("target" to OwnedDevelopment(0)) else emptyMap()))
            val backing=store(before); val c=catalog(definition("spell",kind=AcquisitionKind.SPELL),definition())
            val service=service(backing,c)
            val req=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref("spell"),targetRank=1),AcquisitionTarget(ref("target"),targetRank=1)))
            assertIs<ApplyResult.Applied>(service.apply(command(service,request=req)))
            val after=loaded(backing)
            val proof=assertNotNull(decodeAcquisitionUndoEvidence(after.ledger.entries.single().evidenceCanonical))
            assertEquals(acquisitionProjectionCanonical(before),proof.beforeCanonical)
            assertEquals(acquisitionProjectionCanonical(after),proof.afterCanonical)
            val fields=(com.furybook.core.json.JsonReader(proof.beforeCanonical).read() as com.furybook.core.json.JsonValue.Obj).values
            assertEquals(setOf("characterSnapshot","instances"),fields.keys)
            assertEquals(existing,proof.beforeCanonical.contains("old-spell"))
            assertEquals(existing,before.character.development.containsKey("target"))
            assertFalse(proof.beforeCanonical.contains("ledger")); assertFalse(proof.beforeCanonical.contains("receipts"))
        }
        assertNull(decodeAcquisitionUndoEvidence("{}"))
        assertNull(decodeAcquisitionUndoEvidence("{\"schema\":\"future\"}"))
    }

    @Test fun ordered_transitions_of_one_instance_check_final_rank_and_exact_total() = run {
        for(kind in listOf(AcquisitionKind.DEVELOPMENT,AcquisitionKind.SKILL)) {
        val d=definition(kind=kind).copy(maxRank=2,priceRule=PriceRule.PerRank(10,0,source))
        val backing=store();val service=service(backing,catalog(d))
        val req=AcquisitionRequest("hero",listOf(AcquisitionTarget(ref("target"),targetRank=1),AcquisitionTarget(ref("target"),targetRank=2)))
        val preview=service.preview(req)
        assertTrue(preview.executable,preview.issues.toString());assertEquals(2,preview.changes.size)
        val result=assertIs<ApplyResult.Applied>(service.apply(command(service,request=req)))
        assertEquals(Amount(20,0),result.receipt.result.cost)
        val saved=loaded(backing)
        assertEquals(2,saved.instances.single().rank)
        if(kind==AcquisitionKind.DEVELOPMENT) assertEquals(2,saved.character.development.getValue("target").rank)
        else assertEquals(2,saved.character.skills.values.single().rank)
        assertEquals(Amount(80,0),balance(saved.ledger))
        }
    }

}
