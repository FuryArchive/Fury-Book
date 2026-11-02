# Fury Book

**Cross-platform tabletop character and rules companion built with Kotlin Multiplatform and Compose.**

[![Android CI](https://github.com/FuryArchive/Fury-Book/actions/workflows/android-ci.yml/badge.svg?branch=main)](https://github.com/FuryArchive/Fury-Book/actions/workflows/android-ci.yml)
[![Linux Desktop](https://github.com/FuryArchive/Fury-Book/actions/workflows/linux-appimage.yml/badge.svg?branch=main)](https://github.com/FuryArchive/Fury-Book/actions/workflows/linux-appimage.yml)
[![Windows Desktop](https://github.com/FuryArchive/Fury-Book/actions/workflows/windows-desktop.yml/badge.svg?branch=main)](https://github.com/FuryArchive/Fury-Book/actions/workflows/windows-desktop.yml)

**Fury Book is the product. DUBL 3.69 is the currently shipped ruleset.**

The application keeps Android and Desktop on the same shared application/rules layer instead of reimplementing game logic per platform. For DUBL behavior and canonical content, the DUBL 3.69 rulebooks remain authoritative; Android is a mature UX reference, not a rules authority.

> Status: active development. Current product line: **Fury Book 0.5**.

## Current capabilities

The shipped DUBL 3.69 integration includes:

- character identity, XP and creation economy;
- derived stats, resources, conditions and quick checks;
- skills, ranks, modifiers, specialization and rule-aware rolls;
- development trees, prerequisites, martial arts and Chi;
- magic, mana, schools and spellbook management;
- equipment, carrying state, load and burden;
- multiple local characters;
- shared undo semantics and typed Android/Desktop parity scenarios;
- Android and Compose Desktop frontends over shared Kotlin code.

Android and Desktop currently write snapshot schema **11** with explicit `dubl / 3.69` ruleset identity. Older snapshots are decoded through the shared compatibility path.

## Product vs. ruleset identity

Fury Book branding is intentionally separate from DUBL compatibility identifiers.

Current compatibility identifiers that remain unchanged:

- Android application ID: `com.dubl.character.android`;
- DUBL ruleset identity: `dubl / 3.69`;
- portable character transfer format: `dubl.character` version 1;
- existing Desktop data directory identity: `dubl-character`;
- existing `.dubl` character exchange files.

Changing those is a migration task, not a branding task. Keeping them stable preserves upgrades and local data while Fury Book becomes the user-facing product name.

## Architecture

```text
┌──────────────────┐      ┌─────────────────────┐
│ Android / app    │      │ Desktop / desktopApp│
└────────┬─────────┘      └─────────┬───────────┘
         │                          │
         └────────────┬─────────────┘
                      ▼
             ┌──────────────────┐
             │ shared           │
             │ application      │
             │ rules            │
             │ persistence      │
             │ content/FCP      │
             └──────────────────┘
                      │
                      ▼
             DUBL 3.69 ruleset
             + audited imports
```

The current DUBL application boundary is `DublApplication`. Platform code observes state and invokes typed application operations; executable game formulas must not be duplicated in UI code.

Fury Book is being structured so additional rulesets can be integrated without turning platform UI into rules code. Today, DUBL 3.69 remains the only fully implemented ruleset.

### Repository layout

| Path | Purpose |
| --- | --- |
| `shared/` | KMP model, rules, application layer, codecs, Fury Content Pack runtime, shared UI primitives |
| `app/` | Android frontend and Android-specific adapters |
| `desktopApp/` | Compose Desktop frontend |
| `rulesets/dubl-3.69/` | DUBL ruleset config, resolutions, baseline and promoted generated artifacts |
| `tools/rulebook/` | DUBL rulebook import / validation tooling |
| `tools/fcp/` | Fury Content Pack validation/build tooling |
| `packaging/linux/` | canonical Linux AppImage packaging |
| `docs/` | living architecture/status docs and historical reports |
| `ci/` | CI-only signing/test fixtures |


## Build and verify

Toolchain:

- Kotlin 2.4.20
- Compose Multiplatform 1.12.0
- Android Gradle Plugin 9.3.0
- Gradle 9.7.0
- JVM toolchain 17
- Android compileSdk 37 / targetSdk 36

Shared + Desktop:

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlin
./gradlew :desktopApp:run
```

Android:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The platform workflows under `.github/workflows/` are the authoritative networked build gates.

## Fury Content Packs

Fury Book separates executable rules engines from declarative ruleset content. The built-in DUBL 3.69 content is stored as an unpacked Fury Content Pack under:

`shared/src/commonMain/resources/fcp/dubl-3.69/`

Build the distributable pack with:

```bash
python3 -m tools.fcp.build_fcp \
  --source shared/src/commonMain/resources/fcp/dubl-3.69 \
  --output dist/fcp/dubl-3.69.fcp
```

Android and Desktop both load the same manifest-driven pack. See [FCP format](docs/FCP.md).

## DUBL rulebook-first content pipeline

The stock DUBL book and approved module books are development inputs, not runtime dependencies. Imported material is built into an auditable intermediate representation with provenance and diagnostics before promotion into executable catalogs.

Current authoritative DUBL inputs:

- `core` — DUBL 3.69 core rules
- `melee` — Martial Arts / Chi module
- `archmage` — approved additions to supported magic schools

Typical local rebuild:

```bash
python3 -m tools.rulebook.build_ruleset \
  --source 'core=/path/to/core.docx' \
  --source 'melee=/path/to/melee.docx' \
  --source 'archmage=/path/to/archmage.docx' \
  --repo-root .

python3 -m tools.rulebook.validate_ruleset build/rulesets/dubl-3.69

python3 -m tools.rulebook.check_baseline build/rulesets/dubl-3.69 \
  --baseline rulesets/dubl-3.69/baseline.json
```

Ambiguous or contradictory rules are resolved only through `rulesets/dubl-3.69/resolutions.json`.

## Releases

Fury Book uses one product version and one Git tag for all shipped platforms.

Example:

```bash
git tag -a v0.5 -m "Fury Book 0.5"
git push origin v0.5
```

The unified release publishes:

- `Fury-Book-<version>-Android.apk`
- `Fury-Book-<version>-Linux-x86_64.AppImage`
- `Fury-Book-<version>-Windows-x64.exe`
- `Fury-Book-<version>-Windows-x64.msi`
- `Fury-Book-<version>-DUBL-3.69.fcp`

For Linux, the canonical packaging entrypoint is:

```bash
FURY_BOOK_VERSION=0.5.0 packaging/linux/build-appimage.sh
```

The legacy `DUBL_VERSION` environment variable remains accepted as a temporary local compatibility fallback.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before changing rules, catalogs, persistence, shared application behavior, or platform parity contracts.

Useful engineering references:

- [Architecture](docs/ARCHITECTURE.md)
- [Documentation index](docs/README.md)
- [GitHub workflow](docs/GITHUB_WORKFLOW.md)
- [Current project status](docs/STATUS.md)
- [Engineering handoff](HANDOFF.md)

## Current scope

Current priorities are DUBL 3.69 correctness, Android/Desktop parity, shared application behavior, persistence compatibility, performance, release reliability, and separating product-level Fury Book architecture from ruleset-specific DUBL implementation details.

The web app (`webApp/`, Supabase accounts, cloud sync and campaigns/GM screen) is in active development alongside Android and Desktop.
