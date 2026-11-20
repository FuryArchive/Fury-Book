package com.furybook.dubl.ui.sheet

/** A whole number typed into a field, or null for blank/junk ("", "-", "abc"). */
fun parseIntField(text: String): Int? = text.trim().toIntOrNull()

/** A field where blank means "formula / auto" (maximum overrides, ability points). */
sealed interface OptionalIntField {
    data object Blank : OptionalIntField
    data class Value(val value: Int) : OptionalIntField
    data object Invalid : OptionalIntField
}

fun parseOptionalIntField(text: String): OptionalIntField {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return OptionalIntField.Blank
    return trimmed.toIntOrNull()?.let(OptionalIntField::Value) ?: OptionalIntField.Invalid
}

/** Keeps digits and one leading minus; used as the onValueChange filter of numeric fields. */
internal fun numericInput(text: String, allowNegative: Boolean = true, maxLength: Int = 6): String {
    val digits = text.filter(Char::isDigit).take(maxLength)
    return if (allowNegative && text.trimStart().startsWith("-")) "-$digits" else digits
}

/** The XP dialog's four fields once they parse: totals ≥ 0, adjustment may be negative, blank override = auto. */
data class SheetEconomyForm(val total: Int, val creation: Int, val adjustment: Int, val abilityPointsOverride: Int?)

fun sheetEconomyForm(totalText: String, creationText: String, adjustmentText: String, abilityPointsText: String): SheetEconomyForm? {
    val total = parseIntField(totalText)?.takeIf { it >= 0 } ?: return null
    val creation = parseIntField(creationText)?.takeIf { it >= 0 } ?: return null
    val adjustment = parseIntField(adjustmentText) ?: return null
    val override = when (val parsed = parseOptionalIntField(abilityPointsText)) {
        OptionalIntField.Blank -> null
        is OptionalIntField.Value -> parsed.value.takeIf { it >= 0 } ?: return null
        OptionalIntField.Invalid -> return null
    }
    return SheetEconomyForm(total, creation, adjustment, override)
}

/** Note limits from the old desktop NoteEditorDialog. */
const val SHEET_NOTE_TITLE_MAX = 120
const val SHEET_NOTE_BODY_MAX = 12_000

fun noteTitleInput(text: String): String = text.take(SHEET_NOTE_TITLE_MAX)
fun noteBodyInput(text: String): String = text.take(SHEET_NOTE_BODY_MAX)
