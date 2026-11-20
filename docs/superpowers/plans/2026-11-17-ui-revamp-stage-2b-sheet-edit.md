# UI Revamp Stage 2b — Sheet edit mode, sheet dialogs, old sheet removal

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The shared sheet does everything the old per-platform sheets did — play and edit — so the old desktop, web and Android sheet code can be deleted.

**Architecture:** `DublSheetHost` (2a) gains a sibling `DublSheetEditHost : DublSheetHost` with the edit operations; the three platform hosts implement it as one-line delegates. Pure models (`com.furybook.dubl.ui.sheet`) feed stateless dialogs built from stage 1 components. `SheetScreen` gets a Play/Edit switch: «Правка» flips the same screen into edit mode instead of opening the old sheet. Platform-only pieces stay in the shells and enter through callbacks/slots: portrait picking, and the development details dialog (owned by the Навыки screen until stage 4 rebuilds it).

**Tech Stack:** as stage 2a (Kotlin 2.4.20, Compose Multiplatform 1.12.0, Material3 1.12.0-alpha03).

**Spec:** `docs/superpowers/specs/2026-11-17-ui-revamp-design.md`. Feature inventory: `docs/superpowers/plans/2026-11-17-ui-revamp-stage-2b-sheet-inventory.md` (binding: every unchecked line must be covered). Prior stage: `docs/superpowers/plans/2026-11-17-ui-revamp-stage-2a-sheet-play.md`.

## Global Constraints

- Everything in stage 2a's Global Constraints still holds (rules packages untouched, no android/desktop imports in commonMain, stage 1 components and tokens only, ≥ 4.5:1 text on filled surfaces, ≥ 44dp targets, breakpoints 600 / 1100, same local verification commands and Python baseline).
- No feature in the inventory is dropped. Where desktop and Android differ, the new sheet offers the union (e.g. notes and portrait removal on Android, stat formula breakdown and group reset on desktop).
- Dialogs follow the spec: title + one-line context, chips instead of checkbox lists, steppers for ranks/amounts, full-width fields, destructive action on the left, one filled primary button. All dialogs use `FuryDialogScaffold`.
- Numeric fields accept typing; blank means "formula/auto" wherever the old dialog did (maximum overrides, ability-points override).
- Edit operations go through the host only; no new mutation logic on any platform (one-line delegates to `application.*` are fine).
- Undo: every change the application records for undo shows the platform's existing undo toast/bar (extend `SheetChangeReportingHost`, don't add a second undo path).

## Review Focus

1. **Damage past zero.** 20 damage at 12 HP → health −8, shown as −8, bar empty, critical styling. Pinned in Task 3.
2. **Blank and junk input.** Empty name → «Новый персонаж»; blank maximum → formula; "abc" or "-" in a number field → save disabled, no crash. Pinned in Tasks 3 and 4.
3. **Group manager on touch and mouse.** Long-press drag reorders a group and a parent with its children; a drag dropped outside the list changes nothing. Pinned in Task 6.
4. **Leaving edit mode mid-dialog / switching character in edit mode.** Dialog closes, nothing is written for the old character to the new one, edit mode resets. Pinned in Task 8.
5. **Portrait round trip per platform.** Desktop file copy, web data URL, Android persistable URI; remove works everywhere; a broken path shows the placeholder, not a crash. Pinned in Task 4.

---

