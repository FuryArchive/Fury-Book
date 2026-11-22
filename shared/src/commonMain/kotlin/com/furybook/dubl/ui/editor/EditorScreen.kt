package com.furybook.dubl.ui.editor

import com.furybook.ui.text.ruCount

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.dubl.ui.sheet.SheetPortraitLoader
import com.furybook.dubl.ui.sheet.sheetChiEconomyMounted
import com.furybook.dubl.ui.sheet.sheetLayoutFor
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing
import kotlinx.coroutines.delay

private val RAIL_WIDTH = 232.dp
private val SUMMARY_WIDTH = 312.dp
private const val DEFAULT_NAME = "Новый персонаж"
/** A toast with «Вернуть» stays long enough to reach the button; a plain confirmation is short. */
internal fun editorToastMs(undoable: Boolean): Long = if (undoable) 5000L else 2600L

/** Everything the frame and the steps read for one character state. Built once per change. */
internal class EditorFrameData(host: DublEditorHost, val before: CharacterRestorePoint) {
    val context = EditorPerkContext(host)
    val economy = context.economy
    val now = host.restorePoint()
    val changes = editorChanges(
        before, now, { editorCatalogFor(host, it) }, includeChi = sheetChiEconomyMounted(host),
        effectNames = host.skillEffectCatalog.effects.associate { it.id to it.sourceName },
    )
    val derived = editorDerived(now.character, before.character)
    val problems = context.problems()
    val creating = !host.active.creationComplete
    val rankCount = host.active.skills.values.sumOf { it.rank }
    val perkCount = host.active.development.keys.count { id ->
        id != MagicEquipmentRules.BASE_MANA_ENTRY_ID && host.developmentCatalog.byId(id)?.isAbility != true
    }
    val spellCount = host.active.magic.spells.count { it.learned }
    val gearCount = host.active.gear.items.size
    val carriedCount = host.active.gear.items.count { it.carried }
}

/**
 * The character editor: step rail or tabs, the current step, and the live summary with «Отменить всё».
 * Every change is saved at once through [host]; the snapshot taken when [state] was opened is what
 * the changes list compares against and «Отменить всё» restores.
 */
