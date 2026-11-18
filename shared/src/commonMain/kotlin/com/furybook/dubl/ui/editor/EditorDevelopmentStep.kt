package com.furybook.dubl.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing

/** Развитие: what fits in free XP right now, one tap each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorDevelopmentStep(host: DublEditorHost, state: EditorState, data: EditorFrameData, layout: SheetLayout) {
    val p = FuryColors.current
    val context = data.context
    val view = remember(data) { context.developmentView(host, state.developmentPins) }
    // The first view of this visit pins the rows, so nothing reorders or disappears while the player taps.
    SideEffect { if (state.developmentPins == null) state.developmentPins = view.pins }
    DisposableEffect(Unit) { onDispose { state.developmentPins = null } }
    val control = editorControl(layout)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(editorContentPadding(layout)),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.md),
    ) {
        EditorStepTitle("Развитие", editorDevelopmentSubtitle(data.economy.remainingXp, data.economy.abilityPointsRemaining))
        EditorCard(Modifier.editorReadableWidth()) {
            SectionHeader("Повысить умение", "Все умения →") { state.step = EditorStep.SKILLS }
            if (view.skills.isEmpty()) Muted("Не хватает опыта на следующий ранг ни одного умения.")
            view.skills.forEach { skill ->
                EditorRow {
                    Column(Modifier.weight(1f)) {
                        Text(skill.name, color = p.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${skill.attrShort} · ранг ${skill.from} → ${skill.from + 1} · бонус +${skill.bonusAfter}",
                            color = p.muted, style = MaterialTheme.typography.labelSmall.merge(EditorTabular), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("${skill.cost} XP", color = p.goldText, style = MaterialTheme.typography.labelLarge.merge(EditorTabular), maxLines = 1, softWrap = false)
                    EditorSquareButton("+", "Повысить ${skill.name} до ранга ${skill.from + 1}", skill.affordable, control) {
                        host.changeSkillRank(skill.skillId, 1)
                        state.flash("${skill.name}: ранг ${skill.from + 1}")
                    }
                }
            }
        }
        EditorCard(Modifier.editorReadableWidth()) {
            SectionHeader("Повысить характеристику", "Все →") { state.step = EditorStep.ATTRIBUTES }
            if (view.attributes.isEmpty()) Muted("На следующий пункт характеристики опыта пока не хватает.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                view.attributes.forEach { attr ->
                    EditorOutlineButton("${attr.short} ${attr.from}→${attr.from + 1} · ${attr.cost}", {
                        host.changeAttribute(attr.id, 1)
                        state.flash("${attr.id.title} ${attr.from + 1}")
                    }, control, enabled = attr.affordable)
                }
            }
        }
        EditorCard(Modifier.editorReadableWidth()) {
            SectionHeader("Взять навык", "Все навыки →") { state.step = EditorStep.PERKS }
            if (view.perks.isEmpty()) Muted("Сейчас нет навыков, которые можно взять на свободный опыт.")
            view.perks.forEach { card -> PerkRow(host, state, context, card, control, almost = false) }
        }
        if (view.almost.isNotEmpty()) {
            EditorCard(Modifier.editorReadableWidth()) {
                EditorOverline("Почти — не хватает одного шага")
                view.almost.forEach { card -> PerkRow(host, state, context, card, control, almost = true) }
            }
        }
    }
}


@Composable
private fun SectionHeader(title: String, link: String, onLink: () -> Unit) {
    val p = FuryColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        EditorOverline(title, Modifier.weight(1f))
        Text(
            link, Modifier.clickable(role = Role.Button, onClick = onLink).padding(horizontal = 4.dp, vertical = 8.dp),
            color = p.goldText, style = MaterialTheme.typography.labelLarge,
        )
    }
}



@Composable
private fun Muted(text: String) {
    Text(text, color = FuryColors.current.muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PerkRow(host: DublEditorHost, state: EditorState, context: EditorPerkContext, card: EditorPerkCard, control: Dp, almost: Boolean) {
    val p = FuryColors.current
    val detail = remember(context, card.entryId) { context.perkDetail(card.entryId, emptyMap(), null) } ?: return
    val prerequisite = detail.chain.firstOrNull { !it.isTarget }
    EditorRow {
        Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Подробнее о «${card.name}»") { state.openPerk(context, card.entryId) }) {
            Text(
                card.name + if (card.rank > 0 && card.status != EditorPerkStatus.OWNED) " → ${card.rank + 1}" else "",
                color = p.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (almost && prerequisite != null) "сначала ${prerequisite.label} (${prerequisite.costText})"
                else listOf(card.category, card.benefit.substringBefore('.').substringBefore('\n')).filter { it.isNotBlank() }.joinToString(" · "),
                color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!almost && card.status != EditorPerkStatus.OWNED) Text("${card.nextCost} XP", color = p.goldText, style = MaterialTheme.typography.labelLarge.merge(EditorTabular), maxLines = 1, softWrap = false)
        val action = detail.action
        val label = when {
            card.status == EditorPerkStatus.OWNED -> "Взят"
            almost -> "С цепочкой · ${detail.chainCost}"
            card.rank > 0 -> "Повысить"
            else -> "Взять"
        }
        EditorOutlineButton(label, {
            if (action != null) applyPerkAction(host, state, card.entryId, card.name, action, chain = detail.chain.isNotEmpty())
        }, control, enabled = action != null)
    }
}
