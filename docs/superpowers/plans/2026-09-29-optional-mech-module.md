# Optional Mech Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a disabled-by-default DUBL mech module to Fury Book with a typed catalog, shared mech rules, a separate saved roster, and matching Android/Desktop flows.

**Architecture:** Package mech data in a bundled optional FCP, with DUBL-specific parsing and mechanics in `shared/commonMain`. Persist mech cards through a separate roster store and keep platform code to pack activation, storage adapters, navigation, and Compose screens; both platforms call the same rule and application APIs.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Android Jetpack Compose, Fury Content Packs, the existing `com.furybook.core.json` parser, Python FCP builder, Kotlin common tests, pytest.

**Spec:** [`docs/superpowers/specs/2026-09-29-optional-mech-module-design.md`](../specs/2026-09-29-optional-mech-module-design.md)

## Global Constraints

- The bundled FCP ID is `dubl-mechs-3.69`; it is disabled by default and depends on `dubl-3.69`.
- Mech records live separately from DUBL Character Snapshot and character transfer/export.
- When the module is disabled, its navigation, catalog, cards, and mechanics are absent from the active interface.
- Android and Desktop share the same rules, catalog models, and persistence contract.
- User-created builds that violate mechbook assembly limits are not saved.
- A DUBL pilot and an installed AI system can coexist; control can be passed between them as defined by the mechbook.
- Typed content comes from the mechbook; mechanics use DUBL rules where the mechbook refers to DUBL.
- Any conflict or unclear rule between the mechbook and DUBL pauses implementation until the user answers.

## Review Focus

- **Disabled pack with a previously selected mech route:** the route and controls must close, while stored mech cards remain untouched. Pin in Task 4 activation tests and Task 5 Android/Task 6 Desktop navigation checks.
- **Unknown component IDs or unsupported FCP payload:** loading/activation must fail with a useful error and must not partially activate the module. Pin in Task 1 loader tests and Task 4 activation tests.
- **Missing/deleted pilot with an installed AI system:** the card must load and can be controlled by its AI profile; a mech with neither operator must remain editable but offer no operator-dependent checks. Pin in Task 2 check tests and Task 3 persistence tests.
- **A malformed saved card beside valid cards:** retain all valid cards, return a warning for the malformed one, and do not overwrite the original storage until the user saves. Pin in Task 3 codec/store tests.
- **Pilot with lower stats, direct neural link, or AI handoff:** resolve the exact mechbook modifier and operator profile without duplicating formulas on platforms. Pin in Task 2 rule tests and the shared action-render model test.

---

## File Structure

### Shared content and rules

- Create `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/manifest.json` and `content/{frames,engines,cores,weapons,systems,maneuvers}_catalog.json`: canonical typed entries transcribed from the corresponding mechbook tabs.
- Create `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechCatalog.kt`: typed records for frames, engines, cores, weapons, systems/AI profiles, maneuvers, and the aggregate catalog.
- Create `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechCatalogCodec.kt`: strict parsing from the existing JSON AST into typed records.
- Create `shared/src/commonMain/kotlin/com/furybook/dubl/content/DublMechFcpCatalogLoader.kt`: pack ID/root, entry kinds, catalog load, and bundled-content verification.
- Create `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechBuild.kt`: persisted build and current combat state.
- Create `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechRules.kt`: derived values, validation, check presets, maneuvers, attacks, and armor penetration.
- Create `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechRoster.kt`: roster/store contract, codec, and application operations.

### Platform adapters and screens

