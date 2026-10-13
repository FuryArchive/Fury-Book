package com.furybook.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.MagicSchoolCatalog
import com.furybook.dubl.model.SpellCatalogEntry
import com.furybook.ui.theme.DublFocus
import com.furybook.ui.theme.DublGold
import com.furybook.ui.theme.DublMuted
import com.furybook.desktop.DesktopAppState
import com.furybook.web.webUuid
import com.furybook.dubl.acquisition.*
import androidx.compose.ui.platform.testTag

private enum class MagicTab(val title: String) { SCHOOLS("Школы"), SPELLBOOK("Книга"), CATALOG("Каталог") }

@Composable
fun MagicScreen(state: DesktopAppState, modifier: Modifier = Modifier) {
    val actions=LocalWebAcquisition.current
    val character = state.activeCharacter
    var tab by remember(character.id) { mutableStateOf(MagicTab.SCHOOLS) }
    var search by remember(character.id) { mutableStateOf("") }
    var hideUnlearned by remember(character.id) { mutableStateOf(true) }
    var schoolFilter by remember(character.id) { mutableStateOf<String?>(null) }
    var schoolFilterMenu by remember(character.id) { mutableStateOf(false) }
    var editSchool by remember(character.id) { mutableStateOf<String?>(null) }
    var addSchool by remember(character.id) { mutableStateOf(false) }
    var schoolError by remember(character.id) { mutableStateOf<String?>(null) }
    var editSpell by remember(character.id) { mutableStateOf<KnownSpell?>(null) }
    var addCustomSpell by remember(character.id) { mutableStateOf(false) }
    var catalogDetails by remember(character.id) { mutableStateOf<SpellCatalogEntry?>(null) }
    var pendingDeleteSpellUid by remember(character.id) { mutableStateOf<String?>(null) }
    var pendingDeleteSchoolIndex by remember(character.id) { mutableStateOf<Int?>(null) }

    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard("Магия") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MagicTab.entries.forEach { item -> FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.title) }) }
                    if (tab != MagicTab.SCHOOLS) {
                        OutlinedTextField(search, { search = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { schoolFilterMenu = true }) { Text("Школа: ${schoolFilter ?: "Все"}") }
                        DropdownMenu(expanded = schoolFilterMenu, onDismissRequest = { schoolFilterMenu = false }) {
                            DropdownMenuItem(text = { Text("Все") }, onClick = { schoolFilter = null; schoolFilterMenu = false })
                            MagicSchoolCatalog.schools.forEach { school ->
                                DropdownMenuItem(text = { Text(school) }, onClick = { schoolFilter = school; schoolFilterMenu = false })
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Мана ${character.manaCurrent}/${character.effectiveManaMaximum}", color = DublFocus, fontWeight = FontWeight.Bold)
                    TextButton(
                        enabled = character.manaCurrent > 0,
                        onClick = { state.changeMana(-1) },
                    ) { Text("−") }
                    TextButton(
                        enabled = character.manaCurrent < character.effectiveManaMaximum,
                        onClick = { state.changeMana(1) },
                    ) { Text("+") }
                    Text("Ранг маны ${character.magic.manaRank}/5", color = DublGold)
                    RankStepper(character.magic.manaRank, max = if(state.canonicalMode) Int.MAX_VALUE else 5, enabled = state.canonicalMode || !character.creationComplete) { next ->
                        if(state.canonicalMode) { if(next>character.magic.manaRank) actions?.openTarget(AcquisitionKind.MANA,"mana:rank",next) else actions?.lowerHolding(AcquisitionKind.MANA,"mana:rank",next) }
                        else state.setMagicManaRank(next)
                    }
                    Text("Восстановление ${MagicEquipmentRules.manaRecoveryPerRound(character)}/раунд", color = DublMuted)
                }
                if(state.canonicalMode) {
                    Text(actions?.view?.balance?.let(::acquisitionAmount) ?: "Баланс недоступен")
                    AcquisitionEntrySummary(AcquisitionKind.MANA,"mana:rank",character.magic.manaRank+1)
                } else Text("XP: ранг маны ${MagicEquipmentRules.manaRankXp(character)} · школы ${MagicEquipmentRules.magicSchoolPowerXp(character)} · заклинания ${MagicEquipmentRules.learnedSpellXp(character)}", color = DublMuted)
            }
        }

        when (tab) {
            MagicTab.SCHOOLS -> {
                item {
                    SectionCard("Школы", action = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Checkbox(checked = hideUnlearned, onCheckedChange = { hideUnlearned = it })
                            Text("Только изученные")
                            Button(onClick = { if(state.canonicalMode) actions?.chooseDefinition(AcquisitionKind.SCHOOL) else addSchool = true }) { Text("+ Школа") }
                        }
                    }) {
                        EmptyState(if(state.canonicalMode) "Школа выбирается по точной записи и экземпляру. Неизвестная цена не означает бесплатное обучение." else "Сила школы определяет доступность заклинаний.")
                    }
                }
                if(state.canonicalMode) {
                    items(actions?.view?.instances?.filter { actions.view?.catalog?.definitions?.get(it.definition)?.kind == AcquisitionKind.SCHOOL }.orEmpty(),key={it.instanceId}) { school ->
                        SectionCard(school.label,Modifier.testTag("acquisition.instance.${school.instanceId}")) {
                            Text("Сила ${school.rank}")
                            RankStepper(school.rank,max=Int.MAX_VALUE) { next -> if(next>school.rank) actions?.open(AcquisitionRequest(state.snapshot.activeCharacterId,listOf(AcquisitionTarget(school.definition,school.instanceId,next,school.parameters,school.optionId)))) else actions?.lowerHolding(AcquisitionKind.SCHOOL,school.instanceId,next) }
                            AcquisitionEntrySummary(AcquisitionKind.SCHOOL,school.instanceId,school.rank+1)
                        }
                    }
                } else items(MagicEquipmentRules.visibleMagicSchools(character, hideUnlearned), key = { it }) { school ->
                    val power = MagicEquipmentRules.schoolPower(character, school)
                    SectionCard(school, action = { OutlinedButton(onClick = { editSchool = school }) { Text("Изменить") } }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Сила $power", color = DublFocus, fontWeight = FontWeight.Bold)
                            RankStepper(power, max = Int.MAX_VALUE) { next -> state.setMagicSchoolPower(school, next) }
                        }
                        character.magic.schools.firstOrNull { MagicSchoolCatalog.canonicalizeOrNull(it.name) == school }?.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = DublMuted) }
                    }
                }
            }
            MagicTab.SPELLBOOK -> {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = { addCustomSpell = true }) { Text("+ Своё заклинание") }
                    }
                }
                items(character.magic.spells.filter { spell ->
                    (search.isBlank() || spell.name.contains(search, true) || spell.school.contains(search, true)) &&
                        (schoolFilter == null || MagicSchoolCatalog.parseSchools(spell.school).contains(schoolFilter))
                }, key = { it.uid }) { spell ->
                    val usability = MagicEquipmentRules.spellUsability(character, spell)
                    SectionCard(spell.name, action = { OutlinedButton(onClick = { editSpell = spell }) { Text("Редактировать") } }) {
                        Text("${spell.school} · мана ${spell.cost} · ${if (spell.learned) "изучено" else "не изучено"}", color = if (usability.usable) DublFocus else DublMuted)
                        if (spell.description.isNotBlank()) Text(spell.description, color = DublMuted)
                        if (spell.learned && !usability.usable) Text("Требуется сила школы ${usability.requiredPower}", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        if(state.canonicalMode) AcquisitionEntrySummary(AcquisitionKind.SPELL,spell.catalogId ?: spell.uid,1) else Text("XP: ${spell.xpOverride ?: MagicEquipmentRules.learnXpCost(spell.cost)?.toString() ?: "—"}", color = DublGold)
                    }
                }
            }
            MagicTab.CATALOG -> {
                val catalog = state.magicEquipmentCatalog.spells.filter { spell ->
                    (search.isBlank() || spell.name.contains(search, true) || spell.school.contains(search, true)) &&
                        (schoolFilter == null || MagicSchoolCatalog.parseSchools(spell.school).contains(schoolFilter)) &&
                        character.magic.spells.none { it.catalogId == spell.id }
                }
                items(catalog, key = { it.id }) { spell ->
                    val usability = if(state.canonicalMode) null else MagicEquipmentRules.spellUsability(character, spell)
                    SectionCard(spell.name, action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { catalogDetails = spell }) { Text("Подробнее") }
                            if(!state.canonicalMode) Button(
                                onClick = { state.addCatalogSpell(spell) },
                                modifier = Modifier.testTag("acquisition.spell.${spell.id}"),
                            ) { Text(if (spell.incomplete) "Добавить и исправить" else "Добавить") }
                        }
                    }) {
                        Text("${spell.school} · мана ${spell.cost}", color = if (usability?.usable == true) DublFocus else DublMuted)
                        Text(spell.description, color = DublMuted)
                        if(state.canonicalMode) AcquisitionEntrySummary(AcquisitionKind.SPELL,spell.id,1)
                    }
                }
            }
        }
    }

    editSchool?.let { school -> SchoolDialog(state, school, onError = { schoolError = it }, onDelete = { index -> editSchool = null; pendingDeleteSchoolIndex = index }, onDismiss = { editSchool = null }) }
    if (addSchool) AddSchoolDialog(state, onError = { schoolError = it }, onDismiss = { addSchool = false })
    editSpell?.let { spell -> SpellDialog(state, spell, onDelete = { uid -> editSpell = null; pendingDeleteSpellUid = uid }, onDismiss = { editSpell = null }) }
    if (addCustomSpell) SpellDialog(state, null, onDelete = {}, onDismiss = { addCustomSpell = false })
    catalogDetails?.let { spell -> CatalogSpellDialog(state, spell, onDismiss = { catalogDetails = null }) }
    schoolError?.let { message ->
        FuryDialog(
            onDismissRequest = { schoolError = null },
            title = { Text("Не удалось сохранить школу") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { schoolError = null }) { Text("OK") } },
        )
    }

    pendingDeleteSpellUid?.let { uid ->
        val spellName = state.activeCharacter.magic.spells.firstOrNull { it.uid == uid }?.name ?: "заклинание"
        FuryDialog(
            onDismissRequest = { pendingDeleteSpellUid = null },
            title = { Text("Удалить заклинание?") },
            text = { Text("«$spellName» будет удалено из книги персонажа.") },
            confirmButton = {
                TextButton(onClick = { if(state.canonicalMode) actions?.editManual("Удаление заклинания") { base -> base.copy(magic=base.magic.copy(spells=base.magic.spells.filterNot { it.uid==uid })) } else state.removeSpell(uid); pendingDeleteSpellUid = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteSpellUid = null }) { Text("Отмена") } },
        )
    }
    pendingDeleteSchoolIndex?.let { index ->
        val schoolName = state.activeCharacter.magic.schools.getOrNull(index)?.name ?: "школа"
        FuryDialog(
            onDismissRequest = { pendingDeleteSchoolIndex = null },
            title = { Text("Удалить школу?") },
            text = { Text("«$schoolName» будет удалена у персонажа. Заклинания из книги не удаляются автоматически.") },
            confirmButton = {
                TextButton(onClick = { state.removeMagicSchool(index); pendingDeleteSchoolIndex = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteSchoolIndex = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AddSchoolDialog(state: DesktopAppState, onError: (String) -> Unit, onDismiss: () -> Unit) {
    val owned = state.activeCharacter.magic.schools.mapNotNull { MagicSchoolCatalog.canonicalizeOrNull(it.name) }.toSet()
    val available = MagicSchoolCatalog.schools.filterNot { it in owned }
    var school by remember(state.activeCharacter.id) { mutableStateOf(available.firstOrNull().orEmpty()) }
    var rank by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить школу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (available.isEmpty()) {
                    Text("Все канонические школы уже добавлены.", color = DublMuted)
                } else {
                    OutlinedButton(onClick = { menu = true }) { Text(school.ifBlank { "Выбрать школу" }) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        available.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { school = option; menu = false }) }
                    }
                    OutlinedTextField(rank, { rank = it.filter(Char::isDigit) }, label = { Text("Сила школы") }, singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("Примечание") })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = school.isNotBlank(), onClick = {
                val ok = state.addMagicSchool(school, rank.toIntOrNull()?.coerceAtLeast(0) ?: 0, note)
                if (ok) onDismiss() else onError("Проверьте название школы: школа уже существует или не поддерживается.")
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun SchoolDialog(state: DesktopAppState, school: String, onError: (String) -> Unit, onDelete: (Int) -> Unit, onDismiss: () -> Unit) {
    val existingIndex = state.activeCharacter.magic.schools.indexOfFirst { MagicSchoolCatalog.canonicalizeOrNull(it.name) == school }
    val existing = state.activeCharacter.magic.schools.getOrNull(existingIndex)
    var rank by remember(school) { mutableStateOf((existing?.rank ?: 0).toString()) }
    var note by remember(school) { mutableStateOf(existing?.note.orEmpty()) }
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(school) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rank, { rank = it.filter(Char::isDigit) }, label = { Text("Сила школы") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Примечание") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = rank.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val saved = if (existingIndex >= 0) {
                    state.updateMagicSchool(existingIndex, school, value, note)
                } else {
                    state.addMagicSchool(school, value, note)
                }
                if (saved) onDismiss() else onError("Проверьте название школы: допустима одна каноническая школа, без дубликатов.")
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (existingIndex >= 0) TextButton(onClick = { onDelete(existingIndex) }) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun SpellDialog(state: DesktopAppState, spell: KnownSpell?, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    val actions=LocalWebAcquisition.current
    var name by remember(spell?.uid) { mutableStateOf(spell?.name.orEmpty()) }
    var school by remember(spell?.uid) { mutableStateOf(spell?.school.orEmpty()) }
    var cost by remember(spell?.uid) { mutableStateOf((spell?.cost ?: 0).toString()) }
    var time by remember(spell?.uid) { mutableStateOf(spell?.time.orEmpty()) }
    var range by remember(spell?.uid) { mutableStateOf(spell?.range.orEmpty()) }
    var area by remember(spell?.uid) { mutableStateOf(spell?.area.orEmpty()) }
    var action by remember(spell?.uid) { mutableStateOf(spell?.action.orEmpty()) }
    var duration by remember(spell?.uid) { mutableStateOf(spell?.duration.orEmpty()) }
    var description by remember(spell?.uid) { mutableStateOf(spell?.description.orEmpty()) }
    var enhancement by remember(spell?.uid) { mutableStateOf(spell?.enhancement.orEmpty()) }
    var learned by remember(spell?.uid) { mutableStateOf(spell?.learned ?: !state.canonicalMode) }
    var xp by remember(spell?.uid) { mutableStateOf(spell?.xpOverride?.toString().orEmpty()) }

    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (spell == null) "Своё заклинание" else spell.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") })
                OutlinedTextField(school, { school = it }, label = { Text("Школа") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(cost, { cost = it.filter(Char::isDigit) }, label = { Text("Мана") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(xp, { xp = it.filter(Char::isDigit) }, label = { Text("XP override") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(time, { time = it }, label = { Text("Время") })
                OutlinedTextField(range, { range = it }, label = { Text("Дальность") })
                OutlinedTextField(area, { area = it }, label = { Text("Область") })
                OutlinedTextField(action, { action = it }, label = { Text("Проверка / действие") })
                OutlinedTextField(duration, { duration = it }, label = { Text("Длительность") })
                OutlinedTextField(description, { description = it }, label = { Text("Описание") })
                OutlinedTextField(enhancement, { enhancement = it }, label = { Text("Усиление") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(learned, { next -> if(state.canonicalMode && next) actions?.openTarget(AcquisitionKind.SPELL,spell?.catalogId ?: spell?.uid.orEmpty(),1) else learned=next })
                    Text("Изучено")
                }
                if(state.canonicalMode) Text("Изучение требует отдельного подтверждённого расчёта; изменение полей — явной ручной коррекции.")
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val manaCost = cost.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val updated = (spell ?: KnownSpell(uid = "", custom = true)).copy(
                    name = name.trim(), school = school.trim(), cost = manaCost, manaText = manaCost.toString(),
                    time = time.trim(), range = range.trim(), area = area.trim(), action = action.trim(), duration = duration.trim(),
                    description = description.trim(), enhancement = enhancement.trim(), learned = learned, xpOverride = xp.toIntOrNull()?.coerceAtLeast(0),
                )
                if(state.canonicalMode) actions?.editManual("Изменение заклинания") { base ->
                    val exact=if(spell==null) updated.copy(uid=webUuid(),learned=false) else updated
                    base.copy(magic=base.magic.copy(spells=base.magic.spells.filterNot { it.uid==exact.uid }+exact))
                } else if (spell == null) state.addCustomSpell(updated) else state.updateSpell(updated)
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (spell != null) TextButton(onClick = { onDelete(spell.uid) }) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun CatalogSpellDialog(state: DesktopAppState, spell: SpellCatalogEntry, onDismiss: () -> Unit) {
    val usability = if(state.canonicalMode) null else MagicEquipmentRules.spellUsability(state.activeCharacter, spell)
    FuryDialog(
        onDismissRequest = onDismiss,
        title = { Text(spell.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyValue("Школа", spell.school)
                KeyValue("Мана", spell.cost.toString())
                KeyValue("Время", spell.time)
                KeyValue("Дальность", spell.range)
                KeyValue("Область", spell.area)
                KeyValue("Действие", spell.action)
                KeyValue("Длительность", spell.duration)
                Text(spell.description)
                if (spell.enhancement.isNotBlank()) Text("Усиление: ${spell.enhancement}", color = DublMuted)
                if (spell.conflictNote.isNotBlank()) Text(spell.conflictNote, color = DublMuted)
                if(state.canonicalMode) AcquisitionEntrySummary(AcquisitionKind.SPELL,spell.id,1,onOpen=onDismiss)
                else usability?.let { Text(if (it.usable) "Доступно по силе школы" else "Нужно ${it.requiredPower} силы школы", color = if (it.usable) DublFocus else DublMuted) }
            }
        },
        confirmButton = {
            if(!state.canonicalMode) TextButton(
                onClick = { state.addCatalogSpell(spell); onDismiss() },
            ) { Text(if (spell.incomplete) "Добавить и исправить" else "Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
