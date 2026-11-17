# Play sheet redesign + themes — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 2b play view with the approved at-a-glance play sheet. Make every colour in the app come from one of 5 player-chosen themes.

**Architecture:**
- **Themes.** Colours move into a `FuryPalette` provided by a `CompositionLocal`. The legacy `Dubl*`/`Fury*` colour names become `@Composable` getters into it, so all ~50 call sites follow the theme without edits.
- **Models.** New pure, tested functions turn host state into skills-by-attribute groups, perk cards (with cost tags read from the rules text), condition chips with effect text, and filtered notes.
- **Screen.** New play composables replace the play branch of `SheetScreen`. 2b's edit mode stays as the «Редактор» until the editor stage.

**Tech Stack:** Kotlin Multiplatform 2.4.20, Compose Multiplatform 1.12.0, kotlin.test; Python pytest for repo guards.

**Spec:** `docs/superpowers/specs/2026-11-18-play-sheet-redesign-design.md` (mockup: https://claude.ai/artifact/YBATzTRecCrYC1MEzAg7ex)

## Global Constraints

- The rules packages `com.furybook.dubl.model`, `.application`, `.data`, `.state` and `com.furybook.core` do not change. Saved data formats do not change.
- FCP contract: chi gating stays `uiMounts(CHARACTER_ECONOMY, XP_LINE).forFeature(CHI)`; mounted meters keep their pack tokens.
- Breakpoints: `sheetLayoutFor`: PHONE <600, WIDE <1100, WIDE_WITH_TRAY ≥1100 (unchanged). The tray stays 340dp.
- Touch targets ≥ `FuryMinTouch` (44dp) on PHONE and WIDE.
- Contrast for every palette: body text ≥ 4.5:1, numbers of 18sp and up ≥ 3:1. Checked with `contrastRatio` from `ui/theme/Contrast.kt`.
- Theme ids and names: `fury` «Ярость» (default), `graphite` «Графит», `neon` «Неон», `wasteland` «Пустошь», `parchment` «Пергамент» (light).
- The `graphite` palette must equal today's `Color.kt` values exactly, so «Графит» is the zero-change theme.
- Every 2b behaviour keeps working:
  - rolling (tray / dialog), vitals ±, restore of mounted meters, conditions toggle and dialog, custom resources;
  - notes add/edit/delete, undo toasts, Android Back leaves edit mode;
  - portraits, development details, the economy dialog, stat formula dialogs.
- Verification commands:
  - Kotlin: `/home/claude/gw.sh :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs`.
  - Python: `python3 -m pytest -q -rfE --continue-on-collection-errors tools/tests -p no:cacheprovider > /tmp/now.txt; comm -13 /tmp/pytest-baseline.txt <(grep -E '^(FAILED|ERROR)' /tmp/now.txt | sort)` must print nothing (baseline 37 failed + 6 errors).
- Dev characters: `rm -rf /tmp/seedhome && SEED_DIR=/tmp/seedhome /home/claude/gw.sh :shared:desktopTest --tests '*SeedDevCharacters*'`, then run the desktop app with `XDG_DATA_HOME=/tmp/seedhome`.
- Android compiles only in CI. Every task that touches `app/` is confirmed by the CI run in Task 10.
- Commits end with the session's `Co-Authored-By` / `Claude-Session` lines. Push `ui-revamp` only. No merge to main.

## Review Focus

1. **A colour read outside composition.** After Task 1 the legacy names are `@Composable` getters. Any use in a non-composable place (top-level `val`, default argument, `remember` lambda, plain function) fails to compile or freezes to one theme. Expected: the build passes and a theme switch repaints every screen.
2. **Light theme (Пергамент) on screens built for dark.** White text, hard-coded `Color.White` / `Color.Black`, or Material defaults stay unreadable. Expected: no text below 4.5:1 on any screen under Пергамент.
3. **Cost-tag false positives/negatives** on real catalog texts. Example: "на 1 ОД меньше" must give no tag, "Затрачивая реакцию" must give «реакция». Expected: the tag list matches the tests built from real entries.
4. **Huge or empty data.**
   - 0 skills shown with «Только с рангами»;
   - 0 perks;
   - a perk with an empty benefit;
   - 9+ resources;
   - 8+ conditions;
   - a 60+ character name;
   - a resource with max 0.

   Expected: nothing overflows, a placeholder line where a list is empty, and «…» on long text.
5. **Character switch / resize mid-session.** Expanded perk cards, the open resource adjuster and search text belong to one character. Expected: they reset when the active character changes (as `SheetScreenState.syncCharacter` does), and no crash on resize.

---

### Task 1: Theme palettes behind a CompositionLocal

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/theme/Palette.kt`
- Modify: `shared/src/commonMain/kotlin/com/furybook/ui/theme/Color.kt`, `shared/src/commonMain/kotlin/com/furybook/ui/theme/Theme.kt`
- Modify, wherever the compiler reports a colour read outside composition: files under `shared/`, `desktopApp/`, `webApp/`, `app/`
- Test: `shared/src/commonTest/kotlin/com/furybook/ui/theme/FuryTokensTest.kt`, `shared/src/commonTest/kotlin/com/furybook/ui/theme/FuryPaletteTest.kt`

**Interfaces:**
- Produces:
  - `enum class FuryThemeId(val key: String, val title: String) { FURY("fury","Ярость"), GRAPHITE("graphite","Графит"), NEON("neon","Неон"), WASTELAND("wasteland","Пустошь"), PARCHMENT("parchment","Пергамент"); companion object { fun fromKey(key: String?): FuryThemeId } }`. `fromKey` returns FURY for null or unknown keys.
  - `data class FuryPalette(val isLight: Boolean, val bg: Color, val head: Color, val sunk: Color, val panel: Color, val panel2: Color, val tile: Color, val hover: Color, val line: Color, val line2: Color, val line3: Color, val track: Color, val text: Color, val text2: Color, val muted: Color, val dim: Color, val acc: Color, val acc2: Color, val accPressed: Color, val accSoft: Color, val accLine: Color, val accText: Color, val accDeep: Color, val onAcc: Color, val gold: Color, val goldSoft: Color, val goldLine: Color, val goldText: Color, val dBg: Color, val dLine: Color, val dLabel: Color, val dText: Color)`
  - `fun furyPalette(id: FuryThemeId): FuryPalette`
  - `val LocalFuryPalette: ProvidableCompositionLocal<FuryPalette>`. It is a `staticCompositionLocalOf`; its default is `furyPalette(FuryThemeId.FURY)`.
  - `object FuryColors { val current: FuryPalette @Composable @ReadOnlyComposable get() }`
  - `fun furyColorScheme(p: FuryPalette): ColorScheme`. It builds `lightColorScheme` when `p.isLight`, otherwise `darkColorScheme`, with today's role mapping.
  - `@Composable fun DublTheme(themeId: FuryThemeId = FuryThemeId.FURY, content: @Composable () -> Unit)`
- The legacy names in `Color.kt` become getters, for example `val DublSurface: Color @Composable @ReadOnlyComposable get() = FuryColors.current.panel`. Mapping:
  - `DublBackground`/`DublSurfaceInset` → `bg`
  - `DublSurface` → `panel`
  - `DublSurfaceRaised` → `panel2`
  - `DublBorder` → `line2`
  - `FuryBorderStrong`/`FuryBarTrack` → `track`
  - `DublText` → `text`
  - `DublMuted` → `muted`
  - `DublAccent`/`DublHealth` → `acc`
  - `DublAccentStrong`/`DublFocus`/`DublDanger` → `accText`
  - `DublAccentSoft` → `accSoft`
  - `DublGold`/`DublStamina` → `gold`
  - `FuryAccentFill` → `acc2`
  - `FuryAccentFillPressed` → `accPressed`
  - `FuryOnAccentFill` → `onAcc`
  - `DublMana` (`#6F98D6`) and `DublCustomResource` (`#65B4A4`) stay plain `val`s, the same in every theme.

- [ ] **Step 1: Write the failing tests** in `FuryPaletteTest.kt`:

```kotlin
@Test fun graphiteIsTodaysPalette() {
    val p = furyPalette(FuryThemeId.GRAPHITE)
    assertEquals(Color(0xFF08080A), p.bg); assertEquals(Color(0xFF111114), p.panel)
    assertEquals(Color(0xFF16161A), p.panel2); assertEquals(Color(0xFF26262C), p.line2)
    assertEquals(Color(0xFF34343A), p.track); assertEquals(Color(0xFFF4F1EC), p.text)
    assertEquals(Color(0xFFB4B1AC), p.muted); assertEquals(Color(0xFFE5384F), p.acc)
    assertEquals(Color(0xFFFF6B7F), p.accText); assertEquals(Color(0xFF3A1119), p.accSoft)
    assertEquals(Color(0xFFF5C04E), p.gold); assertEquals(Color(0xFFB82840), p.acc2)
    assertEquals(Color(0xFF8E1B30), p.accPressed); assertEquals(Color.White, p.onAcc)
}
@Test fun everyPaletteReachesContrast() = FuryThemeId.entries.forEach { id ->
    val p = furyPalette(id)
    listOf(p.text to p.bg, p.text to p.panel2, p.muted to p.panel2, p.dim to p.panel, p.onAcc to p.acc2,
        p.accText to p.panel, p.accText to p.accSoft, p.goldText to p.goldSoft, p.dLabel to p.dBg, p.dText to p.dBg)
        .forEach { (fg, bg) -> assertTrue(contrastRatio(fg, bg) >= 4.5, "$id: $fg on $bg") }
    assertTrue(contrastRatio(p.track, p.panel) >= 1.3, "$id track")
    assertTrue(contrastRatio(p.gold, p.panel) >= 3.0 && contrastRatio(p.acc, p.panel) >= 3.0, "$id meters")
}
@Test fun onlyParchmentIsLight() = assertEquals(listOf(FuryThemeId.PARCHMENT), FuryThemeId.entries.filter { furyPalette(it).isLight })
@Test fun unknownKeyFallsBackToFury() { assertEquals(FuryThemeId.FURY, FuryThemeId.fromKey("nope")); assertEquals(FuryThemeId.NEON, FuryThemeId.fromKey("neon")) }
```

Rewrite `FuryTokensTest`. Its `paletteMatchesSpec`, contrast and colour-scheme checks now read `furyPalette(FuryThemeId.GRAPHITE)` and `furyColorScheme(...)`, and the Material `primary` readability check runs for every theme.

- [ ] **Step 2: Run** `/home/claude/gw.sh :shared:desktopTest --tests '*FuryPaletteTest*' --tests '*FuryTokensTest*'`. Expected: compile failure, `furyPalette` unresolved.

- [ ] **Step 3: Implement `Palette.kt`.**
  - Take the values for fury / neon / wasteland / parchment from the mockup's `THEMES` object. Graphite uses today's values. `accPressed` is `acc2` darkened about 25%.
  - Where Step 4 reports a pair below its threshold, change that colour (keep the hue, move lightness) until it passes. Contrast wins over the mockup's exact hex.
  - Then turn `Color.kt` into getters and make `DublTheme(themeId)` provide `LocalFuryPalette` and `MaterialTheme(colorScheme = furyColorScheme(...), typography = furyTypography(), ...)`. `test_fury_typography.py` needs that exact `typography = furyTypography()` text.

- [ ] **Step 4: Run** the Step 2 tests. Expected: PASS.

- [ ] **Step 5: Compile every target.** Run the Kotlin verification command.
  - For each "@Composable invocations can only happen from the context of a @Composable function" error, move the colour read into composition. Typical fixes: pass a `Color` parameter, read the colour inside the composable body, or replace a top-level `val` with a `@Composable` getter.
  - `FuryColorScheme` (top-level) becomes `furyColorScheme(furyPalette(FuryThemeId.GRAPHITE))` wherever tests used it.
  - Grep `app/` for the same patterns: Android is not compiled locally.
  - Expected: BUILD SUCCESSFUL. The Python guard prints nothing.

- [ ] **Step 6: Commit** `feat(theme): five palettes behind LocalFuryPalette; legacy colour names follow the theme`.

### Task 2: Theme choice — stored per device, picked from the account menu

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/theme/FuryThemeStore.kt`, `shared/src/commonMain/kotlin/com/furybook/ui/components/FuryThemePicker.kt`
- Create: `shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopThemeStore.kt`, `webApp/src/jsMain/kotlin/com/furybook/web/WebThemeStore.kt`, `app/src/main/java/com/furybook/android/data/AndroidThemeStore.kt`
- Modify: `desktopApp/.../Main.kt`, `webApp/.../Main.kt`, `app/.../MainActivity.kt`, `app/.../ui/DublApp.kt` (the account / menu slot)
- Test: `shared/src/commonTest/kotlin/com/furybook/ui/theme/FuryThemeStoreTest.kt`, `shared/src/desktopTest/kotlin/com/furybook/dubl/data/DesktopThemeStoreTest.kt`

**Interfaces:**
- Produces:
  - `interface FuryThemeStore { fun load(): FuryThemeId; fun save(id: FuryThemeId) }`
  - `class InMemoryFuryThemeStore(initial: FuryThemeId = FuryThemeId.FURY) : FuryThemeStore`
  - `class DesktopThemeStore(file: Path = defaultDataDirectory().resolve("ui-theme.txt")) : FuryThemeStore`. The file holds the key. A missing or bad file loads FURY.
  - `WebThemeStore`: browser `localStorage` key `furybook.theme`.
  - `AndroidThemeStore(context)`: SharedPreferences `furybook_ui`, key `theme`.
  - `@Composable fun FuryThemePicker(selected: FuryThemeId, onSelect: (FuryThemeId) -> Unit, modifier: Modifier = Modifier)`
- `FuryThemePicker` shows one swatch per theme: a 44dp tap target with a split swatch circle `panel|acc` of that palette inside. The selected one gets a `text`-coloured ring. Each has a content description «Тема: <title>», and the title shows on hover or long-press.
- The app root holds `var theme by remember { mutableStateOf(store.load()) }`, wraps everything in `DublTheme(theme)`, and the picker calls `store.save(it); theme = it`.
- Placement:
  - Desktop/web: inside the existing account popup/slot of `FuryShell`. If there is no popup, add a «Тема» entry that opens a small `FuryDialogScaffold` holding the picker.
  - Android: the same, from the shell menu.

- [ ] **Step 1: Write the failing tests:**
  - `InMemoryFuryThemeStore` save/load round-trip.
  - `DesktopThemeStore` in a temp dir: missing file → FURY; `save(NEON)` then a new instance `load()` → NEON; file containing `"garbage"` → FURY.
- [ ] **Step 2: Run** `/home/claude/gw.sh :shared:desktopTest --tests '*ThemeStore*'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** the stores, the picker, and the three app roots.
- [ ] **Step 4: Run** the Step 2 tests plus the Kotlin verification command. Expected: PASS, BUILD SUCCESSFUL.
- [ ] **Step 5: Manual check** under Xvfb (desktop, seeded home):
  - Pick Неон: the sheet and the Умения screen repaint.
  - Restart the app: it opens in Неон.
  - Pick Графит: it looks exactly like today.
- [ ] **Step 6: Commit** `feat(theme): theme picker in the account menu, remembered per device`.

### Task 3: Perk cost tags read from the rules text

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPerkCosts.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetPerkCostsTest.kt`

**Interfaces:**
- Produces:
  - `fun perkCostTags(entryId: String, benefit: String): List<String>`
  - `internal val PERK_COST_OVERRIDES: Map<String, List<String>>`, keyed by entry id. Use it for any entry the parser gets wrong; it starts empty unless a test shows a need.
- Rules:
  - Match `(\d+)\s*(?:пункт\S*\s*)?(ОД|маны|выносливост\S*)`.
  - Keep a match only if the 25 chars before it contain `За|за|трат|затрач` and the 10 chars after it do not contain `меньше`.
  - Labels: `"N ОД"`, `"N маны"`, `"N вын."`. Deduplicate, keep the order of appearance.
  - If there are no matches and the text matches `(тратя|затрачивая|тратить действие) реакци` (ignoring case), the result is `["реакция"]`.
  - The override map wins over everything.

- [ ] **Step 1: Write the failing tests** with the real catalog texts (copy the full `benefit` strings from `shared/src/commonMain/resources/fcp/dubl-3.69/content/`):
  - «Щит маны» (`За 1 ОД Вы можете активировать щит маны…`) → `["1 ОД"]`
  - «Исцеляющее касание» (`За 1 маны и 2 ОД …`) → `["1 маны", "2 ОД"]`
  - «Совершенный бросок» (`Затрачивая 1 пункт выносливости …`) → `["1 вын."]`
  - «Несокрушимый Дух» (`За 3 ОД Вы издаёте рев…`) → `["3 ОД"]`
  - «Фехтование» (`… разоружение занимает на 1 ОД меньше.`) → `[]`
  - «Боевое колдовство» (`Затрачивая реакцию …`) → `["реакция"]`
  - «Лжец» (`+1 за ранг на проверки красноречия…`) → `[]`
  - an empty string → `[]`
- [ ] **Step 2: Run** `/home/claude/gw.sh :shared:desktopTest --tests '*SheetPerkCostsTest*'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** `perkCostTags`.
- [ ] **Step 4: Run** the Step 2 tests. Expected: PASS.
- [ ] **Step 5: Commit** `feat(sheet): read perk action costs from the rules text`.

### Task 4: Play-sheet models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlayModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetModels.kt` (adds fields with defaults to `SheetSkillRow` and `SheetConditionChip`; existing callers compile unchanged)
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetPlayModelsTest.kt` (uses `FakeSheetHost`)

**Interfaces:**
- Consumes: `perkCostTags` (Task 3); `normalizedDevelopmentGroups`, `SheetGroupingRules`, `SkillEffectRules`, `resolvedSkills`, `skillCalculationForRoll` (existing).
- Produces:
  - `SheetSkillRow` gains `val perkMark: String? = null, val perkTip: String? = null, val breakdown: String = ""`.
  - `SheetConditionChip` gains `val description: String = "", val kind: SheetConditionKind = SheetConditionKind.HARMFUL`, with `enum class SheetConditionKind { HARMFUL, HELPFUL, CUSTOM }`.
  - `data class SheetAttributeSkills(val attribute: AttributeId, val label: String, val value: Int, val rows: List<SheetSkillRow>)`
  - `fun sheetSkillsByAttribute(host: DublSheetHost, rankedOnly: Boolean): List<SheetAttributeSkills>`
  - `fun sheetSkillTotals(host: DublSheetHost): Pair<Int, Int>` (shown with ranks, all visible)
  - `data class SheetPerkCard(val entryId: String, val name: String, val category: String, val rank: Int, val maxRank: Int, val benefit: String, val requirements: String, val costTags: List<String>, val requirementsUnmet: Boolean, val depth: Int, val opensDetails: Boolean, val isAccessRoot: Boolean)`
  - `data class SheetPerkGroup(val id: String, val title: String, val cards: List<SheetPerkCard>)`
  - `enum class SheetPerkFilter { ALL, ACTIVE, PASSIVE }`
  - `fun sheetPerkGroups(host: DublSheetHost): List<SheetPerkGroup>`
  - `fun filterPerkGroups(groups: List<SheetPerkGroup>, filter: SheetPerkFilter, query: String): List<SheetPerkGroup>`
  - `fun sheetActiveConditions(host)` (existing) now fills `description` and `kind`.
  - `fun filterNotes(notes: List<CharacterNote>, query: String): List<CharacterNote>`
  - `const val PERK_SEARCH_THRESHOLD = 10`, `const val NOTE_SEARCH_THRESHOLD = 6`, `const val CONDITION_INLINE_CAP = 5`
- Rules:
  - **Skills by attribute.**
    - Always 8 entries, in `AttributeId.entries` order. `label` is `AttributeId.shortTitle`.
    - Each row sits under its `stockAttribute`. Hidden skills are excluded.
    - `rankedOnly` keeps rank > 0.
    - Rows are sorted by bonus descending (null last), then by name.
    - `perkMark` is `"+N"` when the automatic bonus N ≠ 0, otherwise `"★"` if the skill has toggle or reminder effects, otherwise null. `perkTip` lists the source names, joined by ", ".
    - `breakdown` is `"<name>: 2d6 + <bonus> (<Attribute title> <value> + ранг <rank>)"`, adding `" + <N> навыки"` when the automatic bonus ≠ 0.
  - **Perks.**
    - Groups and order come from `normalizedDevelopmentGroups` (custom groups are kept) and `SheetGroupingRules.hierarchicalOrder`, exactly as `sheetDevelopmentGroups` does.
    - `benefit` is the entry's `benefit` verbatim. `requirements` is the entry's `requirements`. `costTags` comes from `perkCostTags(entry.id, entry.benefit)`. `isAccessRoot` is `entry.id.startsWith("access_")`.
    - `filterPerkGroups`: ACTIVE keeps cards with tags, PASSIVE keeps cards without. The query matches name, benefit or category, case-insensitively, after trimming. Empty groups are dropped.
  - **Conditions.**
    - Built-in: the description is the override description, otherwise `conditionCatalog.summary(id)`. The kind is HELPFUL for `INSPIRED` and `INVISIBILITY`, HARMFUL for all others.
    - Custom: the description is the custom condition's description, the kind is CUSTOM.
  - **Notes:** filter by title or body, case-insensitively; the stored order is kept.

- [ ] **Step 1: Write the failing tests**, using `FakeSheetHost` seeded like Шершень (DEX 8, `melee_weapon` rank 7, `athletics` rank 4 with «Атлетичность» owned, `driving` rank 0):
  - `skillsByAttributeHasEightGroupsInOrder` (CON group present with an empty `rows`)
  - `rowsSortedByBonusDescending`
  - `rankedOnlyDropsRankZero`
  - `athleticsCarriesPerkMarkAndTip`
  - `breakdownText`: equals `"Холодное оружие: 2d6 + 15 (Ловкость 8 + ранг 7)"`
  - `perkCardsKeepCustomGroupOrder`
  - `perkCardsCarryBenefitAndTags`
  - `activeFilterKeepsTaggedOnly`
  - `queryMatchesBenefitText`
  - `emptyGroupsDropped`
  - `conditionChipsCarryDescriptionAndKind` (DAZED harmful with the catalog text, INSPIRED helpful, a custom one with its own text)
  - `filterNotesMatchesBody`
- [ ] **Step 2: Run** `/home/claude/gw.sh :shared:desktopTest --tests '*SheetPlayModelsTest*'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the Step 2 tests plus the full `:shared:desktopTest` (the existing sheet tests must stay green). Expected: PASS.
- [ ] **Step 5: Commit** `feat(sheet): play models — skills by attribute, perk cards, condition details, note filter`.

### Task 5: Play header — identity, resources, conditions

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlayHeader.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetPlayHeaderLogicTest.kt`

**Interfaces:**
- Consumes: `sheetVitals`, `sheetActiveConditions`, `sheetMetrics`, `CONDITION_INLINE_CAP`, `SheetPortrait`, `vitalCanDecrease`, `sheetVitalColor`.
- Produces:
  - `@Composable fun SheetPlayHeaderWide(host: DublSheetEditHost, state: SheetScreenState, playState: SheetPlayState, onOpenEditor: () -> Unit, portrait: (@Composable () -> Unit)?, modifier: Modifier = Modifier)`
  - `@Composable fun SheetPlayHeaderCompact(host: DublSheetEditHost, state: SheetScreenState, playState: SheetPlayState, onOpenEditor: () -> Unit, portrait: (@Composable () -> Unit)?, modifier: Modifier = Modifier)`
  - `class SheetPlayState { var selectedResource: String?; var selectedCondition: String?; var conditionsExpanded: Boolean; var rankedOnly: Boolean = true; var perkFilter: SheetPerkFilter = ALL; var perkQuery: String; var perksBrief: Boolean = true; var expandedPerks: Set<String>; var noteQuery: String; var expandedNotes: Set<String>; fun syncCharacter(id: String) }`. All fields are Compose state; `syncCharacter` resets everything when the id changes.
  - `internal fun resourceUsesPips(vital: SheetVital): Boolean`: true when `vital.id != "health"` and `0 < maximum ≤ 10`.
  - `internal fun visibleConditions(chips: List<SheetConditionChip>, expanded: Boolean): Pair<List<SheetConditionChip>, Int>`: the shown chips and the hidden count.
- What each header shows (per the spec):
  - **Wide:** the identity row (portrait 52dp; name and concept on one line each with ellipsis; the 6 derived tiles in `dBg/dLine/dLabel/dText`, where tapping a tile opens its existing `SheetDialog.Stat`; the XP + ОС gold chip, which opens `SheetDialog.Economy`, with ОС in `accText` when negative; the Редактор icon button). Then a grid of resource cells with `GridCells.Adaptive(178.dp)`, with health spanning 2. Then the conditions line: the cap, «+N» / «свернуть», «+ состояние» (opens `SheetDialog.Conditions`), and the selected chip's description inline.
  - **Compact:** the name row, a full-width health row with 44dp ±, a sideways-scrolling strip of the other resource chips (a selected chip opens an inline ± row with ×), and a sideways-scrolling condition strip with the selected chip's description line under it.
  - Resource ± calls `host.changeResource` / `changeCustomResource`. A mounted meter that can be restored keeps a «восстановить» action inside its inline row.
  - Values use tabular figures and `maxLines = 1`. Labels use `maxLines = 1, overflow = Ellipsis`.

- [ ] **Step 1: Write the failing tests:**
  - `resourceUsesPips`: health → false; 3/5 → true; 37/60 → false; 0/0 → false.
  - `visibleConditions`: 8 chips collapsed gives 5 shown and 3 hidden; expanded gives 8 shown and 0 hidden; 3 chips gives 3 shown and 0 hidden.
  - `SheetPlayState.syncCharacter`: a new id clears the selection, queries and expansions; the same id keeps them.
- [ ] **Step 2: Run** `/home/claude/gw.sh :shared:desktopTest --tests '*SheetPlayHeaderLogicTest*'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** the logic and both composables. All colours come from `FuryColors.current`.
- [ ] **Step 4: Run** the Step 2 tests plus the Kotlin verification command. Expected: PASS, BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(sheet): play header — identity, resource rack/strip, condition line/strip`.

### Task 6: Умения grouped by attribute

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlaySkills.kt`

**Interfaces:**
- Consumes: `sheetSkillsByAttribute`, `sheetSkillTotals`, `SheetPlayState.rankedOnly`.
- Produces: `@Composable fun SheetPlaySkills(groups: List<SheetAttributeSkills>, totals: Pair<Int, Int>, rankedOnly: Boolean, onToggleRanked: () -> Unit, onRollAttribute: (AttributeId) -> Unit, onRollSkill: (String) -> Unit, compact: Boolean, scrollable: Boolean, modifier: Modifier = Modifier)`
- Layout:
  - **Header:** «Умения», "shown из total" when ranked-only, and the toggle «Показать все» / «Только с рангами».
  - **Group:** an attribute tile (56dp wide, 44dp minimum, label and value, `onRollAttribute`) next to the rows. With no rows, a 34dp tile beside the text «нет умений» in `dim`.
  - **Row:**
    - 44dp tall when compact, 32dp otherwise;
    - name with ellipsis;
    - optional `perkMark` chip (`goldSoft` / `gold`, with `perkTip` as tooltip and content description);
    - 10 rank bars;
    - bonus chip: `accSoft` / `accText` when ≥ 12, `hover` / `text` otherwise;
    - the tooltip / long-press shows `breakdown`.
  - `scrollable = true` puts the groups in their own `LazyColumn` (the desktop column).

- [ ] **Step 1: Wire it into a temporary preview path.** Call it from `SheetScreen`'s play branch in place of `SheetSkills` until Task 8 rebuilds that branch. Run the Kotlin verification command. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2: Screenshot** Шершень and the baron (Task 9 fixture, or the throwaway seed until then) at 1440, 1024 and 400.
  - Expected: 8 groups; empty attributes slim; Холодное оружие +15 first under ЛОВ.
  - Tapping ЛОВ opens the attribute roll; tapping a skill opens the skill roll.
- [ ] **Step 3: Commit** `feat(sheet): Умения grouped by attribute with attribute tiles`.

### Task 7: Навыки — perk cards with rules text, filters, search

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlayPerks.kt`

**Interfaces:**
- Consumes: `sheetPerkGroups`, `filterPerkGroups`, `PERK_SEARCH_THRESHOLD`, `SheetPlayState` (filter, query, brief, expanded).
- Produces: `@Composable fun SheetPlayPerks(groups: List<SheetPerkGroup>, playState: SheetPlayState, onOpenDetails: (String) -> Unit, compact: Boolean, scrollable: Boolean, modifier: Modifier = Modifier)`
- Layout:
  - **Header:** «Навыки», the count, a gold warning icon with the count of unmet requirements, and the «Подробно» / «Кратко» button.
  - **Filter chips:** «Все», «Активные N», «Пассивные N». A `FuryTextField` search appears when there are more than `PERK_SEARCH_THRESHOLD` perks in total.
  - Group titles show "title · count". Child cards are indented 18dp per `depth`, with a 2dp `accLine` left edge.
  - **Card:**
    - name with ellipsis, cost tags (`accSoft` / `accText`), the warning icon when unmet, the category (ellipsis, at most 40%), and rank pips (`gold`);
    - the benefit: 1 line when brief and not expanded, full otherwise;
    - an empty benefit shows «Нет описания в правилах — см. требования.» (or «Открывает ветку способностей.» for an access root) in `dim` italic;
    - tapping toggles expansion, which shows «Требования: …» plus a «Подробнее» text button that calls `onOpenDetails` when `opensDetails`.
  - When filtering leaves nothing, show «Ничего не найдено».

- [ ] **Step 1: Wire** it in place of `SheetDevelopment` in the play branch and run the Kotlin verification command. Expected: BUILD SUCCESSFUL.
- [ ] **Step 2: Screenshot** the baron at 1440 and 400.
  - Expected: «Активные 7»; Фехтование untagged; «Подробно» shows the full text.
  - Search «маны» leaves the magic perks; expanding Разметать shows its requirements.
- [ ] **Step 3: Commit** `feat(sheet): Навыки cards with rules text, cost tags and filters`.

### Task 8: Play layout per width + notes + roll placement

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlayNotes.kt`
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetScreen.kt` (the play branch only; the edit branch and dialogs untouched)
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetLayoutTest.kt` (extend)

**Interfaces:**
- Consumes: Tasks 4–7, `SheetCombat`, `SheetScreenRoll`, `SheetNotes` (the edit dialog for add/edit).
- Produces:
  - `@Composable fun SheetPlayNotes(notes: List<CharacterNote>, playState: SheetPlayState, host: DublSheetEditHost, scrollable: Boolean, modifier: Modifier = Modifier)`. It shows: «+ заметка» (opens the existing note editor); the search field when there are more than `NOTE_SEARCH_THRESHOLD` notes; and rows with the title plus a 1-line body, where tapping expands the row and long-press/«Изменить» opens the editor.
  - `internal fun sheetPlayColumns(layout: SheetLayout): Int`: PHONE 1, WIDE 2, WIDE_WITH_TRAY 2.
- Layout:
  - **Play mode** (`!state.editing`):
    - WIDE_WITH_TRAY: a `Column(fillMaxSize)` holds `SheetPlayHeaderWide`, then a `Row(weight(1f))` with two equal columns, `SheetPlaySkills(scrollable = true)` and `SheetPlayPerks(scrollable = true)`. The 340dp tray holds `SheetScreenRoll`, then `SheetCombat`, then `SheetPlayNotes(scrollable = true, Modifier.weight(1f))`. There is no outer scroll.
    - WIDE: `SheetPlayHeaderCompact` stays pinned. Below it one vertical scroll holds the derived stats (a 6-column row), a 2-column row of skills | perks, then a 2-column row of combat | notes.
    - PHONE: `SheetPlayHeaderCompact` stays pinned. Below it one scroll holds the derived stats (a 3×2 grid with full names), skills, perks, combat and notes.
  - **Edit mode:** exactly the current 2b body. Редактор toggles `state.updateEditing`.
  - **Roll:** unchanged. The tray shows it at WIDE_WITH_TRAY; the existing dialog shows it on PHONE and WIDE.
  - `playState` is remembered next to `state` and synced with `host.active.id`.

- [ ] **Step 1: Write the failing test** `sheetPlayColumns`: PHONE → 1, WIDE → 2, WIDE_WITH_TRAY → 2.
- [ ] **Step 2: Run** `/home/claude/gw.sh :shared:desktopTest --tests '*SheetLayoutTest*'`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** `SheetPlayNotes` and the play branch. Delete the now-unused play-only helpers in `SheetScreen.kt` (`SheetScreenChips` if nothing else calls it). Keep everything the edit branch uses.
- [ ] **Step 4: Run** the full Kotlin verification command and the Python guard. Expected: BUILD SUCCESSFUL, the guard prints nothing.
  - If a Python test asserts on removed play-mode text, retarget it to the new file the way 2b did (see the e303a06 commit body). Never weaken an FCP/contract test.
- [ ] **Step 5: Commit** `feat(sheet): play layout per width — no-scroll desktop, pinned header on tablet/phone`.

### Task 9: Dev fixture characters

**Files:**
- Create: `shared/src/desktopTest/kotlin/com/furybook/dev/SeedDevCharacters.kt`
- Delete: `shared/src/desktopTest/kotlin/com/furybook/seed/SeedShowcaseCharacter.kt` (untracked; remove the directory)
- Modify: `docs/superpowers/plans/2026-11-18-play-sheet-redesign.md`: add the one-line seed command under Global Constraints

**Interfaces:**
- Produces a `@Test fun seed()` that returns at once unless `SEED_DIR` is set. When it is set, it writes 2 characters into `$SEED_DIR/dubl-character/`:
  - **Шершень:** as in the throwaway seed (attributes, economy, 12 skills, development entries, mana rank, hp −9, endurance −1, «Ярость» 3/5, DAZED, «Отравлен», notes «Долги» and «Контакты»).
  - **The baron** «Огюст-Анри-Себастьян де Монревель д’Аржантель, Барон де Стигийский»:
    - attributes 4/6/5/5/7/6/8/9;
    - 24 skills (ranks as in the mockup's `HEAVY.skills`);
    - 6 custom resources (Ярость 3/5, Удача 2/3, Патроны 37/60, Порча 6/10, Влияние при дворе 12/20, Концентрация 1/4);
    - 7 built-in conditions (DAZZLED, POISONED, EXHAUSTION, FEAR, ENTANGLED, PRONE, INSPIRED) and a custom «Проклятие ворона»;
    - the 12 notes from the mockup;
    - at least 20 owned development entries spanning regular, special, martial and magic, chosen by catalog id from the names in `/tmp/perks.json` / the mockup.
- Command: `rm -rf /tmp/seedhome && SEED_DIR=/tmp/seedhome /home/claude/gw.sh :shared:desktopTest --tests '*SeedDevCharacters*'`

- [ ] **Step 1: Write the fixture.** Run the command, then run a normal `:shared:desktopTest` without `SEED_DIR`. Expected: both pass; the seeded files exist only when `SEED_DIR` is set.
- [ ] **Step 2: Commit** `test(dev): seedable Шершень and heavy baron fixtures`.

### Task 10: Visual pass and CI

**Files:** whatever the pass finds.

- [ ] **Step 1: Screenshot matrix under Xvfb** (seeded home): {Шершень, baron} × {1440×900, 1024×768, 400×844} × {Ярость, Пергамент}, plus Неон, Пустошь and Графит at 1440 for the baron. Expected:
  - nothing overflows;
  - the desktop has no page scroll;
  - all text is readable on Пергамент;
  - the other screens (Умения, Навыки, Магия, Снаряжение, Персонажи) also repaint in Неон and Пергамент.
- [ ] **Step 2: Run** the full Kotlin verification command and the Python guard; push `ui-revamp` with `git -c push.negotiate=true push origin ui-revamp`. Watch the 5 CI workflows with `gh api` (REST). Expected: all green, Android included.
- [ ] **Step 3: Send the user** the screenshot set and the CI status.
