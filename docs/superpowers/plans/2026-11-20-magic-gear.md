# Магия and Снаряжение on the sheet and in the editor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Магия and Снаряжение tabs (and the phone bottom bar) on all platforms. The sheet gains a Заклинания block with a cast window and a Снаряжение block with an item window. The editor gains Магия and Снаряжение steps that hold everything the old screens did.

**Architecture:** Shared Compose in `shared/…/dubl/ui/sheet` and `…/ui/editor`. Models are plain functions over `DublEditorHost` / `DublCharacter`, tested with `FakeSheetHost`; composables stay thin. Every mutation already exists in `MagicApplication` / `EquipmentApplication`, so the host only gains delegates.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlin.test; Python source guards in `tools/tests`.

**Spec:** `docs/superpowers/specs/2026-11-20-magic-gear-design.md` · **Mockup:** https://claude.ai/artifact/1KQsHrUyHyPTyCuUk737qt

## Global Constraints

- **Unchanged:** rules, formulas, saved data, transfer format, sync. No new application-layer methods.
- **Single sources:** load, capacity, burden, mana, school power, spell usability and XP come only from `MagicEquipmentRules` and `CharacterEconomy`.
- **Keep the existing design (owner):** the mockup decides only the new blocks, windows and steps. The sheet header, vitals, Умения, Навыки, tray, editor rail, step tabs, XP card, change list and toasts keep their stage 2–4 look. New UI is built from existing parts (`EditorCard`, `EditorRow`, `EditorChip`, `EditorSquareButton`, `EditorOutlineButton`, `EditorPrimaryButton`, `FuryDialogScaffold`, the sheet's `PlayCard` look, `SheetSearchField`) in all five themes. No new colours or fonts.
- **Live save:** every editor change is covered by the visit's restore point and listed in «Изменения за этот заход».
- **Undo toast:** casting goes through `changeResource(MANA, −N)`, so the reporting host announces it. Gear quantity and carried on the sheet have no toast.
- **Layouts:** 360dp phone, tablet, desktop 1100 and 1440. Touch targets ≥ 44dp off desktop (`editorControl(layout)` on desktop).
- **Copy (exact strings):**
  - Sheet: «Заклинания», «Снаряжение», «мана C/M», «восстановление N/раунд», «Сотворить», «Сотворить · N маны», «Мана», «Не хватает маны: есть C, нужно N», «нужна сила школы N», «+ Заклинание», «+ Предмет», «Не нести», «Изменить в редакторе», «Количество», «Заклинаний нет», «Снаряжения нет», «Расхождение книги».
  - Editor Магия: «Магия», «Магия выключена», «Ранг маны», «Ранг маны меняется только при создании; дальше — навык «Увеличенный запас маны»», «Школы», «+ Школа», «Не удалось сохранить школу», «Книга заклинаний», «Все», «Изученные», «Неизученные», «+ Из каталога», «+ Своё заклинание», «Добавить», «Добавить и исправить», «Уже в книге», «только доступные по силе школы».
  - Editor Снаряжение: «Снаряжение», «Нагрузка», «Считать автоматически», «Вручную», «Инвентарь», «С собой», «Не с собой», «+ Свой предмет», «N шт. · M позиций».
  - Shared: «Изменить», «Удалить», «Вернуть», «Сохранить», «Отмена».
- **Local checks:** `/home/claude/gw.sh <log> :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs`. Android compiles only in CI.
- **Python baseline:** the ids in `/tmp/pytest-baseline-ids.txt` may only shrink.

## Review Focus

1. **Casting more mana than the character has, or a typed amount that isn't a number.** Nothing is spent; the button is disabled with the reason; a blank/garbage field reads as 0. Test: Task 3 `castRefusesMoreThanCurrent`, `castAmountParsesLeniently`.
2. **Catalog gear whose field names use another spelling** («Треб»/«Требование», «Спец Свойства»/«Специальные свойства», «Скор-ть²», «Дальность (Баз-Макс)»). The sheet line and grouping still find them. Test: Task 2 `fieldSpellingsNormalise`.
3. **Deleting a school or spell that the change list prices, then «Вернуть».** The XP line disappears and comes back; the spell's sheet row returns. Test: Task 7 `removedSchoolAndSpellArePriced`; Task 9 `removeSpellOffersReturn`.
4. **Opening the editor at an item or spell that no longer exists** (deleted elsewhere, character switched). The step opens with no detail. Test: Task 6 `openAtMissingItemShowsList`.
5. **A finished character's mana rank.** The stepper is disabled, the rules' refusal is never hit, and the perk link opens «Увеличенный запас маны». Test: Task 8 `rankLockedAfterCreation`.

---

### Task 1: Magic and gear on the editor host

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/DublEditorHost.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt` and `webApp/src/jsMain/kotlin/com/furybook/desktop/DesktopAppState.kt`: add `override` to the existing methods and to `val magicEquipmentCatalog`.
- Modify: `app/src/main/java/com/furybook/android/ui/AndroidSheetHost.kt`: delegate each member to the existing `CharacterController` method, **inside the class body**; `override val magicEquipmentCatalog by lazy { MagicEquipmentCatalogRepository(appContext).load() }`.
- Modify: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/FakeSheetHost.kt`: delegate to `application.magic` / `application.equipment`; new constructor param `magicEquipmentCatalogOverride: MagicEquipmentCatalog? = null`, defaulting to `loader.loadMagicEquipment()`.
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/editor/DublEditorHostTest.kt`

**Interfaces:**
- Produces on `DublEditorHost`:
  - `val magicEquipmentCatalog: MagicEquipmentCatalog`
  - Magic: `setMagicManaRank(rank: Int)`, `setMagicSchoolPower(name: String, power: Int)`, `addMagicSchool(name: String, rank: Int, note: String): Boolean`, `updateMagicSchool(index: Int, name: String, rank: Int, note: String): Boolean`, `removeMagicSchool(index: Int)`, `addCatalogSpell(entry: SpellCatalogEntry): Boolean`, `addCustomSpell(spell: KnownSpell): String`, `updateSpell(spell: KnownSpell)`, `setSpellLearned(uid: String, learned: Boolean)`, `removeSpell(uid: String)`
  - Gear: `setGearLoadAutomatic(enabled: Boolean)`, `setGearManualLoad(value: Double)`, `syncCatalogGearLoads(entries: List<GearCatalogEntry>)`, `addCatalogGear(entry: GearCatalogEntry): String`, `addCustomGear(item: GearItem): String`, `updateGearItem(item: GearItem)`, `setGearItemCarried(uid: String, carried: Boolean)`, `setGearItemQuantity(uid: String, quantity: Int)`, `removeGearItem(uid: String)`
- **Ruling (spec deviation):** the names follow the existing desktop/Android methods (`setMagicManaRank`, not the spec's `setManaRank`), so desktop and web only add `override`. Where a platform method's return type differs from the interface's `Unit`, give the override a block body. Cost if wrong: renames later.

- [ ] **Step 1: Failing tests** in `DublEditorHostTest`:
  - `magicReachesTheApplication`: on a character in creation, `addMagicSchool("Разрушение", 2, "")` is true; `setMagicSchoolPower("Разрушение", 4)` makes `active.magic.schools[0].rank == 4`; `setMagicManaRank(2)` makes `manaRank == 2`; `addCustomSpell(KnownSpell(uid = "", name = "Искра", custom = true))` returns a uid that `setSpellLearned(uid, false)`, `updateSpell(…copy(name = "Искра+"))` and `removeSpell(uid)` act on.
  - `gearReachesTheApplication`: `addCustomGear(GearItem(uid = "", name = "Верёвка", load = 1.0))` returns a uid; `setGearItemQuantity(uid, 3)`, `setGearItemCarried(uid, false)`, `setGearLoadAutomatic(false)` + `setGearManualLoad(2.5)`, `removeGearItem(uid)` act on it.
  - `catalogGearLoadsAreRepaired`: with `magicEquipmentCatalogOverride` holding entry `gear_x` with `fields = mapOf("Треб." to "3")`, add it, then `updateGearItem(item.copy(load = 9.0))`, then `syncCatalogGearLoads(host.magicEquipmentCatalog.gear)` → load `3.0`.
- [ ] **Step 2: Run** `gw.sh … :shared:desktopTest --tests '*DublEditorHostTest*'`. Expected: compile FAIL (unresolved members).
- [ ] **Step 3: Implement** the members on all four hosts.
- [ ] **Step 4: Run** the test. Expected: PASS. Then `:desktopApp:compileKotlin :webApp:compileKotlinJs`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat(editor): magic and gear on the editor host`.

### Task 2: Gear fields — normaliser, kinds, lines, descriptions, categories

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetGearFields.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetGearFieldsTest.kt`

**Interfaces:**
- Produces:
  - `enum class GearStat(val label: String) { DAMAGE("Урон"), DAMAGE_TYPE("Тип урона"), ARMOUR("Броня"), REQUIREMENT("Треб"), RANGE("Дальность"), SPEED("Скор"), MAGAZINE("Магазин"), CALIBER("Калибр"), PROPERTIES("Свойства"), WEIGHT("Вес") }`
  - `fun gearStat(fields: Map<String, String>, stat: GearStat): String?`: non-blank value under any spelling. Spellings: REQUIREMENT `Треб.`/`Треб`/`Требование`; PROPERTIES `Спец. свойства`/`Спец Свойства`/`Специальные свойства`/`Спецсвойства`; SPEED `Скор-ть`/`Скор-ть²`; RANGE `Дальность`/`Дальность (Баз-Макс)`; WEIGHT `Вес`/`Вес, кг`; the rest by their label. Match after trim, lowercase and `ё→е`.
  - `enum class GearKind(val title: String) { WEAPON("Оружие"), ARMOUR("Броня"), OTHER("Прочее") }`
  - `fun gearKind(fields: Map<String, String>): GearKind`: DAMAGE present → WEAPON, else ARMOUR present → ARMOUR, else OTHER.
  - `data class GearStatValue(val stat: GearStat, val value: String)`
  - `fun gearLine(fields: Map<String, String>, max: Int): List<GearStatValue>`: WEAPON order DAMAGE, DAMAGE_TYPE, REQUIREMENT, RANGE, SPEED, MAGAZINE, PROPERTIES; ARMOUR order ARMOUR, REQUIREMENT, PROPERTIES; OTHER empty. Missing stats are skipped; at most `max`.
  - `fun gearDescriptionExtra(description: String, fields: Map<String, String>): String`: drops each line that is `key: value` for one of the fields (spacing-insensitive), returns what is left, trimmed.
  - `fun gearCategoryKey(category: String): String`: lowercase, `ё→е`, `/` and ` и ` both → ` и `, spaces collapsed. «Пистолеты/Револьверы» and «Пистолеты и револьверы» share a key; so do «Пистолеты-пулеметы» and «Пистолеты-пулемёты».

- [ ] **Step 1: Failing tests:**
  - `fieldSpellingsNormalise`: `mapOf("Треб" to "2")`, `mapOf("Требование" to "2")` and `mapOf("Треб." to "2")` all give REQUIREMENT "2"; `"Специальные свойства"`, `"Спец Свойства"` give PROPERTIES; `"Скор-ть²"` gives SPEED; `"Дальность (Баз-Макс)"` gives RANGE.
  - `kindsByFields`: damage → WEAPON; `Броня` only → ARMOUR; `Вес` only → OTHER.
  - `weaponLineOrderAndLimit`: Наган fields (`Урон 1d4+6`, `Треб. 1`, `Дальность 10-50`, `Скор-ть 1 / - / -`, `Магазин 7`, `Калибр 7.62х38`) with `max = 4` → DAMAGE, REQUIREMENT, RANGE, SPEED.
  - `descriptionThatRepeatsFieldsIsDropped`: the Короткий лук description («Урон: 1d4+1\nТреб.: 1\n…») → `""`; with an extra line «Тихий» → `"Тихий"`.
  - `categorySpellingsMerge`: the two pairs above share keys; «Автоматы» doesn't.
- [ ] **Step 2: Run** `--tests '*SheetGearFieldsTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(sheet): gear field normaliser shared by sheet and editor`.

### Task 3: Sheet Заклинания model and casting

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetSpells.kt` (model + cast logic)
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetSpellsTest.kt`

**Interfaces:**
- Consumes: Task 1 host.
- Produces:
  - `data class SheetSpellRow(val uid: String, val name: String, val manaLabel: String, val meta: String, val weakText: String?)`: `manaLabel` = `"${manaText.ifBlank { cost }} маны"`; `meta` = non-blank `time`, `range`, `action` joined by « · »; `weakText` = «нужна сила школы N» when `spellUsability(...).usable` is false.
  - `data class SheetSpellsView(val current: Int, val maximum: Int, val recovery: Int, val rows: List<SheetSpellRow>)`
  - `fun sheetShowsSpells(character: DublCharacter): Boolean`: `manaEnabled || MagicEquipmentRules.manaMaximum(character) > 0 || character.magic.spells.any { it.learned }`.
  - `fun sheetSpells(host: DublSheetHost): SheetSpellsView?`: null unless `sheetShowsSpells`; rows are learned spells sorted by `MagicSchoolCatalog.sortIndex(school)` then name; current/maximum from `sheetVitals`'s MANA vital (so overrides count), recovery from `manaRecoveryPerRound`.
  - `fun sheetSpellDefaultMana(spell: KnownSpell): Int`: leading integer of `manaText`, else `cost`, never below 0.
  - `fun sheetCastAmount(text: String): Int`: digits only, blank → 0.
  - `fun sheetCastBlockedReason(current: Int, amount: Int): String?`: «Не хватает маны: есть C, нужно N» when `amount > current`, else null.
  - `fun sheetCastSpell(host: DublEditorHost, uid: String, amount: Int): Boolean`: false (and nothing spent) when the spell is missing or blocked; otherwise `changeResource(MANA, -amount)` when amount > 0, then true.

- [ ] **Step 1: Failing tests:**
  - `blockHiddenWithoutMagic`: a fresh character → `sheetSpells(host) == null`; with one learned spell → not null.
  - `rowsAreLearnedSpellsBySchoolThenName`: spells «Щит» (Ограждение), «Искра» (Разрушение), «Видение» (Прорицание, not learned) → rows «Щит», «Искра» (catalog order: Ограждение before Разрушение) and no «Видение».
  - `weakSchoolWarnsOnTheRow`: spell cost 6, school Прорицание power 1 → `weakText == "нужна сила школы 6"`.
  - `defaultManaReadsManaText`: `manaText = "4+"` → 4; blank, cost 2 → 2.
  - `castSpendsTheChosenAmount`: mana 7/12, cast 3 → true, mana 4.
  - `castRefusesMoreThanCurrent`: mana 2, cast 3 → false, mana 2, reason «Не хватает маны: есть 2, нужно 3».
  - `castAmountParsesLeniently`: `"12"` → 12, `""` → 0, `"a3"` → 3.
- [ ] **Step 2: Run** `--tests '*SheetSpellsTest*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(sheet): spells model and casting`.

### Task 4: Sheet Снаряжение model

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetGear.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/ui/sheet/SheetGearTest.kt`

**Interfaces:**
- Consumes: Task 2.
- Produces:
  - `data class SheetGearRow(val uid: String, val name: String, val quantity: Int, val kind: GearKind, val line: List<GearStatValue>)`
  - `data class SheetGearView(val load: String, val capacity: Int, val burden: String, val penalty: Int, val groups: List<Pair<GearKind, List<SheetGearRow>>>)`: `load` via `formatSheetNumber(MagicEquipmentRules.equipmentLoad(c))`; groups in `GearKind` order, empty groups dropped, rows by name; `line = gearLine(fields, 5)`.
  - `fun sheetGear(character: DublCharacter): SheetGearView`: carried items only.
  - `fun sheetGearPenaltyText(penalty: Int): String?`: «−N к атакам и защите» when penalty < 0.
  - `data class SheetGearDetail(val uid: String, val name: String, val category: String, val fields: List<Pair<String, String>>, val extraDescription: String, val quantity: Int, val carried: Boolean)`
  - `fun sheetGearDetail(character: DublCharacter, uid: String): SheetGearDetail?`
  - `fun sheetChangeGearQuantity(host: DublEditorHost, uid: String, delta: Int)`: `setGearItemQuantity(uid, (q + delta).coerceAtLeast(1))`, no call when unchanged.

- [ ] **Step 1: Failing tests:**
  - `onlyCarriedItemsGroupedWeaponsArmourOther`: Кинжал (Урон), Кожаная броня (Броня), Верёвка, Спальник (not carried) → groups WEAPON [Кинжал], ARMOUR [Кожаная броня], OTHER [Верёвка].
  - `loadAndBurdenComeFromRules`: an item with load 30 against a small capacity → `burden == MagicEquipmentRules.burden(c).title`, `penalty < 0`, and `sheetGearPenaltyText(-2) == "−2 к атакам и защите"`.
  - `detailDropsRepeatedDescription`: the Короткий лук entry → `extraDescription == ""`.
  - `quantityNeverBelowOne`: quantity 1, delta −1 → still 1; delta +2 → 3.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(sheet): carried gear model`.

### Task 5: Sheet UI — blocks, tabs, cast window, item window, load repair

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetPlaySpells.kt` (`SheetPlaySpells`, `SheetCastDialog`), `…/sheet/SheetPlayGear.kt` (`SheetPlayGear`, `SheetGearDialog`)
- Modify:
  - `SheetScreenState.kt`: `SheetDialog.Cast(val uid: String)`, `SheetDialog.GearItem(val uid: String)`;
  - `SheetPlayHeader.kt` (`SheetPlayState`): `var rightTab: SheetRightTab by mutableStateOf(SheetRightTab.PERKS)`;
  - `SheetPlay.kt`: `SheetPlayBody` takes `DublEditorHost`; WIDE_WITH_TRAY right column = tab row (`EditorChip`-style, `Role.Tab`) over the chosen block; tablet/phone stack Навыки, Заклинания (if shown), Снаряжение;
  - `SheetScreen.kt`: route the two dialogs; `LaunchedEffect(host, host.active.id) { host.syncCatalogGearLoads(host.magicEquipmentCatalog.gear) }` before any editor open.
- Test: `SheetLayoutTest.kt`, `SheetScreenStateTest.kt`

**Interfaces:**
- Produces: `enum class SheetRightTab(val title: String) { PERKS("Навыки"), SPELLS("Заклинания"), GEAR("Снаряжение") }`; `fun sheetRightTabs(showSpells: Boolean): List<SheetRightTab>`; `fun sheetRightTabOrDefault(tab: SheetRightTab, showSpells: Boolean): SheetRightTab` (SPELLS falls back to PERKS when hidden).
- Consumes: Tasks 3–4; the editor targets from Task 6 (wire «+ Заклинание», «+ Предмет», «Изменить в редакторе» in Task 6, Step 3).
- **Cast window** (`FuryDialogScaffold`, title = spell name, subtitle = school · manaLabel): facts (Время, Дистанция, Область, Проверка, Длительность — non-blank only), description, «Усиление» box, `weakText`, «Расхождение книги» + `conflictNote`, then «Мана» with `EditorSquareButton` −/+ and a numeric `FuryTextField` prefilled with `sheetSpellDefaultMana`, the blocked reason, primary «Сотворить · N маны» (disabled when blocked) → `sheetCastSpell`, then close.
- **Item window:** fields as two columns, `extraDescription`, «Количество» −/+ (`sheetChangeGearQuantity`), «Не нести» (`setGearItemCarried(uid, false)`, then close), «Изменить в редакторе», primary «Готово».

- [ ] **Step 1: Failing tests:** `rightTabsHideSpellsWithoutMagic` (`sheetRightTabs(false) == [PERKS, GEAR]`; `sheetRightTabOrDefault(SPELLS, false) == PERKS`); `castAndGearDialogsOpenAndClose` (SheetScreenStateTest: `openDialog(Cast("u"))` then `updateEditing(true)` → `dialog == null`).
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the model functions, the blocks and the windows.
- [ ] **Step 4: Run.** Expected: PASS. Compile desktop and web.
- [ ] **Step 5: Screens** (Xvfb, `WAIT ≥ 2.5`), desktop 1440 and phone 360: cast «Испепеляющий взгляд» for 6, too-much-mana reason, item ± and «Не нести».
- [ ] **Step 6: Commit** `feat(sheet): Заклинания and Снаряжение blocks with cast and item windows`.

### Task 6: Editor plumbing — steps, open targets, problems, summary links

**Files:**
- Modify:
  - `EditorState.kt`: `EditorStep` gains `MAGIC("Магия")`, `GEAR("Снаряжение")` between PERKS and SUMMARY (both lists); magic/gear UI state (below); `open(host, forceBasics, skillId = null, entryId = null, target: EditorOpenTarget? = null)`;
  - `SheetScreenState.kt`: `openEditor(forceBasics, skillId = null, entryId = null, target: EditorOpenTarget? = null)`; `onEditorOpening: ((Boolean, String?, String?, EditorOpenTarget?) -> Unit)?`;
  - `SheetScreen.kt`: pass the target; wire Task 5's buttons; drop `onOpenMagic` / `onOpenGear`;
  - `EditorScreen.kt`: dispatch MAGIC/GEAR (placeholders until Tasks 9 and 11), step subtitles («N заклинаний · X XP», «N позиций · M с собой»); drop `onOpenMagic` / `onOpenGear`;
  - `EditorSummaryStep.kt`: «Магия →» / «Снаряжение →» set `state.step`;
  - `EditorModels.kt`: «Заклинания без цены» problem → `EditorStep.MAGIC`;
  - call sites in desktop/web `Main.kt` and Android `DublApp.kt`: stop passing the two callbacks (sections stay until Task 12).
- Test: `EditorStateTest.kt`, `SheetScreenStateTest.kt`, `EditorModelsTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface EditorOpenTarget { data class Magic(val spellUid: String? = null, val catalog: Boolean = false) : EditorOpenTarget; data class Gear(val itemUid: String? = null, val catalog: Boolean = false) : EditorOpenTarget }`
  - `sealed interface EditorPane { data class Spell(val uid: String) : EditorPane; data class CatalogSpell(val id: String) : EditorPane; data object SpellCatalog : EditorPane; data class School(val index: Int) : EditorPane; data class Item(val uid: String) : EditorPane; data class CatalogItem(val id: String) : EditorPane; data object GearCatalog : EditorPane }`
  - On `EditorState`: `var magicPane: EditorPane?`, `var gearPane: EditorPane?`, `var spellFilter: EditorSpellFilter` (`enum class EditorSpellFilter { ALL, LEARNED, UNLEARNED }`), `var spellSchool: String?`, `var spellQuery: String`, `var spellCatalogQuery: String`, `var spellCatalogSchool: String?`, `var spellCatalogUsableOnly: Boolean`, `var gearQuery: String`, `var gearCatalogQuery: String`, `var gearCatalogCategory: String?` (a `gearCategoryKey`). All reset in `open`.
  - `open` with `Magic(uid)` → step MAGIC, `magicPane = Spell(uid)` if the spell exists, else null; `catalog = true` → `SpellCatalog`. `Gear` likewise with `Item` / `GearCatalog`.

- [ ] **Step 1: Failing tests:**
  - `stepsIncludeMagicAndGear`: `editorSteps(true)` ends `…, PERKS, MAGIC, GEAR, SUMMARY`; same tail for `false`.
  - `openAtGearItem`: `open(host, false, target = Gear(uid))` → `step == GEAR`, `gearPane == Item(uid)`.
  - `openAtSpellCatalog`: `Magic(catalog = true)` → `magicPane == SpellCatalog`.
  - `openAtMissingItemShowsList`: `Gear("gone")` → `step == GEAR`, `gearPane == null`.
  - `editorOpensAtTargetFromSheet` (SheetScreenStateTest): the recorder sees `(false, null, null, Gear("u"))`.
  - `unpricedSpellsPointToMagic` (EditorModelsTest): a learned spell with cost 25 → problem step MAGIC.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement**; update the platform call sites.
- [ ] **Step 4: Run.** Expected: PASS. Compile desktop and web.
- [ ] **Step 5: Commit** `feat(editor): Магия and Снаряжение steps, open targets from the sheet`.

### Task 7: Change list — mana, schools, spells, gear

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorChanges.kt` (before the «Создание» line)
- Test: `EditorChangesTest.kt`

**Interfaces:**
- Lines (spec «Change list»), priced with `cost(b.copy(magic = …))`, the same pattern as the other lines:
  - «Ранг маны a → b» — `cost(b.copy(magic = b.magic.copy(manaRank = a.magic.manaRank)))`.
  - Schools, matched by `MagicSchoolCatalog.canonicalize(name)`: «+ школа N», «− школа N», «N: сила a → b»; note-only changes → «N: примечание изменено» (0 XP).
  - Spells, matched by `uid`: «+ заклинание S», «− заклинание S», «S: изучено», «S: не изучено», «S: изменено» (any other field). Each priced by putting only that spell's change on `b`.
  - Gear, by `uid`, 0 XP: «+ предмет I», «− предмет I», «I: ×a → ×b», «I: с собой», «I: не с собой», «I: изменён» (other fields, load included); «Нагрузка: вручную V» / «Нагрузка: автоматически»; a manual value change while manual → «Нагрузка: вручную V».
  - Catalog load repair (Task 5) runs before the snapshot, so it never shows.

- [ ] **Step 1: Failing tests:**
  - `manaRankAndSchoolPowerArePriced`: rank 1 → 2 → line «Ранг маны 1 → 2» xp 100; Разрушение 2 → 4 → «Разрушение: сила 2 → 4» xp 50.
  - `removedSchoolAndSpellArePriced`: removing Ограждение (power 3) → «− школа Ограждение» xp −75; removing a learned cost-4 spell → «− заклинание Щит» xp −20.
  - `learnToggleIsPriced`: «Щит: не изучено» xp −20.
  - `gearLinesCostNothing`: add, quantity, carried, manual load → their lines with xp 0, and `netSpent == 0`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): magic and gear lines in the change list`.

### Task 8: Editor Магия models and forms

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorMagicModels.kt`, `…/editor/EditorMagicForms.kt` (form models only)
- Test: `EditorMagicModelsTest.kt`, `EditorMagicFormsTest.kt`

