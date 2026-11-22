# Навыки into the editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Навыки tab on all platforms. Everything only it could do moves into the editor's Навыки step: own perks, local edits, bulk ranks, «Добрать требования», the plan, and the ЦИ setup. Using ЦИ techniques moves to the sheet's ЦИ meter, and the sheet's perk «Подробнее» becomes one shared dialog.

**Architecture:** Shared Compose in `shared/…/dubl/ui/editor` and `…/ui/sheet`. Models are plain functions over `DublEditorHost` / `EditorPerkContext`, tested with `FakeSheetHost`; composables stay thin. Every mutation already exists in `DevelopmentApplication`; the host only gains delegates.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlin.test; Python source guards in `tools/tests`.

**Spec:** `docs/superpowers/specs/2026-11-20-development-into-editor-design.md`

## Global Constraints

- **Unchanged:** rules, formulas, saved data, transfer format, sync and ЦИ rules. No new application-layer methods.
- **Single sources:** availability, costs and ЦИ come only from `DevelopmentRules`, `DevelopmentAcquisitionPlanner`, `ChiRules` and `CharacterEconomy`.
- **Live save:** every editor change is covered by the visit's restore point and listed in «Изменения за этот заход».
- **Plan scope:** the plan is session-only and lives in `EditorState` for one editor visit.
- **Copy (exact strings):**
  - Buttons and dialogs: «+ Свой навык», «Своя запись», «Редактировать свою запись», «Удалить свою запись», «Локальная правка», «Сбросить к рулбуку», «Сохранить», «Вернуть».
  - Bulk actions: «Взять все оставшиеся ранги», «Добрать требования».
  - Plan: «☆ В план», «★ В плане», «План», «План персонажа», «Взять план».
  - ЦИ: «Приёмы ЦИ», «Использовать», «Восстановить», «Количество», «Настроить в редакторе», «Приёмов ЦИ в каталоге нет.»
- **Local checks:** `/home/claude/gw.sh <log> :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs`. Android compiles only in CI.
- **Python baseline:** the ids in `/tmp/pytest-baseline-ids.txt` may only shrink.

## Review Focus

1. **A local edit that changes an owned perk's cost or max rank.** The change list reprices it on the «локальная правка» line and in the net; «Отменить всё» brings the old price back. Test: Task 5 `localEditRepricesOwnedRanks`.
2. **Deleting an own perk that is owned, planned and open.** The detail closes and the plan drops it. The change list shows one «− name» line with its XP, and «Вернуть» brings back the entry and its rank. Tests: Task 5 `deletedOwnedOwnPerkIsOneLine`, Task 4 `planIgnoresMissingEntries`, Task 7 `removeOwnPerkOffersReturn`.
3. **A ЦИ technique the character can't use** (too little ЦИ, ЦИ off, requirements unmet). The button is disabled with the rules' reason, and tapping does nothing. Test: Task 9 `techniqueThatCannotBeUsedSpendsNothing`.
4. **Bad input in the perk form** (blank name, a malformed «источник=цена» part, max rank 0). The form shows errors and nothing saves. Test: Task 2 `formRejectsBadInput`.
5. **Opening the editor at a perk from the sheet when that perk's kind tab is hidden** (ЦИ pack off) **or the perk is gone.** The editor lands on «Взятые», or on the list with no detail. Test: Task 10 `openAtPerkPicksItsKind`.

---

