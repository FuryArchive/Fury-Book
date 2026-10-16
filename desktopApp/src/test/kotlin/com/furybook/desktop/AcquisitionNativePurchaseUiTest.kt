package com.furybook.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.furybook.desktop.screens.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.*
import com.furybook.ui.theme.DublTheme
import org.junit.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** An actual current-book card uses the production root, shared planner, file store and history. */
@OptIn(ExperimentalTestApi::class)
class AcquisitionNativePurchaseUiTest {
    @Test fun trap_card_buys_named_branch_access_in_the_real_purchase_dialog() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
        val card="feat_ff2b2532be3cc900"
        val fixture=AcquisitionUiFixtures(allDefinitions=true,seed={ original -> original.copy(
            character=original.character.copy(attributes=original.character.attributes+(AttributeId.DEXTERITY to AttributeValue(4)))) })
        val state=fixture.app()
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        onNodeWithTag("navigation.DEVELOPMENT").performClick()
        onNodeWithText("Спец. ветки").performClick()
        onNodeWithTag("development.search").performTextInput("Быстрая установка")
        val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.$card"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        onNode(hasTestTag("acquisition.price") and hasAnyAncestor(hasTestTag("acquisition.preview")),useUnmergedTree=true)
            .assertTextEquals("30 XP / 2 ОСп")
        assertEquals(setOf(card,"access_d0fdba646d5da299"),state.acquisitionUiModel?.rows?.mapNotNull { it.definition?.entryId }?.toSet())
        onNodeWithTag("acquisition.confirm").assertIsEnabled().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        assertEquals(Amount(70,0),balance(fixture.saved().ledger))
        assertEquals(1,fixture.saved().character.development.getValue(card).rank)
        assertEquals(1,fixture.saved().instances.count { it.definition.entryId=="access_d0fdba646d5da299" })
    }

    @Test fun beast_nature_is_buyable_and_names_its_unautomated_effect() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
        val card="feat_fc1f69730433e92b"
        val animagia=DefinitionRef("dubl-3.69","feat_29ff1cb23df7879d")
        val fixture=AcquisitionUiFixtures(allDefinitions=true,seed={ original -> original.copy(
            character=original.character.copy(development=mapOf(animagia.entryId to OwnedDevelopment(4))),
            instances=original.instances+AcquisitionInstance("known-animagia",animagia,4,emptyMap(),null,AcquisitionOrigin.New)) })
        val state=fixture.app()
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        onNodeWithTag("navigation.DEVELOPMENT").performClick()
        onNodeWithText("Спец. ветки").performClick()
        onNodeWithTag("development.search").performTextInput("Звериная натура")
        val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.$card"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        onNode(hasTestTag("acquisition.effect.notice") and hasAnyAncestor(hasTestTag("acquisition.preview")),useUnmergedTree=true)
            .assertTextContains("способности освоенных форм выбираются отдельно",substring=true)
        onNode(hasTestTag("acquisition.price") and hasAnyAncestor(hasTestTag("acquisition.preview")),useUnmergedTree=true)
            .assertTextEquals("60 XP / 0 ОСп")
        onNodeWithTag("acquisition.confirm").assertIsEnabled().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        assertEquals(Amount(40,2),balance(fixture.saved().ledger))
        assertEquals(1,fixture.saved().character.development.getValue(card).rank)
        assertTrue(fixture.saved().instances.single { it.definition.entryId==card }.parameters.isEmpty())
    }

    @Test fun acrobatic_dodge_buys_access_and_predecessor_then_reopens_and_undoes() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=150.seconds) {
        val card="feat_7eece523ffdc8431"
        val fixture=AcquisitionUiFixtures(allDefinitions=true,seed={ state ->
            state.copy(character=state.character.copy(attributes=state.character.attributes+
                (AttributeId.DEXTERITY to AttributeValue(4))+(AttributeId.STRENGTH to AttributeValue(1)),
                skills=state.character.skills+("athletics" to CharacterSkill("athletics",definitionId="athletics",rank=4))),
                instances=state.instances+AcquisitionInstance("athletics",DefinitionRef("dubl-3.69","athletics"),4,emptyMap(),null,AcquisitionOrigin.New))
        })
        val before=fixture.saved()
        var state by mutableStateOf(fixture.app())
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        onNodeWithTag("navigation.DEVELOPMENT").performClick()
        onNodeWithText("Спец. ветки").performClick()
        onNodeWithTag("development.search").performTextInput("Акробатическое уклонение")
        val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.$card"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        onNode(hasTestTag("acquisition.price") and hasAnyAncestor(hasTestTag("acquisition.preview")),useUnmergedTree=true).assertTextEquals("80 XP / 1 ОСп")
        assertEquals(setOf("access_45edee695a349bf7","feat_4c85aa747f319702",card),state.acquisitionUiModel?.rows?.mapNotNull { it.definition?.entryId }?.toSet())
        AcquisitionUiEvidence.screenshot("native-acrobatics-preview",captureToImage())
        onNodeWithTag("acquisition.confirm").assertIsEnabled().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        assertEquals(Amount(20,1),balance(fixture.saved().ledger))
        onNodeWithTag("acquisition.receipt").assertExists()
        AcquisitionUiEvidence.screenshot("native-acrobatics-receipt",captureToImage())
        runOnUiThread { state.dispose();state=fixture.app() }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        waitUntil(timeoutMillis=30_000) { onAllNodes(hasTestTag("acquisition.balance") and hasText("20 XP / 1 ОСп")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1,fixture.saved().character.development.getValue(card).rank)
        onNodeWithTag("acquisition.history.open").performClick()
        onNodeWithTag("acquisition.undo").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        assertEquals(acquisitionProjectionCanonical(before),acquisitionProjectionCanonical(fixture.saved()))
    }
}
