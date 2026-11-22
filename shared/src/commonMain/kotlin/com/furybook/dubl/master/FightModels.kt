package com.furybook.dubl.master

private fun FightEntry.initiativeValue(): Int? = initiative.trim().toIntOrNull()

/** Turn order: highest initiative first; blank or non-numeric entries last; ties keep the order they were added. */
fun fightOrder(state: FightState): List<FightEntry> =
    state.entries.sortedWith(compareBy<FightEntry> { it.initiativeValue() == null }.thenByDescending { it.initiativeValue() ?: 0 })

/** «Следующий →»: the next in [fightOrder]; after the last one a new round starts at the top. */
fun nextTurn(state: FightState): FightState {
    val order = fightOrder(state)
    if (order.isEmpty()) return state
    val index = order.indexOfFirst { it.uid == state.turnUid }
    return when {
        index < 0 -> state.copy(turnUid = order.first().uid)
        index == order.lastIndex -> state.copy(turnUid = order.first().uid, round = state.round + 1)
        else -> state.copy(turnUid = order[index + 1].uid)
    }
}

fun newFight(): FightState = FightState(entries = emptyList(), round = 1, turnUid = null)

private fun enemyBase(enemy: MasterEnemy): String = enemy.name.trim().ifBlank { "Враг" }

/** N of a «<base> N» enemy row, or null for anything else («Гоблин-шаман» is not a «Гоблин»). */
private fun copyNumber(entry: FightEntry, base: String): Int? =
    if (entry.kind != FightKind.ENEMY || !entry.name.startsWith("$base ")) null
    else entry.name.removePrefix("$base ").toIntOrNull()

/** How many copies of [enemy] are in the fight («в бою: N» in Враги). */
fun enemyCopiesInFight(state: FightState, enemy: MasterEnemy): Int = state.entries.count { copyNumber(it, enemyBase(enemy)) != null }

/** Adds a copy of [enemy] as «<имя> N» (one past the highest copy still in the fight), with its health and initiative bonus. */
fun addEnemy(state: FightState, enemy: MasterEnemy, newUid: String): FightState {
    val base = enemyBase(enemy)
    val number = (state.entries.mapNotNull { copyNumber(it, base) }.maxOrNull() ?: 0) + 1
    val entry = FightEntry(
        uid = newUid,
        name = "$base $number",
        kind = FightKind.ENEMY,
        // No health typed: no hit-point control (a 0/0 bar helps nobody).
        hp = enemy.health.takeIf { it > 0 },
        hpMax = enemy.health.takeIf { it > 0 },
        initiativeBonus = enemy.initiative,
    )
    return state.copy(entries = state.entries + entry)
}

fun addPlayer(state: FightState, name: String, bonus: Int?, newUid: String): FightState =
    state.copy(entries = state.entries + FightEntry(newUid, name.trim().ifBlank { "Игрок" }, FightKind.PLAYER, initiativeBonus = bonus))

private fun FightState.update(uid: String, transform: (FightEntry) -> FightEntry): FightState =
    copy(entries = entries.map { if (it.uid == uid) transform(it) else it })

/** Changes an enemy's hit points within 0…max; entries without hit points stay as they are. */
fun changeHp(state: FightState, uid: String, delta: Int): FightState = state.update(uid) { entry ->
    val max = entry.hpMax ?: return@update entry
    entry.copy(hp = ((entry.hp ?: max) + delta).coerceIn(0, max))
}

fun toggleCondition(state: FightState, uid: String, condition: String): FightState = state.update(uid) { entry ->
    val clean = condition.trim()
    if (clean.isEmpty()) entry
    else entry.copy(conditions = if (clean in entry.conditions) entry.conditions - clean else entry.conditions + clean)
}

fun setInitiative(state: FightState, uid: String, initiative: String): FightState = state.update(uid) { it.copy(initiative = initiative) }

fun setNote(state: FightState, uid: String, note: String): FightState = state.update(uid) { it.copy(note = note) }

/** Removes a row; if it held the turn, the turn passes to the next one in order. */
fun removeEntry(state: FightState, uid: String): FightState {
    val order = fightOrder(state)
    val next = if (state.turnUid == uid) {
        val index = order.indexOfFirst { it.uid == uid }
        order.getOrNull(index + 1)?.uid ?: order.firstOrNull { it.uid != uid }?.uid
    } else state.turnUid
    return state.copy(entries = state.entries.filterNot { it.uid == uid }, turnUid = next)
}

/** «Бросить» for the GM: 2d6 plus the entry's bonus. */
fun rollInitiative(entry: FightEntry, d6: () -> Int): Int = d6() + d6() + (entry.initiativeBonus ?: 0)

private fun FightState.tiedWithPrevious(uid: String): Pair<FightEntry, FightEntry>? {
    val order = fightOrder(this)
    val index = order.indexOfFirst { it.uid == uid }
    if (index <= 0) return null
    val (before, entry) = order[index - 1] to order[index]
    return if (before.initiativeValue() == entry.initiativeValue()) before to entry else null
}

/** ▲ only makes sense between equal initiatives: the order of everything else comes from the numbers. */
fun canNudgeUp(state: FightState, uid: String): Boolean = state.tiedWithPrevious(uid) != null

/** Moves [uid] above the tied entry before it (swaps their places in the list). */
fun nudgeUp(state: FightState, uid: String): FightState {
    val (before, entry) = state.tiedWithPrevious(uid) ?: return state
    val list = state.entries.toMutableList()
    val i = list.indexOf(before)
    val j = list.indexOf(entry)
    list[i] = entry
    list[j] = before
    return state.copy(entries = list)
}
