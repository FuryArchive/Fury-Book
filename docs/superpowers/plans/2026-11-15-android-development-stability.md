# Android development stability implementation plan

**Goal:** Fix the identified Android lifecycle/concurrency hazards while preserving rules, saves and the bulk-rank action in PR #31.
**Architecture:** Prepare only character-independent catalog/index data asynchronously. Build UI rules/economy from the current character revision; give each background task its own rules/planner. Add cooperative cancellation checkpoints to shared synchronous rules/planning. Cache canonical snapshot serialization per character without changing the existing immediate in-memory SharedPreferences/apply persistence contract.
**Spec:** User approved fixing all concrete risks from issue #30 investigation. The original device crash has not been reproduced; do not claim its specific cause established.
**Tech stack:** Kotlin Multiplatform, Android Compose, Robolectric API 35, JUnit and pytest.

## Constraints and rulings
- Continue the isolated PR #31 checkout; no merge, tag or release without a new publication request.
- Keep prices, requirements, health formulas and snapshot schema unchanged.
- No global shared mutable rules caches. Do not add a main-thread lock around the rules engine.
- Avoid an asynchronous save queue in this patch: it would introduce a new durability window and lifecycle contract. Remove redundant decode/unchanged-character encode work instead; changed-character encoding and assembling the final JSON remain synchronous.
- Preserve direct SharedPreferences updates from other repository instances/cloud reloads by comparing the current raw payload before returning cached state.
- No broad exception suppression; cancellation must propagate and incomplete calculations must not be published.

## Tasks
- [x] Prove sheet continuity failure with the real Android screen under ordinary/manual/slow refresh; add repository cached-read regression and checked snapshot parity cases.
- [x] Split catalog preparation from character revision; keep detail mounted on rank changes; capture immutable filter inputs; confine worker rules/planners to their task.
- [x] Add cancellation checkpoints to catalog scans, recursive rule checks and acquisition planning; test cancellation and fresh retry after cancellation.
- [x] Cache snapshot decode and per-character canonical serialization; test external writes, restart, undo, deleted/reordered rosters, invalid saved values and Unicode.
- [x] Run all Android host tests (including 20 sequential and bulk purchases), shared tests, relevant Python contracts and Android/Desktop/Web compilation; inspect diff and update PR/issue with exact evidence.

## Review focus
1. A rank update must not replace the detail sheet's semantics node or lose expanded/scroll state.
2. UI availability, displayed rank and budget must refer to the same character revision.
3. A cancelled background computation must not fill an index partially or publish stale results.
4. A cloud/other-instance save must invalidate cached reads; a deletion must not survive in serialization caches.
5. Repeated/failed purchases and Undo retain the shared atomic application behavior.

## Ledger
Baseline: b13055b on feat/bulk-development-ranks-20261115; origin/main d8d1de2; all five existing PR checks green.


## Verification ledger — implementation
- Test-only commit `dc69075` failed on the real Android screen: the first purchase recreated the detail node. The repository repeated-read test also failed. The other-instance/raw-preference and canonical round-trip baseline cases passed.
- After the UI change, all four sequential Android scenarios passed with unchanged detail node IDs through every rank 1–20. Manual mode also retains its expanded advanced section. The artificial cache delay no longer triggers a rank-dependent preparation at all.
- Added Android bulk purchase + Undo and live Constitution requirement-change scenarios. These assert the same sheet node remains and the displayed requirement/current action updates.
- UI rules, progress, planner and economy now share the current revision. Static preparation stores no character-specific mutable rules. Background filter/unlock jobs own their own rules/planner and capture immutable request values; result keys exclude stale output synchronously.
- Shared cancellation tests cover interrupted reverse-index construction followed by retry, recursive requirement cancellation and cancelled purchase immutability. Default callers retain original behavior; no coroutines dependency was introduced into the shared rules layer.
- Cached snapshot encoding delegates character serialization/normalization to the canonical codec. Byte-for-byte root output, escaped Unicode, current-character-only changes, deletion/reorder/Undo, external writes, invalid values, empty roster and failed decode recovery are covered. Actual serialization of a changed character and final JSON assembly remain synchronous; no new background-save durability window was introduced.
- Final local command: `./gradlew --no-daemon --configuration-cache :app:testDebugUnitTest :app:assemblePreview :shared:desktopTest :desktopApp:compileKotlin :webApp:compileKotlinJs` → BUILD SUCCESSFUL. Android: **17 tests**, shared: **187 tests**, zero failures/errors/skips. Optimized preview APK built. Tests are host-side Robolectric API 35, not a physical-device run.
- Relevant Python suite: **29 passed**, including the original 38 requirement scenarios, 14 bulk-rank scenarios, updated Android source contracts and catalog audit (796 entries / 28 isolated MANUAL, unchanged).
- One additional UI assertion initially expected the internal requirement string. Inspection confirmed the existing UI splits it into label and `сейчас: N`; the assertion now checks the actual displayed value and action, not an invented combined string.
- Reviewed the whole production diff for shared mutable evaluator access, stale result publication, snapshot schema/byte parity, external cache invalidation, and partial cancellation. No independent reviewer agent was available or claimed.
- Current-head platform CI is checked in PR #31. No merge, production release or claim of reproducing the reporter's original device crash.