### Task 1: Perk editing and ЦИ setup on the editor host

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/DublEditorHost.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt` and `webApp/src/jsMain/kotlin/com/furybook/desktop/DesktopAppState.kt`: mark the existing methods `override`; make `canonicalDevelopmentCatalog` an `override val` with a private setter; make `chiCatalog` an override.
- Modify: `app/src/main/java/com/furybook/android/ui/AndroidSheetHost.kt`:
  - delegate to the `CharacterController` methods that already exist (inside the class body);
  - make `canonicalDevelopmentCatalog` an override;
  - `override val chiCatalog = if (chiPackEnabled) ChiCatalogRepository(appContext).load() else ChiCatalog("disabled", emptyList(), emptyList())`.
- Modify: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/FakeSheetHost.kt`:
  - `canonicalDevelopmentCatalog = developmentCatalogOverride ?: loader.loadDevelopment()`;
  - `developmentCatalog` becomes `get() = editorCatalogFor(this, active)`;
  - new constructor param `chiCatalogOverride: ChiCatalog? = null`;
  - with `chiPackEnabled`, also return a CHI `DEVELOPMENT_TABS/DEVELOPMENT_BROWSER` mount, like the existing XP-line mount.
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorCatalogs.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/DublEditorHostTest.kt`

**Interfaces:**
- Produces on `DublEditorHost`:
  - `val canonicalDevelopmentCatalog: DevelopmentCatalog`
  - `val chiCatalog: ChiCatalog`
  - `fun addCustomDevelopment(entry: DevelopmentEntry): String?`
  - `fun updateCustomDevelopment(entry: DevelopmentEntry): Boolean`
  - `fun removeCustomDevelopment(entryId: String)`
  - `fun setDevelopmentOverride(entry: DevelopmentEntry)`
  - `fun resetDevelopmentOverride(entryId: String)`
  - `fun setChiEnabled(enabled: Boolean)`
  - `fun setChiBonusRanks(rank: Int)`
- Produces: `fun editorCatalogFor(host: DublEditorHost, character: DublCharacter): DevelopmentCatalog`. It returns `character.effectiveDevelopmentCatalog(host.canonicalDevelopmentCatalog)`, then `withoutChiContent()` unless `host.chiPackEnabled`. This is the same composition the desktop and Android hosts use.
- **Ruling (spec deviation):** no `changeChi` / `restoreChi` on the host. `changeResource(CHI, delta)` and `restoreMountedResource(CHI)` already route to them on all hosts. Cost if wrong: one more pair of delegates later.

- [ ] **Step 1: Failing tests** in `DublEditorHostTest`:
  - `perkEditingReachesTheApplication`:
    - `val id = host.addCustomDevelopment(EditorFixtures.entry("", "Гадание"))!!`, then assert `host.developmentCatalog.byId(id)!!.name == "Гадание"`;
    - `updateCustomDevelopment(…copy(id = id, name = "Ворожба"))` renames it;
    - `setDevelopmentOverride(keenHearing.copy(cost = 10))`, then `host.developmentCatalog.byId("feat_hear")!!.cost == 10` and `host.canonicalDevelopmentCatalog.byId("feat_hear")!!.cost == 30`;
    - `resetDevelopmentOverride("feat_hear")` brings back 30;
    - `removeCustomDevelopment(id)`, then `byId(id) == null`.
  - `chiSetupReachesTheApplication`: on a `chiPackEnabled = true` host, `setChiEnabled(true)` makes `host.active.chiActive` true, and `setChiBonusRanks(3)` makes `host.active.chiBonusRanks == 3`.
- [ ] **Step 2: Run** `gw.sh … :shared:desktopTest --tests '*DublEditorHostTest*'`. Expected: compile FAIL (unresolved members).
- [ ] **Step 3: Implement** the members on all four hosts, plus `editorCatalogFor`.
- [ ] **Step 4: Run** the test. Expected: PASS. Then `:desktopApp:compileKotlin :webApp:compileKotlinJs`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(editor): perk editing and ЦИ setup on the editor host`.

