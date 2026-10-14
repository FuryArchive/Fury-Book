# Rule automation: current implementation boundary

This is functional work in progress, not a complete ruleset acceptance or a released build.

## Current additional functionality

- Independent canonical character copies preserve their original ledger, receipts and migration provenance without requiring the original device's legacy files. Original migrations still retain local source-drift protection.
- Skill effect bonuses obey their own source conditions: Ace and Talented Performer require four ranks in the affected skill; creature identification requires five; Erudition's numeric bonus requires a learned, non-secret Knowledge skill.
- Erudition permits untrained ordinary Knowledge; secret Knowledge remains excluded. A disabled effect does not grant this exception.
- Versatile Performer uses the highest learned Performance rank for other learned Performance skills without changing saved ranks, purchase prices or ownership. The optional simultaneous-performance bonus remains separate.
- Quick Search applies its one hindrance only at rank one. Tactile lock perception no longer erroneously adds a hindrance. Its selective immunity does NOT remove unrelated manually entered dice; attribution of those dice still requires explicit context.
- Passive Awareness and the natural-armor bonus from Tough Musculature are calculated from owned ranks and displayed on all three character sheets. Passive Awareness is not added to active Awareness rolls. Armor is labeled as a bonus, not total equipment armor.

Source identities and wording are retained in the existing `skill_effects_catalog.json` (review indices 21, 22, 29, 46, 49, 50, 51, 99, 213). No costs or source uncertainty are rewritten.

- Parry without the owned Parry feature now uses half of its full bonus, rounded upward. False Movement enables Dexterity for Feint only when owned.
- Explicit per-roll condition selectors apply Charge/Raid, Focused Dodge, Falcon Feathers and Team Player without pretending to know distance, prior movement or encounter state.

Targeted model tests and compilation of Android, Desktop and Web succeeded for this functional batch. Browser/device UI execution remains unverified.

- Seven owned, enabled alternative formulas are selectable per roll on Android, Desktop and Web: Motor Knight, Natural Medicine, Wild Camouflage, Logical Argument, Tyranny, First Aid and Face in the Crowd. Selection explicitly confirms the source condition, is mutually exclusive, recalculates from the replacement skill/attribute, and never changes purchased ranks or saved defaults. Four focused model tests and all three platform compilers pass; actual UI execution remains unverified.

- Source-exact prerequisites now support any one attribute, any one skill, both explicit any-Knowledge word orders, and three closed source abbreviations. Original wording remains attached to every node; ambiguous, missing, multi-skill and secret-Knowledge wording remains unresolved. All 21 importer tests pass; prices and independent oracles are unchanged.

- Added no-resource presets for lip reading (Awareness + Intelligence) and three mutually exclusive targeted-search scopes (+1/+2/+3 positive-factor dice). All three UIs apply the selected formula and label it correctly; mixed positive/negative factors remain explicitly blocked rather than silently canceled. Five alternative/preset model tests and all three compilers pass.

- Counted prerequisites now support two distinct learned skills, two Knowledge instances at the stated rank, and any two different attributes. Attribute alternatives enumerate distinct pairs; skill instances cannot count their ranks as multiple skills. Requirement prefixes retain their original source wording. The expanded importer suite passes all 22 tests.

- Trail Reading and Trail Analysis now select their source-defined Perception/Intelligence formulas. Rank two applies the existing two-skill synergy rule without modifying learned ranks, with Adjacent Specialist/Perfect Synergy contributions. Corrected the stale Trail Reading description against current corebook P10474 (Perception, not Intelligence), retaining the current occurrence references. Six alternative/preset tests plus the requirement evaluator tests and all three platform compilers pass; three source-effect contract tests pass.

## Remaining scope

The 283-row effect catalogue still contains stateful, encounter, conditional, alternative, reroll and rule-gap entries which are not fully automated. Reminder text is not implementation. Requirements with unresolved source facts remain blocked rather than guessed. Encounter/resource actions, attribution of situational penalties and remaining formula transitions need implementation before claiming full automation.

