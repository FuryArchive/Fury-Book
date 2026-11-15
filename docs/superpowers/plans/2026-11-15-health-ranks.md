# High-rank purchase report and bulk acquisition

## Report and scope
User forwarded an Android crash report: after adding approximately four or five ranks of Incredible Health, a subsequent addition crashes repeatedly. Cross-platform impact is suspected, not established. The report also requests a button to purchase every remaining rank at once.

## Investigation
Starting main: d8d1de25aa0a5fcc722d115607022ae76485ab40 (0.7.1). The real catalog defines Incredible Health with 20 ranks, 5 XP/rank and Constitution 2. An exploratory run bought all 20 ranks sequentially, evaluated all catalog availabilities, reverse unlocks and the owned sheet at every rank, with no exception; a 64 MiB JVM heap also passed. No affected character export, application version or Android crash trace has been supplied. No attached Android device or installed emulator image is available locally. The reported crash is NOT reproduced or claimed fixed.

## Implementation decision
Use the existing `targetRank` acquisition request and existing preview/confirmation; do not loop over twenty UI clicks and do not set ranks directly. Add a separate "Взять все оставшиеся ранги" button when at least two ranks remain in Android, Desktop and Web, including both Desktop/Web inspector layouts. The preview remains the source of truth for total XP/AP and prerequisites. There is one atomic application update and one undo boundary. Preserve the ordinary one-rank action and existing budget enforcement.

## Work
1. Add failing platform-wiring contracts and runtime tests of sequential, bulk, persistence, affordability, requirements and undo with the real catalog.
2. Implement only the bulk action; do not apply speculative crash fixes.
3. Run existing requirement coverage and new runtime tests. Build all affected platform UIs in CI.
4. Open a draft PR explicitly distinguishing the delivered convenience action from the unresolved crash. Do not merge or release this follow-up without authorization.

Next evidence needed for the crash: affected `.dubl` character immediately before the failing addition, installed application version, ideally Android `FATAL EXCEPTION`/crash trace. A failing fixture must precede a claim of crash resolution.

## Verification record

- Before UI changes: 3 platform-wiring contracts failed because no bulk callback existed; all 14 shared-runtime scenarios passed. Thus these tests do not reproduce the reported Android crash.
- After UI changes: all 4 new pytest tests passed, including 14 runtime scenarios on the real 796-entry catalog with JSON round-trip persistence.
- Combined relevant regression run: `test_automatic_requirements.py`, `test_development_bulk_ranks.py`, `test_compose_development_purchase_parity.py`: 9 pytest tests passed. Existing requirement harness: 38 scenarios; audit remains 796 entries / 28 isolated MANUAL entries.
- Gradle: `:shared:desktopTest :app:compileDebugKotlin :desktopApp:compileKotlin :webApp:compileKotlinJs` succeeded. Shared test XML reports 177 tests, zero failures/errors/skips.
- Reviewed all changed callbacks, both Desktop/Web layouts, ordinary single-rank path, default budget enforcement and one-save/one-undo semantics. No separate reviewer agent or Android device UI test is claimed.
- Original reported crash remains open; no changes were made to health formulas, save formats or prices.
