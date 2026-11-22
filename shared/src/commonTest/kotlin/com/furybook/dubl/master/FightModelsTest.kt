package com.furybook.dubl.master

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FightModelsTest {
    private fun e(uid: String, ini: String, kind: FightKind = FightKind.PLAYER) = FightEntry(uid, uid, kind, ini)

    @Test
    fun fightOrderHandlesBlanksAndTies() {
        val state = FightState(listOf(e("a", "5"), e("b", ""), e("c", "7"), e("d", "5"), e("x", "-")))
        assertEquals(listOf("c", "a", "d", "b", "x"), fightOrder(state).map { it.uid })
        assertEquals(listOf("c"), fightOrder(FightState(listOf(e("c", " 7 ")))).map { it.uid })
    }

    @Test
    fun nextTurnWrapsAndCountsRounds() {
        var state = FightState(listOf(e("a", "3"), e("b", "9")))
        state = nextTurn(state)
        assertEquals("b", state.turnUid); assertEquals(1, state.round)
        state = nextTurn(state)
        assertEquals("a", state.turnUid); assertEquals(1, state.round)
        state = nextTurn(state)
        assertEquals("b", state.turnUid); assertEquals(2, state.round)
    }

    @Test
    fun nextTurnOnEmptyFightDoesNothing() {
        assertEquals(newFight(), nextTurn(newFight()))
        assertEquals(FightState(emptyList(), 1, null), newFight())
    }

    @Test
    fun enemyCopiesAreNumbered() {
        val goblin = MasterEnemy("g", "Гоблин", initiative = 2, health = 8)
        var state = addEnemy(newFight(), goblin, "u1")
        state = addEnemy(state, goblin, "u2")
        assertEquals(listOf("Гоблин 1", "Гоблин 2"), state.entries.map { it.name })
        val first = state.entries.first()
        assertEquals(FightKind.ENEMY, first.kind); assertEquals(8, first.hp); assertEquals(8, first.hpMax); assertEquals(2, first.initiativeBonus)
        // Removing «Гоблин 1» must not make the next copy a second «Гоблин 2»; «Гоблин-шаман» is not a goblin copy.
        val afterRemoval = addEnemy(removeEntry(state, "u1"), goblin, "u9")
        assertEquals(listOf("Гоблин 2", "Гоблин 3"), afterRemoval.entries.map { it.name })
        val shaman = addEnemy(newFight(), MasterEnemy("s", "Гоблин-шаман"), "s1")
        assertEquals("Гоблин 1", addEnemy(shaman, goblin, "g1").entries.last().name)
        assertEquals(2, enemyCopiesInFight(state, goblin))
        assertEquals(0, enemyCopiesInFight(shaman, goblin))
        state = addPlayer(state, "Вика", 3, "u3")
        assertEquals(FightEntry("u3", "Вика", FightKind.PLAYER, initiativeBonus = 3), state.entries.last())
    }

    @Test
    fun hpIsClamped() {
        val state = addEnemy(newFight(), MasterEnemy("g", "Гоблин", health = 8), "u1")
        assertEquals(0, changeHp(state, "u1", -20).entries.single().hp)
        assertEquals(8, changeHp(changeHp(state, "u1", -3), "u1", 10).entries.single().hp)
        assertEquals(5, changeHp(state, "u1", -3).entries.single().hp)
        val toggled = toggleCondition(toggleCondition(state, "u1", "Оглушён"), "u1", "Горит")
        assertEquals(listOf("Оглушён", "Горит"), toggled.entries.single().conditions)
        assertEquals(listOf("Горит"), toggleCondition(toggled, "u1", "Оглушён").entries.single().conditions)
    }

    @Test
    fun rollUsesTheBonus() {
        fun dice(): () -> Int { val rolls = ArrayDeque(listOf(3, 4)); return { rolls.removeFirst() } }
        assertEquals(10, rollInitiative(FightEntry("a", "a", FightKind.ENEMY, initiativeBonus = 3), dice()))
        assertEquals(7, rollInitiative(FightEntry("a", "a", FightKind.ENEMY), dice()))
    }

    @Test
    fun removingTheTurnHolderPassesTheTurn() {
        var state = nextTurn(FightState(listOf(e("a", "9"), e("b", "3"))))
        assertEquals("a", state.turnUid)
        state = removeEntry(state, "a")
        assertEquals("b", state.turnUid)
        assertNull(removeEntry(state, "b").turnUid)
    }
}

class FightNudgeTest {
    private fun e(uid: String, ini: String) = FightEntry(uid, uid, FightKind.PLAYER, ini)

    @Test
    fun nudgeSwapsOnlyTiedNeighbours() {
        val state = FightState(listOf(e("a", "5"), e("b", "9"), e("c", "5")))
        assertEquals(listOf("b", "a", "c"), fightOrder(state).map { it.uid })
        assertEquals(true, canNudgeUp(state, "c"))
        assertEquals(false, canNudgeUp(state, "a"))
        assertEquals(listOf("b", "c", "a"), fightOrder(nudgeUp(state, "c")).map { it.uid })
        assertEquals(state, nudgeUp(state, "a"))
    }
}

class MasterSmallFixesTest {
    @Test
    fun enemyWithoutHealthHasNoHitPoints() {
        val ghost = addEnemy(newFight(), MasterEnemy("g", "Призрак", health = 0), "u").entries.single()
        assertNull(ghost.hp)
        assertNull(ghost.hpMax)
    }

    @Test
    fun blankNewNoteIsDroppedOnClose() {
        val notes = listOf(MasterNote("n2", "", "", "2026-11-22"), MasterNote("n1", "Сессия", "", "2026-11-21"))
        assertEquals(listOf("n1"), saveNoteEdit(notes, notes[0].copy(title = "  ", text = "\n")).map { it.id })
        assertEquals("Таверна", saveNoteEdit(notes, notes[0].copy(title = "Таверна")).first().title)
    }
}
