# Current project status

Last refreshed: 2026-10-13

## Unreleased acquisition and automation work

The current working branch contains shared canonical acquisition, ledger/history/Undo, Android/Desktop/Web adapters, source-qualified roll formulas and prerequisites. This is not a published release: acquisition-wide native evidence integration, platform interaction and server rollout gates remain open. Production capabilities stay disabled. See [automation progress](automation-progress.md) and [rollout and recovery](acquisition-rollout.md). The product milestones below describe the older shipped line, not acceptance of this working branch.

This is the living status document for Fury Book. Historical milestone reports may contain older product names and are not current branding or architecture authority.

## Product

- Product: **Fury Book**
- Current public product line: **0.5**
- Currently shipped ruleset: **DUBL 3.69**
- Frontends: Android + Compose Desktop (Linux/Windows)
- Web/Wasm, accounts and sync: out of current scope

Fury Book is the product shell and cross-platform application. DUBL is the current ruleset integration.

## Shared foundation

Android and Desktop use the same Kotlin Multiplatform model/rules/application layer.

Current hard boundaries:

- Namespace ownership: shared DUBL code is `com.furybook.dubl.*`, shared product UI is `com.furybook.ui.*`, and platform code is isolated under `com.furybook.android.*` / `com.furybook.desktop.*`.

- `DublApplication` is the public state-changing boundary for the current DUBL integration.
- canonical DUBL runtime catalogs live inside the bundled `dubl-3.69` Fury Content Pack.
- executable formulas belong in shared rules, not platform UI.
- typed shared golden/parity scenarios protect Android/Desktop behavior.
- local user overrides remain separate from immutable canonical ruleset data.

## Compatibility identities

Branding changes do not migrate stored data or package identities.

Kept stable intentionally:

- Android application ID: `com.dubl.character.android`
- current snapshot schema: **11**
- DUBL ruleset identity: `dubl / 3.69`
- transfer format: `dubl.character` version **1**
- Desktop local data identity: `dubl-character`

Product-facing names, installers, launchers and release artifacts use **Fury Book**.

## DUBL rulebook-first pipeline

The tracked DUBL ruleset control plane lives in `rulesets/dubl-3.69`.

The importer:

- extracts deterministic source IR from approved books;
- preserves source-qualified provenance;
- surfaces ambiguity/conflicts as diagnostics;
- requires explicit tracked resolutions for executable interpretations;
- keeps full generated bundles under ignored `build/rulesets/dubl-3.69`.

Do not infer a DUBL rule from current UI behavior when the rulebook source is ambiguous.

## Active implementation priorities

1. DUBL 3.69 correctness and domain promotion
2. Android/Desktop parity through shared application contracts
3. product/ruleset separation needed for future Fury Book rulesets
4. persistence / transfer compatibility
5. performance and interaction polish
6. reliable Android, Linux and Windows release gates

## Legacy / historical material

These remain historical context, not current implementation or branding authority:

- `docs/KMP_FOUNDATION_REPORT.md`
- `docs/DESKTOP_0_2_PARITY.md`
- `docs/DESKTOP_CHARACTER_SHEET_REPORT.md`
- `docs/superpowers/plans/`
- `docs/superpowers/specs/`
- `packaging/linux/portable-src/`
- `packaging/linux/build-portable-appimage.sh`

## Verification

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlin
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

For release/rulebook work, also run the relevant Python contract suite and importer validation documented by the workflow or README.
