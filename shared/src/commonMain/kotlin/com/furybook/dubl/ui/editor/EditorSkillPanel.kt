package com.furybook.dubl.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.components.FuryDialogAction
import com.furybook.ui.components.FuryDialogScaffold
import com.furybook.ui.components.FuryStepper
import com.furybook.ui.components.FuryTextField
import com.furybook.ui.theme.FuryColors
import kotlinx.coroutines.delay

private const val SKILL_TEXT_SAVE_DELAY_MS = 600L

/**
 * Everything the old Умения tab's inspector did, in the editor: bonus breakdown, allowed attributes, permanent
 * modifier, rule note, local edits with «К рулбуку», rules-automation switches, hide and delete. Everything saves
 * live; hide, delete and «К рулбуку» offer «Вернуть».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorSkillPanel(host: DublEditorHost, state: EditorState, detail: EditorSkillDetail, layout: SheetLayout, showBack: Boolean) {
    val p = FuryColors.current
    val control = editorControl(layout)
    val id = detail.skillId
    val draft = remember(host.active.id, id) { EditorSkillTextDraft(host.active.id, detail, state.restoreGeneration) }
    val currentHost by rememberUpdatedState(host)
    val currentGeneration by rememberUpdatedState(state.restoreGeneration)
    LaunchedEffect(draft, detail, state.restoreGeneration) { draft.sync(detail, state.restoreGeneration) }
    LaunchedEffect(draft, draft.note, draft.name, draft.description, draft.auto6, draft.auto12) {
        delay(SKILL_TEXT_SAVE_DELAY_MS)
        draft.save(currentHost, currentGeneration)
    }
    DisposableEffect(draft) { onDispose { draft.save(currentHost, currentGeneration) } }
    var localOpen by remember(id) { mutableStateOf(false) }

    // Hide, delete and «К рулбуку» keep no application undo step: the toast's «Вернуть» goes back to [before].
    fun act(toast: String, closes: Boolean, block: () -> Unit) {
        draft.save(host, state.restoreGeneration)
        val before = host.restorePoint()
        block()
        state.flash(toast, undoableAt = host.restorePoint(), undoTo = before)
        if (closes) state.skillPanelId = null
    }

    EditorCard(Modifier.fillMaxWidth()) {
        if (showBack) {
            Text(
                "← К списку", Modifier.clickable(role = Role.Button) { state.skillPanelId = null }.padding(vertical = 10.dp),
                color = p.goldText, style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(detail.name, color = p.text, style = MaterialTheme.typography.titleLarge)
                Text(detail.headline, color = p.muted, style = MaterialTheme.typography.labelSmall.merge(EditorTabular))
            }
            detail.origin?.let { EditorPill(it, EditorPillColors(p.goldSoft, p.goldText)) }
        }
        if (detail.description.isNotBlank()) Text(detail.description, color = p.text2, style = MaterialTheme.typography.bodyMedium)

        EditorOverline("Бонус проверки")
        detail.bonus.forEach { line ->
            EditorRow(minHeight = 40.dp) {
                Column(Modifier.weight(1f)) {
                    Text(line.title, color = p.text, style = MaterialTheme.typography.labelLarge)
                    Text(
                        line.unavailableReason.ifBlank { line.breakdown },
                        color = if (line.total == null) p.accText else p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(line.total?.let(::editorSigned) ?: "—", color = if (line.total == null) p.dim else p.goldText, style = MaterialTheme.typography.titleMedium.merge(EditorTabular))
            }
        }
        if (detail.attributes.size > 1) {
            Text("При броске выбирается одна из характеристик — они не складываются.", color = p.muted, style = MaterialTheme.typography.labelSmall)
        }

        EditorOverline("Допустимые характеристики")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AttributeId.entries.forEach { attribute ->
                EditorChip(attribute.shortTitle, attribute in detail.attributes, {
                    host.setSkillAttributes(id, editorToggledAttributes(detail.attributes, attribute))
                }, control, role = Role.Checkbox)
            }
        }

        EditorOverline("Постоянная поправка")
        FuryStepper(
            detail.modifier, { host.setSkillModifier(id, it) }, min = -20, max = 20,
            decreaseLabel = "Уменьшить поправку", increaseLabel = "Увеличить поправку",
        )

        FuryTextField(draft.note, { draft.edit(note = it.take(300)) }, "Особое правило / условный бонус", singleLine = false)
        Text("Например: +2 с подходящими инструментами или особое условие применения.", color = p.dim, style = MaterialTheme.typography.labelSmall)

        Text(
            (if (localOpen) "▾ " else "▸ ") + "Локальные правки",
            Modifier.clickable(role = Role.Button) { localOpen = !localOpen }.padding(vertical = 8.dp),
            color = p.text, style = MaterialTheme.typography.labelLarge,
        )
        if (localOpen) {
            Text("Перекрывают рулбук только для этого персонажа. Канон не меняется.", color = p.muted, style = MaterialTheme.typography.labelSmall)
            FuryTextField(draft.name, { draft.edit(name = it.take(120)) }, "Название")
            FuryTextField(draft.description, { draft.edit(description = it.take(600)) }, "Описание", singleLine = false)
            EditorOverline("Категория")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkillCategory.entries.forEach { category ->
                    EditorChip(category.title, detail.category == category, { host.setSkillCategoryOverride(id, category) }, control, role = Role.RadioButton)
                }
            }
            EditorOverline("Без обучения")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UntrainedRule.entries.forEach { rule ->
                    EditorChip(rule.label, detail.untrained == rule, { host.setSkillUntrainedOverride(id, rule) }, control, role = Role.RadioButton)
                }
            }
            FuryTextField(draft.auto6, { draft.edit(auto6 = it.take(120)) }, "Автоуспех 6")
            FuryTextField(draft.auto12, { draft.edit(auto12 = it.take(120)) }, "Автоуспех 12")
            if (detail.canResetToBook) {
                EditorOutlineButton("К рулбуку", { act("«${detail.name}»: правки сброшены к рулбуку", closes = false) { host.resetSkillDefinitionOverrides(id) } }, control)
            }
        }

        if (detail.effects.isNotEmpty()) {
            EditorOverline("Автоматизация правил")
            Text("Если приложение неверно трактует эффект, его можно выключить только для этого персонажа.", color = p.muted, style = MaterialTheme.typography.labelSmall)
            detail.effects.forEach { effect ->
                EditorRow {
                    Column(Modifier.weight(1f)) {
                        Text(effect.source, color = p.text, style = MaterialTheme.typography.labelLarge)
                        Text(effect.text, color = p.muted, style = MaterialTheme.typography.labelSmall)
                    }
                    EditorChip(if (effect.enabled) "Вкл." else "Выкл.", effect.enabled, { host.setSkillEffectEnabled(effect.id, !effect.enabled) }, control, role = Role.Switch)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            EditorOutlineButton("Скрыть из списка", { act("Скрыто: ${detail.name}", closes = true) { host.hideSkill(id) } }, control)
            Spacer(Modifier.weight(1f))
            if (detail.canDelete) {
                EditorOutlineButton("Удалить", { act("Удалено: ${detail.name}", closes = true) { host.deleteDynamicSkill(id) } }, control, accent = true)
            }
        }
        Text("Скрытие убирает умение из списков, но не удаляет его.", color = p.dim, style = MaterialTheme.typography.labelSmall)
    }
}

/** «Скрытые · N»: hidden skills with «Вернуть» each and «Вернуть все». */
@Composable
internal fun EditorHiddenSkillsDialog(host: DublEditorHost, layout: SheetLayout, onDismiss: () -> Unit) {
    val p = FuryColors.current
    val hidden = host.active.resolvedSkills(includeHidden = true).filter { it.id in host.active.hiddenSkillIds }
    FuryDialogScaffold(
        title = "Скрытые умения",
        onDismiss = onDismiss,
        subtitle = "Они не удалены — их можно вернуть.",
        primary = if (hidden.isNotEmpty()) FuryDialogAction("Вернуть все", { host.restoreAllSkills(); onDismiss() }) else null,
        secondary = FuryDialogAction("Закрыть", onDismiss),
    ) {
        if (hidden.isEmpty()) Text("Скрытых умений нет.", color = p.muted, style = MaterialTheme.typography.bodySmall)
        hidden.forEach { skill ->
            EditorRow {
                Text(skill.name, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                EditorOutlineButton("Вернуть", { host.restoreSkill(skill.id) }, editorControl(layout))
            }
        }
    }
}