### Task 2: Perk form model

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorPerkForm.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/EditorPerkFormTest.kt`

**Interfaces:**
- Produces:
  - `data class EditorPerkForm(val name: String, val section: String, val category: String, val costType: DevelopmentCostType, val cost: String, val maxRank: String, val requirements: String, val benefit: String, val notes: String, val tags: String, val accessId: String, val abilityOptions: String, val mechanicsConflict: String, val conflictNote: String, val incomplete: Boolean, val repeatable: Boolean, val perfectRoot: Boolean)`
  - `fun editorPerkForm(entry: DevelopmentEntry): EditorPerkForm`
    - tags are joined with `", "`;
    - options are joined as `"источник=цена"` with `"; "`.
  - `fun editorNewPerk(): DevelopmentEntry`: id `""`, section «Свои», category «Домашние правила», XP, cost 0, maxRank 1. Same as the old `emptyCustomDevelopmentEntry`.
  - `fun EditorPerkForm.errors(): List<String>`, with these messages:
    - «Введите название»;
    - «Стоимость — целое число от 0»;
    - «Макс. ранг — целое число от 1»;
    - «Варианты ОС: «<часть>» — нужно источник=цена». The old dialog silently dropped bad parts; now they block saving.
  - `fun EditorPerkForm.toEntry(base: DevelopmentEntry): DevelopmentEntry?`: null when `errors()` is non-empty; otherwise `base.copy(...)` with the parsed fields.
    - tags: split on `,`, trimmed, blanks dropped;
    - `accessId`: blank → null;
    - options: split on `;`, each part `source=value`, blank parts skipped.

- [ ] **Step 1: Failing tests:**
  - `formRoundTripsAnEntry`: an entry with tags `["ЦИ","Приём"]` and options `[AbilityOption("Мастерство",1), AbilityOption("Магия",2)]`. Then `editorPerkForm(e).abilityOptions == "Мастерство=1; Магия=2"` and `editorPerkForm(e).toEntry(e) == e`.
  - `formRejectsBadInput`, each case asserting `toEntry` is null:
    - blank name → errors contain «Введите название»;
    - `maxRank = "0"` → the max-rank error;
    - `abilityOptions = "Мастерство=1; мусор"` → an error mentioning «мусор».
  - `blankOptionPartsAreSkipped`: `"Мастерство=1; ;"` → one option.
- [ ] **Step 2: Run.** Expected: compile FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): shared perk form model with validation`.

### Task 3: Perk detail — links, bulk actions, origin

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorPerkModels.kt` (`EditorRequirementLine`, `EditorPerkDetail`, `perkDetail`)
- Modify: `EditorPerksStep.kt`, only to keep it compiling (`unlocks` becomes links)
- Test: `EditorPerkModelsTest.kt`

**Interfaces:**
- Produces:
  - `EditorRequirementLine(text, status, targetId: String? = null)`, where `targetId` = `check.targetEntryId` when the catalog has it.
  - `data class EditorPerkLink(val entryId: String, val name: String)`, with `EditorPerkDetail.unlocks: List<EditorPerkLink>`.
  - `enum class EditorPerkOrigin { BOOK, BOOK_EDITED, CUSTOM }` and `EditorPerkDetail.origin`. CUSTOM = id in `character.customDevelopmentEntries`; BOOK_EDITED = id in `developmentOverrides`.
  - `EditorPerkDetail.allRanksAction: EditorPerkAction.Acquire?` and `allRanksLabel: String?`:
    - shown when `maxRank - rank >= 2`, the plan for `targetRank = maxRank` has no unresolved requirements, and `affordable(plan.xpCost, plan.abilityCost)`;
    - label `"Взять все оставшиеся ранги · <costLabel>"`.
  - `EditorPerkDetail.prerequisitesAction: EditorPerkAction.Acquire?` and `prerequisitesLabel: String?`:
    - shown when the detail's `chain` is non-empty and the `includeTarget = false` plan has steps, nothing unresolved, and is affordable;
    - label `"Добрать требования · <costLabel>"`.
  - Both requests use `enforceBudget = false`, like `editorRequest`.
  - `applyPerkAction(host, state, entryId, name, action, chain, doneText: String? = null)`: `doneText` replaces the «Взято: …» toast on success.
- **Ruling:** hide a bulk action rather than show a blocked one. The single take already shows the block reason.

- [ ] **Step 1: Failing tests** (use `EditorFixtures`):
  - `allRanksOfferedOnlyWithTwoLeft`:
    - `keenHearing` (max 2) at rank 0, with requirements met and enough XP → `allRanksLabel` starts with «Взять все оставшиеся ранги» and the request's single target has `targetRank == 2`;
    - at rank 1 → `allRanksAction == null`.
  - `prerequisitesOnlyWhenChainExists`: `lockpick` (needs Взлом 6, hornet has 4) → `prerequisitesAction` targets exclude `feat_lock`; `ranger` (requirements met) → null.
  - `linksCarryTargets`: `branch`'s requirement line pointing at `access_van` has `targetId == "access_van"`; `access`'s `unlocks` contains `EditorPerkLink("feat_van", "Разметать")`.
  - `originMarksCustomAndEdited`: `setDevelopmentOverride(keenHearing.copy(cost = 10))` → BOOK_EDITED; an added custom → CUSTOM; ranger → BOOK.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS, with the existing perk tests still green.
- [ ] **Step 5: Commit** `feat(editor): perk detail links, bulk ranks and «Добрать требования»`.

### Task 4: Plan model

**Files:**
- Modify: `EditorState.kt` (`plannedIds: Set<String>`, cleared by `open`), `EditorPerkModels.kt`
- Test: `EditorPerkModelsTest.kt`, `EditorStateTest.kt`

**Interfaces:**
- Produces:
  - `EditorPerkFilter.PLAN`;
  - `EditorPerkContext.perkGroups(kind, filter, query, keep, planned: Set<String> = emptySet())`;
  - `perkCounts(kind, planned: Set<String> = emptySet())`; the PLAN count is the planned entries of that kind;
  - `data class EditorPlanSummary(val count: Int, val xpCost: Int, val abilityCost: Int, val request: DevelopmentAcquisitionRequest?, val blockReason: String?)`;
  - `fun EditorPerkContext.planSummary(planned: Set<String>): EditorPlanSummary?`:
    - null when nothing planned is still in the catalog;
    - targets are the sorted catalog ids, `enforceBudget = false`;
    - `request` is non-null only when the plan has steps, nothing unresolved, and is affordable;
    - otherwise `blockReason` uses the same reason texts `perkDetail` uses («Не хватает опыта: …», «Не все требования можно закрыть автоматически.», «Уже всё взято»).
  - `fun EditorState.togglePlanned(entryId: String)`.

- [ ] **Step 1: Failing tests:**
  - `planFilterShowsOnlyPlanned`: planned `{feat_hear}`, kind REGULAR, PLAN → exactly one card, `feat_hear`.
  - `planSummaryCostsTheWholePlan`: planned `{feat_hear, feat_ranger}` → `xpCost` equals the planner's cost for both targets in one request, and `request!!.targets.map { it.entryId } == listOf("feat_hear", "feat_ranger")`.
  - `planIgnoresMissingEntries`: planned `{"gone"}` → `planSummary == null`.
  - `openClearsThePlan` (EditorStateTest): `togglePlanned("feat_hear")`, then `open(...)` → `plannedIds` is empty.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): character plan in the editor`.

