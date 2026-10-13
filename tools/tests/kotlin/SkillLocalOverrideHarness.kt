package com.furybook.dubl.state

import com.furybook.dubl.data.InMemoryCharacterStore
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SkillCategory
import com.furybook.dubl.model.UntrainedRule
import com.furybook.dubl.model.resolveSkill
import com.furybook.dubl.model.skillCalculation

fun main() {
    val initial = DublCharacter(id = "override")
    val session = CharacterSession(InMemoryCharacterStore(AppSnapshot(listOf(initial), initial.id)), idFactory = { "id" })

    val canonical = session.active.resolveSkill("computers") ?: error("computers missing")
    check(canonical.untrained == UntrainedRule.UNSPECIFIED)
    check(session.active.skillCalculation(canonical).total == null)

    session.setSkillNameOverride("computers", "Компьютеры (домашнее правило)")
    session.setSkillDescriptionOverride("computers", "Локальная трактовка")
    session.setSkillCategoryOverride("computers", SkillCategory.KNOWLEDGE)
    session.setSkillAttributes("computers", listOf(AttributeId.INTELLIGENCE, AttributeId.PERCEPTION))
    session.setSkillUntrainedOverride("computers", UntrainedRule.YES_MINUS_2)
    session.setSkillAutoOverrides("computers", auto6 = "Локальный Auto 6", auto12 = "Локальный Auto 12")
    session.changeSkillRank("computers", 2)
    session.setSkillModifier("computers", 3)
    session.setSkillFormulaNote("computers", "Не терять при reset")

    val overridden = session.active.resolveSkill("computers") ?: error("overridden computers missing")
    check(overridden.name == "Компьютеры (домашнее правило)")
    check(overridden.description == "Локальная трактовка")
    check(overridden.category == SkillCategory.KNOWLEDGE)
    check(overridden.attributes == listOf(AttributeId.INTELLIGENCE, AttributeId.PERCEPTION))
    check(overridden.untrained == UntrainedRule.YES_MINUS_2)
    check(overridden.auto6 == "Локальный Auto 6")
    check(overridden.auto12 == "Локальный Auto 12")

    session.resetSkillDefinitionOverrides("computers")
    val reset = session.active.resolveSkill("computers") ?: error("reset computers missing")
    check(reset.name == canonical.name)
    check(reset.description == canonical.description)
    check(reset.category == canonical.category)
    check(reset.attributes == listOf(canonical.definition!!.defaultAttribute))
    check(reset.untrained == UntrainedRule.UNSPECIFIED)
    check(reset.auto6 == canonical.auto6)
    check(reset.auto12 == canonical.auto12)
    check(reset.rank == 2) { "rank/progression must survive reset" }
    check(reset.modifier == 3) { "character modifier must survive reset" }
    check(reset.formulaNote == "Не терять при reset") { "character note must survive reset" }

    println("SKILL_LOCAL_OVERRIDE_OK")
}
