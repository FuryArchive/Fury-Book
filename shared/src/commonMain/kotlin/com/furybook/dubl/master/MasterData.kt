package com.furybook.dubl.master

import com.furybook.core.json.JsonValue
import com.furybook.core.json.array
import com.furybook.core.json.asObject
import com.furybook.core.json.asString
import com.furybook.core.json.has
import com.furybook.core.json.int
import com.furybook.core.json.isNull
import com.furybook.core.json.jsonArray
import com.furybook.core.json.jsonNumber
import com.furybook.core.json.jsonObject
import com.furybook.core.json.jsonString
import com.furybook.core.json.jsonStringify
import com.furybook.core.json.objectValue
import com.furybook.core.json.parseRoot
import com.furybook.core.json.string

/** A reusable enemy card. [attacks] is free text, one attack per line. */
data class MasterEnemy(
    val id: String,
    val name: String,
    val defense: Int = 0,
    val reflexes: Int = 0,
    val initiative: Int = 0,
    val health: Int = 0,
    val endurance: Int = 0,
    val attacks: String = "",
    val notes: String = "",
)

enum class FightKind { PLAYER, ENEMY }

/**
 * One row of the fight. [initiative] is what the GM typed (dice are rolled at the table), so it can be blank.
 * Players have no [hp]; the GM tracks only the enemies'.
 */
data class FightEntry(
    val uid: String,
    val name: String,
    val kind: FightKind,
    val initiative: String = "",
    val hp: Int? = null,
    val hpMax: Int? = null,
    val conditions: List<String> = emptyList(),
    val initiativeBonus: Int? = null,
    val note: String = "",
)

data class FightState(val entries: List<FightEntry> = emptyList(), val round: Int = 1, val turnUid: String? = null)

data class MasterNote(val id: String, val title: String, val text: String = "", val date: String = "")

/** Everything Мастер keeps on the device. Never sent to the server and never tied to an account. */
data class MasterData(
    val version: Int = 1,
    val enemies: List<MasterEnemy> = emptyList(),
    val fight: FightState = FightState(),
    val notes: List<MasterNote> = emptyList(),
)

/** Where the GM data lives on this device. */
interface MasterStore {
    fun load(): MasterData
    fun save(data: MasterData)
}

class InMemoryMasterStore(private var data: MasterData = MasterData()) : MasterStore {
    override fun load(): MasterData = data
    override fun save(data: MasterData) { this.data = data }
}

/** `master.json`: plain JSON. Anything unreadable gives empty data rather than an error. */
object MasterDataCodec {
    fun encode(data: MasterData): String = jsonStringify(
        jsonObject(
            "version" to jsonNumber(data.version),
            "enemies" to jsonArray(data.enemies.map(::enemy)),
            "fight" to jsonObject(
                "round" to jsonNumber(data.fight.round),
                "turnUid" to data.fight.turnUid?.let(::jsonString),
                "entries" to jsonArray(data.fight.entries.map(::entry)),
            ),
            "notes" to jsonArray(data.notes.map { jsonObject("id" to jsonString(it.id), "title" to jsonString(it.title), "text" to jsonString(it.text), "date" to jsonString(it.date)) }),
        ),
    )

    fun decode(raw: String?): MasterData {
        if (raw.isNullOrBlank()) return MasterData()
        return runCatching {
            val root = parseRoot(raw)
            val fight = root.objectValue("fight")
            MasterData(
                version = root.int("version", 1),
                enemies = root.array("enemies").mapNotNull { it.asObject()?.let(::enemy) },
                fight = FightState(
                    entries = fight?.array("entries").orEmpty().mapNotNull { it.asObject()?.let(::entry) },
                    round = (fight?.int("round", 1) ?: 1).coerceAtLeast(1),
                    turnUid = fight?.string("turnUid")?.takeIf(String::isNotBlank),
                ),
                notes = root.array("notes").mapNotNull { value ->
                    value.asObject()?.let { MasterNote(it.string("id"), it.string("title"), it.string("text"), it.string("date")) }
                },
            )
        }.getOrDefault(MasterData())
    }

    private fun enemy(e: MasterEnemy) = jsonObject(
        "id" to jsonString(e.id), "name" to jsonString(e.name),
        "defense" to jsonNumber(e.defense), "reflexes" to jsonNumber(e.reflexes), "initiative" to jsonNumber(e.initiative),
        "health" to jsonNumber(e.health), "endurance" to jsonNumber(e.endurance),
        "attacks" to jsonString(e.attacks), "notes" to jsonString(e.notes),
    )

    private fun enemy(o: JsonValue.Obj) = MasterEnemy(
        id = o.string("id"), name = o.string("name"),
        defense = o.int("defense"), reflexes = o.int("reflexes"), initiative = o.int("initiative"),
        health = o.int("health"), endurance = o.int("endurance"),
        attacks = o.string("attacks"), notes = o.string("notes"),
    )

    private fun entry(f: FightEntry) = jsonObject(
        "uid" to jsonString(f.uid), "name" to jsonString(f.name), "kind" to jsonString(f.kind.name),
        "initiative" to jsonString(f.initiative),
        "hp" to f.hp?.let(::jsonNumber), "hpMax" to f.hpMax?.let(::jsonNumber),
        "conditions" to jsonArray(f.conditions.map(::jsonString)),
        "initiativeBonus" to f.initiativeBonus?.let(::jsonNumber),
        "note" to jsonString(f.note),
    )

    private fun entry(o: JsonValue.Obj) = FightEntry(
        uid = o.string("uid"), name = o.string("name"),
        kind = if (o.string("kind") == FightKind.PLAYER.name) FightKind.PLAYER else FightKind.ENEMY,
        initiative = o.string("initiative"),
        hp = o.optInt("hp"), hpMax = o.optInt("hpMax"),
        conditions = o.array("conditions").mapNotNull { it.asString() },
        initiativeBonus = o.optInt("initiativeBonus"),
        note = o.string("note"),
    )

    private fun JsonValue.Obj.optInt(key: String): Int? = if (has(key) && !isNull(key)) int(key) else null
}