**Interfaces:**
- Produces (models):
  - `data class EditorMagicHeader(val manaRank: Int, val rankXp: Int, val rankLocked: Boolean, val manaMaximum: Int, val recovery: Int, val schoolXp: Int, val spellXp: Int, val zeroReason: String?)`: `rankLocked = creationComplete`; `zeroReason` «Мана появится, когда ранг маны больше 0 и есть школа с силой» when max is 0.
  - `fun editorMagicHeader(host: DublEditorHost): EditorMagicHeader` (XP from `CharacterEconomy.breakdown`).
  - `data class EditorSchoolRow(val index: Int, val name: String, val power: Int, val xp: Int, val note: String)`; `fun editorSchoolRows(c: DublCharacter): List<EditorSchoolRow>` (catalog order); `fun editorAddableSchools(c: DublCharacter): List<String>` (`MagicSchoolCatalog.schools` minus owned).
  - `data class EditorSpellRow(val uid: String, val name: String, val manaLabel: String, val learned: Boolean, val learnXp: Int?, val weakText: String?)`; `data class EditorSpellGroup(val school: String, val power: Int, val rows: List<EditorSpellRow>)`; `fun editorSpellGroups(c: DublCharacter, filter: EditorSpellFilter, school: String?, query: String): List<EditorSpellGroup>` (query over name, description, enhancement; `learnXp = xpOverride ?: learnXpCost(cost)`).
  - `data class EditorCatalogSpellRow(val id: String, val name: String, val school: String, val manaLabel: String, val known: Boolean, val usable: Boolean, val requiredPower: Int)`; `fun editorSpellCatalog(host: DublEditorHost, query: String, school: String?, usableOnly: Boolean): List<EditorCatalogSpellRow>` (`known` when a spell has that `catalogId`).
  - `data class EditorSpellDetail(val title: String, val subtitle: String, val facts: List<Pair<String, String>>, val description: String, val enhancement: String, val conflictNote: String, val weakText: String?, val incomplete: Boolean)`; `fun editorSpellDetail(c: DublCharacter, spell: KnownSpell)` and `fun editorSpellDetail(c: DublCharacter, entry: SpellCatalogEntry)`.
