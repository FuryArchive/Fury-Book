package com.furybook.android.acquisition

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.furybook.android.ui.acquisition.AcquisitionDefinitionKey
import com.furybook.android.ui.acquisition.AcquisitionPhaseKey
import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Genuine Android input source. Compiling this is not evidence that it ran on a device. */
@RunWith(AndroidJUnit4::class)
class AcquisitionUiTest {
    @get:Rule val compose = createAndroidComposeRule<AcquisitionUiTestActivity>()
    private fun ready() { compose.waitUntil(30_000) { compose.activity.fixture != null || compose.activity.loadError != null }; assertNull(compose.activity.loadError) }
    private fun saved() = runBlocking { (compose.activity.fixture!!.store.load("ui-A") as LoadResult.Loaded).state }
    private fun openSynthetic() {
        ready()
        compose.onNodeWithTag("navigation.feats").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("development.search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("development.search").performTextInput("Проверочный")
        val match = hasTestTag("acquisition.open") and hasAnyAncestor(SemanticsMatcher.expectValue(AcquisitionDefinitionKey, "DEVELOPMENT:${AndroidAcquisitionUiFixture.SYNTHETIC_ID}"))
        compose.waitUntil(30_000) { compose.onAllNodes(match, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(match, useUnmergedTree = true).onFirst().performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("acquisition.preview") and
            SemanticsMatcher.expectValue(AcquisitionPhaseKey, AcquisitionUiPhase.Ready.name)).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun synthetic20_purchase_repeated_confirmation_saved_receipt_and_undo() {
        openSynthetic()
        compose.onNodeWithTag("acquisition.confirm").assertIsEnabled().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("acquisition.receipt").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
        assertEquals(80L, balance(saved().ledger).xp); assertEquals(1, saved().receipts.size)
        compose.onNodeWithTag("acquisition.undo").performScrollTo().performClick()
        compose.onNodeWithTag("acquisition.undo.confirm").assertIsEnabled().performClick()
        compose.waitUntil(30_000) { compose.activity.fixture!!.controller.active.development[AndroidAcquisitionUiFixture.SYNTHETIC_ID] == null }
        assertEquals(100L, balance(saved().ledger).xp)
    }
    @Test fun real_cancel_and_back_do_not_create_a_purchase() {
        openSynthetic()
        compose.onNodeWithTag("acquisition.cancel").performClick()
        assertEquals(100L, balance(saved().ledger).xp); assertTrue(saved().receipts.isEmpty())
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(100L, balance(saved().ledger).xp)
    }
    @Test fun real_catalog_block_identity_is_visible_and_never_forced() {
        ready(); compose.onNodeWithTag("navigation.feats").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("development.search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("development.search").performTextInput("Блокирование")
        val match = hasTestTag("acquisition.open") and hasAnyAncestor(SemanticsMatcher.expectValue(AcquisitionDefinitionKey, "DEVELOPMENT:${AndroidAcquisitionUiFixture.REAL_BLOCK_ID}"))
        compose.waitUntil(30_000) { compose.onAllNodes(match, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(match, useUnmergedTree = true).onFirst().performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("acquisition.preview") and
            SemanticsMatcher.expectValue(AcquisitionPhaseKey, AcquisitionUiPhase.ChoiceRequired.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
        compose.onAllNodesWithText("Цена не определена").onLast().assertExists()
        compose.onAllNodesWithText("Требуется явный выбор").onLast().assertExists()
        val definition = compose.activity.fixture!!.content.acquisition.definitions.getValue(DefinitionRef("dubl-3.69", AndroidAcquisitionUiFixture.REAL_BLOCK_ID))
        assertTrue(definition.repeatPolicy is RepeatPolicy.Unresolved)
        assertEquals(20L, (definition.priceRule as PriceRule.Fixed).xp)
        assertEquals(100L, balance(saved().ledger).xp); assertTrue(saved().receipts.isEmpty())
    }
    @Test fun manual_correction_needs_both_explicit_amounts_and_reason_then_a_separate_confirm() {
        ready()
        compose.onNodeWithTag("acquisition.manual.open").performScrollTo().performClick()
        compose.onNodeWithTag("acquisition.manual.review").assertIsNotEnabled()
        compose.onNodeWithTag("acquisition.manual.xp").performTextInput("-7")
        compose.onNodeWithTag("acquisition.manual.ap").performTextInput("0")
        compose.onNodeWithTag("acquisition.manual.review").assertIsNotEnabled()
        compose.onNodeWithTag("acquisition.manual.reason").performTextInput("SYNTHETIC_RULE explicit correction")
        compose.onNodeWithTag("acquisition.manual.review").assertIsEnabled().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("acquisition.manual.confirm").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(100L, balance(saved().ledger).xp)
        compose.onNodeWithTag("acquisition.manual.confirm").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("acquisition.receipt").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(93L, balance(saved().ledger).xp)
        assertEquals(1, saved().receipts.size)
    }

    @Test fun stale_review_stays_open_and_requires_new_review_without_an_extra_debit() {
        openSynthetic()
        val fixture = compose.activity.fixture!!
        runBlocking {
            val coordinator = CharacterMutationCoordinator(fixture.store, { fixture.content.acquisition }) { "external-fixture" }
            val state = saved()
            assertTrue(coordinator.mutate(CanonicalMutation("external-grant", "ui-A", state.revision,
                MutationPayload.Grant(Amount(1, 0)), "SYNTHETIC_RULE concurrent change")) is MutationResult.Applied)
        }
        compose.onNodeWithTag("acquisition.confirm").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("acquisition.refresh").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("acquisition.preview").assertExists()
        compose.onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
        assertEquals(101L, balance(saved().ledger).xp)
        compose.onNodeWithTag("acquisition.refresh").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("acquisition.preview") and
            SemanticsMatcher.expectValue(AcquisitionPhaseKey, AcquisitionUiPhase.Ready.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("acquisition.confirm").assertIsEnabled()
    }

    @Test fun actual_character_selection_never_transfers_the_closed_a_review_to_b() {
        openSynthetic()
        val fixture = compose.activity.fixture!!
        val old = checkNotNull(fixture.controller.acquisitionUiModel).key
        compose.onNodeWithTag("acquisition.cancel").performClick()
        compose.onNodeWithTag("navigation.characters").performClick()
        compose.onAllNodesWithTag("character.select").onLast().performScrollTo().performClick()
        compose.waitUntil(30_000) { fixture.controller.active.id == "ui-B" }
        compose.onAllNodesWithTag("acquisition.preview").assertCountEquals(0)
        assertTrue(runCatching { runBlocking { fixture.controller.acquisition.confirm(old) } }.isFailure)
        val b = runBlocking { (fixture.store.load("ui-B") as LoadResult.Loaded).state }
        assertEquals(100L, balance(b.ledger).xp); assertTrue(b.receipts.isEmpty())
        assertEquals(100L, balance(saved().ledger).xp)
    }

    @Test fun fresh_funding_is_reviewed_once_and_never_reinterprets_the_old_opening() {
        ready()
        val fixture = compose.activity.fixture!!
        compose.onNodeWithTag("navigation.characters").performClick()
        compose.onNodeWithTag("acquisition.funding.open").performScrollTo().performClick()
        compose.onNodeWithTag("acquisition.funding.review").assertIsNotEnabled()
        compose.onNodeWithTag("acquisition.funding.xp").performTextInput("2500")
        compose.onNodeWithTag("acquisition.funding.review").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag("acquisition.preview") and
            SemanticsMatcher.expectValue(AcquisitionPhaseKey, AcquisitionUiPhase.Ready.name)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Начальный ОСп: 2").assertExists()
        assertEquals(2, runBlocking { fixture.store.characterIds().size })
        compose.onNodeWithTag("acquisition.confirm").performClick()
        compose.waitUntil(30_000) { fixture.controller.active.id !in setOf("ui-A", "ui-B") }
        val id = fixture.controller.active.id
        val fresh = runBlocking { (fixture.store.load(id) as LoadResult.Loaded).state }
        assertEquals(Amount(2500, 2), balance(fresh.ledger))
        assertEquals(3, runBlocking { fixture.store.characterIds().size })
        assertEquals(100L, balance(saved().ledger).xp)
        assertTrue(saved().receipts.isEmpty())
    }

}
