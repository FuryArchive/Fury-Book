package com.furybook.dubl.master

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MasterModelsTest {
    @Test
    fun enemyFormRejectsBadInput() {
        assertEquals(listOf("Нужно имя", "«Защита» — целое число", "«Здоровье» — целое число"),
            EnemyForm(name = " ", defense = "7.5", health = "много").errors())
        assertTrue(EnemyForm(name = "Волк", defense = "", health = " 12 ").errors().isEmpty())
        val base = MasterEnemy("e1", "old")
        assertEquals(MasterEnemy("e1", "Волк", defense = 0, health = 12, attacks = "Укус 2d6"),
            EnemyForm(name = " Волк ", health = "12", attacks = "Укус 2d6").toEnemy(base))
        val enemy = MasterEnemy("e2", "Гоблин", 6, 2, 1, 8, 3, "Нож", "трус")
        assertEquals(enemy, EnemyForm.from(enemy).toEnemy(enemy))
        assertEquals(enemy.copy(id = "e3", name = "Гоблин (копия)"), duplicateEnemy(enemy, "e3"))
    }

    @Test
    fun noteSearchIgnoresCaseAndYo() {
        val notes = listOf(
            MasterNote("n2", "Сессия 5", "Ёжик в тумане", "2026-11-21"),
            MasterNote("n1", "Ежевика", "", "2026-11-14"),
            MasterNote("n0", "Таверна", "ничего", "2026-11-07"),
        )
        assertEquals(listOf("n2", "n1"), searchNotes(notes, "ЕЖ").map { it.id })
        assertEquals(notes, searchNotes(notes, "  "))
        assertEquals(MasterNote("x", "", "", "2026-11-22"), newNote("x", "2026-11-22"))
    }
}
