package com.furybook.dubl.model

import kotlin.test.*

class AutomationConditionsTest {
    private fun effect(id:String, mode:SkillEffectMode=SkillEffectMode.AUTO_BONUS, name:String="Проверка", target:String="Вождение") =
        SkillEffectDefinition("effect",1,name,"source-qualified test","","test","",mode,RollContext.SKILL,listOf(target),1,true,"condition",id)
    private fun rules(character:DublCharacter, effect:SkillEffectDefinition) =
        SkillEffectRules(character,DevelopmentCatalog("test",emptyList()),SkillEffectCatalog("test",listOf(effect)))
    private fun skill(id:String,rank:Int,name:String="",definition:String=id) =
        CharacterSkill(id=id,definitionId=definition,rank=rank,name=name)

    @Test fun ace_is_scoped_to_each_skill_rank_not_another_qualified_skill() {
        val perk="feat_3233f2da5c693e34"
        for(rank in 0..5) {
            val c=DublCharacter("c",skills=mapOf("driving" to skill("driving",rank)),development=mapOf(perk to OwnedDevelopment(2)))
            assertEquals(if(rank>=4)2 else 0,rules(c,effect(perk)).forSkill(c.resolveSkill("driving")!!).automaticBonus)
        }
    }
    @Test fun erudition_enables_untrained_ordinary_knowledge_but_not_secret_or_bonus() {
        val perk="feat_4e8ea7dbf3fb9784"
        val a=skill("ordinary",0,"Знание (История)","knowledge_template")
        val b=skill("secret",0,"Знание (Тайные знания)","knowledge_template")
        val c=DublCharacter("c",skills=mapOf(a.id to a,b.id to b),development=mapOf(perk to OwnedDevelopment(1)))
        val ordinary=c.resolveSkill(a.id)!!;val secret=c.resolveSkill(b.id)!!
        assertNotNull(c.skillCalculation(ordinary).total)
        assertNull(c.skillCalculation(secret).total)
        assertEquals(0,rules(c,effect(perk,target="Знание")).forSkill(ordinary).automaticBonus)
        assertNull(c.copy(disabledSkillEffectIds=setOf("skill_effect_046_77fd048a901f")).skillCalculation(ordinary).total)
    }
    @Test fun versatile_performer_uses_highest_learned_rank_without_mutation() {
        val a=skill("performance_vocal",2);val b=skill("dance",5,"Исполнение (Танец)","performance_template")
        val untrained=skill("flute",0,"Исполнение (Флейта)","performance_template")
        val c=DublCharacter("c",skills=mapOf(a.id to a,b.id to b,untrained.id to untrained),development=mapOf("feat_0bf4bbf1c46717a4" to OwnedDevelopment(1)))
        val selected=c.resolveSkill(a.id)!!
        assertEquals(5,c.effectivePerformanceRank(selected))
        assertEquals(3,c.skillCalculation(selected).contributions.single { it.label=="Разносторонний исполнитель" }.value)
        assertEquals(2,c.skills[a.id]!!.rank)
        assertEquals(0,c.effectivePerformanceRank(c.resolveSkill(untrained.id)!!))
    }
    @Test fun quick_search_second_rank_removes_its_own_hindrance_not_other_dice() {
        val perk="feat_dad6c142bda7893e"
        for(rank in 1..2) {
            val c=DublCharacter("c",development=mapOf(perk to OwnedDevelopment(rank)))
            val e=effect(perk,SkillEffectMode.TOGGLE_HINDRANCE,target="Поиск")
            val option=rules(c,e).forSkill(c.resolveSkill("search")!!).options.single()
            assertEquals(if(rank==1)1 else 0,option.hindranceDice)
        }
    }
    @Test fun tactile_lock_immunity_does_not_add_a_hindrance() {
        val perk="feat_3502559c63707e6a";val c=DublCharacter("c",development=mapOf(perk to OwnedDevelopment(1)))
        val option=rules(c,effect(perk,SkillEffectMode.TOGGLE_HINDRANCE,target="Взлом")).forSkill(c.resolveSkill("lockpicking")!!).options.single()
        assertEquals(0,option.hindranceDice)
    }
    @Test fun vigilance_is_passive_and_musculature_is_an_armor_bonus() {
        val c=DublCharacter("c",skills=mapOf("awareness" to skill("awareness",3)),development=mapOf(DevelopmentEffectIds.VIGILANCE to OwnedDevelopment(2),DevelopmentEffectIds.TOUGH_MUSCULATURE to OwnedDevelopment(3)))
        assertEquals(3+c.perception+4,c.passiveAwareness)
        assertEquals(3,c.naturalArmorDevelopmentBonus)
        assertFalse(c.skillCalculation(c.resolveSkill("awareness")!!).contributions.any { it.label=="Бдительность" })
    }
}
