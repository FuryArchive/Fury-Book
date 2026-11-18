package com.furybook.dubl.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing

private const val DEFAULT_NAME = "Новый персонаж"

/** Итог: the whole character on one page, what needs fixing, and finishing (or reopening) creation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorSummaryStep(
    host: DublEditorHost,
    state: EditorState,
    data: EditorFrameData,
    layout: SheetLayout,
    onOpenMagic: () -> Unit,
    onOpenGear: () -> Unit,
) {
    val p = FuryColors.current
    val character = host.active
    val economy = data.economy
    val control = editorControl(layout)
    val columns = if (layout == SheetLayout.PHONE) 1 else 2
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(editorContentPadding(layout)),
        verticalArrangement = Arrangement.spacedBy(FurySpacing.md),
    ) {
        EditorStepTitle("Итог", "Весь персонаж на одной странице. Нажмите на раздел, чтобы поправить.")
        val ok = data.problems.isEmpty()
        EditorCard(Modifier.editorReadableWidth(), background = if (ok) p.dBg else p.goldSoft, border = if (ok) p.dLine else p.goldLine) {
            // The derived-stat tint is contrast-checked in every theme; the fixed helpful green is not on light ones.
            EditorOverline(if (ok) "Всё в порядке" else "Замечания · ${data.problems.size}", color = if (ok) p.dLabel else p.goldText)
            data.problems.forEach { problem ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable(role = Role.Button) { state.step = problem.step },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorWarnMark("Замечание")
                    Text(problem.text, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodySmall)
                    Text("${problem.step.title} →", color = p.goldText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        val name = character.name.trim().takeIf { it.isNotEmpty() && !(it == DEFAULT_NAME && !character.creationComplete) } ?: "Без имени"
        val skills = character.resolvedSkills().filter { it.rank > 0 }.sortedByDescending { it.rank }.joinToString(", ") { "${it.name} ${it.rank}" }
        val perks = character.development.entries
            .filter { it.key != MagicEquipmentRules.BASE_MANA_ENTRY_ID }
            .mapNotNull { (id, owned) -> host.developmentCatalog.byId(id)?.let { e -> if (e.maxRank > 1) "${e.name} ${owned.rank}" else e.name } }
            .sorted().joinToString(", ")
        val magicXp = economy.manaXp + economy.magicSchoolXp + economy.spellXp
        val xpLines = buildList {
            add("Характеристики" to economy.attributeXp)
            add("Умения" to economy.skillXp)
            add("Навыки" to economy.developmentXp)
            if (magicXp != 0) add("Магия" to magicXp)
            if (economy.chiXp != 0) add("ЦИ" to economy.chiXp)
            if (economy.adjustmentXp != 0) add("Поправка" to economy.adjustmentXp)
        }
        val cards: List<@Composable (Modifier) -> Unit> = listOf(
            { m ->
                EditorCard(m, onClick = { state.step = EditorStep.BASICS }, onClickLabel = "Открыть Основу") {
                    EditorOverline("Основа")
                    Text(name, color = p.text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${character.concept.ifBlank { "без концепции" }} · размер ${character.size} · ног ${character.legs} · мана ${if (character.manaEnabled) "да" else "нет"}",
                        color = p.muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            { m ->
                EditorCard(m) {
                    EditorOverline("Опыт")
                    xpLines.forEach { (label, value) -> XpLine(label, value.toString(), p.text) }
                    XpLine("Свободно из ${economy.totalExperience}", economy.remainingXp.toString(), if (economy.remainingXp < 0) p.accText else p.gold)
                }
            },
            { m ->
                EditorCard(m, onClick = { state.step = EditorStep.ATTRIBUTES }, onClickLabel = "Открыть Характеристики") {
                    EditorOverline("Характеристики")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        editorAttributeCards(character, data.before.character).forEach { card ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(p.tile).padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(card.short, color = p.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                Text(card.value.toString(), color = p.text, style = MaterialTheme.typography.titleSmall.merge(EditorTabular), maxLines = 1)
                            }
                        }
                    }
                    Text(data.derived.joinToString(" · ") { "${it.label} ${it.value}" }, color = p.muted, style = MaterialTheme.typography.labelSmall)
                }
            },
            { m ->
                EditorCard(m, onClick = { state.step = EditorStep.SKILLS }, onClickLabel = "Открыть Умения") {
                    EditorOverline("Умения · ${data.rankCount} рангов")
                    Text(skills.ifEmpty { "Пока нет рангов" }, color = p.text2, style = MaterialTheme.typography.bodySmall)
                }
            },
            { m ->
                EditorCard(m, onClick = { state.step = EditorStep.PERKS }, onClickLabel = "Открыть Навыки") {
                    EditorOverline("Навыки · ${data.perkCount}")
                    Text(perks.ifEmpty { "Пока ничего" }, color = p.text2, style = MaterialTheme.typography.bodySmall)
                }
            },
            { m ->
                EditorCard(m) {
                    EditorOverline("Ещё")
                    Text("Заклинания и снаряжение пока на своих вкладках.", color = p.muted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                        EditorOutlineButton("Магия →", onOpenMagic, control)
                        EditorOutlineButton("Снаряжение →", onOpenGear, control)
                    }
                }
            },
        )
        Column(Modifier.editorReadableWidth(), verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            cards.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
                    row.forEach { card -> card(Modifier.weight(1f)) }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        EditorCard(Modifier.editorReadableWidth()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.md), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (data.creating) {
                        "Когда всё готово, завершите создание: навыки «только при создании» закроются, а правки перестанут сбрасывать здоровье до максимума. Открыть создание снова можно в любой момент."
                    } else "Создание завершено. Сейчас идёт развитие: навыки «только при создании» закрыты.",
                    Modifier.widthIn(max = 560.dp), color = p.text2, style = MaterialTheme.typography.bodySmall,
                )
                if (data.creating) EditorPrimaryButton("Завершить создание", { host.completeCreation(); state.flash("Создание завершено") }, control)
                else EditorOutlineButton("Открыть создание снова", { host.reopenCreation(); state.flash("Создание открыто снова") }, control)
            }
        }
    }
}

@Composable
private fun XpLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    val p = FuryColors.current
    Row {
        Text(label, Modifier.weight(1f), color = p.text2, style = MaterialTheme.typography.bodySmall)
        Text(value, color = valueColor, style = MaterialTheme.typography.labelLarge.merge(EditorTabular))
    }
}
