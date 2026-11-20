# Умения into the editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Умения tab on all platforms and move everything only it could do into the editor's Умения step (skill detail panel, hidden list) and the sheet's roll panel («Об умении», allowed-attribute roll).

**Architecture:** Shared Compose in `shared/…/dubl/ui/editor` and `…/ui/sheet`. Models are plain functions over `DublEditorHost` (tested with `FakeSheetHost`); composables stay thin. All mutations already exist in `SkillsApplication`; this plan only adds host methods that delegate to them.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlin.test; Python source guards in `tools/tests`.

**Spec:** `docs/superpowers/specs/2026-11-18-skills-into-editor-design.md`

## Global Constraints

- Rules, formulas, saved data, transfer format, sync: unchanged. No new application-layer methods.
- Every bonus/breakdown comes from `skillCalculationOptions` / `skillCalculationForRoll` / `SkillCalculation.formulaText`.
- Live save; every editor change is covered by the visit's restore point and listed in «Изменения за этот заход».
- Roll attributes (owner ruling): the skill's allowed attributes + «другая…» for the rest; preferred attribute saved only for allowed picks.
- «К рулбуку» only for `ResolvedSkill.isBuiltIn`; «Удалить» only for `isDynamic`.
- Copy: «Об умении», «Настроить в редакторе», «Скрытые · N», «Скрыть из списка», «Удалить», «К рулбуку», «Локальные правки», «Автоматизация правил», «Допустимые характеристики», «Постоянная поправка», «Особое правило / условный бонус», «другая…», «Вернуть», «Вернуть все».
- Local checks: `/home/claude/gw.sh <log> :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs`; Android compiles only in CI.
- Python baseline (37 failed + 6 errors, ids in `/tmp/pytest-baseline-ids.txt`) may only shrink.

## Review Focus

1. **Typing in a text setting, then «Отменить всё», «Вернуть» or a character switch** — the field must never write stale text back or onto another character (same guarantees as the name field). Test: Task 5 `skillTextDraftDropsTypingAfterRestore`, `skillTextDraftNeverWritesToAnotherCharacter`.
2. **First setting change on an unranked built-in skill** — creates its saved entry; the change list must say «настройки изменены», not «+ name», and «Отменить всё» must remove the entry. Test: Task 4 `settingsOnUnrankedBuiltInAreNotAnAddedSkill`.
3. **Removing a custom skill's primary attribute** — the skill moves group; the panel stays open on it and the list keeps it. Test: Task 2 `toggledAttributesKeepPrimaryAndOrder`, Task 3 `openSkillStaysListedAcrossFilters`.
4. **Saved preferred attribute that is no longer allowed** — the roll starts on the stock attribute; a «другая…» pick is never saved. Test: Task 7 `rollStartsOnAllowedPreferredOrStock`, `onlyAllowedPicksAreRemembered`.
5. **Hide / delete from the open panel, then «Вернуть» or «Отменить всё»** — the skill comes back with its rank and settings. Test: Task 6 `returnUndoesHideAndDelete`.

---

### Task 1: Skill settings on the editor host

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/DublEditorHost.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt`, `webApp/src/jsMain/kotlin/com/furybook/desktop/DesktopAppState.kt` (mark existing methods `override`)
- Modify: `app/src/main/java/com/furybook/android/ui/AndroidSheetHost.kt` (delegate to `CharacterController`; methods go **inside the class body**)
- Modify: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/FakeSheetHost.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/DublEditorHostTest.kt`

**Interfaces:**
- Produces on `DublEditorHost`: `setSkillAttributes(skillId: String, attributes: List<AttributeId>)`, `setSkillModifier(skillId: String, modifier: Int)`, `setSkillFormulaNote(skillId: String, note: String)`, `setSkillNameOverride(skillId: String, name: String)`, `setSkillDescriptionOverride(skillId: String, description: String)`, `setSkillCategoryOverride(skillId: String, category: SkillCategory?)`, `setSkillUntrainedOverride(skillId: String, rule: UntrainedRule?)`, `setSkillAutoOverrides(skillId: String, auto6: String?, auto12: String?)`, `resetSkillDefinitionOverrides(skillId: String)`, `hideSkill(skillId: String)`, `restoreSkill(skillId: String)`, `restoreAllSkills()`, `setSkillEffectEnabled(effectId: String, enabled: Boolean)`, `deleteDynamicSkill(skillId: String)`, `setPreferredSkillAttribute(skillId: String, attribute: AttributeId?)`.