### Task 1: `DublSheetEditHost` and three implementations

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/DublSheetEditHost.kt`
- Modify: `shared/src/commonTest/.../ui/sheet/FakeSheetHost.kt` (implements the edit host), both `DesktopAppState.kt`, `app/.../ui/AndroidSheetHost.kt`, `app/.../state/CharacterController.kt` (missing delegates only)
- Test: `shared/src/commonTest/.../ui/sheet/FakeSheetEditHostTest.kt`, `tools/tests/test_sheet_host_contract.py` (extend)

**Interfaces:**
- Produces:
```kotlin
interface DublSheetEditHost : DublSheetHost {
    fun setIdentity(name: String, concept: String, size: Int, legs: Int, manaEnabled: Boolean)
    fun setEconomy(total: Int, creation: Int, adjustment: Int, abilityPointsOverride: Int?)
    fun completeCreation(); fun reopenCreation()
    fun changeAttribute(id: AttributeId, delta: Int)
    fun setHealthMaximumOverride(value: Int?); fun setEnduranceMaximumOverride(value: Int?); fun setManaMaximumOverride(value: Int?)
    fun addCustomResource(name: String, maximum: Int, current: Int): String?
    fun updateCustomResource(uid: String, name: String, current: Int, maximum: Int)
    fun removeCustomResource(uid: String)
    fun resourceToggleModels(): List<DublResourceToggleModel>
    fun setResourceHidden(resource: CharacterSheetResourceId, hidden: Boolean)
    fun setConditionOverride(condition: CharacterConditionId, title: String, description: String)
    fun resetConditionOverride(condition: CharacterConditionId)
    fun addCustomCondition(title: String, description: String, active: Boolean)
    fun updateCustomCondition(id: String, title: String, description: String)
    fun removeCustomCondition(id: String)
    fun setSkillGroups(groups: List<SheetGroup>); fun setDevelopmentGroups(groups: List<SheetGroup>)
    fun addNote(title: String, body: String); fun updateNote(id: String, title: String, body: String); fun removeNote(id: String)
    fun setPortrait(uri: String?)
}
```
  Exact parameter types: copy from the existing `DesktopAppState` members of the same name (they win over this sketch if they differ; the brief's implementer adjusts the interface to match and notes it). Android: `setIdentity`/`setEconomy` are new one-line `CharacterController` delegates to `application.character.*`.

- [ ] **Step 1: Failing tests.** `FakeSheetEditHostTest`: `setIdentity("", …)` stores «Новый персонаж» only if the application does so today (assert whatever `DublApplication` does — no new rule); `changeAttribute(STRENGTH, +1)` raises it and `canUndo`; `addCustomResource("Ярость", 5, 5)` returns a uid that `active` lists; `addNote("A","b")` appears in `extras.displayNotes()`. Python: each host file declares `DublSheetEditHost`; interface contains every member name above.
- [ ] **Step 2: Run** → fail. **Step 3: Implement** (delegates only). **Step 4: Run** `/home/claude/gw.sh :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs` + Python vs baseline → pass.
- [ ] **Step 5: Commit** — `feat(sheet): edit host boundary`

### Task 2: Pure edit-view models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetEditModels.kt`, `SheetStatBreakdown.kt`
- Test: `shared/src/commonTest/.../ui/sheet/SheetEditModelsTest.kt`, `SheetStatBreakdownTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class SheetEconomyView(val total: Int, val creation: Int, val adjustment: Int, val abilityPointsOverride: Int?,
    val lines: List<Pair<String, Int>>, val remaining: Int, val recommendedAbilityPoints: Int?, val selfTaughtNote: String?,
    val unpricedSpellsWarning: String?, val creationComplete: Boolean)
fun sheetEconomyView(host: DublSheetHost): SheetEconomyView                      // CharacterEconomy.breakdown(…, includeChi = host.chiPackEnabled)
data class SheetStatBreakdown(val title: String, val formula: String, val parts: List<Pair<String, String>>)
fun sheetStatBreakdown(character: DublCharacter, stat: SheetStatId): SheetStatBreakdown   // port of Android OverviewScreen statInfo(), same shared helpers
enum class SheetStatId { DEFENSE, REFLEXES, INITIATIVE, FORTITUDE, RUN, SIZE }
data class SheetDevelopmentRow(val entryId: String, val name: String, val rankLabel: String, val depth: Int, val requirementsUnmet: Boolean, val opensDetails: Boolean)
data class SheetDevelopmentGroup(val id: String, val title: String, val collapsed: Boolean, val rows: List<SheetDevelopmentRow>)
fun sheetDevelopmentGroups(host: DublSheetHost): List<SheetDevelopmentGroup>      // DevelopmentRules(...).ownedSheetSections(), hierarchy as desktop SheetDevelopmentPanel
fun sheetSkillGroupsForEdit(host: DublSheetHost): List<SheetSkillGroup>           // all visible skills incl. rank 0 (desktop), hidden kept in membership
fun healthCriticalLevel(character: DublCharacter): Int                            // move from Android OverviewScreen as-is if pure; else wrap
```
- `SheetSkillGroup` (2a) gains `id: String` and `collapsed: Boolean` (defaults keep 2a callers compiling); `SheetVitalColor` gains `MOUNTED` with the pack's presentation accent token (`fcpAccentColor` logic moved to shared if it is pure).

- [ ] **Step 1: Failing tests:** `economyIncludesChiOnlyWhenPackOn`; `recommendedAbilityPointsMatchesEconomy`; `statBreakdownRunListsBaseMultiplierAndRunner` (values equal to the Android `statInfo` output for the same character — copy one expectation from the Android code path); `statBreakdownDefenseIncludesLoadPenalty`; `developmentRowsKeepParentChildDepth`; `unmetRequirementFlagsRow`; `editSkillGroupsIncludeRankZero`; `collapsedGroupKeepsRowsButFlagsCollapsed`.
- [ ] **Step 2–4:** fail → implement → pass. **Step 5: Commit** — `feat(sheet): edit-view models and stat breakdown`

