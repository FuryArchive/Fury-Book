package com.furybook.web

import com.furybook.desktop.screens.*
import com.furybook.dubl.acquisition.*
import kotlin.test.*

/** Prepared JS binding regressions. These are not a rendered browser/input suite. */
class WebAcquisitionBindingTest {
    private fun model(price: AcquisitionDisplayedPrice = AcquisitionDisplayedPrice.Unknown()) = AcquisitionUiModel(
        AcquisitionScope("fixture", "a", 0, 0, 0), AcquisitionQuoteKey("fixture", 1), AcquisitionUiPhase.Blocked,
        "Покупка недоступна", price = price)
    @Test fun unknown_zero_and_exact_long_money_remain_distinct() {
        assertEquals("Цена не определена", bindWebAcquisition(model()).price)
        assertEquals("0 XP / 0 ОСп", bindWebAcquisition(model(AcquisitionDisplayedPrice.Known(Amount.ZERO))).price)
        assertEquals("9007199254740993 XP / -2 ОСп", bindWebAcquisition(model(AcquisitionDisplayedPrice.Known(Amount(9007199254740993L,-2)))).price)
    }
    @Test fun saved_historical_and_current_balances_are_separate() {
        val shown=bindWebAcquisition(model().copy(phase=AcquisitionUiPhase.Committed,
            receipt=AcquisitionReceiptView(Amount(20,0),Amount(80,2),ReceiptStatus.Applied,false,emptyList(),Amount(95,2))))
        assertEquals("20 XP / 0 ОСп",shown.receiptPrice);assertEquals("80 XP / 2 ОСп",shown.receiptRemaining)
        assertEquals("95 XP / 2 ОСп",shown.currentBalance);assertFalse(shown.canConfirm)
    }
    @Test fun explicit_signed_manual_input_never_defaults_blank_or_overflow_to_zero() {
        assertNull(parseManualAmount("","0","Правка"));assertNull(parseManualAmount("1.5","0","Правка"))
        assertNull(parseManualAmount("9223372036854775808","0","Правка"));assertNull(parseManualAmount("0","0"," "))
        assertEquals(Amount(-20,0),parseManualAmount("-20","0","Исправление"))
    }
    @Test fun excluded_target_is_not_owned() {
        val row=AcquisitionDisplayRow(DefinitionRef("fixture","target"),"Цель",null,1,
            AcquisitionDisplayedPrice.Known(Amount.ZERO),true,alreadyOwned=false,excludedTarget=true)
        assertEquals("Цель · не приобретается",acquisitionRowLabel(row))
    }
    @Test fun delayed_or_failed_first_discovery_cannot_enable_legacy_or_navigation() {
        val pending=WebCanonicalReadState.initial(true,false)
        assertTrue(pending.blocksLegacy);assertFalse(pending.navigationAvailable)
        val failed=pending.failed("Unavailable")
        assertTrue(failed.blocksLegacy);assertFalse(failed.navigationAvailable)
        assertEquals(WebCanonicalReadPhase.Unavailable,failed.phase)
    }
    @Test fun protected_refresh_preserves_navigation_and_classification() {
        val loaded=WebCanonicalReadState.initial(true,false).loaded(true)
        val refreshing=loaded.begin()
        assertTrue(refreshing.blocksLegacy);assertTrue(refreshing.navigationAvailable)
        assertEquals(true,refreshing.classifiedProtected)
        val failed=refreshing.failed("Unavailable")
        assertTrue(failed.blocksLegacy);assertTrue(failed.navigationAvailable)
    }
    @Test fun explicit_empty_discovery_alone_can_classify_legacy() {
        val empty=WebCanonicalReadState.initial(true,false).loaded(false)
        assertFalse(empty.blocksLegacy);assertTrue(empty.navigationAvailable)
        assertTrue(empty.begin().blocksLegacy)
    }
    @Test fun successful_empty_refresh_cannot_downgrade_acknowledged_protected_root() {
        val fresh=WebCanonicalReadState.initial(true,false).loaded(false)
        assertFalse(fresh.blocksLegacy);assertEquals(false,fresh.classifiedProtected)
        val protected=WebCanonicalReadState.initial(true,false).loaded(true).begin().loaded(false)
        assertTrue(protected.blocksLegacy);assertEquals(true,protected.classifiedProtected)
        assertTrue(protected.navigationAvailable)
        assertEquals(WebCanonicalReadPhase.Ready,protected.phase)
        assertTrue(protected.begin().loaded(false).blocksLegacy)
    }
    @Test fun spell_review_display_preserves_shared_unknown_blocked_admission_and_typed_requirements() {
        val required=AcquisitionRequirementView("spell-source","Условие изучения",RequirementStatus.SourceUnresolved,
            RequirementValue.Number(1),RequirementValue.Number(3),null,emptyList(),"Источник требует уточнения")
        val review=model().copy(summary="Изучение заклинания не подтверждено источником",requirements=listOf(required))
        val shown=bindWebAcquisition(review)
        assertEquals("Цена не определена",shown.price)
        assertEquals(review.summary,shown.status);assertFalse(shown.canConfirm)
        assertEquals("1",acquisitionRequirementValue(review.requirements.single().actual,required.label))
        assertEquals("3",acquisitionRequirementValue(review.requirements.single().required,required.label))
        assertEquals(RequirementStatus.SourceUnresolved,review.requirements.single().status)
    }
    @Test fun typed_requirement_values_preserve_missing_numbers_flags_and_readable_identity() {
        assertEquals("не установлено",acquisitionRequirementValue(null,"Known label"))
        assertEquals("3",acquisitionRequirementValue(RequirementValue.Number(3),"Attribute"))
        assertEquals("нет",acquisitionRequirementValue(RequirementValue.Flag(false),"Learned"))
        assertEquals("Readable choice",acquisitionRequirementValue(RequirementValue.StableId("technical-pack","technical-id"),"Readable choice"))
    }

}
