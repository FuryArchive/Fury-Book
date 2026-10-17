package com.furybook.web

import com.furybook.dubl.data.LegacyExtrasDocument
import com.furybook.dubl.model.CharacterSheetExtras

/** Financial migration stays strict; ordinary viewing uses the lossless legacy document. */
internal fun decodeBrowserLegacyExtras(raw: String?): Map<String, CharacterSheetExtras> =
    raw?.let { LegacyExtrasDocument.decode(it).forMigration() }.orEmpty()
