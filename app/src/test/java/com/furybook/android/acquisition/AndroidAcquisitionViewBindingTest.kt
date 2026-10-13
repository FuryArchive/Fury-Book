package com.furybook.android.acquisition

import com.furybook.android.ui.acquisition.*
import com.furybook.dubl.acquisition.*
import org.junit.Assert.*
import org.junit.Test

class AndroidAcquisitionViewBindingTest {
    private fun model(price: AcquisitionDisplayedPrice = AcquisitionDisplayedPrice.Unknown()) = AcquisitionUiModel(
        AcquisitionScope("fixture", "a", 0, 0, 0), AcquisitionQuoteKey("fixture", 1), AcquisitionUiPhase.Blocked,
        "Покупка недоступна", price = price)

    @Test fun unknown_zero_long_and_signed_money_are_not_conflated() {
        assertEquals("Цена не определена", bindAndroidAcquisition(model()).price)
        assertEquals("0 XP / 0 ОСп", bindAndroidAcquisition(model(AcquisitionDisplayedPrice.Known(Amount.ZERO))).price)
        assertEquals("9007199254740993 XP / -2 ОСп", bindAndroidAcquisition(model(
            AcquisitionDisplayedPrice.Known(Amount(9007199254740993L, -2)))).price)
    }

    @Test fun historical_receipt_and_current_balance_remain_distinct() {
        val view = model().copy(phase = AcquisitionUiPhase.Committed,
            receipt = AcquisitionReceiptView(Amount(20, 0), Amount(80, 2), ReceiptStatus.Applied, false, emptyList(), Amount(95, 2)))
        val binding = bindAndroidAcquisition(view)
        assertEquals("20 XP / 0 ОСп", binding.receiptPrice)
        assertEquals("80 XP / 2 ОСп", binding.receiptRemaining)
        assertEquals("95 XP / 2 ОСп", binding.currentBalance)
        assertFalse(binding.canConfirm)
    }

    @Test fun explicit_manual_decimal_and_reason_validation_never_defaults_to_zero() {
        assertNull(parseManualAmount("", "0", "Правка"))
        assertNull(parseManualAmount("1.5", "0", "Правка"))
        assertNull(parseManualAmount("9223372036854775808", "0", "Правка"))
        assertNull(parseManualAmount("0", "0", " "))
        assertEquals(Amount(-20, 0), parseManualAmount("-20", "0", "Исправление"))
        assertEquals(Amount.ZERO, parseManualAmount("0", "0", "Без изменения баланса"))
    }

    @Test fun excluded_target_is_not_reported_as_owned() {
        val row = AcquisitionDisplayRow(DefinitionRef("test", "id"), "Цель", null, 1,
            AcquisitionDisplayedPrice.Known(Amount.ZERO), true, alreadyOwned = false, excludedTarget = true)
        assertEquals("Цель · не приобретается", acquisitionRowLabel(row))
        assertEquals("Цель · уже приобретено", acquisitionRowLabel(row.copy(excludedTarget = false, alreadyOwned = true)))
    }
}
