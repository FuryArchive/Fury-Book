# Fury Book UI/UX revamp — design

Date: 2026-11-17 · Branch: `ui-revamp` · Mockups: https://claude.ai/artifact/Lf752BRmp3qZuJTXnduLiv (direction B, "Fury HUD")

## Why

The current UI is hard to use and unpleasant to look at on every platform. Everything has the
same visual weight, the sheet is laid out for editing rather than playing, each screen styles the
same data differently, dialogs are rough, and the Web UI is a hand-copied fork of Desktop.

## Goals

- One look across Android, Desktop and Web: direction B from the mockups.
- A sheet built for play: vitals and rolls first, editing behind a Play/Edit switch.
- One dialog pattern, one set of components, one implementation of each screen.
- Every action that works today still works.

## Non-goals

- Rules, formulas, persistence, snapshot schema, transfer format, cloud protocol: untouched.
- No light theme in this pass (tokens are structured so one can be added later).
- No new features beyond the docked roll tray and Play/Edit switch.

## Anti-goals

- Breaking or dropping an existing function. Each screen gets a feature inventory (Android and
  Desktop combined) before it is rebuilt, and the rebuilt screen must cover all of it.
- A second copy of game formulas in UI code.

## Look (decided with the owner)

- Fonts, bundled, both OFL with Cyrillic: **Unbounded** (headings, numbers), **Onest** (body).
- Palette: near-black `#08080A`, surfaces `#111114` / `#16161A`, lines `#26262C` / `#34343A`,
  text `#F4F1EC`, muted `#B4B1AC`, crimson `#E5384F` (bars, accents), deep crimson `#B82840`
  (filled buttons, roll tray; white text passes contrast), gold `#F5C04E` (XP, stamina).
- Chunky stat tiles, thick resource bars, 6–8dp radii, 44dp minimum touch targets.
- Rolls: a docked tray on wide screens, a bottom sheet on phones. Shows dice, maths, target
  result, advantage/hindrance/modifier, short history.
- Dialogs: title + one-line context, chips instead of checkbox lists, steppers for ranks, full-width
  fields, destructive action separated on the left, one filled primary button.
- Navigation: top tabs on wide screens; five bottom tabs on phones (Лист, Умения, Навыки,
  Магия, Вещи) with characters/campaign/settings behind a menu button.

## Approaches considered

1. **Reskin in place** — new theme and fonts over existing layouts. Fast, but leaves the
   usability problems and three diverging UIs. Rejected.
2. **Redesign each platform separately** — full freedom per platform, but triples the work and
   keeps the Web fork. Rejected.
3. **Shared design system, then shared adaptive screens** — chosen. Screens move into `shared`
   and adapt by width (`layoutClassForWidth`); platform apps become thin shells.

## How (builder's calls unless marked)

- Design system in `com.furybook.ui.*` (shared): tokens, typography, components
  (button, tile, bar, chip group, stepper, field, tabs, dialog/sheet, roll tray).
- DUBL screens in `com.furybook.dubl.ui.*` (shared), driven by `DublApplication` state and typed
  callbacks. Platform shells keep only: window/activity, file pickers, cloud/auth wiring, storage.
- Fonts ship as shared Compose resources.
- Work lands as stages on `ui-revamp`, each one buildable:
  1. Design system + theme applied app-wide (old screens pick up fonts and colours).
  2. Sheet + roll tray.
  3. Умения + skill dialogs.
  4. Навыки (development).
  5. Магия and Снаряжение.
  6. Персонажи / Ещё, Кампания, login.
  7. Delete old per-platform screens, the Web fork and unused primitives.
- Tests: Python tests that assert on UI source text are retired or rewritten in the same stage
  that replaces their screen. Rules, golden/parity, persistence and FCP tests stay and must pass.
- Verification: Python contract suite and whatever Gradle targets build in the dev sandbox;
  GitHub Actions on the branch is the authority for Android. Owner reviews real builds per stage.
- Leftovers to fix along the way: clipped Кампания title, dead Магия controls, developer notes
  shown to users, duplicated gear descriptions, sync log crowding the character list.

## Left to the builder

Component APIs, file layout inside the packages above, animation details, exact spacing scale,
and the per-screen feature inventories.
