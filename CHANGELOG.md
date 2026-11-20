# Changelog

## Unreleased

### Навыки tab removed (revamp stage 4)

- The separate Навыки tab is gone on Android, Desktop and Web; the phone bottom bar is now Лист, Магия, Вещи, Кампания.
- The editor's Навыки step does everything the tab did: «+ Свой навык», «Редактировать / Удалить свою запись», «Локальная правка» and «Сбросить к рулбуку» (delete and reset offer «Вернуть»), «Взять все оставшиеся ранги», «Добрать требования», and the character plan (☆ in a perk, «План» filter, «Взять план»). Requirement targets and «Открывает» names open that perk.
- The ЦИ tab in the editor has the ЦИ switch, extra ranks with their XP, and the techniques list (searchable, «Доступно» filter).
- Tapping the ЦИ meter on the sheet opens a ЦИ panel: ±1, amount, «Восстановить», and «Использовать» for each technique (or why it can't be used).
- A perk's «Подробнее» on the sheet is one dialog on every platform, with «Настроить в редакторе».
- «Изменения за этот заход» lists own perks, local edits (repriced when the cost changes) and ЦИ settings.

### Умения tab removed (revamp stage 3)

- The separate Умения tab is gone on Android, Desktop and Web; the phone bottom bar is now Лист, Навыки, Магия, Вещи.
- Each skill's rules and settings open from the editor's Умения step: bonus breakdown, allowed attributes, permanent modifier, special rule note, local edits with «К рулбуку», rules-automation switches, hide and delete (with «Вернуть»). Hidden skills: «Скрытые · N».
- Skill search also matches descriptions; category chips filter the list.
- Skill rolls offer the skill's allowed attributes plus «другая…» for a one-off GM call; the last allowed pick is remembered. «Об умении» in the roll panel shows the rules and opens the editor on that skill.

### Character editor («Редактор»)

- New shared step-by-step editor (Android, Desktop, Web) replaces the old sheet edit mode: Развитие (quick rank-ups and perks for an existing character), Основа, Характеристики, Умения, Навыки, Итог.
- Skill ranks and perks are bought inside the editor through the existing rules; missing prerequisites are taken as one chain with its total cost.
- Every change saves at once; «Отменить всё» returns the character to how it was when the editor opened (one undoable step).
- «Новый персонаж» opens the editor at Основа; the play sheet's «Настроить ресурсы» opens resource visibility and custom resources.
- The custom skill/perk group manager is retired; saved groups stay in the data, sync and transfer files.
- Lists in the editor no longer move under the cursor: skills keep their order for the visit, Развитие keeps its rows (taken ones show «Взят»), and an open perk stays listed after it is taken.
- The XP summary tells GM XP apart from refunds («Свободно +3500 XP», «Потрачено 545 XP · свободно +2955»); overspent XP and ability points are said in words.
- Tablets and narrow windows: the section tabs move to their own row when they don't fit beside the title; perk names on the sheet use the free room; long rank ladders show «1/20» instead of twenty pips.

### Fury Book rebrand

- Promoted **Fury Book** to the product/platform name while keeping DUBL 3.69 as the current ruleset identity.
- Renamed Android app labels, Desktop window/distribution names, Linux launcher metadata, CI artifacts, release artifacts, and GitHub Release titles to Fury Book.
- Introduced product-level `FURY_BOOK_*` build variables with legacy `DUBL_VERSION*` fallbacks for local compatibility.
- Preserved Android application IDs, `dubl / 3.69` ruleset identity, `dubl.character` transfer format, `.dubl` files, and existing desktop data identity to avoid a branding-induced data migration.
- Updated living documentation to distinguish Fury Book platform architecture from DUBL-specific implementation details.


### Development screen performance

- Retained Android development preparation across navigation instead of rebuilding the effective catalog and index on every visit.
- Made requirement availability lazy and memoized so closed groups do not trigger catalog-wide rule evaluation.
- Added shared pre-normalized development search/group metadata for Android and Desktop.
- Made regular, special, martial-art, and Chi groups collapsed by default on both platforms; search temporarily reveals matching groups without changing manual expansion state.
- Kept the Owned view immediately visible and uncollapsed.
- Memoized Desktop availability and unlock-count calculations to prevent repeated work during recomposition and scrolling.

### Repository maintenance

- Reworked the repository landing page and contributor guidance around the current FURY/KMP architecture.
- Added structured issue forms, a pull request template, a documentation index, and a living project-status document.
- Removed the stale generated file inventory and obsolete duplicate Gradle bootstrap script.
- Aligned Android and Windows CI/bootstrap paths on the repository Gradle 9.7.0 contract.
- Added SHA-256 verification to the Windows Gradle bootstrap and expanded the version-matrix regression contract.
- Corrected living architecture/persistence documentation to the current shared `DublApplication` boundary and snapshot schema 11.

## FURY 0.5 — unified product release

- Unified Android, Linux, and Windows under one FURY product version and one `v0.5` release tag.
- Added a single `FURY Release` workflow that validates the tag, builds all three platforms, and publishes one GitHub Release only after every platform job succeeds.
- Normalized public `0.5` to `0.5.0` only where native desktop packaging requires three numeric components.
- Moved Android version codes into a higher FURY range (`105000000` for 0.5) so installs can upgrade from the older Android 0.6.2 build line.
- Desktop polish pass: stabilized skill bonus breakdown hover/click behavior and replaced scattered Material alert dialogs with a shared FURY-styled modal surface for rolls, notes, resources, conditions, skills, development, magic, equipment, and character management.

## Desktop 0.2.0 — Android 0.6.2 functional parity (Compose Desktop)

- Added Windows Compose Desktop packaging from the same `desktopApp`: native `.exe` and `.msi` installers are built on `windows-latest` with the shared desktop test gate.
- Desktop CI now builds on every push to `main` on both Linux and Windows; Linux keeps the AppImage parity/smoke gate, while desktop version resolution safely falls back to `0.2.0` for ordinary branch pushes. Android CI already continues to build its development APK on every `main` push.
- Added portable cross-platform character transfer: Android and Compose Desktop can export the active character to a versioned UTF-8 `.dubl` file and import it as a fresh local copy through `DublApplication.transfer`. Transfer v1 preserves the full DUBL 3.69 character payload plus portable sheet extras (conditions, hidden resources, preferred skill attributes, groups, local condition overrides, custom conditions, and notes); platform-local portrait URIs/paths are deliberately excluded. Invalid, unsupported-version, and non-DUBL-ruleset files are rejected without mutating the roster.
- Rebuilt the first desktop presentation slice around a full-width workspace, flatter 220 dp icon navigation rail, reusable desktop panels/vector icons/dense resource-stat-metric-skill primitives, and a responsive Character Sheet dashboard: compact resources, a three-column Characteristics / Derived Stats / Skills core with Skills receiving the most space, hero condition chips, Development summary, and persisted character Notes. The narrow parity-column layout, duplicate empty conditions card, and oversized attribute/resource controls are removed while shared rules behavior remains unchanged.
- Began the semantic DUBL 3.69 audit with Character Core / Derived Stats / Skills / Rolls: fixed equipment-load penalties on attacks and Dexterity checks, aligned Fortitude/Initiative quick presets with all shared passive components, removed a stale Android Fortitude formula copy, added shared two-skill synergy/assistance rules, preserved rulebook-required negative Health, made target comparison honor universal `1–1` critical failure, and added a discrepancy ledger.
- Added a hard Shared Application Lock: `DublApplication` is now the only public state-changing boundary used by Android and Compose Desktop, split into character, skills, development/Chi, magic, equipment, sheet, and transfer capabilities.
- Internalized raw `CharacterSession` / `CharacterExtrasSession` mutation surfaces and removed platform escape hatches such as `updateActive`, generic Desktop `mutate`, direct extras writes, and arbitrary spell/gear transform lambdas from public adapters.
- Expanded typed deterministic Shared Application golden coverage to 17 end-to-end application scenarios, including successful/rejected cross-platform character transfer: character creation/economy/resources, built-in/custom/specialized skill lifecycle, custom development ownership, Chi spend/restore/Undo, Magic schools/spells/mana Undo, equipment quantity/carried/load lifecycle, conditions/grouping/preferences across restart, roster deletion/extras cleanup, per-character extras isolation, and shared semantic Undo.
- Moved recent-change Undo semantics into the shared application boundary; Android/Desktop now invoke the same `undoLast()` operation instead of implementing reverse mutations independently.
- Added hard-lock and golden contracts to the Linux parity release gate before Compose compilation/packaging. Golden scenarios execute through the project `:shared:desktopTest` Kotlin toolchain and protect Android/Desktop behavior parity; they do not declare current behavior rulebook-correct.
- Started the DUBL 3.69 rulebook-first import pipeline: deterministic multi-source DOCX Raw IR, source-qualified provenance, diagnostics/resolutions, structural validation, and reproducibility baselines.
- Registered stock DUBL 3.69, Masters of Melee, and Archmage books as distinct authority scopes instead of pretending all imported module content comes from one document.
- Added explicit ambiguity policy: source conflicts/incomplete mechanics are preserved as diagnostics and cannot become executable rules without a human resolution record.
- Added a compact tracked ruleset control plane (`config`, `resolutions`, `baseline`) while keeping full Raw IR/mirror bundles in ignored `build/` output rather than creating a second committed catalog source.
- Promoted Conditions as the first source-generated domain: 23 runtime conditions and 2 related mechanics are extracted from the core rulebook with stable provenance and runtime-title parity checks.
- Added rulebook importer/validator contracts to the Linux release gate without making the DOCX books runtime or CI dependencies.
- Added an executable Android/Desktop parity contract: shared resources are now the only physical canonical DUBL catalogs, Android repository adapters use the same shared parsers, and importer defaults write to shared resources.
- Added persisted ruleset identity (`dubl` / `3.69`) and bumped snapshots to schema 8 with automatic schema-7 migration.
- Hardened Magic parity in shared `CharacterSession`: incomplete canonical spells are rejected by backend, creation-time school edits keep mana synchronized to the effective maximum, and Compose Desktop exposes the same mana mutation/creation-lock semantics as Android.
- Hardened Equipment parity in shared rules: raw item quantities obey the same minimum-one invariant as normalized/persisted characters, while Desktop quantity/load editing and catalog search now follow canonical Android/shared semantics.
- Added a rules-boundary contract for combat roll choices, selected skill-effect aggregation, Chi/Magic XP constants, and passive derived-stat components so presentation code cannot silently become a second rules implementation.
- Corrected Character Sheet stat explanations to use the exact shared components used by Defense, Reflexes, Initiative, Fortitude, Run, and Size calculations, including load and passive-development bonuses.
- Promoted `desktopApp` Compose Desktop from scaffold to the primary desktop frontend.
- Wired Compose to persistent `DesktopCharacterStore` / `DesktopCharacterExtrasStore`, shared `DublApplication`, and canonical catalogs.
- Added complete Compose workflows for Character Sheet, Skills/Rolls, Development/Special Branches/Martial Arts/Chi, Magic, Equipment, and Characters.
- Preserved Android mutation semantics through shared `CharacterSession` and schema-8-compatible persistence instead of duplicating DUBL formulas in desktop UI code.
- Added Android-parity sheet behavior: XP economy, conditions, automatic Weakness, custom resources, portrait storage/rendering, quick checks, formula/details, recent-change Undo, learned summaries, and persistent grouping/tree movement.
- Added rule-aware rolls, custom/specialized skills, development requirements/economy, Chi techniques, magic schools/spellbook/custom spells, and equipment load/capacity/burden.
- Added confirmations for destructive desktop actions and validation feedback for invalid/duplicate custom skills.
- Switched the Linux release workflow to the Compose packager and added required `:shared:desktopTest` and `:desktopApp:compileKotlin` CI gates.
- Hardened the Compose Linux release with clean-HOME Xvfb AppImage startup smoke testing, pinned appimagetool/Gradle checksums, aligned Compose 1.12 Material3 dependencies, and a JDK 17 CI toolchain.
- Synchronized the build matrix to Kotlin 2.4.20, Compose Multiplatform 1.12.0, AGP 9.3.0, Gradle 9.7.0, and compileSdk 37 while keeping Android targetSdk 36.
- Kept the old portable/Swing frontend only as a temporary restricted-environment oracle/fallback; it is no longer the canonical release target.
- Note: the restricted sandbox used for this migration cannot fetch Gradle/Maven dependencies, so final Compose AppImage binary verification must run in normal CI/local Linux before publishing.

## Desktop 0.1.1 — interactive character sheet

- Replaced the read-only desktop Character Sheet preview with a real editing workflow backed by the shared `DublCharacter` model.
- Added platform-independent `CharacterSheetSession` behavior for identity, experience, attributes, resources, and maximum overrides while keeping all clamping and derived formulas in `DublCharacter.normalized()`.
- Added desktop editing for name, concept, total XP, size, legs, optional mana, primary attributes, Health, Endurance, Mana, and manual resource maximums.
- Derived Defense, Reflexes, Initiative, Fortitude, Run, and Ability Points now refresh immediately from shared rules after edits.
- Updated the portable Linux click-to-run build to expose the same sheet editing semantics as the Compose Desktop source.
- Hardened portable release smoke tests to use a fresh HOME so an older extracted payload can never produce a false-green result.
- Native desktop file persistence remains deliberately deferred; this increment keeps the existing `CharacterStore` boundary and in-memory desktop store.

## KMP foundation — in progress

- Began migrating the canonical Android 0.6.2 model/rules layer into Kotlin Multiplatform `shared` without changing game formulas.
- Added a shared persistence contract while preserving Android SharedPreferences schema 7.
- Moved the canonical DUBL theme and reusable card/stat primitives into shared Compose code.
- Added a responsive Compose Desktop foundation with compact, normal, and wide layouts and no app-level horizontal scrolling.
- Added boundary and source-level regression checks for shared platform independence, theme ownership, and desktop layout policy.
- Kept Web, server/sync, native desktop persistence, and full desktop feature parity out of this foundation phase.

## 0.6.2 — Hierarchical groups, drag ordering, and explicit skill rolls

- Restored dependency-tree presentation for owned special developments and martial arts inside user-defined character-sheet groups.
- Dragging a tree root now moves its complete owned subtree; individual children can still be extracted, reordered, and reattached independently.
- Added exact drag-and-drop ordering for both items and groups and removed the legacy “group above/below” controls.
- Replaced paired two-column rows with independently balanced columns so taller two-line cards no longer leave artificial gaps in the opposite column; linked trees stay in one column.
- Changed character-sheet skill rolls to always open an attribute chooser first, defaulting to the rulebook/stock attribute and recalculating the displayed bonus for any selected attribute.
- Removed eager Martial Arts availability calculation on tab entry; availability is evaluated lazily for visible rows, with full evaluation only for the explicit “available now” filter.
- Returned bottom-sheet gesture ownership to Material3 and disabled partial expansion for key sheets to reduce jerking and make swipe-to-dismiss consistent.
- Added regression coverage for tree moves, sibling ordering, group ordering, roll-attribute overrides, masonry-style columns, and sheet interaction policy.
- Bumped the default Android version to `0.6.2`.

## 0.6.1 — Character-sheet performance and mobile interaction polish

- Reworked learned skills and acquired developments on the Character sheet into true lazy list items so long sheets no longer compose every card inside one parent item while scrolling.
- Switched acquired developments on the Character sheet to the same compact two-column layout as learned skills to reduce vertical scrolling.
- Precomputed Martial Arts availability for the visible catalog state and memoized row availability to avoid repeated prerequisite work while scrolling the large martial catalog.
- Fixed modal bottom-sheet nested scrolling so a downward drag at the top of sheet content is handed back to the sheet and can dismiss it from the content area, not only from the drag handle.
- Added app- and sheet-level focus clearing so tapping away from text input dismisses the mobile keyboard; group name fields also expose an explicit Done IME action.
- Rebuilt the group editor with larger labeled controls, visible item cards, an explicit drag handle, highlighted drop targets, and real long-press drag-and-drop between groups.
- Added regression coverage for sheet gesture policy, two-column layout, cross-group movement, keyboard-dismiss wiring, and Martial Arts scroll caching.
- Bumped the default Android version to `0.6.1`.

## 0.6 — Gameplay sheet groups and content repair

- Replaced Favorite Checks on the Character sheet with every learned skill (rank 1+) in compact two-column cards.
- Added persistent per-character custom groups for learned skills and acquired developments, including collapse state, manual group ordering, and long-press drag movement between adjacent groups.
- Reduced Character-sheet scrolling by collapsing combat quick checks by default and using denser semantic-color sections.
- Kept long skill names readable with two-line compact cards and preserved tap-to-roll behavior.
- Re-audited the 3.69 rulebook development catalogue: restored swallowed entries, repaired neighboring descriptions/requirements, and retained Improvised Tools and Workshop on the Knee in the Master Craftsman branch.
- Corrected Blocking prerequisites to Agility 3 OR Speed 3, plus Cold Weapons 3 OR Unarmed Combat 3.
- Extended the Archmage importer to include five complete Heading 5 Battle Magic additions, including Raven King Blade, while continuing to filter unsupported schools.
- Added offline regression tests for grouping persistence, Archmage Heading 5 import, catalog integrity, and the reported content bugs.
- Bumped the default Android version to `0.6`.

## 0.5.1 — Android compile hotfix

- Fixed missing model extension imports for Chi access and rule-aware roll presets in Android UI/controller code.
- Fixed nullable roll bonus formatting in the shared roll sheet so Debug and Preview Kotlin compilation can proceed.
- Kept the 0.5 rule/effect catalogs and gameplay behavior unchanged.
- Bumped the default Android version to `0.5.1`.

## 0.5 — Rule-aware rolls, Chi techniques, and sheet integration

- Added a reusable roll-context layer for attributes, Reflexes, Initiative, Dodge, raw attacks, Parry, Feint, Grapple, Disarm, Trip, Push, Knockdown, and Break Item checks without introducing combat-state tracking.
- Added optional DC/opponent-result comparison to the shared roll sheet and preserved the existing advantage, hindrance, doubles, and critical follow-up rules.
- Added a data-driven 283-entry skill-effect catalog based on the approved 0.5 review, with automatic bonuses, named situational toggles, advantage/hindrance options, and linked-rule reminders.
- Wired passive development effects into real character math, including Incredible Health, Stalwart, Enduring, Quick Reflexes, Improved Initiative, Runner, Hauler, Self-Taught, and selected unambiguous Chi-school passives.
- Scoped contextual bonuses correctly: Fencer applies to Parry rather than ordinary melee attacks, and Feinter applies only to Feint.
- Expanded Chi from a resource-only feature into a rules domain with 27 purchasable Chi developments, 9 schools, and 68 techniques imported deterministically from Masters of Melee.
- Chi techniques now show requirements, availability, action/cost text, and can spend Chi directly when usable; Internal Chi automatically activates the resource and Chi progression can raise its maximum.
- Added acquired Martial Arts and Chi development as dedicated Character-sheet groups using the same compact interaction pattern as special development.
- Added deterministic 0.5 import tests and pure-model coverage for derived effects, Chi access, roll contexts, target comparison, and skill-effect resolution.
- Kept ambiguous Block calculation and stateful combat effects out of automatic resolution until their rule/state dependencies are explicit.
- Bumped the default Android version to `0.5`.

## 0.4 — Martial arts, Chi, and Archmage expansion

- Added Martial Arts as a dedicated Development tab and XP-only progression domain, separate from ordinary and special development.
- Imported 19 approved unarmed/weapon martial styles and 103 unique techniques from the Master of Melee rulebook, including table-based stances and Gun Kata techniques.
- Added martial-style prerequisite handling for named alternatives and “any martial art” requirements without consuming Ability Points.
- Added persistent Chi as an optional character resource with a base pool of `max(3, Will + 1)` and up to 10 bonus ranks at 50 XP each.
- Added a dedicated Chi tab with current/max controls, bonus-rank purchasing, full restore, and short/long-rest rule reminders.
- Added enabled Chi to the main Character resource strip for fast in-play spending and recovery.
- Added Chi purchases to the shared character XP economy and bumped character persistence to schema 7.
- Imported 54 additional Archmage spells only for already-supported schools: Warding, Divination, Prayer, and Mind.
- Kept Illusion, Elementalistics, Mana Well, Wild Magic, and other experimental Archmage systems out of this release.
- Added deterministic 0.4 content import tooling for repeatable rulebook-to-catalog updates without duplicate entries.
- Bumped the default Android version to `0.4`.

## 0.3.2.1 — Bottom-sheet gesture and toggle visibility hotfix

- Restored normal drag gestures for modal bottom sheets.
- Contained scroll overshoot inside long sheet content so reaching the end of a list no longer hands uncontrolled drag momentum to the parent sheet.
- Added a shared high-contrast switch style with a clearly visible light thumb in the off state and readable disabled states.
- Added release-tag/versionCode support for four-part hotfix versions such as `v0.3.2.1`.
- Bumped the default Android version to `0.3.2.1`.

## 0.3.2 — Compact development sheet and interaction polish

- Grouped acquired development on the Character sheet into ordinary and special sections.
- Ordered acquired development by prerequisites, placing dependent entries under their first owned prerequisite without duplicating them.
- Reduced Character-sheet development rows to name and rank only; tapping opens full details.
- Made the full spell catalog row tappable for learning instead of requiring the small add button.
- Made the full equipment catalog row tappable; tapping an owned catalog item adds one more.
- Added a toggle to hide or reveal unlearned magic schools.
- Disabled bottom-sheet drag gestures so scrolling long sheet content cannot pull or jitter the whole sheet at scroll boundaries.
- Bumped the default Android version to `0.3.2`.

## 0.3.1 — Custom resources and school magic

- Added manual maximum overrides for Health, Endurance, and Mana with reset-to-formula behavior.
- Added persistent custom resources with editable name, current value, and maximum.
- Reworked Magic Power into separate canonical school values at 25 XP per level.
- Added the rulebook school catalog, canonical aliases, consistent school ordering, and school filters for spells.
- Spell usability now checks mana cost against the matching school power; spells can still be learned when currently unusable and are clearly marked.
- Added school Magic Power XP to the shared character XP economy.
- Preserved legacy global Magic Power only as a migration fallback until school power is configured.
- Compressed favorite skill rows on the Character sheet to name, rank, and total bonus.
- Added acquired Development/Feat entries with descriptions below favorite skills on the Character sheet.
- Bumped the default Android version to `0.3.1`.

## 0.2 — Integration, stabilization, and polish

- Integrated Skills, Development, Magic, and Equipment with shared character persistence and connected them to the Character sheet.
- Added the full mobile development workflow with prerequisites, branches, abilities, and ability-point budgeting.
- Added native magic, schools, spellbook management, mana formulas, spell-learning XP, and the local spell catalog.
- Added native equipment, quantities, carried state, custom items, automatic/manual load, and burden penalties.
- Corrected catalog equipment load to use item weight instead of the item requirement field.
- Prevented duplicate catalog equipment rows by increasing the existing item quantity.
- Added confirmation before deleting spells, magic schools, and equipment.
- Added actionable empty states and reset actions for filtered Skills/Development/Magic/Equipment screens.
- Improved long-name and large-font handling across the new screens.
- Increased bottom-navigation icon size.
- Extracted the dice engine into pure model code and added deterministic tests for doubles, critical failures, critical successes, superiority dice, and hindrance follow-ups.
- Added normalization and magic/equipment rule tests for 0.2 data.
- Added `v0.2` release-tag support and set the default app version to `0.2`.

## 0.1.4 — Skills

- Replaced the skills placeholder with a complete native mobile skills screen.
- Ported the 27-entry desktop DUBL base skill catalog and rank XP table.
- Added search, category filtering, trained-only filtering, and skill summary statistics.
- Added rank editing from 0 to 10 and a calculation breakdown for every skill.
- Added multiple selected attributes per skill.
- Added untrained, untrained −2, and unavailable-without-training handling.
- Added per-skill modifiers and calculation notes.
- Added hide/restore behavior that preserves skill data.
- Added Knowledge, Performance, Profession, and Craft specializations.
- Added custom user-defined skills.
- Persisted skill state in the local character snapshot with backward-compatible 0.1.3 loading.
- Added pure-model tests for multi-attribute totals, untrained rules, and XP costs.
- Removed the day-to-day `dubl-android` Git helper from the project workflow; normal Git commands are now documented.

## 0.1.3 — Native rebuild

- Replaced the WebView prototype with a real Kotlin + Jetpack Compose application.
- Raised compile/target SDK to Android API 36.
- Switched to AGP 9.4 and Gradle 9.6.
- Added native local character persistence.
- Added multiple-character creation, selection, and deletion.
- Added all eight primary DUBL attributes.
- Added character size and size-based Strength/Speed adjustment.
- Added DUBL-derived Defense, Health, Reflexes, Initiative, Fortitude, Run, and Ability Points.
- Added Health, Endurance, and optional Mana controls.
- Added responsive phone/tablet stat and attribute grids.
- Added unit tests for core derived-stat formulas.
- No Android permissions are requested.
- Added standalone GitHub Actions CI for automatic development APK builds.
- Added tag-driven signed GitHub Releases for Android only.
- Added separate `.dev` application ID for CI/test builds.
- Added one-command GitHub release-signing setup and a small `dubl-android` workflow helper.
- Pin Compose BOM to 2026.06.00 to keep compileSdk 36 compatibility in CI.