- [ ] **Step 1: Failing test** `skillSettingsReachTheApplication` in `DublEditorHostTest`: on a `FakeSheetHost` with a built-in skill, call `setSkillModifier("stealth", 2)`, `setSkillFormulaNote("stealth", "+2 в темноте")`, `hideSkill("stealth")`, `setPreferredSkillAttribute("stealth", AttributeId.PERCEPTION)`; assert `host.active.skills["stealth"]!!.modifier == 2`, `formulaNote == "+2 в темноте"`, `"stealth" in host.active.hiddenSkillIds`, `host.extras.preferredSkillAttributes["stealth"] == AttributeId.PERCEPTION`; then `restoreSkill("stealth")` and assert it is no longer hidden.
- [ ] **Step 2: Run** `gw.sh … :shared:desktopTest --tests '*DublEditorHostTest*'` → Expected: compile FAIL (unresolved members).
- [ ] **Step 3: Add the members to `DublEditorHost`, implement in all four hosts** (desktop/web already have them; Android delegates to `controller.*`; fake delegates to `application.skills.*` / `application.sheet.setPreferredSkillAttribute`).
- [ ] **Step 4: Run** the test → PASS; `:desktopApp:compileKotlin :webApp:compileKotlinJs` → BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(editor): skill settings on the editor host`.

### Task 2: Skill detail model

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillModels.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/EditorSkillModelsTest.kt`

**Interfaces:**
- Produces:
  - `data class EditorBonusLine(val attribute: AttributeId, val title: String, val breakdown: String, val total: Int?, val unavailableReason: String)`
  - `data class EditorSkillEffect(val id: String, val source: String, val text: String, val enabled: Boolean)`
  - `data class EditorSkillDetail(val skillId: String, val name: String, val origin: String?, val description: String, val headline: String, val rank: Int, val bonus: List<EditorBonusLine>, val attributes: List<AttributeId>, val modifier: Int, val formulaNote: String, val localName: String, val localDescription: String, val category: SkillCategory, val untrained: UntrainedRule, val auto6: String, val auto12: String, val effects: List<EditorSkillEffect>, val canResetToBook: Boolean, val canDelete: Boolean, val hidden: Boolean)`
  - `fun editorSkillDetail(host: DublEditorHost, skillId: String): EditorSkillDetail?` (null when the skill no longer resolves)
  - `fun editorToggledAttributes(current: List<AttributeId>, attribute: AttributeId): List<AttributeId>`
- `origin`: «Своё» for custom, «Специализация» for template-based dynamic, null for built-in (same rule as the old Android `skillOriginLabel`). `headline`: `"${stock.shortTitle} · ранг $rank · ${signed bonus}"`. `breakdown`: contributions joined `" + "` as `"<label> <signed>"`. `bonus` lists `skillCalculationOptions(skill)` (allowed attributes only).

- [ ] **Step 1: Failing tests** in `EditorSkillModelsTest` (use `EditorFixtures.hornet`):
  - `detailShowsBreakdownPerAllowedAttribute`: give stealth attributes `[DEXTERITY, PERCEPTION]`; assert `bonus.map { it.attribute } == listOf(DEXTERITY, PERCEPTION)` and the DEXTERITY line's `total == 8 + 5` and its `breakdown` contains `"+5"`.
  - `customAndBuiltInFlags`: built-in stealth → `canResetToBook == true`, `canDelete == false`, `origin == null`; after `host.addCustomSkill("Гадание", "", listOf(INTELLIGENCE), UntrainedRule.YES)` the new id → `canResetToBook == false`, `canDelete == true`, `origin == "Своё"`.
  - `toggledAttributesKeepPrimaryAndOrder`: `editorToggledAttributes([DEX, PER], STR) == [DEX, PER, STR]`; `editorToggledAttributes([DEX, PER], PER) == [DEX]`; `editorToggledAttributes([DEX], DEX) == [DEX]` (last one stays); `editorToggledAttributes([DEX, PER], DEX) == [PER]`.
  - `missingSkillHasNoDetail`: `editorSkillDetail(host, "nope") == null`.
