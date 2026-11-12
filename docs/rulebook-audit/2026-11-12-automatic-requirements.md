# Automatic prerequisite checks — 2026-11-12

## Scope and method

This change fixes the reported Erudition/custom-Knowledge bug in shared Kotlin rules and acquisition planning. It does not change UI, saved-character schemas, XP prices, canonical catalog contents, or release versions.

The coverage harness loads every bundled `development_*.json` in the DUBL and Chi packs (796 unique entries at the starting commit). It evaluates each entry on an empty character after removing inherited access checks, `incomplete`, `mechanicsConflict`, and `perfectRoot` flags. This isolates prerequisite syntax and unresolved source references. It is **not** a count of all manual checks a populated character can encounter: inherited requirements, descriptor ambiguity, incomplete benefits, and GM decisions can still produce MANUAL.

The initial unchanged production code returned MANUAL for 60 entries in this audit. The first parser implementation reduced that to 38. Explicit catalog spelling aliases are covered by a subsequent regression run. The current exact residual inventory is `tools/tests/fixtures/manual_requirement_inventory.json`; CI checks both the inventory and minimum catalog size, so new manual cases or accidental content removal cannot silently pass.

## Implemented

- Count distinct Knowledge specializations at rank >= the threshold, including custom, hidden, and renamed template instances.
- Preserve built-in Knowledge identity after cosmetic renaming. Do not use UI category as rule identity.
- Count duplicate copies of the same specialization once, while retaining separate specializations of the same template.
- Recognize Knowledge, Craft, Performance and Profession families, named/missing specializations, slash alternatives and postfix `(Любое)` syntax.
- Parse `или` and `и/или` outside parentheses; do not copy a skill's trailing numeric rank onto an unrelated one-rank feat.
- Resolve explicit aliases for Джит Кун До / Джит Кун-До, Дэрвиш / Танец ветра (Дэрвиш), and Открытая Ладонь / Школа Открытой Ладони. Exact local names take precedence, and the separate Танец ветра technique remains distinct.
- Reuse the same prerequisite decisions in acquisition planning. Offer existing candidate choices, buy only missing ranks, and create a specialization only when its name is explicitly required.
- Validate projected targets with the shared rules before declaring a plan applicable. Preserve insufficient-XP rejection, source-rank contradictions, explicit GM decisions, and the original character during planning.

## Remaining work, not silently bypassed

The checked-in inventory gives IDs, names and concrete reasons for each residual isolated requirement. They include unresolved catalog references (such as Ярость, Джеб and Пляшущий ветер), weapon-group/specialization requirements and synergy eligibility that need additional rule-backed representation, and requirements asking rank 2 of catalog entries capped at rank 1. Тысяча порезов literally references `Штука с парированием`.

These are open source/data/model reconciliation tasks, not a claim that further automation is impossible. Similar names are not sufficient evidence for substituting a different ability. This PR deliberately does not return OK or generic FAIL for unknown conditions merely to make the manual count zero.

## Verification

Behavior tests were first committed and executed against unchanged production rules: they failed on the reported knowledge case and other grammar gaps. After the first implementation all 23 main scenarios passed. A second test-only commit established failures for explicit catalog aliases and renamed built-in Knowledge identity before those fixes.

Run:

```sh
python3 tools/tests/test_automatic_requirements.py --audit
python3 tools/tests/test_automatic_requirements.py
./gradlew :shared:desktopTest :desktopApp:compileKotlin
```

The dedicated Rules requirements workflow runs the first two commands. Existing Android, Linux, Windows and Web CI remain enabled. Exact final run results are recorded in PR #29; test status must be checked for the current head rather than an earlier commit. No manual device UI test or production deployment is claimed.