### Task 5: Change list — own perks, local edits, ЦИ, per-side pricing

**Files:**
- Modify: `EditorChanges.kt`, `EditorScreen.kt` (`EditorFrameData` passes `catalogFor = { editorCatalogFor(host, it) }`)
- Test: `EditorChangesTest.kt`

**Interfaces:**
- Produces: `editorChanges(before, after, catalogFor: (DublCharacter) -> DevelopmentCatalog, includeChi, effectNames = emptyMap())`. This replaces the `catalog` parameter; update the existing tests with `{ EditorFixtures.catalog }`.
- **Pricing:** each priced character uses its own catalog: `cost(m) = breakdown(m, catalogFor(m)).spentXp − breakdown(b, catalogFor(b)).spentXp`, and the net uses `catalogFor(b)` / `catalogFor(a)`. Names come from `catalogFor(a).byId(id) ?: catalogFor(b).byId(id)`.
- **New lines:**
  - Own perks (`customDevelopmentEntries` by id):
    - added → `"+ <name>"`;
    - removed → `"− <name>"`, priced with `cost(b.copy(customDevelopmentEntries = a's, development = b.development - id))`, and that id gets no development line;
    - changed → `"<name>: запись изменена"`, priced as `b` with that entry swapped.
  - Overrides:
    - added or changed → `"<name>: локальная правка"`, priced as `b` with that override applied;
    - removed → `"<name>: правка сброшена"`, priced the same way.
  - ЦИ: `"ЦИ включено"` / `"ЦИ выключено"` from `chiEnabled`; `"ЦИ: доп. ранги a → b"` priced with `cost(b.copy(chiBonusRanks = a.chiBonusRanks))`. `chiCurrent` is not listed.

