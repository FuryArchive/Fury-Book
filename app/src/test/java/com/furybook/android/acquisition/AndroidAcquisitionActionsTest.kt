package com.furybook.android.acquisition

import com.furybook.android.data.*
import com.furybook.android.state.*
import com.furybook.android.ui.acquisition.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AndroidAcquisitionActionsTest {
    @Test fun actual_android_adapter_receipt_restart_and_inverse_feed_the_same_binding() = runBlocking<Unit> {
        val root = Files.createTempDirectory("t18a-adapter")
        try {
            val store = AndroidAcquisitionStore(root, AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")), JvmFileOps)
            assertEquals(CommitResult.Committed, store.compareAndSet("real", -1, t13State()))
            fun controller(state: StoredCharacter): CharacterController = CharacterController(
                object : CharacterStore { override fun load() = AppSnapshot(listOf(state.character), "real"); override fun save(snapshot: AppSnapshot) = error("legacy write") },
                object : CharacterExtrasStore { override fun load(characterId: String) = state.extras; override fun save(characterId: String, extras: CharacterSheetExtras) = error("legacy write") },
                store, mapOf("real" to state), catalogProvider = ::t13Catalog,
                capabilities = AcquisitionCapabilities(true, false, true), mainDispatcher = Dispatchers.Unconfined)
            val original = controller(t13State())
            val preview = original.acquisition.preview(t13Request())
            assertEquals("20 XP / 0 ОСп", bindAndroidAcquisition(preview).price)
            val paid = original.acquisition.confirm(preview.key)
            assertEquals("20 XP / 0 ОСп", bindAndroidAcquisition(paid).receiptPrice)
            assertEquals("80 XP / 10 ОСп", bindAndroidAcquisition(paid).receiptRemaining)
            original.acquisition.confirm(preview.key)
            val loaded = (store.load("real") as LoadResult.Loaded).state
            assertEquals(1, loaded.receipts.size)
            val reopened = controller(loaded)
            assertEquals(Amount(80, 10), reopened.acquisition.readCanonicalView().balance)
            val undo = reopened.acquisition.prepareUndo()
            val inverse = reopened.acquisition.confirm(undo.key)
            assertEquals("-20 XP / 0 ОСп", bindAndroidAcquisition(inverse).receiptPrice)
            assertEquals(Amount(100, 10), reopened.acquisition.readCanonicalView().balance)
            assertFalse(AndroidAcquisitionRuntimeGate.production.localDurable)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }
    @Test fun descriptive_edit_stages_once_and_never_publishes_before_exact_confirmation() = runBlocking<Unit> {
        val root = Files.createTempDirectory("t18a-description")
        val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = t13State()
            val store = AndroidAcquisitionStore(root, AndroidLegacyAcquisitionSource(root.resolve("prefs")), JvmFileOps)
            assertEquals(CommitResult.Committed, store.compareAndSet("real", -1, state))
            val controller = CharacterController(
                object : CharacterStore { override fun load() = AppSnapshot(listOf(state.character), "real"); override fun save(snapshot: AppSnapshot) = error("legacy write") },
                object : CharacterExtrasStore { override fun load(characterId: String) = state.extras; override fun save(characterId: String, extras: CharacterSheetExtras) = error("legacy write") },
                store, mapOf("real" to state), catalogProvider = ::t13Catalog,
                capabilities = AcquisitionCapabilities(true, false, true), mainDispatcher = Dispatchers.Unconfined)
            val actions = AndroidAcquisitionActions(controller, uiScope)
            controller.onCanonicalNonFinancialReview = actions::nonFinancial
            suspend fun drained() { uiScope.coroutineContext.job.children.toList().forEach { it.join() } }
            suspend fun saved() = (store.load("real") as LoadResult.Loaded).state
            actions.reload()
            controller.setName("Detached descriptive name")
            drained()
            assertEquals(state.character.name, controller.active.name)
            assertEquals(state, saved())
            val review = checkNotNull(controller.acquisitionUiModel)
            assertEquals(AcquisitionUiPhase.Ready, review.phase)
            actions.confirm(review.key); drained()
            assertEquals("Detached descriptive name", controller.active.name)
            assertEquals(Amount(100, 10), balance(saved().ledger))
            assertEquals(1, saved().receipts.size)
            actions.confirm(review.key); drained()
            assertEquals(1, saved().receipts.size)
            val snapshot = saved()
            assertTrue(runCatching { controller.setExperience(999999) }.exceptionOrNull() is IllegalStateException)
            drained()
            assertEquals(snapshot, saved())
            assertEquals(snapshot.character, controller.active)
            var callbackCount = 0
            actions.nonFinancial { callbackCount++; character.setName("Frozen callback") }; drained()
            val second = checkNotNull(controller.acquisitionUiModel)
            assertEquals(1, callbackCount)
            actions.confirm(second.key); drained()
            actions.confirm(second.key); drained()
            assertEquals(1, callbackCount)
            assertEquals("Frozen callback", saved().character.name)
        } finally {
            uiScope.cancel()
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun protected_read_only_can_quote_but_refuses_descriptive_staging_and_manual_writes() = runBlocking<Unit> {
        val root = Files.createTempDirectory("t18a-protected")
        val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = t13State()
            val store = AndroidAcquisitionStore(root, AndroidLegacyAcquisitionSource(root.resolve("prefs")), JvmFileOps)
            assertEquals(CommitResult.Committed, store.compareAndSet("real", -1, state))
            val controller = CharacterController(
                object : CharacterStore { override fun load() = AppSnapshot(listOf(state.character), "real"); override fun save(snapshot: AppSnapshot) = error("legacy write") },
                object : CharacterExtrasStore { override fun load(characterId: String) = state.extras; override fun save(characterId: String, extras: CharacterSheetExtras) = error("legacy write") },
                store, mapOf("real" to state), catalogProvider = ::t13Catalog,
                capabilities = AndroidAcquisitionRuntimeGate.production, mainDispatcher = Dispatchers.Unconfined)
            val actions = AndroidAcquisitionActions(controller, uiScope)
            actions.reload()
            assertEquals("20 XP / 0 ОСп", bindAndroidAcquisition(controller.acquisition.quoteCard(t13Request())).price)
            var callbacks = 0
            actions.nonFinancial { callbacks++; character.setName("Must not stage") }
            uiScope.coroutineContext.job.children.toList().forEach { it.join() }
            val blocked = checkNotNull(controller.acquisitionUiModel)
            assertFalse(blocked.canConfirm); assertEquals(0, callbacks)
            actions.confirm(blocked.key)
            uiScope.coroutineContext.job.children.toList().forEach { it.join() }
            assertEquals(state, (store.load("real") as LoadResult.Loaded).state)
        } finally {
            uiScope.cancel()
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    @Test fun manual_transform_preserves_canonical_fields_hidden_by_runtime_content_suppression() = runBlocking<Unit> {
        val root = Files.createTempDirectory("t18a-manual-base")
        val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val original = t13State()
            val state = original.copy(character = original.character.copy(chiEnabled = true,
                chiCurrent = 3, chiBonusRanks = 2, development = mapOf("hidden-pack-holding" to OwnedDevelopment(rank = 2))))
            val store = AndroidAcquisitionStore(root, AndroidLegacyAcquisitionSource(root.resolve("prefs")), JvmFileOps)
            assertEquals(CommitResult.Committed, store.compareAndSet("real", -1, state))
            val controller = CharacterController(
                object : CharacterStore { override fun load() = AppSnapshot(listOf(state.character), "real"); override fun save(snapshot: AppSnapshot) = error("legacy write") },
                object : CharacterExtrasStore { override fun load(characterId: String) = state.extras; override fun save(characterId: String, extras: CharacterSheetExtras) = error("legacy write") },
                store, mapOf("real" to state), catalogProvider = ::t13Catalog,
                capabilities = AcquisitionCapabilities(true, false, true), mainDispatcher = Dispatchers.Unconfined)
            controller.setRuntimeContentSuppression(setOf("hidden-pack-holding"), true)
            assertFalse(controller.active.chiEnabled)
            assertTrue(controller.active.development.isEmpty())
            val actions = AndroidAcquisitionActions(controller, uiScope)
            actions.reload()
            actions.editManual("Size only") { it.copy(size = 7) }
            val draft = checkNotNull(actions.manual)
            actions.reviewManual(draft, Amount.ZERO, "Explicit zero-delta size correction")
            uiScope.coroutineContext.job.children.toList().forEach { it.join() }
            assertEquals(state, (store.load("real") as LoadResult.Loaded).state)
            val review = checkNotNull(controller.acquisitionUiModel)
            assertEquals(AcquisitionUiPhase.Ready, review.phase)
            actions.confirm(review.key)
            uiScope.coroutineContext.job.children.toList().forEach { it.join() }
            val saved = (store.load("real") as LoadResult.Loaded).state
            assertEquals(7, saved.character.size)
            assertTrue(saved.character.chiEnabled)
            assertEquals(3, saved.character.chiCurrent)
            assertEquals(2, saved.character.chiBonusRanks)
            assertEquals(state.character.development, saved.character.development)
            assertEquals(state.instances, saved.instances)
            assertEquals(balance(state.ledger), balance(saved.ledger))
            assertEquals(1, saved.receipts.size)
        } finally {
            uiScope.cancel()
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

}
