package com.furybook.dubl.model

import kotlin.test.assertEquals
import kotlin.test.Test

class DevelopmentEffectsTest {
    private fun characterWithEffects(): DublCharacter = DublCharacter(
        id = "effects",
        size = 5,
        attributes = defaultAttributes() + mapOf(
            AttributeId.STRENGTH to AttributeValue(base = 3),
            AttributeId.CONSTITUTION to AttributeValue(base = 4),
            AttributeId.SPEED to AttributeValue(base = 3),
            AttributeId.DEXTERITY to AttributeValue(base = 4),
            AttributeId.PERCEPTION to AttributeValue(base = 2),
            AttributeId.WILL to AttributeValue(base = 3),
        ),
        development = mapOf(
            DevelopmentEffectIds.INCREDIBLE_HEALTH to OwnedDevelopment(3),
            DevelopmentEffectIds.STALWART to OwnedDevelopment(2),
            DevelopmentEffectIds.ENDURING to OwnedDevelopment(2),
            DevelopmentEffectIds.QUICK_REFLEXES to OwnedDevelopment(2),
            DevelopmentEffectIds.IMPROVED_INITIATIVE to OwnedDevelopment(2),
            DevelopmentEffectIds.RUNNER to OwnedDevelopment(2),
        ),
    )

    @Test
    fun passiveDevelopmentChangesDerivedStats() {
        val character = characterWithEffects()

        assertEquals(26, character.healthMaximum)
        assertEquals(9, character.fortitude)
        assertEquals(5, character.enduranceMaximum)
        assertEquals(9, character.reflexes)
        assertEquals(7, character.initiative)
        assertEquals(13.0, character.runFull, 0.001)
    }

    @Test
    fun incredibleHealthScalesForCreaturesLargerThanFive() {
        val character = characterWithEffects().copy(size = 7)

        assertEquals(3 * 4, character.incredibleHealthBonus)
    }

    @Test
    fun selfTaughtDiscountsFirstTwoSkillRanksOnlyAfterCreation() {
        val trained = DublCharacter(
            id = "self-taught",
            creationComplete = true,
            skills = mapOf(
                "athletics" to CharacterSkill(id = "athletics", definitionId = "athletics", rank = 3),
            ),
            development = mapOf(DevelopmentEffectIds.SELF_TAUGHT to OwnedDevelopment(1)),
        )

        assertEquals(45, trained.skillXpSpent())
        assertEquals(30, trained.skillNextRankCost(2))
        assertEquals(60, trained.copy(creationComplete = false).skillXpSpent())
    }

    @Test
    fun selfTaughtDoesNotRefundRanksHeldAtCreation() {
        val selfTaught = mapOf(DevelopmentEffectIds.SELF_TAUGHT to OwnedDevelopment(1))
        fun athletics(rank: Int, creationRank: Int) = mapOf(
            "athletics" to CharacterSkill(id = "athletics", definitionId = "athletics", rank = rank, creationRank = creationRank),
        )

        val boughtAtCreation = DublCharacter(id = "a", creationComplete = true, skills = athletics(2, 2), development = selfTaught)
        assertEquals(30, boughtAtCreation.skillXpSpent())

        // Rank 1 at creation, rank 2 bought later at half price: 10 + 20 / 2.
        val raisedAfter = DublCharacter(id = "b", creationComplete = true, skills = athletics(2, 1), development = selfTaught)
        assertEquals(20, raisedAfter.skillXpSpent())
        assertEquals(10, raisedAfter.skillNextRankCost(1))
    }

    @Test
    fun completingCreationRecordsCreationRanksAndKeepsXpSpent() {
        val session = com.furybook.dubl.state.CharacterSession(
            com.furybook.dubl.data.InMemoryCharacterStore(
                AppSnapshot(
                    listOf(
                        DublCharacter(
                            id = "c",
                            experience = 1000,
                            skills = mapOf("athletics" to CharacterSkill(id = "athletics", definitionId = "athletics", rank = 2)),
                            development = mapOf(DevelopmentEffectIds.SELF_TAUGHT to OwnedDevelopment(1)),
                        ),
                    ),
                    "c",
                ),
            ),
        ) { "id" }
        val before = session.active.skillXpSpent()
        session.completeCreation()

        assertEquals(2, session.active.skills.getValue("athletics").creationRank)
        assertEquals(before, session.active.skillXpSpent())
    }

    @Test
    fun haulerRaisesEquipmentCapacityByTenPercentPerRank() {
        val character = DublCharacter(
            id = "hauler",
            attributes = defaultAttributes() + mapOf(
                AttributeId.STRENGTH to AttributeValue(base = 6),
                AttributeId.CONSTITUTION to AttributeValue(base = 4),
            ),
            development = mapOf(DevelopmentEffectIds.HAULER to OwnedDevelopment(2)),
        )

        assertEquals(12, MagicEquipmentRules.equipmentCapacity(character))
    }
}
