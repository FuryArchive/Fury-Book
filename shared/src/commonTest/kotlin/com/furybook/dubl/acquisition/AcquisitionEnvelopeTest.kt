package com.furybook.dubl.acquisition

import com.furybook.core.json.*
import com.furybook.dubl.data.CharacterTransferCodec
import com.furybook.dubl.data.CharacterTransferDecodeResult
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.*
import kotlin.coroutines.*
import kotlin.test.*

class AcquisitionEnvelopeTest {
    private fun opening(amount: Amount = Amount(100, 2)) = Opening("migration:c", "original-bytes", amount,
        RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.LegacyCalculated)
    private fun state(amount: Amount = Amount(100, 2)) = StoredCharacter(
        character = DublCharacter("c"), extras = CharacterSheetExtras(portraitUri = "local://portrait",
            noteEntries = listOf(CharacterNote("n", "Title", "Body"))),
        instances = emptyList(), ledger = Ledger(opening(amount), emptyList()), receipts = emptyList(),
        outbox = emptyList(), syncMetadata = SyncMetadata(7), revision = 0, storageRevision = 0,
        creationPhase = RequirementNode.CreationPhase.Phase.Creating)
    private fun purchased(): StoredCharacter {
        val change = AcquisitionChange(AcquisitionKind.SKILL, DefinitionRef("pack", "skill"), "i", 0, 1, emptyMap(), null)
        val entry = LedgerEntry("buy", LedgerEntryType.Purchase, listOf(change), Amount(-20, 0), RuleVersions("cat", "res"), creationPhase = RequirementNode.CreationPhase.Phase.Creating)
        val receipt = Receipt("buy", "{\"characterId\":\"c\",\"target\":\"i\"}",
            SavedAcquisitionResult(Amount(20, 0), Amount(80, 2), listOf(change)), ReceiptStatus.Applied, 0, 1)
        return state().copy(ledger = Ledger(opening(), listOf(entry)), receipts = listOf(receipt), revision = 1, storageRevision = 1,
            instances = listOf(AcquisitionInstance("i", change.definition, 1, emptyMap(), null, AcquisitionOrigin.New)))
    }
    private fun rewrite(raw: String, action: (MutableMap<String, JsonValue>) -> Unit): String {
        val fields = parseRoot(raw).values.toMutableMap(); action(fields); return jsonStringify(JsonValue.Obj(fields))
    }
    @Test fun round_trip_large_xp() {
        val original = state(Amount(9_007_199_254_740_993L, Long.MAX_VALUE))
        val raw = encodeEnvelope(original)
        assertTrue(raw.contains("\"9007199254740993\""))
        val loaded = assertIs<LoadResult.Loaded>(decodeEnvelope(raw)).state
        assertEquals(original.ledger.opening.balance, balance(loaded.ledger))
        assertEquals(original.extras, loaded.extras)
        assertEquals(raw, encodeEnvelope(loaded))
    }
    @Test fun unknown_format_no_fresh_character() {
        val raw = " {\"formatVersion\":99,\"anything\":true} "
        assertEquals(raw, assertIs<LoadResult.Unsupported>(decodeEnvelope(raw)).raw)
        assertFailsWith<IllegalArgumentException> { SnapshotCodec.decode(raw) { "new" } }
    }
    @Test fun negative_opening_preserved() {
        assertEquals(Amount(-10, -2), balance(assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(state(Amount(-10,-2))))).state.ledger))
    }
    @Test fun legacy_requires_migration_and_preserves_source() {
        val raw = SnapshotCodec.encode(AppSnapshot(listOf(DublCharacter("old")), "old"))
        assertEquals(raw, assertIs<LoadResult.MigrationRequired>(decodeEnvelope(raw)).raw)
        assertIs<LoadResult.Unsupported>(decodeEnvelope(raw.replace("\"schema\":11", "\"schema\":99")))
        assertIs<LoadResult.Corrupt>(decodeEnvelope("{}"))
    }
    @Test fun same_id_different_payload_rejected() {
        val current = purchased()
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(current.copy(receipts = current.receipts + current.receipts.single().copy(requestCanonical = "{\"characterId\":\"c\",\"other\":1}"))) }
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(current.copy(ledger = Ledger(opening(), current.ledger.entries + current.ledger.entries))) }
    }
    @Test fun truncated_ledger_corrupt() {
        val raw = rewrite(encodeEnvelope(purchased())) { it["ledger"] = jsonObject("opening" to parseRoot(encodeEnvelope(state())).objectValue("ledger")!!.values.getValue("opening")) }
        assertEquals(raw, assertIs<LoadResult.Corrupt>(decodeEnvelope(raw)).raw)
        val missingEntry = rewrite(encodeEnvelope(purchased())) { it["ledger"] = parseRoot(encodeEnvelope(state())).values.getValue("ledger") }
        assertIs<LoadResult.Corrupt>(decodeEnvelope(missingEntry))
    }
    @Test fun inconsistent_receipt_chain_or_scope_is_rejected() {
        val current = purchased()
        for (receipt in listOf(current.receipts.single().copy(beforeRevision = 1),
            current.receipts.single().copy(afterRevision = 2),
            current.receipts.single().copy(requestCanonical = "{\"characterId\":\"other\"}"),
            current.receipts.single().copy(result = SavedAcquisitionResult(Amount(19,0), Amount(81,2), current.receipts.single().result.changes)))) {
            assertFailsWith<IllegalArgumentException> { encodeEnvelope(current.copy(receipts = listOf(receipt))) }
        }
    }
    @Test fun rename_or_key_order_same_token() {
        assertEquals("{\"a\":[true,null,9007199254740993,1.2300e+99],\"z\":\"é\"}",
            canonicalJson(" { \"z\":\"\\u00e9\",\"a\":[true,null,9007199254740993,1.2300e+99] }"))
        assertEquals(canonicalJson("{\"b\":2,\"a\":1}"), canonicalJson("{\"a\":1,\"b\":2}"))
        val change = purchased().receipts.single().result.changes.single()
        assertEquals(change.definition, purchased().copy(character = DublCharacter("c", name = "Renamed")).receipts.single().result.changes.single().definition)
    }
    @Test fun strict_json_rejects_ambiguous_or_invalid_scalars() {
        for (raw in listOf("{\"x\":1,\"x\":2}", "01", "1.", "1e", "\"line\nfeed\"", "[1,]")) assertFails { canonicalJson(raw) }
        val raw = encodeEnvelope(state()).replace("\"xp\":\"100\"", "\"xp\":100")
        assertIs<LoadResult.Corrupt>(decodeEnvelope(raw))
    }
    @Test fun sync_excludes_local_metadata_and_outbox_transfer_preserves_it() {
        val bought = purchased()
        val current = bought.copy(outbox = listOf(OutboxItem("buy", 7, encodeSyncEnvelope(bought))),
            syncMetadata = SyncMetadata(7, encodeSyncEnvelope(state())))
        val sync = parseRoot(encodeSyncEnvelope(current))
        assertFalse(sync.has("outbox")); assertFalse(sync.has("storageRevision")); assertFalse(sync.has("syncMetadata"))
        assertFalse(encodeSyncEnvelope(current).contains("local://portrait"))
        val transfer = CharacterTransferCodec.encodeEnvelope(current)
        val loaded = assertIs<LoadResult.Loaded>(CharacterTransferCodec.decodeEnvelope(transfer)).state
        assertEquals(encodeEnvelope(current), encodeEnvelope(loaded))
        assertIs<CharacterTransferDecodeResult.Failure>(CharacterTransferCodec.decode(transfer) { "new" })
    }
    @Test fun checked_balance_overflow_and_immutable_ledger() {
        val changes = purchased().ledger.entries.single().changes.toMutableList()
        val entry = LedgerEntry("buy", LedgerEntryType.Purchase, changes, Amount(-20,0), RuleVersions("cat","res"), creationPhase = RequirementNode.CreationPhase.Phase.Creating)
        val entries = mutableListOf(entry); val ledger = Ledger(opening(), entries)
        entries.clear(); changes.clear()
        assertEquals(Amount(80,2), balance(ledger)); assertEquals(1, ledger.entries.single().changes.size)
        assertFalse(ledger.entries is MutableList<*>); assertFalse(entry.changes is MutableList<*>)
        assertFailsWith<ArithmeticException> { balance(Ledger(opening(Amount(Long.MAX_VALUE,0)), listOf(entry.copy(amountDelta=Amount(1,0))))) }
    }
    @Test fun cas_atomic_snapshot_and_revision_separation() = runImmediate {
        val store = InMemoryAcquisitionStore()
        assertIs<LoadResult.Missing>(store.load("c"))
        assertIs<CommitResult.Committed>(store.compareAndSet("c", -1, state()))
        assertIs<CommitResult.Conflict>(store.compareAndSet("c", -1, state(Amount(999,0))))
        assertIs<CommitResult.Committed>(store.compareAndSet("c", 0, purchased()))
        assertIs<CommitResult.Conflict>(store.compareAndSet("c", 0, purchased()))
        val ack = purchased().copy(storageRevision = 2, syncMetadata = SyncMetadata(8))
        assertIs<CommitResult.Committed>(store.compareAndSet("c", 1, ack))
        val loaded = assertIs<LoadResult.Loaded>(store.load("c")).state
        assertEquals(1, loaded.revision); assertEquals(2, loaded.storageRevision); assertEquals(8, loaded.syncMetadata.serverRevision)
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("wrong", -1, state()))
        assertEquals(encodeEnvelope(ack), encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("c")).state))
    }
    @Test fun storage_revision_is_independent_of_imported_gameplay_revision() {
        val imported = purchased().copy(storageRevision = 0)
        assertEquals(0L, assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(imported))).state.storageRevision)
    }
    @Test fun canonical_unicode_surrogates_survive_utf8_transport() {
        val raw = "\"\\uD800\""
        val canonical = canonicalJson(raw)
        assertEquals(raw, canonical)
        assertEquals(canonical, canonical.encodeToByteArray().decodeToString())
    }
    @Test fun outbox_cannot_reuse_operation_with_different_payload() {
        val local = purchased()
        val other = local.copy(receipts = listOf(local.receipts.single().copy(requestCanonical = "{\"characterId\":\"c\",\"target\":\"different\"}")))
        assertFailsWith<IllegalArgumentException> {
            encodeEnvelope(local.copy(outbox = listOf(OutboxItem("buy", 7, encodeSyncEnvelope(other)))))
        }
    }
    @Test fun wrong_storage_key_does_not_load_or_replace_another_character() = runImmediate {
        val raw = encodeEnvelope(state())
        val store = InMemoryAcquisitionStore(mapOf("other" to raw))
        assertEquals(raw, assertIs<LoadResult.Corrupt>(store.load("other")).raw)
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("other", 0, state().copy(character = DublCharacter("other"), storageRevision = 1)))
    }
    @Test fun malformed_or_unknown_bytes_are_not_overwritten() = runImmediate {
        for (raw in listOf("{broken", "{\"formatVersion\":99}")) {
            val store = InMemoryAcquisitionStore(mapOf("c" to raw))
            assertIs<CommitResult.StorageFailure>(store.compareAndSet("c", -1, state()))
            when (val loaded = store.load("c")) {
                is LoadResult.Corrupt -> assertEquals(raw, loaded.raw)
                is LoadResult.Unsupported -> assertEquals(raw, loaded.raw)
                else -> fail("Original bytes lost")
            }
        }
    }
    @Test fun undo_retains_original_receipt_and_cannot_refund_twice() {
        val original = purchased()
        val change = original.ledger.entries.single().changes.single().copy(fromRank = 1, toRank = 0)
        val undo = LedgerEntry("undo", LedgerEntryType.Undo, listOf(change), Amount(20,0), RuleVersions("cat","res"),
            reversesId = "buy", creationPhase = RequirementNode.CreationPhase.Phase.Creating)
        val undone = original.copy(revision = 2, storageRevision = 2, instances = emptyList(),
            ledger = Ledger(opening(), original.ledger.entries + undo), receipts = listOf(original.receipts.single().copy(status = ReceiptStatus.Undone),
                Receipt("undo", "{\"characterId\":\"c\",\"undo\":\"buy\"}", SavedAcquisitionResult(Amount(-20,0), Amount(100,2), listOf(change)), ReceiptStatus.Applied, 1,2)))
        val loaded = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(undone))).state
        assertEquals(Amount(100,2), balance(loaded.ledger))
        assertEquals(ReceiptStatus.Undone, loaded.receipts.first().status)
        val twice = undone.copy(revision = 3, storageRevision = 3, ledger = Ledger(opening(), undone.ledger.entries + undo.copy(operationId = "again")),
            receipts = undone.receipts + undone.receipts.last().copy(operationId = "again", beforeRevision = 2, afterRevision = 3,
                result = SavedAcquisitionResult(Amount(-20,0), Amount(120,2), listOf(change))))
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(twice) }
    }
    @Test fun malformed_projection_unknown_fields_and_future_transfer_retain_raw() {
        val valid = encodeEnvelope(state())
        for (raw in listOf(rewrite(valid) { it.remove("receipts") },
            rewrite(valid) { it["futureRequired"] = jsonBoolean(true) }, valid.replace("\"xp\":\"100\"", "\"xp\":\"9223372036854775808\""))) {
            when (val result = decodeEnvelope(raw)) {
                is LoadResult.Corrupt -> assertEquals(raw, result.raw)
                is LoadResult.Unsupported -> assertEquals(raw, result.raw)
                else -> fail("Expected read-only failure")
            }
        }
        val future = CharacterTransferCodec.encodeEnvelope(state()).replace("\"formatVersion\":1", "\"formatVersion\":99")
        assertEquals(future, assertIs<LoadResult.Unsupported>(CharacterTransferCodec.decodeEnvelope(future)).raw)
        assertEquals("{\"x\":2}", jsonStringify(JsonReader("{\"x\":1,\"x\":2}").read()))
    }
    @Test fun immutable_dtos_compare_structurally_after_full_fidelity_round_trip() {
        val huge = 9_007_199_254_740_993L
        val original = purchased()
        val change = original.ledger.entries.single().changes.single().copy(parameters = mapOf("subject" to "stable-id"), optionId = "ap")
        val entry = original.ledger.entries.single().copy(changes = listOf(change), amountDelta = Amount(-huge,-huge))
        val updated = original.copy(ledger = Ledger(opening(Amount(huge + 7, huge + 9)), listOf(entry)),
            receipts = listOf(original.receipts.single().copy(result = SavedAcquisitionResult(Amount(huge, huge), Amount(7,9), listOf(change)))),
            instances = listOf(AcquisitionInstance("i", change.definition, 1, change.parameters, "ap", AcquisitionOrigin.Legacy(
                "c", "11", "old-revision", "original-bytes", "skill:legacy-key"))))
        val loaded = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(updated))).state
        assertEquals(updated, loaded)
        assertEquals(updated.hashCode(), loaded.hashCode())
        assertEquals(Amount(huge, huge), loaded.receipts.single().result.cost)
        assertEquals(Amount(-huge,-huge), loaded.ledger.entries.single().amountDelta)
    }
    @Test fun encode_and_commit_reject_nonfinite_projection_instead_of_silent_zero() = runImmediate {
        val invalid = state().copy(character = DublCharacter("c", gear = CharacterGear(loadManual = Double.NaN)))
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(invalid) }
        val store = InMemoryAcquisitionStore()
        assertIs<CommitResult.StorageFailure>(store.compareAndSet("c", -1, invalid))
        assertIs<LoadResult.Missing>(store.load("c"))
    }
    @Test fun envelope_preserves_manual_projection_while_legacy_decode_still_normalizes() {
        val character = DublCharacter("c", experience = -3, creationExperience = -4, abilityPointsOverride = -2,
            skills = mapOf("custom" to CharacterSkill(id = "custom", name = "Custom", rank = 14)),
            attributes = defaultAttributes() + (AttributeId.STRENGTH to AttributeValue(base = 17)),
            hpCurrent = 999, enduranceCurrent = 999, manaCurrent = 999, chiBonusRanks = 17,
            magic = CharacterMagic(manaRank = 7), gear = CharacterGear(loadManual = -2.5),
            development = mapOf("manual" to OwnedDevelopment(-2,-1)))
        val current = state().copy(character = character)
        val loaded = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(current))).state
        assertEquals(character, loaded.character)
        val legacy = SnapshotCodec.decode(SnapshotCodec.encode(AppSnapshot(listOf(character), "c"))) { "fallback" }.characters.single()
        assertEquals(10, legacy.skills.getValue("custom").rank)
        assertEquals(10, legacy.attributes.getValue(AttributeId.STRENGTH).base)
        assertEquals(0, legacy.experience)
        assertFalse("manual" in legacy.development)
    }
    private fun runImmediate(block: suspend () -> Unit) {
        var completion: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> { override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { completion = result } })
        checkNotNull(completion).getOrThrow()
    }
}
