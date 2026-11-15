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
