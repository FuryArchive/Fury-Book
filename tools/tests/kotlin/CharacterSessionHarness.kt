import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.*
import com.furybook.dubl.state.CharacterSession

fun main() {
    val c = DublCharacter(id = "c1", attributes = AttributeId.entries.associateWith { AttributeValue(base = 2) })
    var id = 0
    val s = CharacterSession(InMemoryCharacterStore(AppSnapshot(listOf(c), c.id)), idFactory = { "id-${++id}" })
    val custom = checkNotNull(s.addCustomSkill("Тест", "", listOf(AttributeId.INTELLIGENCE), UntrainedRule.NO))
    s.changeSkillRank(custom, 4)
    check(s.active.skills.getValue(custom).rank == 4)
    s.setMagicManaRank(2)
    s.setMagicSchoolPower("Разрушение", 5)
    check(MagicEquipmentRules.schoolPower(s.active, "Разрушение") == 5)
    s.addCustomGear(GearItem(uid = "", name = "Молот", load = 2.0))
    check(s.active.gear.items.single().uid == "id-2")
    s.createCharacter()
    check(s.snapshot.characters.size == 2)
    check(s.active.id == "id-3")
    println("CHARACTER_SESSION_OK")
}
