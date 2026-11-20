# Магия and Снаряжение: sheet and editor (revamp stage 5)

Status: approved by the owner · 2026-11-20
Mockup: https://claude.ai/artifact/1KQsHrUyHyPTyCuUk737qt (Design canvas: desktop sheet, editor Магия and Снаряжение, phone sheet and editor)
Builds on:
- `2026-11-17-ui-revamp-design.md` (stage list, leftovers)
- `2026-11-18-play-sheet-redesign-design.md` (sheet blocks, layouts)
- `2026-11-18-character-editor-design.md` (editor steps, restore point, change list)
- stages 3–4 (`…skills-into-editor…`, `…development-into-editor…`): the same split, applied to two more tabs.

## Why

Магия and Снаряжение are still the old per-platform screens:
- **They have drifted apart** (desktop and web are identical, Android is its own).
  - Android has the spell learned switch, item carried switch and quantity on its main screens, and repairs catalog loads when the gear tab opens.
  - Desktop keeps those inside edit dialogs, has catalog «Подробнее», and repairs loads at startup.
- **Leftovers the revamp plan lists for this stage:** dead mana ± at 0/0, a developer note shown to players, and gear descriptions that repeat the stats.

What the owner wants:
- **At the table:** casting spells and spending mana. Known spells belong on the sheet. In any fight you need to see what your gear does: damage, requirement, range. Possibly all carried gear on the sheet.
- **Building:** adding spells, raising mana and school power, and managing the inventory, all in the editor. «+ Предмет» on the sheet opens the editor on the gear tab.

## Goals

- **Sheet:** a **Заклинания** block (known spells, «Сотворить» with a mana window) and a **Снаряжение** block (all carried gear with combat stats, load line).
- **Editor:**
  - a **Магия** step: mana rank, schools, spellbook, catalog, own spells;
  - a **Снаряжение** step: inventory, catalog, own items, carried, quantity, load mode.
- **Remove the Магия and Снаряжение tabs** on Android, desktop and web. Every function either platform has today stays reachable on all three.
- **Phones get no bottom bar.** The sheet is home; Кампания and Персонажи live in the menu.

## Non-goals

- Rules, formulas, saved data, transfer format, sync: unchanged. Every mutation already exists in `MagicApplication` / `EquipmentApplication`.
- Rolling attacks or spell checks from a weapon or spell (roll subjects stay as they are). A possible later stage.
- Кампания, Персонажи, login (stage 6).

## Anti-goals

- Any Магия/Снаряжение function becoming unreachable on any platform.
- A second copy of magic, load or XP rules in UI code. `MagicEquipmentRules` and `CharacterEconomy` stay the only sources.
- Editor changes not covered by «Отменить всё» or not listed in «Изменения за этот заход».
- Developer notes or duplicated text shown to players.

## Approaches

1. **Picked (one sensible way):** sheet blocks plus editor steps in shared code; old screens deleted. Same split as stages 3–4, and it fixes the platform drift at once.
2. Rejected: rebuild the two screens in shared code and hide them behind the menu. That gives two places to manage the same data, and the owner wants managing in the editor.
3. Rejected: sheet blocks only, keeping the old screens for managing. That leaves the drift and the dead controls, and works against stage 7.

## Where each function goes

