package com.furybook.dubl.ui.editor

import com.furybook.dubl.application.CharacterRestorePoint
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterEconomy
import com.furybook.dubl.model.CharacterSkill
import com.furybook.dubl.model.DevelopmentCatalog
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.resolvedSkills
import com.furybook.dubl.model.MagicSchoolCatalog
import com.furybook.dubl.ui.sheet.formatSheetNumber

/** One line of «Изменения за этот заход»; [xpSpent] > 0 means free XP went down. */
data class EditorChangeLine(val text: String, val xpSpent: Int)

/** [netSpent]: how much free XP went down; [spent]: how much more XP is spent on the build (GM XP is neither). */
data class EditorChanges(val lines: List<EditorChangeLine>, val netSpent: Int, val spent: Int = 0)

/**
 * What changed since the editor opened, one line per field. Each line's XP is what the economy says
 * that one change costs on top of [before]: no cost table lives here. [catalogFor] gives each character
 * its own catalog (local edits and own perks included), so a local edit that reprices a perk shows it.
 */
fun editorChanges(
    before: CharacterRestorePoint,
    after: CharacterRestorePoint,
    catalogFor: (DublCharacter) -> DevelopmentCatalog,
    includeChi: Boolean,
    effectNames: Map<String, String> = emptyMap(),
): EditorChanges {
    val b = before.character
    val a = after.character
    val beforeCatalog = catalogFor(b)
    val afterCatalog = catalogFor(a)
    fun spent(character: DublCharacter): Int = CharacterEconomy.breakdown(character, catalogFor(character), includeChi).spentXp
    val baseSpent = spent(b)
    fun cost(modified: DublCharacter): Int = spent(modified) - baseSpent
    val lines = mutableListOf<EditorChangeLine>()
    fun add(text: String, xp: Int = 0) { lines += EditorChangeLine(text, xp) }

    if (a.name != b.name) add("Имя: ${a.name}")
    if (a.concept != b.concept) add("Концепция изменена")
    if (before.extras.portraitUri != after.extras.portraitUri) add("Портрет изменён")
    if (a.size != b.size) add("Размер ${b.size} → ${a.size}")
    if (a.legs != b.legs) add("Ноги ${b.legs} → ${a.legs}")
    if (a.manaEnabled != b.manaEnabled) add(if (a.manaEnabled) "Мана включена" else "Мана выключена")
    if (a.experience != b.experience) add("Опыт ${b.experience} → ${a.experience}", b.experience - a.experience)
    if (a.creationExperience != b.creationExperience) add("Опыт создания ${b.creationExperience} → ${a.creationExperience}")
    if (a.xpAdjustment != b.xpAdjustment) add("Поправка опыта: ${a.xpAdjustment}")
    if (a.abilityPointsOverride != b.abilityPointsOverride) add("Очки способностей: ${a.abilityPointsOverride ?: "авто"}")

    AttributeId.entries.forEach { id ->
        val from = b.attributes[id]?.base ?: 0
        val to = a.attributes[id]?.base ?: 0
        if (from != to) add("${id.title} $from → $to", cost(b.copy(attributes = b.attributes + (id to (a.attributes[id] ?: return@forEach)))))
    }

    val resolved = (b.resolvedSkills(includeHidden = true) + a.resolvedSkills(includeHidden = true)).associateBy { it.id }
    val names = resolved.mapValues { it.value.name }
    (b.skills.keys + a.skills.keys).forEach { id ->
        val from = b.skills[id]?.rank ?: 0
        val to = a.skills[id]?.rank ?: 0
        val dynamic = resolved[id]?.isDynamic ?: true
        // A rulebook skill gets a saved entry the first time a setting changes: that is a setting, not a new skill.
        val unsaved = CharacterSkill(id = id, definitionId = id)
        // Ranks (and the creation snapshot «Завершить создание» takes of them) are not settings.
        val settingsBefore = (b.skills[id] ?: unsaved).copy(rank = 0, creationRank = 0)
        val settingsAfter = (a.skills[id] ?: unsaved).copy(rank = 0, creationRank = 0)
        when {
            dynamic && id !in b.skills -> add("+ ${names[id] ?: id}")
            dynamic && id !in a.skills -> add("− ${names[id] ?: id}")
            settingsBefore != settingsAfter -> add("${names[id] ?: id}: настройки изменены")
        }
        if (from == to) return@forEach
        val modified = a.skills[id]?.let { b.copy(skills = b.skills + (id to it)) } ?: b.copy(skills = b.skills - id)
        add("${names[id] ?: id} $from → $to", cost(modified))
    }

    val ownBefore = b.customDevelopmentEntries.associateBy { it.id }
    val ownAfter = a.customDevelopmentEntries.associateBy { it.id }
    (ownBefore.keys + ownAfter.keys).forEach { id ->
        val was = ownBefore[id]
        val now = ownAfter[id]
        when {
            // «(своя запись)» tells the record line apart from the rank line when the new perk is also taken.
            was == null && now != null -> add("+ ${now.name} (своя запись)", cost(b.copy(customDevelopmentEntries = b.customDevelopmentEntries + now)))
            // Deleting an own perk also drops its ranks: one line, priced with both.
            now == null && was != null -> add(
                "− ${was.name} (своя запись)",
                cost(b.copy(customDevelopmentEntries = b.customDevelopmentEntries - was, development = b.development - id)),
            )
            was != now && now != null -> add(
                "${now.name}: запись изменена",
                cost(b.copy(customDevelopmentEntries = b.customDevelopmentEntries.map { if (it.id == id) now else it })),
            )
        }
    }
    val removedOwn = ownBefore.keys - ownAfter.keys

    (b.developmentOverrides.keys + a.developmentOverrides.keys).forEach { id ->
        val now = a.developmentOverrides[id]
        if (b.developmentOverrides[id] == now) return@forEach
        val name = (afterCatalog.byId(id) ?: beforeCatalog.byId(id))?.name ?: id
        val modified = b.copy(developmentOverrides = if (now == null) b.developmentOverrides - id else b.developmentOverrides + (id to now))
        add(if (now == null) "$name: правка сброшена" else "$name: локальная правка", cost(modified))
    }

    // Rank lines are priced against the visit's records (own perks, local edits), so taking a perk created
    // or edited in this visit costs what its record now says.
    val withRecords = b.copy(customDevelopmentEntries = a.customDevelopmentEntries, developmentOverrides = a.developmentOverrides)
    val recordsSpent = spent(withRecords)
    (b.development.keys + a.development.keys).forEach { id ->
        if (id in removedOwn) return@forEach
        val from = b.development[id]?.rank ?: 0
        val to = a.development[id]?.rank ?: 0
        val name = (afterCatalog.byId(id) ?: beforeCatalog.byId(id))?.name ?: id
        if (from == to) {
            if (b.development[id]?.optionIndex != a.development[id]?.optionIndex) add("$name: другой вариант")
            return@forEach
        }
        val text = when {
            from == 0 -> "+ $name"
            to == 0 -> "− $name"
            else -> "$name: ранг $to"
        }
        val modified = a.development[id]?.let { withRecords.copy(development = withRecords.development + (id to it)) }
            ?: withRecords.copy(development = withRecords.development - id)
        add(text, spent(modified) - recordsSpent)
    }

    (a.hiddenSkillIds - b.hiddenSkillIds).forEach { add("Скрыто: ${names[it] ?: it}") }
    (b.hiddenSkillIds - a.hiddenSkillIds).forEach { add("Возвращено: ${names[it] ?: it}") }
    (a.disabledSkillEffectIds - b.disabledSkillEffectIds).forEach { add("Автоматизация: ${effectNames[it] ?: it} выкл.") }
    (b.disabledSkillEffectIds - a.disabledSkillEffectIds).forEach { add("Автоматизация: ${effectNames[it] ?: it} вкл.") }

    if (a.chiEnabled != b.chiEnabled) add(if (a.chiEnabled) "ЦИ включено" else "ЦИ выключено")
    if (a.chiBonusRanks != b.chiBonusRanks) add("ЦИ: доп. ранги ${b.chiBonusRanks} → ${a.chiBonusRanks}", cost(b.copy(chiBonusRanks = a.chiBonusRanks)))

    magicLines(b, a, ::cost, ::add)
    gearLines(b, a, ::add)

    if (a.creationComplete != b.creationComplete) add(if (a.creationComplete) "Создание завершено" else "Создание открыто снова")
    // Whatever else differs is still reverted by «Отменить всё», so the list (and the button) must not look empty.
    if (lines.isEmpty() && before != after) add("Другие изменения")

    val beforeEconomy = CharacterEconomy.breakdown(b, beforeCatalog, includeChi)
    val afterEconomy = CharacterEconomy.breakdown(a, afterCatalog, includeChi)
    return EditorChanges(lines, beforeEconomy.remainingXp - afterEconomy.remainingXp, afterEconomy.spentXp - beforeEconomy.spentXp)
}

