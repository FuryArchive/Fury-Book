# Fury Book engineering handoff

This file is intentionally short. It is a navigation and invariant sheet, not an execution log.

## Read first

1. [README.md](README.md) — product and repository overview
2. [docs/STATUS.md](docs/STATUS.md) — current implementation status
3. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — architecture and ownership boundaries
4. [CONTRIBUTING.md](CONTRIBUTING.md) — change rules and verification
5. [docs/GITHUB_WORKFLOW.md](docs/GITHUB_WORKFLOW.md) — CI and releases

Historical migration reports and implementation plans are useful context, but they are not current source-of-truth documents.

## Non-negotiable invariants

- DUBL 3.69 rulebooks are authoritative for canonical rules/content.
- Explicit interpretations of ambiguous source material belong in `rulesets/dubl-3.69/resolutions.json`.
- `shared` owns executable rules, persistence codecs, canonical catalog parsing and application mutations.
- `DublApplication` is the public state-changing boundary used by Android and Desktop.
- Platform UI code must not become a second implementation of game formulas.
- Android is the mature UX reference, not a rules authority.
- Canonical runtime catalog payloads live once under `shared/src/commonMain/resources`.
- Local user overrides remain separate from immutable canonical ruleset data.

## Current compatibility boundaries

- Product line: Fury Book 0.5 development
- Current ruleset identity: `dubl / 3.69`
- Character snapshot schema: `SnapshotCodec.SCHEMA = 11`
- Portable character transfer format: `dubl.character`, version 1
- JVM toolchain: 17
- Gradle bootstrap/wrapper version: 9.7.0
- Android compileSdk: 37
- Android targetSdk: 36
- Source namespace ownership: `com.furybook.dubl.*` / `com.furybook.ui.*` / `com.furybook.android.*` / `com.furybook.desktop.*`

Do not manually duplicate schema/version constants into behavior code; these values are listed here only as a current handoff snapshot.

## Primary verification

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlin
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions remains the authoritative networked build/release environment.

## Current engineering direction

The immediate focus is:

- keeping Fury Book product identity separate from DUBL compatibility internals

- DUBL 3.69 correctness and audited rulebook promotion
- Android/Desktop behavior parity through shared application contracts
- Development-screen performance and interaction polish
- persistence/transfer compatibility
- release reliability and packaging

