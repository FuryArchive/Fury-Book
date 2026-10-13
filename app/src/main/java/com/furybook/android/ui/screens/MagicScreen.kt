package com.furybook.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.furybook.android.data.MagicEquipmentCatalogRepository
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.MagicSchool
import com.furybook.dubl.model.MagicSchoolCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.android.state.CharacterController
import com.furybook.android.ui.acquisition.*
import com.furybook.dubl.acquisition.AcquisitionKind
import androidx.compose.ui.platform.testTag
import com.furybook.android.ui.components.containSheetOverscroll
import com.furybook.ui.components.DublCard
import com.furybook.android.ui.components.DublScreenHeader
import com.furybook.android.ui.components.DublSwitch
import com.furybook.ui.theme.DublAccentSoft
import com.furybook.ui.theme.DublFocus
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMana
import com.furybook.ui.theme.DublMuted
import com.furybook.ui.theme.DublSurfaceRaised
import java.util.UUID

@Composable
fun MagicScreen(controller: CharacterController) {
    val character = controller.active
    val acquisition = LocalAndroidAcquisition.current
    val context = LocalContext.current.applicationContext
    val largeText = LocalDensity.current.fontScale >= 1.2f
    val catalog = LocalAndroidAcquisitionContent.current.magicEquipment
    var spellQuery by remember(character.id) { mutableStateOf("") }
    var schoolFilter by remember(character.id) { mutableStateOf<String?>(null) }
    var hideUnlearnedSchools by remember(character.id) { mutableStateOf(true) }
    var selectedSpellUid by remember(character.id) { mutableStateOf<String?>(null) }
    var showCatalog by remember(character.id) { mutableStateOf(false) }
    var editSpell by remember(character.id) { mutableStateOf<KnownSpell?>(null) }
    var createSpell by remember(character.id) { mutableStateOf(false) }
    var editSchoolIndex by remember(character.id) { mutableStateOf<Int?>(null) }
    var createSchool by remember(character.id) { mutableStateOf(false) }
    var pendingDeleteSpellUid by remember(character.id) { mutableStateOf<String?>(null) }
    var pendingDeleteSchoolIndex by remember(character.id) { mutableStateOf<Int?>(null) }
    var schoolError by remember(character.id) { mutableStateOf<String?>(null) }

    val maxMana = character.effectiveManaMaximum
    val recovery = MagicEquipmentRules.manaRecoveryPerRound(character)
    val filteredSpells = remember(character.magic.spells, spellQuery, schoolFilter) {
        val needle = spellQuery.trim().lowercase()
        character.magic.spells.filter { spell ->
            val matchesQuery = needle.isBlank() || listOf(spell.name, spell.school, spell.description, spell.action)
                .joinToString(" ").lowercase().contains(needle)
            val matchesSchool = schoolFilter == null || schoolFilter in MagicSchoolCatalog.parseSchools(spell.school)
            matchesQuery && matchesSchool
        }.sortedWith(compareBy<KnownSpell> { MagicSchoolCatalog.parseSchools(it.school).minOfOrNull(MagicSchoolCatalog::sortIndex) ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DublScreenHeader(
                title = "Магия",
                subtitle = "Сила, школы и книга заклинаний",
            )
        }

        if (acquisition?.canonical == true) item { AcquisitionBalanceCard() }
        item {
            MagicCoreCard(
                manaRank = character.magic.manaRank,
                creationComplete = character.creationComplete,
                currentMana = character.manaCurrent,
                maxMana = maxMana,
                recovery = recovery,
                spellXp = MagicEquipmentRules.learnedSpellXp(character),
                manaXp = MagicEquipmentRules.manaRankXp(character),
                schoolXp = MagicEquipmentRules.magicSchoolPowerXp(character),
                onManaRank = { rank ->
                    if (acquisition?.canonical == true) {
                        if (rank > character.magic.manaRank) acquisition.openTarget(AcquisitionKind.MANA, "mana:rank", rank)
                        else acquisition.lowerHolding(AcquisitionKind.MANA, "mana:rank", rank)
                    } else controller.setMagicManaRank(rank)
                },
                onManaDelta = controller::changeMana,
            )
        }

        item {
            DublCard(Modifier.fillMaxWidth()) {
                Text("Школы магии", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(if (acquisition?.canonical == true) "Цена школы определяется подтверждённым источником в просмотре" else "Сила каждой школы: 25 XP за уровень", style = MaterialTheme.typography.bodySmall, color = DublMuted)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Скрыть неизученные школы", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Показывать только школы с Силой магии 1+", style = MaterialTheme.typography.labelSmall, color = DublMuted)
                    }
                    DublSwitch(checked = hideUnlearnedSchools, onCheckedChange = { hideUnlearnedSchools = it })
                }
                Spacer(Modifier.height(6.dp))
                val visibleSchools = MagicEquipmentRules.visibleMagicSchools(character, hideUnlearnedSchools)
                if (acquisition?.canonical == true) {
                    val view = acquisition.view
                    view?.instances?.filter { view.catalog.definitions[it.definition]?.kind == AcquisitionKind.SCHOOL }?.forEach { instance ->
                        SchoolPowerRow(instance.label, instance.rank,
                            onMinus = { acquisition.lowerHolding(AcquisitionKind.SCHOOL, instance.instanceId, instance.rank - 1) },
                            onPlus = { acquisition.openTarget(AcquisitionKind.SCHOOL, instance.instanceId, instance.rank + 1) })
                        AcquisitionEntrySummary(AcquisitionKind.SCHOOL, instance.instanceId, instance.rank + 1)
                    }
                    TextButton(onClick = { acquisition.chooseDefinition(AcquisitionKind.SCHOOL) }) { Text("Выбрать школу каталога") }
                    if (character.magic.schools.isNotEmpty()) Text("Старые записи по названиям показаны в листе. Они не подменяют точную личность школы.", style = MaterialTheme.typography.bodySmall)
                } else if (visibleSchools.isEmpty()) {
                    Text(
                        "Изученных школ пока нет. Отключите фильтр, чтобы выбрать школу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DublMuted,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                } else {
                    visibleSchools.forEachIndexed { index, schoolName ->
                        val power = MagicEquipmentRules.schoolPower(character, schoolName)
                        SchoolPowerRow(
                            name = schoolName,
                            power = power,
                            onMinus = { if (acquisition?.canonical == true) acquisition.showError("Для снижения выберите точный сохранённый экземпляр школы; имя не подтверждает личность") else controller.setMagicSchoolPower(schoolName, (power - 1).coerceAtLeast(0)) },
                            onPlus = { if (acquisition?.canonical == true) acquisition.chooseDefinition(AcquisitionKind.SCHOOL, power + 1) else controller.setMagicSchoolPower(schoolName, power + 1) },
                        )
                        if (index != visibleSchools.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        }
                    }
                }
            }
        }

        item {
            if (largeText) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text("Книга заклинаний", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${character.magic.spells.size} заклинаний", style = MaterialTheme.typography.bodySmall, color = DublMuted)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { createSpell = true }, modifier = Modifier.weight(1f)) { Text("Своё") }
                        Button(onClick = { showCatalog = true }, modifier = Modifier.weight(1f)) { Text("Из книги") }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text("Книга заклинаний", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${character.magic.spells.size} заклинаний", style = MaterialTheme.typography.bodySmall, color = DublMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { createSpell = true }) { Text("Своё") }
                        Button(onClick = { showCatalog = true }) { Text("Из книги") }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = spellQuery,
                onValueChange = { spellQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поиск по книге заклинаний") },
                singleLine = true,
            )
        }
        item {
            SchoolFilterStrip(selected = schoolFilter, onSelect = { schoolFilter = it })
        }

        if (filteredSpells.isEmpty()) {
            item {
                DublCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (character.magic.spells.isEmpty()) "Книга заклинаний пуста" else "Ничего не найдено",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (character.magic.spells.isEmpty()) "Добавьте заклинание из каталога или создайте своё." else "Измените поисковый запрос.",
                        color = DublMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (character.magic.spells.isEmpty()) {
                        Button(onClick = { showCatalog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Открыть каталог")
                        }
                        OutlinedButton(onClick = { createSpell = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Создать своё заклинание")
                        }
                    } else {
                        OutlinedButton(onClick = { spellQuery = "" }, modifier = Modifier.fillMaxWidth()) {
                            Text("Сбросить поиск")
                        }
                    }
                }
            }
        } else {
            items(filteredSpells, key = { it.uid }) { spell ->
                SpellRow(character = character, spell = spell, onClick = { selectedSpellUid = spell.uid })
            }
        }
    }

    if (showCatalog) {
        SpellCatalogSheet(
            character = character,
            entries = catalog.spells,
            ownedIds = character.magic.spells.mapNotNull { it.catalogId }.toSet(),
            onAdd = { if (acquisition?.canonical == true) { acquisition.openTarget(AcquisitionKind.SPELL, it.id, 1); false } else controller.addCatalogSpell(it) },
            onDismiss = { showCatalog = false },
        )
    }

    selectedSpellUid?.let { uid ->
        character.magic.spells.firstOrNull { it.uid == uid }?.let { spell ->
            SpellDetailSheet(
                character = character,
                spell = spell,
                onEdit = { editSpell = spell; selectedSpellUid = null },
                onRemove = { selectedSpellUid = null; pendingDeleteSpellUid = uid },
                onToggleLearned = { learned ->
                    if (acquisition?.canonical == true) {
                        if (learned && spell.catalogId != null) acquisition.openTarget(AcquisitionKind.SPELL, checkNotNull(spell.catalogId), 1)
                        else acquisition.editManual("Изменение изученности заклинания") { base -> base.copy(magic = base.magic.copy(spells = base.magic.spells.map { if (it.uid == uid) it.copy(learned = learned) else it })) }
                    } else controller.setSpellLearned(uid, learned)
                },
                onDismiss = { selectedSpellUid = null },
            )
        } ?: run { selectedSpellUid = null }
    }

    if (createSpell) {
        SpellEditDialog(
            initial = KnownSpell(uid = UUID.randomUUID().toString(), custom = true),
            title = "Своё заклинание",
            onSave = { if (acquisition?.canonical == true) acquisition.editManual("Своё заклинание, не правило каталога") { base -> base.copy(magic = base.magic.copy(spells = base.magic.spells + it)) } else controller.addCustomSpell(it); createSpell = false },
            onDismiss = { createSpell = false },
        )
    }

    editSpell?.let { spell ->
        SpellEditDialog(
            initial = spell,
            title = spell.name,
            onSave = { updated -> if (acquisition?.canonical == true) acquisition.editManual("Изменение заклинания") { base -> base.copy(magic = base.magic.copy(spells = base.magic.spells.map { if (it.uid == updated.uid) updated else it })) } else controller.updateSpell(updated); editSpell = null },
            onDismiss = { editSpell = null },
        )
    }

    if (createSchool) {
        SchoolEditDialog(
            initial = MagicSchool(),
            title = "Новая школа",
            errorMessage = schoolError,
            onSave = { school ->
                if (acquisition?.canonical == true) { acquisition.chooseDefinition(AcquisitionKind.SCHOOL, school.rank); createSchool = false }
                else if (controller.addMagicSchool(school.name, school.rank, school.note)) {
                    createSchool = false
                    schoolError = null
                } else {
                    schoolError = "Школа с таким названием уже существует."
                }
            },
            onDismiss = { createSchool = false; schoolError = null },
        )
    }

    editSchoolIndex?.let { index ->
        character.magic.schools.getOrNull(index)?.let { school ->
            SchoolEditDialog(
                initial = school,
                title = school.name,
                showDelete = true,
                errorMessage = schoolError,
                onSave = { updated ->
                    if (acquisition?.canonical == true) {
                        acquisition.editManual("Изменение школы") { base -> base.copy(magic = base.magic.copy(schools = base.magic.schools.mapIndexed { i, old -> if (i == index) updated else old })) }
                        editSchoolIndex = null
                    } else if (controller.updateMagicSchool(index, updated.name, updated.rank, updated.note)) {
                        editSchoolIndex = null
                        schoolError = null
                    } else {
                        schoolError = "Школа с таким названием уже существует."
                    }
                },
                onDelete = { editSchoolIndex = null; schoolError = null; pendingDeleteSchoolIndex = index },
                onDismiss = { editSchoolIndex = null; schoolError = null },
            )
        } ?: run { editSchoolIndex = null }
    }

    pendingDeleteSpellUid?.let { uid ->
        val spell = character.magic.spells.firstOrNull { it.uid == uid }
        if (spell != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteSpellUid = null },
                title = { Text("Удалить заклинание?") },
                text = { Text("«${spell.name}» будет удалено из книги заклинаний персонажа.") },
                confirmButton = {
                    Button(onClick = {
                        if (acquisition?.canonical == true) acquisition.editManual("Удаление заклинания") { base -> base.copy(magic = base.magic.copy(spells = base.magic.spells.filterNot { it.uid == uid })) } else controller.removeSpell(uid)
                        pendingDeleteSpellUid = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteSpellUid = null }) { Text("Отмена") }
                },
            )
        } else {
            pendingDeleteSpellUid = null
        }
    }

    pendingDeleteSchoolIndex?.let { index ->
        val school = character.magic.schools.getOrNull(index)
        if (school != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteSchoolIndex = null },
                title = { Text("Удалить школу?") },
                text = { Text("Школа «${school.name}» будет удалена. Заклинания из книги останутся.") },
                confirmButton = {
                    Button(onClick = {
                        if (acquisition?.canonical == true) acquisition.editManual("Удаление школы") { base -> base.copy(magic = base.magic.copy(schools = base.magic.schools.filterIndexed { i, _ -> i != index })) } else controller.removeMagicSchool(index)
                        pendingDeleteSchoolIndex = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteSchoolIndex = null }) { Text("Отмена") }
                },
            )
        } else {
            pendingDeleteSchoolIndex = null
        }
    }
}

