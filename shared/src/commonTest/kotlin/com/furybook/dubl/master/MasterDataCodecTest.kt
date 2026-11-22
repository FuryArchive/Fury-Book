package com.furybook.dubl.master

import kotlin.test.Test
import kotlin.test.assertEquals

class MasterDataCodecTest {
    private val sample = MasterData(
        enemies = listOf(MasterEnemy("e1", "Бандит \"Шрам\"", 7, 2, 1, 12, 3, "Нож 2d6+1\nАрбалет", "Трус")),
        fight = FightState(
            entries = listOf(
                FightEntry("f1", "Вика", FightKind.PLAYER, "14", null, null, emptyList(), null, ""),
                FightEntry("f2", "Бандит 1", FightKind.ENEMY, "", 9, 12, listOf("Оглушён", "Горит"), 1, "у двери"),
            ),
            round = 3,
            turnUid = "f2",
        ),
        notes = listOf(MasterNote("n1", "Сессия 4", "Таверна «Ржавый якорь»", "2026-11-21")),
    )

    @Test
    fun roundTripKeepsEverything() {
        assertEquals(sample, MasterDataCodec.decode(MasterDataCodec.encode(sample)))
        assertEquals(MasterData(), MasterDataCodec.decode(MasterDataCodec.encode(MasterData())))
    }

    @Test
    fun brokenJsonGivesEmptyData() {
        assertEquals(MasterData(), MasterDataCodec.decode(null))
        assertEquals(MasterData(), MasterDataCodec.decode(""))
        assertEquals(MasterData(), MasterDataCodec.decode("{\"enemies\":[{"))
        assertEquals(MasterData(), MasterDataCodec.decode("[1,2]"))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val raw = """{"version":1,"future":{"x":1},"enemies":[{"id":"e1","name":"Волк","defense":5,"glow":true}],
            "fight":{"round":2,"entries":[{"uid":"f1","name":"Волк","kind":"DRAGON","initiative":"7"}]},"notes":[]}"""
        val data = MasterDataCodec.decode(raw)
        assertEquals(MasterEnemy("e1", "Волк", 5, 0, 0, 0, 0, "", ""), data.enemies.single())
        assertEquals(FightKind.ENEMY, data.fight.entries.single().kind)
        assertEquals(2, data.fight.round)
    }
}
