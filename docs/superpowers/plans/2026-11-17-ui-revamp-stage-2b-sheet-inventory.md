# Old sheet feature inventory (input to stage 2b)

Union of the three old sheets that stage 2b replaces and deletes: desktop `CharacterSheetScreen.kt`
(D), web copy (W, = D except portrait via data URL and `webUuid` group ids), Android
`OverviewScreen.kt` (A). Every line must be reachable in the new sheet when 2b ends; Task 10 ticks
them off. "2a" = already covered by the 2a play view.

State APIs: desktop/web `DesktopAppState` and Android `CharacterController` delegate to
`DublApplication` with the same member names unless noted.

## Identity
- [x] Name, concept, size, legs, mana switch — D `setIdentity(name, concept, size, legs, manaEnabled)`; A `setProfile(..., experience, ...)` also edits total XP. → SheetIdentityDialog.kt; total XP in SheetEconomyDialog.kt
- [x] Blank name saves as «Новый персонаж» (A). → application `setIdentity` rule, pinned by FakeSheetEditHostTest
- [x] Portrait add/change — D file → `importPortrait(Path)` → `setPortrait(uri)`; W data URL → `setPortrait`; A SAF + persistable URI → `setPortrait`. → DesktopPortraits.kt / WebPortraits.kt + `pickPortraitDataUrl` / AndroidPortraits.kt + OpenDocument in DublApp.kt; shown by SheetPortrait.kt
- [x] Portrait remove — `setPortrait(null)` (D/W; new on A). → SheetScreen.kt (`host.setPortrait(null)` in edit mode)
- [x] Portrait shown on the sheet. → SheetPortrait.kt in the header
- [x] Size/legs quick edit (A, size 1..10, legs ≥ 2). → SheetStatDialog.kt (Размер popup steppers)

## Economy / XP
- [x] Total, creation XP, adjustment, ability-points override (blank = auto) — D `setEconomy(total, creation, adjustment, apOverride)`; A separate setters. → SheetEconomyDialog.kt + `saveSheetEconomy`
- [x] Breakdown lines incl. chi when the chi pack is on (`CharacterEconomy.breakdown(character, developmentCatalog, includeChi)`), unpriced-spells warning. → SheetEditModels.kt `sheetEconomyView` (chi via FCP mount)
- [x] Recommended ability points hint, «Самоучка» note (A). → SheetEconomyDialog.kt
- [x] Complete / reopen creation — `completeCreation()` / `reopenCreation()`. → SheetEconomyDialog.kt
- [x] XP and ОС chips in the header (2a).

## Resources
- [x] ±1 on health, endurance, mana, mounted, custom (2a).
- [x] Health control: amount, «Получить урон», «Лечение», «Восстановить всё здоровье»; health may go below zero (`test_negative_health_is_preserved…`). → SheetVitalDialog.kt (`vitalAmountDelta`, damage unclamped)
- [x] Maximum overrides for health/endurance/mana, «По формуле» resets — `set{Health,Endurance,Mana}MaximumOverride(Int?)`. → SheetVitalDialog.kt `MaximumOverrideField`
- [x] Custom resources add / edit / remove — `addCustomResource(name, max, cur)`, `updateCustomResource(uid, name, cur, max)`, `removeCustomResource(uid)`. → SheetResourcesDialog.kt + SheetVitalDialog.kt (`SheetCustomResourceForm`)
- [x] Restore mounted resource (2a, «Восстановить»).
- [x] Visibility: hide/show health/endurance/mana and mounted meters — `setResourceHidden(id, hidden)`, `resourceToggleModels()`. → SheetResourcesDialog.kt `sheetResourceVisibility`
- [x] «Нет видимых ресурсов» empty state (2a final fix adds it; keep). → SheetVitals.kt
- [x] Critical-HP styling (A `healthCriticalLevel`). → SheetVitals.kt `vitalValueColor` + `healthCriticalLevel`
- [x] Mounted meter accent/icon from pack presentation (2a shows them as CUSTOM). → SheetVitals.kt `sheetFcpAccent` / `sheetFcpIconGlyph`

