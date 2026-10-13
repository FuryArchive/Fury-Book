package com.furybook.android.acquisition

import com.furybook.android.ui.acquisition.bindAndroidAcquisition
import com.furybook.dubl.acquisition.*
import org.junit.Assert.*
import org.junit.Test

/** All independent case IDs through the actual Shared presentation and Android text binding.
 * This is host presentation-data evidence, never Android rendering, input or device acceptance.
 */
class AndroidAcquisitionPresentationMatrixTest {
    @Test fun all_83818_source_rows_keep_exact_android_binding_and_absent_uncommitted_receipts() {
        val cases = linkedSetOf<String>()
        var observations = 0
        AndroidAcquisitionOracleStream().forEachPresentation { id, model ->
            cases += id; observations++
            val shown = bindAndroidAcquisition(model)
            val expected = when (val price = model.price) {
                is AcquisitionDisplayedPrice.Known -> "${price.amount.xp} XP / ${price.amount.ap} ОСп"
                is AcquisitionDisplayedPrice.Unknown -> "Цена не определена"
            }
            assertEquals(id, expected, shown.price)
            assertEquals(id, model.remaining?.let { "${it.xp} XP / ${it.ap} ОСп" }, shown.remaining)
            assertEquals(id, model.summary, shown.status)
            assertEquals(id, model.canConfirm && model.phase == AcquisitionUiPhase.Ready, shown.canConfirm)
            assertNull("$id: preview is not a saved receipt", shown.receiptPrice)
            assertNull("$id: historical remainder must not be invented", shown.receiptRemaining)
            if (cases.size % 1000 == 0) println("T18A_ANDROID_BINDING cases=${cases.size} observations=$observations case=$id")
        }
        assertEquals(83818, cases.size)
        println("T18A_ANDROID_BINDING_COMPLETE uniqueCases=${cases.size} observations=$observations")
    }
}
