package com.furybook.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import com.furybook.ui.theme.DublTheme
import java.nio.file.Files
import org.junit.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class LegacyCharacterStartupTest {
    private class ExistingProfile {
        val root = Files.createTempDirectory("fury-existing-profile")
        val characters = root.resolve("characters.json")
        val extras = root.resolve("sheet-extras.json")
        val characterRaw = """{"schema":7,"activeCharacterId":"old","characters":[{"id":"old","name":"Персонаж до обновления","experience":3210,"creationComplete":true,"xpAdjustment":-70,"abilityPointsOverride":3,"skills":[{"id":"athletics","definitionId":"athletics","name":"Атлетика","rank":4,"attributes":["STRENGTH","DEXTERITY"]}]}]}"""
        val extrasRaw = """{"version":1,"characters":{"old":{"notes":"Мои старые записи","portraitUri":"portrait.png","conditions":["WEAKNESS"],"favoriteSkillIds":["athletics"],"retiredPanel":{"open":true}}}}"""
        init { Files.writeString(characters, characterRaw); Files.writeString(extras, extrasRaw) }
        fun app(local: Boolean) = DesktopAppState(DesktopCharacterStore(characters), DesktopCharacterExtrasStore(extras), local,
            DesktopAcquisitionStore(root), experimentalGameplayEnabled = local)
        fun assertSourcesUntouched() {
            assertEquals(characterRaw, Files.readString(characters))
            assertEquals(extrasRaw, Files.readString(extras))
            assertFalse(DesktopAcquisitionStore(root).hasProtectedHeads())
        }
        fun assertCharacter(state: DesktopAppState) {
            assertEquals("old", state.snapshot.activeCharacterId)
            assertEquals("Персонаж до обновления", state.activeCharacter.name)
            assertEquals(3210, state.activeCharacter.experience)
            assertEquals(-70, state.activeCharacter.xpAdjustment)
            assertEquals(3, state.activeCharacter.abilityPointsOverride)
            assertEquals(4, state.activeCharacter.skills.getValue("athletics").rank)
            assertEquals("Мои старые записи", state.extras.notes)
            assertEquals(setOf(CharacterConditionId.WEAKNESS), state.extras.activeConditions)
            assertFalse(state.cloudLedgerV2)
        }
    }

    @Test fun existing_profile_opens_and_reopens_in_default_mode_without_manual_file_edits() {
        val profile = ExistingProfile()
        repeat(2) {
            val state = profile.app(false)
            profile.assertCharacter(state)
            profile.assertSourcesUntouched()
            state.dispose()
        }
    }

    @Test fun existing_profile_renders_real_sheet_with_local_rules_and_reopens_without_migration() =
        runDesktopComposeUiTest(width = 1280, height = 900, testTimeout = 120.seconds) {
            val profile = ExistingProfile()
            var state by mutableStateOf(profile.app(true))
            setContent { DublTheme { DesktopVisualTheme { DesktopAppContent(state) } } }
            fun awaitSheet() {
                waitUntil(timeoutMillis = 45_000) {
                    state.canonicalViewReady && onAllNodesWithTag("character.sheet").fetchSemanticsNodes().isNotEmpty()
                }
                onAllNodesWithText("Персонаж до обновления").onFirst().assertExists()
                assertNull(state.storageError)
                assertIs<LoadResult.MigrationRequired>(state.localStoredCharacter())
                profile.assertCharacter(state)
                profile.assertSourcesUntouched()
            }
            awaitSheet()
            runOnUiThread { state.dispose(); state = profile.app(true) }
            awaitSheet()
        }
}
