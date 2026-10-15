package com.furybook.dubl.acquisition

/** An explicit source classification separates a purchase's payment pool from ability identity. */
val AcquisitionDefinition.hasTransitionPaymentOptions: Boolean
    get() = classificationSources["payment:transition-options"]?.originalText?.isNotBlank() == true

internal fun AcquisitionChange.ownedAbilityOptionId(): String? = if (paymentOption) null else optionId
