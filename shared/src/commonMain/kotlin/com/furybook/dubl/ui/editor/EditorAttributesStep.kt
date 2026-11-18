package com.furybook.dubl.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing

/** Характеристики: −/+ with the price of each step; the derived stats underneath follow live. */
@Composable
internal fun EditorAttributesStep(host: DublEditorHost, data: EditorFrameData, layout: SheetLayout) {
    val p = FuryColors.current
    val cards = editorAttributeCards(host.active, data.before.character)
    val columns = if (layout == SheetLayout.PHONE) 2 else 4
    val control = editorControl(layout)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(editorContentPadding(layout)),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.md),
    ) {
        EditorStepTitle(
            "Характеристики",
            "Первый пункт стоит 55 XP, каждый следующий на 5 дороже. Ниже нуля опыт возвращается. Производные ниже пересчитываются сразу.",
        )
        Column(Modifier.editorReadableWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    row.forEach { card ->
                        EditorCard(Modifier.weight(1f), border = if (card.delta != 0) p.goldLine else p.line) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EditorOverline(card.short)
                                Text(card.title, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (card.delta != 0) {
                                    Text(
                                        if (card.delta > 0) "+${card.delta}" else card.delta.toString(),
                                        color = if (card.delta > 0) p.gold else p.accText, style = MaterialTheme.typography.labelMedium.merge(EditorTabular),
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                                EditorSquareButton("−", "Уменьшить: ${card.title}", card.canDec, control) { host.changeAttribute(card.id, -1) }
                                Text(
                                    card.value.toString(), Modifier.weight(1f), color = p.text, textAlign = TextAlign.Center, maxLines = 1,
                                    style = MaterialTheme.typography.headlineMedium.merge(EditorTabular).copy(fontSize = 28.sp, lineHeight = 30.sp),
                                )
                                EditorSquareButton("+", "Увеличить: ${card.title}", card.canInc, control) { host.changeAttribute(card.id, 1) }
                            }
                            Row {
                                Text(card.decText, Modifier.weight(1f), color = p.muted, style = MaterialTheme.typography.labelSmall.merge(EditorTabular), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(card.incText, color = p.goldText, style = MaterialTheme.typography.labelSmall.merge(EditorTabular), maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        EditorCard(Modifier.editorReadableWidth()) {
            EditorOverline("Производные")
            EditorDerivedGrid(data.derived, columns = if (layout == SheetLayout.PHONE) 3 else 6, tileHeight = 58.dp)
        }
    }
}