- [ ] **Step 2: Run** → compile FAIL.
- [ ] **Step 3: Implement** in `EditorSkillModels.kt` (effects from `SkillEffectRules(host.active, host.developmentCatalog, host.skillEffectCatalog).configuredForSkill(skill)`, `enabled = id !in host.active.disabledSkillEffectIds`).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(editor): skill detail model`.

### Task 3: Умения step list — description search, category, open skill kept

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorModels.kt` (`editorSkillGroups`)
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorState.kt`
- Test: `EditorModelsTest.kt`, `EditorStateTest.kt`

**Interfaces:**
- Consumes: Task 2 nothing; existing `editorSkillGroups(host, before, rankedOnly, query)`.
- Produces: `editorSkillGroups(host: DublEditorHost, before: DublCharacter, rankedOnly: Boolean, query: String, category: SkillCategory? = null, keep: String? = null): List<EditorSkillGroup>`; `EditorState.skillCategory: SkillCategory?`, `EditorState.skillPanelId: String?`, `EditorState.open(host, forceBasics, skillId: String? = null)` (skillId ⇒ step SKILLS, panel open, `skillsRankedOnly = false`); `open` resets `skillCategory`/`skillPanelId` otherwise.

- [ ] **Step 1: Failing tests**:
  - `searchMatchesDescriptions` (EditorModelsTest): query a word that appears only in a fixture skill's description → that skill is in the result.
  - `categoryFilterKeepsOnlyThatCategory`: `category = SkillCategory.COMBAT` → every row resolves to a COMBAT skill.
  - `openSkillStaysListedAcrossFilters`: `rankedOnly = true, query = "zzz", keep = "lockpicking"` → `lockpicking` still listed.
  - `openAtSkill` (EditorStateTest): `state.open(host, forceBasics = false, skillId = "stealth")` → `step == SKILLS`, `skillPanelId == "stealth"`, `skillsRankedOnly == false`; a plain `open` after it clears `skillPanelId`.
- [ ] **Step 2: Run** → FAIL (compile).
- [ ] **Step 3: Implement**: description via `host.active.resolveSkill(row.skillId)?.description`; filters skip the `keep` row; order unchanged (visit-start rank).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(editor): skill search by description, category filter, open skill kept`.

### Task 4: Change list covers skill settings

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorChanges.kt`, `EditorScreen.kt` (pass effect names)
- Test: `EditorChangesTest.kt`

**Interfaces:**
- Produces: `editorChanges(before, after, catalog, includeChi, effectNames: Map<String, String> = emptyMap())`.
- Lines: `"<name>: настройки изменены"` when a skill's saved entry differs in anything but `rank` (missing entry ≡ `CharacterSkill(id, definitionId = id)` for built-ins); `"Скрыто: <name>"` / `"Возвращено: <name>"` from `hiddenSkillIds`; `"Автоматизация: <source> выкл."` / `"… вкл."` from `disabledSkillEffectIds`; `"+ <name>"` / `"− <name>"` only for dynamic skills (built-ins never get "+").

- [ ] **Step 1: Failing tests**:
  - `settingsOnUnrankedBuiltInAreNotAnAddedSkill`: after = hornet with `"riding" to CharacterSkill(id = "riding", definitionId = "riding", modifier = 1)` (not in before) → lines == `["Верховая езда: настройки изменены"]`.
  - `hiddenAndEffectLines`: hidden adds `"stealth"`; disabled adds `"fx1"` with `effectNames = mapOf("fx1" to "Ловкие пальцы")` → contains `"Скрыто: Скрытность"` and `"Автоматизация: Ловкие пальцы выкл."`.
  - existing `linesForEveryChangeUndoAllWouldRevert` still passes.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; `EditorFrameData` passes `host.skillEffectCatalog.effects.associate { it.id to it.sourceName }`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(editor): change list covers skill settings`.

### Task 5: Text settings draft

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillTextDraft.kt`
- Test: `EditorSkillTextDraftTest.kt`

**Interfaces:**
- Produces: `class EditorSkillTextDraft(characterId: String, detail: EditorSkillDetail, generation: Int)` with state fields `note`, `name`, `description`, `auto6`, `auto12`; `fun edit(note: String = this.note, name: String = this.name, description: String = this.description, auto6: String = this.auto6, auto12: String = this.auto12)`; `fun save(host: DublEditorHost, generation: Int)` (writes only changed fields via the Task 1 setters; skips when the active character or generation differs, or nothing was typed); `fun sync(detail: EditorSkillDetail, generation: Int)` (same rules as `EditorIdentityDraft.sync`).

- [ ] **Step 1: Failing tests** (mirror `EditorIdentityDraftTest`): `typedNoteIsSaved`, `skillTextDraftNeverWritesToAnotherCharacter` (createCharacter, then save → new character untouched), `skillTextDraftDropsTypingAfterRestore` (edit, `host.restore(point)`, save with generation+1 → note unchanged), `outsideChangeShowsWhenNothingPending`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(editor): debounced text settings for skills`.

