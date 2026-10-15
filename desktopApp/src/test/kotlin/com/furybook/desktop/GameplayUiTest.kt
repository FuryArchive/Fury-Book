package com.furybook.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.furybook.dubl.actions.CombatResources
import com.furybook.ui.theme.DublTheme
import org.junit.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Production sheet, native file store and actual Compose input; no controller-only acceptance. */
@OptIn(ExperimentalTestApi::class)
class GameplayUiTest {
    @Test fun migration_defense_refresh_and_main_metric_use_saved_events() = runDesktopComposeUiTest(width=1280,height=1100,testTimeout=150.seconds) {
        val fixture = AcquisitionUiFixtures(gameplay=true)
        val state = fixture.app()
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        onNodeWithText("Подготовка локальных правил").assertExists()
        onNodeWithText("Подготовить локальные действия").performMouseInput { click(center) }
        try { waitUntil(timeoutMillis=10_000) { state.gameplayMigration?.review != null } }
        catch (failure: Throwable) {
            println(onRoot().printToString())
            AcquisitionUiEvidence.screenshot("gameplay-migration-failure", captureToImage())
            throw failure
        }
        onNodeWithText("Создать копию и перейти / повторить").performClick()
        waitUntil(timeoutMillis=30_000) { fixture.saved().formatVersion == 2 }
        onNodeWithTag("character.sheet").performScrollToNode(hasText("Навыки и развитие"))
        waitForIdle()
        assertNull(state.acquisitionUiModel, "Viewing default groups must not open a mutation confirmation")
        onNodeWithTag("acquisition.preview").assertDoesNotExist()
        onNodeWithTag("character.sheet").performScrollToNode(hasText("Ходы и действия"))
        fun press(text: String) {
            waitUntil(timeoutMillis=30_000) { onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText(text).performScrollTo().performClick()
        }
        press("Ходы и действия")
        fun confirm() {
            waitUntil(timeoutMillis=30_000) { state.gameplayEvents?.review != null }
            val expected = fixture.saved().gameplay?.events?.timeline?.revision?.plus(1) ?: 1
            press("Записать / повторить")
            waitUntil(timeoutMillis=30_000) { fixture.saved().gameplay?.events?.timeline?.revision == expected }
            press("Закрыть")
        }
        press("Начать свой ход"); confirm()
        press("2 ОД"); press("Отложить 2 ОД для защиты"); confirm()
        waitUntil(timeoutMillis=30_000) { state.activeCombat == CombatResources(1,1,2) }
        onNodeWithTag("character.sheet").performScrollToNode(hasTestTag("sheet.metric.Защита"))
        onNode(hasText("12") and hasAnyAncestor(hasTestTag("sheet.metric.Защита")),useUnmergedTree=true).assertExists()
        AcquisitionUiEvidence.screenshot("gameplay-defense-active",captureToImage())
        onNodeWithTag("character.sheet").performScrollToNode(hasText("Завершить текущий ход"))
        press("Завершить текущий ход"); confirm()
        press("Начать свой ход"); confirm()
        waitUntil(timeoutMillis=30_000) { state.activeCombat == CombatResources(3,1,0) }
        onNodeWithTag("character.sheet").performScrollToNode(hasTestTag("sheet.metric.Защита"))
        onNode(hasText("10") and hasAnyAncestor(hasTestTag("sheet.metric.Защита")),useUnmergedTree=true).assertExists()
        assertTrue(fixture.saved().outbox.isEmpty())
        AcquisitionUiEvidence.screenshot("gameplay-defense-expired",captureToImage())
        state.dispose()
    }
}