- [ ] **Step 1: Failing tests:**
  - `localEditRepricesOwnedRanks`: before = hornet; after = hornet with `developmentOverrides = mapOf("feat_ath" to athleticism.copy(cost = 100))` → line `"Атлетичность: локальная правка"` with `xpSpent == 60` (owned rank 1, 40 → 100), and `netSpent == 60`.
  - `deletedOwnedOwnPerkIsOneLine`: before has a custom «Гадание» (cost 25) at rank 1; after has neither the entry nor the rank → lines == `[EditorChangeLine("− Гадание", -25)]`.
  - `chiLines`: `chiEnabled` false → true and `chiBonusRanks` 0 → 2 with `includeChi = true` → contains `"ЦИ включено"` and `"ЦИ: доп. ранги 0 → 2"` with `xpSpent == 2 * CharacterEconomy.CHI_BONUS_RANK_XP`.
  - The existing `linksForEveryChangeUndoAllWouldRevert`-style tests still pass.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): change list covers own perks, local edits and ЦИ`.

### Task 6: Навыки step UI — links, bulk buttons, plan

**Files:**
- Modify: `EditorPerksStep.kt`
- Test: none new. The model is covered by Tasks 3–4. Verify with screens.

**Interfaces:**
- Consumes: Tasks 3–4.
- **Layout:**
  - The PLAN chip «План N» sits after «Почти».
  - When `planSummary` is non-null, a gold card heads the list: «План персонажа · N», «Осталось X XP · Y ОС», and «Взять план». The button is enabled when `request != null`; otherwise the block reason shows. It calls `host.acquireDevelopment` and toasts «План взят» or the rules' message.
  - The detail gets a ☆/★ toggle next to the pill.
  - Requirement lines with a `targetId`, and each «Открывает» name, open via `state.openPerk(context, id)`. These are clickable `Role.Button` texts in `goldText`, at least 44dp tall on touch layouts (`editorControl`).
  - The bulk actions are outline buttons in the action row, using `doneText` «Взяты все ранги: <name>» / «Требования добраны: <name>».

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Compile** desktop and web. Expected: BUILD SUCCESSFUL. Then run the full `:shared:desktopTest`. Expected: PASS.
- [ ] **Step 3: Screens** at 1440 and 360: plan two perks, then «Взять план»; open a link from requirements; «Взять все оставшиеся ранги» on Острый слух.
- [ ] **Step 4: Commit** `feat(editor): plan, links and bulk actions in the Навыки step`.

### Task 7: Own perks and local edits in the editor

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorPerkFormDialog.kt` (`FuryDialogScaffold`, scrolling fields, errors under the save button, primary «Сохранить» disabled while `errors()` is non-empty)
- Modify: `EditorPerkActions.kt`, `EditorPerksStep.kt` (header «+ Свой навык»; detail bottom row by `origin`)
- Test: `EditorStateTest.kt`

**Interfaces:**
- Consumes: Task 1 host, Task 2 form, Task 3 `origin`.
- Produces in `EditorPerkActions.kt`:
  - `fun editorRemoveOwnPerk(host, state, entryId, name)`:
    - takes `before = host.restorePoint()`, calls `removeCustomDevelopment`, then `state.selectChoices(null)`;
    - removes the id from `plannedIds`;
    - `flash("Удалено: <name>", undoableAt = host.restorePoint(), undoTo = before)`.
  - `fun editorResetPerk(host, state, entryId, name)`: same pattern with `resetDevelopmentOverride`, toast «Сброшено к рулбуку: <name>». The detail stays open.
  - `fun editorSavePerk(host, state, form, base, mode: EditorPerkFormMode): Boolean`, with `enum class EditorPerkFormMode { NEW, EDIT_OWN, LOCAL_EDIT }`:
    - NEW → `addCustomDevelopment`, then `openPerk` on the new id with a fresh `EditorPerkContext(host)`;
    - EDIT_OWN → `updateCustomDevelopment`;
    - LOCAL_EDIT → `setDevelopmentOverride`;
    - toast «Сохранено: <name>»; returns false when `toEntry` is null or the host refuses.
- **Detail bottom row:**
  - BOOK → «Локальная правка»;
  - BOOK_EDITED → «Локальная правка» and «Сбросить к рулбуку»;
  - CUSTOM → «Редактировать свою запись» and «Удалить свою запись».