| Today (old screens; platform in brackets when only one has it) | New home |
|---|---|
| Mana current ± (dead on desktop at 0/0) | Sheet mana meter (exists) + «Сотворить» window |
| Mana rank 0–5 with XP (desktop) | Editor Магия: mana rank stepper with XP |
| Mana recovery per round, XP breakdown (rank · schools · spells) | Editor Магия header; sheet Заклинания header shows recovery |
| Schools: add / edit (name from the school list, power, note) / delete / power stepper; «Только изученные» filter | Editor Магия: schools list |
| Spellbook: known spells, learned toggle (Android), usability («Требуется сила школы N») | Editor Магия: spellbook; learned spells also on the sheet |
| Spell catalog: search, school filter, «Подробнее» (full card, book discrepancy, school-power check) (desktop), add | Editor Магия: «+ Из каталога» with a read-only detail before «Добавить» |
| Own spell: add / edit / delete | Editor Магия: «+ Своё заклинание», spell detail |
| Inventory list, search, count «N шт. · M позиций» | Editor Снаряжение |
| Gear catalog: search, sections, owned counts, «Подробнее» (category, load, all fields) (desktop), add | Editor Снаряжение: «+ Из каталога» with a read-only detail before «Добавить» |
| Own item: add / edit / delete | Editor Снаряжение: «+ Свой предмет», item detail |
| Carried toggle, quantity (Android) | Editor Снаряжение item detail; sheet item detail (play changes) |
| Load: current / capacity, burden label, automatic or manual value (desktop) | Editor Снаряжение header (both); sheet Снаряжение header (read-only line) |
| Catalog load repair `syncCatalogLoads` (Android on gear-tab open; desktop/web at startup) | Shared: whenever the sheet shows a character (start, switch, reload), before any editor snapshot; desktop's startup call stays |

## Design

### Sheet: Заклинания block

- Shown when the character uses magic: `manaEnabled`, or mana maximum > 0, or any learned spell (the same test the mana meter uses, plus spells).
- Header: «Заклинания · мана C/M · восстановление N/раунд».
- One row per **learned** spell (`KnownSpell.learned`), sorted by school then name:
  - name;
  - `manaText` (fallback: cost) «маны»;
  - time, range, and the check (`action`).
  - If `MagicEquipmentRules.spellUsability` says the school is too weak, the row says «нужна сила школы N».
- Tapping the row or its «Сотворить» opens the **cast window**:
  - the spell's full text: time, range, area, duration, check, description, enhancement;
  - «Мана» amount, prefilled with the spell's mana (leading number of `manaText`, else `cost`), with −/+ and typing;
  - «Сотворить · N маны» spends N via `changeResource(MANA, −N)`. It is disabled when N > current mana, and says why.
  - **Builder's call:** too-weak school power warns but doesn't block, like the old screens.
- Empty state (magic on, no learned spells): «Заклинаний нет».
- The spell's `conflictNote` shows under «Расхождение книги», as perks do since stage 4.
- **«+ Заклинание»** in the header opens the editor at Магия.

### Sheet: Снаряжение block

- Shown for every character.
- Header: «Снаряжение · нагрузка L/C — <burden label>», with the penalty when non-zero («−2 к атакам»).
- Rows: every **carried** item.
  - **Order:** weapons first (items with a damage field), then armour (armour field), then everything else, by name inside each group.
  - **Weapon line:** damage, damage type, requirement, range, speed, magazine, the first few properties. Field names vary in the catalog (`Треб.`/`Треб`/`Требование`, several spellings of «Спец. свойства»). One shared normaliser maps them, with tests.
  - **Armour line:** armour value and requirement. **Other items:** name ×quantity.
