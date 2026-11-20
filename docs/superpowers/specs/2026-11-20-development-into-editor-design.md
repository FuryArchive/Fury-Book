# Навыки into the editor — design (revamp stage 4)

Status: draft for owner approval · 2026-11-20
Builds on: `2026-11-18-character-editor-design.md` (editor, perk detail), `2026-11-18-skills-into-editor-design.md`
(stage 3: the same move for Умения), `2026-11-18-play-sheet-redesign-design.md` (sheet, resource meters).

## Why

Stage 4 was "rebuild the Навыки tab". The editor's Навыки step already browses, takes (with chains), raises and
removes perks, and the sheet shows owned perks. The owner's call: **the tab goes; nothing it does is lost; whatever
has to move into shared code moves** ("an absolute win"). Same split as stage 3: the sheet is for playing, the
editor is for changing the character.

## Goals

- Remove the Навыки tab on Android (`FeatsScreen`), desktop and web (`DevelopmentScreen`).
- Every function it had is reachable afterwards (table below), on all three platforms.
- Perk editing (custom perks, local edits) lives in the editor's Навыки step.
- ЦИ setup (on/off, extra ranks) lives in the editor; using ЦИ techniques lives on the sheet, behind the ЦИ meter.
- The sheet's perk «Подробнее» becomes one shared dialog on all platforms (today desktop/web borrow the old tab's
  dialog and Android has a read-only one).

## Non-goals

- Rules, formulas, saved data, transfer format, sync, ЦИ rules: untouched. All mutations exist already
  (`DevelopmentApplication`, chi calls on the platform states / `CharacterController`).
- Магия and Снаряжение (stage 5).

## Anti-goals

- Any Навыки-tab function becoming unreachable on any platform.
- A second copy of availability, cost or ЦИ rules in UI code (`DevelopmentRules`, `DevelopmentAcquisitionPlanner`,
  `ChiRules`, `CharacterEconomy` stay the only sources).
- Editor changes not covered by «Отменить всё» or not listed in «Изменения за этот заход».

## Approaches (owner picked 1)

1. **Into the editor + ЦИ techniques on the sheet's ЦИ meter** — *picked*: same split as stage 3; the meter is
   where the player already looks at ЦИ.
2. Techniques as cards in the sheet's Навыки block with «Использовать» — rejected: mixes actions into a reading list.
3. Keep a slim Навыки tab for editing — rejected: an extra screen for rare actions, against stage 3's direction.

## Where each tab function goes

| Old Навыки tab (Android FeatsScreen + desktop/web DevelopmentScreen) | New home |
|---|---|
| Catalog by kind, search, filters «доступно/почти», grouping | Editor Навыки step (exists) |
| Detail: benefit, requirements, unlocks, chain preview, ability-point source choice | Editor perk detail (exists) |
| Take / take with chain / forced take with confirm / raise / remove rank | Editor perk detail (exists) |
| **«Взять все оставшиеся ранги»** (≥2 ranks left, `targetRank = maxRank`; health-ranks plan) | Editor perk detail: new action |
| **«Добрать требования»** (prerequisites only, `includeTarget = false`) | Editor perk detail: new action when a chain exists |
| **«План персонажа»**: ☆/★ per perk, «План» filter, summary «Осталось X XP · Y ОС», «Взять план» (one multi-target request) | Editor Навыки step: ☆ in the detail, «План» filter chip, plan card above the list with «Взять план» |
| Linked entries: tap a requirement target or an unlocked perk to open it | Editor perk detail: requirement targets and «Открывает» names become links that open that perk |
| Budget panel (XP, ОС, ЦИ economy) | Editor rail + summary (exists) |
| **Add own perk** (`addCustomDevelopment`) | Editor Навыки step: «+ Свой навык» → perk form |
| **Edit own perk** (`updateCustomDevelopment`), **delete own perk** (`removeCustomDevelopment`) | Editor perk detail: «Редактировать свою запись», «Удалить свою запись» |
| **Local edit of a rulebook perk** (`setDevelopmentOverride`), «Сбросить к рулбуку» (`resetDevelopmentOverride`) | Editor perk detail: «Локальная правка», «Сбросить к рулбуку» |
| **ЦИ on/off** (`setChiEnabled`; locked on while «Внутренняя ЦИ» is owned) | Editor Навыки step, ЦИ kind: ЦИ card at the top |
| **ЦИ extra ranks** (`setChiBonusRanks`, 0–10, XP per rank) | Same ЦИ card: stepper with the XP price |
| ЦИ current ±, «Восстановить» | Sheet ЦИ meter (exists) and the new ЦИ panel |
| **«Приёмы ЦИ»** list with «Использовать» (spend the technique's ЦИ cost; reason when it can't be used) | Sheet: tap the ЦИ meter → ЦИ panel; also a read-only list under the editor's ЦИ card, so techniques stay readable while ЦИ is off or its meter is hidden |
| Sheet perk «Подробнее» (desktop: local edit/reset/delete via the old dialog; Android: read-only) | One shared dialog: details + «Настроить в редакторе» (opens the editor on that perk) |

