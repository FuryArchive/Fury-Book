package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.furyOverlineStyle

internal const val PERK_NO_BENEFIT = "Нет описания в правилах — см. требования."
internal const val PERK_ACCESS_ROOT = "Открывает ветку способностей."

/** Навыки: the rules text on every card, cost tags, filters, search; tapping a card opens it in place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SheetPlayPerks(
    groups: List<SheetPerkGroup>,
    playState: SheetPlayState,
    onOpenDetails: (String) -> Unit,
    compact: Boolean,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = FuryColors.current
    val all = groups.flatMap { it.cards }
    val active = all.count { it.costTags.isNotEmpty() }
    val unmet = all.count { it.requirementsUnmet }
    val shown = filterPerkGroups(groups, playState.perkFilter, playState.perkQuery)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(p.panel).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Навыки", color = p.text, style = MaterialTheme.typography.titleLarge)
            Text("${all.size}", color = p.dim, style = MaterialTheme.typography.bodySmall)
            if (unmet > 0) {
                Row(
                    Modifier.semantics { contentDescription = "Требования не выполнены: $unmet" },
                    horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    WarningGlyph()
                    Text("$unmet", color = p.gold, style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(Modifier.weight(1f))
            HeaderToggle(if (playState.perksBrief) "Подробно" else "Кратко", { playState.perksBrief = !playState.perksBrief }, compact)
        }
        if (all.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip("Все", playState.perkFilter == SheetPerkFilter.ALL, compact) { playState.perkFilter = SheetPerkFilter.ALL }
                FilterChip("Активные $active", playState.perkFilter == SheetPerkFilter.ACTIVE, compact) { playState.perkFilter = SheetPerkFilter.ACTIVE }
                FilterChip("Пассивные ${all.size - active}", playState.perkFilter == SheetPerkFilter.PASSIVE, compact) { playState.perkFilter = SheetPerkFilter.PASSIVE }
                if (all.size > PERK_SEARCH_THRESHOLD) {
                    SheetSearchField(playState.perkQuery, { playState.perkQuery = it }, "Поиск навыков…", Modifier.widthIn(min = 140.dp).weight(1f), compact)
                }
            }
        }
        val empty = when {
            all.isEmpty() -> "Навыков пока нет"
            shown.isEmpty() -> "Ничего не найдено"
            else -> null
        }
        if (empty != null) {
            Text(empty, Modifier.fillMaxWidth().padding(16.dp), color = p.dim, style = MaterialTheme.typography.bodyMedium)
        } else if (scrollable) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { group -> PerkGroup(group, playState, onOpenDetails, compact) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { shown.forEach { group -> PerkGroup(group, playState, onOpenDetails, compact) } }
        }
    }
}

@Composable
private fun PerkGroup(group: SheetPerkGroup, playState: SheetPlayState, onOpenDetails: (String) -> Unit, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("${group.title.uppercase()} · ${group.cards.size}", Modifier.padding(horizontal = 4.dp), color = FuryColors.current.dim, style = furyOverlineStyle())
        group.cards.forEach { card ->
            val open = card.entryId in playState.expandedPerks
            PerkCard(card, open, brief = playState.perksBrief, onToggle = {
                playState.expandedPerks = if (open) playState.expandedPerks - card.entryId else playState.expandedPerks + card.entryId
            }, onOpenDetails = onOpenDetails, compact = compact)
        }
    }
}

@Composable
private fun PerkCard(card: SheetPerkCard, open: Boolean, brief: Boolean, onToggle: () -> Unit, onOpenDetails: (String) -> Unit, compact: Boolean) {
    val p = FuryColors.current
    val indent = (18 * card.depth).dp
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth().padding(start = indent)) {
        if (card.depth > 0) Box(Modifier.width(2.dp).heightIn(min = 40.dp).background(p.accLine))
        Column(
            Modifier.weight(1f).clip(shape).background(p.panel2).border(1.dp, if (open) p.line3 else p.line, shape)
                .clickable(role = Role.Button, onClickLabel = if (open) "Свернуть ${card.name}" else "Раскрыть ${card.name}", onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.name, Modifier.weight(1f, fill = false), color = p.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                card.costTags.forEach { tag ->
                    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(p.accSoft).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(tag, color = p.accText, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                    }
                }
                Box(Modifier.weight(1f, fill = true))
                if (card.requirementsUnmet) WarningGlyph(Modifier.semantics { contentDescription = "Требования не выполнены" })
                Text(card.category, Modifier.widthIn(max = 140.dp), color = p.dim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RankPips(card.rank, card.maxRank)
            }
            val text = card.benefit.ifBlank { if (card.isAccessRoot) PERK_ACCESS_ROOT else PERK_NO_BENEFIT }
            Text(
                text,
                color = if (card.benefit.isBlank()) p.dim else p.text2,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = if (card.benefit.isBlank()) FontStyle.Italic else FontStyle.Normal),
                maxLines = if (brief && !open) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            if (open) {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                    Text(
                        "Требования: ${card.requirements.ifBlank { "нет" }}", color = p.muted, style = MaterialTheme.typography.bodySmall,
                    )
                    if (card.opensDetails) {
                        Text(
                            "Подробнее", Modifier.heightIn(min = sheetControlMinHeightDp(compact).dp).clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button) { onOpenDetails(card.entryId) }
                                .wrapContentHeight().padding(vertical = 4.dp),
                            color = p.goldText, style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankPips(rank: Int, maxRank: Int) {
    val p = FuryColors.current
    val width = if (maxRank > 5) 6.dp else 12.dp
    Row(Modifier.semantics { contentDescription = "ранг $rank из $maxRank" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(maxRank) { index ->
            Box(Modifier.size(width = width, height = 5.dp).clip(RoundedCornerShape(2.dp)).background(if (index < rank) p.gold else p.track))
        }
    }
}

@Composable
private fun WarningGlyph(modifier: Modifier = Modifier) {
    Text("⚠", modifier, color = FuryColors.current.gold, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun FilterChip(text: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.heightIn(min = sheetControlMinHeightDp(compact).dp).clip(shape).border(1.dp, if (selected) p.acc else p.line2, shape)
            .background(if (selected) p.accSoft else p.panel2)
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) p.accText else p.text2, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

/** Compact one-line search used by Навыки and Заметки. */
@Composable
internal fun SheetSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = p.text),
        cursorBrush = SolidColor(p.acc),
        modifier = modifier.heightIn(min = sheetControlMinHeightDp(compact).dp).clip(shape).border(1.dp, p.line2, shape).background(p.sunk)
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, color = p.dim, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                inner()
            }
        },
    )
}