- Tapping a row opens the **item window**:
  - all fields as a two-column list;
  - the description only when it isn't just the fields again (fixes the duplicated descriptions);
  - quantity −/+;
  - «Не нести» (the item leaves the sheet; it stays in the editor's inventory);
  - «Изменить в редакторе» (opens the editor at Снаряжение with that item open).
- Quantity (never below 1) and carried are play changes, saved at once. They get no undo toast: gear has no undo today, and adding it would change the application layer. −/+ and the editor reverse them (builder's call).
- Empty state: «Снаряжения нет».
- **«+ Предмет»** in the header opens the editor at Снаряжение.

### Sheet layout (builder's call, owner may override)

- **Desktop with tray (≥1100dp):** the right column becomes tabs «Навыки · Заклинания · Снаряжение»; Заклинания only when the block is shown. The last tab is remembered for the session.
- **Tablet and phone:** the blocks stack after Навыки: Заклинания, then Снаряжение.

```
┌ Умения ───────────┐ ┌ [Навыки] [Заклинания] [Снаряжение] ┐
│ …                 │ │ СНАРЯЖЕНИЕ · нагрузка 3/8 — без штрафа │
│                   │ │ Длинный лук  1d6+2 · Треб 2 · 30/150   │
│                   │ │ Кожаная броня  Броня 2                 │
│                   │ │ Верёвка ×2                [+ Предмет]  │
└───────────────────┘ └────────────────────────────────────────┘
```

### Editor: Магия step

Steps become: Развитие · Основа · Характеристики · Умения · Навыки · **Магия** · **Снаряжение** · Итог. The Магия step is always present.

- **Магия выключена** (manaEnabled off): a card **above** the step body, with the same mana switch as Основа (`setIdentity`) and a short line saying what turns on. Schools and spells stay manageable under it, as on the old screens.
- **Header:**
  - mana rank stepper 0–5 with its XP (`MagicEquipmentRules.manaRankXp`);
  - mana maximum and recovery;
  - XP line «ранг маны · школы · заклинания» from `CharacterEconomy`.
  - Mana rank is creation-only in the rules (`setMagicManaRank` refuses after creation, as both old screens show). After creation the stepper is disabled with «Ранг маны меняется только при создании; дальше — навык «Увеличенный запас маны»», plus a link that opens that perk in Навыки.
  - Mana maximum needs rank > 0 and a school with power > 0; the header says so when it is 0.
- **Schools:** rows with a power stepper (`setSchoolPower`) and the XP per power (`magicSchoolRankXp`). «+ Школа» picks from the rulebook school list (`MagicSchoolCatalog`; the rules only save and price those names), with power and note. A school detail edits the name, power and note and offers «Удалить» with «Вернуть». A refused save says «Не удалось сохранить школу» instead of failing silently.
- **Spellbook:**
  - known spells grouped by school, with a filter «Все / Изученные / Неизученные», school chips (the old screens' school filter), and search;
  - **row:** learned switch with the XP it costs, and the usability warning;
  - **detail:** full text, «Изменить» (spell form: name, school, cost, mana text, time, range, area, check, duration, description, enhancement, XP override, «неполное»), «Удалить» with «Вернуть»;
  - **«+ Из каталога»:** search, school chips, «только доступные по силе школы». Each entry opens a read-only detail (full card, book discrepancy, school-power check, the XP it will cost) with «Добавить». Adding learns the spell (`addCatalogSpell`), so reading first matters. Already-known spells are marked. An «неполное» entry stays addable: its button reads «Добавить и исправить» and opens the spell form right after adding (the old escape hatch);
  - **«+ Своё заклинание»:** the same form.
- Split/overlay layout like the Навыки step (`editorPerksSplit`).

### Editor: Снаряжение step

- **Header:** load L/C with the burden label and penalty; «Нагрузка: автоматически / вручную» (`setLoadAutomatic`); a manual value field when manual (`setManualLoad`); the count «N шт. · M позиций».
- **Inventory:** search, groups like the sheet (weapons, armour, other), each row with a carried switch and quantity.
- **Item detail:** fields, description (deduplicated), carried, quantity, «Изменить» (item form: name, quantity, load, category, section, description, fields as «Название: значение» lines), «Удалить» with «Вернуть».
- **«+ Из каталога»:** search, category chips (the catalog's duplicate spellings merged), owned count. Each entry opens a read-only detail (category, load, all fields) with «Добавить». **«+ Свой предмет»:** the item form.
- Opening the editor at an item (from the sheet) opens that item's detail.

### Change list

New lines:
- **Mana:** «Ранг маны a → b» (XP).
- **Schools:** «+ школа N» / «− школа N» / «N: сила a → b» (XP).
- **Spells:** «+ заклинание S» / «− заклинание S» / «S: изучено» / «S: не изучено» (XP) / «S: изменено».
- **Gear (0 XP):** «+ предмет I» / «− предмет I» / «I: ×a → ×b» / «I: с собой» / «I: не с собой» / «I: изменён» / «Нагрузка: вручную V» / «Нагрузка: автоматически».

Each priced like other lines: `CharacterEconomy` on `before` with that one change.

### Host

`DublEditorHost` gains delegates to `DublApplication.magic` and `.equipment`, and `val magicEquipmentCatalog`:
- **Magic:** `setManaRank`, `addMagicSchool`, `updateMagicSchool`, `removeMagicSchool`, `setMagicSchoolPower`, `addCatalogSpell`, `addCustomSpell`, `updateSpell`, `setSpellLearned`, `removeSpell`.
- **Gear:** `addCatalogGear`, `addCustomGear`, `updateGearItem`, `setGearItemCarried`, `setGearItemQuantity`, `removeGearItem`, `setGearLoadAutomatic`, `setGearManualLoad`, `syncCatalogGearLoads`.
- Mana spending uses the existing `changeResource(MANA, delta)`.
- Desktop/web states have most of these already; Android delegates to `CharacterController`, adding any missing call.

### Navigation

- Remove Магия and Снаряжение sections on all platforms (desktop/web `DesktopSection.MAGIC/EQUIPMENT`, Android `AppSection.MAGIC/INVENTORY`).
- **Phones (owner's call):** no bottom bar. `FuryShell` draws none when no section is primary; Кампания and Персонажи (Android «Ещё») sit in the menu.
- Wide top tabs: Лист · Кампания · Персонажи.
- The editor's Итог links «Магия →» / «Снаряжение →» jump to the new steps. `SheetScreen`/`EditorScreen` lose `onOpenMagic` / `onOpenGear`.
- Delete the per-platform `MagicScreen.kt` and `EquipmentScreen.kt` (desktop, web, Android), plus anything only they use.

## Constraints

- **Keep the design we already have (owner).** The mockup decides only the new parts: the Заклинания and Снаряжение blocks, the cast and item windows, and the Магия and Снаряжение steps. Everything else keeps its stage 2–4 look and components: the sheet header, vitals, Умения, Навыки, tray, the editor rail, step tabs, XP card, change list and toasts. The mockup's versions of those are stand-ins. New UI is built from the existing parts (`EditorCard`, `EditorRow`, `EditorChip`, `EditorSquareButton`, `FuryDialogScaffold`, the sheet's play cards) in the app's five themes, not from the mockup's CSS.
- **Shared code only:** platforms keep wiring.
- **Restore point:** every editor action is inside the visit's restore point (magic and gear live on the character).
- **Undo toast:** casting (mana) reports to the platform undo toast, like the mana meter. Gear quantity and carried on the sheet don't (see the sheet Снаряжение block). None of these are editor changes.
- **Layout and themes:** works at 360dp, tablets and desktop, in all five themes.
- **Python guards:** guards pinning the old screens are repointed when they guard a function and retired when they pinned old layout. The failing baseline may only shrink.

## Decisions from the builder check (builder's calls)

- **No free-name schools.** The rules only save and price rulebook school names, and rules stay unchanged. The form picks from the list, which also fixes Android's misleading «уже существует» on free text.
- **Catalog loads are repaired whenever the sheet shows a character.** That way the sheet's load line and the burden penalty on defence and attacks are right without opening the editor.
- **Both catalog pickers get a read-only detail before «Добавить»** (desktop's «Подробнее»; nothing lost).
- **Mana rank stays creation-only** (rules unchanged); after creation the step points to the «Увеличенный запас маны» perk. The «Магия выключена» card sits above the step body.
- **Gear play changes on the sheet have no undo toast** (there is none today).

## Left to the builder

- Exact row layouts.
- Which weapon fields fit on one line at each width.
- Panel vs dialog for forms on wide screens.
- Test file layout.
- Wording of empty states and reasons copied from the old screens.