### Task 6: Skill detail panel, hidden list, undo for hide/delete/reset

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillPanel.kt` (panel + `EditorHiddenSkillsDialog`)
- Modify: `EditorSkillsStep.kt` (row name opens panel; toolbar «Скрытые · N»; category chips; split/overlay like `EditorPerksStep` via `editorPerksSplit`; empty state with «Сбросить фильтры» / «Открыть скрытые»)
- Modify: `EditorState.kt`, `EditorScreen.kt` (toast «Вернуть» uses an undo point when set)
- Test: `EditorStateTest.kt`

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: `EditorState.flash(text: String, undoableAt: CharacterRestorePoint? = null, undoTo: CharacterRestorePoint? = null)`; `fun EditorState.undoToast(host: DublEditorHost): Boolean` — restores `undoTo` when set, else `host.undoLast()`; marks restored on success.

- [ ] **Step 1: Failing test** `returnUndoesHideAndDelete` (EditorStateTest): add a custom skill with rank 2; `val before = host.restorePoint()`; `host.deleteDynamicSkill(id)`; `state.flash("Удалено", undoableAt = host.restorePoint(), undoTo = before)`; `state.undoToast(host)` → skill back with rank 2; same for `hideSkill`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement** `undoToast`, then the panel per spec (sections: header with origin pill and headline; description; «Бонус проверки» lines; «Допустимые характеристики» chips using `editorToggledAttributes`; «Постоянная поправка» stepper; «Особое правило / условный бонус» field; collapsible «Локальные правки» (name, description, category chips, «Без обучения» chips, Auto 6, Auto 12, «К рулбуку» when `canResetToBook`); «Автоматизация правил» switches when any; bottom «Скрыть из списка» and «Удалить» when `canDelete`). Text fields use `EditorSkillTextDraft` with `state.restoreGeneration`. Hide/Delete/К рулбуку take `host.restorePoint()` first, act, flash with `undoTo`, and hide/delete close the panel.
- [ ] **Step 4: Run** tests → PASS; compile desktop + web.
- [ ] **Step 5: Screens** at 1440, 1100, 360: open panel, change attribute, type a note, hide → «Вернуть», hidden dialog «Вернуть все».
- [ ] **Step 6: Commit** `feat(editor): skill detail panel and hidden skills`.

### Task 7: Roll offers allowed attributes + «другая…», remembers allowed picks

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetRollSession.kt` (`resolveSkillRoll`, `SheetRollOptions`)
- Modify: `SheetRollPanel.kt` (chips + «другая…»; new `onAttributePicked: (skillId: String, attribute: AttributeId) -> Unit = { _, _ -> }`)
- Modify: `SheetScreen.kt` (wire `onAttributePicked` to `host.setPreferredSkillAttribute` when `sheetRemembersAttribute`)
- Test: `SheetRollSessionTest.kt`

**Interfaces:**
- Produces: `SheetRollOptions.otherAttributeChoices: List<AttributeId> = emptyList()`; `fun sheetRememberedAttribute(skill: ResolvedSkill, extras: CharacterSheetExtras): AttributeId` (preferred if allowed, else `stockAttribute`); `fun sheetRemembersAttribute(skill: ResolvedSkill, attribute: AttributeId): Boolean = attribute in skill.attributes`.

