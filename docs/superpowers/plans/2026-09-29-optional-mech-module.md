# Optional Mech Module — Implementation Plan

**Goal:** implement `dubl-mechs-3.69` as a real optional Fury Content Pack without introducing a mech-specific side architecture.

**Architecture rule:** Fury Book host owns generic pack composition, UI surfaces and platform rendering. The DUBL adapter owns mech semantics and rules. The FCP owns declarative data and its UI/module contribution. Android is the UX reference; Desktop consumes the same shared application/rules/render models.

**Spec:** `docs/superpowers/specs/2026-09-29-optional-mech-module-design.md`

## Hard constraints

- No mech fields in `DublCharacter`, `AppSnapshot` or `dubl.character` transfer.
- No executable mechanics in FCP.
- No `isMechsEnabled` / `setMechsEnabled` / `mechPackEnabled` shadow state if `FcpComposition` already answers activation.
- No raw pack binding strings in platform screens.
- No `if (pack == dubl-mechs-3.69)` in generic host UI.
- No platform copy of mech formulas.
- No public arbitrary `updateCombatState` setter.
- No monolithic recovery path where one malformed mech can be lost when another mech is saved.
- Do not invent source values. Pin the exact mechbook v0.5 source before catalog transcription.

## Target shape

`dubl-mechs-3.69`:
- bundled optional pack;
- dependency on `dubl-3.69 / 3.69`;
- typed mech catalog entries;
- a host-supported module-page UI contribution with binding `dubl.mechs`;
- pack activation controlled by common enabled-pack composition.

Shared DUBL code:
- `MechCatalog` / codecs;
- `MechDraft` and `MechBuild`;
- `MechRules`;
- `MechApplication` command boundary;
- per-record persistence codec/contracts;
- typed UI/module render models.

Platform code:
- bytes/storage adapters;
- Compose rendering;
- route mounting from typed module models;
- no rules.

---

## Task 0 — Pin the source before coding rules

- [ ] Put the exact mechbook v0.5 source under a stable repository path or document an immutable source reference plus checksum.
- [ ] Record provenance in the spec/FCP docs.
- [ ] Extract a small set of canonical source examples for golden tests: one valid full build, one invalid build for each major constraint family, one pilot check, one heat/stress transition, one armor-penetration example, and AI/control examples only if explicitly present in the source.
- [ ] If a required rule is ambiguous, stop that rule implementation and record the question instead of choosing an interpretation.

**Exit condition:** every numeric golden test can point to an exact source location/version.

---

## Task 1 — Add a generic module-page FCP UI capability

Do this before adding a mech route.

- [ ] Extend the existing host UI capability registry with one generic module destination surface/component family (naming should follow existing FCP conventions; expected semantic shape is `app.modules/module-page`).
- [ ] Keep validation host-owned: supported properties should be semantic presentation tokens such as label/icon/order, not Compose classes.
- [ ] Extend the shared DUBL UI registry so `binding = dubl.mechs` resolves to a typed DUBL feature/module model.
- [ ] Platform UI must consume the typed model; the raw binding string stays inside the DUBL adapter.
- [ ] Add tests proving unsupported module renderer/property/binding combinations fail or are ignored according to the existing host contract.
- [ ] Add a regression test proving existing Chi UI contributions still behave identically.

**Do not** add a mech-specific navigation button directly in `CharactersScreen` or the FCP manager.

**Exit condition:** a synthetic active FCP contribution can create a typed module destination without a platform check on its pack ID.

---

## Task 2 — Add the bundled mech FCP and typed catalog

Create:

- `shared/src/commonMain/resources/fcp/dubl-mechs-3.69/manifest.json`;
- typed content files for frames, engines, cores, weapons, systems and maneuvers;
- shared DUBL catalog models/codecs/loader.

Manifest rules:

- pack ID `dubl-mechs-3.69`;
- dependency `dubl-3.69 / 3.69`;
- the internal module may be required/default-enabled **inside the pack**; do not use module `enabledByDefault` as pack activation state;
- declare the module-page UI contribution from Task 1;
- no executable content.

Implementation rules:

- use stable IDs;
- represent numbers/limits/costs structurally;
- never parse mechanics from description text at runtime;
- reject missing required fields, duplicate IDs, invalid bounds and undeclared/missing content files;
- reserve bundled pack IDs against external import replacement.

Tests:

- manifest/dependency/content completeness;
- strict loader behavior;
- deterministic FCP build/checksum;
- host capability validation for the mech UI contribution.

**Exit condition:** the pack builds deterministically and can be loaded/validated without any Android/Desktop mech code.

---

## Task 3 — Implement shared mech domain and rules

Create shared DUBL models for:

- `MechDraft` — nullable/incomplete builder selections;
- `MechBuild` — saved mechanically valid assembly;
- `MechOperatorState` / control mode;
- `MechCombatState`;
- typed validation violations;
- derived stats;
- actions/costs/results/check presets.

Implement `MechRules` as pure common code.

Required rule areas, only where grounded in the pinned source:

- derived frame/engine/core/system stats;
- mount/class/load/capacity/energy validation;
- weapon/system compatibility;
- pilot/mech characteristic resolution;
- direct connection;
- AI profile/control handoff;
- action/reaction economy;
- ammunition/energy;
- heat/stress/structure;
- armor penetration/damage;
- repair;
- DUBL roll pipeline integration.

Rules for saved state:

- incomplete `MechDraft` is not a saved `MechBuild`;
- invalid assembly cannot replace a valid saved build;
- a mechanically valid build may be saved without an operator and is then `UNCREWED`;
- `UNCREWED` cannot produce operator-dependent check presets.

