package com.furybook.android.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.furybook.android.data.CharacterRepository
import com.furybook.android.data.CharacterSheetExtrasRepository
import com.furybook.android.state.CharacterController
import com.furybook.dubl.model.*
import com.furybook.core.cloud.ObservingCharacterStore
import com.furybook.ui.theme.DublTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DevelopmentScreenStabilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedHealthPurchaseThroughRealAndroidScreen() = scenario(false, false)
    @Test fun repeatedPurchaseWithAvailabilityFilterAndChi() = scenario(true, false)
    @Test fun manualPurchaseWithAvailabilityFilterAndChi() = scenario(true, true)

    @Test fun slowPreparationWhilePurchasingHealth() = scenario(false, false, true)

    @Test fun bulkPurchaseAndUndoKeepSheetMounted() {
        val (controller, store) = openHealth()
        val before = compose.onNodeWithText("Что даёт").fetchSemanticsNode().id
        compose.onNode(hasText("Взять все оставшиеся ранги", substring = true)).performScrollTo().performClick()
        compose.onNode(hasText("Применить · 100 XP", substring = true)).performClick()
        compose.waitUntil(30000) { controller.active.developmentRank(DevelopmentEffectIds.INCREDIBLE_HEALTH) == 20 }
        assertEquals(before, compose.onNodeWithText("Что даёт").fetchSemanticsNode().id)
        compose.runOnIdle { check(controller.undoLast()) }
        compose.waitUntil(30000) { controller.active.developmentRank(DevelopmentEffectIds.INCREDIBLE_HEALTH) == 0 }
        assertEquals(before, compose.onNodeWithText("Что даёт").fetchSemanticsNode().id)
        assertEquals(0, store.load().activeCharacter.developmentRank(DevelopmentEffectIds.INCREDIBLE_HEALTH))
    }

    @Test fun currentRequirementsUpdateWithoutReplacingSheet() {
        val (controller, _) = openHealth()
        val before = compose.onNodeWithText("Что даёт").fetchSemanticsNode().id
        compose.runOnIdle { controller.changeAttribute(AttributeId.CONSTITUTION, -1) }
        compose.onNode(hasText("сейчас: 1")).assertExists()
        compose.onNode(hasText("Добрать и взять", substring = true)).assertExists()
        assertEquals(before, compose.onNodeWithText("Что даёт").fetchSemanticsNode().id)
        compose.runOnIdle { controller.changeAttribute(AttributeId.CONSTITUTION, 1) }
        compose.onNode(hasText("сейчас: 2")).assertExists()
        compose.onNode(hasText("Взять ранг", substring = true)).assertExists()
    }

    private fun openHealth(): Pair<CharacterController, CharacterRepository> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val character = DublCharacter("android-bulk", experience = 10000,
            attributes = defaultAttributes().mapValues { AttributeValue(base = 2) })
        val store = CharacterRepository(context)
        store.save(AppSnapshot(listOf(character), character.id))
        val controller = CharacterController(ObservingCharacterStore(store), CharacterSheetExtrasRepository(context))
        compose.setContent { DublTheme { FeatsScreen(controller, chiPackEnabled = false) } }
        compose.waitUntil(30000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Невероятное здоровье")
        val row = hasText("Невероятное здоровье") and hasClickAction() and !hasSetTextAction()
        compose.waitUntil(30000) { compose.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(row).onFirst().performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("Что даёт").fetchSemanticsNodes().isNotEmpty() }
        return controller to store
    }

    private fun scenario(available: Boolean, manual: Boolean, slow: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val character = DublCharacter("android-repro", experience = 10000,
            attributes = defaultAttributes().mapValues { AttributeValue(base = 2) })
        val store = CharacterRepository(context)
        store.save(AppSnapshot(listOf(character), character.id))
        val controller = CharacterController(ObservingCharacterStore(store), CharacterSheetExtrasRepository(context))
        compose.setContent { DublTheme { FeatsScreen(controller, chiPackEnabled = available) } }
        compose.waitUntil(30000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        if (available) compose.onNodeWithText("Можно взять").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Невероятное здоровье")
        compose.waitUntil(30000) { compose.onAllNodes(hasText("Невероятное здоровье") and hasClickAction() and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("Невероятное здоровье") and hasClickAction() and !hasSetTextAction()).onFirst().performScrollTo().performClick()
        for (rank in 1..20) {
            println("ANDROID_UI_BEGIN_RANK=$rank")
            compose.waitUntil(30000) { compose.onAllNodes(hasText("Взять ранг", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            val sheetNodeBefore = compose.onNodeWithText("Что даёт").fetchSemanticsNode().id
            if (manual) {
                if (compose.onAllNodesWithText("Дополнительно ▼").fetchSemanticsNodes().isNotEmpty()) {
                    compose.onNodeWithText("Дополнительно ▼").performScrollTo().performClick()
                }
                val label = if (rank == 1) "Получить вручную" else "+ ранг вручную"
                compose.onNode(hasText(label, substring = true)).performScrollTo().performClick()
            } else {
                compose.onNode(hasText("Взять ранг", substring = true)).performScrollTo().performClick()
                compose.waitUntil(30000) { compose.onAllNodes(hasText("Применить ·", substring = true)).fetchSemanticsNodes().isNotEmpty() }
                if (slow) {
                    val cacheField = Class.forName("com.furybook.android.ui.screens.FeatsScreenKt")
                        .getDeclaredField("developmentPreparationCache").apply { isAccessible = true }
                    val cache = checkNotNull(cacheField.get(null))
                    val locked = CountDownLatch(1)
                    val worker = thread(name = "SlowPreparationTest") {
                        synchronized(cache) {
                            locked.countDown()
                            Thread.sleep(250)
                        }
                    }
                    check(locked.await(5, TimeUnit.SECONDS))
                    compose.onNode(hasText("Применить ·", substring = true)).performClick()
                    worker.join(1000)
                } else {
                    compose.onNode(hasText("Применить ·", substring = true)).performClick()
                }
            }
            compose.waitUntil(30000) { controller.active.developmentRank(DevelopmentEffectIds.INCREDIBLE_HEALTH) == rank }
            compose.waitUntil(30000) { compose.onAllNodesWithText("Что даёт").fetchSemanticsNodes().isNotEmpty() }
            val sheetNodeAfter = compose.onNodeWithText("Что даёт").fetchSemanticsNode().id
            assertEquals("Purchasing rank $rank must not recreate the detail sheet", sheetNodeBefore, sheetNodeAfter)
            compose.onNode(hasText("ранг $rank/20", substring = true)).assertExists()
            assertEquals(rank, store.load().activeCharacter.developmentRank(DevelopmentEffectIds.INCREDIBLE_HEALTH))
            if (manual) compose.onNodeWithText("Дополнительно ▲").assertExists()
            println("ANDROID_UI_APPLIED_RANK=$rank sheet=$sheetNodeAfter")
        }
        println("ANDROID_UI_TWENTY_RANKS_OK")
    }
}
