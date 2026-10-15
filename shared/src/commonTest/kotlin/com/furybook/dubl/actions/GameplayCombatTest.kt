package com.furybook.dubl.actions

import kotlin.test.*
import com.furybook.dubl.model.*

class GameplayCombatTest {
    @Test fun reserved_defense_is_added_to_the_character_total_and_disappears_after_expiry() {
        val character = DublCharacter("hero", enduranceCurrent = 0)
        val conditions = setOf(CharacterConditionId.EXHAUSTION)
        var state = apply(GameplayTimeline("hero"), GameBoundary.TurnStarted("own-1", "hero"))
        state = apply(state, GameBoundary.DefenseReserved("reserve", 2))
        assertEquals(character.defense - 3, character.conditionDerivedValues(conditions,
            temporaryDefenseBonus = state.combat.defenseBonus).defense)
        state = apply(state, GameBoundary.TurnEnded("end-own", "hero", "own-1"))
        state = apply(state, GameBoundary.TurnStarted("own-2", "hero"))
        assertEquals(character.defense - 5, character.conditionDerivedValues(conditions,
            temporaryDefenseBonus = state.combat.defenseBonus).defense)
    }

    private fun apply(state: GameplayTimeline, event: GameBoundary): GameplayTimeline {
        val result = GameplayEvents.apply(state, RecordGameBoundary("op-${event.boundaryId}", state.characterId, state.revision, event))
        assertIs<BoundaryTransition.Applied>(result)
        return result.timeline
    }

    @Test fun own_turn_refills_actions_and_reaction_but_other_boundaries_do_not() {
        var state = apply(GameplayTimeline("hero"), GameBoundary.TurnStarted("own-1", "hero"))
        state = apply(state, GameBoundary.ActionPointsSpent("spend", 2))
        state = apply(state, GameBoundary.ReactionSpent("react"))
        assertEquals(1, state.combat.actionPoints)
        assertEquals(0, state.combat.reactions)
        state = apply(state, GameBoundary.TurnEnded("end-own", "hero", "own-1"))
        state = apply(state, GameBoundary.TurnStarted("other", "enemy"))
        state = apply(state, GameBoundary.TurnEnded("end-other", "enemy", "other"))
        state = apply(state, GameBoundary.RoundEnded("round"))
        state = apply(state, GameBoundary.SceneEnded("scene"))
        state = apply(state, GameBoundary.RestCompleted("rest", RestKind.FULL, 480))
        assertEquals(1, state.combat.actionPoints)
        assertEquals(0, state.combat.reactions)
        state = apply(state, GameBoundary.TurnStarted("own-2", "hero"))
        assertEquals(3, state.combat.actionPoints)
        assertEquals(1, state.combat.reactions)
    }

    @Test fun source_backed_defensive_reservation_spends_actions_and_expires_at_next_own_start() {
        var state = apply(GameplayTimeline("hero"), GameBoundary.TurnStarted("own-1", "hero"))
        state = apply(state, GameBoundary.DefenseReserved("reserve", 2))
        assertEquals(1, state.combat.actionPoints)
        assertEquals(2, state.combat.defenseBonus)
        val effect = state.effects.single()
        assertEquals("core:P14265", effect.sourceRule)
        assertEquals(2, effect.defenseBonus)
        state = apply(state, GameBoundary.TurnEnded("end-own", "hero", "own-1"))
        state = apply(state, GameBoundary.TurnStarted("other", "enemy"))
        state = apply(state, GameBoundary.ReactionSpent("react"))
        state = apply(state, GameBoundary.TurnEnded("end-other", "enemy", "other"))
        state = apply(state, GameBoundary.RoundEnded("round"))
        assertEquals(2, state.combat.defenseBonus)
        state = apply(state, GameBoundary.TurnStarted("own-2", "hero"))
        assertEquals(0, state.combat.defenseBonus)
        assertTrue(state.effects.isEmpty())
        assertEquals(listOf(effect.instanceId), state.receipts.last().expiredEffectIds)
    }

    @Test fun duplicate_spend_is_replayed_and_invalid_costs_do_not_change_state() {
        val started = apply(GameplayTimeline("hero"), GameBoundary.TurnStarted("own", "hero"))
        val request = RecordGameBoundary("spend", "hero", started.revision, GameBoundary.ActionPointsSpent("spent", 2))
        val state = assertIs<BoundaryTransition.Applied>(GameplayEvents.apply(started, request)).timeline
        assertIs<BoundaryTransition.Replayed>(GameplayEvents.apply(state, request))
        assertEquals(1, state.combat.actionPoints)
        for (cost in listOf(-1, 0, 2, Int.MAX_VALUE)) {
            assertIs<BoundaryTransition.Blocked>(GameplayEvents.apply(state,
                RecordGameBoundary("bad-$cost", "hero", state.revision, GameBoundary.ActionPointsSpent("bad-$cost", cost))))
        }
        val reacted = apply(state, GameBoundary.ReactionSpent("reaction"))
        assertIs<BoundaryTransition.Blocked>(GameplayEvents.apply(reacted,
            RecordGameBoundary("again", "hero", reacted.revision, GameBoundary.ReactionSpent("again"))))
        assertEquals(0, reacted.combat.reactions)
        assertEquals(1, reacted.combat.actionPoints)
    }

    @Test fun action_spending_needs_own_turn_and_saved_history_reconstructs_budget_and_effects() {
        val empty = GameplayTimeline("hero")
        assertIs<BoundaryTransition.Blocked>(GameplayEvents.apply(empty,
            RecordGameBoundary("no-turn", "hero", 0, GameBoundary.ActionPointsSpent("no-turn", 1))))
        var state = apply(empty, GameBoundary.TurnStarted("own", "hero"))
        state = apply(state, GameBoundary.DefenseReserved("reserve", 1))
        state = apply(state, GameBoundary.ActionPointsSpent("spend", 1))
        val restored = GameplayEventLog("hero", state.receipts.map { it.request }).timeline
        assertEquals(state.combat, restored.combat)
        assertEquals(state.effects, restored.effects)
        assertEquals(1, restored.combat.actionPoints)
        assertEquals(1, restored.combat.defenseBonus)
    }
}
