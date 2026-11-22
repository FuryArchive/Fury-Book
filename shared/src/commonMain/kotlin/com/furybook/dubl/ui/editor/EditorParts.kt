package com.furybook.dubl.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryHelpfulChip
import com.furybook.ui.theme.FuryHelpfulChipText
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

internal val EditorTabular = TextStyle(fontFeatureSettings = "tnum")

/** Control size: 44dp on touch layouts, 36dp next to the docked desktop columns. */
internal fun editorControl(layout: SheetLayout): Dp = if (layout == SheetLayout.WIDE_WITH_TRAY) 36.dp else FuryMinTouch

@Composable
internal fun EditorStepTitle(title: String, subtitle: String) {
    val p = FuryColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = p.text, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, color = p.muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun EditorOverline(text: String, modifier: Modifier = Modifier, color: Color = FuryColors.current.muted) {
    Text(text.uppercase(), modifier, color = color, style = furyOverlineStyle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun EditorCard(
    modifier: Modifier = Modifier,
    background: Color = FuryColors.current.panel,
    border: Color = FuryColors.current.line,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(background).border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(FurySpacing.md),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.sm),
        content = content,
    )
}

/** A row on panel2: the shared shape of list rows in Развитие, Умения and the change list. */
@Composable
internal fun EditorRow(modifier: Modifier = Modifier, minHeight: Dp = 44.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(10.dp)).background(FuryColors.current.panel2)
            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm),
        content = content,
    )
}

/** Square −/+ button. */
@Composable
internal fun EditorSquareButton(text: String, label: String, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(size).clip(shape).background(p.tile).border(1.dp, p.line2, shape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = p.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 20.sp))
    }
}

/** Outlined text button with a fixed height; [accent] colours it as a destructive or warning action. */
@Composable
internal fun EditorOutlineButton(
    text: String,
    onClick: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.heightIn(min = height).defaultMinSize(minWidth = height).clip(shape).background(p.panel2)
            .border(1.dp, if (accent) p.accLine else p.line2, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (accent) p.accText else p.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The one filled action of a view (acc2 with onAcc text). */
@Composable
internal fun EditorPrimaryButton(text: String, onClick: () -> Unit, height: Dp, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.heightIn(min = height).clip(shape).background(p.acc2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = p.onAcc, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Selectable chip (kind tabs, filters, choices). [gold] picks the gold selection colour used by filters. */
@Composable
internal fun EditorChip(text: String, selected: Boolean, onClick: () -> Unit, height: Dp, gold: Boolean = false, role: Role = Role.Tab) {
    val p = FuryColors.current
    val (bg, line, fg) = when {
        !selected -> Triple(if (gold) Color.Transparent else p.panel, p.line2, p.text2)
        gold -> Triple(p.goldSoft, p.gold, p.goldText)
        else -> Triple(p.accSoft, p.acc, p.accText)
    }
    val shape = RoundedCornerShape(if (gold) height / 2 else 10.dp)
    Box(
        Modifier.heightIn(min = height).clip(shape).background(bg).border(BorderStroke(1.dp, line), shape)
            .clickable(role = role, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

internal data class EditorPillColors(val background: Color, val content: Color)

@Composable
internal fun editorPillColors(status: EditorPerkStatus, rank: Int): EditorPillColors {
    val p = FuryColors.current
    return when {
        rank > 0 -> EditorPillColors(p.accSoft, p.accText)
        status == EditorPerkStatus.AVAILABLE || status == EditorPerkStatus.UPGRADABLE -> EditorPillColors(FuryHelpfulChip, FuryHelpfulChipText)
        status == EditorPerkStatus.ALMOST -> EditorPillColors(p.goldSoft, p.goldText)
        status == EditorPerkStatus.LOW_XP -> EditorPillColors(p.hover, p.muted)
        else -> EditorPillColors(p.hover, p.muted)
    }
}

@Composable
internal fun EditorPill(text: String, colors: EditorPillColors) {
    Box(
        Modifier.heightIn(min = 22.dp).clip(RoundedCornerShape(11.dp)).background(colors.background).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.content, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
    }
}

/** Yellow «!»: an owned perk whose requirements are no longer met. */
@Composable
internal fun EditorWarnMark(label: String) {
    val p = FuryColors.current
    Box(
        Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(p.goldSoft).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text("!", color = p.gold, style = MaterialTheme.typography.labelMedium)
    }
}

/** Rank pips: [gained] of them (from the end of [filled]) drawn gold. */
@Composable
internal fun EditorPips(filled: Int, total: Int, color: Color, modifier: Modifier = Modifier, gainedFrom: Int = filled, pipWidth: Dp? = null, height: Dp = 5.dp) {
    val p = FuryColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(total) { i ->
            val c = when {
                i >= filled -> p.line2
                i >= gainedFrom -> p.gold
                else -> color
            }
            val m = if (pipWidth != null) Modifier.size(pipWidth, height) else Modifier.weight(1f).heightIn(min = height, max = height)
            Box(m.clip(RoundedCornerShape(2.dp)).background(c))
        }
    }
}

/** The six derived stats as tiles with the change since opening. */
@Composable
internal fun EditorDerivedGrid(items: List<EditorDerived>, columns: Int, tileHeight: Dp = 54.dp) {
    val p = FuryColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d ->
                    Column(
                        Modifier.weight(1f).heightIn(min = tileHeight).clip(RoundedCornerShape(10.dp)).background(p.dBg)
                            .border(1.dp, p.dLine, RoundedCornerShape(10.dp)).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        Text(d.label, color = p.dLabel, style = furyOverlineStyle().copy(fontSize = 9.sp), maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(d.value, color = p.dText, maxLines = 1, softWrap = false, style = MaterialTheme.typography.titleLarge.merge(EditorTabular).copy(fontSize = 15.sp, lineHeight = 18.sp))
                            if (d.delta != 0) {
                                Text(
                                    if (d.delta > 0) " +${d.delta}" else " ${d.delta}",
                                    color = if (d.delta > 0) p.gold else p.accText, maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall.merge(EditorTabular),
                                )
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Card that fills the width of a narrow layout but stops growing on wide ones. */
internal fun Modifier.editorReadableWidth(): Modifier = this.widthIn(max = 980.dp).fillMaxWidth()
