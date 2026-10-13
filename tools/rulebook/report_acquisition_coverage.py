"""Join exact acquisition obligations and evidence; never manufacture execution.

Ordinary CLI success means integrity, not release readiness. The immutable price
oracle is independent of the runtime. Missing actual evidence is never PASS.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import subprocess

from tools.rulebook.acquisition_coverage_records import (
    read, semantic_sha as digest, BASE_HASHES, MONEY_FIELDS, load_source_rules,
    load_effective_expected_prices, authority_revision, resolution_revision,
)
from tools.rulebook.validate_acquisition_matrix import validate_matrix
from tools.rulebook.acquisition_audit_projection import validate_projection

SOURCE_FACETS = ('price', 'admission', 'rankMaximum', 'discount', 'identity', 'option')
PLATFORMS = ('ANDROID', 'DESKTOP', 'WEB')
STAGES = ('card', 'preview', 'apply', 'ledger', 'restart', 'undo')
REQUIRED_GATES = (
    'source_universe_integrity', 'source_rule_complete', 'runtime_case_acceptance',
    'platform_presentation_android', 'platform_presentation_desktop', 'platform_presentation_web',
    'platform_live_flow_android', 'platform_live_flow_desktop', 'platform_live_flow_web',
    'durability_android', 'durability_desktop', 'durability_web', 'shared_platform_semantics',
    'migration_compatibility', 'standard_build', 'server_writer_reader_guards',
    'rollout_false_capability_tuples', 'rollout_protected_old_writer_rollback',
    'rollout_canonical_fresh_root_backup', 'rollout_rollback',
)


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def file_sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_path(path, root):
    return path.relative_to(root).as_posix()


def source_inventory(root):
    roots = ('shared/src', 'app/src', 'desktopApp/src', 'webApp/src', 'rulesets',
             'tools/rulebook', 'tools/tests', '.github/workflows')
    paths = {p for folder in roots for p in (root/folder).rglob('*')
             if p.is_file() and p.suffix in ('.kt', '.json', '.py', '.kts', '.yml')}
    paths.update(root/name for name in ('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
                                      'shared/build.gradle.kts', 'app/build.gradle.kts',
                                      'desktopApp/build.gradle.kts', 'webApp/build.gradle.kts'))
    return {source_path(p, root): file_sha(p) for p in sorted(paths)}


def contains_type(value, kind):
    if isinstance(value, dict):
        return value.get('type') == kind or any(contains_type(v, kind) for v in value.values())
    return isinstance(value, list) and any(contains_type(v, kind) for v in value)


def load_report_inputs(root, evidence_paths=()):
    root = Path(root).resolve(); data = root/'rulesets/dubl-3.69/acquisition'
    for name, expected in BASE_HASHES.items():
        require(file_sha(data/name) == expected, 'immutable_input_changed:'+name)
    source = read(data/'source_questions.json'); corpus = read(data/'coverage_manifest.json')
    original = read(data/'expected_prices.json'); rules = load_source_rules(root)
    require(not validate_matrix(original, corpus), 'invalid_independent_matrix')
    effective = load_effective_expected_prices(original, corpus, rules, read(data/'author_expected_price_updates.json'))
    cases = effective['cases']; raw = {c['caseId']: c for c in original['cases']}
    actual = {}; packs = {}
    for pack in ('dubl-3.69', 'dubl-chi-3.69'):
        folder = root/'shared/src/commonMain/resources/fcp'/pack
        payload = read(folder/'content/acquisition_catalog.json'); packs[pack] = payload
        require(any(e['kind']=='dubl.acquisition' and e['path']=='content/acquisition_catalog.json'
                    for e in read(folder/'manifest.json')['entries']), 'undeclared_acquisition_payload')
        for definition in payload['definitions']:
            key = (definition['ref']['packId'], definition['ref']['entryId'])
            require(key not in actual, 'duplicate_runtime_definition'); actual[key] = definition
    definitions = {d['id']: d for d in source['definitions']}
    requirements = {}
    for case in cases:
        ref = case['definitionRef']
        candidate = {'packId': ref['pack'], 'entryId': ref['id']} if isinstance(ref,dict) else {
            'packId': definitions[ref]['pack'], 'entryId': ref}
        definition = actual.get((candidate['packId'], candidate['entryId']))
        confirmed = lambda value: 'CONFIRMED' if value else 'SOURCE_UNVERIFIED'
        facets = dict(price=case['evidenceStatus'], admission=confirmed(definition is not None and not contains_type(definition['requirements'], 'Unresolved')),
            rankMaximum=confirmed(definition is not None and definition['maxRank'] is not None),
            discount=confirmed(case['discountConditions']!='UNRESOLVED_SOURCE_DISCOUNT'),
            identity=confirmed(definition is not None and definition['repeatPolicy']['type']!='Unresolved'),
            option=confirmed(definition is not None and definition['abilityOptionIds'] is not None))
        requirements[case['caseId']] = dict(rawCaseSha256=digest(raw[case['caseId']]), effectiveCaseSha256=digest(case),
            candidateDefinitionRef=candidate, actualDefinitionRef=definition['ref'] if definition else None,
            actualKind=definition['kind'] if definition else None, sourceFacets=facets,
            sourceLinks=case['sourceRef'], sourceOccurrenceIds=case['sourceId'],
            executionRequirement={'expectedOutcome':'UNRESOLVED'})
    source_audit = validate_projection(root)
    occurrences = source['sourceOccurrences']; graph = source['definitions']
    matched = [r for r in occurrences if r['runtimeId']]
    universe = dict(sourceOccurrences=len(occurrences), matchedOccurrences=len(matched),
        uniqueMatchedRuntimeIds=len({r['runtimeId'] for r in matched}), graphNodes=len(graph),
        buyable=sum(d['included'] for d in graph),
        developmentWithChi=sum(d['kind']=='development' for d in graph),
        developmentWithoutChi=sum(d['kind']=='development' and d['activeWithoutChi'] for d in graph),
        byKind=dict(Counter(d['kind'] for d in graph)),
        referenceOnly=sum(d['kind'] in ('gear_inventory','chi_school_reference','predicate') for d in graph),
        useOnly=sum(d['kind']=='chi_technique' for d in graph))
    require([universe[k] for k in ('sourceOccurrences','matchedOccurrences','uniqueMatchedRuntimeIds','graphNodes','buyable','developmentWithChi','developmentWithoutChi')]
            == [536,524,517,1498,1111,796,768], 'source_universe_drift')
    manifest = dict(scope='PRODUCTION', testedCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
        inputDigests=source_inventory(root), sourceAudit=source_audit, caseRequirements=requirements, sourceRules=rules, sourceUniverse=universe,
        sourcePriceCounts=dict(baseKnown=sum(c['evidenceStatus']=='CONFIRMED' for c in original['cases']),
            baseUnknown=sum(c['evidenceStatus']!='CONFIRMED' for c in original['cases']),
            effectiveKnown=sum(c['evidenceStatus']=='CONFIRMED' for c in cases),
            effectiveUnknown=sum(c['evidenceStatus']!='CONFIRMED' for c in cases)),
        sourceRuleRevision=authority_revision(rules,'SOURCE_DERIVED'), authorRevision=authority_revision(rules,'USER_RULING'),
        catalogPayloadRevisions={key:value['catalogRevision'] for key,value in packs.items()}, integrityErrors=[])
    # Until the native/provenance reader is implemented, supplied records fail closed.
    evidence = [dict(type='UNVERIFIED_INPUT', path=str(p)) for p in evidence_paths]
    return manifest, cases, evidence


def report_coverage(manifest, cases, evidence):
    integrity = list(manifest.get('integrityErrors', [])); required = manifest['caseRequirements']
    indexed = defaultdict(list)
    for case in cases: indexed[case['caseId']].append(case)
    if set(indexed) != set(required): integrity.append('case_universe_mismatch')
    if any(len(rows)!=1 for rows in indexed.values()): integrity.append('duplicate_case')
    if evidence: integrity.append('evidence_validation_pending')
    results = []
    for identity, requirement in required.items():
        rows = indexed.get(identity, []); case = rows[0] if rows else {}
        failures = []
        if len(rows)!=1: failures.append('missing_or_duplicate_case')
        elif digest(case)!=requirement['effectiveCaseSha256']:
            failures.append('changed_case'); integrity.append('changed_case:'+identity)
        source_blockers = [name for name,status in requirement['sourceFacets'].items() if status!='CONFIRMED']
        if requirement['candidateDefinitionRef']!=requirement['actualDefinitionRef']:
            source_blockers.append('definition_pack_mismatch')
        missing = ['SHARED_JVM:rules_apply_receipt_ledger_restart_undo'] + [p+':'+s for p in PLATFORMS for s in STAGES]
        status = 'FAILED' if failures else ('SOURCE_UNVERIFIED' if source_blockers else 'UI_UNVERIFIED')
        price = 'UNKNOWN_NULL' if case.get('evidenceStatus')!='CONFIRMED' else (
            'KNOWN_ZERO' if [case.get('expectedCostXP'),case.get('expectedCostAP')]==[0,0] else 'KNOWN_AMOUNT')
        results.append(dict(caseId=identity, status=status, rawCaseSha256=requirement['rawCaseSha256'],
            candidateDefinitionRef=requirement['candidateDefinitionRef'], actualDefinitionRef=requirement['actualDefinitionRef'],
            sourceFacets=requirement['sourceFacets'], sourceLinks=requirement['sourceLinks'],
            sourceOccurrenceIds=requirement['sourceOccurrenceIds'], priceClassification=price,
            expectedMoney={k:case.get(k) for k in MONEY_FIELDS}, executionRequirement=requirement['executionRequirement'],
            sourceBlockers=source_blockers, missingEvidence=missing, failures=failures, evidenceIds=[]))
    gates = {name: {'status':'NOT_RUN','evidenceIds':[]} for name in REQUIRED_GATES}
    if manifest.get('scope') == 'PRODUCTION' and manifest.get('sourceAudit') and not integrity:
        gates['source_universe_integrity'] = {'status':'PASS','evidenceIds':['PINNED_SOURCE_AUDIT_JOIN'],
            'scope':'SOURCE_IDENTITIES_ONLY_NOT_RUNTIME_ACCEPTANCE'}
    assertions = {rule['id']: {'status':'NOT_RUN','evidenceIds':[]} for rule in manifest['sourceRules']['rules']}
    return dict(schemaVersion=1, integrityOK=not integrity, integrityErrors=integrity, releaseReady=False,
        testedCommit=manifest['testedCommit'], inputDigests=manifest['inputDigests'], requiredCaseCount=len(required),
        sourceUniverse=manifest['sourceUniverse'], sourceAudit=manifest.get('sourceAudit'), sourcePriceCounts=manifest.get('sourcePriceCounts',{}),
        acceptanceCounts=dict(Counter(r['status'] for r in results)), cases=results, gates=gates,
        sourceAssertions=assertions, pendingAuthorDecisions=manifest['sourceRules']['pendingDecisions'],
        limitations=['WIP: native execution evidence is not yet joined. Pinned source identities are validated, not runtime behavior; no release acceptance.'])


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=Path('.'))
    parser.add_argument('--evidence-dir', type=Path, default=Path('build/acquisition-evidence'))
    parser.add_argument('--output', type=Path, default=Path('build/acquisition-evidence/coverage.json'))
    parser.add_argument('--require-release-ready', action='store_true')
    args=parser.parse_args(argv)
    try:
        paths=[] if not args.evidence_dir.exists() else [p for p in args.evidence_dir.iterdir() if p.resolve()!=args.output.resolve()]
        report=report_coverage(*load_report_inputs(args.repo_root,paths))
    except (ValueError,KeyError,TypeError,OSError) as error:
        report={'integrityOK':False,'releaseReady':False,'integrityErrors':[str(error)]}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,ensure_ascii=False,separators=(',',':'))+'\n')
    print(json.dumps({k:report[k] for k in ('integrityOK','releaseReady')}))
    return int(not report['integrityOK'] or (args.require_release_ready and not report['releaseReady']) or any(c['status']=='FAILED' for c in report.get('cases',[])))


if __name__=='__main__': raise SystemExit(main())