fun editorNetText(spent: Int, freeDelta: Int): String {
    val spentText = if (spent > 0) "Потрачено $spent XP" else "Возвращено ${-spent} XP"
    val freeText = "свободно " + (if (freeDelta > 0) "+" else "") + freeDelta
    return when {
        spent == 0 && freeDelta == 0 -> "Опыт не изменился"
        spent == 0 -> "Свободно " + (if (freeDelta > 0) "+" else "") + "$freeDelta XP"
        freeDelta == -spent -> spentText
        else -> "$spentText · $freeText"
    }
}

fun editorNetText(changes: EditorChanges): String = editorNetText(changes.spent, -changes.netSpent)

fun editorXpText(spent: Int): String = when {
    spent > 0 -> "−$spent XP"
    spent < 0 -> "+${-spent} XP"
    else -> ""
}

/** Mana rank, schools (matched by their rulebook name) and spells (by uid), each priced by putting only that change on [b]. */
private fun magicLines(b: DublCharacter, a: DublCharacter, cost: (DublCharacter) -> Int, add: (String, Int) -> Unit) {
    fun withMagic(magic: com.furybook.dubl.model.CharacterMagic) = b.copy(magic = magic)
    if (a.magic.manaRank != b.magic.manaRank) {
        add("Ранг маны ${b.magic.manaRank} → ${a.magic.manaRank}", cost(withMagic(b.magic.copy(manaRank = a.magic.manaRank))))
    }
    val schoolsBefore = b.magic.schools.associateBy { MagicSchoolCatalog.canonicalize(it.name) }
    val schoolsAfter = a.magic.schools.associateBy { MagicSchoolCatalog.canonicalize(it.name) }
    (schoolsBefore.keys + schoolsAfter.keys).forEach { key ->
        val was = schoolsBefore[key]
        val now = schoolsAfter[key]
        when {
            was == null && now != null -> add("+ школа ${now.name}", cost(withMagic(b.magic.copy(schools = b.magic.schools + now))))
            now == null && was != null -> add("− школа ${was.name}", cost(withMagic(b.magic.copy(schools = b.magic.schools - was))))
            was != null && now != null -> {
                val replaced = { school: com.furybook.dubl.model.MagicSchool -> withMagic(b.magic.copy(schools = b.magic.schools.map { if (it === was) school else it })) }
                if (was.rank != now.rank) add("${now.name}: сила ${was.rank} → ${now.rank}", cost(replaced(was.copy(rank = now.rank))))
                if (was.note != now.note) add("${now.name}: примечание изменено", 0)
            }
        }
    }
    val spellsBefore = b.magic.spells.associateBy { it.uid }
    val spellsAfter = a.magic.spells.associateBy { it.uid }
    (spellsBefore.keys + spellsAfter.keys).forEach { uid ->
        val was = spellsBefore[uid]
        val now = spellsAfter[uid]
        when {
            was == null && now != null -> add("+ заклинание ${now.name}", cost(withMagic(b.magic.copy(spells = b.magic.spells + now))))
            now == null && was != null -> add("− заклинание ${was.name}", cost(withMagic(b.magic.copy(spells = b.magic.spells - was))))
            was != null && now != null -> {
                val replaced = { spell: com.furybook.dubl.model.KnownSpell -> withMagic(b.magic.copy(spells = b.magic.spells.map { if (it.uid == uid) spell else it })) }
                val learnedOnly = was.copy(learned = now.learned)
                val learnedCost = cost(replaced(learnedOnly))
                if (was.learned != now.learned) add(if (now.learned) "${now.name}: изучено" else "${now.name}: не изучено", learnedCost)
                if (learnedOnly != now) add("${now.name}: изменено", cost(replaced(now)) - learnedCost)
            }
        }
    }
}