- Produces (forms):
  - `data class EditorSpellForm(val name: String, val school: String, val cost: String, val manaText: String, val time: String, val range: String, val area: String, val action: String, val duration: String, val description: String, val enhancement: String, val xpOverride: String, val learned: Boolean, val incomplete: Boolean)`; `fun editorSpellForm(spell: KnownSpell?): EditorSpellForm`; `fun EditorSpellForm.errors(): List<String>` («Нужно название»; cost not a number → «Мана — число»); `fun EditorSpellForm.toSpell(base: KnownSpell?): KnownSpell` (`custom = true` when new; blank `manaText` → cost; trimmed).
  - `data class EditorSchoolForm(val name: String, val power: String, val note: String)`; `fun EditorSchoolForm.errors(allowed: List<String>): List<String>` (name not in `allowed` → «Выберите школу из списка»; power not a number → «Сила — число»).
  - `data class EditorGearForm` lives in Task 10.

- [ ] **Step 1: Failing tests:**
  - `rankLockedAfterCreation`: finished character → `rankLocked`; in creation → not.
  - `headerExplainsZeroMana`: rank 0 → `zeroReason` set; rank 2 + Разрушение 3 → null and `manaMaximum == MagicEquipmentRules.manaMaximum(c)`.
  - `addableSchoolsSkipOwned`: owning Разрушение → list without it, 12 entries.
  - `spellbookFiltersAndGroups`: LEARNED drops unlearned; `school = "Ограждение"` keeps one group; query «щит» matches by name.
  - `catalogMarksKnownAndUsable`: an entry already added → `known`; cost 6 with power 4 → `!usable`; `usableOnly` hides it.
  - `spellFormRejectsBadInput`: blank name → «Нужно название»; cost «x» → «Мана — число»; good form → `toSpell(null).custom`.
  - `schoolFormNeedsAListedSchool`: «Огонь» → «Выберите школу из списка».
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): Магия models and forms`.

### Task 9: Editor Магия step UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorMagicStep.kt` (step, panes), `…/editor/EditorMagicDialogs.kt` (spell form and school form dialogs on `FuryDialogScaffold`), `…/editor/EditorMagicActions.kt`
- Modify: `EditorScreen.kt` (MAGIC → `EditorMagicStep`)
- Test: `EditorMagicActionsTest.kt`

