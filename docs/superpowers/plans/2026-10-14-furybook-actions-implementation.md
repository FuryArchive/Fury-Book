# Actions implementation

Design approved by the owner on 2026-10-14. Execution stays in the existing working branch and current session; no coding workers or production deployment. This document is an implementation sequence, not a completion claim.

## First source-backed slice

Use physical-attribute overexertion: corebook P125 spends one endurance, takes no action, can be used outside the owner's turn; P127 grants one advantage die to Strength, Dexterity, Speed or Constitution. P14781 forbids voluntary endurance expenditure while Weak. No automatic restoration, turn refresh, Sprint duration or Weak/Exhaustion stacking is inferred. Attribute checks only initially, not every skill or attack using a physical attribute. A successful resource debit is not a successful roll.

## 1. Pure transitions and focused contracts

Add `shared/commonMain/.../dubl/actions/GameplayActions.kt` and common tests. Explicit request identity, character/revision preconditions, source version, pending physical check and typed submitted result. Preview is pure. Start debits once; replay returns the saved outcome before new eligibility checks. Same ID with different input conflicts. Resolution requires the existing pending action and a new operation ID, and does not debit again. Changing presentation mode is not a command. Result input and modifiers are distinct; no automatic mixed advantage/disadvantage cancellation.

Keep this initial state machine disconnected from application writes. Test blocked/missing-resource starts, Weak and unknown condition semantics, exact physical attribute set, preview immutability, duplicate start/result, ID collision, stale revision, second start while pending, cancellation not silently refunding, and catalog version pinned at start. Use actual Kotlin execution when compiler access is available; a source scan is not a passing runtime test.

## 2. Canonical integration, not a sidecar

Introduce explicit format 2 in `AcquisitionEnvelope.kt`/`AcquisitionEnvelopeCodec.kt`: gameplay state and independent gameplay receipts belong in the same atomic StoredCharacter as endurance. Preserve acquisition ledger, receipts, ancestry and outbox byte semantics. Resources remain authoritative on the character; do not add a second mutable endurance balance. Codec validation checks character identity, resource consistency, operation IDs and revision relations.

Format 1 stays readable without silently becoming writable format 2. A separate, deliberate migration records backup evidence and is capability-gated. Old clients must reject new bytes before any write. Audit every constructor, copy, mutation projection, transfer, backup restore and sync encoding. Existing cloud write capabilities stay false; a local format-2 path must not reach the legacy sync encoder.

## 3. Durable service and Undo

Use the existing whole-envelope CAS contract. On uncertain commit, reread with the same request ID. Conflict never silently rebases a confirmed action. Undo targets the last independent local gameplay receipt only, verifies its after-state, restores exact before-state atomically and appends a reverse receipt. It cannot remove receipts, resurrect consumed IDs, undo an acquisition, or rewind externally synchronized consequences. A resolved action depends on its start: do not undo start alone.

Focused persistence tests cover separate store instances, process restart, before/after-commit failures, replay after Undo, unrelated resource mutation, migration, unsupported writers and preservation of all acquisition fields. Do not substitute same-process reopen for process-restart evidence.

## 4. Events and effects

Add explicit own-turn, other-turn, round, scene and rest event types with idempotent IDs. Only source-declared anchors expire effects. No implicit healing, AP/reaction refresh, progression of time on view switches, or retroactive consumption of already processed events. Unknown policies block only dependent effects with source diagnostics. Sprint remains disabled until its complete timing and interaction policy is determined.

## 5. UI and acceptance

Expose one shared preview/start/resolve/Undo service on Android, Desktop and Web. Show cost before confirmation, pending check after reopening, exact bonus sources and rejected operations. Physical and built-in dice feed the same typed resolver. Existing condition projections still apply after endurance reaches zero; unresolved overlaps stay unavailable rather than guessing.

Run scoped tests while implementing, then aggregate regression at the durable vertical-slice milestone. Real device/browser acceptance, offline/restart behavior and server compatibility are separate gates. If a network or runtime restriction blocks a gate, report it and leave that gate open. Preserve source checkpoints in the authorized backup branch without touching main or enabling production flags.