@Composable
private fun MagicCoreCard(
    manaRank: Int,
    creationComplete: Boolean,
    currentMana: Int,
    maxMana: Int,
    recovery: Int,
    spellXp: Int,
    manaXp: Int,
    schoolXp: Int,
    onManaRank: (Int) -> Unit,
    onManaDelta: (Int) -> Unit,
) {
    val acquisition = LocalAndroidAcquisition.current
    DublCard(Modifier.fillMaxWidth()) {
        Text("Магический потенциал", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        MagicCounter(
            title = "Ранг запаса",
            value = manaRank,
            modifier = Modifier.fillMaxWidth(),
            onMinus = { onManaRank((manaRank - 1).coerceAtLeast(0)) },
            onPlus = { onManaRank((manaRank + 1).coerceAtMost(5)) },
            plusEnabled = acquisition?.canonical == true || (!creationComplete && manaRank < 5),
            minusEnabled = manaRank > 0 && (acquisition?.canonical == true || !creationComplete),
        )
        Spacer(Modifier.height(8.dp))
        if (acquisition?.canonical == true) {
            AcquisitionEntrySummary(AcquisitionKind.MANA, "mana:rank", manaRank + 1)
        } else if (creationComplete) {
            Text(
                "Базовый запас маны можно повышать только при создании персонажа.",
                style = MaterialTheme.typography.bodySmall,
                color = DublMuted,
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                "Базовый запас маны: 100 XP за ранг · только при создании.",
                style = MaterialTheme.typography.bodySmall,
                color = DublMuted,
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DublMana.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, DublMana.copy(alpha = 0.35f)),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Мана", style = MaterialTheme.typography.labelLarge, color = DublMuted)
                        Text("$currentMana / $maxMana", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = DublMana)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onManaDelta(-1) }, enabled = currentMana > 0) { Text("−") }
                        TextButton(onClick = { onManaDelta(1) }, enabled = currentMana < maxMana) { Text("+") }
                    }
                }
                Text("Восстановление: +$recovery / раунд", style = MaterialTheme.typography.bodySmall, color = DublMuted)
                Text(if (acquisition?.canonical == true) "Фактическая оплата сохранена в истории операций" else "Опыт: запас $manaXp · школы $schoolXp · заклинания $spellXp", style = MaterialTheme.typography.bodySmall, color = DublMuted)
            }
        }
    }
}

