package com.furybook.dubl.ui.editor

import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.effectiveDevelopmentCatalog
import com.furybook.dubl.model.withoutChiContent

/** [character]'s own catalog, composed exactly as the platform hosts compose `developmentCatalog` for the active one. */
fun editorCatalogFor(host: DublEditorHost, character: DublCharacter): DevelopmentCatalog =
    character.effectiveDevelopmentCatalog(host.canonicalDevelopmentCatalog)
        .let { catalog -> if (host.chiPackEnabled) catalog else catalog.withoutChiContent() }
