# Acquisition cloud format: local server prerequisites

Status: unreleased, locally tested prerequisites. Production `cloudLedgerV2` remains false. No migration in this document has been deployed. Local SQL PASS does not authorize deployment or establish staging compatibility, production capacity, or old-binary compatibility.

## Migration and observed baseline

The official Supabase CLI 2.120.0 generated:

`supabase/migrations/20261012222846_acquisition_ledger_v2.sql`

All older migrations are unchanged. The tested baseline was captured read-only from the deployed application schema: 40 structural metadata sections, all 17 function bodies, exact `auth.uid()` / `auth.role()` helpers, 35 `auth.users` columns, application roles, default privileges, grants and RLS. The live inventory had no V2 RPC, receipt-identity uniqueness, durable min-writer floor, or tombstone guard. Repository migration-history drift is not evidence that these guards are deployed.

The disposable runtime is schema-only PostgreSQL 17.6.1.178-r0. The managed engine build was .171. Ten inbound Auth-service tables, complete role topology, GoTrue, PostgREST, JWT cryptography, lifecycle hooks and managed statement/time limits were not reproduced. Tests use synthetic claims and `SET LOCAL ROLE authenticated`; fixture setup alone uses the local administrative role. Application `postgres` still cannot CREATE in public. Tests roll back their migration, users, profiles, rows, receipts and archives.

## Revised wire contract

The RPC signature is unchanged:

`acquisition_sync_v2(p_character_id text, p_expected_revision bigint, p_operation_ids jsonb, p_envelope jsonb, p_writer_version integer, p_resolution jsonb default null)`

Writer version is exactly 2. One saved ordinary tip operation is submitted per request. Expected revisions, all money and response revisions remain decimal strings on the client wire. The SQL argument for the expected revision is bigint.

`p_envelope` is exactly one JSONB field: `{"payloadCanonical":"<exact canonical durable wire text>"}`. Missing, extra and wrongly typed wrapper fields are refused. Ordinary and dedicated resolution requests use this same wrapper. This is an explicitly approved T16 correction to the unreleased T15 DTO, not a compatibility story for an already deployed API. Native and Web adapters consume the shared DTO; neither has a separate relaxed encoder.

Why retain text: PostgreSQL JSONB changes number lexemes and rejects lone UTF-16 surrogate escapes. PostgreSQL json accepts the input but its native field/key access can also reject nested lone surrogates or NUL, even when extracting an unrelated field. The server first validates canonical lexical structure, retains raw text, and uses bounded lexical field access only when native access cannot represent it. It does not substitute Unicode, serialize Double values, or reconstruct an acknowledgement from JSONB.

Lossless snapshot admission also rejects canonical Int32 signed zero/out-of-range tokens, duplicate or filtered keyed/set records, and identities which the existing projection decoder would trim or discard. It preserves manual values, array order and Double tokens. Legacy admission derives the original ordered ownership records/stage from the captured supported backup, pins origin/mapping/candidate/deterministic identities, and follows exact inherited/copied holding mutation and Undo proofs, including removals. Nullable free-text opening reasons retain NUL and lone-surrogate content.

Canonical validation checks duplicate decoded keys, UTF-16 key order, exact escapes, number grammar, required/unknown fields, typed snapshots, notes, instances, receipt completeness/revision chain/financial results, copy boundaries, pooled proof content identity and closure, immutable selected prefixes, exact archive identities and dedicated choice scope. It retains v1 inline adoption evidence unchanged. Legacy openings retain unknown history and the exact captured source/fingerprint/baseline. No server rulebook-price recomputation or new price is introduced.

The server stores and returns byte-identical `payloadCanonical` text:

- `ack`: operationId, payloadCanonical, serverRevision
- `conflict`: serverCanonical, serverRevision
- `identityConflict`: reason, with no acknowledgement and no durable partial write

Dedicated `p_resolution` has exactly choice, archiveId, expectedServerRevision. It must match the saved adoption tip, immutable archived branches, confirmed revision and actual current server canonical branch. `pendingResolution`, outbox, storageRevision and other local metadata never enter the durable wire. Device-local portrait data is excluded; noteEntries are required. The unchanged public legacy extras projection remains unchanged.