### Task 3: Play-mode dialogs — vital control, stat info, conditions

**Files:**
- Create in `shared/.../dubl/ui/sheet/`: `SheetVitalDialog.kt`, `SheetStatDialog.kt`, `SheetConditionsDialog.kt`
- Modify: `SheetVitals.kt` (tap a vital opens its dialog; ± at bounds disabled except health −, which may go negative), `SheetStats.kt` (Защита/Размер tiles open stat info; rollable tiles get an info affordance), `SheetHeader.kt` (chip tap opens the conditions dialog; «+ Состояние» chip)
- Test: `tools/tests/test_sheet_dialogs.py`, `shared/src/commonTest/.../ui/sheet/SheetVitalDialogLogicTest.kt`

**Interfaces:**
- Consumes: Task 1 host, Task 2 models.
- Produces: `SheetVitalDialog(vital: SheetVital, host: DublSheetEditHost, onDismiss)` — amount `FuryStepper` + typed field (default 1), «Получить урон», «Лечение», «Восстановить всё» (health: `max - current`), «Изменить максимум» (health/endurance/mana; blank = formula), custom resources: name/max/current edit + «Удалить» on the left. Pure helper `vitalAmountDelta(action: VitalAction, amount: Int, vital: SheetVital): Int` holds the arithmetic. `SheetStatDialog(breakdown, onRoll: (() -> Unit)?, sizeLegsEditor: Boolean, host, onDismiss)` (size 1..10, legs ≥ 2 steppers inside Размер). `SheetConditionsDialog(host, onDismiss)` — standard conditions as chips with descriptions, automatic Слабость shown locked, per-condition override editor + «К рулбуку», custom conditions add/edit/remove/activate.

- [ ] **Step 1: Failing tests:** `damageBelowZeroKeepsNegative` (`vitalAmountDelta(DAMAGE, 20, hp 12/30) == -20` and host result −8); `restoreAllIsMaxMinusCurrent`; `junkAmountDisablesActions` (parse helper returns null for "abc", "-", ""); Python: dialogs use `FuryDialogScaffold`, no `Color(0x`, conditions dialog references `setConditionOverride`, `resetConditionOverride`, `addCustomCondition`.
- [ ] **Step 2–4:** fail → implement → pass (+ compile). **Step 5: Commit** — `feat(sheet): vital, stat and condition dialogs`

### Task 4: Identity, XP and portrait

**Files:**
- Create: `shared/.../ui/sheet/SheetIdentityDialog.kt`, `SheetEconomyDialog.kt`, `SheetPortrait.kt`
- Modify: `SheetHeader.kt` (portrait slot, name tap → identity dialog in edit mode, XP chip → economy dialog in both modes), platform shells (portrait picker callback)
- Test: `shared/src/commonTest/.../ui/sheet/SheetFormParsingTest.kt`, `tools/tests/test_sheet_identity_portrait.py`

**Interfaces:**
- Produces: `SheetIdentityDialog(host, onDismiss)` (name, concept, size stepper 1..10, legs stepper ≥ 2, mana switch); `SheetEconomyDialog(view: SheetEconomyView, host, onDismiss)` (four typed fields, breakdown lines, warnings, complete/reopen creation as a secondary action); `SheetPortrait(uri: String?, loader: SheetPortraitLoader, onPick: (() -> Unit)?, onRemove: (() -> Unit)?)`; `fun interface SheetPortraitLoader { @Composable fun load(uri: String): ImageBitmap? }` supplied per platform (desktop file, web data URL, Android content URI); `SheetScreen` gains `portraitLoader: SheetPortraitLoader` and `onPickPortrait: (() -> Unit)?` params. Pickers stay in platform code: desktop `pickPortraitFile()` + `importPortrait`, web `pickPortraitDataUrl`, Android `OpenDocument` + `takePersistableUriPermission`.
- Form parsing: `parseIntField(text: String): Int?`, `parseOptionalIntField(text: String): OptionalInt` (`Blank` / `Value(n)` / `Invalid`) — save disabled on `Invalid`.

- [ ] **Step 1: Failing tests:** `blankOverrideIsAuto`, `junkIsInvalid`, `negativeAllowedWhereOldDialogAllowedIt` (XP adjustment); Python: each platform passes a loader and a picker to `SheetScreen(`; `setPortrait(null)` reachable.
- [ ] **Step 2–4:** fail → implement → pass; desktop screenshot with a portrait. **Step 5: Commit** — `feat(sheet): identity, XP and portrait`

