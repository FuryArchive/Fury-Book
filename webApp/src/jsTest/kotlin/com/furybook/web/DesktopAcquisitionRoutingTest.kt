package com.furybook.web

import com.furybook.desktop.DesktopAppState
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.model.AttributeId
import kotlinx.coroutines.*
import kotlin.js.Promise
import kotlin.test.*

class DesktopAcquisitionRoutingTest {
    private fun test(body:suspend CoroutineScope.()->Unit):Promise<Unit> = MainScope().promise { body() }
    @Test fun actual_adapter_funding_and_quote_use_owner_bound_native_heads()=test {
        val started=kotlin.js.Date.now()
        fun stage(name:String) { println("T17_WEB_STAGE|$name|${kotlin.js.Date.now()-started}") }
        stage("start")
        clearBrowserSessionData()
        val texts=loadBundledFcpTexts()
        stage("catalog-fetched")
        assertTrue(texts.containsKey("fcp/dubl-3.69/content/acquisition_catalog.json"))
        var ownerId:String?="t17-owner"
        receiveBrowserLegacySource("t17-owner",null)
        val owner=BrowserOwnerScope.fromVerifiedSession(WebAuthSession("t17-owner","synthetic@example.test")){ownerId}
        val store=BrowserAcquisitionStore(owner,"t17-state-"+webUuid(),localWritesEnabled=true)
        assertTrue(store.initialize())
        stage("store-ready")
        try {
            val state=DesktopAppState(texts,acquisitionStore=store,localAcquisitionEnabled=true)
            stage("state-created")
            val review=state.reviewNewCharacterFunding(2500)
            stage("funding-reviewed")
            assertEquals(2L,review.funding?.initialAP)
            assertTrue(store.characterIds().isEmpty())
            assertEquals(AcquisitionUiPhase.Committed,state.confirmNewCharacterFunding(review.key).phase)
            stage("funding-committed")
            val id=state.snapshot.activeCharacterId
            state.confirmNewCharacterFunding(review.key)
            assertEquals(setOf(id),store.characterIds());assertEquals(1,state.snapshot.characters.size)
            val quote=state.acquisition.preview(AcquisitionIntent.attribute(AttributeId.STRENGTH,1))
            stage("attribute-quoted")
            assertEquals(AcquisitionUiPhase.Ready,quote.phase)
            ownerId="different-owner"
            assertEquals(AcquisitionUiPhase.Stale,state.acquisition.confirm(quote.key).phase)
            ownerId="t17-owner"
            assertTrue((store.load(id) as LoadResult.Loaded).state.receipts.isEmpty())
            val disabled=DesktopAppState(texts,acquisitionStore=store,initialCanonicalStates=mapOf(id to (store.load(id) as LoadResult.Loaded).state))
            assertFalse(disabled.reviewNewCharacterFunding(2500).canConfirm)
            assertFalse(state.cloudLedgerV2)
            stage("all-assertions-completed")
        } finally { store.close();clearBrowserSessionData() }
    }
}