The acquisition-wide T19 report now validates and joins the pinned original source inventories (536 occurrences, 27 general questions), with 17 focused report/audit tests passing. This proves source identity integrity only, not execution. Native execution evidence joining remains unfinished, T20 local rollout characterization is reconstructed: six scoped tests pass for capability withdrawal, old-writer isolation and fresh-JVM original-root restoration; broader acceptance remains separate, and external browser/device/server release gates remain open. Existing production capability defaults have not been enabled. Targeted changed-area checks occur during implementation; a full regression is reserved for the completed functional batch. No independent-review claim is made for direct work performed while delegated execution is unavailable.

## Functional-batch regression, 2026-10-14

- Full shared JVM run: 501 of 502 tests passed. The only failure was the source-matrix test's stale final distribution (27,606 blocked / 2,008 compound); all 83,818 per-row assertions had completed. The independently passing receipt/Undo matrix observed 27,590 blocked / 2,024 compound after the exact prerequisite grammar updates. Updated only these two expectations, then reran that complete 83,818-row test successfully; the other 501 tests were not repeated.
- Receipt/Undo matrix: 16,459 receipt scenarios, including 7,530 no-op receipts for which Undo is correctly unavailable. Immutable monetary input hashes did not change.
- All 65 Android host unit tests passed, including the 83,818-case presentation binding matrix. This is not a physical-device rendering/input test.
- Desktop compilation and Web test-source compilation passed. Web browser execution remains unverified.
- Full Python tools run: 481 passed, one failed, four skipped. The failure was a stale textual API-boundary assertion after the shared owned-feature attribute API was introduced. Updated that assertion and reran all six tests in its file successfully. The full Python suite was not repeated; the four skips remain open, including missing real rulebook import artifacts.
- Final scoped Gradle run exited 0 in 1,322 seconds. No production changes were needed for these two test-expectation corrections. This closes this batch's regression, not the remaining automation or release gates.

## Next bounded formula batch

- Mediator and Service Worker expose explicit, mutually exclusive two-skill synergy presets for conflict-exit negotiations and digital-device repair. Stable Barter/Eloquence and Engineering (Repair)/Computers identities are used, not user-editable names. Neither purchased ranks nor resource state changes; forbidden untrained use stays unavailable.
- Strong Back is selectable only for Strength attribute checks or Strength-based object breaking, with the source condition stated. Hauler adds its owned rank only to explicitly selected Strength lifting checks; its existing carry-weight formula is not duplicated.
- Closed Mind and Hero of Second Chance are explicit per-roll conditions, not automatic advantage or permission to attempt a roll again. They do not infer mental attacks, previous failure, or resources.
- Android now exposes the existing generic attribute-check dialog and routes its Fortitude shortcut through the same contextual effect path. All three UIs supply the selected attribute to the shared condition resolver so a Strength bonus cannot leak into Dexterity after changing the selection.
- Changed-area verification passed: both affected model test classes (11 tests), plus Android, Desktop and Web compilation in 70 seconds. The completed aggregate regression above predates these additions. Actual platform interaction is still unverified.

## Bounded native evidence join

Added a real Desktop-file-store producer for two immutable Athletics 0→1 cases (creation and after creation), plus strict source/commit-bound consumption in the full-denominator report. Receipt, signed ledger, persisted ownership, reopened state and inverse Undo are validated from native canonical envelopes, not a claimed PASS summary. The runtime gate can be PARTIAL only; platform interaction and release gates stay open. The initial native export ran successfully; 42 affected Python native/probe/report contract tests passed. The final same-commit export/report integration is run separately after committing these sources. See [reproduction and scope](acquisition-native-probe.md).

The first exact-commit join succeeded with integrityOK true and releaseReady false: two actual persisted native scenarios joined into all 83,818 obligations, retaining 18 unverified platform stages per case. The next exporter revision expands only that same simple 0→1/no-discount/fixed-instance witness to 23 explicitly enumerated core skills at both phases (46 cases). Other transitions, custom instances, discounts and prerequisite families remain outside this probe; its expanded execution is pending until the post-commit run.
