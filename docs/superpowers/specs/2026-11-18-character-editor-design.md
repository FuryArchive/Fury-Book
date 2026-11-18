# Character editor («Редактор») — design

Status: draft for owner approval · 2026-11-18
Mockup (approved look, clickable): https://claude.ai/artifact/3u11jd826wDCe7wvRvEtZv
Builds on: `2026-11-17-ui-revamp-design.md` (Fury HUD), `2026-11-18-play-sheet-redesign-design.md` (play sheet, themes).

## Why

The play sheet now does one job: playing. Creating and changing a character still happens in the
cramped 2b edit mode, plus the old Умения / Навыки tabs. The owner wants one roomy editor that is:

- **friendly for a brand-new character**: a guided walk, step by step, with the cost of every
  choice in view;
- **fast for a small addition to an existing one**: after a session, a new perk or a few rank-ups
  in one to three taps.

## Goals

- One shared editor screen (Android, desktop, web) that replaces 2b edit mode.
- Steps: **Основа → Характеристики → Умения → Навыки → Итог**. For an existing character there is
  also a **Развитие** landing view, which opens first.
- Skill ranks and perks are bought *inside* the editor, with the existing rules: availability,
  requirements, prerequisite chains.
- XP and ОС always visible. Every change is saved immediately, and **«Отменить всё»** puts the
  character back the way it was when the editor opened.
- Works under load: the heavy baron, 26 perks, long names, at every width.

## Non-goals

- Магия and Снаряжение steps. They stay on their tabs, linked from Итог (stage 5 rebuilds them).
- Rebuilding the Умения / Навыки tabs. They stay as they are until stages 3–4.
- Rules, formulas, saved data, transfer format. The only application change is one restore call (below).
- Resources, conditions and notes. They are play-time things and live on the play sheet.

## Anti-goals (a failure even if it "works")

- Anything that works in 2b edit mode becomes unreachable, apart from the group manager (retired
  by owner ruling, below).
- A second implementation of costs or requirements in UI code. Every number comes from the model.
- A perk taken through a chain that skips a requirement, or charges a different price than the
  Навыки tab would.
