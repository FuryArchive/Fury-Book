package com.furybook.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import com.furybook.dubl.data.DesktopAcquisitionWriteStage
import com.furybook.dubl.model.AttributeId
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.furybook.dubl.acquisition.*
import com.furybook.ui.theme.DublTheme
import com.furybook.desktop.screens.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Actual production root, existing screens and FuryDialog with native Skia input. */
@OptIn(ExperimentalTestApi::class)
class AcquisitionUiTest {
    @Test fun synthetic20_actual_card_purchase_receipt_restart_and_undo() = runDesktopComposeUiTest(width=1280,height=900,testTimeout=120.seconds) {
        val fixture = AcquisitionUiFixtures()
        AcquisitionUiEvidence.envelope("synthetic-before",fixture.raw())
        var state by mutableStateOf(fixture.app())
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        waitUntil(timeoutMillis=30_000) { onAllNodes(hasTestTag("acquisition.balance") and hasText("100 XP / 2 ОСп")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("navigation.DEVELOPMENT").performClick()
        onNodeWithTag("development.search").performTextInput("Проверочный")
        val open = hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.${AcquisitionUiFixtures.SYNTHETIC_ID}"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performMouseInput { click(center) }
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase == AcquisitionUiPhase.Ready }
        onNode(hasTestTag("acquisition.price") and hasAnyAncestor(hasTestTag("acquisition.preview")),useUnmergedTree=true).assertTextEquals("20 XP / 0 ОСп")
        AcquisitionUiEvidence.screenshot("synthetic-preview",captureToImage())
        onNodeWithTag("acquisition.confirm").assertIsEnabled().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase == AcquisitionUiPhase.Committed }
        onNodeWithTag("acquisition.receipt").assertExists()
        AcquisitionUiEvidence.screenshot("synthetic-receipt",captureToImage())
        AcquisitionUiEvidence.envelope("synthetic-committed",fixture.raw())
        assertEquals(Amount(80,2),balance(fixture.saved().ledger)); assertEquals(1,fixture.saved().receipts.size)
        runOnUiThread { state.dispose(); state=fixture.app() }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        waitUntil(timeoutMillis=30_000) { onAllNodes(hasTestTag("acquisition.balance") and hasText("80 XP / 2 ОСп")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("acquisition.balance").assertTextEquals("80 XP / 2 ОСп")
        AcquisitionUiEvidence.screenshot("synthetic-restarted",captureToImage())
        AcquisitionUiEvidence.envelope("synthetic-restarted",fixture.raw())
        onNodeWithTag("acquisition.history.open").performClick()
        onNodeWithTag("acquisition.undo").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase == AcquisitionUiPhase.Ready }
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase == AcquisitionUiPhase.Committed }
        assertEquals(Amount(100,2),balance(fixture.saved().ledger)); assertEquals(ReceiptStatus.Undone,fixture.saved().receipts.first().status)
        AcquisitionUiEvidence.screenshot("synthetic-undone",captureToImage())
        AcquisitionUiEvidence.envelope("synthetic-undone",fixture.raw())
    }
    @Test fun protected_defaultfalse_root_is_readable_and_cannot_confirm_funding() = runDesktopComposeUiTest(width=1100,height=800,testTimeout=60.seconds) {
        val fixture=AcquisitionUiFixtures(writable=false); val state=fixture.app(); val before=fixture.raw()
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        waitUntil(timeoutMillis=30_000) { onAllNodes(hasTestTag("acquisition.balance") and hasText("100 XP / 2 ОСп")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("acquisition.balance").assertTextEquals("100 XP / 2 ОСп")
        onNodeWithTag("navigation.CHARACTERS").performClick()
        onNodeWithTag("acquisition.funding.open").performClick()
        onNodeWithTag("acquisition.funding.xp").performTextInput("2500")
        onNodeWithTag("acquisition.funding.review").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.funding != null }
        onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
        assertEquals(before,fixture.raw()); assertTrue(state.readOnlyCanonicalMode)
    }
    @Test fun real_block_is_settled_choice_not_synthetic20_and_cancel_leaves_bytes() = runDesktopComposeUiTest(width=1280,height=900,testTimeout=90.seconds) {
        val fixture=AcquisitionUiFixtures(); val state=fixture.app(); val before=fixture.raw()
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        waitUntil(timeoutMillis=30_000) { onAllNodes(hasTestTag("acquisition.balance") and hasText("100 XP / 2 ОСп")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("navigation.DEVELOPMENT").performClick()
        onNodeWithTag("development.search").performTextInput("Блокирование")
        val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.${AcquisitionUiFixtures.REAL_BLOCK_ID}"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.ChoiceRequired }
        onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
        onNode(hasTestTag("acquisition.price") and hasAnyAncestor(hasTestTag("acquisition.preview")),useUnmergedTree=true).assertTextEquals("Цена не определена")
        onNodeWithTag("acquisition.cancel").performClick(); assertEquals(before,fixture.raw())
    }
    private fun ComposeUiTest.mount(state:DesktopAppState) {
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady && onAllNodesWithTag("acquisition.balance").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun ComposeUiTest.openSynthetic(state:DesktopAppState, expected:AcquisitionUiPhase=AcquisitionUiPhase.Ready) {
        onNodeWithTag("navigation.DEVELOPMENT").performClick()
        onNodeWithTag("development.search").performTextReplacement("Проверочный")
        val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.${AcquisitionUiFixtures.SYNTHETIC_ID}"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==expected }
    }
    @Test fun cancel_and_escape_use_real_dialog_input_without_financial_id() = runDesktopComposeUiTest(width=1100,height=800,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val state=f.app();val before=f.raw();mount(state);openSynthetic(state)
        onNodeWithTag("acquisition.cancel").performClick()
        assertEquals(before,f.raw());assertNull(state.acquisition.retainedOperationId)
        openSynthetic(state)
        onNodeWithTag("acquisition.preview").performKeyInput { pressKey(Key.Escape) }
        onAllNodesWithTag("acquisition.preview").assertCountEquals(0)
        assertEquals(before,f.raw());assertNull(state.acquisition.retainedOperationId)
    }
    @Test fun manual_input_rejects_blank_and_overflow_then_records_exact_signed_delta() = runDesktopComposeUiTest(width=1100,height=900,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val state=f.app();val before=f.saved();mount(state)
        onNodeWithTag("acquisition.manual.open").performClick()
        onNodeWithTag("acquisition.manual.review").assertIsNotEnabled()
        onNodeWithTag("acquisition.manual.xp").performTextInput("9223372036854775808")
        onNodeWithTag("acquisition.manual.ap").performTextInput("0")
        onNodeWithTag("acquisition.manual.reason").performTextInput("Проверка явной поправки")
        onNodeWithTag("acquisition.manual.review").assertIsNotEnabled()
        onNodeWithTag("acquisition.manual.xp").performTextReplacement("-10")
        onNodeWithTag("acquisition.manual.review").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        assertEquals(before,f.saved())
        AcquisitionUiEvidence.screenshot("manual-review",captureToImage())
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        assertEquals(Amount(90,2),balance(f.saved().ledger));assertEquals(before.character,f.saved().character)
        assertEquals(1,f.saved().receipts.size)
    }
    @Test fun explicit_new_funding_has_one_creation_and_does_not_refill_existing_heads() = runDesktopComposeUiTest(width=1100,height=900,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val state=f.app();val beforeA=f.raw();val beforeB=f.raw("ui-B");mount(state)
        onNodeWithTag("navigation.CHARACTERS").performClick();onNodeWithTag("acquisition.funding.open").performClick()
        onNodeWithTag("acquisition.funding.xp").performTextInput("2500");onNodeWithTag("acquisition.funding.review").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        assertEquals(2L,state.acquisitionUiModel?.funding?.initialAP)
        AcquisitionUiEvidence.screenshot("funding-review",captureToImage())
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.snapshot.activeCharacterId !in setOf("ui-A","ui-B") }
        val id=state.snapshot.activeCharacterId
        assertEquals(Amount(2500,2),balance(f.saved(id).ledger));assertEquals(3,runBlocking { f.store.characterIds().size })
        assertEquals(beforeA,f.raw());assertEquals(beforeB,f.raw("ui-B"))
    }
    @Test fun synthetic_price_staleness_stays_open_until_explicit_new_review() = runDesktopComposeUiTest(width=1100,height=900,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val state=f.app();mount(state);openSynthetic(state)
        val before=f.raw();val ref=DefinitionRef("dubl-3.69",AcquisitionUiFixtures.SYNTHETIC_ID);val definition=f.catalog.definitions.getValue(ref)
        f.catalog=f.catalog.copy(catalogRevision=f.catalog.catalogRevision+":changed",definitions=f.catalog.definitions+(ref to definition.copy(priceRule=PriceRule.Fixed(30,0,definition.sourceRef))))
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Stale }
        onNodeWithTag("acquisition.preview").assertExists();onNodeWithTag("acquisition.confirm").assertIsNotEnabled();assertEquals(before,f.raw())
        AcquisitionUiEvidence.screenshot("stale-price",captureToImage())
        onNodeWithTag("acquisition.refresh").performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        assertEquals(Amount(30,0),(state.acquisitionUiModel?.price as AcquisitionDisplayedPrice.Known).amount)
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        assertEquals(Amount(70,2),balance(f.saved().ledger))
    }
    @Test fun repeated_input_and_character_switch_keep_pending_original_scope() = runDesktopComposeUiTest(width=1280,height=900,testTimeout=120.seconds) {
        val armed=AtomicBoolean(false);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val f=AcquisitionUiFixtures(fault={stage -> if(stage==DesktopAcquisitionWriteStage.BeforeReplace && armed.compareAndSet(true,false)) { entered.countDown();check(release.await(45,TimeUnit.SECONDS)) } })
        val state=f.app();mount(state);openSynthetic(state);armed.set(true)
        try {
            onNodeWithTag("acquisition.confirm").performMouseInput { click(center);click(center) }
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Applying && entered.count==0L }
            val id=state.acquisition.retainedOperationId
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
            onNodeWithTag("acquisition.cancel").performClick()
            onNodeWithTag("navigation.CHARACTERS").performClick();onNodeWithTag("character.ui-B").performClick()
            waitUntil(timeoutMillis=30_000) { state.snapshot.activeCharacterId=="ui-B" }
            assertNull(state.acquisitionUiModel);assertEquals(Amount(100,2),balance(f.saved("ui-B").ledger))
            openSynthetic(state);onNodeWithTag("acquisition.confirm").performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Applying }
            val bId=state.acquisition.retainedOperationId;assertNotNull(bId);assertNotEquals(id,bId)
            release.countDown()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
            assertEquals(bId,state.acquisition.retainedOperationId);assertEquals(1,f.saved().receipts.size)
            assertEquals(1,f.saved("ui-B").receipts.size);assertEquals(Amount(80,2),balance(f.saved("ui-B").ledger))
            onNodeWithTag("acquisition.cancel").performClick();onNodeWithTag("navigation.CHARACTERS").performClick()
            onNodeWithTag("character.ui-A").performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
            assertEquals(id,state.acquisition.retainedOperationId)
            onNodeWithTag("acquisition.reopen").performClick()
            onNodeWithTag("acquisition.receipt").assertExists()
            assertEquals(Amount(80,2),balance(f.saved().ledger));assertEquals(1,f.saved().receipts.size)
        } finally { release.countDown() }
    }
    @Test fun actual_store_failure_before_and_after_replace_reconciles_same_id() {
        for(stage in listOf(DesktopAcquisitionWriteStage.BeforeReplace,DesktopAcquisitionWriteStage.AfterReplace)) runDesktopComposeUiTest(width=1100,height=900,testTimeout=120.seconds) {
            val armed=AtomicBoolean(false)
            val f=AcquisitionUiFixtures(fault={ current -> if(current==stage && armed.compareAndSet(true,false)) throw java.io.IOException("Synthetic boundary fault $stage") })
            val state=f.app();mount(state);openSynthetic(state);armed.set(true)
            onNodeWithTag("acquisition.confirm").performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Failed }
            val id=state.acquisition.retainedOperationId;assertNotNull(id)
            val recovered=f.saved()
            assertEquals(if(stage==DesktopAcquisitionWriteStage.BeforeReplace) Amount(100,2) else Amount(80,2),balance(recovered.ledger))
            AcquisitionUiEvidence.screenshot("failure-${stage.name}",captureToImage())
            onNodeWithTag(if(stage==DesktopAcquisitionWriteStage.BeforeReplace) "acquisition.retry" else "acquisition.reconcile").performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
            assertEquals(id,state.acquisition.retainedOperationId);assertEquals(Amount(80,2),balance(f.saved().ledger));assertEquals(1,f.saved().receipts.size)
        }
    }
    @Test fun source_attribute_step_uses_actual_sheet_button_and_saved_historical_evidence() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val state=f.app();mount(state)
        val plus=hasText("+") and hasAnyAncestor(hasTestTag("attribute.STRENGTH"))
        onNode(plus,useUnmergedTree=true).performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        onNode(hasTestTag("acquisition.price") and hasAnyAncestor(hasTestTag("acquisition.preview")),useUnmergedTree=true).assertTextEquals("55 XP / 0 ОСп")
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.receipt?.savedOperation?.evidence is AcquisitionSavedEvidence.Recorded }
        assertEquals(Amount(45,2),balance(f.saved().ledger));assertEquals(1,f.saved().character.attributes.getValue(AttributeId.STRENGTH).base)
        onNodeWithTag("acquisition.receipt.operation").assertExists()
        val saved=assertIs<AcquisitionSavedEvidence.Recorded>(state.acquisitionUiModel?.receipt?.savedOperation?.evidence)
        assertTrue(saved.prices.flatMap { listOf(it.source)+it.componentSources }.all { it.documentId!="SYNTHETIC_RULE" })
        AcquisitionUiEvidence.screenshot("source-attribute-receipt",captureToImage())
        AcquisitionUiEvidence.envelope("source-attribute-receipt",f.raw())
    }
    @Test fun typed_ability_choice_preserves_explicit_instance_and_never_defaults() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val ref=DefinitionRef("dubl-3.69",AcquisitionUiFixtures.SYNTHETIC_ID)
        val old=f.catalog.definitions.getValue(ref)
        f.catalog=f.catalog.copy(definitions=f.catalog.definitions+(ref to old.copy(abilityOptionIds=setOf("chi","morale"),
            priceRule=PriceRule.ByOption(mapOf("chi" to PriceRule.Fixed(0,1,old.sourceRef),"morale" to PriceRule.Fixed(20,0,old.sourceRef)),old.sourceRef))))
        val state=f.app();mount(state)
        onNodeWithTag("navigation.DEVELOPMENT").performClick();onNodeWithTag("development.search").performTextInput("Проверочный")
        val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.${AcquisitionUiFixtures.SYNTHETIC_ID}"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.ChoiceRequired }
        assertNull(state.acquisitionUiModel?.request?.targets?.single()?.optionId);onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
        onNodeWithText("Новый экземпляр с этими параметрами").performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.single()?.instanceId!=null }
        val instance=state.acquisitionUiModel?.request?.targets?.single()?.instanceId
        waitUntil(timeoutMillis=30_000) { onAllNodesWithText("Ци: 0 XP / 1 ОСп").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Ци: 0 XP / 1 ОСп").performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        assertEquals(instance,state.acquisitionUiModel?.request?.targets?.single()?.instanceId)
        assertEquals("chi",state.acquisitionUiModel?.request?.targets?.single()?.optionId)
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        assertEquals(Amount(100,1),balance(f.saved().ledger));assertEquals("chi",f.saved().instances.single { it.instanceId==instance }.abilityOptionId)
    }
    @Test fun existing_skills_and_magic_cards_open_typed_source_reviews_without_mutation() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val state=f.app();val before=f.raw();mount(state)
        for((section,id,kind) in listOf(Triple("SKILLS","unarmed",AcquisitionKind.SKILL),Triple("MAGIC","mana:rank",AcquisitionKind.MANA))) {
            onNodeWithTag("navigation.$section").performClick()
            val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.$id"))
            waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
            onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.singleOrNull()?.definition?.entryId==id }
            val target=state.acquisitionUiModel!!.request!!.targets.single()
            assertEquals(kind,f.catalog.definitions.getValue(target.definition).kind)
            onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
        }
    }
    @Test fun reviewed_rows_disclose_exact_partial_prices_without_inventing_unknown_total() = runDesktopComposeUiTest(width=1000,height=900,testTimeout=60.seconds) {
        val scope=AcquisitionScope("fixture","hero",0,0,0)
        val model=AcquisitionUiModel(scope,AcquisitionQuoteKey("rows",0),AcquisitionUiPhase.Blocked,"Частично известная цена",
            rows=listOf(AcquisitionDisplayRow(null,"Цель",0,1,AcquisitionDisplayedPrice.Known(Amount(20,1)),true),
                AcquisitionDisplayRow(null,"Требование",0,1,AcquisitionDisplayedPrice.Unknown(),false),
                AcquisitionDisplayRow(null,"Исключённая цель",null,1,AcquisitionDisplayedPrice.Known(Amount.ZERO),true,excludedTarget=true)))
        setContent { DublTheme { DesktopVisualTheme { AcquisitionPlanContent(model) } } }
        onAllNodesWithTag("acquisition.row.price").assertCountEquals(3)
        onAllNodesWithTag("acquisition.row.price")[0].assertTextEquals("20 XP / 1 ОСп")
        onAllNodesWithTag("acquisition.row.price")[1].assertTextEquals("Цена не определена")
        onAllNodesWithTag("acquisition.row.price")[2].assertTextEquals("0 XP / 0 ОСп")
        onNodeWithTag("acquisition.price").assertTextEquals("Цена не определена")
        onNodeWithText("Исключённая цель · не приобретается").assertExists()
    }
    @Test fun canonical_chi_navigation_preserves_existing_technique_information() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures(allDefinitions=true);val state=f.app()
        try {
            state.setChiPackActive(true);mount(state)
            onNodeWithTag("navigation.DEVELOPMENT").performClick();onNodeWithText("ЦИ",useUnmergedTree=true).performClick()
            val first=state.chiCatalog.techniques.first()
            onNodeWithText("${first.name} · ${first.school}").performScrollTo().assertIsDisplayed()
            onNodeWithText(first.effect).assertExists()
            assertTrue(f.saved().receipts.isEmpty())
        } finally { state.setChiPackActive(false) }
    }
    private fun ComposeUiTest.openDevelopment(state:DesktopAppState,id:String) {
        onNodeWithTag("navigation.DEVELOPMENT").performClick()
        val entry=checkNotNull(state.developmentCatalog.byId(id))
        val tab=when { entry.isChiDevelopment -> "ЦИ";entry.isMartialArt -> "Боевые искусства";entry.isSpecialDevelopment -> "Спец. ветки";else -> "Обычные" }
        onNodeWithText(tab,useUnmergedTree=true).performClick()
        onNodeWithTag("development.search").performTextReplacement(entry.name)
        val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.$id"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodes(open,useUnmergedTree=true).onFirst().performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.singleOrNull()?.definition?.entryId==id && state.acquisitionUiModel?.phase!=AcquisitionUiPhase.Loading }
    }
    private fun sourceOwned(id:String,rank:Int=1,option:String?=null)=AcquisitionInstance("owned/$id",DefinitionRef("dubl-3.69",id),rank,emptyMap(),option,AcquisitionOrigin.New)
    @Test fun source_combat_cries_morale_one_and_chi_two_ap_are_explicit() {
        for((option,label,cost) in listOf(Triple("morale","Мораль",1L),Triple("chi","Ци",2L)))
            runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
                val f=AcquisitionUiFixtures(allDefinitions=true,seed={ original -> original.copy(
                    character=original.character.copy(attributes=AttributeId.entries.associateWith { com.furybook.dubl.model.AttributeValue(6) }),
                    instances=listOf(sourceOwned("feat_a700b29906107b2b"))) })
                val state=f.app();mount(state);openDevelopment(state,"access_17f99cc35c559d82")
                assertNull(state.acquisitionUiModel?.request?.targets?.single()?.optionId)
                onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
                onNodeWithText("Новый экземпляр с этими параметрами").performScrollTo().performClick()
                val selection=hasTestTag("acquisition.choice") and hasText("$label: 0 XP / $cost ОСп")
                waitUntil(timeoutMillis=30_000) { onAllNodes(selection).fetchSemanticsNodes().isNotEmpty() }
                onNode(selection).performScrollTo().performClick()
                waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
                val instance=state.acquisitionUiModel!!.request!!.targets.single().instanceId
                onNodeWithTag("acquisition.confirm").performClick()
                waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
                assertEquals(Amount(100,2-cost),balance(f.saved().ledger))
                assertEquals(option,f.saved().instances.single { it.instanceId==instance }.abilityOptionId)
                assertEquals(Amount(0,cost),f.saved().receipts.single().result.cost)
            }
    }
    @Test fun source_distinct_cries_count_instances_not_a_single_rank_three() {
        for(count in listOf(1,2,3)) runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
            val source=listOf(sourceOwned("access_17f99cc35c559d82",option="morale"),sourceOwned("feat_a700b29906107b2b"),
                sourceOwned("feat_9813ba6aff7624ad",if(count==1) 3 else 1),sourceOwned("intimidation",5),sourceOwned("eloquence",4),sourceOwned("leadership",4))+
                (if(count>=2) listOf(sourceOwned("feat_ff626e2585de2dc7")) else emptyList())+
                (if(count>=3) listOf(sourceOwned("feat_e000383baa5b3afa")) else emptyList())
            val f=AcquisitionUiFixtures(allDefinitions=true,seed={ original -> original.copy(character=original.character.copy(
                attributes=AttributeId.entries.associateWith { com.furybook.dubl.model.AttributeValue(6) },creationComplete=true),instances=source,
                creationPhase=RequirementNode.CreationPhase.Phase.Finalized,ledger=original.ledger.copy(opening=original.ledger.opening.copy(legacyStage=RequirementNode.CreationPhase.Phase.Finalized))) })
            val state=f.app();val before=f.raw();mount(state);openDevelopment(state,if(count==3) "feat_127f2d478d76e66d" else "feat_8b6f3261d7c5c8ea")
            fun countProof(nodes:List<AcquisitionRequirementView>):AcquisitionRequirementView?=nodes.firstNotNullOfOrNull { if(it.label=="Различные приобретения") it else countProof(it.children) }
            val proof=assertNotNull(countProof(state.acquisitionUiModel!!.baseRequirements))
            val actual=if(count==1) 0 else count // A rank-three instance exceeds this definition's max rank one; it is not a valid owned cry.
            assertEquals(RequirementValue.Number(actual),proof.actual)
            assertEquals(RequirementValue.Number(if(count==3) 3 else 2),proof.required)
            onAllNodesWithText("Факт: $actual · Требуется: ${if(count==3) 3 else 2}").onFirst().assertExists()
            // Source count is known, but new-instance identity is still explicitly unresolved.
            assertEquals(AcquisitionUiPhase.ChoiceRequired,state.acquisitionUiModel?.phase)
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
        }
    }
    @Test fun compact_input_insufficient_xp_and_exact_zero_are_not_conflated() {
        for(xp in listOf(19L,20L)) runDesktopComposeUiTest(width=800,height=900,testTimeout=90.seconds) {
            val f=AcquisitionUiFixtures(seed={it.copy(ledger=it.ledger.copy(opening=it.ledger.opening.copy(balance=Amount(xp,2))))})
            val state=f.app();val before=f.raw();mount(state);openSynthetic(state,if(xp==19L) AcquisitionUiPhase.Blocked else AcquisitionUiPhase.Ready)
            if(xp==19L) { onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw()) }
            else { onNodeWithTag("acquisition.confirm").performClick();waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed };assertEquals(Amount(0,2),balance(f.saved().ledger)) }
        }
    }
    @Test fun explicit_zero_manual_grant_replay_and_creation_completion_do_not_refill() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
        val f=AcquisitionUiFixtures();var state by mutableStateOf(f.app())
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady && onAllNodesWithTag("acquisition.balance").fetchSemanticsNodes().isNotEmpty() }
        fun fill(xp:String,ap:String,reason:String) {
            onNodeWithTag("acquisition.manual.xp").performTextInput(xp);onNodeWithTag("acquisition.manual.ap").performTextInput(ap)
            onNodeWithTag("acquisition.manual.reason").performTextInput(reason);onNodeWithTag("acquisition.manual.review").performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready };onNodeWithTag("acquisition.confirm").performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed };onNodeWithTag("acquisition.cancel").performClick()
        }
        onNodeWithTag("acquisition.manual.open").performClick();fill("0","0","Явная нулевая поправка")
        assertEquals(Amount(100,2),balance(f.saved().ledger))
        onNodeWithText("Выдать").performClick();fill("5","0","Награда за сессию")
        val operation=state.acquisition.retainedOperationId;val count=f.saved().receipts.size
        onNodeWithTag("acquisition.reopen").performClick();onNodeWithTag("acquisition.reconcile").performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        assertEquals(operation,state.acquisition.retainedOperationId);assertEquals(count,f.saved().receipts.size)
        onNodeWithTag("acquisition.cancel").performClick();onNodeWithText("Завершить создание").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready };onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { f.saved().character.creationComplete };onNodeWithTag("acquisition.cancel").performClick()
        waitUntil(timeoutMillis=30_000) { state.activeCharacter.creationComplete }
        onNodeWithText("Завершить создание").assertIsNotEnabled();assertEquals(Amount(105,2),balance(f.saved().ledger))
        assertEquals(RequirementNode.CreationPhase.Phase.Finalized,f.saved().creationPhase)
        val before=f.raw();runOnUiThread { state.dispose();state=f.app() }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady && onAllNodes(hasTestTag("acquisition.balance") and hasText("105 XP / 2 ОСп")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Завершить создание").assertIsNotEnabled();assertEquals(before,f.raw())
        onNodeWithTag("acquisition.history.open").performClick();onNodeWithTag("acquisition.undo").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
        // CompleteCreation is a valid invertible operation, not an invented Undo barrier.
        onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
    }
    @Test fun real_school_creation_raise_spell_and_chi_bonus_settle_blocked_without_writes() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=150.seconds) {
        val school=DefinitionRef("dubl-3.69","magic-school:боевая магия")
        val f=AcquisitionUiFixtures(allDefinitions=true,seed={it.copy(instances=it.instances+AcquisitionInstance("saved-school",school,1,emptyMap(),null,AcquisitionOrigin.New))})
        val state=f.app();f.enableChi(state);val before=f.raw()
        try {
            mount(state);onNodeWithTag("navigation.MAGIC").performClick();onNodeWithText("+ Школа").performClick()
            onNode(hasTestTag("acquisition.open") and SemanticsMatcher.expectValue(AcquisitionDefinitionKey,"dubl-3.69:magic-school:воплощение"),useUnmergedTree=true).performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.singleOrNull()?.definition?.entryId=="magic-school:воплощение" }
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
            val schoolOpen=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.saved-school"))
            waitUntil(timeoutMillis=30_000) { onAllNodes(schoolOpen and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() };onNode(schoolOpen,useUnmergedTree=true).performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.singleOrNull()?.instanceId=="saved-school" }
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
            onNodeWithText("Каталог",useUnmergedTree=true).performClick();onNode(hasSetTextAction() and hasText("Поиск")).performTextInput("Агония")
            onNodeWithTag("acquisition.spell.spell_54234bfde5761792").performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.singleOrNull()?.definition?.entryId=="spell_54234bfde5761792" }
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
            onNodeWithTag("navigation.DEVELOPMENT").performClick();onNodeWithText("ЦИ",useUnmergedTree=true).performClick()
            val chiOpen=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.chi:bonus-ranks"))
            waitUntil(timeoutMillis=30_000) { onAllNodes(chiOpen and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() };onNode(chiOpen,useUnmergedTree=true).performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.singleOrNull()?.definition?.entryId=="chi:bonus-ranks" }
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
        } finally { state.setChiPackActive(false) }
    }
    @Test fun unavailable_source_spell_never_fabricates_true_false_absent_or_ambiguous_proof() {
        for(mode in listOf("true","false","absent","ambiguous")) runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
            val spell=com.furybook.dubl.model.KnownSpell("recorded-spell",catalogId="spell_e3bebd434e638daa",learned=mode!="false")
            val f=AcquisitionUiFixtures(allDefinitions=true,seed={it.copy(character=it.character.copy(magic=it.character.magic.copy(spells=when(mode) {
                "absent" -> emptyList();"ambiguous" -> listOf(spell,spell.copy(uid="duplicate-spell"));else -> listOf(spell)
            })))})
            val state=f.app();val before=f.raw();mount(state);openDevelopment(state,"feat_5368c7691988fd41")
            fun find(nodes:List<AcquisitionRequirementView>):AcquisitionRequirementView?=nodes.firstNotNullOfOrNull { if(it.nodeId=="feat_5368c7691988fd41/requirements/1") it else find(it.children) }
            val proof=assertNotNull(find(state.acquisitionUiModel!!.baseRequirements))
            assertEquals(com.furybook.dubl.acquisition.RequirementStatus.SourceUnresolved,proof.status)
            assertNull(proof.actual,"Unavailable source cannot become a fabricated learned fact in $mode")
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
        }
    }
    @Test fun source_parameter_instances_preserve_identity_without_granting_unresolved_new_holdings() {
        for(mode in listOf("new","existing","renamed","distinct","empty","unknown","wrong-type")) runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
            val ref=DefinitionRef("dubl-3.69","knowledge_template")
            val params=when(mode) { "empty" -> emptyMap();"unknown" -> mapOf("specialization" to "unknown-source-id");"wrong-type" -> mapOf("weaponId" to "sword");else -> mapOf("specialization" to "spec:a") }
            val first=AcquisitionInstance("stable-knowledge",ref,1,params,null,AcquisitionOrigin.New)
            val f=AcquisitionUiFixtures(seed={it.copy(instances=when(mode) { "new" -> emptyList();"distinct" -> listOf(first,first.copy(instanceId="other-knowledge",parameters=mapOf("specialization" to "spec:b")));else -> listOf(first) },
                character=it.character.copy(skills=if(mode=="renamed") mapOf("stable-knowledge" to com.furybook.dubl.model.CharacterSkill("stable-knowledge","knowledge_template",name="Переименованное знание",rank=1)) else emptyMap()))})
            val state=f.app();val before=f.raw();mount(state);onNodeWithTag("navigation.SKILLS").performClick();onNodeWithText("Специализация").performClick()
            onNode(hasTestTag("acquisition.open") and SemanticsMatcher.expectValue(AcquisitionDefinitionKey,"dubl-3.69:knowledge_template"),useUnmergedTree=true).performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.singleOrNull()?.definition==ref }
            if(mode!="new") {
                val choices=onAllNodes(hasTestTag("acquisition.choice") and hasText("ранг 1",substring=true))
                choices.onFirst().performScrollTo().performClick()
                waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.request?.targets?.singleOrNull()?.instanceId=="stable-knowledge" }
                assertEquals(params,state.acquisitionUiModel!!.request!!.targets.single().parameters)
            }
            if(mode=="new") {
                onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
            } else {
                // Explicitly reviewing an already attained identical instance is an inert no-op,
                // not evidence that unresolved specialization metadata authorizes a new acquisition.
                val reviewed=state.acquisitionUiModel!!;val saved=f.saved()
                assertEquals(AcquisitionUiPhase.Ready,reviewed.phase)
                assertEquals(AcquisitionDisplayedPrice.Known(Amount.ZERO),reviewed.price)
                assertTrue(reviewed.rows.isNotEmpty() && reviewed.rows.all { it.alreadyOwned })
                onNodeWithTag("acquisition.confirm").assertIsEnabled().performClick()
                waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
                assertEquals(saved.instances,f.saved().instances);assertEquals(saved.character,f.saved().character)
                assertEquals(balance(saved.ledger),balance(f.saved().ledger))
                assertEquals(LedgerEntryType.NonFinancial,f.saved().ledger.entries.last().type)
                assertEquals(Amount.ZERO,f.saved().receipts.single().result.cost)
                assertTrue(f.saved().receipts.single().result.changes.isEmpty())
            }
        }
    }
    @Test fun cheaper_catalog_and_external_revision_changes_still_require_fresh_confirmation() {
        for(mode in listOf("cheaper","revision")) runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
            val f=AcquisitionUiFixtures();val state=f.app();mount(state);openSynthetic(state)
            if(mode=="cheaper") {
                val ref=DefinitionRef("dubl-3.69",AcquisitionUiFixtures.SYNTHETIC_ID);val old=f.catalog.definitions.getValue(ref)
                f.catalog=f.catalog.copy(catalogRevision="synthetic-cheaper",definitions=f.catalog.definitions+(ref to old.copy(priceRule=PriceRule.Fixed(10,0,old.sourceRef))))
            } else runBlocking { assertIs<MutationResult.Applied>(CharacterMutationCoordinator(f.store,{f.catalog},{"external-id"}).mutate(
                CanonicalMutation("external-award","ui-A",0,MutationPayload.Grant(Amount(1,0)),"External fixture award"))) }
            val before=f.raw();onNodeWithTag("acquisition.confirm").performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Stale }
            assertEquals(before,f.raw());onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
            onNodeWithTag("acquisition.refresh").performScrollTo().performClick();waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
            assertEquals(before,f.raw());onNodeWithTag("acquisition.confirm").performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
            assertEquals(Amount(if(mode=="cheaper") 90 else 81,2),balance(f.saved().ledger))
        }
    }
    @Test fun acknowledged_write_retains_receipt_when_view_publication_fails() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val armed=AtomicBoolean(false);val failReads=AtomicBoolean(false)
        val f=AcquisitionUiFixtures(fault={if(it==DesktopAcquisitionWriteStage.AfterReplace && armed.compareAndSet(true,false)) failReads.set(true)})
        val state=f.app(beforeLegacyRead={check(!failReads.get()) { "Injected view publication failure" }})
        mount(state);openSynthetic(state);armed.set(true)
        onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed && state.acquisitionUiModel?.viewRefreshError!=null }
        val id=state.acquisition.retainedOperationId
        onNodeWithTag("acquisition.receipt").assertExists();onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
        assertEquals(Amount(80,2),balance(f.saved().ledger));assertEquals(1,f.saved().receipts.size)
        assertEquals(Amount(20,0),state.acquisitionUiModel?.receipt?.cost)
        failReads.set(false);onNodeWithTag("acquisition.reconcile").performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.viewRefreshError==null }
        assertEquals(id,state.acquisition.retainedOperationId);assertEquals(1,f.saved().receipts.size)
    }
    @Test fun readonly_manual_and_undo_controls_cannot_fall_back_to_legacy() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures(writable=false);val state=f.app();val before=f.raw();mount(state)
        onNodeWithTag("acquisition.manual.open").performClick();onNodeWithTag("acquisition.manual.xp").performTextInput("0")
        onNodeWithTag("acquisition.manual.ap").performTextInput("0");onNodeWithTag("acquisition.manual.reason").performTextInput("Readonly check")
        onNodeWithTag("acquisition.manual.review").performClick();waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel!=null }
        onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick()
        onNodeWithTag("acquisition.history.open").performClick();onNodeWithTag("acquisition.undo").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Blocked }
        onNodeWithTag("acquisition.confirm").assertIsNotEnabled();assertEquals(before,f.raw())
    }
    @Test fun source_combat_cry_insufficient_ap_never_becomes_free() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
        val f=AcquisitionUiFixtures(allDefinitions=true,seed={original -> original.copy(
            character=original.character.copy(attributes=AttributeId.entries.associateWith { com.furybook.dubl.model.AttributeValue(6) }),
            instances=listOf(sourceOwned("feat_a700b29906107b2b")),ledger=original.ledger.copy(opening=original.ledger.opening.copy(balance=Amount(100,0))))})
        val state=f.app();val before=f.raw();mount(state);openDevelopment(state,"access_17f99cc35c559d82")
        onNodeWithText("Новый экземпляр с этими параметрами").performScrollTo().performClick()
        val selection=hasTestTag("acquisition.choice") and hasText("Мораль: 0 XP / 1 ОСп")
        waitUntil(timeoutMillis=30_000) { onAllNodes(selection).fetchSemanticsNodes().isNotEmpty() };onNode(selection).performScrollTo().performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Blocked }
        onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
    }
    @Test fun source_weapon_specialization_preserves_new_existing_and_distinct_identity_gaps() {
        for(mode in listOf("new","existing","distinct")) runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
            val ref=DefinitionRef("dubl-3.69","feat_2334f8b24105194c")
            val first=AcquisitionInstance("recorded-weapon",ref,1,mapOf("weaponId" to "sword"),null,AcquisitionOrigin.New)
            val f=AcquisitionUiFixtures(allDefinitions=true,seed={it.copy(instances=listOf(sourceOwned("melee_weapon",3))+when(mode) {
                "new" -> emptyList();"distinct" -> listOf(first,first.copy(instanceId="other-weapon",parameters=mapOf("weaponId" to "axe")));else -> listOf(first)
            })})
            val state=f.app();val before=f.raw();mount(state);openDevelopment(state,ref.entryId)
            if(mode=="existing") {
                assertEquals("recorded-weapon",state.acquisitionUiModel!!.request!!.targets.single().instanceId)
                assertEquals(first.parameters,state.acquisitionUiModel!!.request!!.targets.single().parameters)
            } else assertNull(state.acquisitionUiModel!!.request!!.targets.single().instanceId)
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled();onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
        }
    }
    @Test fun corrupt_and_future_protected_roots_remain_recovery_only_and_preserve_bytes() {
        for(future in listOf(false,true)) runDesktopComposeUiTest(width=1000,height=900,testTimeout=90.seconds) {
            val f=AcquisitionUiFixtures(writable=false);val original=f.saved()
            val raw=if(future) f.raw().replace("\"formatVersion\":1","\"formatVersion\":999") else "{"
            java.nio.file.Files.writeString(f.store.envelopePath("ui-A"),raw)
            val state=f.appFromOriginal(original)
            setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
            waitUntil(timeoutMillis=30_000) { state.storageError!=null }
            assertFalse(state.canonicalViewReady)
            onNodeWithText("Каноническое хранилище недоступно. Сохраните исходные файлы для восстановления.").assertExists()
            onAllNodesWithTag("acquisition.confirm").assertCountEquals(0);assertEquals(raw,f.raw());assertTrue(state.readOnlyCanonicalMode)
        }
    }
    @Test fun card_click_before_paired_review_is_ready_cannot_infer_a_fallback_request() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val armed=AtomicBoolean(false);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val state=f.app(catalogReader={if(armed.compareAndSet(true,false)) {entered.countDown();check(release.await(30,TimeUnit.SECONDS))};f.catalog})
        mount(state);armed.set(true)
        try {
            onNodeWithTag("navigation.DEVELOPMENT").performClick();onNodeWithTag("development.search").performTextInput("Проверочный")
            waitUntil(timeoutMillis=30_000) { entered.count==0L }
            val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.${AcquisitionUiFixtures.SYNTHETIC_ID}"))
            onNode(open,useUnmergedTree=true).assertIsNotEnabled().performMouseInput { click(center) }
            assertNull(state.acquisitionUiModel);assertNull(state.acquisition.retainedOperationId)
            release.countDown()
            waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
            onNode(open,useUnmergedTree=true).performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready }
            assertEquals(1,state.acquisitionUiModel!!.request!!.targets.single().targetRank)
        } finally {release.countDown()}
    }
    @Test fun loading_review_character_switch_cannot_dispatch_or_publish_for_the_new_owner() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val armed=AtomicBoolean(false);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val state=f.app(catalogReader={if(armed.compareAndSet(true,false)) { entered.countDown();check(release.await(30,TimeUnit.SECONDS)) };f.catalog})
        mount(state);onNodeWithTag("navigation.DEVELOPMENT").performClick();onNodeWithTag("development.search").performTextInput("Проверочный")
        val open=hasTestTag("acquisition.open") and hasAnyAncestor(hasTestTag("acquisition.definition.${AcquisitionUiFixtures.SYNTHETIC_ID}"))
        waitUntil(timeoutMillis=30_000) { onAllNodes(open and isEnabled(),useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        val beforeA=f.raw();val beforeB=f.raw("ui-B");armed.set(true)
        try {
            onNode(open,useUnmergedTree=true).performScrollTo().performClick()
            waitUntil(timeoutMillis=30_000) { entered.count==0L && state.acquisitionUiModel?.phase==AcquisitionUiPhase.Loading }
            onNodeWithTag("acquisition.confirm").assertIsNotEnabled();assertNull(state.acquisition.retainedOperationId)
            onNodeWithTag("acquisition.cancel").performClick();onNodeWithTag("navigation.CHARACTERS").performClick();onNodeWithTag("character.ui-B").performClick()
            waitUntil(timeoutMillis=30_000) { state.snapshot.activeCharacterId=="ui-B" }
            release.countDown();waitForIdle()
            assertNull(state.acquisitionUiModel);assertNull(state.acquisition.retainedOperationId)
            assertEquals(beforeA,f.raw());assertEquals(beforeB,f.raw("ui-B"))
            openSynthetic(state);assertEquals("ui-B",state.acquisitionUiModel!!.request!!.characterId)
            onNodeWithTag("acquisition.confirm").performClick();waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
            assertEquals(beforeA,f.raw());assertEquals(Amount(80,2),balance(f.saved("ui-B").ledger))
        } finally { release.countDown() }
    }
    @Test fun original_same_id_replay_after_actual_undo_stays_undone_and_history_is_durable() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val state=f.app();mount(state);openSynthetic(state)
        val request=state.acquisitionUiModel!!.request!!
        val service=AcquisitionService(f.store,{f.catalog},{error("Replay cannot allocate a new operation")})
        val exact=runBlocking { service.preview(request) }
        onNodeWithTag("acquisition.confirm").performClick();waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        val id=state.acquisition.retainedOperationId!!
        val command=ConfirmAcquisition(id,"ui-A",request,exact.baseRevision,exact.previewToken!!)
        assertEquals(command.canonical,f.saved().receipts.single().requestCanonical)
        onNodeWithTag("acquisition.cancel").performClick();onNodeWithTag("navigation.SHEET").performClick();onNodeWithTag("acquisition.history.open").performClick();onNodeWithTag("acquisition.undo").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Ready };onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Committed }
        val before=f.raw()
        // Original command is injected through the existing service seam: the UI now retains the Undo,
        // and deliberately has no control to resurrect an old confirmed command as a new purchase.
        assertIs<ApplyResult.AlreadyUndone>(runBlocking { service.apply(command) })
        assertEquals(before,f.raw());assertEquals(Amount(100,2),balance(f.saved().ledger))
        onNodeWithTag("acquisition.cancel").performClick();onNodeWithTag("navigation.SHEET").performClick();onNodeWithTag("acquisition.history.open").performClick()
        onAllNodesWithText("Операция отменена").onFirst().assertExists()
        onNodeWithTag("acquisition.undo").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Blocked }
        onNodeWithTag("acquisition.confirm").assertIsNotEnabled();assertEquals(before,f.raw())
    }
    @Test fun invalid_canonical_remap_injected_at_review_seam_is_refused_by_actual_confirm() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures();val state=f.app();mount(state);val before=f.raw();val saved=f.saved()
        val remapped=saved.instances.map { it.copy(definition=DefinitionRef("dubl-3.69","melee_weapon")) }
        val patch=CharacterPatch.fromCanonicalState(saved,instances=remapped)
        // No production editor exposes definition-ID remapping. Exercise its invalid captured payload
        // through the existing public review seam, then use the actual retained-operation UI to submit.
        runBlocking { state.acquisition.prepareManual(patch,Amount.ZERO,"Invalid identity remap fixture") }
        onNodeWithTag("acquisition.reopen").performClick();onNodeWithTag("acquisition.confirm").performClick()
        waitUntil(timeoutMillis=30_000) { state.acquisitionUiModel?.phase==AcquisitionUiPhase.Blocked }
        onNodeWithTag("acquisition.confirm").assertIsNotEnabled();assertEquals(before,f.raw())
        assertEquals(saved.instances,f.saved().instances);assertTrue(f.saved().receipts.isEmpty())
    }
    @Test fun saved_sync_conflict_is_displayed_without_automatic_apply_or_branch_union() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=90.seconds) {
        val f=AcquisitionUiFixtures(seed={ original -> original.copy(syncMetadata=SyncMetadata(0,
            encodeSyncEnvelope(original.copy(character=original.character.copy(name="Remote fixture branch"))))) })
        val state=f.app();val before=f.raw();val saved=f.saved();mount(state);openSynthetic(state)
        onNodeWithTag("acquisition.sync.status").assertTextEquals("Конфликт облачных версий; автоматического объединения нет")
        assertEquals(AcquisitionSyncStatus.Conflict,state.acquisitionUiModel!!.sync.status)
        onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,f.raw())
        assertEquals(saved.character,f.saved().character);assertEquals(saved.syncMetadata,f.saved().syncMetadata)
        assertTrue(f.saved().receipts.isEmpty());assertFalse(state.cloudLedgerV2)
    }
    @Test fun legacy_declaration_invalidation_and_explicit_negative_opening_preserve_source_and_block_purchase() = runDesktopComposeUiTest(width=1280,height=1000,testTimeout=120.seconds) {
        val f=AcquisitionUiFixtures();val directory=java.nio.file.Files.createTempDirectory("t18b-migration-ui-")
        val legacy=com.furybook.dubl.model.DublCharacter("legacy-ui",name="Legacy source fixture")
        val raw=com.furybook.dubl.data.SnapshotCodec.encode(com.furybook.dubl.model.AppSnapshot(listOf(legacy),legacy.id))
        val path=directory.resolve("characters.json");java.nio.file.Files.writeString(path,raw)
        val store=com.furybook.dubl.data.DesktopAcquisitionStore(directory)
        val state=DesktopAppState(com.furybook.dubl.data.DesktopCharacterStore(path),com.furybook.dubl.data.DesktopCharacterExtrasStore(directory.resolve("sheet-extras.json")),true,store,{f.catalog},f.development)
        setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
        waitUntil(timeoutMillis=30_000) { state.canonicalViewReady }
        val packs=hasSetTextAction() and hasText("Пакеты исходного листа, через запятую")
        onNode(packs).performTextReplacement("dubl-3.69,unavailable-fixture-pack")
        onNodeWithText("Подготовить миграцию").performClick()
        waitUntil(timeoutMillis=30_000) { state.pendingLegacyMigration!=null }
        assertFalse(java.nio.file.Files.exists(store.envelopePath(legacy.id)))
        onNode(packs).performTextReplacement("dubl-3.69,changed-fixture-pack")
        assertNull(state.pendingLegacyMigration);assertNull(state.preparedLegacyMigration)
        onAllNodesWithText("Подтверждаю остаток и исходные пакеты").assertCountEquals(0)
        onNodeWithText("Подготовить миграцию").performClick();waitUntil(timeoutMillis=30_000) { state.pendingLegacyMigration!=null }
        onNode(hasSetTextAction() and hasText("Известный остаток XP")).performTextInput("-5")
        onNode(hasSetTextAction() and hasText("Известный остаток ОСп")).performTextInput("0")
        onNode(hasSetTextAction() and hasText("Основание подтверждения")).performTextInput("Known legacy debt; no repricing")
        onNodeWithText("Подтверждаю остаток и исходные пакеты").performClick()
        waitUntil(timeoutMillis=30_000) { onAllNodes(hasTestTag("acquisition.balance") and hasText("-5 XP / 0 ОСп")).fetchSemanticsNodes().isNotEmpty() }
        val saved=runBlocking { (store.load(legacy.id) as LoadResult.Loaded).state }
        assertEquals(Amount(-5,0),balance(saved.ledger));assertTrue(saved.receipts.isEmpty())
        assertEquals(raw,java.nio.file.Files.readString(path));val before=java.nio.file.Files.readString(store.envelopePath(legacy.id))
        openSynthetic(state,AcquisitionUiPhase.Blocked);onNodeWithTag("acquisition.confirm").assertIsNotEnabled()
        onNodeWithTag("acquisition.cancel").performClick();assertEquals(before,java.nio.file.Files.readString(store.envelopePath(legacy.id)))
        assertEquals(raw,java.nio.file.Files.readString(path))
    }
    @Test fun all_83818_cases_render_actual_card_preview_and_absent_receipt_content() = runDesktopComposeUiTest(width=1440,height=1000,testTimeout=5400.seconds) {
        var current by mutableStateOf<AcquisitionUiModel?>(null)
        setContent { DublTheme { DesktopVisualTheme { current?.let { model ->
            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).testTag("matrix.card")) { AcquisitionCardSummary(model) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("matrix.preview")) { AcquisitionPlanContent(model) }
                Column(Modifier.weight(1f).testTag("matrix.history")) { AcquisitionHistoryContent(emptyList()) }
            }
        } } } }
        val seen=linkedSetOf<String>();var observations=0
        val directory=File(System.getProperty("furybook.acquisition.ui.evidence")).also { it.mkdirs() }
        File(directory,"renderer-matrix.tsv").bufferedWriter().use { evidence ->
            DesktopAcquisitionOracleStream().forEachPresentation { id,model ->
                seen+=id;observations++
                val expected=when(val price=model.price) { is AcquisitionDisplayedPrice.Known -> "${price.amount.xp} XP / ${price.amount.ap} ОСп"; is AcquisitionDisplayedPrice.Unknown -> price.message }
                runOnIdle { current=model }
                for(parent in listOf("matrix.card","matrix.preview")) {
                    onNode(hasTestTag("acquisition.price") and hasAnyAncestor(hasTestTag(parent)),useUnmergedTree=true).assertTextEquals(expected).assertIsDisplayed()
                    onNode(hasTestTag("acquisition.status") and hasAnyAncestor(hasTestTag(parent)),useUnmergedTree=true).assertTextEquals(model.summary).assertIsDisplayed()
                }
                val expectedRows=model.rows.map { row -> when(val price=row.price) {
                    is AcquisitionDisplayedPrice.Known -> "${price.amount.xp} XP / ${price.amount.ap} ОСп";is AcquisitionDisplayedPrice.Unknown -> price.message
                } }
                val renderedRows=onAllNodesWithTag("acquisition.row.price",useUnmergedTree=true).fetchSemanticsNodes().map { node ->
                    node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
                }
                assertEquals(expectedRows,renderedRows,"$id exact ordered partial prices")
                onNodeWithTag("acquisition.history.empty").assertTextEquals("Нет подтверждённой покупки").assertIsDisplayed()
                onAllNodesWithTag("acquisition.receipt").assertCountEquals(0)
                assertNull(model.receipt,"$id is not a saved receipt")
                evidence.write("$observations\t$id\t${model.phase}\tcard-preview-history-rendered\treceipt-absent\n");evidence.flush()
                if(observations==1 || seen.size==83818) AcquisitionUiEvidence.screenshot("matrix-$observations",captureToImage())
                if(observations%1000==0) println("T18B_RENDERER_CASES unique=${seen.size} observations=$observations id=$id")
            }
        }
        assertEquals(83818,seen.size);assertEquals(119121,observations)
        println("T18B_RENDERER_COMPLETE unique=${seen.size} observations=$observations actualPaidReceipts=0 reusedFinancialEvidence=16459")
    }
}