## Conditions
- [x] Toggle standard conditions; automatic Слабость at 0 endurance (2a, chips).
- [x] Condition picker with descriptions from `conditionCatalog.summary`. → SheetConditionsDialog.kt
- [x] Local override title/description, reset to rulebook — `setConditionOverride`, `resetConditionOverride`. → SheetConditionsDialog.kt `StandardOverrideEditor`
- [x] Custom conditions add / edit / remove / activate — `addCustomCondition`, `updateCustomCondition`, `removeCustomCondition`, `setCustomConditionActive`. → SheetConditionsDialog.kt `CustomConditionEditor`

## Attributes and metrics
- [x] Attribute −/+ — `changeAttribute(id, delta)` with undo toast. → SheetStats.kt `onAdjust` (edit mode) + SheetChange.Attribute toast
- [x] Stat formula breakdown for Защита, Рефлексы, Инициатива, Стойкость, Бег, Размер (A `statInfo()`: load penalty, quick reflexes, improved initiative, storm lord, stalwart, still mountain, run base/multiplier/runner). → SheetStatBreakdown.kt + SheetStatDialog.kt
- [x] Quick rolls: 8 attributes, 4 metric contexts, 11 combat contexts, skill rolls (2a).

## Skills panel
- [x] Trained skills grouped, bonus, roll (2a).
- [x] Group collapse persisted — `setSkillGroups(SheetGroupingRules.toggleCollapsed(...))`. → SheetSkills.kt / SheetDevelopment.kt `onToggleCollapsed`
- [x] Bonus breakdown (D hover/pin popover: calculation + automatic effect sources). → roll panel: SheetRollSession.kt `sheetRollFormulaLine` + «Автоматически» list (ruling)
- [x] Group manager: long-press drag of groups and items (tree blocks), rename, delete, create, reset to default (A) — `SheetGroupingRules.*`, `setSkillGroups`. → SheetGroupManager.kt + SheetGroupDrag.kt
- [x] «Все умения» link (2a).
- [x] Hidden skills keep their group membership. → SheetModels.kt `normalizedSkillGroups` (includeHidden = true)

## Development panel («Взятые навыки»)
- [x] Owned entries grouped, hierarchy (parent/child), rank labels — `DevelopmentRules(...).ownedSheetSections()`, `setDevelopmentGroups`. → SheetEditModels.kt `sheetDevelopmentGroups` + SheetDevelopment.kt
- [x] Red tile when requirements unmet (A). → SheetDevelopment.kt («!» + crimson)
- [x] Details: benefit, notes, requirements, cost; D also edit local / reset / delete custom / open linked entry (`DevelopmentDetailsDialog`, `DevelopmentLocalEditDialog` in DevelopmentScreen.kt). → desktop/web SheetDevelopmentDetails.kt (full); Android SheetDevelopmentDetailsDialog (read-only, as before)
- [x] Group manager for development groups (same as skills; tree roots = special/martial arts on A). → SheetGroupManager.kt (SheetGroupKind.DEVELOPMENT, tree roots)
- [x] «Все навыки» link. → SheetDevelopment.kt → `onOpenDevelopment`

## Notes (D/W; new on A)
- [x] Multiple titled, collapsible notes; add / edit / delete — `addNote(title, body)`, `updateNote(id, title, body)`, `removeNote(id)`; title ≤ 120, body ≤ 12000. → SheetNotes.kt

## Undo
- [x] Undo toast/bar after resource and condition changes (2a).
- [x] Undo toast after attribute, identity, name, XP, size, legs, condition-dialog changes (D Identity/Attribute/Conditions, A Name/Experience/Size/Legs). → SheetChangeReportingHost.kt (Attribute, Identity, Experience; name/size/legs go through setIdentity)

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

## Sign-off (stage 2b, Task 10)

Every line above is ticked with the file that covers it. Remaining differences, all by ruling: the desktop
skill-bonus hover popover is replaced by the roll panel's formula line; Android development details stay
read-only (the edit/reset/delete flow is desktop/web, as before 2b); the economy dialog writes in one step
except a total-only change, which stays undoable.
