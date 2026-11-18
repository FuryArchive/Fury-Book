package com.furybook.dubl.ui.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterEconomyBreakdown
import com.furybook.ui.components.FuryBar
import com.furybook.ui.components.FuryIconButton
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryHelpfulChip
import com.furybook.ui.theme.FuryHelpfulChipText
import com.furybook.ui.theme.FuryMinTouch
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

/**
 * What the play sheet remembers between taps for one character: the open resource adjuster, the condition
 * whose effect is shown, list filters, searches and expanded cards. A different character starts clean.
 */
class SheetPlayState {
    var selectedResource: String? by mutableStateOf(null)
    var selectedCondition: String? by mutableStateOf(null)
    var conditionsExpanded: Boolean by mutableStateOf(false)
    var rankedOnly: Boolean by mutableStateOf(true)
    var perkFilter: SheetPerkFilter by mutableStateOf(SheetPerkFilter.ALL)
    var perkQuery: String by mutableStateOf("")
    var perksBrief: Boolean by mutableStateOf(true)
    var expandedPerks: Set<String> by mutableStateOf(emptySet())
    var noteQuery: String by mutableStateOf("")
    var expandedNotes: Set<String> by mutableStateOf(emptySet())
    private var characterId: String? = null

    fun syncCharacter(id: String) {
        if (id == characterId) return
        characterId = id
        selectedResource = null
        selectedCondition = null
        conditionsExpanded = false
        rankedOnly = true
        perkFilter = SheetPerkFilter.ALL
        perkQuery = ""
        perksBrief = true
        expandedPerks = emptySet()
        noteQuery = ""
        expandedNotes = emptySet()
    }
}

/** Opens the resources dialog (hide/show meters, add custom resources) from the play sheet. */
internal const val SHEET_RESOURCES_SETUP = "Настроить ресурсы"

/** Small resources (max 1–10, not health) show one pip per point; the rest a bar. */
internal fun resourceUsesPips(vital: SheetVital): Boolean = vital.id != "health" && vital.maximum in 1..10

/** Chips shown on the desktop condition line and how many hide behind «+N». */
internal fun visibleConditions(chips: List<SheetConditionChip>, expanded: Boolean): Pair<List<SheetConditionChip>, Int> =
    if (expanded || chips.size <= CONDITION_INLINE_CAP) chips to 0 else chips.take(CONDITION_INLINE_CAP) to chips.size - CONDITION_INLINE_CAP

private val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/** Room the desktop identity row keeps for everything but the six derived tiles: portrait, name (≥160), XP chip, Редактор, gaps. */
private const val IDENTITY_FIXED_DP = 52 + 160 + 150 + 44 + 4 * 12
private const val DERIVED_TILE_MAX_DP = 74
private const val DERIVED_TILE_MIN_DP = 48

/** Width of each derived tile in the desktop identity row, or null when they need a row of their own. */
internal fun derivedTileWidthDp(rowWidthDp: Int): Int? {
    val tile = (rowWidthDp - IDENTITY_FIXED_DP - 5 * 4) / 6
    return if (tile < DERIVED_TILE_MIN_DP) null else tile.coerceAtMost(DERIVED_TILE_MAX_DP)
}
private val RESOURCE_CELL_MIN = 178.dp

/** Everything the header shows, gathered once per composition. */
private class HeaderData(
    val name: String,
    val concept: String,
    val economy: CharacterEconomyBreakdown,
    val vitals: List<SheetVital>,
    val conditions: List<SheetConditionChip>,
    val metrics: List<SheetStat>,
)

@Composable
private fun headerData(host: DublSheetEditHost): HeaderData = HeaderData(
    name = host.active.name,
    concept = host.active.concept,
    economy = rememberSheetEconomy(host),
    vitals = sheetVitals(host),
    conditions = sheetActiveConditions(host),
    metrics = sheetMetrics(host.active),
)

/** Tapping a resource's name or value opens its amount dialog (damage / healing by N, restore, remove). */
private fun Modifier.openVital(vital: SheetVital, state: SheetScreenState): Modifier =
    clickable(role = Role.Button, onClickLabel = "Изменить ${vital.label} на несколько") { state.openDialog(SheetDialog.Vital(vital.id)) }

