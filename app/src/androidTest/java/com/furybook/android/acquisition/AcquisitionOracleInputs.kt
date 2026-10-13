package com.furybook.android.acquisition

import android.content.Context
import com.furybook.dubl.acquisition.AndroidAcquisitionOracleStream

/** Literal source corpus is only in instrumentation assets; FCP remains actual target assets. */
internal object AcquisitionOracleInputs {
    fun stream(target: Context, instrumentation: Context) = AndroidAcquisitionOracleStream(
        readText = { path ->
            val assets = if (path.startsWith("acquisition-oracle/")) instrumentation.assets else target.assets
            runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
        },
        readRows = { consume -> instrumentation.assets.open("acquisition-oracle/expected_prices.json")
            .bufferedReader().useLines { lines -> lines.forEach(consume) } },
    )
}