- Modify `app/src/main/java/com/furybook/android/data/AndroidDublFcp.kt` and `AndroidContentPackState.kt`: load, verify, compose, enable, and disable the bundled pack.
- Create `app/src/main/java/com/furybook/android/data/MechRepository.kt` and `app/src/main/java/com/furybook/android/state/MechController.kt`: Android persistence and observable adapter.
- Modify `app/src/main/java/com/furybook/android/ui/DublApp.kt` and `ui/screens/CharactersScreen.kt`; create `ui/screens/MechScreen.kt`: open a dedicated mech page from the optional pack area without adding a seventh bottom-nav item.
- Modify `shared/src/desktopMain/kotlin/com/furybook/desktop/data/DesktopCatalogLoader.kt` and `DesktopCharacterStore.kt` only to share existing resource and data-directory conventions; create `DesktopMechStore.kt`.
- Modify `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt`, `Main.kt`, and `screens/CharactersScreen.kt`; create `screens/MechScreen.kt`.
- Modify `tools/tests/test_fcp_contract.py` and add common tests under `shared/src/commonTest/kotlin/com/furybook/dubl/mechs/` and `.../dubl/content/`.

---

### Task 1: Build the bundled mech FCP and typed catalog

**Files:**
- Create: `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/manifest.json`
- Create: `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/content/frames_catalog.json`
- Create: `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/content/engines_catalog.json`
- Create: `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/content/cores_catalog.json`
- Create: `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/content/weapons_catalog.json`
- Create: `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/content/systems_catalog.json`
- Create: `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/content/maneuvers_catalog.json`
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechCatalog.kt`
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechCatalogCodec.kt`
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/content/DublMechFcpCatalogLoader.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/content/DublMechFcpCatalogLoaderTest.kt`
- Test/modify: `tools/tests/test_fcp_contract.py`

**Interfaces:**
- Produces `MechCatalog(frames, engines, cores, weapons, systems, maneuvers)`.
- Produces `DublMechFcp.open(source: FcpTextSource): DublMechFcpCatalogLoader`, `DublMechFcpCatalogLoader.loadCatalog(): MechCatalog`, and `verifyContent(): Unit`.
- Entry kinds are `dubl.mechs.frames`, `dubl.mechs.engines`, `dubl.mechs.cores`, `dubl.mechs.weapons`, `dubl.mechs.systems`, and `dubl.mechs.maneuvers`. The manifest declares all under an optional module and the core-pack dependency.

- [ ] **Step 1: Add failing manifest and loader tests.** Add `testMechFcpIsOptionalAndDependsOnDublCore` to `test_fcp_contract.py`; assert pack ID/version/ruleset/dependency, `enabledByDefault=false`, six declared entry kinds, and all referenced files exist. Add `loadsAllMechCatalogsFromDeclaredEntries` and `rejectsMalformedOrDuplicateMechCatalogIds` to `DublMechFcpCatalogLoaderTest.kt`.
- [ ] **Step 2: Run the tests and confirm the missing-pack failures.** Run `python3 -m pytest tools/tests/test_fcp_contract.py -q` and `./gradlew :shared:desktopTest --tests com.furybook.dubl.content.DublMechFcpCatalogLoaderTest`. Expected: fail because the pack and loader do not exist.
- [ ] **Step 3: Define typed catalog records and strict codecs.** Add a typed field for each numeric, enum, list, and rules-text field present in the mechbook cards. Store AI operator skill profiles on AI system entries. Keep damage expressions typed as a display string only if the source formula is not a single numeric value; do not parse mechanics from prose.
- [ ] **Step 4: Add the bundled FCP and loader.** Transcribe the complete initial frame/engine/core/weapon/system/maneuver catalogs from the mechbook. Reject missing required fields, duplicate IDs, invalid numeric bounds, and undeclared/missing content files. Do not add UI contributions to the pack.
- [ ] **Step 5: Run loader, FCP-contract, and deterministic-builder tests.** Run `python3 -m pytest tools/tests/test_fcp_contract.py -q`, `./gradlew :shared:desktopTest --tests com.furybook.dubl.content.DublMechFcpCatalogLoaderTest`, and `python3 -m tools.fcp.build_fcp --source shared/src/commonMain/resources/fcp/dubl-mechs-3.69 --output /tmp/dubl-mechs-3.69.fcp`. Expected: all tests pass and the builder reports a valid deterministic archive.
- [ ] **Step 6: Commit the catalog and loader.** `git add shared/src/commonMain/resources/fcp/dubl-mechs-3.69 shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechCatalog.kt shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechCatalogCodec.kt shared/src/commonMain/kotlin/com/furybook/dubl/content/DublMechFcpCatalogLoader.kt shared/src/commonTest/kotlin/com/furybook/dubl/content/DublMechFcpCatalogLoaderTest.kt tools/tests/test_fcp_contract.py && git commit -m "feat: add bundled mech catalog"`

---

### Task 2: Implement shared mech construction and play rules

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechBuild.kt`
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechRules.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/dubl/mechs/MechRulesTest.kt`

**Interfaces:**
- `MechBuild(id, name, pilotCharacterId, frameId, engineId, coreId, mountedWeapons, installedSystemIds, currentSectionDurability, structure, stress, energy, heat, repairs, ammunition)`.
- `MechRules.derive(build: MechBuild, catalog: MechCatalog): MechDerivedStats`.
- `MechRules.validate(build: MechBuild, catalog: MechCatalog): MechBuildValidation`; validation returns typed violations for missing IDs, class limits, incompatible mounts, weapon traction, load, mass/cargo, capacity, and energy.
- `MechRules.checkPreset(kind: MechCheckKind, build: MechBuild, catalog: MechCatalog, pilot: DublCharacter?): MechCheckPreset`.
- `MechRules.effectiveArmor(targetArmor: Int, armorPiercing: Int): Int`.
- Maneuver models retain action-point cost, energy/heat cost, check kind/difficulty, and effects/text exactly as catalogued. Roll resolution calls the existing shared `rollCheck`, `rollFollowUp`, and DUBL target comparison APIs.

- [ ] **Step 1: Add failing golden rules tests.** Create `MechRulesTest.kt` with `everestExampleDerivesDocumentedStats`, `validationRejectsEngineAboveFrameLimit`, `validationRejectsUnavailableMountAndOverloadedBuild`, `pilotChecksUseLowerRelevantStatAndKeepSkillRank`, `directConnectionUsesFullMechStatAndKeepsPilotSkill`, `aiControlUsesInstalledAiProfileAndCanCoexistWithPilot`, `unoperatedMechHasNoOperatorCheckPreset`, `armorPiercingFourReducesArmorByFourToMinimumZero`, and `maneuverAndAttackPresetsUseTheirDocumentedSkills`.
- [ ] **Step 2: Run the tests and verify they fail.** Run `./gradlew :shared:desktopTest --tests com.furybook.dubl.mechs.MechRulesTest`. Expected: compilation/test failure because the mech model and rules are missing.
- [ ] **Step 3: Add the persisted build and derived/validation result types.** Use stable component IDs rather than copying catalog records into a build. Keep current combat values separate from derived maximums so catalog updates recalculate limits without overwriting current values.
- [ ] **Step 4: Implement derivation and build validation.** Apply catalog values and installed modifiers once. For the documented Everest example, assert hull durability `192`, section durability `48`, load limit `20`, and the stated frame/engine/core totals. Invalid builds return violations and never become saved application state.
- [ ] **Step 5: Implement pilot, direct-link, AI, maneuver, attack, and armor rules.** Resolve the rules exactly as stated by the mechbook: use the lower applicable pilot/mech characteristic, retain the relevant DUBL skill, bypass the limit only for the direct-connection trait, use the AI system profile while it controls the mech, and leave a mech without an operator unable to make operator-dependent checks. Armor penetration reduces armor by its listed value to a minimum of zero. Use the DUBL roll pipeline rather than a second dice implementation.
- [ ] **Step 6: Run the rule tests.** Run `./gradlew :shared:desktopTest --tests com.furybook.dubl.mechs.MechRulesTest`. Expected: all golden results and validation violations pass.
- [ ] **Step 7: Commit the shared rules.** `git add shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechBuild.kt shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechRules.kt shared/src/commonTest/kotlin/com/furybook/dubl/mechs/MechRulesTest.kt && git commit -m "feat: add shared mech rules"`

---

### Task 3: Add separate mech roster persistence and application boundary

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechRoster.kt`
- Create: `shared/src/commonTest/kotlin/com/furybook/dubl/mechs/MechRosterTest.kt`