**Interfaces:**
- Consumes: Tasks 6, 8.
- Produces in `EditorMagicActions.kt` (each records a restore point first; deletes flash with «Вернуть» via `undoTo`, as stage 4 does):
  - `fun editorRemoveSpell(host: DublEditorHost, state: EditorState, uid: String)`; `fun editorRemoveSchool(host: DublEditorHost, state: EditorState, index: Int)`
  - `fun editorAddCatalogSpell(host: DublEditorHost, state: EditorState, entry: SpellCatalogEntry): String?`: returns the new spell's uid (found by `catalogId`) and opens `Spell(uid)`; for `incomplete` entries the UI then opens the spell form.
  - `fun editorSaveSchool(host: DublEditorHost, state: EditorState, index: Int?, form: EditorSchoolForm): Boolean`: add or update; false → flash «Не удалось сохранить школу».
- **Rows:** school row = name, «сила N · X XP», `EditorSquareButton` −/+ (`setMagicSchoolPower`), tap → school dialog (power, note, «Удалить»). Spell row = name, manaLabel, `weakText`, and a gold `EditorChip` «Изучено · X XP» / «Изучить · X XP» (`setSpellLearned`); tap → `Spell(uid)` pane (detail, «Изменить», «Удалить»).
- **Layout:** split like `EditorPerksStep` (`editorPerksSplit`, 360dp pane) with list left and pane right; overlay with «← Назад» when narrow. «Магия выключена» card above the body with the mana switch (`setIdentity(…, manaEnabled = true)`). Rank stepper disabled when `rankLocked`, with the copy line and a link that sets `step = PERKS` and `openPerk(EditorPerkContext(host), MagicEquipmentRules.INCREASED_MANA_ENTRY_ID)`.
- **Ruling (guard deviation):** old desktop deletes asked «Удалить школу?/заклинание?». The editor uses «Удалить» + «Вернуть» like stage 4. Task 12 repoints the guard to that. Cost if wrong: one confirm dialog later.

