package com.furybook.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furybook.android.data.DevelopmentCatalogRepository
import com.furybook.android.data.SkillEffectCatalogRepository
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.ResolvedSkill
import com.furybook.dubl.model.SkillCalculation
import com.furybook.dubl.model.SkillCatalog
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.SkillEffectRules
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.skillCalculationOptions
import com.furybook.dubl.model.skillXpSpent
import com.furybook.android.state.CharacterController
import com.furybook.android.ui.acquisition.*
import com.furybook.dubl.acquisition.AcquisitionKind
import androidx.compose.ui.platform.testTag
import com.furybook.android.ui.components.containSheetOverscroll
import com.furybook.ui.components.DublCard
import com.furybook.android.ui.components.DublScreenHeader
import com.furybook.ui.theme.DublAccent
import com.furybook.ui.theme.DublGold

@Composable
fun SkillsScreen(controller: CharacterController) {
    val character = controller.active
    val acquisition = LocalAndroidAcquisition.current
    val context = LocalContext.current
    val sheetExtras = controller.extras

    var query by remember(character.id) { mutableStateOf("") }
    var selectedCategory by remember(character.id) { mutableStateOf<SkillCategory?>(null) }
    var trainedOnly by remember(character.id) { mutableStateOf(false) }
    var selectedSkillId by remember(character.id) { mutableStateOf<String?>(null) }
    var selectedRollSkillId by remember(character.id) { mutableStateOf<String?>(null) }
    var selectedBreakdownSkillId by remember(character.id) { mutableStateOf<String?>(null) }
    var selectedRankSkillId by remember(character.id) { mutableStateOf<String?>(null) }
    var showAdd by remember(character.id) { mutableStateOf(false) }
    var showHidden by remember(character.id) { mutableStateOf(false) }

    fun rememberSkillAttribute(skillId: String, attribute: AttributeId) {
        controller.setPreferredSkillAttribute(skillId, attribute)
    }

    val visibleSkills = character.resolvedSkills()
    val filtered = visibleSkills.filter { skill ->
        val needle = query.trim()
        (selectedCategory == null || skill.category == selectedCategory) &&
            (!trainedOnly || skill.rank > 0) &&
            (needle.isBlank() || skill.name.contains(needle, ignoreCase = true) ||
                skill.description.contains(needle, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            DublScreenHeader(
                title = "Умения",
                subtitle = character.name,
                action = {
                    Button(
                        onClick = { if (acquisition?.canonical == true) acquisition.chooseDefinition(AcquisitionKind.SKILL) else showAdd = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("+ Добавить")
                    }
                },
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = DublGold.copy(alpha = 0.035f),
                border = BorderStroke(1.dp, DublGold.copy(alpha = 0.24f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val trained = character.resolvedSkills(includeHidden = true).count { it.rank > 0 }
                    Text(
                        "Изучено $trained",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (acquisition?.canonical == true) acquisition.view?.balance?.let(::acquisitionAmount) ?: "Баланс недоступен" else "${character.skillXpSpent()} XP вложено",
                        style = MaterialTheme.typography.labelLarge,
                        color = DublGold,
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск умения") },
                singleLine = true,
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("Все") },
                    )
                }
                items(SkillCategory.entries.filter { category ->
                    visibleSkills.any { it.category == category }
                }) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text(category.title) },
                    )
                }
                item {
                    FilterChip(
                        selected = trainedOnly,
                        onClick = { trainedOnly = !trainedOnly },
                        label = { Text("Изученные") },
                    )
                }
            }
        }

        if (character.hiddenSkillIds.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHidden = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Скрытые умения · ${character.hiddenSkillIds.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Они не удалены — их можно вернуть",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                DublCard(Modifier.fillMaxWidth()) {
                    Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Сбросьте поиск/фильтр или верните скрытые умения.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            query = ""
                            selectedCategory = null
                            trainedOnly = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Сбросить фильтры") }
                    if (character.hiddenSkillIds.isNotEmpty()) {
                        TextButton(onClick = { showHidden = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Открыть скрытые умения")
                        }
                    }
                }
            }
        } else {
            SkillCategory.entries.forEach { category ->
                val categorySkills = filtered.filter { it.category == category }
                if (categorySkills.isNotEmpty()) {
                    item(key = "header-${category.name}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 11.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                category.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                categorySkills.size.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            )
                        }
                    }
                    items(categorySkills, key = { it.id }) { skill ->
                        SkillRow(
                            character = character,
                            skill = skill,
                            onClick = { selectedSkillId = skill.id },
                            onRoll = { selectedRollSkillId = skill.id },
                            onBreakdown = { selectedBreakdownSkillId = skill.id },
                            onRank = { selectedRankSkillId = skill.id },
                        )
                    }
                }
            }
        }
    }

    selectedSkillId?.let { skillId ->
        character.resolveSkill(skillId)?.let { skill ->
            SkillDetailSheet(
                character = character,
                skill = skill,
                controller = controller,
                onRoll = {
                    selectedSkillId = null
                    selectedRollSkillId = skill.id
                },
                onDismiss = { selectedSkillId = null },
            )
        } ?: run { selectedSkillId = null }
    }

    selectedRollSkillId?.let { skillId ->
        character.resolveSkill(skillId)?.let { skill ->
            DublSkillRollSheet(
                character = character,
                skill = skill,
                preferredAttribute = sheetExtras.preferredSkillAttributes[skill.id],
                onPreferredAttribute = { attribute -> rememberSkillAttribute(skill.id, attribute) },
                onDismiss = { selectedRollSkillId = null },
            )
        } ?: run { selectedRollSkillId = null }
    }

    selectedBreakdownSkillId?.let { skillId ->
        character.resolveSkill(skillId)?.let { skill ->
            SkillBreakdownSheet(
                character = character,
                skill = skill,
                onDismiss = { selectedBreakdownSkillId = null },
            )
        } ?: run { selectedBreakdownSkillId = null }
    }

    selectedRankSkillId?.let { skillId ->
        character.resolveSkill(skillId)?.let { skill ->
            SkillRankSheet(
                skill = skill,
                controller = controller,
                onDismiss = { selectedRankSkillId = null },
            )
        } ?: run { selectedRankSkillId = null }
    }

    if (showAdd) {
        AddSkillDialog(
            controller = controller,
            onDismiss = { showAdd = false },
            onAdded = { id ->
                showAdd = false
                selectedSkillId = id
            },
        )
    }

    if (showHidden) {
        HiddenSkillsDialog(
            character = character,
            controller = controller,
            onDismiss = { showHidden = false },
        )
    }
}

