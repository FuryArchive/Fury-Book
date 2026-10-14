package com.furybook.android.acquisition

import com.furybook.android.data.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.actions.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Real Android adapter with checked JVM file operations; NOT an Android device/native-Os test. */
private fun gameplayStore(root: Path, enabled: Boolean = true, fault: (AndroidAcquisitionWriteStage) -> Unit = {}) =
    AndroidAcquisitionStore(root, AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")), JvmFileOps,
        gameplayFormatEnabled = enabled, fault = fault)

class AndroidGameplayStoreJvmTest {
    @Test fun explicit_migration_keeps_original_backup_and_default_writer_fence() = runBlocking {
        val root = Files.createTempDirectory("android-gameplay")
        val store = gameplayStore(root); val source = t13State(); val raw = encodeEnvelope(source)
        assertEquals(CommitResult.Committed, store.compareAndSet("real", -1, source))
        val request = GameplayMigrationRequest("migration", "real", 0, raw)
        assertTrue(gameplayStore(root, false).migrateGameplay(request) is GameplayMigrationResult.Blocked)
        assertTrue(store.compareAndSet("real", 0, prepareGameplayMigration(source, raw, request)) is CommitResult.StorageFailure)
        val done = store.migrateGameplay(request) as GameplayMigrationResult.Migrated
        assertEquals(raw, Files.readString(Path.of(done.backupPath)))
        assertTrue(gameplayStore(root, false).load("real") is LoadResult.Unsupported)
        assertEquals(listOf("real"), store.characterIds())
        assertTrue(runCatching { gameplayStore(root, false).characterIds() }.isFailure)
        assertTrue(gameplayStore(root, false).compareAndSet("real", 1, source.copy(storageRevision = 2)) is CommitResult.StorageFailure)
        assertTrue(store.compareAndSet("real", 1, source.copy(storageRevision = 2)) is CommitResult.StorageFailure)
        val exports = store.recoveryExport("real")
        assertEquals(raw, String(exports.filterKeys { it.startsWith("gameplay-migration-backups/") }.values.single()))
        assertTrue(store.recoveryExport("other").keys.none { it.startsWith("gameplay-migration-backups/") })
        assertTrue(store.recoveryExportAll().keys.any { it.startsWith("gameplay-migration-backups/") })
        assertTrue(store.legacySource.gate.readOnly)
    }

    @Test fun interrupted_migrations_reconcile_same_id_and_preserve_backup() = runBlocking {
        for (stage in listOf(AndroidAcquisitionWriteStage.AfterGameplayBackup,
            AndroidAcquisitionWriteStage.BeforeGameplayHeadReplace, AndroidAcquisitionWriteStage.AfterReplace)) {
            val root = Files.createTempDirectory("android-migration-fault")
            val source = t13State(); val raw = encodeEnvelope(source)
            assertEquals(CommitResult.Committed, gameplayStore(root).compareAndSet("real", -1, source))
            var fired = false; var replaces = 0
            val faulty = gameplayStore(root) { reached ->
                if (reached == AndroidAcquisitionWriteStage.AfterReplace) replaces++
                if (!fired && reached == stage && (stage != AndroidAcquisitionWriteStage.AfterReplace || replaces == 2)) {
                    fired = true; error("injected $stage")
                }
            }
            val command = GameplayMigrationRequest("migration", "real", 0, raw)
            assertTrue(faulty.migrateGameplay(command) is GameplayMigrationResult.StorageFailure)
            assertTrue(fired)
            val retry = gameplayStore(root).migrateGameplay(command)
            val backup = when (retry) {
                is GameplayMigrationResult.Migrated -> { assertNotEquals(AndroidAcquisitionWriteStage.AfterReplace, stage); retry.backupPath }
                is GameplayMigrationResult.AlreadyMigrated -> { assertEquals(AndroidAcquisitionWriteStage.AfterReplace, stage); retry.backupPath }
                else -> error("Unexpected migration retry: $retry")
            }
            assertEquals(raw, Files.readString(Path.of(backup)))
        }
    }

    @Test fun intervening_change_and_cloud_history_cannot_be_silently_migrated() = runBlocking {
        val root = Files.createTempDirectory("android-migration-stale")
        val source = t13State(); val store = gameplayStore(root)
        store.compareAndSet("real", -1, source)
        val request = GameplayMigrationRequest("migration", "real", 0, encodeEnvelope(source))
        store.compareAndSet("real", 0, source.copy(storageRevision = 1))
        val before = Files.readAllBytes(store.envelopePath("real"))
        assertTrue(store.migrateGameplay(request) is GameplayMigrationResult.Blocked)
        assertArrayEquals(before, Files.readAllBytes(store.envelopePath("real")))
        val cloud = source.copy(storageRevision = 2, syncMetadata = SyncMetadata(2))
        store.compareAndSet("real", 1, cloud)
        assertTrue(store.migrateGameplay(request.copy(expectedStorageRevision = 2,
            expectedSourceCanonical = encodeEnvelope(cloud))) is GameplayMigrationResult.Blocked)
    }
}

/** Start, Undo and verify are deliberately launched in different host JVMs. */
fun main(args: Array<String>) = runBlocking {
    val root = Path.of(args[0]); Files.createDirectories(root)
    val store = gameplayStore(root)
    val service = GameplayService(store) { GameplayCapabilities(true, true) }
    val start = StartOverexertion("start", "real", 0, PhysicalAttribute.STRENGTH)
    val source = t13State(); val migration = GameplayMigrationRequest("migration", "real", 0, encodeEnvelope(source))
    when (args[1]) {
        "start" -> {
            check(store.compareAndSet("real", -1, source) == CommitResult.Committed)
            check(store.migrateGameplay(migration) is GameplayMigrationResult.Migrated)
            check(service.apply(start, 1) is GameplayCommitResult.Applied)
        }
        "undo" -> {
            val saved = (store.load("real") as LoadResult.Loaded).state
            check(saved.character.enduranceCurrent == 2 && saved.gameplay?.pending?.startOperationId == "start")
            check(service.apply(start, 1) is GameplayCommitResult.Replayed)
            check(service.apply(UndoGameplay("undo", "real", 1, "start"), 2) is GameplayCommitResult.Applied)
        }
        "verify" -> {
            val saved = (store.load("real") as LoadResult.Loaded).state
            check(saved.character.enduranceCurrent == 3 && saved.gameplay?.pending == null)
            check((service.apply(start, 1) as GameplayCommitResult.Replayed).undone)
            check(store.migrateGameplay(migration) is GameplayMigrationResult.AlreadyMigrated)
            check(saved.gameplay?.migration?.sourceRaw == encodeEnvelope(source))
        }
        else -> error("Unknown phase")
    }
    println("ANDROID_GAMEPLAY_HOST_PHASE_OK:${args[1]}")
}