@Composable
private fun MagicCounter(
    title: String,
    value: Int,
    modifier: Modifier = Modifier,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = DublSurfaceRaised,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = DublMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onMinus, enabled = minusEnabled) { Text("−") }
                Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onPlus, enabled = plusEnabled) { Text("+") }
            }
        }
    }
}

@Composable
private fun SchoolPowerRow(
    name: String,
    power: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val canonical = LocalAndroidAcquisition.current?.canonical == true
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = if (power > 0) FontWeight.SemiBold else FontWeight.Normal)
            Text(
                if (canonical) "Сила $power · цена в просмотре" else if (power > 0) "Сила $power · ${MagicEquipmentRules.magicSchoolRankXp(power)} XP" else "Не изучена",
                style = MaterialTheme.typography.bodySmall,
                color = DublMuted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus, enabled = power > 0) { Text("−") }
            Text(power.toString(), fontWeight = FontWeight.Bold, color = if (power > 0) DublFocus else DublMuted)
            TextButton(onClick = onPlus) { Text("+") }
        }
    }
}

@Composable
private fun SchoolFilterStrip(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SchoolFilterChip("Все", selected == null) { onSelect(null) }
        MagicSchoolCatalog.schools.forEach { school ->
            SchoolFilterChip(school, selected == school) { onSelect(school) }
        }
    }
}

