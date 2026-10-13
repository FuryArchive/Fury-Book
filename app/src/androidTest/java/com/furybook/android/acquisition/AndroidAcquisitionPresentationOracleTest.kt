package com.furybook.android.acquisition

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.furybook.android.ui.acquisition.*
import com.furybook.dubl.acquisition.*
import com.furybook.ui.theme.DublTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Prepared, genuine Android renderer gate. Host execution is NOT evidence for this test.
 * Deterministic shards still stream every accepted row/assertion; only selected cases compose.
 * No source-unverified or uncommitted model receives a fabricated receipt.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAcquisitionPresentationOracleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun exact_card_preview_and_receipt_absence_for_every_selected_source_case() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val shardCount = args.getString("oracleShardCount")?.toInt() ?: 1
        val shard = args.getString("oracleShardIndex")?.toInt() ?: 0
        require(shardCount in 1..1024 && shard in 0 until shardCount)
        val fixture = runBlocking { AndroidAcquisitionUiFixture.open(instrumentation.targetContext,
            "matrix-${UUID.randomUUID()}", false) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val actions = AndroidAcquisitionActions(fixture.controller, scope)
        var model by mutableStateOf<AcquisitionUiModel?>(null)
        var preview by mutableStateOf(false)
        compose.setContent { DublTheme { model?.let { current ->
            if (preview) AcquisitionPurchaseSurface(current, actions) else Column { AcquisitionCardSummary(current) }
        } } }
        val seen = linkedSetOf<String>(); var observations = 0
        val manifest = instrumentation.targetContext.filesDir.resolve("acquisition-ui-tests/${fixture.runId}/renderer-shard-$shard-of-$shardCount.txt")
        try {
            manifest.bufferedWriter().use { evidence ->
                AcquisitionOracleInputs.stream(instrumentation.targetContext, instrumentation.context).forEachPresentation { id, current ->
                    if ((id.hashCode().toUInt() % shardCount.toUInt()).toInt() != shard) return@forEachPresentation
                    seen += id; observations++
                    val expectedPrice = when (val price = current.price) {
                        is AcquisitionDisplayedPrice.Known -> "${price.amount.xp} XP / ${price.amount.ap} ОСп"
                        is AcquisitionDisplayedPrice.Unknown -> price.message
                    }
                    compose.runOnIdle { model = current; preview = false }
                    compose.onNodeWithTag("acquisition.card").assertExists()
                    compose.onNodeWithTag("acquisition.price").assertTextEquals(expectedPrice)
                    compose.onNodeWithTag("acquisition.status").assertTextEquals(current.summary)
                    compose.runOnIdle { preview = true }
                    compose.onNodeWithTag("acquisition.preview").assertExists()
                    compose.onNodeWithTag("acquisition.price").assertTextEquals(expectedPrice)
                    if (current.canConfirm && current.phase == AcquisitionUiPhase.Ready)
                        compose.onNodeWithTag("acquisition.confirm").assertIsEnabled()
                    else compose.onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
                    compose.onAllNodesWithTag("acquisition.receipt").assertCountEquals(0)
                    evidence.write("$id|${current.phase}|receipt-absent\n"); evidence.flush()
                }
            }
            assertTrue("A configured shard must contain source cases", seen.isNotEmpty())
            if (shardCount == 1) assertEquals(83818, seen.size)
            println("T18A_RENDERER shard=$shard/$shardCount uniqueCases=${seen.size} observations=$observations manifest=${manifest.name}")
        } finally { scope.cancel(); fixture.controller.dispose() }
    }
}
