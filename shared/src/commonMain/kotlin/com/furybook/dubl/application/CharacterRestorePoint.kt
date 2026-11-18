package com.furybook.dubl.application

import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter

/** A character and its sheet extras as they were at one moment; the editor's «Отменить всё» writes it back. */
data class CharacterRestorePoint(val character: DublCharacter, val extras: CharacterSheetExtras)