@Composable
private fun SchoolFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) DublAccentSoft else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (selected) DublFocus.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SpellRow(character: DublCharacter, spell: KnownSpell, onClick: () -> Unit) {
    val usability = MagicEquipmentRules.spellUsability(character, spell)
    DublCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        spell.name,
                        modifier = Modifier.weight(1f, fill = false),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (spell.custom) {
                        Spacer(Modifier.width(6.dp))
                        Text("СВОЁ", style = MaterialTheme.typography.labelSmall, color = DublGold)
                    }
                }
                Text(
                    listOfNotNull(
                        spell.school.takeIf { it.isNotBlank() },
                        spell.time.takeIf { it.isNotBlank() },
                        if (spell.learned) null else "не изучено",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = DublMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (spell.learned && !usability.usable) {
                    Text(
                        "Нельзя использовать · требуется Сила магии ${usability.requiredPower}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Surface(shape = RoundedCornerShape(999.dp), color = DublMana.copy(alpha = 0.12f)) {
                Text("${spell.cost} маны", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = DublMana)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpellCatalogSheet(
    character: DublCharacter,
    entries: List<SpellCatalogEntry>,
    ownedIds: Set<String>,
    onAdd: (SpellCatalogEntry) -> Boolean,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var schoolFilter by remember { mutableStateOf<String?>(null) }
    val filtered = remember(query, schoolFilter, entries) {
        val needle = query.trim().lowercase()
        entries.filter { entry ->
            val matchesQuery = needle.isBlank() || listOf(entry.name, entry.school, entry.description, entry.action)
                .joinToString(" ").lowercase().contains(needle)
            val matchesSchool = schoolFilter == null || schoolFilter in MagicSchoolCatalog.parseSchools(entry.school)
            matchesQuery && matchesSchool
        }.sortedWith(compareBy<SpellCatalogEntry> { MagicSchoolCatalog.parseSchools(it.school).minOfOrNull(MagicSchoolCatalog::sortIndex) ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .containSheetOverscroll()
                .padding(horizontal = 16.dp),
        ) {
            Text("Каталог заклинаний", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${entries.size} заклинаний в каталоге", color = DublMuted)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Поиск") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            SchoolFilterStrip(selected = schoolFilter, onSelect = { schoolFilter = it })
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Ничего не найдено", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Попробуйте другой запрос.",
                                style = MaterialTheme.typography.bodySmall,
                                color = DublMuted,
                            )
                            if (query.isNotBlank()) {
                                TextButton(onClick = { query = "" }) { Text("Сбросить поиск") }
                            }
                        }
                    }
                }
                items(filtered, key = { it.id }) { entry ->
                    val owned = entry.id in ownedIds
                    val usability = MagicEquipmentRules.spellUsability(character, entry)
                    Surface(
                        onClick = { if (!owned) onAdd(entry) },
                        enabled = !owned,
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${entry.school.ifBlank { "Без школы" }} · ${entry.cost} маны",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DublMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                AcquisitionEntrySummary(AcquisitionKind.SPELL, entry.id, 1)
                                if (!usability.usable) {
                                    Text(
                                        "После изучения пока нельзя использовать · нужна Сила магии ${usability.requiredPower}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                if (owned) "Изучено" else if (entry.incomplete) "Добавить и исправить" else "Добавить",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (owned) DublMuted else DublMana,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpellDetailSheet(
    character: DublCharacter,
    spell: KnownSpell,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onToggleLearned: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .containSheetOverscroll()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(spell.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${spell.school.ifBlank { "Без школы" }} · ${spell.cost} маны", color = DublMana)
            val usability = MagicEquipmentRules.spellUsability(character, spell)
            if (spell.learned && !usability.usable) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)) {
                    Text(
                        "Нельзя использовать: нужна Сила магии ${usability.requiredPower} хотя бы в одной школе заклинания.",
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            DetailFact("Время", spell.time)
            DetailFact("Дальность", spell.range)
            DetailFact("Область", spell.area)
            DetailFact("Проверка", spell.action)
            DetailFact("Длительность", spell.duration)
            if (spell.description.isNotBlank()) {
                HorizontalDivider()
                Text("Описание", fontWeight = FontWeight.Bold)
                Text(spell.description)
            }
            if (spell.enhancement.isNotBlank()) {
                Text("Усиление", fontWeight = FontWeight.Bold, color = DublGold)
                Text(spell.enhancement)
            }
            if (spell.conflictNote.isNotBlank()) {
                Text(spell.conflictNote, style = MaterialTheme.typography.bodySmall, color = DublMuted)
            }
            if (LocalAndroidAcquisition.current?.canonical == true) {
                spell.catalogId?.let { AcquisitionEntrySummary(AcquisitionKind.SPELL, it, 1) }
                if (spell.catalogId == null) Text("Локальная запись: изученность изменяется только ручной коррекцией")
            } else {
                val xp = spell.xpOverride ?: MagicEquipmentRules.learnXpCost(spell.cost)
                Text("Цена изучения: ${xp?.let { "$it опыта" } ?: "не определена"}", style = MaterialTheme.typography.bodySmall, color = DublMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Изучено", modifier = Modifier.weight(1f))
                DublSwitch(checked = spell.learned, onCheckedChange = onToggleLearned)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Удалить") }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Изменить") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String) {
    if (value.isBlank()) return
    if (LocalDensity.current.fontScale >= 1.2f) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = DublMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.width(92.dp), style = MaterialTheme.typography.labelMedium, color = DublMuted)
            Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SpellEditDialog(
    initial: KnownSpell,
    title: String,
    onSave: (KnownSpell) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial.uid) { mutableStateOf(initial.name.takeUnless { it == "Заклинание" } ?: "") }
    var school by remember(initial.uid) { mutableStateOf(initial.school) }
    var costText by remember(initial.uid) { mutableStateOf(initial.cost.toString()) }
    var time by remember(initial.uid) { mutableStateOf(initial.time) }
    var range by remember(initial.uid) { mutableStateOf(initial.range) }
    var area by remember(initial.uid) { mutableStateOf(initial.area) }
    var action by remember(initial.uid) { mutableStateOf(initial.action) }
    var duration by remember(initial.uid) { mutableStateOf(initial.duration) }
    var description by remember(initial.uid) { mutableStateOf(initial.description) }
    var enhancement by remember(initial.uid) { mutableStateOf(initial.enhancement) }
    var learned by remember(initial.uid) { mutableStateOf(initial.learned) }
    var manualXp by remember(initial.uid) { mutableStateOf(initial.xpOverride != null) }
    var xpText by remember(initial.uid) { mutableStateOf((initial.xpOverride ?: MagicEquipmentRules.learnXpCost(initial.cost) ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(school, { school = it }, label = { Text("Школа") }, singleLine = true)
                OutlinedTextField(costText, { costText = it.filter(Char::isDigit) }, label = { Text("Стоимость маны") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(time, { time = it }, label = { Text("Время") }, singleLine = true)
                OutlinedTextField(range, { range = it }, label = { Text("Дальность") }, singleLine = true)
                OutlinedTextField(area, { area = it }, label = { Text("Область") }, singleLine = true)
                OutlinedTextField(action, { action = it }, label = { Text("Проверка") })
                OutlinedTextField(duration, { duration = it }, label = { Text("Длительность") })
                OutlinedTextField(description, { description = it }, label = { Text("Описание") }, minLines = 3)
                OutlinedTextField(enhancement, { enhancement = it }, label = { Text("Усиление") }, minLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Изучено", modifier = Modifier.weight(1f))
                    DublSwitch(learned, { learned = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Цена опыта вручную", modifier = Modifier.weight(1f))
                    DublSwitch(manualXp, { manualXp = it })
                }
                if (manualXp) {
                    OutlinedTextField(xpText, { xpText = it.filter(Char::isDigit) }, label = { Text("Опыт за изучение") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = name.trim()
                    if (clean.isBlank()) return@Button
                    val cost = costText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    onSave(
                        initial.copy(
                            name = clean,
                            school = school.trim(),
                            cost = cost,
                            manaText = cost.toString(),
                            time = time.trim(),
                            range = range.trim(),
                            area = area.trim(),
                            action = action.trim(),
                            duration = duration.trim(),
                            description = description.trim(),
                            enhancement = enhancement.trim(),
                            learned = learned,
                            xpOverride = if (manualXp) xpText.toIntOrNull()?.coerceAtLeast(0) ?: 0 else null,
                        )
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SchoolEditDialog(
    initial: MagicSchool,
    title: String,
    showDelete: Boolean = false,
    errorMessage: String? = null,
    onSave: (MagicSchool) -> Unit,
    onDelete: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    var name by remember(initial.name) { mutableStateOf(initial.name.takeUnless { it == "Школа" } ?: "") }
    var rankText by remember(initial.name, initial.rank) { mutableStateOf(initial.rank.toString()) }
    var note by remember(initial.name) { mutableStateOf(initial.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(rankText, { rankText = it.filter(Char::isDigit) }, label = { Text("Уровень") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Заметки") }, minLines = 2)
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                onSave(MagicSchool(name.trim(), rankText.toIntOrNull()?.coerceAtLeast(0) ?: 0, note.trim()))
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (showDelete) TextButton(onClick = onDelete) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
