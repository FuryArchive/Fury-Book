# Acquisition UI acceptance

## Status and scope

Android T18A connects the existing navigator and four purchase screens to the retained Shared acquisition controller. It is **not device accepted or release ready**. Production `AndroidAcquisitionRuntimeGate.production.localDurable` remains false, cloud writes remain false, and protected data is never downgraded to legacy setters. No install, device test, emulator/UTP route, process kill, live account or deployment is included in this change.

Desktop and Web UI acceptance are separate T18B/T18C deliverables. Shared tests or compiled Android instrumentation do not establish either platform's rendered behavior. T17's native IndexedDB/controller browser checks are not Compose renderer acceptance.

## Source and fixture distinctions

The accepted source catalog's Блокирование (`dubl-3.69/feat_fcdd74622d21435d`) has fixed 20 XP pricing metadata but unresolved `Q_SOURCE_PARAMETER_IDENTITY`. For a target without an instance ID the accepted full plan stops at `ChoiceRequired`, with an Unknown total and a generic explicit-selection explanation. Its real Android test must wait for that settled, disabled, no-write review; it must not display or assert a fabricated known-20 plan. Do not manufacture a successful source example by replacing that policy.

The separate debug-only `ui-synthetic-20` definition, labelled `SYNTHETIC_RULE`, tests arithmetic and input mechanics: opening 100 XP, reviewed charge 20, acknowledged remainder 80, historical receipt 20, explicit Undo back to 100. This is not source-rule acceptance. Its fixture uses the actual Android file store, isolated under `filesDir/acquisition-ui-tests/<validated run ID>`, no production preferences/cloud, and debug-only explicit capabilities. Production cannot select it via an intent or a global switch.

Fresh creation uses the accepted source policy: AP = floor(initial XP / 1000), once for the new character. It is not conversion and never retroactively adjusts old balances. Later advancement retains the stated currency; explicit source creation-only restrictions remain visible.

## Automated gates and evidence levels

