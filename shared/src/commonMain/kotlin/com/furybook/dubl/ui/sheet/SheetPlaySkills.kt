package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.dubl.model.AttributeId
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.furyOverlineStyle

private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
private const val RANK_BARS = 10
private const val STRONG_BONUS = 12

/**
 * Умения grouped by attribute: each group's attribute tile (tap = roll the attribute) sits left of its
 * skills (tap = roll the skill). [scrollable] puts the groups in their own list (the desktop column).
 */
@Composable
fun SheetPlaySkills(
    groups: List<SheetAttributeSkills>,
    totals: Pair<Int, Int>,
    rankedOnly: Boolean,
    onToggleRanked: () -> Unit,
    onRollAttribute: (AttributeId) -> Unit,
    onRollSkill: (String) -> Unit,
    compact: Boolean,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val p = FuryColors.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(p.panel).padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Умения", color = p.text, style = MaterialTheme.typography.titleLarge)
            Text(
                if (rankedOnly) "${totals.first} из ${totals.second}" else "${totals.second}",
                Modifier.weight(1f), color = p.dim, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
            HeaderToggle(if (rankedOnly) "Показать все" else "Только с рангами", onToggleRanked)
        }
        if (scrollable) {
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it.attribute.name }) { group -> AttributeGroup(group, compact, onRollAttribute, onRollSkill) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { group -> AttributeGroup(group, compact, onRollAttribute, onRollSkill) }
            }
        }
    }
}

@Composable
internal fun HeaderToggle(text: String, onClick: () -> Unit) {
    val p = FuryColors.current
    Box(
        Modifier.heightIn(min = 30.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, p.line2, RoundedCornerShape(8.dp))
            .background(p.panel2).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = p.text, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun AttributeGroup(
    group: SheetAttributeSkills,
    compact: Boolean,
    onRollAttribute: (AttributeId) -> Unit,
    onRollSkill: (String) -> Unit,
) {
    val p = FuryColors.current
    val empty = group.rows.isEmpty()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val tileShape = RoundedCornerShape(10.dp)
        val tile = Modifier.width(56.dp).clip(tileShape).border(1.dp, p.line, tileShape)
            .background(if (empty) p.sunk else p.tile)
            .semantics { contentDescription = "${group.attribute.title} ${group.value}, бросок" }
            .clickable(role = Role.Button) { onRollAttribute(group.attribute) }
        if (empty) {
            Row(tile.heightIn(min = 34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text(group.label, color = p.muted, style = furyOverlineStyle())
                Text(group.value.toString(), color = p.text2, style = MaterialTheme.typography.labelLarge.merge(TabularNumbers))
            }
            Text("нет умений", Modifier.align(Alignment.CenterVertically).padding(start = 8.dp), color = p.dim, style = MaterialTheme.typography.bodySmall)
        } else {
            Column(tile.fillMaxHeight().heightIn(min = 44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                Text(group.label, color = p.muted, style = furyOverlineStyle())
                Text(
                    group.value.toString(), color = p.text, maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers).copy(fontSize = 20.sp, lineHeight = 22.sp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                group.rows.forEach { row -> SkillRow(row, compact, onRollSkill) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillRow(row: SheetSkillRow, compact: Boolean, onRollSkill: (String) -> Unit) {
    val p = FuryColors.current
    val strong = (row.bonus ?: 0) >= STRONG_BONUS
    val zero = row.rank == 0
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(row.breakdown) } },
        state = rememberTooltipState(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 32.dp).clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClickLabel = "Бросок: ${row.name}") { onRollSkill(row.skillId) }
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                row.name, Modifier.weight(1f), color = if (zero) p.dim else p.text, style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            row.perkMark?.let { mark ->
                Box(
                    Modifier.clip(RoundedCornerShape(5.dp)).background(p.goldSoft).padding(horizontal = 5.dp, vertical = 1.dp)
                        .semantics { contentDescription = "От навыков: ${row.perkTip.orEmpty()}" },
                ) {
                    Text(mark, color = p.gold, style = MaterialTheme.typography.labelSmall)
                }
            }
            RankBars(row.rank, strong, Modifier.width(56.dp))
            Box(
                Modifier.widthIn(min = 44.dp).height(26.dp).clip(RoundedCornerShape(7.dp)).background(if (strong) p.accSoft else p.hover),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.bonus?.let { if (it >= 0) "+$it" else "$it" } ?: "—",
                    Modifier.padding(horizontal = 6.dp),
                    color = when {
                        strong -> p.accText
                        zero -> p.dim
                        else -> p.text
                    },
                    style = MaterialTheme.typography.titleLarge.merge(TabularNumbers).copy(fontSize = 14.sp, lineHeight = 18.sp),
                    maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun RankBars(rank: Int, strong: Boolean, modifier: Modifier) {
    val p = FuryColors.current
    Row(modifier.semantics { contentDescription = "ранг $rank" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(RANK_BARS) { index ->
            Box(
                Modifier.weight(1f).height(9.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (index < rank) (if (strong) p.acc else p.dim) else p.track),
            )
        }
    }
}
