# UI Revamp Stage 2a — Sheet (play view), roll tray and app shell

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One shared, adaptive character sheet for play (vitals, attributes, metrics, conditions, trained skills, combat actions, rolls) plus the new navigation shell, live on Android, Desktop and Web.

**Architecture:** The sheet lives in `shared` under `com.furybook.dubl.ui.sheet` and talks to the app only through a small `DublSheetHost` interface. Desktop and Web `DesktopAppState` implement it directly; Android gets a thin `AndroidSheetHost` adapter over `CharacterController` plus its catalogs. Rolls are driven by a pure `SheetRollSession` that calls the existing shared roll engine (`rollPreset`, `rollCheck`, `rollFollowUp`, `compareRollToTarget`, `resolveAttack`, `FlurryAttackRule.resolve`, `SkillEffectRules`) — no rule is re-implemented.

**Tech Stack:** as stage 1 (Kotlin 2.4.20, Compose Multiplatform 1.12.0, Material3 1.12.0-alpha03).

**Spec:** `docs/superpowers/specs/2026-11-17-ui-revamp-design.md`. Stage 1 plan for the components used here: `docs/superpowers/plans/2026-11-17-ui-revamp-stage-1-design-system.md`.

## Scope split (ruling)

The spec's stage 2 ("Sheet + roll tray") is split:
- **2a (this plan):** play view, roll flow, shell. The **«Правка»** button opens the *old* sheet screen for that platform, so every editing feature keeps working unchanged.
- **2b (next plan):** new edit mode and all sheet dialogs (identity, XP, maximums, custom resources, visibility, conditions editor, groups, notes, development panel, portrait), then deletion of the old sheet code.

## Global Constraints

- Rules, formulas, persistence, snapshot schema, transfer format, cloud protocol: untouched. No edits under `com.furybook.dubl.model`, `.application`, `.data`, `.state`, `com.furybook.core`.
- `shared/commonMain` never imports `com.furybook.android.*` or `com.furybook.desktop.*`.
- UI uses stage 1 components and tokens only (`Fury*`, `Dubl*` colours, `furyOverlineStyle()`); no new hard-coded colours.
- Text on filled surfaces ≥ 4.5:1; touch targets ≥ 44dp.
- Layout breakpoint for the sheet: `widthDp < 600` → phone layout (single column, bottom tabs, roll as bottom sheet); otherwise wide layout (top tabs, docked roll tray on the right when `widthDp ≥ 1100`, roll as centred dialog below that).
- Every roll the old sheets can make, the new sheet can make with the same inputs: attribute, skill (with attribute choice and skill-effect toggles), fortitude, reflexes, initiative, run, and all 13 combat `RollContext`s incl. flurry profile and target/defence; follow-up dice; advantage/hindrance with effect count; situational modifier.
- Local verification: `/home/claude/gw.sh :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs` and the Python suite against `/tmp/pytest-baseline.txt` (37 failed, 6 errors before stage 1; may only shrink). Android compiles in CI only.

## Review Focus

