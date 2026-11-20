package com.furybook.dubl.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.DevelopmentEntry
import com.furybook.dubl.model.RequirementStatus
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FuryHelpfulChip
import com.furybook.ui.theme.FuryHelpfulChipText
import com.furybook.ui.theme.FurySpacing

private val DETAIL_WIDTH = 360.dp
private const val SPLIT_MIN_CONTENT_DP = 760

/** The detail pane sits beside the list only when the list keeps ~350dp next to its 360dp; otherwise it opens over it. */
internal fun editorPerksSplit(contentWidthDp: Int): Boolean = contentWidthDp >= SPLIT_MIN_CONTENT_DP

private val KIND_TITLES = mapOf(
    EditorPerkKind.REGULAR to "Обычные",
    EditorPerkKind.SPECIAL to "Особые",
    EditorPerkKind.MARTIAL to "Боевые",
    EditorPerkKind.CHI to "ЦИ",
    EditorPerkKind.OWNED to "Взятые",
)

/** Навыки: the catalog by kind, with status pills, and a detail that takes a perk — through its whole chain if needed. */
@Composable
internal fun EditorPerksStep(host: DublEditorHost, state: EditorState, data: EditorFrameData, layout: SheetLayout) {
    val context = data.context
    val wide = layout == SheetLayout.WIDE_WITH_TRAY
    val kinds = EditorPerkKind.entries.filter { it != EditorPerkKind.CHI || context.showsChi }
    if (state.perkKind !in kinds) state.perkKind = EditorPerkKind.REGULAR
    val kindCounts = remember(data) { kinds.associateWith { context.perkCounts(it).getValue(EditorPerkFilter.ALL) } }
    val counts = remember(data, state.perkKind, state.plannedIds) { context.perkCounts(state.perkKind, state.plannedIds) }
    val groups = remember(data, state.perkKind, state.perkFilter, state.perkQuery, state.selectedEntryId, state.plannedIds) {
        context.perkGroups(state.perkKind, state.perkFilter, state.perkQuery, keep = state.selectedEntryId, planned = state.plannedIds)
    }
    val plan = remember(data, state.plannedIds) { context.planSummary(state.plannedIds) }
    var form by remember { mutableStateOf<Pair<DevelopmentEntry, EditorPerkFormMode>?>(null) }
    val openForm: (String?, EditorPerkFormMode) -> Unit = { id, mode ->
        val base = if (id == null) editorNewPerk() else host.developmentCatalog.byId(id)
        if (base != null) form = base to mode
    }
    val selected = state.selectedEntryId
    val detail = remember(data, selected, state.choiceSelections, state.optionIndex) {
        selected?.let { context.perkDetail(it, state.choiceSelections, state.optionIndex) }
    }
    val padding = editorContentPadding(layout)
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            PerksHeader(state, kinds, kindCounts, counts, layout) { openForm(null, EditorPerkFormMode.NEW) }
            plan?.let { PlanCard(host, state, it, layout) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val split = wide && editorPerksSplit(maxWidth.value.toInt())
    if (split) {
        Column(Modifier.fillMaxSize().padding(start = padding, end = padding, top = padding), verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            header()
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    perkList(groups, state, layout)
                    item { Box(Modifier.heightIn(min = padding)) }
                }
                Column(Modifier.width(DETAIL_WIDTH).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = padding)) {
                    if (detail == null) {
                        EditorCard(Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
                            Text("Выберите навык слева — здесь будут правила, требования и кнопка «Взять».", color = FuryColors.current.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        PerkDetail(host, state, context, detail, layout, showBack = false, openForm)
                    }
                }
            }
        }
    } else if (detail != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
            PerkDetail(host, state, context, detail, layout, showBack = true, openForm)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(padding), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            item { Box(Modifier.padding(bottom = FurySpacing.sm)) { header() } }
            perkList(groups, state, layout)
        }
    }
    }
    form?.let { (base, mode) -> EditorPerkFormDialog(host, state, base, mode, layout) { form = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerksHeader(
    state: EditorState,
    kinds: List<EditorPerkKind>,
    kindCounts: Map<EditorPerkKind, Int>,
    counts: Map<EditorPerkFilter, Int>,
    layout: SheetLayout,
    onNewPerk: () -> Unit,
) {
    val control = editorControl(layout)
    Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            Box(Modifier.weight(1f)) {
                EditorStepTitle("Навыки", "Нажмите навык — откроются правила, требования и кнопка «Взять». Недостающие ступени берутся цепочкой.")
            }
            EditorOutlineButton("+ Свой навык", onNewPerk, control)
        }
        // Wraps rather than scrolls: «Взятые» must never hide off the edge of a phone.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            kinds.forEach { kind ->
                EditorChip("${KIND_TITLES.getValue(kind)} ${kindCounts[kind] ?: 0}", state.perkKind == kind, {
                    state.perkKind = kind
                    if (kind == EditorPerkKind.OWNED) state.perkFilter = EditorPerkFilter.ALL
                }, control)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm), itemVerticalAlignment = Alignment.Bottom) {
            listOf(
                EditorPerkFilter.ALL to "Все",
                EditorPerkFilter.AVAILABLE to "Доступно",
                EditorPerkFilter.ALMOST to "Почти",
                EditorPerkFilter.PLAN to "План",
            ).forEach { (filter, title) ->
                EditorChip("$title ${counts[filter] ?: 0}", state.perkFilter == filter, { state.perkFilter = filter }, control, gold = true, role = Role.RadioButton)
            }
            FuryTextField(state.perkQuery, { state.perkQuery = it }, "Поиск по названию и правилам", Modifier.widthIn(min = 200.dp, max = 360.dp))
        }
    }
}