- [ ] **Step 1: Failing tests** (EditorStateTest):
  - `removeOwnPerkOffersReturn`: add a custom perk and set it to rank 1; plan it and select it. Then `editorRemoveOwnPerk(...)` → the entry is gone, `selectedEntryId == null`, and it is not in `plannedIds`. Then `state.undoToast(host)` → the entry is back at rank 1.
  - `resetPerkOffersReturn`: an override with cost 10, then `editorResetPerk` → cost 30; `undoToast` → cost 10.
  - `newPerkOpensInDetail`: `editorSavePerk(host, state, editorPerkForm(editorNewPerk()).copy(name = "Гадание"), editorNewPerk(), NEW)` → true, `step == PERKS`, and `selectedEntryId` is the new id.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the actions, then the dialog and the buttons.
- [ ] **Step 4: Run.** Expected: PASS. Compile desktop and web.
- [ ] **Step 5: Screens** at 1440 and 360: «+ Свой навык» → save → detail; «Локальная правка» on a book perk → «Сбросить к рулбуку» → «Вернуть»; delete own perk → «Вернуть».
- [ ] **Step 6: Commit** `feat(editor): own perks and local edits in the Навыки step`.

### Task 8: ЦИ card in the editor

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorChiCard.kt` (model + composable)
- Modify: `EditorPerksStep.kt` (the card heads the list when `perkKind == CHI`)
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/EditorChiCardTest.kt`

**Interfaces:**
- Produces:
  - `data class EditorChiTechniqueLine(val name: String, val school: String, val cost: Int, val action: String, val effect: String, val status: String)`, where `status` is the reason from `ChiRules`, or «Можно использовать».
  - `data class EditorChiCard(val active: Boolean, val locked: Boolean, val lockReason: String?, val current: Int, val maximum: Int, val bonusRanks: Int, val rankXp: Int, val maximumText: String, val techniques: List<EditorChiTechniqueLine>)`
  - `fun editorChiCard(host: DublEditorHost): EditorChiCard?`:
    - null unless `editorShowsChi(host)`;
    - `locked = active.chiAutomaticAccess`;
    - `lockReason` = «Ресурс открыт способностью «Внутренняя ЦИ» и остаётся активным, пока способность изучена.»;
    - `maximumText` = «Максимум: база B + запас R + развитие P = M» (old card wording);
    - `rankXp = CharacterEconomy.CHI_BONUS_RANK_XP`.
- **Composable:**
  - a switch (disabled when locked, reason below);
  - when active: a stepper `[−] R [+]` from 0 to 10, «· N XP за ранг», and the max text;
  - when inactive: «Включение ресурса само по себе не расходует XP и не выдаёт способности автоматически.»;
  - then a collapsible «Приёмы ЦИ · N», read-only lines, or «Приёмов ЦИ в каталоге нет.»

- [ ] **Step 1: Failing tests** (`FakeSheetHost(chiPackEnabled = true, chiCatalogOverride = ChiCatalog("t", emptyList(), listOf(technique)))`):
  - `chiCardHiddenWithoutPack`: pack off → null.
  - `chiSwitchLockedByInternalChi`: the character owns `DevelopmentEffectIds.INTERNAL_CHI` (add an entry with that id to the catalog override) → `locked == true` and `lockReason != null`.
  - `chiCardShowsMaximumAndTechniques`: enabled, bonus 2 → `maximumText == "Максимум: база ${c.chiBaseMaximum} + запас 2 + развитие 0 = ${c.chiMaximum}"` (c = `host.active`); `techniques.single().status` is ChiRules' reason when chi is 0 and the technique costs 2 («Недостаточно ЦИ: нужно 2»).
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS. Compile.
- [ ] **Step 5: Commit** `feat(editor): ЦИ setup card in the Навыки step`.

### Task 9: Sheet ЦИ panel

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetChiPanel.kt` (model + `SheetChiDialog`)
- Modify:
  - `SheetScreen.kt`: `SheetDialog.Vital` whose vital has `resource == CHI` → `SheetChiDialog`;
  - `SheetPlayHeader.kt`: on the phone, a tap on the ЦИ `ResourceChip` opens `SheetDialog.Vital(id)` instead of the inline adjuster;
  - tray and tablet rows already open the dialog via `openVital`.
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetChiPanelTest.kt`