1. **Character with 0 trained skills / no conditions / mana disabled.** Expect empty-state text, no blank cards, no mana bar. Pinned in Task 3.
2. **Advantage and hindrance effects both present** (the old dialog silently refused to roll). Expect the roll button disabled with a one-line reason. Pinned in Task 2.
3. **Phone width with long names** ("Огюст-Анри-Себастьян де Монревель д’Аржантель, Барон де Стигийский", a real character in the owner's data). Expect two lines max, ellipsis. Pinned in Task 4.
4. **Hidden resources** (`extras.hiddenResources`) and content-pack meters (Chi). Expect hidden ones absent, Chi present only when its pack is on. Pinned in Task 3.
5. **Undo after a resource change on the new sheet.** Expect the existing undo toast/behaviour on each platform still to fire. Pinned in Task 6.

---

### Task 1: `DublSheetHost` and its three implementations

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/DublSheetHost.kt`
- Create: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/FakeSheetHost.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt`, `webApp/src/jsMain/kotlin/com/furybook/desktop/DesktopAppState.kt` (add `: DublSheetHost`, `override` on matching members, add missing ones as one-line delegates)
- Create: `app/src/main/java/com/furybook/android/ui/AndroidSheetHost.kt`
- Test: `tools/tests/test_sheet_host_contract.py`, `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/FakeSheetHostTest.kt`

**Interfaces:**
- Produces:
```kotlin
interface DublSheetHost {
    val active: DublCharacter
    val extras: CharacterSheetExtras
    val canUndo: Boolean
    val conditionCatalog: ConditionCatalog
    val developmentCatalog: DevelopmentCatalog
    val skillEffectCatalog: SkillEffectCatalog
    fun resourceMeters(): List<DublResourceMeterModel>
    fun changeResource(resource: CharacterSheetResourceId, delta: Int)
    fun changeCustomResource(uid: String, delta: Int)
    fun restoreMountedResource(resource: CharacterSheetResourceId)
    fun toggleCondition(condition: CharacterConditionId)
    fun setCustomConditionActive(id: String, active: Boolean)
    fun undoLast(): Boolean
}
```
- `FakeSheetHost(character: DublCharacter, extras: CharacterSheetExtras = CharacterSheetExtras(...defaults))` in commonTest: a real `DublApplication` over `InMemoryCharacterStore` and an in-memory extras store (copy the shape of `SharedApplicationGoldenTest.GoldenFixture`), catalogs loaded the way `DublFcpCatalogLoaderTest` loads them, `resourceMeters()` from `DublUiRenderModels.resourceMeters` with the core composition.
- `AndroidSheetHost(controller: CharacterController, context: Context, chiPackEnabled: Boolean)`; catalogs from `ConditionCatalogRepository`, `DevelopmentCatalogRepository`, `SkillEffectCatalogRepository`; `resourceMeters()` = `AndroidContentPackState.resourceMeterModels(context, chiPackEnabled, controller.active)`. Remember it with those three keys.

- [ ] **Step 1: Write the failing tests.** `test_sheet_host_contract.py`: each of the two `DesktopAppState.kt` files declares `: DublSheetHost` and `AndroidSheetHost.kt` declares `class AndroidSheetHost(` and `) : DublSheetHost`; `DublSheetHost.kt` contains every member name above. `FakeSheetHostTest`: `changeResource(HEALTH, -3)` lowers `active.hpCurrent` by 3 and `canUndo` becomes true; `undoLast()` restores it.
- [ ] **Step 2: Run** → both fail.
- [ ] **Step 3: Implement.** Where a platform lacks a member, add it as a delegate to `application.*` (never new logic).
- [ ] **Step 4: Run** Python + `:shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs` → pass; Python suite vs baseline.
- [ ] **Step 5: Commit** — `feat(sheet): DublSheetHost boundary for the shared sheet`

### Task 2: `SheetRollSession` — pure roll flow

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetRollSession.kt`
- Modify: `shared/src/commonMain/kotlin/com/furybook/ui/components/FuryRollTray.kt` (`FuryRollView` gains `keptDice: Set<Int> = dice.indices.toSet()` and `specialResult: String? = null`)
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetRollSessionTest.kt`

**Interfaces:**
- Consumes: `DublSheetHost` (Task 1); shared model roll API.
- Produces:
```kotlin
sealed interface SheetRollSubject {
    data class Attribute(val id: AttributeId) : SheetRollSubject
    data class Skill(val skillId: String) : SheetRollSubject
    data class Context(val context: RollContext) : SheetRollSubject   // FORTITUDE…BREAK_ITEM
}
data class SheetRollRequest(
    val subject: SheetRollSubject,
    val skillId: String? = null,            // chosen skill for contexts that allow several
    val attribute: AttributeId? = null,     // chosen attribute when the skill/context allows several
    val mode: RollMode = RollMode.NORMAL,
    val manualEffectCount: Int = 1,         // 1..9, used only when mode != NORMAL
    val selectedEffectIds: Set<String> = emptySet(),
    val situational: Int = 0,               // -99..99
    val target: Int? = null,                // СЛ, or defence for ATTACK/FLURRY
    val flurryProfile: FlurryWeaponProfile = FlurryWeaponProfile.LIGHT,
    val shortFlurry: Boolean = false,
)
data class SheetRollOptions(
    val title: String,
    val skillChoices: List<String>,         // empty when fixed
    val attributeChoices: List<AttributeId>,// empty when fixed
    val effectToggles: List<Pair<String, String>>, // id to label, from SkillEffectRules
    val preset: CharacterRollPreset,
    val blockedReason: String?,             // e.g. preset unavailable, or both advantage and hindrance
)
fun sheetRollOptions(host: DublSheetHost, request: SheetRollRequest): SheetRollOptions
fun performSheetRoll(host: DublSheetHost, request: SheetRollRequest, rollDie: () -> Int = ::rollD6): RollResult?   // null when blocked
fun followUpSheetRoll(result: RollResult, rollDie: () -> Int = ::rollD6): RollResult
fun sheetRollView(options: SheetRollOptions, request: SheetRollRequest, result: RollResult): FuryRollView
```
- Effect-count, mode and bonus arithmetic: copy the logic from `desktopApp/.../screens/RollDialog.kt` lines 228–250 (skill) and 395–405 (context) **verbatim in meaning**; `outcome` text = `compareRollToTarget`, `resolveAttack` (ATTACK) or `FlurryAttackRule.resolve(...).summary` (FLURRY), as the desktop dialog shows them (lines 205–215, 368–380). `terms` = `preset.contributions` (+ skill effects' automatic/toggled bonus as one term "Эффекты", + situational as "Поправка").

- [ ] **Step 1: Write the failing test** (deterministic dice via `rollDie = sequence(…)::next`):
  - `attributeRollUsesPresetBonus`: Шершень-like character (DEX 8); `Attribute(DEXTERITY)`, dice 4,5 → total = 9 + preset bonus; `terms` lists "Ловкость 8".
  - `advantageWithTwoEffectsRollsFourDiceAndKeepsBestTwo`: dice 1,6,2,5 → `keptDice` = indices of 6 and 5.
  - `advantageAndHindranceTogetherIsBlocked`: a selected effect granting hindrance + `mode = ADVANTAGE` → `blockedReason != null`, `performSheetRoll` returns null.
  - `attackAgainstDefenceReportsOutcome`: `Context(ATTACK)`, skill `melee_weapon`, target 20 → `outcome` equals `resolveAttack(result, 20)` rendered text.
  - `flurryUsesProfileRule`: `Context(FLURRY)`, profile LIGHT, target 15 → `outcome == FlurryWeaponProfile.LIGHT.rule(false).resolve(result, 15).summary`.
  - `followUpAddsDie`: a double result with `followUp != null` → `followUpSheetRoll` adds one die.
- [ ] **Step 2: Run** → fail. **Step 3: Implement.** **Step 4: Run** → pass. **Step 5: Commit** — `feat(sheet): pure roll session over the shared roll engine`

### Task 3: Sheet play models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetModels.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetModelsTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class SheetVital(val id: String, val label: String, val current: Int, val maximum: Int,
    val color: SheetVitalColor, val resource: CharacterSheetResourceId?, val customUid: String?, val restoreable: Boolean)
enum class SheetVitalColor { HEALTH, STAMINA, MANA, CUSTOM }
data class SheetStat(val label: String, val value: Int, val roll: SheetRollSubject?)
data class SheetSkillRow(val skillId: String, val name: String, val attributeLabel: String, val rank: Int, val bonus: Int)
data class SheetSkillGroup(val title: String, val rows: List<SheetSkillRow>)
data class SheetConditionChip(val key: String, val title: String, val active: Boolean, val custom: Boolean)
fun sheetVitals(host: DublSheetHost): List<SheetVital>        // health, endurance, mana(if enabled), mounted meters, custom; minus extras hidden
fun sheetAttributes(character: DublCharacter): List<SheetStat> // 8, order: STR DEX CON SPD INT PER WIL CHA, each rollable
fun sheetMetrics(character: DublCharacter): List<SheetStat>    // Защита, Рефлексы*, Инициатива*, Стойкость*, Бег*, Размер (*rollable via Context)
fun sheetSkillGroups(host: DublSheetHost): List<SheetSkillGroup> // trained skills only, groups/order exactly as desktop SheetSkillsPanel
fun sheetActiveConditions(host: DublSheetHost): List<SheetConditionChip>
val SHEET_COMBAT_CONTEXTS: List<RollContext>                   // the 13 from CombatActionsPanel, same order
```
- Labels and membership: copy from desktop `HeroResources`, `HeroCharacteristicsStrip`, `HeroMetricsStrip`, `SheetSkillsPanel`, `HeroConditions`, `CombatActionsPanel` in `CharacterSheetScreen.kt`. Skill bonus = the value those rows display today (same helper call).

- [ ] **Step 1: Write the failing tests:** `noTrainedSkillsGivesNoGroups`; `manaHiddenWhenDisabled`; `hiddenResourceIsOmitted` (`extras` with HEALTH hidden); `attributesInSheetOrder`; `metricsMarkRollableOnes` (Защита and Размер have `roll == null`); `combatContextsAreThirteenInPanelOrder`; `activeConditionsIncludeCustom`.
- [ ] **Step 2–4:** fail → implement → pass. **Step 5: Commit** — `feat(sheet): pure play-view models`

### Task 4: Sheet play composables

**Files:**
- Create in `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/`: `SheetHeader.kt`, `SheetVitals.kt`, `SheetStats.kt`, `SheetSkills.kt`, `SheetCombat.kt`, `SheetRollPanel.kt`
- Test: `tools/tests/test_sheet_play_components.py`

**Interfaces:**
- Consumes: Task 2–3 models; stage 1 components.
- Produces (all stateless):
  - `SheetHeader(name, concept, xpFree: Int, xpTotal: Int, abilityText: String, conditions: List<SheetConditionChip>, compact: Boolean, onToggleCondition: (SheetConditionChip) -> Unit, onEdit: () -> Unit, modifier)` — name in `headlineMedium` (`headlineSmall` when compact), `maxLines = 2`, ellipsis; XP chip gold; «Правка» `FuryButton` Secondary.
  - `SheetVitals(vitals: List<SheetVital>, compact: Boolean, onChange: (SheetVital, Int) -> Unit, onRestore: (SheetVital) -> Unit, modifier)` — per vital: overline label, `current / maximum`, −/+ `FuryIconButton`s labelled «Уменьшить …»/«Увеличить …», `FuryBar`; «Восстановить» ghost button when `restoreable`.
  - `SheetStatGrid(stats: List<SheetStat>, columns: Int, compact: Boolean, onRoll: (SheetRollSubject) -> Unit, modifier)` — `FuryTile`, clickable only when `roll != null`.
  - `SheetSkills(groups: List<SheetSkillGroup>, columns: Int, onRoll: (String) -> Unit, onOpenAll: () -> Unit, modifier)` — rows as in the B mockup (name + "attr · ранг N", bonus block on `FuryAccentFill`); empty state «Нет изученных умений».
  - `SheetCombat(contexts: List<RollContext>, onRoll: (RollContext) -> Unit, modifier)` — wrap row of Secondary buttons.
  - `SheetRollPanel(host: DublSheetHost, request: SheetRollRequest, onRequestChange: (SheetRollRequest) -> Unit, result: RollResult?, onRoll: () -> Unit, onFollowUp: () -> Unit, history: List<FuryRollHistoryEntry>, modifier)` — `FuryRollTray` with `controls` = mode `FuryChipGroup` (Обычный/Преим./Помеха), effect-count `FuryStepper` (1..9, shown when mode ≠ NORMAL), situational stepper (−99..99), target `FuryTextField` (label «Защита цели» for ATTACK/FLURRY else «Цель / СЛ»), skill and attribute `FuryChipGroup`s when `options` offer choices, effect `FuryMultiChipGroup`, flurry profile chips + «Короткий» toggle for FLURRY; follow-up `FuryButton` OnAccent with `RollFollowUp.buttonTitle`; blocked reason shown instead of the roll button. `FuryRollTray` renders dropped dice (not in `keptDice`) at 40% alpha and `specialResult` as a line under the total.

- [ ] **Step 1: Write the failing test** (source contract): each file exists; `SheetHeader.kt` has `maxLines = 2`; `SheetRollPanel.kt` references `RollMode.ADVANTAGE`, `FlurryWeaponProfile`, `followUp`, `blockedReason`; no file under `ui/sheet/` contains `Color(0x`.
- [ ] **Step 2–4:** fail → implement → pass (+ shared compile). **Step 5: Commit** — `feat(sheet): play-view composables`

### Task 5: `SheetScreen` — adaptive layout

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetScreen.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetLayoutTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class SheetLayout { PHONE, WIDE, WIDE_WITH_TRAY }
fun sheetLayoutFor(widthDp: Int): SheetLayout   // <600 PHONE, <1100 WIDE, else WIDE_WITH_TRAY
@Composable fun SheetScreen(host: DublSheetHost, onEdit: () -> Unit, onOpenSkills: () -> Unit, modifier: Modifier = Modifier)
```
- Order: header → vitals → metrics → attributes → skills → combat. PHONE: metrics 5 columns compact (stat labels abbreviated as in the B phone mockup), attributes 4×2 compact, skills 1 column, roll in `FuryDialogScaffold` (sheet). WIDE: attributes 4 columns, skills 2 columns, roll in `FuryDialogScaffold` (centred). WIDE_WITH_TRAY: content left, `SheetRollPanel` docked right at 340dp, always visible (empty text until first roll). Roll history: last 5, kept in screen state, cleared when `host.active.id` changes. Content scrolls; header stays at top on PHONE.

- [ ] **Step 1: Write the failing test:** `sheetLayoutFor(390) == PHONE`, `(599) == PHONE`, `(600) == WIDE`, `(1099) == WIDE`, `(1100) == WIDE_WITH_TRAY`.
- [ ] **Step 2–4:** fail → implement → pass (+ desktop and web compile). **Step 5: Commit** — `feat(sheet): adaptive SheetScreen`

### Task 6: Shell and wiring on all three platforms

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/components/FuryShell.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt`, `webApp/src/jsMain/kotlin/com/furybook/web/Main.kt`, `app/src/main/java/com/furybook/android/ui/DublApp.kt`
- Test: `tools/tests/test_sheet_wiring.py`, `shared/src/commonTest/kotlin/com/furybook/ui/components/FuryShellPolicyTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class FuryShellSection<T>(val value: T, val label: String, val primary: Boolean)   // primary = shown as a bottom tab on phones
fun <T> furyBottomTabs(sections: List<FuryShellSection<T>>): List<FuryShellSection<T>>  // primary ones, at most 5
fun <T> furyMenuSections(sections: List<FuryShellSection<T>>): List<FuryShellSection<T>> // the rest
@Composable fun <T> FuryShell(title: String, characterName: String, sections: List<FuryShellSection<T>>, selected: T,
    onSelect: (T) -> Unit, onSwitchCharacter: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit)
```
- Wide: top bar = «FURY BOOK» wordmark, `FuryTopTabs` of all sections, character button on the right. Phone: content + `FuryBottomTabs` (Лист, Умения, Навыки, Магия, Вещи); menu `FuryIconButton` «Меню» in the sheet header area opens a `FuryDialogScaffold` listing Кампания, Персонажи/Ещё.
- Each platform: replace its section navigation with `FuryShell`, keep the same section enum and destinations. The sheet section shows `SheetScreen(host, onEdit = { editing = true }, onOpenSkills = { select(SKILLS) })`; while `editing`, show the platform's old sheet (`CharacterSheetScreen` / `OverviewScreen`) with a «Готово» `FuryButton` Primary above it that sets `editing = false`. Undo toast/handling stays where it is today on each platform and must still fire after a change made on the new sheet.

- [ ] **Step 1: Write the failing tests.** `FuryShellPolicyTest`: 7 Android sections with 5 primary → 5 bottom tabs in order, 2 menu sections. `test_sheet_wiring.py`: each of the three files calls `FuryShell(` and `SheetScreen(` and still references the old sheet composable inside an `editing` branch; Desktop `Main.kt` no longer defines `CompactNavigation(`.
- [ ] **Step 2–4:** fail → implement → pass (+ desktop/web compile; launch desktop under Xvfb at 1360×900 and 400×844 and look at both screenshots). **Step 5: Commit** — `feat(shell): Fury shell and shared sheet on Android, Desktop and Web`

### Task 7: Migrate UI-source tests this stage made stale

- [ ] **Step 1:** Run the Python suite; list every test newly failing vs `/tmp/pytest-baseline.txt`.
- [ ] **Step 2:** For each: if it pins a *behaviour* that still exists (e.g. "the sheet offers a flurry roll", "desktop navigation has Кампания"), rewrite its assertions against the new shared files. If it pins only old styling/structure of code that the new sheet replaced (sidebar layout, old hero spacing), delete that test function and name it in the commit body. Never weaken a rules/persistence/parity test.
- [ ] **Step 3:** Suite failures ⊆ baseline. **Step 4: Commit** — `test: retarget sheet and navigation UI contracts at the shared sheet`

### Task 8: CI and hand-over

- [ ] Push `ui-revamp`; PR #32 runs all workflows. Expected: all green; fix compile errors without touching rules code.
- [ ] Send the owner desktop screenshots (wide and phone width) and the Android APK / Linux AppImage links.
