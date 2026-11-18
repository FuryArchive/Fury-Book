package com.furybook.dubl.ui.sheet

import com.furybook.content.FcpUiIconToken
import com.furybook.content.FcpUiAccentToken
import com.furybook.ui.theme.DublAccentStrong
import com.furybook.dubl.model.CharacterSheetResourceId
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.components.FuryBar
import com.furybook.ui.components.FuryButton
import com.furybook.ui.components.FuryButtonStyle
import com.furybook.ui.components.FuryIconButton
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublCustomResource
import com.furybook.ui.theme.DublHealth
import com.furybook.ui.theme.DublMana
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublStamina
import com.furybook.ui.theme.DublSurfaceRaised
import com.furybook.ui.theme.DublText
import com.furybook.ui.theme.FuryShapes
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

internal const val SHEET_NO_VISIBLE_VITALS = "Нет видимых ресурсов"

@Composable
@ReadOnlyComposable
internal fun sheetVitalColor(vital: SheetVital): Color = when (vital.color) {
    SheetVitalColor.HEALTH -> DublHealth
    SheetVitalColor.STAMINA -> DublStamina
    SheetVitalColor.MANA -> DublMana
    SheetVitalColor.CUSTOM -> DublCustomResource
    SheetVitalColor.MOUNTED -> sheetFcpAccent(vital.accentToken)
}

/** Pack accent token → theme colour; every token maps to the crimson accent today (as on the old sheets). */
@Composable
@ReadOnlyComposable
internal fun sheetFcpAccent(token: String?): Color = when (token) {
    FcpUiAccentToken.FURY_ACCENT -> DublAccent
    else -> DublAccent
}

/** Pack icon token → a glyph shown before the meter's label (◎ for Ци, as on the old Android sheet). */
internal fun sheetFcpIconGlyph(token: String?): String? = when (token) {
    FcpUiIconToken.CHI -> "◎"
    else -> null
}

internal fun vitalTitle(vital: SheetVital): String =
    sheetFcpIconGlyph(vital.iconToken)?.let { "$it ${vital.label}" } ?: vital.label

/** Health may go below zero (damage does not stop at 0); every other resource stops at 0. */
internal fun vitalCanDecrease(vital: SheetVital): Boolean =
    vital.resource == CharacterSheetResourceId.HEALTH || vital.current > 0

/** Health text turns crimson at ≤ 25 % and at or below zero (Android critical-health styling). */
@Composable
@ReadOnlyComposable
internal fun vitalValueColor(vital: SheetVital): Color =
    if (vital.resource == CharacterSheetResourceId.HEALTH && healthCriticalLevel(vital.current, vital.maximum) >= 2) DublAccentStrong
    else DublText
