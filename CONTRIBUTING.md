# Contributing to Fury Book

Fury Book is a cross-platform tabletop character/rules companion. DUBL 3.69 is the currently shipped ruleset, whose executable behavior must stay aligned with its source material and the shared Android/Desktop application contract.

## Source-of-truth order

When changing behavior, use this order:

1. DUBL 3.69 rulebooks and approved module books
2. explicit tracked resolutions in `rulesets/dubl-3.69/resolutions.json`
3. shared rules/application code and typed golden scenarios
4. platform UI behavior

Android is the mature UX reference. It is not a rules authority when it conflicts with the books.

## Change boundaries

Prefer changes in `shared/` for:

- game formulas and rule evaluation
- application mutations
- persistence codecs/contracts
- catalog parsing
- cross-platform behavior

Keep platform modules focused on presentation, platform services and adapters.

Do not duplicate a formula in Android and Desktop to make the screens agree.

## Namespace ownership

Keep package ownership aligned with module responsibility:

- shared DUBL behavior: `com.furybook.dubl.*`;
- shared Fury Book UI primitives: `com.furybook.ui.*`;
- Android-only code: `com.furybook.android.*`;
- Desktop-only code: `com.furybook.desktop.*`.

Do not place platform packages in `shared/commonMain`. Do not rename compatibility identifiers such as the Android `applicationId`, DUBL ruleset ID, transfer format, or local data identity as part of ordinary namespace cleanup.

## Fury Content Packs

Canonical DUBL runtime catalogs belong under `shared/src/commonMain/resources/fcp/dubl-3.69/content` and must be declared by that pack's `manifest.json`.

Do not add new loose canonical catalog files under `shared/src/commonMain/resources`, Android assets, or Desktop-only resources. Format-level FCP code belongs in `com.furybook.content` and must remain ruleset-agnostic; DUBL interpretation belongs in `com.furybook.dubl.*`.

When adding or moving pack content, update the FCP contract tests and deterministic pack build. See `docs/FCP.md`.

## Rulebook and catalog changes

For rulebook-driven changes:

- preserve provenance
- do not silently resolve ambiguous wording
- add/update diagnostics or `resolutions.json` when interpretation is required
- update the ruleset baseline only after reviewing the generated diff
- update shared tests/golden scenarios when executable behavior intentionally changes

The rulebook DOCX files are development inputs and must not be added as runtime assets.

## Verification

Run the relevant gates before opening a PR.

Shared/Desktop:

```bash
./gradlew :shared:desktopTest :desktopApp:compileKotlin
```

Android:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

For rulebook import changes, also run the importer validation/baseline checks documented in the README.

If a full Gradle build cannot be run locally because dependencies are unavailable, state that explicitly in the PR and rely on GitHub Actions for the authoritative networked compile.

## Pull requests

Keep each PR focused. In the description, include:

- what changed
- why it changed
- affected platform(s)
- rules/source references when behavior changed
- migration/persistence impact
- tests or verification performed
- screenshots for visible UI changes

Avoid unrelated formatting churn in behavior-changing PRs.

## Compatibility expectations

Treat these as compatibility boundaries unless a deliberate migration is included:

- character persistence schema
- ruleset identity
- Android/Desktop application semantics
- canonical catalog IDs
- typed golden scenarios
- public release packaging

