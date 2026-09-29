package com.furybook.desktop.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.SkillCatalog
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.skillCalculation
import com.furybook.dubl.model.skillNextRankCost
import com.furybook.dubl.model.skillXpSpent
import com.furybook.ui.theme.DublMuted
import com.furybook.desktop.DesktopAppState

@Composable
fun SkillsScreen(state: DesktopAppState, modifier: Modifier = Modifier) {
    val character = state.activeCharacter
    var search by remember(character.id) { mutableStateOf("") }
    var category by remember(character.id) { mutableStateOf<SkillCategory?>(null) }
    var learnedOnly by remember(character.id) { mutableStateOf(false) }
    var selectedSkillId by remember(character.id) { mutableStateOf<String?>(null) }
    var rollSkill by remember(character.id) { mutableStateOf<ResolvedSkill?>(null) }
    var showHidden by remember(character.id) { mutableStateOf(false) }
    var showCustom by remember(character.id) { mutableStateOf(false) }
    var showSpecialized by remember(character.id) { mutableStateOf(false) }
    var customError by remember(character.id) { mutableStateOf<String?>(null) }

    val skills = character.resolvedSkills().filter { skill ->
        (search.isBlank() || skill.name.contains(search, ignoreCase = true) || skill.description.contains(search, ignoreCase = true)) &&
            (category == null || skill.category == category) &&
            (!learnedOnly || skill.rank > 0)
    }
    val selectedSkill = selectedSkillId?.let { id ->
        character.resolvedSkills(includeHidden = true).firstOrNull { it.id == id }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesktopSkillsToolbar(
            search = search,
            onSearchChange = { search = it },
            spentXp = character.skillXpSpent(),
            category = category,
            onCategoryChange = { category = it },
            learnedOnly = learnedOnly,
            onLearnedOnlyChange = { learnedOnly = it },
            hiddenCount = character.hiddenSkillIds.size,
            onSpecialized = { showSpecialized = true },
            onCustom = { showCustom = true },
            onHidden = { showHidden = true },
        )

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val columns = if (maxWidth >= 980.dp) 2 else 1
                    if (skills.isEmpty()) {
                        DesktopPanel(Modifier.fillMaxWidth()) {
                            EmptyState("По этим фильтрам умений нет.")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            skills.groupBy { it.category }.forEach { (skillCategory, categorySkills) ->
                                item(
                                    key = "skill-category-${skillCategory.name}",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    SkillCategoryHeader(skillCategory.title, categorySkills.size)
                                }
                                items(categorySkills, key = { it.id }) { skill ->
                                    val preferred = state.extras.preferredSkillAttributes[skill.id]
                                        ?.takeIf { it in skill.attributes }
                                        ?: skill.attributes.first()
                                    val calc = character.skillCalculation(skill, preferred)
                                    DesktopSkillCard(
                                        title = skill.name,
                                        bonus = calc.total?.let(::signed) ?: "—",
                                        attribute = preferred.title,
                                        rank = skill.rank,
                                        nextRankCost = character.skillNextRankCost(skill.rank),
                                        description = skill.description,
                                        selected = selectedSkillId == skill.id,
                                        canRoll = calc.total != null,
                                        onSelect = { selectedSkillId = if (selectedSkillId == skill.id) null else skill.id },
                                        onRoll = { rollSkill = skill },
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedSkill != null) {
                    SkillInspector(
                        state = state,
                        skillId = selectedSkill.id,
                        onClose = { selectedSkillId = null },
                        onRoll = { rollSkill = selectedSkill },
                        modifier = Modifier.widthIn(min = 340.dp, max = 430.dp).fillMaxHeight(),
                    )
                }
            }
        }
    }

    rollSkill?.let { skill ->
        SkillRollDialog(
            character = state.activeCharacter,
            skill = skill,
            preferredAttribute = state.extras.preferredSkillAttributes[skill.id],
            developmentCatalog = state.developmentCatalog,
            effectCatalog = state.skillEffectCatalog,
            onPreferredAttribute = { attr -> state.setPreferredSkillAttribute(skill.id, attr) },
            onDismiss = { rollSkill = null },
        )
    }
    if (showHidden) HiddenSkillsDialog(state, onDismiss = { showHidden = false })
    if (showCustom) CustomSkillDialog(state, onError = { customError = it }, onDismiss = { showCustom = false })
    if (showSpecialized) SpecializedSkillDialog(state, onError = { customError = it }, onDismiss = { showSpecialized = false })

    customError?.let { message ->
        FuryDialog(
            onDismissRequest = { customError = null },
            title = { Text("Не удалось добавить умение") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { customError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun DesktopSkillsToolbar(
    search: String,
    onSearchChange: (String) -> Unit,
    spentXp: Int,
    category: SkillCategory?,
    onCategoryChange: (SkillCategory?) -> Unit,
    learnedOnly: Boolean,
    onLearnedOnlyChange: (Boolean) -> Unit,
    hiddenCount: Int,
    onSpecialized: () -> Unit,
    onCustom: () -> Unit,
    onHidden: () -> Unit,
) {
    DesktopPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Умения",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DesktopText,
                )
                Text("Потрачено XP: $spentXp", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Поиск умений…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                DesktopSmallAction("Специализация", onSpecialized, emphasized = true)
                DesktopSmallAction("Своё", onCustom)
                DesktopInlineAction(if (hiddenCount > 0) "Скрытые · $hiddenCount" else "Скрытые", onHidden)
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wideFilters = maxWidth >= 1180.dp
                if (wideFilters) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkillCategoryFilter("Все", category == null) { onCategoryChange(null) }
                            SkillCategory.entries.forEach { option ->
                                SkillCategoryFilter(option.title, category == option) {
                                    onCategoryChange(if (category == option) null else option)
                                }
                            }
                        }
                        LearnedOnlyToggle(learnedOnly, onLearnedOnlyChange)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SkillCategoryFilter("Все", category == null) { onCategoryChange(null) }
                            SkillCategory.entries.take(3).forEach { option ->
                                SkillCategoryFilter(option.title, category == option) {
                                    onCategoryChange(if (category == option) null else option)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SkillCategory.entries.drop(3).forEach { option ->
                                SkillCategoryFilter(option.title, category == option) {
                                    onCategoryChange(if (category == option) null else option)
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            LearnedOnlyToggle(learnedOnly, onLearnedOnlyChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillCategoryFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun LearnedOnlyToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text("Только изученные", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SkillCategoryHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = DesktopText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text("· $count", color = DesktopAccent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DesktopSkillCard(
    title: String,
    bonus: String,
    attribute: String,
    rank: Int,
    nextRankCost: Int?,
    description: String,
    selected: Boolean,
    canRoll: Boolean,
    onSelect: () -> Unit,
    onRoll: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) DesktopSurfaceRaised else DesktopSurface,
        border = BorderStroke(
            1.dp,
            if (selected) DesktopAccent.copy(alpha = .72f) else DesktopBorder.copy(alpha = .88f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = DesktopText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        bonus,
                        color = DesktopAccent,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                DesktopSmallAction(
                    label = "Бросок",
                    onClick = onRoll,
                    enabled = canRoll,
                    emphasized = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(attribute, color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
                Text("·", color = DesktopBorder)
                Text("ранг $rank", color = if (rank > 0) DesktopText else DesktopMuted, style = MaterialTheme.typography.bodySmall)
                nextRankCost?.let {
                    Text("·", color = DesktopBorder)
                    Text("$it XP", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                description.ifBlank { "Без описания" },
                color = DesktopMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SkillInspector(
    state: DesktopAppState,
    skillId: String,
    onClose: () -> Unit,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skill = state.activeCharacter.resolvedSkills(includeHidden = true).firstOrNull { it.id == skillId } ?: return
    val preferred = state.extras.preferredSkillAttributes[skill.id]?.takeIf { it in skill.attributes } ?: skill.attributes.first()
    val calculation = state.activeCharacter.skillCalculation(skill, preferred)
    var modifierText by remember(skill.id, skill.modifier) { mutableStateOf(skill.modifier.toString()) }
    var note by remember(skill.id, skill.formulaNote) { mutableStateOf(skill.formulaNote) }
    var attributes by remember(skill.id, skill.attributes) { mutableStateOf(skill.attributes.toSet()) }
    var localName by remember(skill.id, skill.name) { mutableStateOf(skill.name) }
    var localDescription by remember(skill.id, skill.description) { mutableStateOf(skill.description) }
    var localCategory by remember(skill.id, skill.category) { mutableStateOf(skill.category) }
    var localUntrained by remember(skill.id, skill.untrained) { mutableStateOf(skill.untrained) }
    var localAuto6 by remember(skill.id, skill.auto6) { mutableStateOf(skill.auto6) }
    var localAuto12 by remember(skill.id, skill.auto12) { mutableStateOf(skill.auto12) }
    val configurableEffects = remember(state.activeCharacter, skill.id, state.developmentCatalog, state.skillEffectCatalog) {
        SkillEffectRules(state.activeCharacter, state.developmentCatalog, state.skillEffectCatalog).configuredForSkill(skill)
    }
    val scroll = rememberScrollState()

    DesktopPanel(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(skill.name, color = DesktopText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(calculation.total?.let(::signed) ?: "—", color = DesktopAccent, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
                        Text(preferred.title, color = DesktopMuted)
                    }
                }
                DesktopInlineAction("Закрыть", onClose)
            }

            if (skill.description.isNotBlank()) {
                Text(skill.description, color = DesktopMuted, style = MaterialTheme.typography.bodyMedium)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                color = DesktopSurfaceInset.copy(alpha = .62f),
                border = BorderStroke(1.dp, DesktopBorder.copy(alpha = .62f)),
            ) {
                Text(
                    calculation.formulaText(skill),
                    modifier = Modifier.padding(10.dp),
                    color = DesktopMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopSmallAction("Бросок", onRoll, modifier = Modifier.weight(1f), enabled = calculation.total != null, emphasized = true)
            }

            HorizontalDivider(color = DesktopBorder.copy(alpha = .72f))
            Text("Развитие", color = DesktopText, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ранг", color = DesktopMuted)
                RankStepper(skill.rank, max = 10) { next -> state.changeSkillRank(skill.id, next - skill.rank) }
                state.activeCharacter.skillNextRankCost(skill.rank)?.let { cost ->
                    Text("Следующий: $cost XP", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("Характеристики", color = DesktopText, fontWeight = FontWeight.Bold)
            AttributeId.entries.forEach { attr ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = attr in attributes,
                        onCheckedChange = { checked ->
                            val next = if (checked) attributes + attr else attributes - attr
                            if (next.isNotEmpty()) attributes = next
                        },
                    )
                    Text(attr.title, color = DesktopText)
                }
            }

            OutlinedTextField(modifierText, { modifierText = it.take(4) }, label = { Text("Поправка") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Примечание к формуле") }, modifier = Modifier.fillMaxWidth())

            HorizontalDivider(color = DesktopBorder.copy(alpha = .72f))
            Text("Локальные правки", color = DesktopText, fontWeight = FontWeight.Bold)
            Text("Каноническое определение рулбука не меняется.", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(localName, { localName = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(localDescription, { localDescription = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth())
            SkillCategory.entries.chunked(2).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { option ->
                        FilterChip(
                            selected = localCategory == option,
                            onClick = { localCategory = option },
                            label = { Text(option.title) },
                        )
                    }
                }
            }
            UntrainedRule.entries.chunked(2).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { option ->
                        FilterChip(
                            selected = localUntrained == option,
                            onClick = { localUntrained = option },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
            OutlinedTextField(localAuto6, { localAuto6 = it }, label = { Text("Auto 6") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(localAuto12, { localAuto12 = it }, label = { Text("Auto 12") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            if (configurableEffects.isNotEmpty()) {
                Text("Автоматизация правил", color = DesktopText, fontWeight = FontWeight.Bold)
                Text("Можно отключить ошибочную трактовку только для этого персонажа.", color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
                configurableEffects.forEach { effect ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = effect.id !in state.activeCharacter.disabledSkillEffectIds,
                            onCheckedChange = { enabled -> state.setSkillEffectEnabled(effect.id, enabled) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(effect.sourceName, color = DesktopText)
                            Text(effect.effectText, color = DesktopMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            DesktopSmallAction(
                label = "Сохранить настройки",
                onClick = {
                    state.setSkillAttributes(skill.id, attributes.toList())
                    state.setSkillModifier(skill.id, modifierText.toIntOrNull() ?: 0)
                    state.setSkillFormulaNote(skill.id, note)
                    state.setSkillNameOverride(skill.id, localName)
                    state.setSkillDescriptionOverride(skill.id, localDescription)
                    state.setSkillCategoryOverride(skill.id, localCategory)
                    state.setSkillUntrainedOverride(skill.id, localUntrained)
                    state.setSkillAutoOverrides(skill.id, localAuto6, localAuto12)
                },
                modifier = Modifier.fillMaxWidth(),
                emphasized = true,
            )

            HorizontalDivider(color = DesktopBorder.copy(alpha = .72f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton(onClick = { state.hideSkill(skill.id); onClose() }) { Text("Скрыть") }
                if (skill.isBuiltIn) {
                    TextButton(onClick = { state.resetSkillDefinitionOverrides(skill.id); onClose() }) { Text("К рулбуку") }
                }
                if (skill.isDynamic) {
                    TextButton(onClick = { state.deleteDynamicSkill(skill.id); onClose() }) { Text("Удалить") }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun HiddenSkillsDialog(state: DesktopAppState, onDismiss: () -> Unit) {
    val hidden = state.activeCharacter.resolvedSkills(includeHidden = true).filter { it.id in state.activeCharacter.hiddenSkillIds }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Скрытые умения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (hidden.isEmpty()) EmptyState("Скрытых умений нет.")
                hidden.forEach { skill ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(skill.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { state.restoreSkill(skill.id) }) { Text("Вернуть") }
                    }
                }
            }
        },
        confirmButton = { if (hidden.isNotEmpty()) TextButton(onClick = { state.restoreAllSkills(); onDismiss() }) { Text("Вернуть все") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun SpecializedSkillDialog(state: DesktopAppState, onError: (String) -> Unit, onDismiss: () -> Unit) {
    var specialization by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(SkillCatalog.templates.first()) }
    var expanded by remember { mutableStateOf(false) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить специализацию") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = true }) { Text(template.name) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    SkillCatalog.templates.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { template = option; expanded = false }) }
                }
                OutlinedTextField(specialization, { specialization = it }, label = { Text("Специализация") })
            }
        },
        confirmButton = { TextButton(enabled = specialization.isNotBlank(), onClick = {
            val added = state.addSpecializedSkill(template.id, specialization)
            if (added != null) onDismiss() else onError("Введите корректное уникальное название. Такое умение уже может существовать.")
        }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun CustomSkillDialog(state: DesktopAppState, onError: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attributes by remember { mutableStateOf(setOf(AttributeId.INTELLIGENCE)) }
    var untrained by remember { mutableStateOf(UntrainedRule.YES) }
    var untrainedMenu by remember { mutableStateOf(false) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Своё умение") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") })
                OutlinedTextField(description, { description = it }, label = { Text("Описание") })
                AttributeId.entries.forEach { attr ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = attr in attributes, onCheckedChange = { checked ->
                            val next = if (checked) attributes + attr else attributes - attr
                            if (next.isNotEmpty()) attributes = next
                        })
                        Text(attr.title)
                    }
                }
                OutlinedButton(onClick = { untrainedMenu = true }) { Text("Без обучения: ${untrained.label}") }
                DropdownMenu(expanded = untrainedMenu, onDismissRequest = { untrainedMenu = false }) {
                    UntrainedRule.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { untrained = option; untrainedMenu = false }) }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            val added = state.addCustomSkill(name, description, attributes.toList(), untrained)
            if (added != null) onDismiss() else onError("Введите корректное уникальное название. Такое умение уже может существовать.")
        }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
