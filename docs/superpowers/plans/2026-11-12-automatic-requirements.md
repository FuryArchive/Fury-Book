# Automatic requirements implementation plan

**Goal:** Fix custom knowledge prerequisites and reduce avoidable manual requirement checks without treating unknown conditions as satisfied.

**Architecture:** Requirement evaluation and acquisition planning share deterministic Kotlin skill selectors. UI, save schemas and canonical catalog contents remain unchanged.

**Tech Stack:** Kotlin Multiplatform; Kotlin CLI regression harness; Python; GitHub Actions.

**Spec:** Academic Knowledge rank 5 plus a custom Knowledge rank 5 must satisfy Erudition's two knowledges of rank 4. Audit all bundled development requirements, not only this feat.

## Constraints retained

- DUBL 3.69 is the source of truth; unresolved source identities and contradictory ranks are not guessed.
- No UI redesign, additional confirmations, save-format changes or production release.
- Work in `fix/automatic-requirements-20261112`; main remains unchanged.
- Count ranks >= the threshold, including hidden learned skills.
- UI grouping is not rule identity. Medicine is not a Knowledge specialization.
- Never turn MANUAL into OK merely to reduce the count.

## Implementation and evidence

- [x] Add and execute failing behavior tests on unchanged production code (commits `7cdee0a`, `b1ce0cd`).
- [x] Audit all 796 bundled development entries. Initial isolated-prerequisite manual count: 60.
- [x] Implement shared skill-family/count parsing and acquisition planning; first 23 scenarios passed on `741e122`.
- [x] Establish failing edge cases for catalog aliases and renamed built-in Knowledge on `d832c77` before fixing them.
- [x] Verify all 38 scenarios and the coverage gate on `b596ae9`: Rules run `43218821811`, job `131184813910`, success.
- [x] Inventory every remaining isolated prerequisite case with a concrete reason. Verified count: 28. New or silently removed manual cases fail the coverage gate.
- [x] Review the complete PR diff for unintended edits. Only shared rule evaluation/planning, tests, CI and documentation changed.
- [ ] Confirm all platform workflows for the final PR head. The current-head results are recorded in PR #29 rather than inferred from older green builds.

## Open work beyond this patch

Zero manual checks has not been achieved. The 28-entry inventory is a tracked remainder, not a claim that further automation is impossible. It includes unresolved named catalog references, weapon-group/specialization and synergy state, and requirements for ranks above current catalog maxima. Inherited conditions, incomplete mechanics, spell-descriptor ambiguity and explicit GM decisions are outside this isolated-prerequisite count.

See `docs/rulebook-audit/2026-11-12-automatic-requirements.md` and `tools/tests/fixtures/manual_requirement_inventory.json`. No manual device UI validation or production deployment is claimed.
