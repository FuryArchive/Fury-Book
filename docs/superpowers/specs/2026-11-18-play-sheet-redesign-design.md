# Play sheet redesign + themes — design

Status: draft for owner approval · 2026-11-18
Mockup (approved look): https://claude.ai/artifact/YBATzTRecCrYC1MEzAg7ex
Builds on: `2026-11-17-ui-revamp-design.md` (Fury HUD), stage 2b sheet (`com.furybook.dubl.ui.sheet`).

## Why

The 2b sheet works, but with a real character it reads as one grey block. Everything sits on the
same tile, it hugs the screen edges, and skills and perks are the plainest parts even though they
are the most complex. The owner wants the sheet split into two jobs:

1. **Игра**: during a session, show as much as possible at a glance, with as little scrolling and
   as few pop-ups as possible.
2. **Редактор**: creating and changing a character, roomy and step-by-step (next design doc, not this one).

This doc covers (1) plus app-wide colour themes.

## Goals

- Desktop (≥1100dp): the whole play sheet fits one screen. Long lists scroll inside their own
  column, never the page.
- Phone: everything needed in a fight is on the first screen. Everything else is reachable by
  scrolling or with one tap, without dialogs.
- Умения and Навыки get most of the space. Attributes and derived stats get very little.
- Holds up under load: 9+ resources, 8+ conditions, 25+ perks, 12+ notes, a 60-character name.
- 5 colour themes the player picks; the choice persists.

## Non-goals

- The editor / character creation (separate doc, next).
- Changing any rule, calculation or saved data. The rules packages stay untouched.
- Hand-written perk summaries.

## Anti-goals (a failure even if it "works")

- Something that worked in 2b stops working: rolling, vitals, conditions, custom resources, notes,
  undo toasts, FCP chi gating, Android back, portraits.
- Text overflowing its box, or numbers wrapping, at any width or with long data.
- A theme that fails contrast (text < 4.5:1, large numbers < 3:1).

## Approaches considered

- **A. One sheet with an edit toggle**, re-laid out. Cheapest, but creation stays cramped.
- **B. Separate Игра and Редактор screens.** ← **picked by the owner.** Each screen gets one job.
- **C. Play sheet split into tabs (Бой / Умения / Навыки / Отыгрыш).** Hides what should be visible
  at a glance.

Within B, for the stats the owner tried and rejected: a 14-chip row (cramped), two full rows
(too tall), and chips next to the name. They picked **skills grouped by attribute, with the
attribute tile as the group's left edge**.

## The play sheet (decided with the owner, see the mockup)

### Layout by width

Breakpoints are the shell's existing ones (`PHONE` <600, `WIDE` <1100, `WIDE_WITH_TRAY` ≥1100).

- **≥1100 (desktop).** No page scroll.
  - Top block: identity row, resource rack, conditions line.
  - Then two columns, **Умения** | **Навыки**, each scrolling on its own.
  - The shell's 340dp right tray becomes the third column: roll result, combat actions, notes.
- **600–1099 (tablet).**
  - Compact header pinned at the top.
  - The page scrolls: derived stats row, then Умения | Навыки in two columns, then actions + notes
    side by side.
  - Roll result shows as a toast in the bottom-right corner.
- **<600 (phone).**
  - Compact header pinned at the top.
  - One scrolling column: derived stats, Умения, Навыки, actions, notes.
  - Roll result shows as a toast above the bottom nav.

### Identity row (desktop)

- Portrait (52dp), then name and concept, one line each, cut with "…" (full text on hover).
- The 6 derived stats as small tiles in their own tint (see Themes, `derived*`). They are not
  clickable and look clearly different from attributes.
- One gold chip with XP (free / total) and ОС ("очки способностей"); ОС turns accent-red when negative.
- An icon button for Редактор.

### Compact header (tablet and phone, pinned)

- Portrait 36dp, name and concept (cut with "…"), XP chip, ОС chip, a 44dp Редактор button.
- **Health** gets its own full row: value, bar, and 44dp − / + buttons.
- **Other resources** sit in a sideways-scrolling strip of chips (short name, value, thin bar).
  Tapping a chip opens an inline − / + row under the strip. Tapping again or × closes it.
- **Conditions** sit in a sideways-scrolling strip, followed by "+ состояние". Tapping a condition
  shows its effect as one line under the strip.

### Resources (desktop rack)

- Grid of cells with a minimum width of 178dp, filling the row and wrapping as needed.
- Health always comes first and spans two cells.
- Each cell: colour dot, label (cut with "…"), value (never wraps, fixed-width digits), small − / +.
- Meter: pips if max ≤ 10, otherwise a bar. Health always uses a bar.
- A resource with max 0 is dimmed (as mana is today).

### Conditions (desktop)

