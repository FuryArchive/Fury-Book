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

## Existing condition projections

Shared pure projections now feed saved conditions to the Android, Desktop and Web stat displays and all sheet/dedicated-skill roll entry points. Zero endurance automatically projects Weakness without persisting a new condition; explicit Weakness counts once and remains selected when endurance returns. Current source P14776–14781 supplies -2 defense/reflexes/initiative/run and two negative factors for checks using the physical attributes listed at P127. Fixed Strength/Dexterity maneuvers use their actual shared attribute; Dodge receives the Reflexes adjustment once.

Shock applies -1 only to the source-listed check families (P14791), while Deafness contributes -4 initiative (P14771). Source contributions and compulsory negative factors are displayed for physical dice as well as app rolls. This does not automate Deafness spell failure, charge prohibition, spending restrictions, condition transitions, durations, recovery, or every condition. Exhaustion's overlapping formula remains explicitly unavailable in affected checks/metrics rather than silently stacking an incomplete Weakness result. Other unsupported conditions are named in the roll notice.

All 20 changed/adjacent model tests and Android/Desktop/Web compilers passed in 71 seconds. Actual platform UI interaction is still unverified. These changes do not modify storage formats, bought ranks, XP receipts, attributes, resources or elapsed time. The native 46-case evidence remains evidence for commit 7ad72b1, not a claim that its source-bound report was rerun after these formula changes.

## Stable effect target identities

Canonical skill effects now resolve their 20 catalog target labels to 22 stable skill definitions/families, not editable display names. Renaming a bound skill no longer loses its effects, and naming a custom skill “Athletics” or “Knowledge” does not grant canonical bonuses. This also fixes Experienced Repairer missing Engineering (Repair) because its display label previously required exact equality with the family label. Knowledge does not include Medicine or Profession just because they share a display category. Existing owned-rank, disabled-effect and source-condition gates remain in force; this does not introduce a new persisted classification for secret Knowledge.

Thirteen affected effect/alternative tests and all three platform compilers passed (64 seconds); the six source-qualified automation tests passed separately (10 seconds). No monetary corpus rerun was performed for this target-binding-only change.

The current cloud workspace has Chromium installed, but a normal fresh-profile launch fails before opening a page because its local process socket is forbidden. The approved tool-level elevated retry fails identically. Chromium's security sandbox was not disabled. Actual browser interaction therefore remains unverified, not silently replaced by a compiler result.

## Additional condition formulas and UI invalidation

Exhaustion by itself now applies the source's -3 Defense/Reflexes/Initiative, half Run rounded upward, and -2 plus two negative factors for physical-attribute checks. Its combination with Weakness remains unavailable for affected values pending the user's answer; neither stacking nor replacement is assumed. Poisoning adds one negative factor to checks with an actual attribute. Shock also covers skill-based combat maneuvers. These are formula projections, not resource restrictions, recovery or duration automation.

All 15 affected/adjacent model tests and the Android, Desktop and Web compilers passed in 72 seconds. A subsequent UI-only change exposes the unresolved overlap on the sheet, uses the actual condition contributions in Android breakdowns, and invalidates displayed roll results when their character/condition/formula inputs change. All three compilers passed again in 32 seconds; the model tests were not unnecessarily repeated for the UI-only changes. Browser/device interaction remains unverified.

## Explicit retry and tactical contexts

Added six source-scoped choices using the existing shared roll-option path: Barter retry after failure (-2), immediate Lockpicking retry after critical failure (one negative factor), Archivist failed-Knowledge retry with a knowledge source (one positive factor), attacking the same target with one's pet (+1), Pack Tactics with an actual flanking bonus (one positive factor), and Fast Start during sprint (+owned rank to Run). Selection explicitly states the external condition; it does not infer prior failure, spend resources, modify a pet/target, or advance time. Unselected, unowned and disabled effects do not apply. Existing stable skill-family targeting prevents spillover to unrelated skills.

Current source corrections are retained in both the development and generated effect catalogs: Fast Start refers to sprint, not the older acceleration wording (P11783–11787); Burglar Agility includes the missing combat-hindrance sentence (P10353–10360). That selective hindrance removal and Pack Tactics' distraction penalty remain unimplemented, not silently claimed by the new choices. Previous roll history and authoritative outcomes are not automatically tracked by these selectors.

All nine scoped model tests and all three platform compilers passed in 66 seconds. Three source-generation/import contract tests passed. No storage format, XP, purchase price, or acquisition eligibility changed. Platform interaction remains unverified.

## Entangled, prone and stunned projections

Entangled applies the single -3/one-negative-factor penalty to attacks or Dexterity checks, not twice to Dexterity attacks. Its Run action is unavailable with an explicit explanation (P14774); this does not guess walking distance or sprint costs. Prone applies -2 Defense and -2 only to melee attacks, not shooting, throwing, ordinary skill checks or Parry (P14789). Stunned contributes -2 Defense (P14801); action-point loss, dropped items and duration remain outside this formula-only slice. The notice now explicitly says these conditions are only partially automated.

