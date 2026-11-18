# Умения into the editor — design (revamp stage 3)

Status: draft for owner approval · 2026-11-18
Builds on: `2026-11-17-ui-revamp-design.md` (stage list, anti-goals), `2026-11-18-character-editor-design.md` (editor),
`2026-11-18-play-sheet-redesign-design.md` (play sheet, roll panel).

## Why

Stage 3 was "rebuild the Умения tab". Since the editor landed, most of that tab has another home: the
sheet rolls skills, the editor's Умения step buys ranks and adds skills. The owner's call: **the tab
goes, and what only it could do moves into the editor.** That keeps the rule the editor set up: the
sheet is for playing, the editor is for changing the character.

## Goals

- Remove the Умения tab on Android, desktop and web.
- Everything the tab could do is still reachable (table below). Nothing is dropped.
- Per-skill settings live in the editor's Умения step, in a detail panel opened by clicking a skill,
  the same pattern as perks on Навыки.
- During play, the roll panel can show a skill's rules and bonus breakdown without opening the editor.

## Non-goals

- Rules, formulas, saved data, transfer format, sync: untouched. All mutations already exist in
  `SkillsApplication`; this stage only exposes them through the editor host.
- The Навыки tab (stage 4). It probably faces the same question; not decided here.
- Rolling inside the editor. Rolling stays on the sheet.

## Anti-goals