**Interfaces:**
- `data class MechRoster(val builds: List<MechBuild>, val selectedBuildId: String?)`.
- `data class MechRosterLoadResult(val roster: MechRoster, val warnings: List<String>)`.
- `interface MechRosterStore { fun load(): MechRosterLoadResult; fun save(roster: MechRoster) }`.
- `object MechRosterCodec { fun encode(roster: MechRoster): String; fun decode(raw: String): MechRosterLoadResult }`.
- `class MechApplication(store: MechRosterStore, idFactory: () -> String)` exposes `roster`, `createBuild(name)`, `saveBuild(build, catalog): MechBuildSaveResult`, `deleteBuild(id)`, `setControlMode(id, mode)`, and `updateCombatState(id, state)`. `saveBuild` persists only when `MechRules.validate` has no violations.

- [ ] **Step 1: Add failing round-trip and recovery tests.** Add `rosterCodecRoundTripsPilotAiAndCurrentState`, `decodeKeepsValidBuildsAndWarnsForMalformedBuild`, `missingPilotReferenceDoesNotDropBuild`, `saveBuildRejectsInvalidAssemblyWithoutChangingRoster`, and `disabledModuleDoesNotDeleteRoster`.
- [ ] **Step 2: Run the tests and verify they fail.** Run `./gradlew :shared:desktopTest --tests com.furybook.dubl.mechs.MechRosterTest`. Expected: missing roster API or failing assertions.
- [ ] **Step 3: Implement the roster codec using `com.furybook.core.json`.** Version the independent roster document; decode entries independently so malformed cards become warnings without discarding valid cards. Keep the raw store unchanged until a successful explicit save.
- [ ] **Step 4: Implement `MechApplication`.** Centralize creation, validated save, delete, control handoff, and current combat-state updates; do not expose or mutate `DublApplication` snapshots.
- [ ] **Step 5: Run roster and rules tests.** Run `./gradlew :shared:desktopTest --tests com.furybook.dubl.mechs.MechRosterTest --tests com.furybook.dubl.mechs.MechRulesTest`. Expected: all tests pass.
- [ ] **Step 6: Commit the roster boundary.** `git add shared/src/commonMain/kotlin/com/furybook/dubl/mechs/MechRoster.kt shared/src/commonTest/kotlin/com/furybook/dubl/mechs/MechRosterTest.kt && git commit -m "feat: add separate mech roster"`