Twelve affected condition model tests and all three compilers passed in 44 seconds. An initial compile caught an overbroad UI insertion referencing conditions from the attribute-only panel; that insertion was removed before the successful run. No schema/resource/time transition changed. Device/browser interaction remains unverified.

## Consolidated formula checkpoint 4451b41

The changed-area model and catalog-loader regression passed all 134 tests across 22 classes. The debug Android APK assembled successfully in the same 47-second run. Its package is `com.dubl.character.android.preview`, version `0.5-WIP-4451b41`, version code 105000000. APK archive integrity and its v2 signature verified; SHA-256 is `ab5362b1dbc358098a5d1496dcb63b5c3ae522585c3194a2df4b066d596979eb` (16,011,718 bytes). This is an untested-on-device work-in-progress package, not a release acceptance claim. Installing it may update an existing Preview installation; export characters first.

A 487,836-byte incremental Git bundle from the already backed-up cfb3dd6 to 4451b41 was successfully restored on top of the base full bundle in a new clone. Full fsck and every one of the 677 tracked file hashes passed. Upload was subsequently refused pending explicit permission to publish code/history to the public FuryArchive/Fury-Book repository. No alternate upload or ref mutation was attempted after that denial. The user was asked for permission naming the repository, public visibility and separate backup branch. The latest verified remote backup remains 5e0da41 (source cfb3dd6); main remains f47a525. Self-contained archives and the APK were delivered through the private conversation.

The action/event/persistence document dated 2026-10-14 is a review proposal, not an implemented or approved state engine. Its approval and the separate Weakness/Exhaustion question remain pending. No production capabilities, schema migrations, server deployment or main-branch changes were enabled.

## Any-Performance prerequisites

The exact source wording “Исполнение (Любое) 4/5” now resolves to the two stable Performance families (Vocal and parameterized Performance), retaining source text and P10542/P10547. A renamed skill is not identified by its label; specific Dance/Comedy requirements remain unresolved without a proven specialization identity. Missing family definitions remain a source blocker. This fixes the requirement nodes for Talented Performer and Versatile Performer, not their unrelated unresolved acquisition policies.

All 23 importer tests passed. The two native requirement/catalog test classes (26 tests) and all three compilers passed in 21 seconds after correcting an off-by-one paragraph assertion in the new test. The exact 42 affected rows of the existing independent presentation/money corpus passed separately in 19 seconds: 28 confirmed (16 blocked, 12 receipt-eligible), 14 source-unverified. These classifications match their prior full-run evidence; global distribution assertions were not changed and the other 83,776 rows were not unnecessarily rerun. Both immutable monetary-file SHA-256 values remain unchanged. The already-delivered APK is still the earlier 4451b41 snapshot and does not include this later prerequisite change.

## 2026-10-14: approved action architecture, first pure transition slice

The owner approved the saved-action/resource/event design and public backup-branch publication. The separate Weakness/Exhaustion combination question remains open. Implementation sequence: `docs/superpowers/plans/2026-10-14-furybook-actions-implementation.md`.

Added a disconnected shared Overexertion transition kernel for physical attribute checks (P125/P127, Weakness prohibition P14781): immutable preview, one endurance debit on start, pinned pending rule, typed success/failure input, exact operation replay, ID collision/stale-revision rejection, and no refund for failed checks. Spending the final point makes the projected character weak; this does not cancel or silently simplify mixed roll factors. Unknown resource consequences fail closed.

Verification: real Kotlin 2.4.20 compiler, first failing harness due to absent implementation, then 32 assertions passing. Compiled all 85 non-UI shared Kotlin sources together with the harness and executed those same 32 assertions successfully. No Gradle/platform/UI acceptance claim. The compiler was restored from official Maven Central artifacts with repository SHA1 checks; the previously denied Google dependency operation was not retried.

This is NOT yet user-facing automation: no canonical format migration, durable action service, Undo, event expiry, UI binding, or production enablement in this checkpoint. The test harness is registered through `tools/tests/test_gameplay_actions.py`; that Python runner itself was not run in the restored environment.

## 2026-10-14: opt-in gameplay envelope and durable Desktop proof

Added a strict gameplay journal/codec and whole-envelope CAS service. Gameplay revision/receipts are separate from acquisition revision/XP ledger, while resources, pending action and both histories commit together. Receipt validation re-executes typed transitions; unknown fields/commands, altered costs, broken pending chains and duplicate IDs are rejected. Format 1 encoding is unchanged. Format 2 requires explicit decode opt-in, is disabled by default, and is rejected by both cloud reader and writer. No production caller opts in.