## Writer and reader boundary

| Path | Local migration behavior |
| --- | --- |
| acquisition_sync_v2 | Owner-scoped lock/CAS, immutable receipts and operation IDs, exact retry acknowledgement; version declaration alone is insufficient |
| acquisition_get_v2 | Exact reader 2; returns complete raw text and string revision, including explicit deleted state |
| acquisition_delete_v2 | Owner/CAS/version gated; retains raw envelope, receipts, archives and min-writer floor in a durable tombstone |
| save_web_character, native_force_character, delete_web_character | Refuse a migrated identity, including force/delete and full-envelope attempts; legacy CAS cannot be skipped with NULL |
| native_sync_snapshot, native_link_device | Explicit upgrade refusal for an owner with any durable floor; fresh new-format data cannot be imported as legacy |
| save_web_state | Refuses selecting a migrated identity; NULL cannot skip its legacy CAS |
| get_web_bootstrap | Explicitly refuses an owner with a migrated identity, including tombstones; does not return a stripped editable schema-11 representation |
| campaign_get_dashboard | Checks GM authorization first, then explicitly requires a V2 campaign reader when a linked character is migrated |
| campaign_get_dashboard_v2 | Exact reader 2 plus existing GM authorization; returns complete raw acquisition text and explicit legacy/acquisition classification |
| Other campaign RPCs | Retain owner/member authorization; change campaign metadata/membership, not character data or ledger; join/set-character cannot link another owner's identity |
| Direct web_characters, user_state, sync_history | Authenticated/anonymous direct access revoked; the character row trigger additionally rejects downgrade, identity reassignment and new-format bypass |
| Direct profiles, web_state | Surplus mutation/TRUNCATE grants closed; established owner-authorized RPCs remain |
| Private floors, operations and archives | Private schema, RLS with no application policies, no application table/execute privileges; explicit owners/default-ACL cleanup |

Unique operation identity is `(user_id, character_id, operation_id)`. An exact retry returns its original acknowledgement despite a stale expected revision. A different valid body using the same accepted identity is refused. Ordinary writes extend the immutable server ledger; explicit branch selection does not add two offline balances. Delete/recreate cannot erase the floor. A deliberate local copy requires a new character ID and a zero-cost import boundary with validated ancestry. The same textual character/operation ID under a different authenticated owner is independent.

Authenticated TRUNCATE grants in the captured baseline were SQL-credential-only risks: no exposed raw-SQL/TRUNCATE RPC was demonstrated, and ordinary HTTP DELETE is not TRUNCATE. No live exploitation or grant change was performed.

## Verification and reproduction

`supabase/tests/acquisition_v2.sql` is the baseline RED/guard suite. `acquisition_v2_green.sql` applies the actual migration inside the same rollback transaction. Independent rollback batches cover hostile lexical/proof/shape inputs (`acquisition_v2_edges.sql`), every supported operation type plus historical v1 and legacy migration (`acquisition_v2_types.sql`), and resource/representation refusal (`acquisition_v2_resources.sql`). The scoped admission regressions are `acquisition_v2_fix1.sql`, with separately public-produced legacy-copy/confirmation, paid/manual removal/Undo, old-schema and manual-value positives, plus public-codec-rejected representation/provenance variations and authorization-before-reader checks. The final batches must be executed against a disposable copy of the captured schema, never production or user rows.

The full expanded admission aggregate exceeded the disposable launcher's unchanged 120-second per-file timeout. Its complete assertion matrix is also partitioned into `acquisition_v2_fix1_negative_a.sql`, `acquisition_v2_fix1_negative_b.sql`, `acquisition_v2_fix1_positive.sql`, `acquisition_v2_fix1_atomic.sql` and `acquisition_v2_fix1_campaign.sql`; run all five independent rollback batches. These partitions retain every case and complete proof, with no admission-oracle, valid-history cap or launcher-timeout change. Aggregate fixture duration is not an individual-request or managed-gateway latency measurement.