## Design

### Editor — Навыки step

- **«+ Свой навык»** in the step header opens the perk form (below) for a new entry; on save the new perk opens in
  the detail.
- **Perk detail** gains, at the bottom:
  - for a rulebook perk: «Локальная правка» (form pre-filled; saves with `setDevelopmentOverride`) and, when an
    override exists, «Сбросить к рулбуку»;
  - for an own perk: «Редактировать свою запись» (`updateCustomDevelopment`) and «Удалить свою запись»
    (`removeCustomDevelopment`).
  «Сбросить к рулбуку» and «Удалить свою запись» act at once and offer «Вернуть» (restore point taken just
  before, like stage 3's hide/delete). Deleting closes the detail.
- **Links in the detail:** a requirement line whose target is a catalog perk, and each name under «Открывает»,
  open that perk (same as picking it from the list).
- **ЦИ kind tab** (only when the ЦИ pack is mounted, as today): a card above the list —
  `ЦИ  [вкл/выкл]   Доп. ранги [−] 3 [+] · 20 XP за ранг   Максимум: база 4 + запас 3 + развитие 1 = 8`.
  The switch is disabled with the reason when «Внутренняя ЦИ» keeps it on; text matches the old card.

- **Bulk actions in the detail:** «Взять все оставшиеся ранги · <цена>» when two or more ranks remain and the
  whole run is affordable through the planner (`targetRank = maxRank`); «Добрать требования · <цена>» when the perk
  has a chain (`includeTarget = false`). Both go through `acquireDevelopment` and land as normal change lines.
- **Plan:** ☆ «В план» / ★ «В плане» toggle in the detail; a «План · N» filter chip next to Все / Доступно / Почти;
  when the plan is non-empty, a card above the list: «План персонажа · N — осталось X XP · Y ОС» and «Взять план»
  (one multi-target `DevelopmentAcquisitionRequest` for the planned entries, as the old screens built it). The plan
  is session-only, exactly as today (not saved), kept in editor state for the editor visit and across steps.

### Perk form (shared, one composable)

Fields, as the old local-edit dialog: Название, Раздел, Категория, cost type (XP / ОС chips), Стоимость, Макс. ранг,
Требования, Эффект, Особое / заметки, Теги (через запятую), ID родительской ветки, Варианты ОС
(«источник=цена; …»), Механический конфликт, Комментарий конфликта, and three switches: «Правило неполное / спорное»,
«Повторяемая запись», «Корень совершенной ветки». «Сохранить» with validation (name required, numbers ≥ 0);
it is an explicit-save dialog (these are multi-field records), not live typing. Parsing of tags/ability options
reuses what the old dialog did, moved to a shared function with tests.

### Changes list

New lines: `+ <свой навык>` / `− <свой навык>` / `<навык>: запись изменена` (custom entries),
`<навык>: локальная правка` / `<навык>: правка сброшена` (overrides), `ЦИ включено` / `ЦИ выключено`,
`ЦИ: доп. ранги a → b` with its XP from `CharacterEconomy`. ЦИ current value is play state and is not listed.
Pricing: each side of the comparison is priced with **its own** effective catalog
(`before.effectiveDevelopmentCatalog(canonical)` / `after…`), so a local edit that changes a perk's cost or max rank
shows the repricing in the line's XP and in the net, and a deleted own perk keeps its name. Deleting an owned own
perk is one line («− <навык>»), not two. This needs the canonical catalog on the host (below).

### Sheet — ЦИ panel

- Tapping the ЦИ meter (where it is shown) opens a ЦИ panel instead of the generic resource dialog/adjuster, on
  every width: current/max with −1 / +1, the «Количество» amount field the generic dialog has (multi-point
  changes), «Восстановить», then «Приёмы ЦИ»: each technique with name, school, ЦИ cost, action,
  effect, and «Использовать» (spends `availability.chiCost`) or the reason it can't be used, from `ChiRules`.
- Empty catalog → «Приёмов ЦИ в каталоге нет.»

### Sheet — perk «Подробнее»

- One shared dialog for all platforms: name, category, rank, benefit, requirements with status, «Открывает».
  One action, «Настроить в редакторе», opens the editor at Навыки with that perk's detail open (like stage 3's
  «Об умении»). Desktop/web stop borrowing the old tab's dialog; Android gains the editing path.