- Any skill function becoming unreachable (the old tab's feature list is the checklist).
- A second implementation of skill maths in UI code: bonus and breakdown come from
  `skillCalculation` / `skillCalculationOptions` / `formulaText`.
- Settings that save in one place and not in «Отменить всё»: every change made in the editor must be
  covered by the visit's restore point and listed in «Изменения за этот заход».

## Approaches (owner picked 1)

1. **Into the editor** — detail panel in the Умения step with all settings, hidden list next to the
   search; read-only «Об умении» from the roll panel. *Picked:* matches play-vs-change split.
2. Into the sheet's roll panel — settings where you roll; rejected: crowds the play view with setup.
3. Keep a slim tab for reference and settings — rejected: an extra screen for rarely used things.

## Where each tab function goes

| Old Умения tab (Android + desktop/web) | New home |
|---|---|
| List grouped by category, search by name **and description** | Editor Умения step: search now also matches descriptions |
| Category filter | Editor Умения step: category chips under the search («Все» + `SkillCategory` entries) |
| «Только изученные» | Existing «Только с рангами» |
| «Изучено N · X XP вложено» | Existing rail line «N рангов · X XP» |
| Roll a skill (and pick attribute) | Sheet roll (exists) |
| Remembered attribute per skill (`preferredSkillAttributes`) | Sheet roll panel starts on the saved attribute when the skill allows it; picking another saves it |
| Rank ± with cost | Existing Умения step row stepper |
| Add specialization / custom skill | Existing «+ Своё / специализация» |
| Bonus breakdown per attribute, unavailable reason | Detail panel; also «Об умении» from the roll panel |
| Description, origin («Своё» / «Специализация») | Detail panel header |
| Allowed attributes | Detail panel: attribute chips (at least one stays on) |
| Permanent modifier | Detail panel: stepper |
| Special rule / conditional bonus note (`formulaNote`) | Detail panel: text field |
| Local edits: name, description, category, «без обучения», Auto 6, Auto 12 | Detail panel, «Локальные правки» section |
| «К рулбуку» / «Сбросить к рулбуку» | Detail panel button (built-in skills) |
| Rules automation switches (`SkillEffectRules.configuredForSkill`) | Detail panel section, shown when the skill has any |
| Hide skill | Detail panel button |
| Hidden list, «Вернуть», «Вернуть все» | «Скрытые · N» button next to the search → dialog |
| Delete custom/specialized skill | Detail panel button (dynamic skills) |
| Empty state «Сбросить фильтры» / «Открыть скрытые» | Умения step empty state offers both |

## Design

### Editor — Умения step

- Rows stay as they are (name, pips, cost, ±, bonus). **Clicking the name area opens the detail
  panel** for that skill; ± keep working without opening it.
- Toolbar: search · «Только с рангами» · «+ Своё / специализация» · **«Скрытые · N»** (shown when N > 0).
  Category chips sit on the line below.
- Panel placement copies Навыки: beside the groups when the step is wide enough for both
  (same `editorPerksSplit` rule), otherwise over the list with «← К списку». With the panel beside,
  the groups drop to one column.
- Row order stays fixed for the visit (already true); the open skill stays listed even if a filter
  would hide it (same rule as the open perk).

### Detail panel (top to bottom)

```
Скрытность                         [своё]
ЛОВ · ранг 3 · +7
Описание правила…
БОНУС ПРОВЕРКИ
  Ловкость      ЛОВ 4 + ранг 3 = +7
  Восприятие    —  (нет без обучения)
ДОПУСТИМЫЕ ХАРАКТЕРИСТИКИ  [СИЛ][ЛОВ✓][ТЕЛ]…
ПОСТОЯННАЯ ПОПРАВКА  [−] +0 [+]
ОСОБОЕ ПРАВИЛО / УСЛОВНЫЙ БОНУС  [________]
▸ Локальные правки (свернуто)
    Название · Описание · Категория chips · Без обучения chips · Auto 6 · Auto 12 · [К рулбуку]
▸ Автоматизация правил (если есть)
    ☑ Источник — текст эффекта
[Скрыть из списка]                 [Удалить]   (Удалить только для своих/специализаций)
```

- **Saving:** live, like the rest of the editor. Chips, steppers and switches save on tap; text
  fields save after a short pause and when the panel closes, with the same safeguards as the
  name field (never written onto another character, never re-applied after «Отменить всё»/«Вернуть»).
- «Локальные правки» starts collapsed: rarely used, and long.
- **Hide / Delete / К рулбуку** act at once, then show a toast with «Вернуть». `SkillsApplication`
  records no undo step for these, so the editor takes `host.restorePoint()` just before and «Вернуть»
  calls `host.restore(point)` (no application-layer change). Hiding or deleting closes the panel.
- «К рулбуку» is shown for built-in skills only: for a custom skill the "overrides" are its real
  definition and resetting would wipe it.
- Allowed-attribute chips keep the first (primary) attribute stable when others are toggled; only
  removing it makes the next one primary, which can move a custom skill to another group. The panel
  stays open on it.

### Changes list

`editorChanges` gains lines so «Отменить всё» never looks empty when it would revert something:
`<Умение>: настройки изменены` (attributes, modifier, rule note, local edits differ),
`Скрыто: <Умение>` / `Возвращено: <Умение>`, `− <Умение>` for a deleted one (exists for removed skills).
Effect switches: `Автоматизация: <источник> выкл./вкл.`
Note: the first setting change on an unranked built-in skill creates its saved entry; that must read as
«настройки изменены», never as a spurious `+ <Умение>`.

### Sheet roll panel — «Об умении»

- For a skill roll, a small «Об умении» link in the roll panel opens a read-only dialog: description,
  bonus breakdown per attribute, the rule note, «без обучения», Auto 6/12.
- The dialog has one action, «Настроить в редакторе», which opens the editor straight at Умения with
  that skill's panel open.
- **Attribute choice (owner ruling):** the roll offers the skill's **allowed attributes** as chips,
  plus a «другая…» chip that reveals the other attributes for a one-off GM call. It starts on
  `preferredSkillAttributes[skill]` when that is allowed, otherwise on the skill's stock attribute.
  Picking an allowed attribute saves it as the preference; a one-off «другая…» pick is not saved.
  This replaces today's "all eight" list in `resolveSkillRoll`; its tests change accordingly.
- The «Об умении» breakdown lists the allowed attributes only (as the editor panel does).

### Navigation

- Remove the Умения section from desktop/web (`DesktopSection.SKILLS`) and Android
  (`AppSection.SKILLS`). Phone bottom bar becomes four tabs: Лист, Навыки, Магия, Вещи (builder's
  call: bigger targets rather than pulling Кампания in; owner may override).
- Delete the old screens: desktop/web `SkillsScreen.kt`, Android `SkillsScreen.kt`, and roll dialogs
  used only by them (`SkillRollDialog` desktop/web, `DublSkillRollSheet` Android) if nothing else uses them.

### Host

`DublEditorHost` gains the skill setting calls the tab used (all exist on the platform states and
`CharacterController`): `setSkillAttributes`, `setSkillModifier`, `setSkillFormulaNote`,
`setSkillNameOverride`, `setSkillDescriptionOverride`, `setSkillCategoryOverride`,
`setSkillUntrainedOverride`, `setSkillAutoOverrides`, `resetSkillDefinitionOverrides`, `hideSkill`,
`restoreSkill`, `restoreAllSkills`, `setSkillEffectEnabled`, `deleteDynamicSkill`,
`setPreferredSkillAttribute`. Desktop/web/Android hosts and the test fake implement them.

## Constraints

- Shared code in `shared/…/dubl/ui/editor` and `…/ui/sheet`; platforms keep only wiring.
- Live save + «Отменить всё» covers everything (restore point already includes skills and extras;
  preferred attribute lives in extras).
- Works at 360dp (panel over the list), tablets and desktop; all five themes.
- Python guards that pin the old screens (`test_skill_local_override_ui`,
  `test_skill_effect_disable_escape_hatch`, desktop skills browser/parity guards) are repointed at the
  editor files, not deleted, where they guard a function; pure old-layout guards are retired. Baseline
  (37 failed + 6 errors) may only shrink.

## Decisions from the builder check

- Owner: roll attributes = allowed ones + «другая…» (above).
- Builder's calls: undo for hide/delete/reset via restore point; «К рулбуку» built-in only; primary
  attribute stays stable; no spurious "+" line; which old guards pin a function (repoint) vs layout
  (retire) is judged per test, `test_shared_application_lock` included.

## Left to the builder

Exact panel spacing and section styling, whether rows get a small mark for skills with a rule or
modifier, test file layout, and how the read-only dialog is laid out on phone vs wide.