---

### Task 4: Connect optional FCP activation and platform stores

**Files:**
- Modify: `app/src/main/java/com/furybook/android/data/AndroidDublFcp.kt`
- Modify: `app/src/main/java/com/furybook/android/data/AndroidContentPackState.kt`
- Create: `app/src/main/java/com/furybook/android/data/MechRepository.kt`
- Modify: `shared/src/desktopMain/kotlin/com/furybook/desktop/data/DesktopCatalogLoader.kt`
- Create: `shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopMechStore.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt`
- Test: `tools/tests/test_fcp_contract.py`

**Interfaces:**
- Android: `AndroidContentPackState.isMechsEnabled(context)`, `setMechsEnabled(context, enabled)`, and `mechCatalog(context): MechCatalog?`.
- Desktop: `DesktopAppState.mechPackEnabled`, `mechCatalog: MechCatalog?`, and existing `setContentPackActive(packId, enabled)`.
- Both platform compositions always list the bundled mech manifest as available but include its ID in `enabledPackIds` only after the user enables it.
- `MechRepository(context): MechRosterStore` uses its own preference key; `DesktopMechStore(file: Path = defaultDataDirectory().resolve("mechs.json")): MechRosterStore` uses a separate file.

- [ ] **Step 1: Add failing activation contract tests.** Add `mechPackIsAvailableDisabledByDefaultAndBundledOnBothPlatforms`, `enablingMechPackLoadsCatalog`, `disablingMechPackRemovesRuntimeCatalogButPreservesRoster`, and `mechPackCannotBeImportedAsAnExternalOverride` to the source-contract tests. Assert that the new pack ID is treated as bundled and is never loaded through the external development-addon path.
- [ ] **Step 2: Run the tests and confirm the missing activation path.** Run `python3 -m pytest tools/tests/test_fcp_contract.py -q`. Expected: fail on missing built-in pack and state accessors.
- [ ] **Step 3: Add Android and Desktop pack loaders and activation state.** Include the new manifest in composition availability, persist enabled state with the existing per-install content-pack preferences, verify bundled content before activation, and return no active mech catalog while disabled.
- [ ] **Step 4: Add separate platform stores.** Android persists encoded roster in a dedicated key; Desktop uses a dedicated file under the existing data directory. Neither adapter reads/writes character state.
- [ ] **Step 5: Verify activation and store isolation.** Run `python3 -m pytest tools/tests/test_fcp_contract.py -q` and `./gradlew :shared:desktopTest --tests com.furybook.dubl.mechs.MechRosterTest`. Expected: module toggles independently of Chi; roster survives disable/re-enable; DUBL snapshot storage remains untouched.
- [ ] **Step 6: Commit activation and stores.** `git add app/src/main/java/com/furybook/android/data/AndroidDublFcp.kt app/src/main/java/com/furybook/android/data/AndroidContentPackState.kt app/src/main/java/com/furybook/android/data/MechRepository.kt shared/src/desktopMain/kotlin/com/furybook/desktop/data/DesktopCatalogLoader.kt shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopMechStore.kt desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt tools/tests/test_fcp_contract.py && git commit -m "feat: activate optional mech pack"`

