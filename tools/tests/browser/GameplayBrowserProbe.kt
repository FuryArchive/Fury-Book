package com.furybook.web

import com.furybook.dubl.acquisition.*
import com.furybook.dubl.actions.*
import com.furybook.dubl.model.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** Isolated real IndexedDB fixture. Never connects to authentication, sync or production storage. */
private const val DB = "furybook-gameplay-probe-20261014-1436"
private fun source() = StoredCharacter(DublCharacter("hero", name = "Fixture", experience = 100),
    CharacterSheetExtras(notes = "Preserve exact original"), emptyList(),
    Ledger(Opening("opening", "source", Amount(100, 2), RequirementNode.CreationPhase.Phase.Creating,
        OpeningProvenance.NewCharacter), emptyList()), emptyList(), emptyList(), SyncMetadata(0), 0, 0,
    RequirementNode.CreationPhase.Phase.Creating)
private fun request() = GameplayMigrationRequest("migration", "hero", 0, encodeEnvelope(source()))
private val start = StartOverexertion("start", "hero", 0, PhysicalAttribute.DEXTERITY)
private fun store(name: String = DB, enabled: Boolean = true,
    fault: (BrowserAcquisitionWriteStage) -> Unit = {}): BrowserAcquisitionStore {
    val owner = BrowserOwnerScope.fromVerifiedSession(WebAuthSession("isolated-fixture", "fixture.invalid")) { "isolated-fixture" }
    return BrowserAcquisitionStore(owner, name, localWritesEnabled = true, legacySource = { null },
        fault = fault, gameplayFormatEnabled = enabled)
}
private suspend fun opened(name: String = DB, enabled: Boolean = true,
    fault: (BrowserAcquisitionWriteStage) -> Unit = {}): BrowserAcquisitionStore = store(name, enabled, fault).also {
    check(it.initialize()) { "Initialization: ${it.storageError}" }
}
private suspend fun phase(name: String) {
    val s = opened()
    try {
        val service = GameplayService(s) { GameplayCapabilities(s.capabilities.localDurable, true) }
        when (name) {
            "Start isolated test" -> {
                check(s.compareAndSet("hero", -1, source()) == CommitResult.Committed)
                check(s.migrateGameplay(request()) is GameplayMigrationResult.Migrated)
                check(s.gameplayMigrationBackupForExport("hero", "migration") == encodeEnvelope(source()))
                check(service.apply(start, 1) is GameplayCommitResult.Applied)
                val saved = (s.load("hero") as LoadResult.Loaded).state
                check(saved.character.enduranceCurrent == 2 && saved.gameplay?.pending != null)
            }
            "Reopen and Undo" -> {
                val saved = (s.load("hero") as LoadResult.Loaded).state
                check(saved.character.enduranceCurrent == 2 && saved.gameplay?.pending?.startOperationId == "start")
                check(service.apply(start, 1) is GameplayCommitResult.Replayed)
                check(service.apply(UndoGameplay("undo", "hero", 1, "start"), 2) is GameplayCommitResult.Applied)
            }
            "Verify after reload" -> {
                val saved = (s.load("hero") as LoadResult.Loaded).state
                check(saved.character.enduranceCurrent == 3 && saved.gameplay?.pending == null)
                check((service.apply(start, 1) as GameplayCommitResult.Replayed).undone)
                check(s.migrateGameplay(request()) is GameplayMigrationResult.AlreadyMigrated)
                check(s.gameplayMigrationBackupForExport("hero", "migration") == encodeEnvelope(source()))
                val old = opened(enabled = false)
                try { check(old.load("hero") is LoadResult.Unsupported) } finally { old.close() }
                check(s.compareAndSet("hero", saved.storageRevision, source().copy(storageRevision = saved.storageRevision + 1)) is CommitResult.StorageFailure)
            }
            "Fault checks" -> {
                for (stage in listOf(BrowserAcquisitionWriteStage.AfterGameplayBackup,
                    BrowserAcquisitionWriteStage.BeforeGameplayHeadReplace, BrowserAcquisitionWriteStage.AfterPutSucceeded)) {
                    val db = "$DB-${stage.name}"
                    val seed = opened(db)
                    check(seed.compareAndSet("hero", -1, source()) == CommitResult.Committed); seed.close()
                    var fired = false
                    val broken = opened(db, fault = { if (it == stage) { fired = true; error("injected $stage") } })
                    check(broken.migrateGameplay(request()) is GameplayMigrationResult.StorageFailure && fired); broken.close()
                    val retry = opened(db)
                    try {
                        check((retry.load("hero") as LoadResult.Loaded).state == source())
                        check(retry.gameplayMigrationBackupForExport("hero", "migration") == null)
                        check(retry.migrateGameplay(request()) is GameplayMigrationResult.Migrated)
                        check(retry.gameplayMigrationBackupForExport("hero", "migration") == encodeEnvelope(source()))
                    } finally { retry.close() }
                }
            }
            else -> error("Unknown phase")
        }
    } finally { s.close() }
}
fun main() {
    val scope = MainScope()
    val status = document.createElement("pre")
    status.textContent = "Ready. Isolated fixtures only. Reload between the first three phases."
    document.body!!.appendChild(status)
    for (name in listOf("Start isolated test", "Reopen and Undo", "Verify after reload", "Fault checks")) {
        val button = document.createElement("button")
        button.textContent = name
        button.addEventListener("click", {
            status.textContent = "Running: $name"
            scope.launch {
                status.textContent = try { phase(name); "PASS: $name" }
                catch (failure: Throwable) { "FAIL: $name\n${failure.stackTraceToString()}" }
            }
        })
        document.body!!.appendChild(button)
    }
}
