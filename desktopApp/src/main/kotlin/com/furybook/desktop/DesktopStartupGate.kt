package com.furybook.desktop

import com.furybook.dubl.data.DesktopAcquisitionStore
import com.furybook.desktop.cloud.DesktopCloudController

/** Decide before constructing/binding a legacy cloud writer. Unknown storage is protected. */
internal fun legacyCloudForStorage(store: DesktopAcquisitionStore?, localWritesEnabled: Boolean,
    create: () -> DesktopCloudController): DesktopCloudController? =
    if (localWritesEnabled || store?.hasProtectedHeads() != false) null else create()