Positive fixture bytes are emitted by `tools/tests/kotlin/AcquisitionServerFixtureProducer.kt`, compiled as a separate module against real shared production classes and runtime dependencies, with no friends/stubs/joint production compilation. The historical v1 fixture converts branches captured from the public producer and is validated by the public codec. Negative fixtures are generated by `tools/tests/build_acquisition_server_negative_fixtures.py`; its independent test-only renderer first byte-checks every unmodified positive wire, then mutates only negative cases. Supply the actual production classpath when compiling the producer. Run the utility with that classpath and shared resources, capture default stdout to `acquisition_v2_fixtures.sql`, and `--fix-1` stdout to `acquisition_v2_fix1_fixtures.sql`, then run the Python generator. `--check-admission acquisition_v2_fix1_invalid_fixtures.sql` verifies the hostile cases fail both the actual public wire codec and ACK consumer; `--check-refusals <captured-reason-file>` tests unchanged raw/outbox across retries. Original 26 wire/DTO fixture bytes remain unchanged.

Real coordinated Gradle verification ran Shared desktopTest, Desktop compileKotlin, Android testDebugUnitTest/assembleDebug, and Web compileKotlinJs with rerun-tasks/no-build-cache. It passed 417 Shared and 7 Android unit tests on frozen source. Public native/Web transport tests were included, and the Web adapter compiled. Public protocol refusal tests separately verify raw envelope/outbox preservation across repeated non-ack responses. These are not Android-device, Desktop-renderer, IndexedDB/browser-lifecycle or live-HTTP acceptance tests.

## Explicit release gaps

1. **Resource limits are prototype compatibility limits, not approved rollout limits.** The unreleased SQL validator refuses over 8 MiB of raw canonical text, nesting deeper than 64, more than 2,048 ledger entries or more than 512 pooled proofs. These conservative sentinels have no measured managed-capacity basis. The byte-size check runs before costly parsing. Depth is checked while lexing; ledger/proof count checks run after lexical/shape parsing and do not guarantee cheap processing. Count-refusal tests intentionally use malformed oversized collections to prove guard order, not valid large-history compatibility. No evidence is truncated, dropped or normalized. Refusal returns identityConflict and writes no floor, operation, archive or public row; the unchanged public protocol retains its local raw data and queue. Valid larger histories can remain blocked and require a reviewed compatibility/performance decision before release.
2. **Performance is not production-certified.** Initial genuine proof validation exceeded the unchanged 120-second disposable launcher timeout. After lexical/proof traversal improvements, a synthetic three-wire closure/copy diagnostic completed in about 20.2 seconds. A later oversized test-fixture construction also exceeded 120 seconds and remains recorded separately. These synthetic administrative/runtime timings are not managed-role/PostgREST latency limits. Local PASS does not establish acceptable deployment latency.
3. **Cross-platform Double serialization is a proven shared-code blocker.** The independent real Kotlin 2.4.20 JVM/JS probe found differing spellings, including JVM 0.0/-0.0/1.0E-10 versus JS 0/0/1e-10, and strict-codec interop failures. T16 preserves whatever canonical bytes the valid producer supplied and does not impose JVM-only Double spelling. A separately reviewed shared serialization/old-v1 history-preservation repair remains required; T16 does not silently redesign it.
4. **Identifiers and gateways remain bounded.** NUL/lone-surrogate note/name/other non-identity content round-trips exactly in the tested raw wrapper. An identifier which cannot be represented as a PostgreSQL text/RPC parameter fails explicitly instead of being normalized or acknowledged. The local SQL gateway/queue tests do not establish real PostgREST behavior for such parameters.
5. **Deployment and consumer rollout remain separate.** Old binaries, all negotiated discovery/read adapters, authenticated live gateway behavior, concurrency under real services, staging grants/RLS, managed resource limits and the eventual capability switch need independent evidence and explicit deployment authority. Deleting an Auth account/admin fixture is outside the application-character tombstone guarantee. Production cloudLedgerV2 stays false throughout.