- Text overlapping or numbers wrapping at any width (the mockup's skill-cost overlap is the example).

## Approaches considered

- **Scope** (owner picked 3):
  1. A full builder with Магия/Вещи steps: too big, pulls stages 3–5 forward.
  2. Sheet stuff only, with steps that jump out to the old tabs: creation still bounces between screens.
  3. **Core builder**: steps for identity, attributes, skills and perks, with buying inside; magic
     and gear linked.
- **Saving** (owner picked 1):
  1. **Live, plus «Отменить всё»**.
  2. Draft until «Готово»: the sheet goes stale, work is lost on a crash, more code.
  3. Live only: unwinding a misclicked chain by hand is annoying.
- **Build** (builder's call, owner did not object):
  - **chosen:** a new shared screen reusing the existing rules objects;
  - rejected: wrapping the old platform Skills/Development screens as steps (three copies, old look);
  - rejected: dialogs stacked over the sheet (cramped).

## Owner rulings

- Live saving + «Отменить всё» (one call that restores the snapshot taken on open).
- **Group manager retired; data kept.** `SheetGroup` lists stay in extras, sync and transfer
  untouched, but no screen edits them. Can come back later.
- The mockup is the approved look, including the overlap fix (cost label truncates with "…").

## The editor (as mocked up)

### Entry points

- Play sheet «Редактор» button:
  - character in creation (`!creationComplete`): opens at **Основа**;
  - otherwise: opens at **Развитие**.
- Characters list «Новый персонаж» creates the character as today, then opens the editor at
  **Основа** in creation mode.
- Leaving: the header's back arrow, «Готово», and Android system Back all return to the play sheet.
  Nothing to confirm, because everything is already saved.

### Layout by width (shell breakpoints)

- **≥1100 (desktop).** Three columns:
  - left, a step rail (232dp): number, step name, and a one-line status such as "37 рангов · 920 XP"
    or "2 замеч.";
  - centre, the step content, with «← Назад / Далее: <step> →» pinned under it;
  - right, a live summary (312dp): free XP (big, red when negative) with a spent bar, ОС free/budget,
    the 6 derived stats with the delta since opening, and **«Изменения за этот заход»** with
    «Отменить всё».
- **<1100 (tablet, phone).**
  - Steps are a sideways-scrolling tab strip under the header.
  - The content scrolls.
  - A sticky footer holds a combined XP/ОС chip, a «↺ N» changes button that opens a bottom sheet
    with the list and «Отменить всё», and ‹ / «Далее ›».
- Header everywhere: back arrow, «Редактор · <name>», a mode badge («Создание» in gold /
  «Развитие» neutral) with a short hint, and «Готово».
- On phone the editor takes the whole screen, hiding the shell's bottom nav (*builder's call*:
  focus, and footer space).

### Развитие (existing characters only, first view)

Free XP and ОС at the top, then four short lists, each drawn from the rules:

- **Повысить умение:** ranked skills whose next rank fits in free XP, by bonus, top 6. Each row:
  name, attribute, rank r→r+1, new bonus, cost, «+».
- **Повысить характеристику:** attributes whose next point fits, cheapest first, as chips
  «ЛОВ 8→9 · 95».
- **Взять навык:** perks available now and affordable, cheapest first, top 6. Each row: name,
  category, the first sentence of the rules text, cost, «Взять» / «Повысить». Tapping the name
  opens it in Навыки.
- **Почти:** perks one missing step away (the desktop screen's ALMOST rule), with "сначала <step>"
  and «С цепочкой · N XP».
- «Все умения →» / «Все навыки →» jump to the steps.

### ① Основа

- Portrait (pick / remove, as today), name, concept.
- Размер and Ноги steppers, the Мана switch.
- Опыт: total XP with ±100 and a number field. Creation XP and the adjustment work as in today's
  economy dialog. ОС budget shows the auto value ("1 за каждые полные 1000 XP") with an
  override stepper and «вернуть авто».
- A line saying that resources, conditions and notes are edited on the sheet.

### ② Характеристики

- 8 cards: −/+ (−5…10), the value, "+1: N XP" and "−1: вернёт N" from `CharacterEconomy`, and a
  delta badge when it changed since opening.
- The derived stats underneath (and in the desktop summary) update live, with deltas.

### ③ Умения

- Grouped by attribute, like the play sheet:
  - desktop and tablet: the attribute tile on the left;
  - phone: a slim header row ("ЛОВ 8 Ловкость"), so names get the width.
- Row:
  - name;
  - rank pips (ranks gained this visit in gold);
  - next-rank cost (`skillNextRankCost`, so Самоучка is respected), cut with "…";
  - −, rank, +;
  - bonus chip.
- Toolbar: search, «Только с рангами / Показать все» (default: with ranks for existing characters,
  all for new ones), «+ Своё / специализация». It calls the existing `addSpecialized` / `addCustom`
  through new shared forms, because today's forms are platform code (*my call*).
- Hide/restore of skills stays on the old Умения tab, along with local rulebook overrides (rename,
  Auto 6/12, untrained rule, effect toggles). They are rare and not about building.

### ④ Навыки

- Kind tabs: Обычные / Особые / Боевые / ЦИ / Взятые, with counts. ЦИ appears only when the pack
  mounts it (`uiMounts(...).forFeature(CHI)`, same as the Development screen).
- Filter chips: Все / Доступно / Почти. Search over name, category and rules text.
- Base mana stays on Магия, excluded as on the Development screen.
- These stay on the old Навыки tab until stage 4: custom entries («Своя запись»), local edit/reset
  of an entry, and the chi resource/techniques cards. The ЦИ tab here lists chi development entries
  only (*my call*).
- List cards:
  - name, cost ("40 XP/ранг", "1 ОС"), category;
  - a status pill: Доступно / Почти / Мало XP / Требования / Только при создании /
    Взят · r/m;
  - a yellow «!» on owned perks whose requirements are no longer met;
  - branch children indented under their root, as on the play sheet.
- Detail (desktop: a 360dp pane beside the list; tablet and phone: over the list, with «← К списку»):
  - name, category, kind, cost, rank pips;
  - the rules text verbatim (empty: "Нет описания в правилах — см. требования.");
  - **requirements** one per line with ✓ / ✗ / «?» (cannot be checked automatically, does not
    block) and "у вас N";
  - «Открывает» (`planner.unlocks`).
- Actions:
  - «Взять · N XP» / «Повысить до ранга r · N XP»;
  - when requirements are missing: **«Взять с цепочкой · N XP»**, with a gold box listing every
    step and its cost, from `DevelopmentAcquisitionPlanner.plan`;
  - planner choices ("Холодное оружие 5 или Рукопашный бой 5", ability options) appear as chips
    inside that box;
  - unresolved requirements, too little XP/ОС, or "только при создании" after creation: the
    reason in place of the button;
  - owned: «Убрать ранг (+N XP)». No cascade: perks that depended on it stay and get the «!»
    (*my call*, same as the old screens).
- Taking a perk: the step goes through `DevelopmentApplication.acquire(request)`. A short toast
  confirms it ("Взято цепочкой: …").

### ⑤ Итог

- **Замечания** box (green "Всё в порядке" when empty). Each line jumps to the step that fixes it:
  - XP overspent;
  - ОС over budget;
  - no name;
  - owned perks with unmet requirements;
  - catalog spells with no price (the existing economy warning).
- Cards, each opening its step:
  - Основа;
  - Опыт breakdown (attributes / skills / perks / free, plus chi when mounted);
  - Характеристики (8 values + derived line);
  - Умения (ranked list);
  - Навыки (owned list);
  - Ещё: links to Магия and Снаряжение.
- «Завершить создание», with one line on what changes, or «Открыть создание снова».

### Changes and «Отменить всё»

- On open, the editor stores a snapshot of the active character and its extras.
- The changes list compares the snapshot with the current state, one line per field:
  - "Ловкость 8 → 9 −95 XP";
  - "+ Острый слух −30 XP";
  - "Опыт 4580 → 4680";
  - "Создание завершено";
  - and so on.
- The net line reads "Потрачено N XP" / "Возвращено N XP".
- «Отменить всё» calls a new **`DublApplication.restoreActive(snapshot)`**. It writes the stored
  character and extras back through the sessions (persisted and synced like any change) and records
  one undo entry, so the play sheet's undo can bring the edits back.
- The list resets when the editor is closed and opened again.

## Where 2b edit-mode features end up

| 2b edit mode | After |
|---|---|
| Identity dialog, portrait pick/remove | Основа |
| Economy dialog (total, creation, adjustment, ОС override, complete/reopen) | Основа + Итог; the play-sheet XP chip still opens the dialog |
| Attribute −/+ with undo toast | Характеристики (live, plus «Отменить всё») |
| Resources dialog (hide/show, add custom) | Play sheet: «Настроить» at the end of the resource rack / strip |
| Vital dialog, conditions dialog, notes | Already on the play sheet, unchanged |
| Size/legs quick edit (stat popup) | Still in the play-sheet Размер popup, and in Основа |
| Skill / development group managers | Retired (owner ruling); data kept |
| «Все умения» / «Все навыки» links | Развитие and the step tabs |

When this ships, 2b edit mode and its edit-only code are deleted: `SheetScreen`'s editing branch,
`SheetGroupManager`, `SheetGroupDrag`, `SheetIdentityDialog`, and the edit paths in `SheetStats`,
`SheetSkills` and `SheetDevelopment` where nothing else uses them. The Python tests that read those
files are retired or rewritten in the same stage.

## Constraints

- Kotlin Multiplatform; shared UI in `shared/`; Android builds only in CI.
- Rules packages unchanged, except the one application-level restore call.
- FCP: chi stays behind `uiMounts(...).forFeature(CHI)`.
- Python baseline (37 failed + 6 errors) may only shrink.
- 44dp touch targets on touch layouts. Contrast holds in all 5 themes.
- Undo, toasts, Android Back, portraits keep working. No merge to main without the owner.

## Left to the builder (my calls, marked)

- *My call:* a new package `com.furybook.dubl.ui.editor`. Its host interface extends
  `DublSheetEditHost` with the skill and development calls it needs, each a one-line
  `DublApplication` delegate, like today.
- *My call:* `SheetScreen` swaps to `EditorScreen` instead of its edit body. The platforms pass an
  "open editor at Основа" flag after «Новый персонаж».
- *My call:* the editor's view models are pure functions over the character and catalog, unit-tested
  with Шершень and the heavy baron fixtures. Covered: Развитие lists, status pills, changes diff,
  Итог problems.
- *My call:* «Почти» uses the Development screen's rule: a prerequisite plan with no unresolved
  requirements and exactly one step.
- Exact dp, animation, icons; what the tablet detail sheet looks like (slide-over vs full).