- [ ] **Step 1: Failing tests:**
  - `removeSpellOffersReturn`: remove a learned spell → gone and `state.toast` starts with «Удалено»; `state.undoToast(host)` → true and the spell is back (still learned).
  - `incompleteCatalogSpellIsAddable`: entry `incomplete = true` → `editorAddCatalogSpell` returns a uid; the spell has `incomplete == true`.
  - `refusedSchoolSaveSaysSo`: saving «Разрушение» twice as new → false, `state.toast == "Не удалось сохранить школу"`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** the actions, then the step and dialogs.
- [ ] **Step 4: Run.** Expected: PASS. Compile desktop and web.
- [ ] **Step 5: Screens** desktop 1440 and phone 360: school ±, learn toggle, catalog detail → «Добавить», own spell, delete → «Вернуть», locked rank link.
- [ ] **Step 6: Commit** `feat(editor): Магия step`.

### Task 10: Editor Снаряжение models and form

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorGearModels.kt`
- Test: `EditorGearModelsTest.kt`

**Interfaces:**
- Consumes: Task 2.
- Produces:
  - `data class EditorGearHeader(val load: String, val capacity: Int, val burden: String, val penalty: Int, val automatic: Boolean, val manual: String, val count: String)`: `count` = «N шт. · M позиций».
  - `fun editorGearHeader(c: DublCharacter): EditorGearHeader`
  - `data class EditorGearRow(val uid: String, val name: String, val line: String, val quantity: Int, val carried: Boolean)`: `line` = `gearLine(fields, 4)` as «Урон 1d4+6 · Треб 1 …», else category.
  - `fun editorInventory(c: DublCharacter, query: String): List<Pair<GearKind, List<EditorGearRow>>>`: search over name, description, `fields.values` (the old screens' search).
  - `data class EditorCatalogGearRow(val id: String, val name: String, val line: String, val owned: Int)`; `fun editorGearCategories(entries: List<GearCatalogEntry>): List<Pair<String, String>>` (key → first label); `fun editorGearCatalog(host: DublEditorHost, query: String, categoryKey: String?): List<EditorCatalogGearRow>` (search over name, category, description, fields).
  - `data class EditorGearForm(val name: String, val quantity: String, val load: String, val category: String, val section: String, val description: String, val fields: String, val carried: Boolean)`; `fun editorGearForm(item: GearItem?)` (fields as «Название: значение» lines); `fun EditorGearForm.errors()` («Нужно название», «Количество — целое число от 1», «Нагрузка — число»); `fun EditorGearForm.toItem(base: GearItem?): GearItem` (lines without «:» are skipped; `,` accepted in load).
  - `fun editorManualLoad(text: String): Double?`

- [ ] **Step 1: Failing tests:** `countAndBurden`; `inventorySearchesFields` (query «7.62» finds Наган by Калибр); `catalogCategoriesMerge` («Пистолеты/Револьверы» + «Пистолеты и револьверы» → one chip); `gearFormRoundTripsFields` («Урон: 1d6\nТреб.: 2» ↔ map); `gearFormRejectsBadInput`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(editor): Снаряжение models and form`.

