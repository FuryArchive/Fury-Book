package com.furybook.dubl.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.furybook.dubl.model.KnownSpell
import com.furybook.dubl.model.MagicEquipmentRules
import com.furybook.dubl.model.MagicSchoolCatalog
import com.furybook.dubl.ui.sheet.SheetLayout
import com.furybook.dubl.ui.sheet.SheetSearchField
import com.furybook.ui.theme.FuryColors
import com.furybook.ui.theme.FurySpacing
import com.furybook.ui.theme.furyOverlineStyle

private sealed interface MagicForm {
    data class Spell(val base: KnownSpell?) : MagicForm
    data class School(val index: Int?) : MagicForm
}

/** Магия: mana rank, schools, the spellbook and the rulebook catalog (the old Магия tab, now in the editor). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorMagicStep(host: DublEditorHost, state: EditorState, data: EditorFrameData, layout: SheetLayout) {
    val p = FuryColors.current
    val control = editorControl(layout)
    val compact = layout != SheetLayout.WIDE_WITH_TRAY
    val character = host.active
    val header = remember(data) { editorMagicHeader(host) }
    val schools = remember(data) { editorSchoolRows(character) }
    val groups = remember(data, state.spellFilter, state.spellSchool, state.spellQuery) {
        editorSpellGroups(character, state.spellFilter, state.spellSchool, state.spellQuery)
    }
    val spellSchools = remember(data) {
        character.magic.spells.flatMap { MagicSchoolCatalog.parseSchools(it.school) }.distinct().sortedBy(MagicSchoolCatalog::sortIndex)
    }
    var form by remember { mutableStateOf<MagicForm?>(null) }

    val headerContent: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(FurySpacing.md)) {
            EditorStepTitle("Магия", "Запас маны, школы и книга заклинаний. Изученные заклинания стоят опыта и появляются на листе.")
            if (!character.manaEnabled) {
                EditorCard(Modifier.fillMaxWidth(), background = p.goldSoft, border = p.goldLine) {
                    EditorOverline("Магия выключена", color = p.goldText)
                    Text("Включите ману, чтобы на листе появились запас маны и заклинания. Школы и книгу можно вести и так.", color = p.text2, style = MaterialTheme.typography.bodySmall)
                    EditorChip("Включить ману", false, { host.setIdentity(character.name, character.concept, character.size, character.legs, true) }, control, role = Role.Switch)
                }
            }
            EditorCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    Column(Modifier.weight(1f)) {
                        EditorOverline("Ранг маны")
                        Text("${header.manaRank} из $EDITOR_MANA_RANK_MAX · ${header.rankXp} XP", color = p.text, style = MaterialTheme.typography.titleMedium)
                    }
                    EditorSquareButton("−", "Понизить ранг маны", !header.rankLocked && header.manaRank > 0, control) { host.setMagicManaRank(header.manaRank - 1) }
                    EditorSquareButton("+", "Повысить ранг маны", !header.rankLocked && header.manaRank < EDITOR_MANA_RANK_MAX, control) { host.setMagicManaRank(header.manaRank + 1) }
                }
                if (header.rankLocked) {
                    Text("Ранг маны меняется только при создании; дальше — навык «Увеличенный запас маны»", color = p.muted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Открыть навык →",
                        Modifier.clickable(role = Role.Button) {
                            state.step = EditorStep.PERKS
                            state.openPerk(EditorPerkContext(host), MagicEquipmentRules.INCREASED_MANA_ENTRY_ID)
                        }.padding(vertical = 8.dp),
                        color = p.goldText, style = MaterialTheme.typography.labelLarge,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MagicStat("мана", header.manaMaximum.toString())
                    MagicStat("восстановление", "${header.recovery}/раунд")
                    MagicStat("XP", "ранг ${header.rankXp} · школы ${header.schoolXp} · заклинания ${header.spellXp}")
                }
                header.zeroReason?.let { Text(it, color = p.goldText, style = MaterialTheme.typography.bodySmall) }
            }
            EditorCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Школы", Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.titleMedium)
                    EditorOutlineButton("+ Школа", { form = MagicForm.School(null) }, control, enabled = editorAddableSchools(character).isNotEmpty())
                }
                if (schools.isEmpty()) Text("Школ пока нет", color = p.dim, style = MaterialTheme.typography.bodySmall)
                schools.forEach { school ->
                    EditorRow(Modifier.clickable(role = Role.Button, onClickLabel = "Изменить школу ${school.name}") { form = MagicForm.School(school.index) }, minHeight = control + 6.dp) {
                        Column(Modifier.weight(1f)) {
                            Text(school.name, color = p.text, style = MaterialTheme.typography.labelLarge)
                            Text("сила ${school.power} · ${school.xp} XP", color = p.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        EditorSquareButton("−", "Снизить силу школы ${school.name}", school.power > 0, control) { host.setMagicSchoolPower(school.name, school.power - 1) }
                        Text(school.power.toString(), Modifier.widthIn(min = 22.dp), color = p.text, style = MaterialTheme.typography.titleMedium)
                        EditorSquareButton("+", "Повысить силу школы ${school.name}", true, control) { host.setMagicSchoolPower(school.name, school.power + 1) }
                    }
                }
            }
            EditorCard(Modifier.fillMaxWidth()) {
                FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
                    Text("Книга заклинаний", Modifier.padding(top = 8.dp), color = p.text, style = MaterialTheme.typography.titleMedium)
                    EditorOutlineButton("+ Из каталога", { state.magicPane = EditorPane.SpellCatalog }, control)
                    EditorOutlineButton("+ Своё заклинание", { form = MagicForm.Spell(null) }, control)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(EditorSpellFilter.ALL to "Все", EditorSpellFilter.LEARNED to "Изученные", EditorSpellFilter.UNLEARNED to "Неизученные").forEach { (filter, label) ->
                        EditorChip(label, state.spellFilter == filter, { state.spellFilter = filter }, control, gold = true)
                    }
                }
                if (spellSchools.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditorChip("Все школы", state.spellSchool == null, { state.spellSchool = null }, control)
                        spellSchools.forEach { school -> EditorChip(school, state.spellSchool == school, { state.spellSchool = school }, control) }
                    }
                }
                SheetSearchField(state.spellQuery, { state.spellQuery = it }, "Поиск по книге…", Modifier.fillMaxWidth(), compact)
                if (groups.isEmpty()) Text(if (character.magic.spells.isEmpty()) "Заклинаний нет" else "Ничего не найдено", color = p.dim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    val list: LazyListScope.() -> Unit = {
        groups.forEach { group ->
            item(key = "school:${group.school}") {
                Text("${group.school.uppercase()} · СИЛА ${group.power}", Modifier.padding(start = 4.dp), color = p.dim, style = furyOverlineStyle())
            }
            items(group.rows, key = { "spell:${it.uid}" }) { row -> SpellBookRow(host, state, row, control) }
        }
    }
    val pane: (@Composable () -> Unit)? = when (val open = state.magicPane) {
        is EditorPane.Spell -> character.magic.spells.firstOrNull { it.uid == open.uid }?.let { spell ->
            { OwnedSpellPane(host, state, spell, control) { form = MagicForm.Spell(spell) } }
        }
        is EditorPane.CatalogSpell -> host.magicEquipmentCatalog.spells.firstOrNull { it.id == open.id }?.let { entry ->
            { CatalogSpellPane(host, state, entry, control) { uid -> form = MagicForm.Spell(host.active.magic.spells.firstOrNull { it.uid == uid }) } }
        }
        EditorPane.SpellCatalog -> { { SpellCatalogPane(host, state, control, compact) } }
        else -> null
    }
    EditorListPane(layout, headerContent, list, pane, "Выберите заклинание слева — здесь будут полный текст и правка.", onBack = { state.magicPane = null })
    when (val open = form) {
        null -> Unit
        is MagicForm.Spell -> EditorSpellFormDialog(host, state, open.base, layout) { form = null }
        is MagicForm.School -> EditorSchoolDialog(host, state, open.index, layout) { form = null }
    }
}

@Composable
private fun MagicStat(label: String, value: String) {
    val p = FuryColors.current
    Row(Modifier.clip(RoundedCornerShape(7.dp)).background(p.tile).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = p.muted, style = MaterialTheme.typography.labelMedium)
        Text(value, color = p.text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SpellBookRow(host: DublEditorHost, state: EditorState, row: EditorSpellRow, control: androidx.compose.ui.unit.Dp) {
    val p = FuryColors.current
    val selected = state.magicPane == EditorPane.Spell(row.uid)
    val shape = RoundedCornerShape(10.dp)
    EditorRow(
        Modifier.then(if (selected) Modifier.border(1.dp, p.acc, shape) else Modifier)
            .clickable(role = Role.Button, onClickLabel = "Открыть ${row.name}") { state.magicPane = EditorPane.Spell(row.uid) },
        minHeight = control + 8.dp,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.name, Modifier.weight(1f, fill = false), color = p.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.manaLabel, color = p.manaText, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
            }
            if (row.learned) row.weakText?.let { Text(it, color = p.goldText, style = MaterialTheme.typography.labelSmall) }
        }
        val xp = row.learnXp?.let { " · $it XP" } ?: ""
        EditorChip(if (row.learned) "Изучено$xp" else "Изучить$xp", row.learned, { host.setSpellLearned(row.uid, !row.learned) }, control, gold = true, role = Role.Switch)
    }
}

@Composable
private fun SpellDetailBody(detail: EditorSpellDetail) {
    val p = FuryColors.current
    Text(detail.title, color = p.text, style = MaterialTheme.typography.titleLarge)
    Text(detail.subtitle, color = p.muted, style = MaterialTheme.typography.bodySmall)
    detail.facts.forEach { (key, value) ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(key, Modifier.widthIn(min = 100.dp), color = p.muted, style = MaterialTheme.typography.bodySmall)
            Text(value, Modifier.weight(1f), color = p.text, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (detail.description.isNotBlank()) Text(detail.description, color = p.text2, style = MaterialTheme.typography.bodyMedium)
    if (detail.enhancement.isNotBlank()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.goldSoft).border(1.dp, p.goldLine, RoundedCornerShape(10.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EditorOverline("Усиление", color = p.goldText)
            Text(detail.enhancement, color = p.text2, style = MaterialTheme.typography.bodySmall)
        }
    }
    detail.weakText?.let { Text(it, color = p.goldText, style = MaterialTheme.typography.labelMedium) }
    if (detail.conflictNote.isNotBlank()) {
        EditorOverline("Расхождение книги")
        Text(detail.conflictNote, color = p.muted, style = MaterialTheme.typography.bodySmall)
    }
    if (detail.incomplete) Text("Правило неполное — проверьте и поправьте текст.", color = p.goldText, style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnedSpellPane(host: DublEditorHost, state: EditorState, spell: KnownSpell, control: androidx.compose.ui.unit.Dp, onEdit: () -> Unit) {
    EditorCard(Modifier.fillMaxWidth()) {
        SpellDetailBody(editorSpellDetail(host.active, spell))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            val xp = editorSpellLearnXp(spell)?.let { " · $it XP" } ?: ""
            EditorChip(if (spell.learned) "Изучено$xp" else "Изучить$xp", spell.learned, { host.setSpellLearned(spell.uid, !spell.learned) }, control, gold = true, role = Role.Switch)
            EditorOutlineButton("Изменить", onEdit, control)
            EditorOutlineButton("Удалить", { editorRemoveSpell(host, state, spell.uid) }, control, accent = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogSpellPane(
    host: DublEditorHost,
    state: EditorState,
    entry: com.furybook.dubl.model.SpellCatalogEntry,
    control: androidx.compose.ui.unit.Dp,
    onFix: (String) -> Unit,
) {
    val known = host.active.magic.spells.any { it.catalogId == entry.id }
    val xp = MagicEquipmentRules.learnXpCost(entry.cost)?.let { " · $it XP" } ?: ""
    EditorCard(Modifier.fillMaxWidth()) {
        SpellDetailBody(editorSpellDetail(host.active, entry))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FurySpacing.sm), verticalArrangement = Arrangement.spacedBy(FurySpacing.sm)) {
            val label = when {
                known -> "Уже в книге"
                entry.incomplete -> "Добавить и исправить"
                else -> "Добавить$xp"
            }
            EditorPrimaryButton(label, {
                val uid = editorAddCatalogSpell(host, state, entry)
                if (uid != null && entry.incomplete) onFix(uid)
            }, control, enabled = !known)
            EditorOutlineButton("К каталогу", { state.magicPane = EditorPane.SpellCatalog }, control)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpellCatalogPane(host: DublEditorHost, state: EditorState, control: androidx.compose.ui.unit.Dp, compact: Boolean) {
    val p = FuryColors.current
    val rows = editorSpellCatalog(host, state.spellCatalogQuery, state.spellCatalogSchool, state.spellCatalogUsableOnly)
    val schools = MagicSchoolCatalog.schools.filter { school -> host.magicEquipmentCatalog.spells.any { school in MagicSchoolCatalog.parseSchools(it.school) } }
    EditorCard(Modifier.fillMaxWidth()) {
        Text("Каталог заклинаний", color = p.text, style = MaterialTheme.typography.titleMedium)
        SheetSearchField(state.spellCatalogQuery, { state.spellCatalogQuery = it }, "Поиск по названию и тексту…", Modifier.fillMaxWidth(), compact)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorChip("Все школы", state.spellCatalogSchool == null, { state.spellCatalogSchool = null }, control)
            schools.forEach { school -> EditorChip(school, state.spellCatalogSchool == school, { state.spellCatalogSchool = school }, control) }
        }
        EditorChip("только доступные по силе школы", state.spellCatalogUsableOnly, { state.spellCatalogUsableOnly = !state.spellCatalogUsableOnly }, control, gold = true, role = Role.Switch)
        if (rows.isEmpty()) Text(if (host.magicEquipmentCatalog.spells.isEmpty()) "Каталог заклинаний пуст" else "Ничего не найдено", color = p.dim, style = MaterialTheme.typography.bodySmall)
        rows.forEach { row ->
            EditorRow(Modifier.clickable(role = Role.Button, onClickLabel = "Подробнее: ${row.name}") { state.magicPane = EditorPane.CatalogSpell(row.id) }, minHeight = control + 8.dp) {
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.name, Modifier.weight(1f, fill = false), color = p.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row.manaLabel, color = p.manaText, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                    }
                    Text(row.school, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val state2 = when {
                    row.known -> "в книге"
                    !row.usable -> "сила ${row.requiredPower}"
                    else -> null
                }
                state2?.let { Text(it, Modifier.padding(end = 6.dp), color = if (row.known) p.muted else p.goldText, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
