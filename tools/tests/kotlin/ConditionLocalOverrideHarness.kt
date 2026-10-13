package com.furybook.dubl.state

import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras

private class MemoryExtrasStore : CharacterExtrasStore {
    private val data = mutableMapOf<String, CharacterSheetExtras>()
    override fun load(characterId: String): CharacterSheetExtras = data[characterId] ?: CharacterSheetExtras()
    override fun save(characterId: String, extras: CharacterSheetExtras) { data[characterId] = extras }
    override fun delete(characterId: String) { data.remove(characterId) }
}

fun main() {
    val session = CharacterExtrasSession(MemoryExtrasStore(), customConditionIdFactory = { "custom-1" })
    val characterId = "hero"

    session.setConditionOverride(characterId, CharacterConditionId.TIRED, "Утомлён", "Домашнее описание")
    var extras = session.load(characterId)
    val override = extras.conditionOverrides.getValue(CharacterConditionId.TIRED)
    check(override.title == "Утомлён")
    check(override.description == "Домашнее описание")

    val uid = session.addCustomCondition(characterId, "Горит синим", "Домашнее состояние") ?: error("custom condition not added")
    check(uid == "custom-1")
    session.setCustomConditionActive(characterId, uid, true)
    extras = session.load(characterId)
    check(extras.customConditions.single().active)

    session.updateCustomCondition(characterId, uid, "Горит сильнее", "Новое описание")
    extras = session.load(characterId)
    check(extras.customConditions.single().title == "Горит сильнее")

    session.resetConditionOverride(characterId, CharacterConditionId.TIRED)
    check(CharacterConditionId.TIRED !in session.load(characterId).conditionOverrides)

    session.removeCustomCondition(characterId, uid)
    check(session.load(characterId).customConditions.isEmpty())

    println("CONDITION_LOCAL_OVERRIDE_OK")
}