Desktop store has an explicit opt-in used only by tests. Ordinary CAS refuses format upgrades/downgrades; existing-character migration is NOT implemented. Service defaults disabled, does not migrate or use legacy fallback, checks capabilities again before CAS and reconciles uncertain commits using the same ID. Weakness forbids spending; tired/exhausted expenditure consequences remain blocked. Full physical-roll eligibility and UI integration are not complete.

Executed evidence (not inferred from source):
- 32 action + 14 journal + 29 strict codec + 16 envelope + 22 service assertions passed. Service fault harness uses simulated storage, not a disk claim.
- Real Desktop store: separate JVM phases start, finish, verify preserve pending state across process exit, debit exactly once, retain acquisition history and notes, and reject default-reader/downgrade writes without changing original bytes. Fault phases after temporary-file flush and after atomic replacement reconcile correctly. This is Linux filesystem evidence, not a power-loss or cross-OS certification.
- An actual pre-format-2 shared jar, in a separate JVM, round-trips format-1 bytes and returns format-2 bytes unchanged as Unsupported.
- 49 existing AcquisitionEnvelopeTest, AcquisitionServiceTest and AcquisitionManualBaseRevisionTest JUnit tests passed. The first direct compilation omitted InMemoryAcquisitionStore; rerunning with the existing fixture corrected that test-command omission. No product test assertions were weakened.
- All 88 non-UI shared Kotlin sources plus the three real Desktop storage classes compiled. No Compose/platform UI build, Android/Web runtime test, full monetary matrix or full regression rerun in this block.

Still open: backup-backed user migration, Undo, events/durations, UI physical/built-in dice flow and rollout acceptance. The current implementation does not turn existing characters into format 2 or claim complete automation.

## 2026-10-14: guarded local gameplay Undo

Added typed Undo with inverse receipts, not history deletion. Only the last independent local gameplay operation can be undone, never an Undo itself. A resolved check prevents undoing its start; undoing the result reopens its paid pending check. Replaying an undone operation returns its recorded outcome marked undone and never applies or charges it again. A new result requires a new operation ID.

The journal records the storage revision of its last action commit. Service Undo is blocked if any later saved operation intervened, even if resources happen to match. Exact resource, pending, weakness and consequence-policy checks also guard inversion. This deliberately conservative first policy does not infer independence from matching numeric values.

Executed: 32 action, 14 journal, 29 codec, 16 envelope, 27 service and 16 Undo assertions (134 total); six separate real Desktop JVM phases start/finish/verify/undo/verify-undo/faults passed. This includes persistence of inverse receipts and replay state after restart. Full shared non-UI plus three Desktop storage classes compiled. No UI enablement, existing-character migration or events in this checkpoint. The earlier 49 acquisition tests were not rerun just for this isolated Undo extension.

## 2026-10-14: explicit Desktop format migration with original-byte backup

Added a reviewed-request migration from canonical format 1 to opt-in local format 2. It checks character identity, exact source canonical content and storage revision, and refuses cloud history rather than resetting it. The source character, notes, acquisition ledger/receipts and revision are preserved. Original bytes are saved in an independent backup before replacing the head; proof remains in the journal across subsequent actions and Undo. Same-ID retry reconciles migration after an uncertain write; other input/IDs cannot silently remigrate.

Real Desktop tests ran in three separate JVM phases: migrate; reopen, replay, restore the original format-1 backup into a separate store, and Undo an action; then fault cases after backup, immediately before head replacement, and after head replacement. All passed. A review caught that the pre-existing replace helper forced the head container rather than a nested backup's parent; it now forces the actual destination parent. The three native phases were rerun after that correction and passed. These tests do not claim power-loss certification.

All 89 non-UI shared sources plus real Desktop storage compiled. The 134 action/journal/codec/envelope/service/Undo assertions and 49 existing acquisition JUnit tests passed against this migration model. Python test entry points are registered; direct Kotlin/JVM execution is the evidence in this environment.

No user data was migrated: all migration runs used isolated test directories. Default production format/read/write gates remain disabled. Android/Web migration adapters, UI confirmation, event durations, physical/built-in dice integration and full rollout acceptance are still open. Current format 2 is a pre-release opt-in development format, not an enabled public persistence contract.

### 2026-10-14: Android opt-in gameplay storage adapter

Implemented explicit backed-up format migration, strict format transition refusal in ordinary CAS,
owner/source fencing, exact original backup export and format-2 roster loading in the Android file adapter.
The gate remains disabled by default; no production character was migrated.

Verification: compiled the real Android adapter with Kotlin 2.4.20 against Android API classes and
injected JVM file operations. Seven host JUnit tests passed (four existing storage tests and three
new gameplay migration/fault/export tests). Separate host JVM start, reopen/Undo and final reopen
phases passed. This is not Android device/native Os validation, not a Compose or Gradle build,
and not a new APK. The browser adapter and UI integration remain separate unfinished work.