@Composable
fun EditorScreen(
    host: DublEditorHost,
    state: EditorState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    portraitLoader: SheetPortraitLoader? = null,
    onPickPortrait: (() -> Unit)? = null,
) {
    LaunchedEffect(host.active.id) { state.syncCharacter(host) }
    val toast = state.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(editorToastMs(state.toastUndoable))
            if (state.toast == toast) state.toast = null
        }
    }
    val before = state.snapshot ?: host.restorePoint()
    val data = remember(host, host.active, host.extras, host.developmentCatalog, before) { EditorFrameData(host, before) }
    SideEffect { state.dropStaleUndo(data.now) }
    val p = FuryColors.current
    BoxWithConstraints(modifier.fillMaxSize().background(p.bg)) {
        val layout = sheetLayoutFor(maxWidth.value.toInt())
        val wide = layout == SheetLayout.WIDE_WITH_TRAY
        val steps = editorSteps(host.active.creationComplete)
        val index = steps.indexOf(state.step).coerceAtLeast(0)
        Column(Modifier.fillMaxSize()) {
            EditorHeader(host, data, layout, onClose)
            if (!wide) EditorStepTabs(steps, state, data, layout)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (wide) EditorRail(steps, state, data, Modifier.width(RAIL_WIDTH).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EditorStepContent(host, state, data, layout, portraitLoader, onPickPortrait)
                    }
                    if (wide) EditorNavBar(steps, index, state, host, onClose)
                }
                if (wide) EditorSummary(host, state, data, Modifier.width(SUMMARY_WIDTH).fillMaxHeight())
            }
            if (!wide) EditorFooter(steps, index, state, host, data, onClose)
        }
        if (!wide && state.changesOpen) {
            FuryDialogScaffold(
                title = "Изменения за этот заход",
                onDismiss = { state.changesOpen = false },
                subtitle = editorNetText(data.changes),
                destructive = if (data.changes.lines.isEmpty()) null else FuryDialogAction("Отменить всё", { undoAll(host, state) }),
            ) { EditorChangeList(data.changes) }
        }
        toast?.let { text ->
            Box(Modifier.fillMaxSize().padding(bottom = if (wide) 72.dp else 84.dp), contentAlignment = Alignment.BottomCenter) {
                Row(
                    Modifier.widthIn(max = 560.dp).padding(horizontal = FurySpacing.lg).clip(RoundedCornerShape(12.dp)).background(p.text)
                        .padding(start = 14.dp, end = if (state.toastUndoable) 4.dp else 14.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text, Modifier.weight(1f, fill = false), color = p.bg, style = MaterialTheme.typography.labelLarge)
                    if (state.toastUndoable) {
                        Box(
                            Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { state.undoToast(host) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Вернуть", color = if (p.isLight) p.goldSoft else p.acc2, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }
    }
}

private fun undoAll(host: DublEditorHost, state: EditorState) {
    val point = state.snapshot ?: return
    if (host.restore(point)) {
        state.markRestored()
        state.changesOpen = false
        state.flash("Персонаж возвращён к тому, каким был при открытии редактора", undoableAt = host.restorePoint())
    }
}

/** The current step's body. */
@Composable
internal fun EditorStepContent(
    host: DublEditorHost,
    state: EditorState,
    data: EditorFrameData,
    layout: SheetLayout,
    portraitLoader: SheetPortraitLoader?,
    onPickPortrait: (() -> Unit)?,
) {
    when (state.step) {
        EditorStep.DEVELOPMENT -> EditorDevelopmentStep(host, state, data, layout)
        EditorStep.BASICS -> EditorBasicsStep(host, state, data, layout, portraitLoader, onPickPortrait)
        EditorStep.ATTRIBUTES -> EditorAttributesStep(host, data, layout)
        EditorStep.SKILLS -> EditorSkillsStep(host, state, data, layout)
        EditorStep.PERKS -> EditorPerksStep(host, state, data, layout)
        EditorStep.MAGIC -> EditorMagicStep(host, state, data, layout)
        EditorStep.GEAR -> EditorGearStep(host, state, data, layout)
        EditorStep.SUMMARY -> EditorSummaryStep(host, state, data, layout)
    }
}

internal fun editorContentPadding(layout: SheetLayout) = when (layout) {
    SheetLayout.PHONE -> 12.dp
    SheetLayout.WIDE -> 20.dp
    SheetLayout.WIDE_WITH_TRAY -> 24.dp
}

private fun editorTitle(host: DublEditorHost): String {
    val name = host.active.name.trim()
    val unnamed = name.isEmpty() || (!host.active.creationComplete && name == DEFAULT_NAME)
    return if (unnamed) DEFAULT_NAME else "Редактор · $name"
}

@Composable
private fun EditorHeader(host: DublEditorHost, data: EditorFrameData, layout: SheetLayout, onClose: () -> Unit) {
    val p = FuryColors.current
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(p.head).padding(start = 4.dp, end = FurySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).semantics { contentDescription = "Закрыть редактор и вернуться к листу" }
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("←", color = p.text, style = MaterialTheme.typography.titleLarge) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(editorTitle(host), color = p.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorPill(
                    if (data.creating) "Создание" else "Развитие",
                    if (data.creating) EditorPillColors(p.goldSoft, p.goldText) else EditorPillColors(p.hover, p.text2),
                )
                Text(
                    if (data.creating) "всё можно менять свободно" else "правки сохраняются сразу",
                    color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        EditorPrimaryButton("Готово", onClose, editorControl(layout))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

private fun stepSub(step: EditorStep, data: EditorFrameData, name: String): String = when (step) {
    EditorStep.DEVELOPMENT -> "свободно ${data.economy.remainingXp} XP"
    EditorStep.BASICS -> name.trim().takeIf { it.isNotEmpty() && it != DEFAULT_NAME } ?: "без имени"
    EditorStep.ATTRIBUTES -> "${data.economy.attributeXp} XP"
    EditorStep.SKILLS -> "${ruCount(data.rankCount, "ранг", "ранга", "рангов")} · ${data.economy.skillXp} XP"
    EditorStep.PERKS -> "${data.perkCount} взято · ${data.economy.developmentXp} XP"
    EditorStep.MAGIC -> "${ruCount(data.spellCount, "заклинание", "заклинания", "заклинаний")} · ${data.economy.manaXp + data.economy.magicSchoolXp + data.economy.spellXp} XP"
    EditorStep.GEAR -> "${ruCount(data.gearCount, "позиция", "позиции", "позиций")} · ${data.carriedCount} с собой"
    EditorStep.SUMMARY -> if (data.problems.isEmpty()) "всё в порядке" else ruCount(data.problems.size, "замечание", "замечания", "замечаний")
}

private fun stepNumber(step: EditorStep, steps: List<EditorStep>): String =
    if (step == EditorStep.DEVELOPMENT) "★" else (steps.filter { it != EditorStep.DEVELOPMENT }.indexOf(step) + 1).toString()

@Composable
private fun StepNumber(text: String, current: Boolean) {
    val p = FuryColors.current
    Box(
        Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (current) p.acc2 else p.tile),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (current) p.onAcc else p.muted, style = MaterialTheme.typography.labelMedium.merge(EditorTabular))
    }
}

@Composable
private fun EditorRail(steps: List<EditorStep>, state: EditorState, data: EditorFrameData, modifier: Modifier) {
    val p = FuryColors.current
    Row(modifier) {
        Column(
            Modifier.weight(1f).fillMaxHeight().background(p.head).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            steps.forEach { step ->
                val current = state.step == step
                val warn = step == EditorStep.SUMMARY && data.problems.isNotEmpty()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (current) p.accSoft else p.head)
                        .border(1.dp, if (current) p.accLine else p.head, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab, onClick = { state.step = step }).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StepNumber(stepNumber(step, steps), current)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(step.title, color = if (current) p.text else p.text2, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stepSub(step, data, data.now.character.name),
                            color = when { warn -> p.goldText; current -> p.accText; else -> p.dim },
                            style = MaterialTheme.typography.labelSmall.merge(EditorTabular), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(p.line))
    }
}

@Composable
private fun EditorStepTabs(steps: List<EditorStep>, state: EditorState, data: EditorFrameData, layout: SheetLayout) {
    val p = FuryColors.current
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(0) }
    // Where the current tab sits in the strip; the strip scrolls so it is never hidden off the edge.
    var currentTab by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(state.step, currentTab, viewport) {
        val (start, width) = currentTab ?: return@LaunchedEffect
        if (viewport > 0) scroll.animateScrollTo(editorTabScrollTarget(start, width, viewport, scroll.maxValue))
    }
    Row(
        Modifier.fillMaxWidth().onSizeChanged { viewport = it.width }.horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEach { step ->
            val current = state.step == step
            Row(
                Modifier
                    .then(if (current) Modifier.onPlaced { currentTab = it.positionInParent().x.toInt() to it.size.width } else Modifier)
                    .heightIn(min = editorControl(layout)).clip(RoundedCornerShape(10.dp))
                    .background(if (current) p.accSoft else p.panel)
                    .border(1.dp, if (current) p.accLine else p.line, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Tab, onClick = { state.step = step }).padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepNumber(stepNumber(step, steps), current)
                Text(step.title, color = if (current) p.accText else p.text2, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                if (step == EditorStep.SUMMARY && data.problems.isNotEmpty()) EditorWarnMark("Есть замечания")
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
private fun EditorNavBar(steps: List<EditorStep>, index: Int, state: EditorState, host: DublEditorHost, onClose: () -> Unit) {
    val p = FuryColors.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    Row(
        Modifier.fillMaxWidth().background(p.head).padding(horizontal = 28.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorOutlineButton(
            "← " + (steps.getOrNull(index - 1)?.title ?: "Назад"), { state.back(host) }, 38.dp, enabled = index > 0,
        )
        Spacer(Modifier.weight(1f))
        val last = index == steps.lastIndex
        EditorPrimaryButton(if (last) "Готово" else "Далее: ${steps[index + 1].title} →", { if (last) onClose() else state.next(host) }, 38.dp)
    }
}

@Composable
private fun EditorFooter(steps: List<EditorStep>, index: Int, state: EditorState, host: DublEditorHost, data: EditorFrameData, onClose: () -> Unit) {
    val p = FuryColors.current
    val negative = data.economy.remainingXp < 0
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
    Row(
        Modifier.fillMaxWidth().background(p.head).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm),
    ) {
        Column(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (negative) p.accSoft else p.goldSoft)
                .border(1.dp, if (negative) p.accLine else p.goldLine, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        ) {
            Text(
                "XP ${data.economy.remainingXp}", color = if (negative) p.accText else p.gold, maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.titleSmall.merge(EditorTabular).copy(fontSize = 13.sp),
            )
            Text(
                "ОС ${data.economy.abilityPointsRemaining}/${data.economy.abilityPointsBudget}",
                color = if (data.economy.abilityPointsRemaining < 0) p.accText else p.goldText, maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.labelSmall.merge(EditorTabular),
            )
        }
        EditorOutlineButton("↺ ${data.changes.lines.size}", { state.changesOpen = true }, 44.dp, Modifier.semantics {
            contentDescription = "Изменения за этот заход: ${data.changes.lines.size}"
        })
        Spacer(Modifier.weight(1f))
        EditorSquareButton("‹", "Назад: " + (steps.getOrNull(index - 1)?.title ?: ""), index > 0, 44.dp) { state.back(host) }
        val last = index == steps.lastIndex
        EditorPrimaryButton(if (last) "Готово" else "Далее ›", { if (last) onClose() else state.next(host) }, 44.dp)
    }
}

@Composable
private fun EditorSummary(host: DublEditorHost, state: EditorState, data: EditorFrameData, modifier: Modifier) {
    val p = FuryColors.current
    val economy = data.economy
    val negative = economy.remainingXp < 0
    Row(modifier) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(p.line))
        Column(
            Modifier.weight(1f).fillMaxHeight().background(p.head).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorCard(background = if (negative) p.accSoft else p.goldSoft, border = if (negative) p.accLine else p.goldLine) {
                EditorOverline("Свободно", color = p.goldText)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        economy.remainingXp.toString(), color = if (negative) p.accText else p.gold, maxLines = 1, softWrap = false,
                        style = MaterialTheme.typography.displaySmall.merge(EditorTabular).copy(fontSize = 30.sp, lineHeight = 32.sp),
                    )
                    Text("из ${economy.totalExperience} XP", color = p.goldText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                val fraction = editorSpentFraction(economy.spentXp, economy.totalExperience)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.track)) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(if (negative) p.acc else p.gold))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Очки способностей", Modifier.weight(1f), color = p.text2, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${economy.abilityPointsRemaining} / ${economy.abilityPointsBudget}",
                        color = if (economy.abilityPointsRemaining < 0) p.accText else p.text,
                        style = MaterialTheme.typography.labelLarge.merge(EditorTabular),
                    )
                }
            }
            EditorDerivedGrid(data.derived, columns = 3, tileHeight = 46.dp)
            EditorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EditorOverline("Изменения за этот заход", Modifier.weight(1f))
                    Text(data.changes.lines.size.toString(), color = p.muted, style = MaterialTheme.typography.labelSmall)
                }
                EditorChangeList(data.changes)
                if (data.changes.lines.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(editorNetText(data.changes), Modifier.weight(1f), color = p.muted, style = MaterialTheme.typography.labelSmall)
                        EditorOutlineButton("Отменить всё", { undoAll(host, state) }, 38.dp, accent = true)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorChangeList(changes: EditorChanges) {
    val p = FuryColors.current
    if (changes.lines.isEmpty()) {
        Text("Пока ничего. Всё сохраняется сразу, а отсюда можно откатить весь заход.", color = p.muted, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        changes.lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.text, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodySmall)
                val xp = editorXpText(line.xpSpent)
                if (xp.isNotEmpty()) {
                    Text(
                        xp, color = if (line.xpSpent > 0) p.goldText else p.dLabel,
                        style = MaterialTheme.typography.labelSmall.merge(EditorTabular), maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}
