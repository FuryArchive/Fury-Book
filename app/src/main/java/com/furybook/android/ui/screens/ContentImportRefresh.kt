package com.furybook.android.ui.screens

/** Only completed imports notify the root to recapture its single immutable content snapshot. */
internal fun <T> importAndRefreshContent(onInstalled: () -> Unit, install: () -> T): T =
    install().also { onInstalled() }
