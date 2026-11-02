# Architecture

## Authority and source of truth

Fury Book separates **product identity**, **ruleset authority**, and **implementation reference**.

For the currently shipped DUBL integration, authority order for behavior and canonical content:

1. DUBL 3.69 rulebooks and approved module books
2. explicit tracked interpretations in `rulesets/dubl-3.69/resolutions.json`
3. shared executable rules/application contracts
4. platform presentation behavior

Android is the mature UX reference. It is not a rules authority when it conflicts with the books.

## Product / ruleset boundary

**Fury Book** is the user-facing product and cross-platform shell. **DUBL 3.69** is the currently implemented ruleset. Existing DUBL package names, ruleset IDs, transfer IDs and data-directory names are compatibility identities and are not renamed as part of product branding.

The current executable application boundary is still DUBL-specific (`DublApplication`). Future ruleset work should introduce explicit ruleset composition/adapter boundaries rather than renaming DUBL internals in-place or moving formulas into platform UI.

## Namespace ownership

Stage 1 establishes package ownership independently of compatibility identifiers:

- `com.furybook.dubl.*` — shared DUBL domain, application, persistence contracts and DUBL-specific shared helpers;
- `com.furybook.ui.*` — product-level shared Compose primitives/theme/layout policy;
- `com.furybook.android.*` — Android frontend and Android-only adapters;
- `com.furybook.desktop.*` — Compose Desktop frontend and desktop-only adapters.

`shared/commonMain` must never depend on or declare Android/Desktop platform packages. Android's installed `applicationId` remains `com.dubl.character.android` for upgrade compatibility even though its source namespace is `com.furybook.android`.

## Shared core

`shared` contains the platform-independent character model, executable rules, roll engine, development/magic/equipment logic, canonical catalog payloads/parsers, persistence codecs/contracts, responsive policy, theme tokens, and reusable Compose primitives.

`commonMain` must not depend on Android- or desktop-only APIs.

### Application boundary

`DublApplication` is the public state-changing boundary for both Android and Compose Desktop. It exposes focused capabilities for character/resources, skills, development/Chi, magic, equipment, transfer, and sheet extras/grouping.

Raw sessions and repositories are implementation details. Platform adapters may observe state and call typed application operations, but platform UI must not own duplicate game formulas or arbitrary persistence mutations.

Typed golden/parity scenarios protect cross-platform behavior. They are behavior locks, not substitutes for rulebook correctness: a verified rulebook correction may intentionally update a shared rule and its golden expectation once.

## Fury Content Packs

Fury Book separates executable rules from declarative content. `.fcp` is the product-level content container: a deterministic ZIP with `manifest.json`, typed ordered entries and checksums. Format-level code is owned by `com.furybook.content` and must not depend on DUBL.

DUBL 3.69 is the first native FCP-backed ruleset. Its engine remains in `com.furybook.dubl.*`; its canonical promoted catalogs live in the bundled `dubl-3.69` FCP. `GeneratedSkillCatalog.kt` is a compiled projection of pack-owned skill JSON, not a separate content authority.

See `docs/FCP.md`.

## Persistence

`CharacterStore` and the shared codecs define the character persistence boundary.

- Android uses its platform repository/SharedPreferences adapter.
- Desktop uses `DesktopCharacterStore` and `DesktopCharacterExtrasStore` under the user's local data directory.
- Both platforms use `SnapshotCodec`; the current written snapshot schema is **11**.
- Each character persists a `RulesetRef`; DUBL uses `dubl / 3.69`.
- Portable character exchange uses `CharacterTransferCodec` format `dubl.character` version 1.
- Older snapshots are handled through the shared compatibility/defaulting path rather than separate platform migrations.

## Canonical catalogs and rulebook import

Canonical runtime content is packaged as Fury Content Packs. The bundled DUBL pack lives unpacked under `shared/src/commonMain/resources/fcp/dubl-3.69/`; Android and Desktop consume it through the same `FcpContentPack` / `DublFcpCatalogLoader` boundary.

Rulebook DOCX files are development inputs, not runtime assets. The import pipeline preserves source provenance, diagnostics and unresolved ambiguity. Executable interpretation of ambiguous source material must be explicit in `rulesets/dubl-3.69/resolutions.json`.

Generated full rulebook bundles belong under ignored `build/rulesets/dubl-3.69`. Only the compact control plane and promoted artifacts are tracked.

## Frontends

### Android

Jetpack Compose mobile application in `app`. Android owns mobile presentation, Android persistence adapters and platform services while delegating rules/application behavior to `shared`.

### Compose Desktop

`desktopApp` is the canonical desktop frontend. It uses desktop-native responsive Compose layouts over the same shared application, rules, catalogs and persistence semantics.

## Release architecture

- Android development builds: `.github/workflows/android-ci.yml`
- Linux Compose AppImage: `.github/workflows/linux-appimage.yml`
- Windows Compose EXE/MSI: `.github/workflows/windows-desktop.yml`
- Unified production release: `.github/workflows/release.yml`

The project uses the 9.7.0 Gradle bootstrap/wrapper contract and JVM 17. Platform CI must use the repository wrapper entrypoints rather than independently pinning a different Gradle version.