@Composable
private fun SkillRow(
    character: DublCharacter,
    skill: ResolvedSkill,
    onClick: () -> Unit,
    onRoll: () -> Unit,
    onBreakdown: () -> Unit,
    onRank: () -> Unit,
) {
    val calculations = character.skillCalculationOptions(skill)
    val trained = skill.rank > 0
    val border = if (trained) DublGold.copy(alpha = 0.34f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.48f)
    val container = if (trained) DublGold.copy(alpha = 0.035f) else MaterialTheme.colorScheme.surface
    val primaryText = if (trained) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val acquisition = LocalAndroidAcquisition.current
    val nextCost = if (acquisition?.canonical == true) null else SkillCatalog.nextRankCost(skill.rank)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(if (trained) 1.2.dp else 1.dp, border),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            skill.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        skillOriginLabel(skill)?.let { origin ->
                            Spacer(Modifier.width(6.dp))
                            CompactBadge(origin, if (origin == "Своё") DublAccent else DublGold)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        skill.attributes.joinToString(" / ") { it.shortTitle },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (trained) DublGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (skillHasRule(skill) || skill.modifier != 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            if (skillHasRule(skill)) {
                                CompactBadge("◇ Правило", DublAccent)
                            }
                            if (skill.modifier != 0) {
                                CompactBadge("Поправка ${signedSkill(skill.modifier)}", DublGold)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
                MiniSkillAction(
                    text = "⚄",
                    selected = true,
                    accent = DublAccent,
                    onClick = onRoll,
                )
            }

            Spacer(Modifier.height(7.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DenseInfoChip(
                    text = if (trained) "Ранг ${skill.rank}" else "Ранг 0 · не изучено",
                    accent = if (trained) DublGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onRank,
                )
                DenseInfoChip(
                    text = skillBonusSummary(calculations),
                    accent = if (calculations.any { it.second.total != null }) DublAccent else MaterialTheme.colorScheme.error,
                    onClick = onBreakdown,
                )
            }
            AcquisitionEntrySummary(AcquisitionKind.SKILL, skill.id, skill.rank + 1)
            Text(
                text = if (acquisition?.canonical == true) "Точная цена в просмотре покупки" else nextCost?.let { "Следующий ранг ${skill.rank + 1} · $it XP" } ?: "Максимальный ранг",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MiniSkillAction(
    text: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = accent.copy(alpha = if (selected) 0.14f else 0.045f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (selected) 0.48f else 0.26f)),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DenseInfoChip(
    text: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = accent.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

@Composable
private fun CompactBadge(
    text: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
        )
    }
}

private fun skillOriginLabel(skill: ResolvedSkill): String? = when {
    !skill.isDynamic -> null
    skill.state.definitionId != null && skill.definition?.template == true -> "Специализация"
    else -> "Своё"
}

private fun skillHasRule(skill: ResolvedSkill): Boolean {
    if (skill.formulaNote.isNotBlank() || skill.modifier != 0) return true
    val definition = skill.definition ?: return skill.untrained != UntrainedRule.YES
    return skill.untrained != UntrainedRule.YES ||
        definition.auto6 != "Нет" ||
        definition.auto12 != "Нет"
}

private fun skillBonusSummary(options: List<Pair<AttributeId, SkillCalculation>>): String {
    if (options.isEmpty()) return "Бонус —"
    if (options.size == 1) return "Бонус ${options.first().second.total?.let(::signedSkill) ?: "—"}"
    val values = options.joinToString(" / ") { (_, calculation) ->
        calculation.total?.let(::signedSkill) ?: "—"
    }
    return "Бонус $values"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillBreakdownSheet(
    character: DublCharacter,
    skill: ResolvedSkill,
    onDismiss: () -> Unit,
) {
    val options = character.skillCalculationOptions(skill)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(skill.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Разбор бонуса",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (skill.attributes.size > 1) {
                Text(
                    "У умения несколько допустимых характеристик. При броске выбирается одна — они не складываются.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            options.forEach { (attribute, calculation) ->
                DublCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(attribute.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                calculationBreakdown(calculation),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            calculation.total?.let(::signedSkill) ?: "—",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (calculation.total != null) DublGold else MaterialTheme.colorScheme.error,
                        )
                    }
                    if (calculation.unavailableReason.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(calculation.unavailableReason, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (skill.formulaNote.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = DublAccent.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, DublAccent.copy(alpha = 0.3f)),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("◇ Особое правило / условный бонус", fontWeight = FontWeight.Bold, color = DublAccent)
                        Spacer(Modifier.height(3.dp))
                        Text(skill.formulaNote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillRankSheet(
    skill: ResolvedSkill,
    controller: CharacterController,
    onDismiss: () -> Unit,
) {
    val acquisition = LocalAndroidAcquisition.current
    val nextCost = if (acquisition?.canonical == true) null else SkillCatalog.nextRankCost(skill.rank)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(skill.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            AcquisitionEntrySummary(AcquisitionKind.SKILL, skill.id, skill.rank + 1)
            Text("Ранг", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(skill.rank.toString(), fontSize = 42.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, color = DublGold)
            Spacer(Modifier.height(10.dp))
            Text(
                if (acquisition?.canonical == true) "Фактические расходы в истории операций" else "Потрачено XP: ${SkillCatalog.costForRank(skill.rank)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (acquisition?.canonical == true) "Просмотр покупки не меняет лист" else nextCost?.let { "Следующий ранг ${skill.rank + 1}: +$it XP" } ?: "Достигнут максимальный ранг",
                style = MaterialTheme.typography.bodyMedium,
                color = if (nextCost != null) DublGold else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { if (acquisition?.canonical == true) acquisition.lowerHolding(AcquisitionKind.SKILL, skill.id, skill.rank - 1) else controller.changeSkillRank(skill.id, -1) },
                    enabled = skill.rank > 0,
                    modifier = Modifier.weight(1f),
                ) { Text("− 1 ранг") }
                Button(
                    onClick = { if (acquisition?.canonical == true) acquisition.openTarget(AcquisitionKind.SKILL, skill.id, skill.rank + 1) else controller.changeSkillRank(skill.id, 1) },
                    enabled = skill.rank < 10,
                    modifier = Modifier.weight(1f),
                ) { Text("+ 1 ранг") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillDetailSheet(
    character: DublCharacter,
    skill: ResolvedSkill,
    controller: CharacterController,
    onRoll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember(skill.id, skill.formulaNote) { mutableStateOf(skill.formulaNote) }
    var localName by remember(skill.id, skill.name) { mutableStateOf(skill.name) }
    var localDescription by remember(skill.id, skill.description) { mutableStateOf(skill.description) }
    var localCategory by remember(skill.id, skill.category) { mutableStateOf(skill.category) }
    var localUntrained by remember(skill.id, skill.untrained) { mutableStateOf(skill.untrained) }
    var localAuto6 by remember(skill.id, skill.auto6) { mutableStateOf(skill.auto6) }
    var localAuto12 by remember(skill.id, skill.auto12) { mutableStateOf(skill.auto12) }
    val calculations = character.skillCalculationOptions(skill)
    val acquisition = LocalAndroidAcquisition.current
    val nextCost = if (acquisition?.canonical == true) null else SkillCatalog.nextRankCost(skill.rank)
    val context = LocalContext.current
    val developmentCatalog = LocalAndroidAcquisitionContent.current.development
    val effectCatalog = LocalAndroidAcquisitionContent.current.skillEffects
    val configurableEffects = remember(character, skill.id, developmentCatalog, effectCatalog) {
        SkillEffectRules(character, developmentCatalog, effectCatalog).configuredForSkill(skill)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(skill.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    skillOriginLabel(skill)?.let { origin ->
                        Spacer(Modifier.height(4.dp))
                        CompactBadge(origin, if (origin == "Своё") DublAccent else DublGold)
                    }
                }
            }

            if (skill.description.isNotBlank()) {
                Text(skill.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = onRoll,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 11.dp),
            ) {
                Text("⚄  Бросить проверку")
            }

            DublCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Ранг", style = MaterialTheme.typography.labelLarge)
                        Text(skill.rank.toString(), style = MaterialTheme.typography.headlineMedium, color = if (skill.rank > 0) DublGold else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { if (acquisition?.canonical == true) acquisition.lowerHolding(AcquisitionKind.SKILL, skill.id, skill.rank - 1) else controller.changeSkillRank(skill.id, -1) },
                            enabled = skill.rank > 0,
                        ) { Text("−") }
                        Button(
                            onClick = { if (acquisition?.canonical == true) acquisition.openTarget(AcquisitionKind.SKILL, skill.id, skill.rank + 1) else controller.changeSkillRank(skill.id, 1) },
                            enabled = skill.rank < 10,
                        ) { Text("+") }
                    }
                }
                Spacer(Modifier.height(7.dp))
                AcquisitionEntrySummary(AcquisitionKind.SKILL, skill.id, skill.rank + 1)
                Text(
                    if (acquisition?.canonical == true) "Фактические расходы в истории операций" else "Потрачено XP: ${SkillCatalog.costForRank(skill.rank)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (acquisition?.canonical == true) "Просмотр покупки не меняет лист" else nextCost?.let { "Следующий ранг ${skill.rank + 1}: +$it XP" } ?: "Максимальный ранг",
                    color = if (nextCost != null) DublGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Допустимые характеристики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (skill.attributes.size > 1) {
                        "При броске выбирается одна из отмеченных характеристик. Они не складываются."
                    } else {
                        "Можно добавить альтернативную характеристику для ситуационных проверок."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(AttributeId.entries) { attribute ->
                    val selected = attribute in skill.attributes
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val updated = if (selected) {
                                if (skill.attributes.size == 1) skill.attributes else skill.attributes - attribute
                            } else {
                                skill.attributes + attribute
                            }
                            if (acquisition?.canonical == true) acquisition.manualSkill(skill.id, "Изменение допустимых характеристик") { it.copy(attributes = updated) } else controller.setSkillAttributes(skill.id, updated)
                        },
                        label = { Text(attribute.shortTitle) },
                    )
                }
            }

            Text("Бонус проверки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            calculations.forEach { (attribute, calculation) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = DublGold.copy(alpha = 0.045f),
                    border = BorderStroke(1.dp, DublGold.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(attribute.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                calculationBreakdown(calculation),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            calculation.total?.let(::signedSkill) ?: "—",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (calculation.total != null) DublGold else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Text("Постоянная поправка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { controller.setSkillModifier(skill.id, skill.modifier - 1) }) { Text("−") }
                Text(
                    signedSkill(skill.modifier),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(onClick = { controller.setSkillModifier(skill.id, skill.modifier + 1) }) { Text("+") }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Особое правило / условный бонус") },
                supportingText = { Text("Например: +2 с подходящими инструментами или особое условие применения") },
                minLines = 2,
            )
            Button(
                onClick = { controller.setSkillFormulaNote(skill.id, note) },
                enabled = note.trim() != skill.formulaNote,
            ) { Text("Сохранить правило") }

            if (configurableEffects.isNotEmpty()) {
                HorizontalDivider()
                Text("Автоматизация правил", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Если приложение неверно трактует эффект, его можно отключить только для этого персонажа.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                configurableEffects.forEach { effect ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = effect.id !in character.disabledSkillEffectIds,
                            onCheckedChange = { enabled -> controller.setSkillEffectEnabled(effect.id, enabled) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(effect.sourceName, fontWeight = FontWeight.SemiBold)
                            Text(effect.effectText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            HorizontalDivider()
            Text("Без обучения: ${skill.untrained.label}")
            Text("Автоуспех 6: ${skill.auto6.ifBlank { "—" }} • Автоуспех 12: ${skill.auto12.ifBlank { "—" }}")

            Text("Локальные правки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Эти поля перекрывают импорт из рулбука только для этого персонажа. Канон не меняется.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = localName,
                onValueChange = { localName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") },
                singleLine = true,
            )
            OutlinedTextField(
                value = localDescription,
                onValueChange = { localDescription = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Описание") },
                minLines = 2,
            )
            Text("Категория", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(SkillCategory.entries) { category ->
                    FilterChip(
                        selected = localCategory == category,
                        onClick = { localCategory = category },
                        label = { Text(category.title) },
                    )
                }
            }
            Text("Без обучения", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(UntrainedRule.entries) { rule ->
                    FilterChip(
                        selected = localUntrained == rule,
                        onClick = { localUntrained = rule },
                        label = { Text(rule.label) },
                    )
                }
            }
            OutlinedTextField(
                value = localAuto6,
                onValueChange = { localAuto6 = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Auto 6") },
                singleLine = true,
            )
            OutlinedTextField(
                value = localAuto12,
                onValueChange = { localAuto12 = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Auto 12") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (acquisition?.canonical == true) acquisition.manualSkill(skill.id, "Локальная правка умения") {
                            it.copy(name = localName, description = localDescription, categoryOverride = localCategory,
                                untrainedOverride = localUntrained, auto6Override = localAuto6, auto12Override = localAuto12)
                        } else {
                        controller.setSkillNameOverride(skill.id, localName)
                        controller.setSkillDescriptionOverride(skill.id, localDescription)
                        controller.setSkillCategoryOverride(skill.id, localCategory)
                        controller.setSkillUntrainedOverride(skill.id, localUntrained)
                        controller.setSkillAutoOverrides(skill.id, localAuto6, localAuto12)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Сохранить локально") }
                OutlinedButton(
                    onClick = {
                        if (acquisition?.canonical == true) acquisition.manualSkill(skill.id, "Сброс локальных правил") { it.copy(name = "", description = "", categoryOverride = null, untrainedOverride = null, auto6Override = null, auto12Override = null) } else controller.resetSkillDefinitionOverrides(skill.id)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Сбросить к рулбуку") }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            ) {
                Text(
                    "Скрытие убирает умение из основного списка, но не удаляет его данные.",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        controller.hideSkill(skill.id)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Скрыть из списка") }
                if (skill.isDynamic) {
                    TextButton(
                        onClick = {
                            if (acquisition?.canonical == true) acquisition.lowerHolding(AcquisitionKind.SKILL, skill.id, 0) else controller.deleteDynamicSkill(skill.id)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Удалить") }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun AddSkillDialog(
    controller: CharacterController,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit,
) {
    var customMode by remember { mutableStateOf(false) }
    var templateId by remember { mutableStateOf(SkillCatalog.templates.first().id) }
    var specialization by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var customDescription by remember { mutableStateOf("") }
    var customAttrs by remember { mutableStateOf(listOf(AttributeId.INTELLIGENCE)) }
    var customUntrained by remember { mutableStateOf(UntrainedRule.YES) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить умение") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !customMode,
                        onClick = { customMode = false; error = "" },
                        label = { Text("Специализация") },
                    )
                    FilterChip(
                        selected = customMode,
                        onClick = { customMode = true; error = "" },
                        label = { Text("Своё") },
                    )
                }

                if (!customMode) {
                    Text("Тип", style = MaterialTheme.typography.titleMedium)
                    SkillCatalog.templates.forEach { template ->
                        FilterChip(
                            selected = templateId == template.id,
                            onClick = { templateId = template.id },
                            label = { Text(template.name) },
                        )
                    }
                    OutlinedTextField(
                        value = specialization,
                        onValueChange = { specialization = it; error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Специализация") },
                        placeholder = { Text("Например: Биология") },
                        singleLine = true,
                    )
                } else {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it; error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = customDescription,
                        onValueChange = { customDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Описание") },
                        minLines = 2,
                    )
                    Text("Допустимые характеристики", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Если отмечено несколько, при броске выбирается одна характеристика.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AttributeId.entries) { attribute ->
                            val selected = attribute in customAttrs
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    customAttrs = if (selected) {
                                        if (customAttrs.size == 1) customAttrs else customAttrs - attribute
                                    } else customAttrs + attribute
                                },
                                label = { Text(attribute.shortTitle) },
                            )
                        }
                    }
                    Text("Без обучения", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(UntrainedRule.YES, UntrainedRule.YES_MINUS_2, UntrainedRule.NO).forEach { rule ->
                            FilterChip(
                                selected = customUntrained == rule,
                                onClick = { customUntrained = rule },
                                label = { Text(rule.label) },
                            )
                        }
                    }
                }

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val id = if (customMode) {
                        controller.addCustomSkill(customName, customDescription, customAttrs, customUntrained)
                    } else {
                        controller.addSpecializedSkill(templateId, specialization)
                    }
                    if (id == null) {
                        error = "Введите корректное уникальное название. Такое умение уже может существовать."
                    } else {
                        onAdded(id)
                    }
                },
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun HiddenSkillsDialog(
    character: DublCharacter,
    controller: CharacterController,
    onDismiss: () -> Unit,
) {
    val hidden = character.resolvedSkills(includeHidden = true).filter { it.id in character.hiddenSkillIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Скрытые умения") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Скрытие не удаляет ранги, настройки и описание умения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                hidden.forEach { skill ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Ранг ${skill.rank}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { controller.restoreSkill(skill.id) }) { Text("Вернуть") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    controller.restoreAllSkills()
                    onDismiss()
                },
            ) { Text("Вернуть все") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

private fun calculationBreakdown(calculation: SkillCalculation): String =
    calculation.contributions.joinToString(" + ") { contribution ->
        "${contribution.label} ${signedSkill(contribution.value)}"
    }

private fun signedSkill(value: Int): String = if (value >= 0) "+$value" else value.toString()