- [ ] **Step 1: Failing tests**:
  - `skillRollOffersAllowedThenOthers`: stealth attributes `[DEXTERITY, PERCEPTION]` → `attributeChoices == [DEXTERITY, PERCEPTION]`, `otherAttributeChoices` = the other six in `AttributeId.entries` order.
  - `rollStartsOnAllowedPreferredOrStock`: preferred PERCEPTION → start PERCEPTION; preferred STRENGTH (not allowed) → start = stock.
  - `onlyAllowedPicksAreRemembered`: `sheetRemembersAttribute(stealth, PERCEPTION) == true`, `(stealth, STRENGTH) == false`.
  - A one-off pick still rolls: request.attribute = STRENGTH → `checkBonusLabel` ends with «Сила».
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; panel shows allowed chips, a «другая…» chip that reveals `otherAttributeChoices` (stays revealed when the request's attribute is one of them).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat(sheet): skill rolls offer allowed attributes plus «другая…»`.

### Task 8: «Об умении» and opening the editor at a skill

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetSkillInfoDialog.kt`
- Modify: `SheetRollPanel.kt` («Об умении» link for skill subjects; `onSkillInfo: (String) -> Unit = {}`), `SheetScreen.kt`, `SheetScreenState.kt`
- Test: `SheetScreenStateTest.kt`

**Interfaces:**
- Consumes: Task 2 `editorSkillDetail`, Task 3 `EditorState.open(host, forceBasics, skillId)`.
- Produces: `SheetDialog.SkillInfo(skillId: String)`; `SheetScreenState.openEditor(forceBasics: Boolean, skillId: String? = null)`; `onEditorOpening: ((forceBasics: Boolean, skillId: String?) -> Unit)?`.

- [ ] **Step 1: Failing test** `editorOpensAtSkillFromRollPanel`: set `onEditorOpening` recorder; `state.openEditor(false, skillId = "stealth")` → recorded `(false, "stealth")`, `editing == true`, any open roll closed.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**: dialog (`FuryDialogScaffold`) shows name, origin, description, bonus lines, rule note, «Без обучения», Auto 6/12; one action «Настроить в редакторе» → closes roll + dialog, `openEditor(false, skillId)`.
- [ ] **Step 4: Run** → PASS; compile.
- [ ] **Step 5: Commit** `feat(sheet): «Об умении» from the roll panel`.

### Task 9: Remove the Умения tab

**Files:**
- Modify: `desktopApp/…/Main.kt`, `webApp/…/Main.kt` (drop `DesktopSection.SKILLS` from sections and `when`), the `DesktopSection` enum file(s), `app/…/ui/DublApp.kt` (drop `AppSection.SKILLS`; phone bottom tabs: Лист, Навыки, Магия, Вещи)
- Delete: `desktopApp/…/screens/SkillsScreen.kt`, `webApp/…/screens/SkillsScreen.kt`, `app/…/ui/screens/SkillsScreen.kt`; `SkillRollDialog` (desktop/web `RollDialog.kt`) and `DublSkillRollSheet` (Android `SkillRollSheets.kt`) **only if** nothing else references them after the deletions (grep first).
- Modify: Python guards — repoint function guards to the editor/sheet files: `test_skill_local_override_ui.py`, `test_skill_effect_disable_escape_hatch.py`, `test_shared_application_lock.py` (screen paths), destructive/collection/parity guards that pin skill hide/delete/restore; retire pure old-layout guards (`test_desktop_skills_browser.py`, skills parts of `test_desktop_ui_structural_redesign.py`, `test_desktop_polish_dialogs.py`, `test_compose_desktop_parity.py`) case by case.
- Modify: `CHANGELOG.md` (Unreleased: «Умения tab removed; its settings live in the editor»).
- Test: Create `tools/tests/test_skills_tab_removed.py`

- [ ] **Step 1: Failing guard** `test_skills_tab_removed.py`:
  - `test_no_skills_section_on_any_platform`: `"SKILLS"` not in desktop/web `Main.kt` section lists or the `DesktopSection` enum, and `AppSection.SKILLS` not in `DublApp.kt`.
  - `test_old_skills_screens_are_gone`: the three `SkillsScreen.kt` paths do not exist.
  - `test_moved_functions_live_in_the_editor`: `EditorSkillPanel.kt` contains `setSkillAutoOverrides`, `resetSkillDefinitionOverrides`, `setSkillEffectEnabled`, `hideSkill`, `deleteDynamicSkill`, `setSkillAttributes`, `setSkillModifier`, `setSkillFormulaNote`; `EditorSkillPanel.kt` or `EditorSkillsStep.kt` contains `restoreAllSkills`.
- [ ] **Step 2: Run** `python3 -m pytest -q tools/tests/test_skills_tab_removed.py -p no:cacheprovider` → Expected: FAIL (section and files still present).
- [ ] **Step 3: Remove sections, delete files, repoint/retire guards.**
- [ ] **Step 4: Run** full local checks → BUILD SUCCESSFUL; Python diff vs baseline → no new ids.
- [ ] **Step 5: Commit** `feat: remove the Умения tab; its functions live in the editor and roll panel`.

### Task 10: Verify, review, ship

- [ ] **Step 1:** Screens: desktop 1440 / 1100, tablet 834, phone 360, Пергамент; paths: sheet roll → «другая…» → «Об умении» → «Настроить в редакторе» → panel; change attributes, note, local edits, К рулбуку, hide → «Вернуть», delete custom → «Отменить всё».
- [ ] **Step 2:** Full local checks + Python diff.
- [ ] **Step 3:** One fresh reviewer (subagent, most capable available model) over the branch diff since this plan's base, with the spec, this plan and Review Focus. Fix Critical/Important with RED→GREEN tests; ledger minors.
- [ ] **Step 4:** Push; wait for all 5 workflows green; update PR #32 description (stage 3 section).