### Navigation and deletions

- Remove the Навыки section on all platforms (desktop/web `DesktopSection.DEVELOPMENT`, Android `AppSection.FEATS`
  and its loading gate). Phone bottom bar: **Лист, Магия, Вещи, Кампания** (builder's call: Кампания joins rather
  than leaving three tabs; owner may override).
- Delete `DevelopmentScreen.kt` (desktop, web), `SheetDevelopmentDetails.kt` (desktop, web), `FeatsScreen.kt`
  (Android) and anything only they use. `SheetScreen`'s platform `developmentDetails` hook goes away.

### Host

- `DublEditorHost` gains: `addCustomDevelopment(entry): String?`, `updateCustomDevelopment(entry): Boolean`,
  `removeCustomDevelopment(entryId)`, `setDevelopmentOverride(entry)`, `resetDevelopmentOverride(entryId)`,
  `setChiEnabled(enabled)`, `setChiBonusRanks(rank)`, `changeChi(delta)`, `restoreChi()`, and `val chiCatalog: ChiCatalog`
  (empty when the pack is off), and `val canonicalDevelopmentCatalog: DevelopmentCatalog` (the catalog before this
  character's overrides/custom entries, for pricing the change list). Desktop/web states have them; Android adds delegates to `CharacterController`
  (adding the chi enable call there if missing) and loads the catalog like `FeatsScreen` does.

## Constraints

- Shared code only for UI; platforms keep wiring.
- Every new editor action is inside the visit's restore point (custom entries, overrides and ЦИ settings are on the
  character).
- Works at 360dp, tablets, desktop; five themes.
- Python guards pinning old Навыки files are repointed where they guard a function, retired where they pinned old
  layout. Baseline may only shrink.

## Decisions from the builder check

- Plan, bulk ranks and «Добрать требования» move to the editor (owner rule: nothing lost); the plan stays
  session-only as today.
- ЦИ techniques are also readable in the editor's ЦИ card (read-only), so hiding the meter or turning ЦИ off never
  hides them; the ЦИ panel replaces the generic meter dialog for ЦИ and keeps its amount field.
- Change-list pricing uses each side's own effective catalog; host exposes the canonical catalog.
- Guards: test_development_bulk_ranks, the development cockpit/performance/stability guards that pin plan or bulk
  functions are repointed to the editor.

## Left to the builder

Panel and form layout details, whether the form is a dialog or a panel on wide screens, test file layout, the exact
wording of reasons copied from the old screens.