private fun changeVital(host: DublSheetEditHost, vital: SheetVital, delta: Int) {
    val resource = vital.resource
    val uid = vital.customUid
    if (resource != null) host.changeResource(resource, delta) else if (uid != null) host.changeCustomResource(uid, delta)
}

private fun removeCondition(host: DublSheetEditHost, chip: SheetConditionChip) {
    if (chip.custom) host.setCustomConditionActive(chip.key, false)
    else CharacterConditionId.entries.firstOrNull { it.name == chip.key }?.let(host::toggleCondition)
}

// ───────────────────────────── desktop ─────────────────────────────

/** Desktop: identity row with derived stats and XP, the resource rack, then the condition line. */
@Composable
fun SheetPlayHeaderWide(
    host: DublSheetEditHost,
    state: SheetScreenState,
    playState: SheetPlayState,
    onOpenEditor: () -> Unit,
    portrait: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val data = headerData(host)
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val tileWidth = derivedTileWidthDp(maxWidth.value.toInt())
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(FurySpacing.md), verticalAlignment = Alignment.CenterVertically) {
            portrait?.invoke() ?: InitialBadge(data.name, 52.dp)
            Column(Modifier.weight(1f).widthIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(data.name, color = FuryColors.current.text, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(data.concept.ifBlank { "Без концепта" }, color = FuryColors.current.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tileWidth != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    data.metrics.forEach { stat -> DerivedTile(stat, Modifier.width(tileWidth.dp).height(48.dp), state) }
                }
            }
            EconomyChip(data.economy, host.active.experience, Modifier.height(56.dp)) { state.openDialog(SheetDialog.Economy) }
            FuryIconButton("Редактор", onOpenEditor, Modifier.border(1.dp, FuryColors.current.line2, RoundedCornerShape(12.dp))) { PencilGlyph() }
        }
        if (tileWidth == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                data.metrics.forEach { stat -> DerivedTile(stat, Modifier.weight(1f).height(44.dp), state) }
            }
        }
        ResourceRack(data.vitals, host, state)
        ConditionLine(data.conditions, playState, host, state)
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionLine(chips: List<SheetConditionChip>, playState: SheetPlayState, host: DublSheetEditHost, state: SheetScreenState) {
    val (shown, hidden) = visibleConditions(chips, playState.conditionsExpanded)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("СОСТОЯНИЯ", Modifier.align(Alignment.CenterVertically).padding(end = 4.dp), color = FuryColors.current.dim, style = furyOverlineStyle())
            shown.forEach { chip -> ConditionChip(chip, playState.selectedCondition == chip.key) { playState.toggleCondition(chip.key) } }
            if (hidden > 0 || playState.conditionsExpanded && chips.size > CONDITION_INLINE_CAP) {
                SmallPill(if (playState.conditionsExpanded) "свернуть" else "+$hidden", dashed = false) {
                    playState.conditionsExpanded = !playState.conditionsExpanded
                }
            }
            SmallPill("+ состояние", dashed = true) { state.openDialog(SheetDialog.Conditions) }
            SmallPill(SHEET_RESOURCES_SETUP, dashed = true) { state.openDialog(SheetDialog.Resources) }
        }
        SelectedConditionLine(chips, playState, host)
    }
}

@Composable
private fun SelectedConditionLine(chips: List<SheetConditionChip>, playState: SheetPlayState, host: DublSheetEditHost, minHeight: Dp = 28.dp) {
    val chip = chips.firstOrNull { it.key == playState.selectedCondition } ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${chip.title}: ${chip.description.ifBlank { "без описания" }}",
            Modifier.weight(1f), color = FuryColors.current.text2, style = MaterialTheme.typography.bodySmall,
        )
        if (!chip.automatic) {
            SmallPill("Снять", dashed = false, minHeight) {
                playState.selectedCondition = null
                removeCondition(host, chip)
            }
        }
    }
}

private fun SheetPlayState.toggleCondition(key: String) {
    selectedCondition = if (selectedCondition == key) null else key
}

