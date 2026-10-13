package com.furybook.android.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Publish a complete immutable batch. Rows have no lazy fallback capable of doing rule work. */
internal suspend fun <T, K, V> prepareDevelopmentRows(
    entries: List<T>, key: (T) -> K, availability: (T) -> V,
): Map<K, V> = withContext(Dispatchers.Default) {
    entries.associate { key(it) to availability(it) }.toMap()
}