---

### Task 5: Add Android mech roster, builder, and play screen

**Files:**
- Modify: `app/src/main/java/com/furybook/android/ui/DublApp.kt`
- Modify: `app/src/main/java/com/furybook/android/ui/screens/CharactersScreen.kt`
- Create: `app/src/main/java/com/furybook/android/state/MechController.kt`
- Create: `app/src/main/java/com/furybook/android/ui/screens/MechScreen.kt`
- Test: `tools/tests/test_fcp_contract.py` or the existing Android state-test source set if available.

**Interfaces:**
- `MechController(catalog: MechCatalog, store: MechRosterStore, idFactory: () -> String)` exposes observable roster, `saveBuild`, `setControlMode`, `updateCombatState`, and load warnings by adapting `MechApplication`.
- `MechScreen(controller, pilots, onBack)` provides roster, creation/editing, build validation, mech card, action rolls, and combat-state fields.
- The Android bottom navigation stays six items. The optional pack card in `CharactersScreen` opens the dedicated mech page; leaving the page returns to the same DUBL area.

- [ ] **Step 1: Add failing state-adapter tests for Android.** Verify that creating a valid build updates observable roster, invalid builds return validation errors without mutation, pilot IDs link to the provided DUBL roster, and a disabled catalog closes the page.
- [ ] **Step 2: Run the relevant Android tests and confirm failure.** Run `./gradlew :app:testDebugUnitTest`. Expected: missing controller/UI contracts or failing assertions.
- [ ] **Step 3: Implement `MechController`.** Wrap `MechApplication` and publish only its separate roster; resolve a pilot by ID from the supplied character snapshot and tolerate a missing pilot as defined by Task 2.
- [ ] **Step 4: Add Android page entry and disabled-state behavior.** Add an “Открыть мехи” action to the active pack card in `CharactersScreen`; have `DublApp` open a dedicated page, close it when the pack becomes disabled, and keep the bottom bar at six items.
- [ ] **Step 5: Implement the build and mech card UI.** Add frame/engine/core selectors, compatible weapon mounts and systems, AI install/control handoff, validation messages, derived values, pilot picker, current structure/stress/energy/heat/repairs/ammunition, maneuver/action list, and DUBL-compatible roll result breakdowns. Disable save while validation violations remain.
- [ ] **Step 6: Run Android tests and compile.** Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: tests pass and debug APK compiles with module both enabled and disabled.
- [ ] **Step 7: Commit Android integration.** `git add app/src/main/java/com/furybook/android/ui/DublApp.kt app/src/main/java/com/furybook/android/ui/screens/CharactersScreen.kt app/src/main/java/com/furybook/android/state/MechController.kt app/src/main/java/com/furybook/android/ui/screens/MechScreen.kt && git commit -m "feat: add Android mech screens"`

