package com.furybook.dubl.model
import kotlin.test.*

class SkillRollAlternativesTest {
    private val ids = listOf("feat_ef01c79bad95bc11","feat_48d67bcbe8fc28c8","feat_098eff5ce64b5b87",
        "feat_80cb914af7c6df13","feat_b4c4cb83cb4afccd","feat_5da49e15b0b6c3dc","feat_1e4f2d8d4bab0107")
    private val catalog = SkillEffectCatalog("test",ids.mapIndexed { i,id ->
        SkillEffectDefinition("effect-$i",i,"Source", "Condition", "", "", "",SkillEffectMode.ALTERNATIVE,
            RollContext.SKILL,emptyList(),0,false,"",id)
    })
    private val character = DublCharacter("c",skills=mapOf(
        "riding" to CharacterSkill("riding","riding",rank=1),
        "driving" to CharacterSkill("driving","driving",rank=5),
        "survival" to CharacterSkill("survival","survival",rank=4)),
        development=ids.associateWith { OwnedDevelopment(1) })
    @Test fun alternatives_are_owned_and_scoped_not_global_attribute_permissions() {
        assertEquals(2,character.skillRollAlternatives(character.resolveSkill("medicine")!!,catalog).size)
        assertEquals(2,character.skillRollAlternatives(character.resolveSkill("stealth")!!,catalog).size)
        assertTrue(character.skillRollAlternatives(character.resolveSkill("athletics")!!,catalog).isEmpty())
        assertTrue(character.copy(development=emptyMap()).skillRollAlternatives(character.resolveSkill("medicine")!!,catalog).isEmpty())
        assertTrue(character.copy(disabledSkillEffectIds=catalog.effects.map { it.id }.toSet())
            .skillRollAlternatives(character.resolveSkill("medicine")!!,catalog).isEmpty())
    }
    @Test fun motor_knight_substitutes_only_roll_rank_without_mutating_saved_state() {
        val roll=character.skillRollAlternatives(character.resolveSkill("riding")!!,catalog).single()
        assertEquals(5,roll.skill.rank);assertEquals(1,character.resolveSkill("riding")!!.rank)
        assertEquals("riding",roll.skill.id)
    }
    @Test fun medical_care_and_first_aid_are_mutually_distinct_formulas() {
        val variants=character.skillRollAlternatives(character.resolveSkill("medicine")!!,catalog)
        assertEquals(listOf("survival","medicine"),variants.map { it.skill.id })
        assertEquals(listOf(AttributeId.INTELLIGENCE,AttributeId.DEXTERITY),variants.map { it.attribute })
        assertTrue(variants.last().label.contains("15"))
        assertNotNull(character.skillCalculationForRoll(variants.first().skill,variants.first().attribute).total)
    }
    @Test fun crowd_logic_and_command_use_exact_replacement_attribute_or_skill() {
        assertEquals(AttributeId.CHARISMA,character.skillRollAlternatives(character.resolveSkill("stealth")!!,catalog).last().attribute)
        assertEquals(AttributeId.INTELLIGENCE,character.skillRollAlternatives(character.resolveSkill("eloquence")!!,catalog).single().attribute)
        assertEquals("intimidation",character.skillRollAlternatives(character.resolveSkill("leadership")!!,catalog).single().skill.id)
    }
    @Test fun source_presets_are_exclusive_and_do_not_invent_resource_actions() {
        val lip="feat_b6173be4e251982d"; val search="feat_e63bcf4c87dd83e1"
        val c=character.copy(development=mapOf(lip to OwnedDevelopment(1),search to OwnedDevelopment(1)))
        val presets=SkillEffectCatalog("test",listOf(lip,search).mapIndexed { i,id ->
            SkillEffectDefinition("preset-$i",i,"Source","Source condition","","","",SkillEffectMode.PRESET,
                RollContext.SKILL,emptyList(),0,false,"",id)
        })
        val reading=c.skillRollAlternatives(c.resolveSkill("awareness")!!,presets).single()
        assertEquals(AttributeId.INTELLIGENCE,reading.attribute)
        assertEquals(0,reading.advantageDice)
        val searching=c.skillRollAlternatives(c.resolveSkill("search")!!,presets)
        assertEquals(listOf(1,2,3),searching.map { it.advantageDice })
        assertEquals(3,searching.map { it.id }.toSet().size)
        assertTrue(searching.all { it.attribute==AttributeId.INTELLIGENCE && it.skill.id=="search" })
        assertTrue(c.skillRollAlternatives(c.resolveSkill("medicine")!!,presets).isEmpty())
        assertTrue(c.copy(disabledSkillEffectIds=setOf("preset-1"))
            .skillRollAlternatives(c.resolveSkill("search")!!,presets).isEmpty())
    }
}