@Composable
private fun ResourceRack(vitals: List<SheetVital>, host: DublSheetEditHost, state: SheetScreenState) {
    if (vitals.isEmpty()) {
        Text(SHEET_NO_VISIBLE_VITALS, color = FuryColors.current.dim, style = MaterialTheme.typography.bodySmall)
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val columns = ((maxWidth + gap) / (RESOURCE_CELL_MIN + gap)).toInt().coerceAtLeast(2)
        // Health takes two cells; the rest fill rows left to right.
        val rows = mutableListOf<MutableList<Pair<SheetVital, Int>>>(mutableListOf())
        var used = 0
        vitals.forEach { vital ->
            val span = if (vital.id == "health") 2 else 1
            if (used + span > columns) {
                rows += mutableListOf<Pair<SheetVital, Int>>()
                used = 0
            }
            rows.last() += vital to span
            used += span
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (vital, span) -> ResourceCell(vital, host, state, Modifier.weight(span.toFloat())) }
                    val free = columns - row.sumOf { it.second }
                    if (free > 0) Spacer(Modifier.weight(free.toFloat()))
                }
            }
        }
    }
}

@Composable
private fun ResourceCell(vital: SheetVital, host: DublSheetEditHost, state: SheetScreenState, modifier: Modifier) {
    val color = sheetVitalColor(vital)
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(FuryColors.current.panel)
            .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .alpha(if (vital.maximum == 0) 0.45f else 1f),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).openVital(vital, state),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(
                vitalTitle(vital).uppercase(), Modifier.weight(1f), color = FuryColors.current.muted, style = furyOverlineStyle(),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${vital.current}/${vital.maximum}", color = vitalValueColor(vital), maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.titleLarge.merge(TabularNumbers).copy(fontSize = 15.sp, lineHeight = 18.sp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TinyButton("−", "Уменьшить ${vital.label}", vitalCanDecrease(vital)) { changeVital(host, vital, -1) }
            ResourceMeter(vital, color, Modifier.weight(1f))
            TinyButton("+", "Увеличить ${vital.label}", vital.current < vital.maximum) { changeVital(host, vital, 1) }
            if (vital.restoreable) TinyButton("↺", "Восстановить ${vital.label}", true) { vital.resource?.let(host::restoreMountedResource) }
        }
    }
}

@Composable
private fun ResourceMeter(vital: SheetVital, color: Color, modifier: Modifier, height: Dp = 8.dp) {
    if (resourceUsesPips(vital)) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(vital.maximum) { index ->
                Box(
                    Modifier.weight(1f).height(height).clip(RoundedCornerShape(2.dp))
                        .background(if (index < vital.current) color else FuryColors.current.track),
                )
            }
        }
    } else {
        FuryBar(vital.current, vital.maximum, color, modifier, height = height)
    }
}

