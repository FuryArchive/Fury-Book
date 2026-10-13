import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.*
import com.furybook.dubl.state.CharacterSession

fun main() {
    var nextId = 0
    val initial = DublCharacter(
        id = "gear",
        attributes = AttributeId.entries.associateWith { AttributeValue(base = 2) },
    ).normalized()
    val session = CharacterSession(InMemoryCharacterStore(AppSnapshot(listOf(initial), initial.id)), idFactory = { "gear-${++nextId}" })

    val catalog = GearCatalogEntry(
        id = "sword",
        name = "Меч",
        category = "Оружие",
        section = "Ближний бой",
        fields = linkedMapOf("Вес" to "2,5 кг", "Урон" to "1d6"),
        description = "Стальной меч",
    )
    val uid = session.addCatalogGear(catalog)
    var item = session.active.gear.items.single()
    check(item.uid == uid)
    check(item.catalogId == catalog.id)
    check(item.name == catalog.name)
    check(item.category == catalog.category)
    check(item.section == catalog.section)
    check(item.description == catalog.description)
    check(item.fields == catalog.fields)
    check(item.load == 2.5)
    check(item.quantity == 1)
    check(!item.custom)

    check(session.addCatalogGear(catalog) == uid)
    item = session.active.gear.items.single()
    check(item.quantity == 2) { "re-adding canonical gear must increment the existing stack" }
    check(MagicEquipmentRules.equipmentLoad(session.active) == 5.0)

    val customUid = session.addCustomGear(
        GearItem(
            uid = "",
            catalogId = "must-be-cleared",
            name = "  Верёвка  ",
            quantity = 0,
            load = -7.0,
            carried = true,
            custom = false,
        )
    )
    val custom = session.active.gear.items.single { it.uid == customUid }
    check(custom.catalogId == null)
    check(custom.custom)
    check(custom.name == "Верёвка")
    check(custom.quantity == 1)
    check(custom.load == 0.0)

    session.updateGearItem(customUid) { it.copy(quantity = -5, load = -1.0) }
    val normalizedCustom = session.active.gear.items.single { it.uid == customUid }
    check(normalizedCustom.quantity == 1)
    check(normalizedCustom.load == 0.0)

    session.updateGearItem(uid) { it.copy(carried = false) }
    check(MagicEquipmentRules.equipmentLoad(session.active) == 0.0)
    session.updateGearItem(uid) { it.copy(carried = true, quantity = 3) }
    check(MagicEquipmentRules.equipmentLoad(session.active) == 7.5)

    session.setGearLoadAutomatic(false)
    session.setGearManualLoad(-100.0)
    check(MagicEquipmentRules.equipmentLoad(session.active) == 0.0)
    session.setGearManualLoad(12.75)
    check(MagicEquipmentRules.equipmentLoad(session.active) == 12.75)

    val raw = DublCharacter(
        id = "raw",
        gear = CharacterGear(items = listOf(GearItem(uid = "raw-item", quantity = 0, load = 3.0, carried = true))),
    )
    check(MagicEquipmentRules.equipmentLoad(raw) == 3.0) {
        "equipmentLoad must uphold the model invariant that a stored item has at least quantity 1"
    }

    val legacy = GearCatalogEntry(
        id = "legacy",
        name = "Старый доспех",
        category = "Доспех",
        section = "Броня",
        fields = linkedMapOf("Вес" to "8", "Треб." to "3"),
        description = "",
    )
    session.addCatalogGear(legacy)
    val legacyUid = session.active.gear.items.single { it.catalogId == legacy.id }.uid
    session.updateGearItem(legacyUid) { it.copy(load = 3.0) }
    session.syncCatalogGearLoads(listOf(catalog, legacy))
    check(session.active.gear.items.single { it.uid == legacyUid }.load == 8.0) {
        "legacy requirement-as-weight values must be repaired from the canonical catalog"
    }

    println("EQUIPMENT_PARITY_OK")
}