Tests should be source/golden driven, not implementation-shaped.

**Exit condition:** all mech calculations can run in common tests without platform classes or storage.

---

## Task 4 — Add the command application boundary and safe persistence

### Application

Implement `MechApplication` over shared rules and a store abstraction.

Expose typed commands, not raw state mutation:

- create/edit/save;
- copy/rename/delete;
- bind/unbind pilot;
- set/handoff operator mode;
- start turn;
- perform action;
- apply damage/heat;
- repair;
- typed tracked-resource correction where needed.

Every command validates invariants before persistence.

There must be no public `updateCombatState(id, arbitraryState)`.

### Persistence

Use a versioned per-card codec plus roster/index metadata, or an equivalent record-oriented store contract.

Required behavior:

- one corrupted build does not block valid builds;
- corrupted raw data is preserved until explicit delete/replace;
- saving build A cannot erase corrupted build B;
- missing pilot reference does not invalidate the mech record;
- unknown component IDs produce a typed load/validation problem;
- module disable/enable never rewrites records.

Platform adapters:

- Android: dedicated app-local mech namespace;
- Desktop: dedicated mech namespace under the existing Fury Book data directory;
- neither touches `DublApplication` storage.

Tests:

- round trip;
- partial corruption recovery;
- save isolation;
- invalid edit preserves previous valid record;
- missing pilot;
- disable/re-enable persistence.

**Exit condition:** persistence failure of one card cannot destroy another and UI cannot bypass shared transitions.

---

## Task 5 — Integrate bundled-pack activation without new shadow flags

Use the existing pack activation/composition path.

- [ ] Add the bundled mech manifest to the DUBL bundled-pack source/registry used by both platforms.
- [ ] Prefer normalizing the existing bundled-pack handling over adding a third platform-specific `when(packId)` branch.
- [ ] Activation verifies bundled content before writing enabled state.
- [ ] `FcpComposition` is the source of truth for whether mech runtime content/UI is active.
- [ ] Catalog/runtime access is derived from the active composition.
- [ ] Add the mech pack to reserved bundled IDs for import protection through the shared/bundled registry.
- [ ] When the pack deactivates while its route is open, navigation returns to a stable host destination.
- [ ] No mech persistence is touched on deactivate.

Do not introduce dedicated `isMechsEnabled`, `setMechsEnabled` or desktop `mechPackEnabled` properties unless they are merely temporary private adapters removed before merge.

Tests:

- available but inactive by default;
- dependency closure;
- enable -> typed catalog + module mount present;
- disable -> both absent;
- persistence unchanged;
- core/Chi behavior unchanged.

**Exit condition:** active composition alone explains whether the mech module exists at runtime.

---

## Task 6 — Android UX first

Android defines the concrete UX flow.

Implement a dedicated mech module screen from the typed module destination, not from an FCP-manager button.

The module provides:

- roster;
- create/edit builder;
- validation with explicit reasons;
- pilot/operator selection;
- derived stats/equipment;
- play view;
- tracked combat resources;
- maneuvers/weapons/actions;
- DUBL-compatible result breakdowns.

UI uses a thin observable adapter over `MechApplication`. It may hold ephemeral `MechDraft` UI state, but persisted mutations go through application commands.

Navigation requirements:

- destination exists only while the FCP contribution is active;
- pack manager remains only a manager;
- exact compact placement is host-owned, but the mech destination is first-class;
- disabling the pack while viewing mechs exits safely.

Verification:

- Android unit/source-contract tests;
- full `:app:assembleDebug`;
- enabled and disabled flows;
- edit failure does not overwrite saved build;
- missing pilot/uncrewed UI.

**Exit condition:** complete Android flow works without platform formulas or raw combat-state writes.

---

## Task 7 — Desktop parity

Desktop consumes the same:

- active `FcpComposition`;
- typed module destination;
- `MechCatalog`;
- `MechApplication`;
- shared render/check/action models.

Desktop may render the responsive navigation differently, but it must preserve Android's information hierarchy and capabilities.

Do not create desktop-only rule helpers or a separate mech state machine.

Verification:

- same create/edit/play cases as Android;
- route appears/disappears from the same typed contribution;
- same validation and action results for identical golden inputs;
- existing character/Chi flows remain unchanged;
- `:desktopApp:compileKotlin` passes.

**Exit condition:** parity is demonstrated through shared scenarios, not duplicated screen logic.

---

## Task 8 — Final architecture and regression verification

Run at minimum:

- `python3 -m pytest tools/tests/test_fcp_contract.py -q`;
- shared desktop tests for catalog/rules/application/persistence;
- `./gradlew :shared:desktopTest :desktopApp:compileKotlin`;
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`;
- deterministic mech FCP build twice and compare SHA-256.

Update `docs/FCP.md` with:

- bundled mech pack identity;
- typed entry kinds;
- module-page UI contribution;
- pack activation semantics;
- DUBL adapter boundary;
- separate mech persistence;
- explicit note that FCP data is declarative while mechanics remain in the ruleset adapter.

## Merge gate

Do not merge if any of these are true:

- Android/Desktop checks a mech pack ID to decide whether to draw the route instead of consuming a typed FCP contribution.
- A second boolean can disagree with `FcpComposition` about whether mechs are active.
- Platform code calculates mech rules.
- UI can write arbitrary combat state.
- Invalid draft overwrites a valid build.
- Saving one mech can silently discard another corrupted record.
- Disabling the FCP mutates mech or character persistence.
- Catalog values cannot be traced to the pinned mechbook source.
