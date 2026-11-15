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
- [ ] Prove sheet continuity failure with the real Android screen under ordinary/manual/slow refresh; add repository cached-read regression and checked snapshot parity cases.
- [ ] Split catalog preparation from character revision; keep detail mounted on rank changes; capture immutable filter inputs; confine worker rules/planners to their task.
- [ ] Add cancellation checkpoints to catalog scans, recursive rule checks and acquisition planning; test cancellation and fresh retry after cancellation.
- [ ] Cache snapshot decode and per-character canonical serialization; test external writes, restart, undo, deleted/reordered rosters, invalid saved values and Unicode.
- [ ] Run all Android host tests (including 20 sequential and bulk purchases), shared tests, relevant Python contracts and Android/Desktop/Web compilation; inspect diff and update PR/issue with exact evidence.

## Review focus
1. A rank update must not replace the detail sheet's semantics node or lose expanded/scroll state.
2. UI availability, displayed rank and budget must refer to the same character revision.
3. A cancelled background computation must not fill an index partially or publish stale results.
4. A cloud/other-instance save must invalidate cached reads; a deletion must not survive in serialization caches.
5. Repeated/failed purchases and Undo retain the shared atomic application behavior.

## Ledger
Baseline: b13055b on feat/bulk-development-ranks-20261115; origin/main d8d1de2; all five existing PR checks green.