- One line: the first 5 chips, a "+N" button that expands to all of them (and "свернуть" to
  collapse), and "+ состояние".
- Chip colour shows the kind: harmful, helpful, or custom.
- Tapping a chip shows its effect inline on the same line.

### Умения: grouped by attribute

- 8 groups in the fixed attribute order СИЛ ЛОВ ТЕЛ СКР ИНТ ВОС ВОЛ ХАР.
- Each group has an **attribute tile** on the left (short name and value). Tapping the tile rolls
  the attribute.
- To the right of the tile, that attribute's skills, highest bonus first. A skill sits under its
  default attribute; choosing another attribute still happens at roll time, as today.
- An attribute with no shown skills shrinks to a slim row with the text "нет умений".
- Skill row:
  - name (cut with "…");
  - an optional gold mark when a perk modifies the skill (from `skill_effects`), with the reason on hover;
  - rank shown as up to 10 small bars;
  - the bonus chip.
- Bonuses ≥ 12 are highlighted with the accent. The others are neutral.
- Header toggle: "Только с рангами" (the default) / "Показать все".
- Hovering a skill row shows how the number is built, for example "2d6 + 15 (Харизма 9 + ранг 6)".

### Навыки (perks)

- Description = the rules' `benefit` text, verbatim. By default it is cut to one line.
  A header toggle switches between "Подробно" and "Кратко", and tapping a card expands it in place
  (full text plus requirements).
- Empty `benefit`: show "Нет описания в правилах — см. требования." (or, for an access root,
  "Открывает ветку способностей.").
- **Cost tags** are read from the benefit text: "N ОД", "N маны", "N вын.", "реакция". Only phrases
  like "за N ОД / тратите / затрачивая" count; "на N ОД меньше" does not.
- A perk with any cost tag counts as **Активный**; filter chips: Все / Активные N / Пассивные N.
- A perk whose requirements are not met gets a yellow warning icon, not red. The header shows
  the total count.
- Groups follow the development type (Обычные / Особые способности / Боевые искусства / Магия).
  Children of a branch are indented under their root.
- A search field appears when there are more than 10 perks.
- Rank is shown as small pips.

### Side content

- Combat actions: chips, as today.
- Notes:
  - a list showing the title and one line of text; pinned notes first;
  - tapping a note expands it in place;
  - a search field appears when there are more than 6 notes;
  - on desktop the list scrolls inside its own box.
- Roll: the existing roll session and history. Only where the result is shown changes (see Layout).

## Themes

- **5 themes:** Ярость (default), Графит, Неон, Пустошь, Пергамент (light). The exact values are in
  the mockup, in `THEMES` inside any board.
- **Roles:**
  - surfaces: `bg head sunk panel panel2 tile hover`;
  - lines: `line line2 line3 track`;
  - text: `text text2 muted dim`;
  - accent: `acc acc2 accSoft accLine accText accDeep onAcc`;
  - gold: `gold goldSoft goldLine goldText`;
  - derived stats: `dBg dLine dLabel dText`.
- Health follows `acc` and endurance follows `gold`. Mana, custom resource colours and the
  "helpful condition" colour stay fixed in every theme.
- The choice persists per device. The picker lives in the account / character menu (top-right on
  desktop, the menu on phone), not on the sheet.
- **Applies app-wide.** Today, 22 files read the top-level colour `val`s in `ui/theme/Color.kt`
  directly. They move to a palette provided through a `CompositionLocal`, with the Material
  `ColorScheme` built from the active palette (light for Пергамент). The `Dubl*` / `Fury*` names
  may stay as aliases while screens migrate, but every screen must follow the theme before this
  stage counts as done.

## Constraints

- Kotlin Multiplatform; shared UI in `shared/`; Android, desktop and web shells; Android builds only in CI.
- The rules packages (`com.furybook.dubl.model/.application/.data/.state`, `com.furybook.core`) don't change.
- FCP contract: chi gating stays behind `uiMounts(...).forFeature(CHI)`.
- The Python test baseline (37 failed + 6 errors) may only shrink.
- 44dp touch targets on touch layouts. Contrast is checked per theme, using the existing `Contrast.kt` helpers.
- No merge to main without the owner's say-so.

## Left to the builder (my calls, marked)

- *My call:* the play sheet replaces 2b's play mode inside `SheetScreen`. Until the editor stage
  ships, "Редактор" opens 2b's edit mode, which stays as it is.
- *My call:* Шершень and the heavy baron become a committed dev fixture (seeded only when
  `SEED_DIR` is set), replacing the untracked throwaway seed test.
- *My call:* the cost-tag parser is a pure function with tests built from real catalog texts,
  plus a small override map for the cases it gets wrong.
- Exact dp spacing, animation, icons, and how the theme preference is stored on each platform.
