# Character editor («Редактор») Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace 2b edit mode with a shared, step-by-step character editor (Развитие · Основа · Характеристики · Умения · Навыки · Итог) that buys ranks and perks through the existing rules, saves live, and can undo the whole visit.

**Architecture:** New package `com.furybook.dubl.ui.editor` in `shared`. Pure view-model functions over `DublEditorHost` (an extension of `DublSheetEditHost`) feed Compose step screens. Costs, requirements and chains come from `CharacterEconomy`, `DevelopmentRules` and `DevelopmentAcquisitionPlanner`. «Отменить всё» uses one new application call, `DublApplication.restore(point)`. `SheetScreen` shows `EditorScreen` where it used to show its edit body, and 2b edit-only code is deleted.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlin.test; Python contract tests in `tools/tests`.

**Spec:** `docs/superpowers/specs/2026-11-18-character-editor-design.md` (mockup: https://claude.ai/artifact/3u11jd826wDCe7wvRvEtZv)

## Global Constraints

- Rules packages (`com.furybook.dubl.model/.data/.state`, `com.furybook.core`) unchanged. The application layer gains only `CharacterRestorePoint`, `restorePoint()` and `restore()` (Task 1).
- No cost or requirement logic in UI code: every number comes from `CharacterEconomy`, `skillNextRankCost`, `DevelopmentRules`, `DevelopmentAcquisitionPlanner`.
- FCP: the ЦИ tab is shown only when `host.uiMounts(FcpUiSurface.DEVELOPMENT_TABS, FcpUiComponent.DEVELOPMENT_BROWSER).forFeature(DublUiFeature.CHI) != null`.
- Base mana (`MagicEquipmentRules.BASE_MANA_ENTRY_ID`) is never listed in the editor.
- Breakpoints from `sheetLayoutFor`: PHONE <600, WIDE <1100, WIDE_WITH_TRAY ≥1100. Rail 232dp, summary 312dp, perk detail pane 360dp.
- 44dp touch targets on PHONE/WIDE. Text 4.5:1, large numbers 3:1 in all 5 palettes (`Contrast.kt`).
- No text overlap or wrapping numbers at any width. Cost labels truncate with "…".
- Python baseline `37 failed + 6 errors` (saved in `/tmp/pytest-baseline-ids.txt`) may only shrink.
- Local checks: `./gradlew :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs`; Android only in CI.
- Copy is Russian, exactly as in the spec/mockup (e.g. «Отменить всё», «Взять с цепочкой · N XP», «Нет описания в правилах — см. требования.»).
- Commits end with the session attribution lines; push with `git -c push.negotiate=true push origin ui-revamp`. Never merge to main.

## Review Focus

1. **Switching character while the editor is open** (character picker, cloud pull): the snapshot must reset, and «Отменить всё» must never write character A's snapshot onto B. Tests: Task 1 `restoreRefusesAnotherCharacter`, Task 3 `stateResetsOnCharacterChange`.
2. **Planner choices and ability options** ("Холодное оружие 5 или Рукопашный бой 5", roots with several ОС options): switching the choice must change the chain, cost and what gets bought. Test: Task 5 `choiceSelectionChangesChain`.
3. **Overspent XP during creation** (free < 0): editor still opens and edits; buy buttons show the reason instead; Итог lists "Перерасход опыта". Tests: Task 5 `overspentBlocksTakeWithReason`, Task 6 `problemsListOverspentAndNoName`.
4. **Heavy baron at 360dp** (26 perks, 60-char name, long perk names): names truncate, costs never overlap controls. Check: Task 14 screenshots at 360×800 with the baron.
5. **Removing a prerequisite perk**: dependents stay, get «!», and are listed in Итог. Test: Task 6 `problemsListUnmetOwnedPerk`.

---

### Task 1: Restore point in the application layer

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/application/CharacterRestorePoint.kt`
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/application/DublApplication.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/application/CharacterRestoreTest.kt`

**Interfaces:**
- Produces: `data class CharacterRestorePoint(val character: DublCharacter, val extras: CharacterSheetExtras)`; `DublApplication.restorePoint(): CharacterRestorePoint` (active character + `activeExtras`); `DublApplication.restore(point: CharacterRestorePoint): Boolean`.

- [ ] **Step 1: Write the failing tests** (in-memory stores as in `FakeSheetHost`)
  - `restoreBringsCharacterAndExtrasBack`: take point; `changeAttribute(DEXTERITY, +1)`, `sheet.setPortrait("x")`; `restore(point)` returns true; `active == point.character`, `activeExtras == point.extras`.
  - `restoreIsOneUndoStep`: after the above, `undoLast()` returns true and DEXTERITY is +1 again with portrait "x".
  - `restoreRefusesAnotherCharacter`: point taken on A; `createCharacter()` (B is active); `restore(point)` returns false; B unchanged.
- [ ] **Step 2: Run** `./gradlew :shared:desktopTest --tests '*CharacterRestoreTest*'`. Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement.** `restore` returns false when `point.character.id != active.id`. Otherwise it captures `before = restorePoint()`, then `characterSession.updateActive { point.character }` and `extrasSession.replace(id, point.extras)`, then `undoManager.record { restore(before) }`.
- [ ] **Step 4: Run** the same command. Expected: PASS.
- [ ] **Step 5: Commit** `feat(app): character restore point for the editor's «Отменить всё»`.

### Task 2: `DublEditorHost` and its implementations

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/DublEditorHost.kt`
- Modify: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/FakeSheetHost.kt` (implement the new interface)
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt`, `webApp/src/jsMain/kotlin/com/furybook/desktop/DesktopAppState.kt`. The same-named methods at ~270–291 become `override`. Add `restorePoint`/`restore` delegates wrapped in `sync {}`.
- Modify: `app/src/main/java/com/furybook/android/ui/AndroidSheetHost.kt` (delegate to `CharacterController`), `app/src/main/java/com/furybook/android/state/CharacterController.kt` (add `restorePoint()`/`restore()` via `sync {}`)
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/DublEditorHostTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces:
```kotlin
interface DublEditorHost : DublSheetEditHost {
    fun changeSkillRank(skillId: String, delta: Int)
    fun addSpecializedSkill(templateId: String, specialization: String): String?
    fun addCustomSkill(name: String, description: String, attributes: List<AttributeId>, untrained: UntrainedRule): String?
    fun acquireDevelopment(request: DevelopmentAcquisitionRequest): DevelopmentAcquisitionResult
    fun setDevelopmentRank(entryId: String, rank: Int, optionIndex: Int = 0)
    fun restorePoint(): CharacterRestorePoint
    fun restore(point: CharacterRestorePoint): Boolean
}
```
  `FakeSheetHost` becomes `FakeSheetHost(...) : DublEditorHost`, with each member a one-line delegate to its `application`.

- [ ] **Step 1: Write the failing test** `fakeHostDelegatesEditorCalls`: `changeSkillRank("stealth", 1)` raises the rank; `acquireDevelopment(single(id))` applies and returns `applied == true`; `restore(restorePoint())` returns true.
- [ ] **Step 2: Run** `./gradlew :shared:desktopTest --tests '*DublEditorHostTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the interface and the four host implementations (fake, desktop, web, Android).
- [ ] **Step 4: Run** `./gradlew :shared:desktopTest --tests '*DublEditorHostTest*' :desktopApp:compileKotlin :webApp:compileKotlinJs`. Expected: PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(editor): DublEditorHost on every platform`.

### Task 3: Editor steps and screen state

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorState.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/EditorStateTest.kt`

**Interfaces:**
- Consumes: Task 1, Task 2.
- Produces:
  - `enum class EditorStep(val title: String) { DEVELOPMENT("Развитие"), BASICS("Основа"), ATTRIBUTES("Характеристики"), SKILLS("Умения"), PERKS("Навыки"), SUMMARY("Итог") }`
  - `fun editorSteps(creationComplete: Boolean): List<EditorStep>`: creating gives BASICS…SUMMARY; complete gives DEVELOPMENT first, then the same five.
  - `fun editorStartStep(creationComplete: Boolean, forceBasics: Boolean): EditorStep`: BASICS when creating or `forceBasics`, else DEVELOPMENT.
  - `enum class EditorPerkKind { REGULAR, SPECIAL, MARTIAL, CHI, OWNED }`, `enum class EditorPerkFilter { ALL, AVAILABLE, ALMOST }`
  - `class EditorState` (Compose `mutableStateOf` fields):
    - `step`, `perkKind`, `perkFilter`, `perkQuery`, `skillQuery`, `skillsRankedOnly`;
    - `selectedEntryId: String?`, `choiceSelections: Map<String, Int>`, `optionIndex: Int?`;
    - `changesOpen`;
    - `snapshot: CharacterRestorePoint?`;
    - `fun open(host: DublEditorHost, forceBasics: Boolean)`: takes the snapshot, sets the start step, and `skillsRankedOnly = host.active.creationComplete`;
    - `fun syncCharacter(host: DublEditorHost)`: re-opens when `host.active.id != snapshot?.character?.id`;
    - `fun next()`, `fun back()` along `editorSteps(...)`.

- [ ] **Step 1: Write the failing tests**
  - `stepsForNewAndExisting`: `editorSteps(false) == [BASICS, ATTRIBUTES, SKILLS, PERKS, SUMMARY]`; `editorSteps(true).first() == DEVELOPMENT`, size 6.
  - `startStep`: `(false,false)→BASICS`, `(true,false)→DEVELOPMENT`, `(true,true)→BASICS`.
  - `nextAndBackClamp`: from BASICS `back()` stays BASICS; from SUMMARY `next()` stays SUMMARY.
  - `stateResetsOnCharacterChange`: open on Шершень, change step to SKILLS; switch the active character in the fake host; `syncCharacter` gives `snapshot.character.id == newId` and the step back at the start.
- [ ] **Step 2: Run** `--tests '*EditorStateTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): steps and screen state`.

### Task 4: Changes since opening

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorChanges.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/EditorChangesTest.kt`

**Interfaces:**
- Consumes: Task 1.
- Produces: `data class EditorChangeLine(val text: String, val xpSpent: Int)` (positive = XP spent); `data class EditorChanges(val lines: List<EditorChangeLine>, val netSpent: Int)`; `fun editorChanges(before: CharacterRestorePoint, after: CharacterRestorePoint, catalog: DevelopmentCatalog, includeChi: Boolean): EditorChanges`; `fun editorNetText(netSpent: Int): String` gives "Опыт не изменился" / "Потрачено N XP" / "Возвращено N XP"; `fun editorXpText(spent: Int): String` gives "−95 XP" / "+10 XP" / "".
- Line formats (spec):
  - name: "Имя: <new>";
  - concept: "Концепция изменена";
  - size: "Размер a → b";
  - legs: "Ноги a → b";
  - mana: "Мана включена" / "Мана выключена";
  - XP: "Опыт a → b";
  - ОС override: "Очки способностей: <n|авто>";
  - attribute: "<title> a → b";
  - skill: "<name> a → b";
  - perk added: "+ <name>"; rank change: "<name>: ранг r"; removed: "− <name>";
  - portrait: "Портрет изменён";
  - creation: "Создание завершено" / "Создание открыто снова".
- XP per line: attribute and skill deltas come from `CharacterEconomy.attributeCost` and `skillXpCostForRank`; a perk line's XP = `breakdown(before with only that entry set to its after-rank).spentXp − breakdown(before).spentXp`. `netSpent = before.remainingXp − after.remainingXp`.

- [ ] **Step 1: Write the failing tests**
  - `attributeAndSkillLines`: DEXTERITY 8→9 gives `("Ловкость 8 → 9", 95)`; stealth 5→6 gives `("Скрытность 5 → 6", 60)`.
  - `perkLines`: added Острый слух (30) gives `("+ Острый слух", 30)`; removed gives `("− Острый слух", -30)`.
  - `experienceAndCreation`: XP 4580→4680 gives text "Опыт 4580 → 4680"; complete gives "Создание завершено"; `editorNetText(110) == "Потрачено 110 XP"`, `editorNetText(-10) == "Возвращено 10 XP"`.
  - `noChangesIsEmpty`: identical points give no lines, net 0.
- [ ] **Step 2: Run** `--tests '*EditorChangesTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): change list since opening`.

### Task 5: Perk catalog, status and detail models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorPerkModels.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/EditorPerkModelsTest.kt` (small catalog built in the test, as `SheetPlayModelsTest` does)

**Interfaces:**
- Consumes: Tasks 2–3.
- Produces:
  - `enum class EditorPerkStatus(val label: String)`: `AVAILABLE("Доступно")`, `ALMOST("Почти")`, `LOW_XP("Мало XP")`, `REQUIREMENTS("Требования")`, `CREATION_ONLY("Только при создании")`, `OWNED("Взят")`, `UPGRADABLE("Взят")`. The pill text for OWNED/UPGRADABLE with maxRank > 1 is "Взят · r/m".
  - `data class EditorPerkCard(val entryId: String, val name: String, val category: String, val costText: String, val status: EditorPerkStatus, val pill: String, val warn: Boolean, val depth: Int, val rank: Int, val maxRank: Int)`
  - `data class EditorPerkGroup(val title: String, val cards: List<EditorPerkCard>)`
  - `fun editorPerkStatus(host: DublEditorHost, entry: DevelopmentEntry): EditorPerkStatus`
  - `fun editorPerkGroups(host: DublEditorHost, kind: EditorPerkKind, filter: EditorPerkFilter, query: String): List<EditorPerkGroup>`
  - `fun editorPerkCounts(host: DublEditorHost, kind: EditorPerkKind): Map<EditorPerkFilter, Int>`
  - `fun editorShowsChi(host: DublEditorHost): Boolean`
  - `data class EditorRequirementLine(val text: String, val status: RequirementStatus)`
  - `data class EditorChainStep(val label: String, val costText: String)`
  - `data class EditorChoice(val id: String, val label: String, val options: List<String>, val selected: Int)`
  - `data class EditorPerkDetail(val entryId: String, val name: String, val category: String, val kindLabel: String, val costText: String, val status: EditorPerkStatus, val pill: String, val rank: Int, val maxRank: Int, val benefit: String, val benefitMissing: Boolean, val requirements: List<EditorRequirementLine>, val unlocks: List<String>, val chain: List<EditorChainStep>, val chainCost: String, val choices: List<EditorChoice>, val actionLabel: String?, val blockReason: String?, val request: DevelopmentAcquisitionRequest?, val removeLabel: String?)`
  - `fun editorPerkDetail(host: DublEditorHost, entryId: String, choiceSelections: Map<String, Int>, optionIndex: Int?): EditorPerkDetail?`
- Rules for status, in order:
  1. rank ≥ max → OWNED.
  2. Any availability check FAIL whose text contains `CREATION_CLOSED_MARK = "создание уже завершено"` → CREATION_ONLY.
  3. `plan = planner.plan(single(id, targetRank = rank + 1, enforceBudget = false))`. If `unresolvedRequirements` is not empty → REQUIREMENTS.
  4. Prerequisite steps (steps that are not this entry's own Development step): 0 → AVAILABLE/UPGRADABLE if `plan.xpCost ≤ remainingXp && plan.abilityCost ≤ abilityPointsRemaining`, else LOW_XP; 1 → ALMOST; more → REQUIREMENTS.
- Detail rules:
  - `actionLabel`: «Взять · N XP» / «Повысить до ранга r · N XP» / with prerequisites «Взять с цепочкой · N XP». Add " + K ОС" when the ability cost > 0. Set when unresolved is empty and the plan is affordable; `request` is the request to pass to `acquireDevelopment`.
  - `blockReason`:
    - "Только при создании персонажа — создание уже завершено.";
    - "Не все требования можно закрыть автоматически.";
    - "Не хватает опыта: нужно N XP, свободно M.";
    - "Не хватает очков способностей.".
  - `benefit`: the empty case reads "Нет описания в правилах — см. требования."; an access root reads "Открывает ветку способностей.".
  - `unlocks` from `planner.unlocks(id)`.
  - `removeLabel`: "Убрать (+N XP)" / "Убрать ранг (+N XP)" when owned.
- Groups:
  - REGULAR and MARTIAL by category;
  - SPECIAL by branch name (as the Development screen's `branchName`), children with `depth = 1`;
  - OWNED by kind title ("Обычные навыки", "Особые способности", "Боевые искусства", "ЦИ");
  - base mana excluded.

- [ ] **Step 1: Write the failing tests** (fixture: Шершень-like character, catalog with Острый слух, Атлетичность "Сила 4, …", an "X 5 или Y 5" entry, a "только при создании" entry, a branch root and a child)
  - `statusAvailableAlmostRequirements`: Острый слух → AVAILABLE; Атлетичность rank 1 of 2 with Сила 3 → pill "Взят · 1/2"; an entry missing one skill rank → ALMOST; missing two things → REQUIREMENTS.
  - `creationOnlyAfterComplete`: complete creation → CREATION_ONLY; reopen → not CREATION_ONLY.
  - `chainDetail`: the ALMOST entry has `chain.size == 2`, `actionLabel` starting "Взять с цепочкой · ", and `request != null`; `host.acquireDevelopment(request)` raises the skill and adds the perk.
  - `choiceSelectionChangesChain`: for "X 5 или Y 5", selecting option 1 changes the chain's first label from X to Y and changes `chainCost`.
  - `overspentBlocksTakeWithReason`: set total XP below spent → `actionLabel == null`, `blockReason` starts "Не хватает опыта".
  - `chiTabOnlyWhenMounted`: `editorShowsChi` is false with `FakeSheetHost(chiPackEnabled = false)`.
  - `baseManaNeverListed`.
- [ ] **Step 2: Run** `--tests '*EditorPerkModelsTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): perk status, list and detail models`.

### Task 6: Развитие, attribute, skill and Итог models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorModels.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/EditorModelsTest.kt`

**Interfaces:**
- Consumes: Tasks 2–5. Also `sheetSkillsByAttribute`, `sheetMetrics`, `rememberSheetEconomy` logic (`CharacterEconomy.breakdown` with `sheetChiEconomyMounted`).
- Produces:
  - `data class EditorAttributeCard(val id: AttributeId, val short: String, val title: String, val value: Int, val delta: Int, val incText: String, val decText: String, val canInc: Boolean, val canDec: Boolean)`. incText is "+1: N XP" / "максимум"; decText is "−1: вернёт N" / "минимум".
  - `fun editorAttributeCards(character: DublCharacter, before: DublCharacter): List<EditorAttributeCard>`
  - `data class EditorDerived(val label: String, val value: String, val delta: Int)`; `fun editorDerived(after: DublCharacter, before: DublCharacter): List<EditorDerived>` (the 6 play-sheet metrics)
  - `data class EditorSkillRow(val skillId: String, val name: String, val rank: Int, val gainedFrom: Int, val nextCostText: String, val bonus: Int?, val canInc: Boolean, val canDec: Boolean)`. nextCostText is "+N XP" / "макс.".
  - `data class EditorSkillGroup(val attribute: AttributeId, val short: String, val title: String, val value: Int, val rows: List<EditorSkillRow>)`
  - `fun editorSkillGroups(host: DublEditorHost, before: DublCharacter, rankedOnly: Boolean, query: String): List<EditorSkillGroup>`: rows ranked > 0, or ranked in `before`, when `rankedOnly`; by rank desc, then name.
  - `data class EditorDevSkill(val skillId: String, val name: String, val attrShort: String, val from: Int, val cost: Int, val bonusAfter: Int)`
  - `data class EditorDevAttr(val id: AttributeId, val short: String, val from: Int, val cost: Int)`
  - `data class EditorDevelopmentView(val skills: List<EditorDevSkill>, val attributes: List<EditorDevAttr>, val perks: List<EditorPerkCard>, val almost: List<EditorPerkCard>)`
  - `fun editorDevelopmentView(host: DublEditorHost): EditorDevelopmentView`. Sizes and order:
    - skills: top 6 by bonus;
    - attributes: up to 8, cheapest first;
    - perks: AVAILABLE/UPGRADABLE top 6, cheapest first, no access roots;
    - almost: ALMOST top 4, cheapest chain first;
    - all of them filtered to what fits in free XP.
  - `data class EditorProblem(val text: String, val step: EditorStep)`; `fun editorProblems(host: DublEditorHost): List<EditorProblem>`. Texts:
    - "Перерасход опыта: N XP";
    - "Очков способностей потрачено больше бюджета: a из b";
    - "У персонажа нет имени" (blank, or still «Новый персонаж» while creating);
    - "<perk>: требования больше не выполнены" (owned entries with a FAIL check other than the creation mark);
    - "Заклинания без цены: N" (`unpricedLearnedSpells`).

- [ ] **Step 1: Write the failing tests**
  - `attributeCardsCostsAndDelta`: base DEX 8 gives incText "+1: 95 XP", decText "−1: вернёт 90"; after +1 vs before, delta 1.
  - `skillGroupsKeepRankedFromBefore`: rank 1→0 this visit still shows with `rankedOnly = true`; the next-cost text equals `"+" + skillNextRankCost(rank) + " XP"`.
  - `developmentViewFitsFreeXp`: with 45 free XP, no skill row costs more than 45, and perks are sorted by cost.
  - `problemsListOverspentAndNoName`: new character named «Новый персонаж» with XP 0 and DEX +1 gives both texts.
  - `problemsListUnmetOwnedPerk`: own Атлетичность, lower Сила below 4, gives "Атлетичность: требования больше не выполнены".
- [ ] **Step 2: Run** `--tests '*EditorModelsTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): development, attribute, skill and summary models`.

### Task 7: `EditorScreen` frame

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorScreen.kt` (frame, rail, tabs, footer, summary column, changes sheet)

**Interfaces:**
- Consumes: Tasks 3, 4, 6.
- Produces:
```kotlin
@Composable fun EditorScreen(
    host: DublEditorHost, state: EditorState, onClose: () -> Unit, modifier: Modifier = Modifier,
    portraitLoader: SheetPortraitLoader? = null, onPickPortrait: (() -> Unit)? = null,
)
```
  Each step body is an `internal @Composable fun Editor<Step>Step(host: DublEditorHost, state: EditorState, layout: SheetLayout)`, added in Tasks 8–11. Until then the frame shows a stub `Text(step.title)`.
- Layout (spec, "Layout by width"):
  - Header (60dp): back arrow → `onClose`; «Редактор · <name>» ("Новый персонаж" when creating and unnamed); badge «Создание» (gold) / «Развитие» (neutral) with hint "всё можно менять свободно" / "правки сохраняются сразу"; «Готово» → `onClose`.
  - ≥1100: rail, content with «← <prev> / Далее: <next> →» (last: «Готово»), summary column with XP card, derived, «Изменения за этот заход», net text, «Отменить всё».
  - <1100: tab strip; footer with XP/ОС chip, «↺ N», ‹, «Далее ›»; bottom-sheet changes via `FuryDialogScaffold`.
  - «Отменить всё» calls `host.restore(state.snapshot!!)`, then a toast "Персонаж возвращён к тому, каким был при открытии редактора".
  - Rail sub-lines:
    - Развитие: "свободно N XP";
    - Основа: the name, or "без имени";
    - Характеристики: "N XP";
    - Умения: "N рангов · N XP";
    - Навыки: "N взято · N XP";
    - Итог: "N замеч." or "всё в порядке".

- [ ] **Step 1:** Implement the frame with stubs.
- [ ] **Step 2: Run** `./gradlew :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat(editor): editor frame — rail, tabs, footer, live summary`.

### Task 8: Развитие, Основа, Характеристики steps

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorDevelopmentStep.kt`, `EditorBasicsStep.kt`, `EditorAttributesStep.kt`

**Interfaces:**
- Consumes: Task 6 models; `host.setIdentity`, `host.setEconomy`, `host.setPortrait`, `host.changeAttribute`, `host.changeSkillRank`, `host.acquireDevelopment`.
- Behaviour (spec sections Развитие / ① / ②):
  - Развитие: the four lists, «Все умения →» / «Все навыки →» / «Все →» set `state.step`. Tapping a perk name sets `state.step = PERKS`, `perkKind` and `selectedEntryId`.
  - Основа: identity via `setIdentity` (on every field change, with the other values unchanged); portrait pick/remove; XP ±100 and the number field through `setEconomy(total, creation, adjustment, override)`, keeping creation and adjustment; ОС stepper sets the override; «вернуть авто» passes `null`.
  - Характеристики: cards from `editorAttributeCards(host.active, state.snapshot.character)`; derived grid with deltas.

- [ ] **Step 1:** Implement the three steps.
- [ ] **Step 2: Run** the local checks. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat(editor): Развитие, Основа and Характеристики steps`.

### Task 9: Умения step and shared skill forms

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillsStep.kt`, `EditorSkillForms.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/EditorSkillFormsTest.kt`

**Interfaces:**
- Consumes: `editorSkillGroups`, `SkillCatalog.templates`, `host.addSpecializedSkill`, `host.addCustomSkill`.
- Produces: `internal fun validateSpecialization(text: String): String?`; `internal fun validateCustomSkill(name: String, attributes: Set<AttributeId>): String?`. Both return an error message or null.
- Layout:
  - PHONE: a header row "ЛОВ 8 Ловкость" above the rows;
  - WIDE and up: a 52dp tile on the left, 2 group columns at ≥1100;
  - rows as in the spec, cost text `maxLines = 1, TextOverflow.Ellipsis`.
- Forms (one `FuryDialogScaffold`, two tabs «Специализация» / «Своё»), with the same fields as the desktop `SpecializedSkillDialog` / `CustomSkillDialog`. Errors: "Введите название." / "Выберите хотя бы одну характеристику." / "Такое умение уже есть." (when the call returns null).

- [ ] **Step 1: Write the failing tests** `validateSpecializationBlank` ("  " gives "Введите название.") and `validateCustomSkillNeedsAttribute` (empty set gives "Выберите хотя бы одну характеристику.").
- [ ] **Step 2: Run** `--tests '*EditorSkillFormsTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** the validators, the forms and the step.
- [ ] **Step 4: Run** the tests and local checks. Expected: PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(editor): Умения step and shared skill forms`.

### Task 10: Навыки step

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorPerksStep.kt`

**Interfaces:**
- Consumes: Task 5 models; `host.acquireDevelopment`, `host.setDevelopmentRank`.
- Behaviour (spec ④):
  - kind tabs with counts; ЦИ only if `editorShowsChi`;
  - filter chips «Все N / Доступно N / Почти N»; search;
  - cards with pill and «!»; children indented 16dp;
  - detail as a 360dp pane at ≥1100, otherwise a full-content overlay with «← К списку»;
  - choice chips write `state.choiceSelections` / `state.optionIndex`;
  - the action button calls `host.acquireDevelopment(detail.request!!)`, then a toast "Взято: <name>" or "Взято цепочкой: <name>" (from `chain.size`). On `applied == false` the toast shows `result.message`;
  - «Убрать…» calls `setDevelopmentRank(id, rank - 1, optionIndex)`.

- [ ] **Step 1:** Implement.
- [ ] **Step 2: Run** the local checks. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat(editor): Навыки step with chains`.

### Task 11: Итог step

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSummaryStep.kt`

**Interfaces:**
- Consumes: `editorProblems`, economy breakdown, `editorAttributeCards`, `editorDerived`; `host.completeCreation()` / `host.reopenCreation()`. Two new callbacks on `EditorScreen` for the Магия/Снаряжение links: `onOpenMagic: () -> Unit` and `onOpenGear: () -> Unit`, default `{}`.
- Behaviour (spec ⑤):
  - Замечания box: green «ВСЁ В ПОРЯДКЕ» or gold «ЗАМЕЧАНИЯ · N»; each line jumps to `problem.step`;
  - cards jump to their step;
  - XP lines: Характеристики / Умения / Навыки / (ЦИ when mounted) / «Свободно из N»;
  - creation text and button copied from the spec/mockup.

- [ ] **Step 1:** Implement.
- [ ] **Step 2: Run** the local checks. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `feat(editor): Итог step`.

### Task 12: Wire the editor in; resources setup on the play sheet

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetScreen.kt`. `host` becomes `DublEditorHost`. `state.editing` renders `EditorScreen(host, editorState, onClose = { state.updateEditing(false) }, …)`. New params `openEditorAtBasics: Boolean = false`, `onEditorShown: (Boolean) -> Unit = {}`, `onOpenMagic`, `onOpenGear`.
- Modify: `SheetPlayHeader.kt`. Add a «Настроить» `SmallPill` after the last `ResourceCell` in `ResourceRack` and at the end of the compact resource strip; it opens `SheetDialog.Resources`.
- Modify: `shared/src/commonMain/kotlin/com/furybook/ui/components/FuryShell.kt`. New param `phoneChromeHidden: Boolean = false` hides the phone bottom nav.
- Modify: desktop/web `Main.kt` and `CharactersScreen.kt`, Android `DublApp.kt` and `CharactersScreen.kt`. «Новый персонаж» calls `createCharacter()`, selects the sheet section and sets `openEditorAtBasics = true` (cleared once consumed). Pass `onEditorShown` into the shell's `phoneChromeHidden`. Android keeps `backHandler` (Back closes the editor). Link `onOpenMagic` / `onOpenGear` to the sections.
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetScreenStateTest.kt` (extend)

- [ ] **Step 1: Write the failing test** `editorOpensAtBasicsWhenRequested`: `SheetScreenState.openEditor(forceBasics = true)` sets `editing = true` and records `forceBasics`; `updateEditing(false)` clears it.
- [ ] **Step 2: Run** `--tests '*SheetScreenStateTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `SheetScreenState.openEditor(forceBasics: Boolean)` and the wiring above.
- [ ] **Step 4: Run** the local checks. Expected: PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(editor): open from the sheet and «Новый персонаж»; resources setup on the play sheet`.

### Task 13: Delete 2b edit mode and retire its tests

**Files:**
- Delete:
  - `SheetGroupManager.kt`, `SheetGroupDrag.kt`, `SheetIdentityDialog.kt`, `SheetHeader.kt` (if only the edit body uses it);
  - the edit body in `SheetScreen.kt` (`SheetScreenHeader`, `SheetScreenChips`, `SheetScreenBody`, `SheetSection`);
  - `SheetStats.kt` / `SheetSkills.kt` / `SheetDevelopment.kt` if they have no callers left, otherwise only their edit-only parameters;
  - `SheetDialog.Identity` and `SheetDialog.Groups`;
  - commonTest `SheetGroupDragTest.kt`, `SheetEditModeTest.kt` (rewrite any case that covers a surviving function into `EditorStateTest`).
- Modify: `tools/tests/*` files that read the deleted sources (list from `grep -rln "SheetGroupManager\|SheetGroupDrag\|SheetIdentityDialog\|SheetScreen.kt\|SheetStats.kt\|SheetSkills.kt\|SheetDevelopment.kt\|SheetHeader.kt" tools/tests`). Rewrite an assertion to point at the editor file that now owns the behaviour, or delete it when it pinned the deleted UI. Remove the matching entries in `.github/workflows/linux-appimage.yml` and `release.yml` if present.
- Modify: `CHANGELOG.md` (Unreleased: editor added, 2b edit mode removed, group manager retired with data kept).

- [ ] **Step 1:** Delete and rewrite as above. Check with `grep -rn "SheetGroupManager\|SheetIdentityDialog\|SheetDialog.Groups" shared desktopApp webApp app`, which should find nothing.
- [ ] **Step 2: Run** the local checks. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Run** `python3 -m pytest -q -rfE --continue-on-collection-errors tools/tests -p no:cacheprovider | grep -E "^(FAILED|ERROR)" | sort > /tmp/pytest-now.txt; comm -13 /tmp/pytest-baseline-ids.txt /tmp/pytest-now.txt`. Expected: empty output (no new failures).
- [ ] **Step 4: Commit** `refactor(sheet): remove 2b edit mode; retire group manager UI (data kept)`.

### Task 14: Verify, screenshot, review, push

- [ ] **Step 1:** Seed: `rm -rf /tmp/seedhome && SEED_DIR=/tmp/seedhome ./gradlew :shared:desktopTest --tests '*SeedDevCharacters*'`.
- [ ] **Step 2:** Screenshots (Xvfb :99 + xdotool + `import`), desktop app with `XDG_DATA_HOME=/tmp/seedhome`:
  - widths 1440×900, 1100×768, 834×1112, 360×800;
  - Шершень on every step, plus the baron on Навыки, Умения and Итог;
  - Ярость and Пергамент.
  - Expected: no overlap, no clipped numbers, «Настроить» visible on the rack.
- [ ] **Step 3:** Manual flows on desktop:
  - new character from the list opens at Основа;
  - raise a skill from Развитие;
  - take Атлетичность rank 2 by chain;
  - «Отменить всё» restores, and «Отменить» on the sheet brings the edits back;
  - switch character with the editor open: no cross-restore.
- [ ] **Step 4:** Full local checks plus the Python diff (Task 13 Step 3). Expected: green, no new failures.
- [ ] **Step 5:** One fresh reviewer (subagent) over the whole branch diff since `bd4664a`, against the spec and this plan. Fix the confirmed findings.
- [ ] **Step 6:** Push, then wait for all 5 workflows (incl. Android) to go green on `ui-revamp` (`gh api repos/FuryArchive/fury-book/actions/runs?branch=ui-revamp`). Fix and re-push until green.
- [ ] **Step 7:** Update draft PR #32's description with an editor section (via `gh api` REST), ending with the PR attribution lines.