### Task 5: Resources editing — custom resources, visibility, maximums

**Files:**
- Create: `shared/.../ui/sheet/SheetResourcesDialog.kt`
- Modify: `SheetVitals.kt` (edit mode: «Добавить ресурс», «Показать / скрыть»; mounted accent from Task 2)
- Test: `tools/tests/test_sheet_resources.py`, `shared/src/commonTest/.../ui/sheet/SheetResourcesLogicTest.kt`

**Interfaces:**
- Produces: `SheetResourcesDialog(host, onDismiss)` — visibility chips for health/endurance/mana + `resourceToggleModels()`, list of custom resources with edit/remove, «Добавить ресурс» form (name, maximum, current ≤ maximum). Vital dialog (Task 3) reuses the same custom-resource form composable.

- [ ] **Step 1: Failing tests:** `hidingHealthRemovesItFromVitals` (via host + `sheetVitals`); `customCurrentClampedToMaximumInForm`; Python: references `setResourceHidden`, `addCustomResource`, `removeCustomResource`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(sheet): resource visibility and custom resources`

### Task 6: Group manager (skills and development)

**Files:**
- Create: `shared/.../ui/sheet/SheetGroupManager.kt`, `SheetGroupDrag.kt` (pure drag-target arithmetic)
- Test: `shared/src/commonTest/.../ui/sheet/SheetGroupDragTest.kt`, `tools/tests/test_sheet_group_manager.py`

**Interfaces:**
- Produces: `SheetGroupManager(kind: SheetGroupKind, host: DublSheetEditHost, onDismiss)` with `enum class SheetGroupKind { SKILLS, DEVELOPMENT }`. Long-press (touch) or press-drag on a handle (mouse) reorders groups (`SheetGroupingRules.moveGroupToIndex`) and items incl. subtree blocks (`moveItems`, `subtreeBlock`); rename, delete (not "ungrouped"), create (`addGroup` with id `"custom:" + randomId()` — `randomId` from a shared helper, no platform UUID), collapse, «По умолчанию» (`emptyList()` → defaults). Development tree roots = special/martial-art entries as Android does. Autoscroll near edges.
- Pure: `fun dropIndex(itemTops: List<Float>, itemHeights: List<Float>, dragY: Float): Int?` — null outside the list.

- [ ] **Step 1: Failing tests:** `dropOutsideListIsNull`; `dropBetweenItemsPicksGap`; `movingParentMovesChildren` (via `SheetGroupingRules` + host → `active` groups); `resetRestoresDefaults`; Python: both kinds reachable from the sheet, `moveGroupToIndex`, `subtreeBlock`, `renameGroup`, `deleteGroup`, `addGroup` referenced.
- [ ] **Step 2–4** (+ desktop screenshot of the manager). **Step 5: Commit** — `feat(sheet): group manager with drag`

### Task 7: Notes and development panel

**Files:**
- Create: `shared/.../ui/sheet/SheetNotes.kt`, `SheetDevelopment.kt`
- Test: `tools/tests/test_sheet_notes_development.py`, `shared/src/commonTest/.../ui/sheet/SheetNotesLogicTest.kt`

**Interfaces:**
- Produces: `SheetNotes(notes: List<CharacterNote>, editable: Boolean, host, modifier)` — collapsible titled notes, add/edit/delete in a dialog (title ≤ 120, body ≤ 12000 enforced in the field). `SheetDevelopment(groups: List<SheetDevelopmentGroup>, onOpenDetails: (String) -> Unit, onOpenAll: () -> Unit, onManageGroups: () -> Unit, onToggleCollapsed: (String) -> Unit, modifier)` — tree rows with depth indent, unmet-requirement rows in crimson text + icon (not colour alone), empty state «Нет взятых навыков».
- Details stay platform-owned until stage 4: `SheetScreen` gains `developmentDetails: @Composable (entryId: String, onDismiss: () -> Unit) -> Unit`. Desktop/web pass their existing `DevelopmentDetailsDialog` with its edit/reset/delete/open-linked callbacks exactly as `CharacterSheetScreen` wires them; Android passes `OwnedDevelopmentDetailSheet` moved out of `OverviewScreen.kt`.

- [ ] **Step 1: Failing tests:** `noteLimitsEnforced`; Python: three platforms pass `developmentDetails =`; desktop wiring references `resetDevelopmentOverride`, `removeCustomDevelopment`, `updateCustomDevelopment`.
- [ ] **Step 2–4.** **Step 5: Commit** — `feat(sheet): notes and development panel`

### Task 8: Edit mode in `SheetScreen` and platform wiring

**Files:**
- Modify: `SheetScreen.kt`, `SheetScreenState.kt`, `SheetChangeReportingHost.kt`, `SheetStats.kt` (attribute −/+ in edit mode), desktop/web `Main.kt`, Android `DublApp.kt`
- Test: `shared/src/commonTest/.../ui/sheet/SheetEditModeTest.kt`, `tools/tests/test_sheet_wiring.py` (rewrite the editing-branch assertions)

**Interfaces:**
- `SheetScreen(host: DublSheetEditHost, onOpenSkills, onOpenDevelopment, portraitLoader, onPickPortrait, developmentDetails, modifier)`; the `onEdit` param goes away — «Правка»/«Готово» is a `FuryChipGroup` Play/Edit switch in the header, state in `SheetScreenState.editing`, reset on `host.active.id` change (Review Focus 4: any open dialog closes on character change).
- Section order in edit mode: header (identity/XP/portrait editable) → vitals (+ add/visibility) → metrics → attributes (steppers) → skills (all visible, collapse, «Настроить группы») → development → notes → combat. Play mode unchanged from 2a plus notes and development panels (read-only, collapsible).
- `SheetChangeReportingHost` reports attribute, identity, economy, size/legs and condition-dialog changes with the same texts the old platforms used (desktop «Персонаж изменён», «Состояния изменены», «<Характеристика> ±1»; Android «Имя изменено», «Общий опыт: a → b», etc. via the existing Android helpers).
- Platforms: the `editing` branch that showed the old sheet is removed; Android `BackHandler` (2a) now leaves edit mode on the shared screen.

- [ ] **Step 1: Failing tests:** `editingResetsOnCharacterChange`; `attributeChangeReportsUndoToast`; Python: no `CharacterSheetScreen(` / `OverviewScreen(` call left in Main/DublApp; `SheetScreen(` gets `developmentDetails` and `portraitLoader`.
- [ ] **Step 2–4** (+ screenshots wide/phone in both modes). **Step 5: Commit** — `feat(sheet): edit mode replaces the old sheet on all platforms`

### Task 9: Delete the old sheet code and retarget tests

**Files:**
- Move (Android): `DublSkillRollSheet`, `CheckRollSheet` and their helpers → `app/.../ui/screens/SkillRollSheets.kt`; `RecentChangeBar`, `recentResourceChange`, `resourceChangeText`, `fcpAccentColor` → `app/.../ui/RecentChange.kt`; `OwnedDevelopmentDetailSheet` → `app/.../ui/screens/DevelopmentDetailSheet.kt` (if not already moved in Task 7)
- Delete: everything listed under "Deleted in 2b" in the inventory doc
- Modify: Python tests that read the deleted files (~20 files; grep `CharacterSheetScreen`, `OverviewScreen`, `ContextRollDialog`, `SkillAttributeChoiceDialog`, `DesktopResourceTile` etc. under `tools/tests`), `.github/workflows/linux-appimage.yml`, `.github/workflows/release.yml`

- [ ] **Step 1:** Move the Android keepers (no behaviour change); compile-only check of references via grep.
- [ ] **Step 2:** Delete files/functions; `grep -rn` for every deleted symbol returns only the plan docs.
- [ ] **Step 3:** Python tests: rewrite assertions whose behaviour now lives in `shared/.../ui/sheet/` (health amount control, negative health, condition overrides, development details wiring, roll context reminders, stock-attribute default, undo via `undoLast`, FCP resource presentation, stat explanations using shared formula components); delete functions that pinned only old layout; never weaken rules/persistence/parity/FCP assertions — retarget them. Update workflow test lists to match.
- [ ] **Step 4:** Gradle trio + Python suite ⊆ baseline. **Step 5: Commit** — `refactor: remove the old per-platform sheets` (body lists every deleted/rewritten test).

### Task 10: Inventory sign-off, 2a carry-overs, CI

- [ ] **Step 1:** 2a deferred items: `SheetScreen` economy/skill-group `remember` keys add `host.developmentCatalog` (stale after a pack enables at startup); web sign-out reachable from the wide shell (account button next to the character button); IME padding in `FuryDialogScaffold` sheet mode; pin only the name row on phones (chips scroll); animated selected colour in `FuryTopTabs`/`FuryBottomTabs`; target change keeps the last result visible but marked «до изменения цели» instead of clearing (desktop let you type defence after rolling). Each with a test or screenshot.
- [ ] **Step 2:** Walk the inventory doc: tick every line with the file:line that covers it; anything unticked is a bug → fix before Step 3.
- [ ] **Step 3:** Push; all PR #32 workflows green; send the owner desktop screenshots (wide/phone, play/edit), APK and AppImage links.