---

### Task 6: Add Desktop parity and complete verification

**Files:**
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharactersScreen.kt`
- Create: `desktopApp/src/main/kotlin/com/furybook/desktop/screens/MechScreen.kt`
- Modify: `docs/FCP.md`
- Modify: `tools/tests/test_fcp_contract.py`

**Interfaces:**
- Desktop uses Task 2 rules and Task 3 `MechApplication` through a Compose-observable adapter/state; no desktop formula implementation is allowed.
- `DesktopSection.MECHS` is present in the rail/compact navigation only while the pack is enabled.
- The content-pack manager and mech page use the same stable pack ID, label, and behavior as Android.

- [ ] **Step 1: Add failing Desktop parity contract checks.** Assert the conditional mech navigation, pack-manager entry, same shared `MechRules`/controller boundary, and hidden route when disabled.
- [ ] **Step 2: Run `python3 -m pytest tools/tests/test_fcp_contract.py -q` and confirm the missing Desktop route assertions.**
- [ ] **Step 3: Add conditional Desktop navigation and page entry.** Update `DesktopSection`, rail, compact navigation, `DesktopContent`, and pack manager so the route appears only while active; if disabled while selected, return to the character sheet.
- [ ] **Step 4: Implement Desktop mech UI using shared models.** Match Android's creation/editing fields and validation, pilot/AI handoff, combat values, maneuvers, checks, attacks, and result breakdown. Keep list and builder behavior equivalent across platforms.
- [ ] **Step 5: Update FCP documentation.** Document the built-in optional pack ID, catalog entry kinds, default-off activation, separate roster persistence, and the host adapter boundary in `docs/FCP.md`.
- [ ] **Step 6: Run all required verification.** Run `python3 -m pytest tools/tests/test_fcp_contract.py -q`, `./gradlew :shared:desktopTest :desktopApp:compileKotlin`, and `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: all pass. Rebuild the deterministic mech FCP and confirm its SHA-256 is unchanged between two builds.
- [ ] **Step 7: Commit Desktop parity and docs.** `git add desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharactersScreen.kt desktopApp/src/main/kotlin/com/furybook/desktop/screens/MechScreen.kt docs/FCP.md tools/tests/test_fcp_contract.py && git commit -m "feat: add desktop mech screens"`

---

## Plan Self-Review

- **Spec coverage:** Optional activation and disabled UI are covered by Task 4 and Task 5/6; all catalogs and source data by Task 1; build calculations, pilot limits, direct connection, weapons, maneuvers and AI handoff by Task 2; separate roster, corruption recovery and pilot reference behavior by Task 3; both platform flows by Tasks 5 and 6; compatibility and FCP docs by Task 6.
- **Step scan:** Each checkbox is one test, implementation, verification, or commit action. Shared APIs are listed before consuming tasks.
- **Type consistency:** Tasks 1–3 define `MechCatalog`, `MechBuild`, `MechRosterStore`, `MechApplication`, and `MechRules` before platform consumers.
- **Review Focus:** All five listed failure classes have named tests or contract checks in the task that owns the behavior.
- **Proportion:** Tasks describe interfaces and exact gates without reproducing implementation bodies; catalog numeric values remain grounded in the mechbook rather than guessed in the plan.
