# Old sheet feature inventory (input to stage 2b)

Union of the three old sheets that stage 2b replaces and deletes: desktop `CharacterSheetScreen.kt`
(D), web copy (W, = D except portrait via data URL and `webUuid` group ids), Android
`OverviewScreen.kt` (A). Every line must be reachable in the new sheet when 2b ends; Task 10 ticks
them off. "2a" = already covered by the 2a play view.

State APIs: desktop/web `DesktopAppState` and Android `CharacterController` delegate to
`DublApplication` with the same member names unless noted.

## Identity
- [ ] Name, concept, size, legs, mana switch — D `setIdentity(name, concept, size, legs, manaEnabled)`; A `setProfile(..., experience, ...)` also edits total XP.
- [ ] Blank name saves as «Новый персонаж» (A).
- [ ] Portrait add/change — D file → `importPortrait(Path)` → `setPortrait(uri)`; W data URL → `setPortrait`; A SAF + persistable URI → `setPortrait`.
- [ ] Portrait remove — `setPortrait(null)` (D/W; new on A).
- [ ] Portrait shown on the sheet.
- [ ] Size/legs quick edit (A, size 1..10, legs ≥ 2).

## Economy / XP
- [ ] Total, creation XP, adjustment, ability-points override (blank = auto) — D `setEconomy(total, creation, adjustment, apOverride)`; A separate setters.
- [ ] Breakdown lines incl. chi when the chi pack is on (`CharacterEconomy.breakdown(character, developmentCatalog, includeChi)`), unpriced-spells warning.
- [ ] Recommended ability points hint, «Самоучка» note (A).
- [ ] Complete / reopen creation — `completeCreation()` / `reopenCreation()`.
- [x] XP and ОС chips in the header (2a).

## Resources
- [x] ±1 on health, endurance, mana, mounted, custom (2a).
- [ ] Health control: amount, «Получить урон», «Лечение», «Восстановить всё здоровье»; health may go below zero (`test_negative_health_is_preserved…`).
- [ ] Maximum overrides for health/endurance/mana, «По формуле» resets — `set{Health,Endurance,Mana}MaximumOverride(Int?)`.
- [ ] Custom resources add / edit / remove — `addCustomResource(name, max, cur)`, `updateCustomResource(uid, name, cur, max)`, `removeCustomResource(uid)`.
- [x] Restore mounted resource (2a, «Восстановить»).
- [ ] Visibility: hide/show health/endurance/mana and mounted meters — `setResourceHidden(id, hidden)`, `resourceToggleModels()`.
- [ ] «Нет видимых ресурсов» empty state (2a final fix adds it; keep).
- [ ] Critical-HP styling (A `healthCriticalLevel`).
- [ ] Mounted meter accent/icon from pack presentation (2a shows them as CUSTOM).

## Conditions
- [x] Toggle standard conditions; automatic Слабость at 0 endurance (2a, chips).
- [ ] Condition picker with descriptions from `conditionCatalog.summary`.
- [ ] Local override title/description, reset to rulebook — `setConditionOverride`, `resetConditionOverride`.
- [ ] Custom conditions add / edit / remove / activate — `addCustomCondition`, `updateCustomCondition`, `removeCustomCondition`, `setCustomConditionActive`.

## Attributes and metrics
- [ ] Attribute −/+ — `changeAttribute(id, delta)` with undo toast.
- [ ] Stat formula breakdown for Защита, Рефлексы, Инициатива, Стойкость, Бег, Размер (A `statInfo()`: load penalty, quick reflexes, improved initiative, storm lord, stalwart, still mountain, run base/multiplier/runner).
- [x] Quick rolls: 8 attributes, 4 metric contexts, 11 combat contexts, skill rolls (2a).

## Skills panel
- [x] Trained skills grouped, bonus, roll (2a).
- [ ] Group collapse persisted — `setSkillGroups(SheetGroupingRules.toggleCollapsed(...))`.
- [ ] Bonus breakdown (D hover/pin popover: calculation + automatic effect sources).
- [ ] Group manager: long-press drag of groups and items (tree blocks), rename, delete, create, reset to default (A) — `SheetGroupingRules.*`, `setSkillGroups`.
- [x] «Все умения» link (2a).
- [ ] Hidden skills keep their group membership.

## Development panel («Взятые навыки»)
- [ ] Owned entries grouped, hierarchy (parent/child), rank labels — `DevelopmentRules(...).ownedSheetSections()`, `setDevelopmentGroups`.
- [ ] Red tile when requirements unmet (A).
- [ ] Details: benefit, notes, requirements, cost; D also edit local / reset / delete custom / open linked entry (`DevelopmentDetailsDialog`, `DevelopmentLocalEditDialog` in DevelopmentScreen.kt).
- [ ] Group manager for development groups (same as skills; tree roots = special/martial arts on A).
- [ ] «Все навыки» link.

## Notes (D/W; new on A)
- [ ] Multiple titled, collapsible notes; add / edit / delete — `addNote(title, body)`, `updateNote(id, title, body)`, `removeNote(id)`; title ≤ 120, body ≤ 12000.

## Undo
- [x] Undo toast/bar after resource and condition changes (2a).
- [ ] Undo toast after attribute, identity, name, XP, size, legs, condition-dialog changes (D Identity/Attribute/Conditions, A Name/Experience/Size/Legs).

## Code that stays (move, don't delete)
- A `DublSkillRollSheet` + `CheckRollSheet` + roll helpers (used by `SkillsScreen.kt:300`).
- A `RecentChangeBar`, `recentResourceChange`, `resourceChangeText`, `fcpAccentColor` (used by `DublApp.kt`).
- D/W `SkillRollDialog`, `FurySegmentedControl`, `FuryChoiceButton` in RollDialog.kt (Skills screen).
- D/W `DevelopmentDetailsDialog`, `DevelopmentLocalEditDialog` (Development screen).

## Deleted in 2b
- D/W `CharacterSheetScreen.kt`; A `OverviewScreen.kt` minus the moved parts.
- D/W RollDialog.kt `ContextRollDialog`, `SkillAttributeChoiceDialog`.
- D/W UiPrimitives sheet-only: `DesktopHeroSection`, `DesktopConditionChip`, `DesktopHeroAttributeCell`, `DesktopHeroMetricCell`, `DesktopSkillRow` + `FuryBreakdownPopover`, `DesktopResourceTile`, `FuryStepper` (desktop copy), `DesktopTinyButton`, `DesktopIconButton`, colour aliases `DesktopGold/Health/Stamina/Mana/CustomResource`; already dead: `DesktopResourceRow`, `DesktopStatCell`, `DesktopDenseAttributeRow`, `DesktopDenseMetricRow`.
- W `pickPortraitDataUrl`, `dataUrlBytes` only if the new portrait flow doesn't reuse them.
- A `AndroidContentPackState.resourceMeterModels` / `resourceToggleModels` if unused after 2b.
- Python tests that read these files (~20 files, ~70 functions) and the matching entries in `.github/workflows/linux-appimage.yml` and `release.yml`.