@Composable
private fun TinyButton(text: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 26.dp, height = 22.dp).clip(RoundedCornerShape(6.dp)).background(FuryColors.current.hover)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = FuryColors.current.text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DerivedTile(stat: SheetStat, modifier: Modifier, state: SheetScreenState) {
    val info = stat.info
    Column(
        modifier.clip(RoundedCornerShape(9.dp)).background(FuryColors.current.dBg)
            .border(1.dp, FuryColors.current.dLine, RoundedCornerShape(9.dp))
            .then(if (info != null) Modifier.clickable(role = Role.Button, onClickLabel = "Формула ${stat.label}") { state.openDialog(SheetDialog.Stat(info, stat.roll)) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(derivedShortLabel(stat.label), color = FuryColors.current.dLabel, style = furyOverlineStyle().copy(fontSize = 9.sp), maxLines = 1)
        Text(
            stat.valueText, color = FuryColors.current.dText, maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.titleLarge.merge(TabularNumbers).copy(fontSize = 15.sp, lineHeight = 18.sp),
        )
    }
}

internal fun derivedShortLabel(label: String): String = when (label) {
    "Защита" -> "ЗАЩ"
    "Рефлексы" -> "РЕФЛ"
    "Инициатива" -> "ИНИЦ"
    "Стойкость" -> "СТОЙК"
    "Размер" -> "РАЗМ"
    else -> label.uppercase()
}

@Composable
private fun EconomyChip(economy: CharacterEconomyBreakdown, xpTotal: Int, modifier: Modifier, onClick: () -> Unit) {
    val p = FuryColors.current
    val negative = economy.abilityPointsRemaining < 0
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(p.goldSoft).border(1.dp, p.goldLine, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Опыт и очки способностей", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Text(
            "XP ${economy.remainingXp} / $xpTotal", color = p.goldText, maxLines = 1, softWrap = false,
            style = MaterialTheme.typography.titleLarge.merge(TabularNumbers).copy(fontSize = 14.sp, lineHeight = 18.sp),
        )
        Text(
            "ОС ${economy.abilityPointsRemaining}/${economy.abilityPointsBudget}", color = if (negative) p.accText else p.text2, maxLines = 1,
            style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
        )
    }
}

@Composable
private fun ConditionChip(chip: SheetConditionChip, selected: Boolean, minHeight: Dp = 28.dp, onClick: () -> Unit) {
    val p = FuryColors.current
    val (bg, fg) = when (chip.kind) {
        SheetConditionKind.HARMFUL -> p.accSoft to p.accText
        SheetConditionKind.HELPFUL -> FuryHelpfulChip to FuryHelpfulChipText
        SheetConditionKind.CUSTOM -> p.hover to p.text2
    }
    Box(
        Modifier.heightIn(min = minHeight).clip(RoundedCornerShape(14.dp)).background(bg)
            .border(BorderStroke(1.dp, if (selected) fg else Color.Transparent), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = "Что делает «${chip.title}»", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .alpha(if (chip.automatic) 0.75f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(chip.title, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SmallPill(text: String, dashed: Boolean, minHeight: Dp = 28.dp, onClick: () -> Unit) {
    val p = FuryColors.current
    Box(
        Modifier.heightIn(min = minHeight).clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (dashed) p.line3 else p.line2, RoundedCornerShape(14.dp))
            .background(if (dashed) Color.Transparent else p.panel2)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (dashed) p.muted else p.text, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun InitialBadge(name: String, size: Dp) {
    val p = FuryColors.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(p.accDeep).border(1.dp, p.accLine, RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = p.acc, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun PencilGlyph() {
    Text("✎", color = FuryColors.current.text, style = MaterialTheme.typography.titleLarge)
}

// ───────────────────────────── tablet / phone ─────────────────────────────

/** Phone and tablet, pinned: name row, health with ±, a strip of the other resources, a strip of conditions. */
@Composable
fun SheetPlayHeaderCompact(
    host: DublSheetEditHost,
    state: SheetScreenState,
    playState: SheetPlayState,
    onOpenEditor: () -> Unit,
    portrait: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val data = headerData(host)
    val p = FuryColors.current
    Column(
        modifier.fillMaxWidth().background(p.head).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            portrait?.invoke() ?: InitialBadge(data.name, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(data.name, color = p.text, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(data.concept.ifBlank { "Без концепта" }, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CompactEconomy(data.economy) { state.openDialog(SheetDialog.Economy) }
            FuryIconButton("Редактор", onOpenEditor, Modifier.border(1.dp, p.line2, RoundedCornerShape(12.dp))) { PencilGlyph() }
        }
        val health = data.vitals.firstOrNull { it.id == "health" }
        if (health != null) HealthRow(health, host, state)
        val rest = data.vitals.filter { it.id != "health" }
        if (rest.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rest.forEach { vital ->
                    ResourceChip(vital, playState.selectedResource == vital.id) {
                        playState.selectedResource = if (playState.selectedResource == vital.id) null else vital.id
                    }
                }
            }
            rest.firstOrNull { it.id == playState.selectedResource }?.let { vital -> ResourceAdjuster(vital, host, state) { playState.selectedResource = null } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            data.conditions.forEach { chip -> ConditionChip(chip, playState.selectedCondition == chip.key, FuryMinTouch) { playState.toggleCondition(chip.key) } }
            SmallPill("+ состояние", dashed = true, FuryMinTouch) { state.openDialog(SheetDialog.Conditions) }
            SmallPill(SHEET_RESOURCES_SETUP, dashed = true, FuryMinTouch) { state.openDialog(SheetDialog.Resources) }
        }
        SelectedConditionLine(data.conditions, playState, host, FuryMinTouch)
    }
}

@Composable
private fun CompactEconomy(economy: CharacterEconomyBreakdown, onClick: () -> Unit) {
    val p = FuryColors.current
    val negative = economy.abilityPointsRemaining < 0
    Row(
        Modifier.heightIn(min = FuryMinTouch).clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Опыт и очки способностей", onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(p.goldSoft).border(1.dp, p.goldLine, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text("XP ${economy.remainingXp}", color = p.goldText, style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), maxLines = 1, softWrap = false)
        }
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (negative) p.accSoft else p.panel2).padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(
                "ОС ${economy.abilityPointsRemaining}/${economy.abilityPointsBudget}", color = if (negative) p.accText else p.text,
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), maxLines = 1, softWrap = false,
            )
        }
    }
}

@Composable
private fun HealthRow(vital: SheetVital, host: DublSheetEditHost, state: SheetScreenState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(sheetVitalColor(vital)))
        Box(Modifier.heightIn(min = FuryMinTouch).widthIn(min = 56.dp).clip(RoundedCornerShape(6.dp)).openVital(vital, state), contentAlignment = Alignment.CenterStart) {
            Text(
                "${vital.current}/${vital.maximum}", color = vitalValueColor(vital), maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.titleLarge.merge(TabularNumbers).copy(fontSize = 15.sp),
            )
        }
        FuryBar(vital.current, vital.maximum, sheetVitalColor(vital), Modifier.weight(1f), height = 10.dp)
        FuryIconButton("Уменьшить ${vital.label}", { changeVital(host, vital, -1) }, enabled = vitalCanDecrease(vital)) {
            Text("−", color = FuryColors.current.text, style = MaterialTheme.typography.titleLarge)
        }
        FuryIconButton("Увеличить ${vital.label}", { changeVital(host, vital, 1) }, enabled = vital.current < vital.maximum) {
            Text("+", color = FuryColors.current.text, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ResourceChip(vital: SheetVital, selected: Boolean, onClick: () -> Unit) {
    val p = FuryColors.current
    val color = sheetVitalColor(vital)
    Column(
        Modifier.widthIn(min = 84.dp).heightIn(min = FuryMinTouch).clip(RoundedCornerShape(10.dp)).background(p.panel)
            .border(1.dp, if (selected) p.acc else p.line, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Изменить ${vital.label}", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .alpha(if (vital.maximum == 0) 0.45f else 1f),
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(vitalTitle(vital), color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
            Text(
                "${vital.current}/${vital.maximum}", color = p.text, maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
            )
        }
        FuryBar(vital.current, vital.maximum, color, Modifier.width(64.dp), height = 3.dp)
    }
}

@Composable
private fun ResourceAdjuster(vital: SheetVital, host: DublSheetEditHost, state: SheetScreenState, onClose: () -> Unit) {
    val p = FuryColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.panel2).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${vitalTitle(vital)} · ${vital.current}/${vital.maximum}",
            Modifier.weight(1f).heightIn(min = FuryMinTouch).clip(RoundedCornerShape(6.dp)).openVital(vital, state).wrapContentHeight(),
            color = p.text,
            style = MaterialTheme.typography.labelLarge.merge(TabularNumbers), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (vital.restoreable) {
            FuryIconButton("Восстановить ${vital.label}", { vital.resource?.let(host::restoreMountedResource) }) {
                Text("↺", color = p.text, style = MaterialTheme.typography.titleLarge)
            }
        }
        FuryIconButton("Уменьшить ${vital.label}", { changeVital(host, vital, -1) }, enabled = vitalCanDecrease(vital)) {
            Text("−", color = p.text, style = MaterialTheme.typography.titleLarge)
        }
        FuryIconButton("Увеличить ${vital.label}", { changeVital(host, vital, 1) }, enabled = vital.current < vital.maximum) {
            Text("+", color = p.text, style = MaterialTheme.typography.titleLarge)
        }
        FuryIconButton("Закрыть", onClose) { Text("×", color = p.muted, style = MaterialTheme.typography.titleLarge) }
    }
}
