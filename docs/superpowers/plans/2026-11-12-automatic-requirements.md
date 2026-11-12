# Automatic requirements implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix custom knowledge prerequisites and eliminate avoidable manual requirement checks without treating unknown conditions as satisfied.

**Architecture:** Keep requirement evaluation and acquisition planning in the shared Kotlin rules layer. Recognize deterministic rulebook expressions and count actual character skills, including dynamic specializations. Preserve explicit GM decisions and incomplete source mechanics as unresolved, with an auditable inventory.

**Tech Stack:** Kotlin Multiplatform; Kotlin CLI regression harness; Python; GitHub Actions.

**Spec:** User bug report: academic knowledge rank 5 plus custom knowledge rank 5 must satisfy Erudition's two knowledges of rank 4. Audit the complete bundled DUBL 3.69 catalog, not only this feat.

## Global constraints

- DUBL 3.69 remains the ruleset source of truth; do not invent missing rules.
- No UI redesign, extra confirmations, save-format changes, or automatic production release.
- Work in fix/automatic-requirements-20261112; leave main unchanged.
- Requirements must use ranks >= their threshold and include hidden but owned skills.
- UI categories are presentation only: Medicine is not automatically a Knowledge specialization.
- A manual requirement must never become OK solely to reduce the manual count.

## Review focus

- Distinct specializations sharing knowledge_template must count separately.
- Rank 0/3 must not qualify for a rank 4 condition; rank 5 must qualify.
- Renaming template-backed knowledge must not erase its rule identity.
- Empty characters and missing named specializations must fail known requirements, not be marked manual.
- Autopurchase and direct checks must agree; blocked plans must not charge XP or mutate the original character.

## Tasks

- [ ] Add failing behavior tests for Erudition, custom knowledge, rank boundaries, and acquisition planning; execute against unchanged production code.
- [ ] Audit all bundled development requirements and classify manual results as parser gaps, missing source data, or explicit GM decisions.
- [ ] Implement shared deterministic parsing and skill-family selection. Integrate acquisition planning without silently selecting genuinely ambiguous alternatives.
- [ ] Add regression cases for each newly recognized expression and a catalog coverage gate preventing regression.
- [ ] Run the new harness, existing tests, and platform CI. Report exact results and remaining manual requirements.
