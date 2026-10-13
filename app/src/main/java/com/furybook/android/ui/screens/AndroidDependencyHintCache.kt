package com.furybook.android.ui.screens

import com.furybook.dubl.acquisition.*

/** Canceled Default work can overlap its successor. Serialize the caller-owned Shared cache. */
internal class AndroidDependencyHintCache {
    private val cache = AcquisitionDependencyHintCache()
    @Synchronized fun get(catalog: AcquisitionCatalog): AcquisitionDependencyHints = cache.get(catalog)
}
