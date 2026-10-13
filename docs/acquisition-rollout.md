# Acquisition rollout and recovery

Status: working implementation, not release authorization. Production cloud capabilities remain disabled. This procedure separates source backups from actual character-data backups.

## Before migration or upgrade

1. Stop every writer for the selected data root. Copy the whole data root, including characters.json, sheet-extras.json, canonical envelopes, conflict archives and referenced assets. Include externally stored portraits/assets separately. Do not mistake a Git source archive for a character-data backup.
2. Keep original bytes and record hashes. Publish a backup only after copying and verifying; use a new destination, not an overwrite of the original.
3. Reopen a separate restored root in the compatible current client and compare character identity, current XP/AP, opening provenance, receipts, outbox, notes and assets. Keep the original backup unchanged.
4. Never reconstruct historical purchases, recalculate their price or refill an existing opening during migration. An unprovable remaining balance requires explicit confirmation. Do not remove provenance to bypass an import conflict.

## Capability rollback

- Local canonical writes require local durability and a writable recognized format. Cloud synchronization additionally requires verified server ledger-V2 guards.
- Removing a capability must disable already-open confirmations, not merely hide new buttons. Read/export of a recognized canonical envelope remains available without converting it to a legacy editable snapshot.
- Any false cloud-capability tuple must stop before store reads/writes, conflict archiving, ID allocation or RPC invocation. Future-format bytes are retained unchanged.
- An explicitly protected application blocks legacy writers even without canonical collaborators. There is no fallback from failed canonical storage to a legacy save.
- A legacy client may change its isolated source files, but cannot overwrite the migrated canonical head. Such source drift makes the original migration read-only until deliberately resolved.

## Restore versus independent copy

Restoring the same migrated identity requires its captured original characters and extras files. Missing or changed originals cause LegacyImportConflict; they must not be synthesized. A new independent canonical copy is different: it has a new identity and a validated zero-cost copy boundary, keeps ancestry and historical receipts, and does not depend on another device's legacy files.

Replaying a saved operation must return its original receipt before consulting today's catalogue. Restoring does not constitute another purchase, and new catalogue prices must not alter the original charge.

## Characterization boundary

AcquisitionRolloutTest covers the seven false capability tuples against a paid head and a future-format head, direct RPC refusal, thirteen protected legacy actions, and dynamic withdrawal of local write capabilities with read/export preserved.

DesktopAcquisitionRolloutTest covers an original migrated three-file fixture, staging/forcing/hash checking into absent backup and restore roots, reopening in a separate JVM, exact receipt replay without current-catalog reads, four missing/changed-source controls, and an old writer changing only its legacy branch.

The fixture is a bounded Linux characterization. It is not a complete user-data backup utility, a power-loss proof, a device test, managed-server validation, or a guarantee for every filesystem. It does not close the native-evidence/report integration, browser/device interaction, source-rule uncertainty or deployment gates. The six named characterization methods passed together on 2026-10-13 in the scoped desktop JVM run. This is not a full regression or a live-platform acceptance result.

## Release remains blocked

Do not enable production capabilities based on these tests alone. Source completeness, native execution acceptance, platform interaction, server compatibility and authorized deployment remain separate requirements. Keep raw unknown/newer data available for export and recovery when any gate is unavailable.
