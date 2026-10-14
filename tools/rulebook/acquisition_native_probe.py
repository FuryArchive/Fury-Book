"""Bounded native evidence admission. Never accepts arbitrary declared outcomes or release PASS."""
from tools.rulebook.acquisition_coverage_records import read, require, semantic_sha
from tools.rulebook.acquisition_native_evidence import validate_native

PRODUCER = 'AcquisitionNativeEvidenceProbeTest.fixed_skill_purchase_reopen_and_undo_export_native_envelopes'
CASE_IDS = frozenset(f'athletics|0|1|DEFAULT|{phase}|NONE|EXISTING|FIXED'
                     for phase in ('CREATION', 'AFTER_CREATION'))
SCOPE = 'DESKTOP_REAL_FILE_STORE_REOPEN_SAME_PROCESS_NOT_UI_OR_CRASH'


def execution_requirement(case, definition):
    unresolved = {'expectedOutcome': 'UNRESOLVED'}
    if case['caseId'] not in CASE_IDS or not definition:
        return unresolved
    # This deliberately narrow source witness is not an evaluator that fabricates
    # prerequisites from whatever a runtime happens to require.
    req = definition['requirements']
    if not (definition['ref'] == {'packId': 'dubl-3.69', 'entryId': 'athletics'}
            and definition['kind'] == 'SKILL' and definition['sourceStatus'] == 'Confirmed'
            and definition['repeatPolicy'] == {'type': 'Single'}
            and definition['parameterDefinitions'] == [] and definition['abilityOptionIds'] == []
            and req['type'] == 'AllOf' and req['children'] == [] and req.get('noRequirementsStatement')
            and case['fromRank'] == 0 and case['toRank'] == 1 and case['optionId'] == 'DEFAULT'
            and case['discountConditions'] == 'NONE' and case['instance'] == 'EXISTING'
            and case['parameters'] == {'semanticClass': 'FIXED'} and not case['ownedPrerequisites']
            and case['evidenceStatus'] == 'CONFIRMED'):
        return unresolved
    return dict(expectedOutcome='APPLIED', minimumAttributes={}, selfTaughtOwned=False,
                basis='EXPLICIT_GENERAL_FIXED_SKILL_PURCHASE_NO_ADDITIONAL_PREREQUISITE')


def catalog_evidence(packs):
    refs = []
    def collect(value):
        if isinstance(value, dict):
            if {'documentId', 'documentVersion', 'originalText'} <= value.keys():
                refs.append({key: value.get(key) for key in ('documentId', 'documentVersion', 'originalText',
                                                          'sheet', 'paragraph', 'resolutionId', 'resolutionRevision')})
            else:
                for child in value.values(): collect(child)
        elif isinstance(value, list):
            for child in value: collect(child)
    for payload in packs.values(): collect(payload)
    return dict(catalogRevision='|'.join(f'{key}:content/acquisition_catalog.json:{p["catalogRevision"]}' for key,p in packs.items()),
                resolutionRevision='|'.join(f'{key}:{p["resolutionRevision"]}' for key,p in packs.items()), sourceRefs=refs)


def validate_probe(bundle, manifest, cases, packs):
    require(set(bundle) == {'schema', 'producer', 'testedCommit', 'inputDigests', 'records'}, 'native_probe_fields')
    require(bundle['schema'] == 'FURY_NATIVE_PROBE_V1' and bundle['producer'] == PRODUCER, 'native_probe_producer')
    require(bundle['testedCommit'] == manifest['testedCommit'], 'stale_native_commit')
    require(bundle['inputDigests'] == manifest['inputDigests'] and bool(bundle['inputDigests']), 'stale_native_sources')
    require(isinstance(bundle['records'], list), 'native_records_not_list')
    identities = [r['caseId'] for r in bundle['records']]
    require(len(identities) == len(CASE_IDS) and set(identities) == CASE_IDS, 'missing_duplicate_or_unexpected_native_cases')
    indexed = {c['caseId']: c for c in cases}
    catalog = catalog_evidence(packs)
    evidence = []
    for record in bundle['records']:
        require(set(record) == {'caseId', 'outcome', 'binding', 'native'}, 'native_record_fields')
        identity = record['caseId']; case = indexed[identity]
        requirement = manifest['caseRequirements'][identity]
        require(semantic_sha(case) == requirement['effectiveCaseSha256'], 'native_case_digest_mismatch')
        require(all(v == 'CONFIRMED' for v in requirement['sourceFacets'].values()), 'unconfirmed_native_source_facet')
        result = validate_native(record, case, requirement, catalog)
        evidence.append(dict(type='VALIDATED_NATIVE_PROBE_CASE', caseId=identity,
                             evidenceId='NATIVE:'+semantic_sha(record), scope=SCOPE, normalized=result))
    return evidence


def load_probes(paths, manifest, cases, packs):
    require(len(paths) <= 1, 'duplicate_native_evidence_bundles')
    return [] if not paths else validate_probe(read(paths[0]), manifest, cases, packs)