/** «План персонажа»: what the ☆ perks still cost together, and one button that takes them all. */
@Composable
private fun PlanCard(host: DublEditorHost, state: EditorState, plan: EditorPlanSummary, layout: SheetLayout) {
    val p = FuryColors.current
    EditorCard(Modifier.fillMaxWidth(), background = p.goldSoft, border = p.goldLine) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("План персонажа · ${plan.count}", color = p.text, style = MaterialTheme.typography.labelLarge)
                Text(
                    "Осталось ${plan.xpCost} XP" + (if (plan.abilityCost > 0) " · ${plan.abilityCost} ОС" else ""),
                    color = p.goldText, style = MaterialTheme.typography.labelMedium.merge(EditorTabular),
                )
            }
            val request = plan.request
            // Outline: the detail's «Взять» stays the one filled action of the step.
            EditorOutlineButton("Взять план", {
                if (request != null) {
                    val result = host.acquireDevelopment(request)
                    state.flash(if (result.applied) "План взят" else result.message)
                }
            }, editorControl(layout), enabled = request != null)
        }
        plan.blockReason?.let { Text(it, color = p.accText, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun LazyListScope.perkList(groups: List<EditorPerkGroup>, state: EditorState, layout: SheetLayout) {
    if (groups.isEmpty()) {
        item {
            EditorCard(Modifier.fillMaxWidth()) {
                Text("Ничего не найдено. Попробуйте «Все» или другой вид.", color = FuryColors.current.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    groups.forEach { group ->
        item(key = "g:" + group.title) { EditorOverline("${group.title} · ${group.cards.size}", Modifier.padding(top = 6.dp, start = 2.dp)) }
        items(group.cards, key = { "c:" + group.title + ":" + it.entryId }) { card -> PerkCard(card, state.selectedEntryId == card.entryId, layout) { state.selectChoices(card.entryId) } }
    }
}

@Composable
private fun PerkCard(card: EditorPerkCard, selected: Boolean, layout: SheetLayout, onClick: () -> Unit) {
    val p = FuryColors.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().padding(start = if (card.depth > 0) 16.dp else 0.dp).heightIn(min = if (layout == SheetLayout.WIDE_WITH_TRAY) 52.dp else 56.dp)
            .clip(shape).background(p.panel2).border(1.dp, if (selected) p.acc else if (card.depth > 0) p.accLine else p.line, shape)
            .clickable(role = Role.Button, onClickLabel = "Подробнее о «${card.name}»", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.name, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (card.warn) EditorWarnMark("Требования больше не выполнены")
            Text(card.costText, color = p.goldText, style = MaterialTheme.typography.labelMedium.merge(EditorTabular), maxLines = 1, softWrap = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.category, Modifier.weight(1f), color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            EditorPill(card.pill, editorPillColors(card.status, card.rank))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerkDetail(
    host: DublEditorHost, state: EditorState, context: EditorPerkContext, detail: EditorPerkDetail, layout: SheetLayout, showBack: Boolean,
    openForm: (String?, EditorPerkFormMode) -> Unit,
) {
    val p = FuryColors.current
    val control = editorControl(layout)
    var confirmForce by remember(detail.entryId) { mutableStateOf(false) }
    EditorCard(Modifier.fillMaxWidth()) {
        if (showBack) {
            Text(
                "← К списку", Modifier.clickable(role = Role.Button) { state.selectChoices(null) }.padding(vertical = 10.dp),
                color = p.goldText, style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(detail.name, color = p.text, style = MaterialTheme.typography.titleLarge)
                Text("${detail.category} · ${detail.kindLabel} · ${detail.costText}", color = p.muted, style = MaterialTheme.typography.labelSmall)
            }
            EditorPill(detail.pill, editorPillColors(detail.status, detail.rank))
        }
        val planned = detail.entryId in state.plannedIds
        EditorChip(if (planned) "★ В плане" else "☆ В план", planned, { state.togglePlanned(detail.entryId) }, control, gold = true, role = Role.Checkbox)
        if (detail.maxRank > 1) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Long rank ladders (Невероятное здоровье has 20) read better as text than as a row of pips.
                if (detail.maxRank <= 10) EditorPips(detail.rank, detail.maxRank, p.gold, pipWidth = 14.dp, height = 6.dp)
                Text("ранг ${detail.rank} из ${detail.maxRank}", color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
            }
        }
        Text(
            detail.benefit, color = if (detail.benefitMissing) p.dim else p.text2,
            style = MaterialTheme.typography.bodyMedium.let { if (detail.benefitMissing) it.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) else it },
        )
        EditorOverline("Требования")
        if (detail.requirements.isEmpty()) Text("Нет", color = p.muted, style = MaterialTheme.typography.bodySmall)
        detail.requirements.forEach { line -> RequirementLine(line, control) { id -> state.openPerk(context, id) } }
        if (detail.unlocks.isNotEmpty()) {
            EditorOverline("Открывает")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                detail.unlocks.forEach { link -> PerkLink(link.name, control) { state.openPerk(context, link.entryId) } }
            }
        }
        detail.choices.forEach { choice ->
            EditorOverline(choice.label)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                choice.options.forEachIndexed { index, option ->
                    EditorChip(option, choice.selected == index, {
                        if (choice.id == "option") state.optionIndex = index
                        else state.choiceSelections = state.choiceSelections + (choice.id to index)
                    }, control, gold = true, role = Role.RadioButton)
                }
            }
        }
        if (detail.chain.isNotEmpty()) {
            EditorCard(background = p.goldSoft, border = p.goldLine) {
                EditorOverline("Цепочка · ${detail.chainCost}", color = p.goldText)
                detail.chain.forEach { step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(step.label, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodySmall)
                        Text(step.costText, color = p.goldText, style = MaterialTheme.typography.labelSmall.merge(EditorTabular), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        detail.blockReason?.let { Text(it, color = p.accText, style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            val action = detail.action
            if (action != null && detail.actionLabel != null) {
                EditorPrimaryButton(detail.actionLabel, {
                    applyPerkAction(host, state, detail.entryId, detail.name, action, chain = detail.chain.isNotEmpty())
                }, control)
            }
            val prerequisites = detail.prerequisitesAction
            if (prerequisites != null && detail.prerequisitesLabel != null) {
                EditorOutlineButton(detail.prerequisitesLabel, {
                    applyPerkAction(host, state, detail.entryId, detail.name, prerequisites, chain = true, doneText = "Требования добраны: ${detail.name}")
                }, control)
            }
            val allRanks = detail.allRanksAction
            if (allRanks != null && detail.allRanksLabel != null) {
                EditorOutlineButton(detail.allRanksLabel, {
                    applyPerkAction(host, state, detail.entryId, detail.name, allRanks, chain = false, doneText = "Взяты все ранги: ${detail.name}")
                }, control)
            }
            if (detail.forceAction != null && detail.forceLabel != null) EditorOutlineButton(detail.forceLabel, { confirmForce = true }, control)
            val remove = detail.removeAction
            if (remove != null && detail.removeLabel != null) {
                EditorOutlineButton(detail.removeLabel, { applyPerkAction(host, state, detail.entryId, detail.name, remove, chain = false) }, control, accent = true)
            }
        }
        EditorOverline("Запись", Modifier.padding(top = FurySpacing.xs))
        // The record itself: a local edit of a rulebook perk, or the player's own perk.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            when (detail.origin) {
                EditorPerkOrigin.CUSTOM -> {
                    EditorOutlineButton("Редактировать свою запись", { openForm(detail.entryId, EditorPerkFormMode.EDIT_OWN) }, control)
                    EditorOutlineButton("Удалить свою запись", { editorRemoveOwnPerk(host, state, detail.entryId, detail.name) }, control, accent = true)
                }
                else -> {
                    EditorOutlineButton("Локальная правка", { openForm(detail.entryId, EditorPerkFormMode.LOCAL_EDIT) }, control)
                    if (detail.origin == EditorPerkOrigin.BOOK_EDITED) {
                        EditorOutlineButton("Сбросить к рулбуку", { editorResetPerk(host, state, detail.entryId, detail.name) }, control, accent = true)
                    }
                }
            }
        }
    }
    val force = detail.forceAction
    if (confirmForce && force != null) {
        FuryDialogScaffold(
            title = "Требования не выполнены",
            onDismiss = { confirmForce = false },
            subtitle = "Добавить «${detail.name}» несмотря на это? Запись останется помеченной, пока условия не будут выполнены.",
            primary = FuryDialogAction(detail.forceLabel ?: "Взять всё равно", {
                confirmForce = false
                applyPerkAction(host, state, detail.entryId, detail.name, force, chain = false)
            }),
            secondary = FuryDialogAction("Отмена", { confirmForce = false }),
        ) {
            detail.forceChecks.forEach { Text("• $it", color = p.text2, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** A perk name that opens that perk's detail (requirement targets, «Открывает»). */
@Composable
private fun PerkLink(name: String, height: androidx.compose.ui.unit.Dp, onOpen: () -> Unit) {
    Box(Modifier.heightIn(min = height).clickable(role = Role.Button, onClickLabel = "Открыть «$name»", onClick = onOpen), contentAlignment = Alignment.CenterStart) {
        Text("$name →", color = FuryColors.current.goldText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RequirementLine(line: EditorRequirementLine, height: androidx.compose.ui.unit.Dp, onOpen: (String) -> Unit) {
    val p = FuryColors.current
    val (mark, bg, fg) = when (line.status) {
        RequirementStatus.OK -> Triple("✓", FuryHelpfulChip, FuryHelpfulChipText)
        RequirementStatus.FAIL -> Triple("✗", p.accSoft, p.accText)
        RequirementStatus.MANUAL -> Triple("?", p.hover, p.muted)
    }
    val target = line.targetId
    val link = if (target != null) Modifier.heightIn(min = height).clickable(role = Role.Button, onClickLabel = "Открыть требование") { onOpen(target) } else Modifier
    Row(Modifier.heightIn(min = 26.dp).then(link), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(mark, color = fg, style = MaterialTheme.typography.labelSmall)
        }
        Text(line.text + if (target != null) " →" else "", Modifier.weight(1f), color = if (target != null) p.goldText else p.text2, style = MaterialTheme.typography.bodySmall)
    }
}