/** Gear costs no XP: one line per item change, then the load mode. */
private fun gearLines(b: DublCharacter, a: DublCharacter, add: (String, Int) -> Unit) {
    val before = b.gear.items.associateBy { it.uid }
    val afterIds = a.gear.items.mapTo(mutableSetOf()) { it.uid }
    a.gear.items.forEach { now ->
        val was = before[now.uid]
        if (was == null) {
            add("+ предмет ${now.name}", 0)
            return@forEach
        }
        if (was.quantity != now.quantity) add("${now.name}: ×${was.quantity} → ×${now.quantity}", 0)
        if (was.carried != now.carried) add(if (now.carried) "${now.name}: с собой" else "${now.name}: не с собой", 0)
        if (was.copy(quantity = now.quantity, carried = now.carried) != now) add("${now.name}: изменён", 0)
    }
    b.gear.items.filter { it.uid !in afterIds }.forEach { add("− предмет ${it.name}", 0) }
    val manualChanged = !a.gear.loadAutomatic && a.gear.loadManual != b.gear.loadManual
    when {
        a.gear.loadAutomatic != b.gear.loadAutomatic || manualChanged ->
            add(if (a.gear.loadAutomatic) "Нагрузка: автоматически" else "Нагрузка: вручную ${formatSheetNumber(a.gear.loadManual)}", 0)
    }
}
