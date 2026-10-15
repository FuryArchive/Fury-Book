package com.furybook.web

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.actions.*
import com.furybook.dubl.model.*
import com.furybook.dubl.data.SnapshotCodec
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlin.js.Promise
import kotlin.test.*

/** Real IndexedDB; fixture principal never enters production authentication or sync. */
class WebGameplayStoreTest {
    private fun source() = StoredCharacter(DublCharacter("hero", name = "Local fixture"), CharacterSheetExtras(notes = "Keep me"),
        emptyList(), Ledger(Opening("opening", "fixture", Amount(100, 0), RequirementNode.CreationPhase.Phase.Creating,
            OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
        RequirementNode.CreationPhase.Phase.Creating)
    private suspend fun open(name: String, enabled: Boolean = true,
        fault: (BrowserAcquisitionWriteStage) -> Unit = {}): BrowserAcquisitionStore {
        val owner = BrowserOwnerScope.fromVerifiedSession(WebAuthSession("gameplay-fixture", "fixture.invalid")) { "gameplay-fixture" }
        return BrowserAcquisitionStore(owner, name, localWritesEnabled = true, legacySource = { null },
            fault = fault, gameplayFormatEnabled = enabled).also { assertTrue(it.initialize(), it.storageError) }
    }
    private fun test(body: suspend () -> Unit): Promise<Unit> = MainScope().promise { withTimeout(15000) { body() } }
    private suspend fun saved(store: BrowserAcquisitionStore) = assertIs<LoadResult.Loaded>(store.load("hero")).state
    private fun migration() = GameplayMigrationRequest("migration", "hero", 0, encodeEnvelope(source()))
    private suspend fun event(store: BrowserAcquisitionStore, id: String, boundary: GameBoundary): RecordGameBoundary {
        val state = saved(store)
        val request = RecordGameBoundary(id, "hero", state.gameplay?.events?.timeline?.revision ?: 0, boundary)
        assertIs<GameplayEventCommitResult.Applied>(GameplayEventService(store) { GameplayCapabilities(true, true) }
            .apply(request, state.storageRevision))
        return request
    }
    private suspend fun seed(store: BrowserAcquisitionStore) {
        assertEquals(CommitResult.Committed, store.compareAndSet("hero", -1, source()))
        assertIs<GameplayMigrationResult.Migrated>(store.migrateGameplay(migration()))
        assertEquals(encodeEnvelope(source()), store.gameplayMigrationBackupForExport("hero", "migration"))
    }
    @Test fun combat_budget_effect_and_old_writer_guard_use_real_idb() = test {
        val name = "furybook-combat-" + webUuid()
        var store = open(name)
        try {
            seed(store)
            event(store, "turn", GameBoundary.TurnStarted("turn-start", "hero"))
            val reserve = event(store, "reserve", GameBoundary.DefenseReserved("reserve-defense", 2))
            event(store, "reaction", GameBoundary.ReactionSpent("reaction-spent"))
            store.close(); store = open(name)
            val reopened = saved(store)
            assertEquals(CombatResources(1, 0, 2), reopened.gameplay!!.events!!.timeline.combat)
            assertIs<GameplayEventCommitResult.Replayed>(GameplayEventService(store) { GameplayCapabilities(true, true) }.apply(reserve, 0))
            assertEquals(reopened, saved(store))
            event(store, "end", GameBoundary.TurnEnded("turn-end", "hero", "turn-start"))
            event(store, "round", GameBoundary.RoundEnded("round-end"))
            assertEquals(CombatResources(1, 0, 2), saved(store).gameplay!!.events!!.timeline.combat)
            event(store, "next", GameBoundary.TurnStarted("next-start", "hero"))
            assertEquals(CombatResources(3, 1, 0), saved(store).gameplay!!.events!!.timeline.combat)
            assertEquals("Keep me", saved(store).extras.notes)
            assertTrue(saved(store).outbox.isEmpty())
            assertFailsWith<IllegalArgumentException> { encodeSyncEnvelope(saved(store)) }
            val old = open(name, enabled = false)
            try { assertIs<LoadResult.Unsupported>(old.load("hero")) } finally { old.close() }
        } finally { store.close() }
    }
    @Test fun migration_abort_retains_head_and_exact_backup() = test {
        for (stage in listOf(BrowserAcquisitionWriteStage.AfterGameplayBackup, BrowserAcquisitionWriteStage.BeforeGameplayHeadReplace)) {
            val name = "furybook-gameplay-abort-" + webUuid()
            var store = open(name)
            assertEquals(CommitResult.Committed, store.compareAndSet("hero", -1, source())); store.close()
            store = open(name, fault = { if (it == stage) error("Injected $stage") })
            assertIs<GameplayMigrationResult.StorageFailure>(store.migrateGameplay(migration())); store.close()
            store = open(name)
            try {
                assertEquals(source(), saved(store))
                assertNull(store.gameplayMigrationBackupForExport("hero", "migration"))
                assertIs<GameplayMigrationResult.Migrated>(store.migrateGameplay(migration()))
                assertEquals(encodeEnvelope(source()), store.gameplayMigrationBackupForExport("hero", "migration"))
            } finally { store.close() }
        }
    }
    @Test fun mode_off_bootstrap_reads_supported_local_format_without_write_capability() = test {
        val name = "furybook-gameplay-readonly-" + webUuid()
        val writable = open(name)
        seed(writable)
        val expected = writable.rawForExport("hero")
        writable.close()
        val session = WebAuthSession("gameplay-fixture", "fixture.invalid")
        val readonly = BrowserAcquisitionSession.open(session, { session.userId }, name, verifyOwner = { it })
        try {
            assertEquals(2, readonly.states.getValue("hero").formatVersion)
            assertFalse(readonly.store.capabilities.localDurable)
            val state = saved(readonly.store)
            assertIs<CommitResult.StorageFailure>(readonly.store.compareAndSet("hero", state.storageRevision,
                state.copy(storageRevision = state.storageRevision + 1)))
            assertEquals(expected, readonly.store.rawForExport("hero"))
        } finally { readonly.store.close() }
    }
    @Test fun session_store_keeps_typed_changed_legacy_source_fence() = test {
        clearBrowserSessionData()
        val snapshot = AppSnapshot(listOf(DublCharacter("hero",name="Original")), "hero")
        val raw = SnapshotCodec.encode(snapshot)
        receiveBrowserLegacySource("gameplay-fixture", BrowserLegacySource(raw, "{\"version\":1,\"characters\":{}}"))
        val session = WebAuthSession("gameplay-fixture", "fixture.invalid")
        val opened = BrowserAcquisitionSession.open(session, { session.userId }, "furybook-source-fence-"+webUuid(),
            localRulesEnabled=true, verifyOwner={it})
        try {
            assertIs<LoadResult.MigrationRequired>(opened.store.load("hero"))
            BrowserCharacterStore().save(snapshot.copy(characters=listOf(snapshot.characters.single().copy(name="Changed"))))
            assertIs<LoadResult.LegacyImportConflict>(opened.store.load("hero"))
            assertNull(opened.store.rawForExport("hero"))
        } finally { opened.store.close(); clearBrowserSessionData() }
    }
    @Test fun separate_browser_process_reopens_gameplay_budget_and_pending_physical_check() = test {
        val phase = window.asDynamic().__karma__.config.furybookIdbPhase as? String ?: "normal"
        if (phase == "normal") return@test
        val store = open("furybook-gameplay-process-20261015")
        try {
            if (phase == "write") {
                assertIs<LoadResult.Missing>(store.load("hero")); seed(store)
                assertIs<GameplayCommitResult.Applied>(GameplayService(store) { GameplayCapabilities(true, true) }
                    .apply(StartOverexertion("effort", "hero", 0, PhysicalAttribute.STRENGTH), 1))
                event(store, "turn", GameBoundary.TurnStarted("turn-start", "hero"))
                event(store, "reserve", GameBoundary.DefenseReserved("reserve-defense", 2))
                event(store, "reaction", GameBoundary.ReactionSpent("reaction-spent"))
            } else assertEquals("reopen", phase)
            val state = saved(store)
            assertEquals(2, state.character.enduranceCurrent)
            assertEquals("effort", state.gameplay!!.pending?.startOperationId)
            assertEquals(CombatResources(1, 0, 2), state.gameplay!!.events!!.timeline.combat)
            assertTrue(state.outbox.isEmpty())
            println("GAMEPLAY_REAL_BROWSER_PROCESS|$phase|E2|AP1|reaction0|defense2|pending-effort|${store.observedDurability}")
        } finally { store.close() }
    }
}