**Interfaces:**
- Produces:
  - `data class SheetChiTechnique(val id: String, val name: String, val school: String, val cost: Int, val action: String, val effect: String, val canUse: Boolean, val reason: String)`
  - `data class SheetChiPanel(val current: Int, val maximum: Int, val techniques: List<SheetChiTechnique>)`
  - `fun sheetChiPanel(host: DublEditorHost): SheetChiPanel`, built from `ChiRules(host.active, host.developmentCatalog)`.
  - `fun sheetUseChiTechnique(host: DublEditorHost, techniqueId: String): Boolean`: re-checks availability, then `host.changeResource(CHI, -chiCost)`; false and no change otherwise.
- **Dialog:**
  - title «ЦИ», subtitle «Сейчас C из M»;
  - the «Количество» field with «Уменьшить» / «Увеличить», using `vitalAmountDelta`;
  - −1 / +1 and «Восстановить» (`restoreMountedResource(CHI)`);
  - then «Приёмы ЦИ»: each technique as `name · school`, `cost ЦИ · action`, the effect, and «Использовать» (disabled with `reason` shown when `!canUse`). Empty list → «Приёмов ЦИ в каталоге нет.»

- [ ] **Step 1: Failing tests:**
  - `usingATechniqueSpendsItsCost`: active ЦИ 5/7, technique cost 2, requirements empty → true, then chi 3.
  - `techniqueThatCannotBeUsedSpendsNothing`: chi 1, cost 2 → false, chi still 1, and `reason == "Недостаточно ЦИ: нужно 2"`. Same with ЦИ off → false, and reason «Запас ЦИ недоступен».
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS. Compile.
- [ ] **Step 5: Screens**, phone 360 and desktop 1440: tap the ЦИ meter, use a technique, «Восстановить».
- [ ] **Step 6: Commit** `feat(sheet): ЦИ panel with techniques behind the ЦИ meter`.

