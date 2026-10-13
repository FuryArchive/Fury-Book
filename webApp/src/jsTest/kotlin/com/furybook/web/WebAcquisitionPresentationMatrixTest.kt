package com.furybook.web

import com.furybook.desktop.screens.*
import com.furybook.dubl.acquisition.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlin.js.Promise
import kotlin.test.*

/** Prepared full83818 binding-data gate. Does not render Compose pixels or perform83818 transactions. */
class WebAcquisitionPresentationMatrixTest {
    @Test fun all_83818_source_cases_preserve_shared_presentation_and_web_binding():Promise<Unit> = MainScope().promise {
        val resources=loadBundledFcpTexts()
        suspend fun read(name:String):String {
            val response=(window.asDynamic().fetch("/acquisition-oracle/$name") as Promise<dynamic>).await()
            check(response.ok as Boolean) { "Independent oracle unavailable" }
            return (response.text() as Promise<String>).await()
        }
        val expected=read("expected_prices.json");val overlay=read("author_expected_price_updates.json")
        val cases=linkedSetOf<String>();var observations=0
        WebAcquisitionOracleStream(resources,expected,overlay).forEachPresentation { id,model ->
            cases+=id;observations++
            val shown=bindWebAcquisition(model)
            val price=when(val value=model.price) {
                is AcquisitionDisplayedPrice.Known -> "${value.amount.xp} XP / ${value.amount.ap} ОСп"
                is AcquisitionDisplayedPrice.Unknown -> "Цена не определена"
            }
            assertEquals(price,shown.price,id)
            assertEquals(model.remaining?.let { "${it.xp} XP / ${it.ap} ОСп" },shown.remaining,id)
            assertEquals(model.summary,shown.status,id)
            assertEquals(model.canConfirm && model.phase==AcquisitionUiPhase.Ready,shown.canConfirm,id)
            assertNull(shown.receiptPrice,id);assertNull(shown.receiptRemaining,id)
            if(cases.size%1000==0) println("T18C_WEB_BINDING_PROGRESS|uniqueCases=${cases.size}|observations=$observations|case=$id")
        }
        assertEquals(83818,cases.size)
        println("T18C_WEB_JS_PRESENTATION_DATA_COMPLETE|uniqueCases=${cases.size}|observations=$observations")
    }
}
