package com.furybook.android.acquisition

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.furybook.dubl.acquisition.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Activity recreation is not OS process-death acceptance. The manual two-process gate is separate. */
@RunWith(AndroidJUnit4::class)
class AcquisitionUiRestartTest {
    @get:Rule val compose = createAndroidComposeRule<AcquisitionUiTestActivity>()
    @Test fun recreation_resumes_exact_fixture_and_does_not_reseed_opening() {
        compose.waitUntil(30_000) { compose.activity.fixture != null }
        val before = compose.activity.fixture!!
        val command = runBlocking {
            val target = AcquisitionTarget(DefinitionRef("dubl-3.69", AndroidAcquisitionUiFixture.SYNTHETIC_ID), "synthetic-instance", 1)
            val request = AcquisitionRequest("ui-A", listOf(target))
            val service = AcquisitionService(before.store, { before.content.acquisition }) { "unused" }
            val quote = service.preview(request)
            ConfirmAcquisition("restart-seed", "ui-A", request, quote.baseRevision, quote.previewToken).also {
                assertTrue(service.apply(it) is ApplyResult.Applied)
            }
        }
        val run = before.runId
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { compose.activity.fixture?.runId == run }
        val resumed = compose.activity.fixture!!
        assertNotSame(before.controller, resumed.controller)
        val saved = runBlocking { (resumed.store.load("ui-A") as LoadResult.Loaded).state }
        assertEquals(80L, balance(saved.ledger).xp)
        assertEquals(listOf(command.operationId), saved.receipts.map { it.operationId })
        compose.waitUntil(30_000) { compose.onAllNodesWithText("80 XP / 2 ОСп").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("acquisition.balance").assertTextContains("80 XP")
    }
}