### Task 11: Editor Снаряжение step UI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorGearStep.kt`, `…/editor/EditorGearDialogs.kt`
- Modify: `EditorScreen.kt` (GEAR → `EditorGearStep`); `EditorMagicActions.kt` → add `editorRemoveGear(host, state, uid)` (flash + «Вернуть»)
- Test: `EditorMagicActionsTest.kt` (`removeGearOffersReturn`)

**Interfaces:**
- Consumes: Tasks 6, 10.
- Header card (load, burden + penalty, «Считать автоматически» / «Вручную» chips, manual field committing on valid input, count); inventory with search, groups, row: name + line, qty −/+ (`sheetChangeGearQuantity`, min 1), «С собой»/«Не с собой» chip; pane: item detail (fields, `gearDescriptionExtra`, «Изменить», «Удалить») / gear catalog (search, merged category chips, owned count) / catalog item detail (category, load `catalogGearLoad`, fields, «Добавить»). Same split/overlay as Магия.

- [ ] **Step 1: Failing test** `removeGearOffersReturn`.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS. Compile.
- [ ] **Step 5: Screens** desktop 1440 and phone 360: manual load, qty, carried, catalog add, own item, delete → «Вернуть», sheet «Изменить в редакторе» lands on the item.
- [ ] **Step 6: Commit** `feat(editor): Снаряжение step`.