- `:app:testDebugUnitTest`: host binding, protected-mode, staged-edit and actual adapter tests. Host JVM file behavior is not Android OS/device durability.
- `:shared:desktopTest --tests '*AcquisitionDependencyHintsTest'`: typed, pack-aware, deduplicated navigation hints and snapshot cache. Hints never authorize a purchase.
- `:app:assembleDebug :app:assembleDebugAndroidTest`: production/debug and genuine instrumentation source compatibility. Assembly is not execution.
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.furybook.android.acquisition.AcquisitionUiTest`: future authorized real Android input gate. **NOT RUN / NOT VERIFIED** here.
- `AcquisitionUiRestartTest`: prepared activity-recreation check, explicitly not fresh-process death/relaunch acceptance.
- `AndroidAcquisitionPresentationMatrixTest`: all 83,818 independent case IDs through accepted Shared direct/bulk/source presentation and the actual Android binding. The unchanged source expectations, including null amounts, remain independent. Price-confirmed admission blocks and compound-fixture gaps stay distinct. Historical receipt/Undo binding uses a bounded real adapter purchase/reload/Undo test; accepted unchanged financial engine coverage is reused, not presented as 16,459 new Android transactions.
- `AndroidAcquisitionPresentationOracleTest`: prepared real Compose card/preview/absent-receipt renderer source for all rows. **NOT RUN / NOT VERIFIED**. Use `oracleShardCount` and `oracleShardIndex` only for deterministic case-ID hash shards. Aggregate every unique original case ID across complete shards; a sample is not full coverage. Records with no applied command must never have an invented receipt. Successful receipt rendering is exercised by the separate actual input purchase test.

The corpus and author-approved overlay are staged by a declared Gradle task only into generated host resources and instrumentation assets. Neither the 62 MB corpus nor oracle helper belongs in main/release assets. The adapter reuses the accepted fixture/stream assertions, translates only its internal JSON boundary into a test namespace, and uses no Shared friend flags or copied acquisition implementation.

## Required device checklist

Every future execution records source/build SHA, fixture/run ID, device model/API, actual PID, operation ID, independent case ID when applicable, observations, screenshots and exact artifact hashes. Pending rows below are not implied passes.

| Case | Required observation | Current evidence |
|---|---|---|
| Synthetic purchase | Real Feats card/preview 20; confirm 80; one saved receipt; historical charge 20; Undo 100 | Input source prepared; device not verified |
| Actual catalog Block | Fixed20 source metadata is distinct from settled ChoiceRequired/Unknown full plan; disabled confirmation; unchanged 100 | Input source prepared; device not verified |
| Repeated confirm | Rapid taps across an IO barrier and reopen keep one ID/debit/receipt | Disabled-after-commit source prepared; rapid in-flight case still pending |
| Back/cancel/scrim/swipe | Before dispatch no write; after dispatch hide retains uncertainty, not a promised rollback | Cancel/back source prepared; full system gestures pending |
| Stale review | External canonical change leaves review open/stale; explicit refresh; no new charge | Real adapter mutation plus input source prepared; device not verified |
| A to B | Actual selection hides A's plan; old callback cannot purchase B; retained A operation survives | Input source prepared; delayed in-flight/account-generation extensions pending |
| Manual correction | Explicit signed XP and AP, reason, separate preview and confirmation; no automatic refund | Input source prepared; device not verified |
| Fresh funding | 2500 XP previews 2 AP; one new opening; old character remains unchanged | Input source prepared; device not verified |
| Each kind/choice | Attributes, skills, specialization/new versus existing instance, developments, school, mana, Chi, spell; correct access/payment and distinct-cry proofs | Shared/controller and host evidence separate; exhaustive live journeys pending |
| Storage faults | Before replace remains 100; after replace uncertainty retains same ID; reconcile establishes at most one 80 | T13 adapter evidence reused; new UI fault/barrier journeys pending |
| Activity recreation | Resume exact root and receipt, never reseed | Source prepared; device not verified |
| Fresh process/offline | Fresh OS process restores acknowledged 80, Undo 100, offline status stays separate from local success | Manual gate pending; activity recreation is not a substitute |
| Source errors | Unknown cost/condition never 0/yes; no checkbox for unresolved rules | Host corpus plus prepared renderer source; live device not verified |
| All-row rendering | Real production surfaces and semantics for all 83,818 IDs with complete shard accounting | Prepared only; no rendered result claimed |

## Fresh-process/manual procedure

Only on a separately authorized, identified clean test device, using the non-exported debug fixture host:

1. Record build SHA/device/API and a validated unique fixture run ID. Seed a fresh fixture exactly once; never clear app data, uninstall or overwrite an existing fixture to obtain a passing state.
2. Drive the actual synthetic purchase through the UI. Export the fixture checkpoint with operation ID, envelope hash, balance and receipt. Record the process PID and screenshot of the acknowledged result.
3. With explicit runtime authority, end that test process and launch a separate instrumentation/process phase using the same `fixtureRun` and `resumeFixture=true`. The resumed host refuses missing heads instead of creating an opening.
4. Record a different PID and reload 80 XP with the same receipt and no reseeding. Use real Undo input and observe the inverse receipt and 100 XP.
5. Repeat only the authorized before-dispatch/before-replace/after-replace scenarios with fixture-scoped adapter barriers. A killed instrumentation process cannot report its own later successful resumption. Keep pre-kill and resumed evidence as separate process records.
6. Keep original fixture files for evidence. Cleanup is limited to verified task-owned fixture paths under applicable deletion authority; never production preference/data paths.

## Readable behavior

Cards quote without replacing a retained command. The active dialog renders exact Shared prices, planned versus acknowledged-before requirements/proofs, excluded targets versus genuinely owned rows, typed choices, explicit external evidence, errors, retry/reconcile and saved receipts. History distinguishes the balance at the transaction from today's balance. Source-unresolved requirements never receive a generic “force” checkbox. Financial edits use typed acquisition/manual review; enumerated descriptive/resource edits stage once on detached state and require explicit confirmation. Protected read-only capability refuses staging and confirmation.

The development list prepares availability off the UI thread for ALL/PLAN/OWNED and selected details. Generation-specific immutable row maps have no main-thread fallback evaluation. Reverse links come only from typed, pack-aware requirement references with deterministic deduplication; they are navigation hints, not eligibility.

## Observed host matrix, 2026-10-13

The dedicated Android production-binding run completed successfully: 83,818 unique case IDs exactly equal the independent corpus, 119,121 model observations, zero failures, no missing/unexpected IDs or duplicate ordinal mismatch. It retained 80,416 exact direct/bulk comparisons. After the unchanged approved 3,922-case overlay, 46,073 source-price rows were confirmed and 37,745 remained source-unverified. Confirmed rows retained their separate classifications: 27,606 admission-blocked, 2,008 compound-fixture gaps, 16,459 receipt-eligible with unchanged financial evidence reused. This is host presentation-data evidence, not Android renderer or transaction acceptance.

The reviewed correction round has a real old-route regression: a runtime-filtered whole-character manual edit changed canonical Chi unintentionally. The canonical-base transform passes the same preservation assertions after correction. Focused host tests also cover typed actual/required values, concurrent exact-snapshot hint cache reads and success-only import refresh callbacks. Device execution remains not verified.