### Task 10: Shared perk «Подробнее» and opening the editor at a perk

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetDevelopment.kt`:
  - `SheetDevelopmentDetailsDialog(host, entryId, onConfigure, onDismiss)`;
  - adds «Открывает» names (`DevelopmentAcquisitionPlanner.unlocks`);
  - primary «Настроить в редакторе», secondary «Закрыть».
- Modify:
  - `SheetScreen.kt`: drop the `developmentDetails` parameter and route to the shared dialog;
  - `SheetScreenState.kt`: `openEditor(forceBasics, skillId = null, entryId = null)`; `onEditorOpening: ((Boolean, String?, String?) -> Unit)?`;
  - `EditorState.kt`: `open(host, forceBasics, skillId = null, entryId = null)`. An `entryId` goes to `openPerk(EditorPerkContext(host), entryId)` when the catalog has it; otherwise step PERKS with no selection.
- Modify call sites: desktop and web `Main.kt` (stop passing `developmentDetails`), Android `DublApp.kt` (same).
- Delete: `desktopApp/…/screens/SheetDevelopmentDetails.kt`, `webApp/…/screens/SheetDevelopmentDetails.kt`
- Test: `SheetScreenStateTest.kt`, `EditorStateTest.kt`

- [ ] **Step 1: Failing tests:**
  - `editorOpensAtPerkFromSheet` (SheetScreenStateTest): with a recorder set, `openEditor(false, entryId = "feat_hear")` → recorded `(false, null, "feat_hear")` and `editing == true`.
  - `openAtPerkPicksItsKind` (EditorStateTest):
    - `open(host, false, entryId = "feat_hear")` → `step == PERKS`, `perkKind == REGULAR`, `selectedEntryId == "feat_hear"`;
    - an owned ЦИ entry with the pack off → `perkKind == OWNED`;
    - `entryId = "gone"` → `step == PERKS` and `selectedEntryId == null`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement**, then delete the two desktop/web files.
- [ ] **Step 4: Run.** Expected: PASS. Compile desktop and web.
- [ ] **Step 5: Commit** `feat(sheet): one perk details dialog with «Настроить в редакторе»`.

### Task 11: Remove the Навыки tab

**Files:**
- Modify:
  - desktop and web `Main.kt` and their `DesktopSection` enum files: drop `DEVELOPMENT`;
  - `app/…/ui/DublApp.kt`:
    - drop `AppSection.FEATS`, `pendingSection` and the loading gate;
    - `AppSection.primary` becomes `this != AppSection.MORE`, so the phone bar is Лист, Магия, Вещи, Кампания;
    - Кампания's `onBack` still goes to MORE.
- Delete: desktop and web `screens/DevelopmentScreen.kt`, Android `ui/screens/FeatsScreen.kt`, plus helpers only they use (grep each before deleting).
- Modify the Python guards:
  - repoint function guards to the editor/sheet files: `test_development_bulk_ranks.py` (→ `EditorPerkModels.kt` `targetRank = entry.maxRank`), `test_development_override_ui.py` (→ `EditorPerkActions.kt` / `EditorPerkFormDialog.kt`), `test_compose_development_chi_parity.py` (→ `EditorChiCard.kt`, `SheetChiPanel.kt`), `test_fcp_contract.py` and `test_rules_boundary_contract.py` (→ the shared editor files that read the CHI mounts and the rules), `test_sheet_notes_development.py` (shared dialog);
  - retire guards that only pin old layout, case by case: `test_android_development_loading_gate.py`, `…_performance.py`, `…_cockpit.py`, `…_navigation_loading.py`, `…_stability.py`, `test_desktop_development_cockpit.py`, `test_development_cockpit_visual_polish.py`, and the development parts of `test_compose_*_parity.py`, `test_desktop_polish_dialogs.py`, `test_desktop_ui_structural_redesign.py`, `test_android_0_6_1_ui.py`;
  - **Ruling rule:** a guard that pins a function is repointed, never deleted; each retirement is ledgered.
- Modify: `CHANGELOG.md` (Unreleased: «Навыки tab removed (revamp stage 4)»)
- Create: `tools/tests/test_development_tab_removed.py`

- [ ] **Step 1: Failing guard** `test_development_tab_removed.py`:
  - `test_no_development_section_on_any_platform`: `DEVELOPMENT` is not in the desktop/web section enums or the `Main.kt` section lists; `AppSection.FEATS` and `FeatsScreen` are not in `DublApp.kt`.
  - `test_old_development_screens_are_gone`: the three screen files and the two `SheetDevelopmentDetails.kt` files do not exist.
  - `test_moved_functions_live_in_shared_code`:
    - `EditorPerkActions.kt` contains `removeCustomDevelopment`, `resetDevelopmentOverride`, `addCustomDevelopment`, `updateCustomDevelopment`, `setDevelopmentOverride`;
    - `EditorChiCard.kt` contains `setChiEnabled` and `setChiBonusRanks`;
    - `SheetChiPanel.kt` contains `ChiRules` and `restoreMountedResource`;
    - `EditorPerkModels.kt` contains `includeTarget = false` and `targetRank = entry.maxRank`.
- [ ] **Step 2: Run** `python3 -m pytest -q tools/tests/test_development_tab_removed.py -p no:cacheprovider`. Expected: FAIL.
- [ ] **Step 3: Remove** the sections, delete the files, then repoint or retire the guards.
- [ ] **Step 4: Run** the full local checks. Expected: BUILD SUCCESSFUL. Then the Python suite, diffed against the baseline. Expected: no new ids.
- [ ] **Step 5: Commit** `feat: remove the Навыки tab; its functions live in the editor and on the sheet`.

### Task 12: Verify, review, ship

- [ ] **Step 1: Screens** at desktop 1440 and 1100, tablet 834, phone 360, plus Пергамент.
  - Sheet path: perk «Подробнее» → «Настроить в редакторе» → detail.
  - Editor paths: plan → «Взять план»; local edit → change list repriced → «Отменить всё»; own perk add / edit / delete → «Вернуть»; ЦИ on → ranks → max text.
  - ЦИ panel: amount field, technique use.
- [ ] **Step 2:** Full local checks, plus the Python diff.
- [ ] **Step 3:** One fresh reviewer (subagent, opus) over the branch diff since this plan's base, with the spec, this plan and the Review Focus. Fix Critical/Important findings with RED→GREEN tests; ledger the minors.
- [ ] **Step 4:** Push, then wait for all 5 workflows to go green. Update the PR #32 description with a stage 4 section.