### Task 12: Remove the Магия and Снаряжение tabs and the phone bottom bar

**Files:**
- Modify:
  - desktop `Main.kt`, web `web/Main.kt`: drop `MAGIC`, `EQUIPMENT`, the `phoneLabel` helper and the screen routes; `primary` is false for every section;
  - `app/…/ui/DublApp.kt`: drop `AppSection.MAGIC`, `INVENTORY` and their routes; `primary` false for every section;
  - `shared/…/ui/components/FuryShell.kt`: no `FuryBottomTabs` when `furyBottomTabs(sections)` is empty; the menu then lists every section (Лист first).
- Delete: desktop and web `screens/MagicScreen.kt`, `screens/EquipmentScreen.kt`; Android `ui/screens/MagicScreen.kt`, `ui/screens/EquipmentScreen.kt`; helpers only they use (grep each first, e.g. `DesktopIconKind.MAGIC/EQUIPMENT` if unused).
- Python guards:
  - **Repoint** (functions): `test_compose_desktop_parity.py` (→ `EditorMagicStep.kt`/`EditorGearStep.kt` hold `setMagicManaRank`, `setMagicSchoolPower`, `addCatalogSpell`, `setGearLoadAutomatic`, `addCatalogGear`; `SheetGear.kt` holds `equipmentLoad`); `test_compose_destructive_and_validation_parity.py` (→ deletes offer «Вернуть»: `editorRemoveSpell`, `editorRemoveSchool`, `editorRemoveGear` use `undoTo`); `test_compose_magic_parity.py` (→ «+ Школа», `addMagicSchool`, school chips via `spellSchool`/`spellCatalogSchool`, «Не удалось сохранить школу»); `test_equipment_backend_parity.py` (screen rules → `SheetGear.kt`/`EditorGearModels.kt`; search over `description` and `fields.values`); `test_magic_unresolved_escape_hatch.py` (→ «Добавить и исправить» and `addCatalogSpell` in the editor files); `test_rules_boundary_contract.py` (→ `magicSchoolRankXp` in `EditorMagicModels.kt`); `test_sheet_wiring.py` (no `onOpenMagic =`; phone tab label test → no bottom tabs).
  - **Retire** only pure old-layout pins (`test_desktop_polish_dialogs.py`'s Magic/Equipment users), each ledgered.
- Modify: `CHANGELOG.md` (Unreleased, stage 5).
- Create: `tools/tests/test_magic_gear_tabs_removed.py`
- Test: `FuryShellPolicyTest.kt` (`noPrimarySectionsMeansNoBottomTabs`)

- [ ] **Step 1: Failing tests:**
  - `noPrimarySectionsMeansNoBottomTabs`: all `primary = false` → `furyBottomTabs` empty, `furyMenuSections` == all three.
  - Guard: no `MAGIC`/`EQUIPMENT`/`INVENTORY` sections; the six screen files don't exist; `FuryShell.kt` skips the bar when the tab list is empty; `SheetScreen.kt` calls `syncCatalogGearLoads`.
- [ ] **Step 2: Run** both. Expected: FAIL.
- [ ] **Step 3: Remove** the sections and files; repoint/retire guards.
- [ ] **Step 4: Run** full local checks (BUILD SUCCESSFUL) and the Python suite diffed against the baseline (no new ids).
- [ ] **Step 5: Commit** `feat: remove the Магия and Снаряжение tabs and the phone bottom bar`.

### Task 13: Verify, review, ship

- [ ] **Step 1: Screens** at desktop 1440 and 1100, tablet 834, phone 360, plus Пергамент: sheet tabs and stacked blocks; cast; item window; «+ Заклинание» / «+ Предмет»; both editor steps; phone menu without a bottom bar.
- [ ] **Step 2:** Full local checks plus the Python diff.
- [ ] **Step 3:** One fresh reviewer (subagent, opus) over the diff since this plan's base, with the spec, this plan and the Review Focus. Fix Critical/Important with RED→GREEN tests; ledger minors.
- [ ] **Step 4:** Push; wait for all 5 workflows green. Update PR #32 with a stage 5 section.
