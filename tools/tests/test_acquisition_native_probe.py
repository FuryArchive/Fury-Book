"""Negative admission contracts. These fabricated bundles are never execution evidence."""
import copy
import pytest
from tools.rulebook.acquisition_native_probe import CASE_IDS, PRODUCER, validate_probe, execution_requirement


def fixture():
    bundle = dict(schema='FURY_NATIVE_PROBE_V1', producer=PRODUCER, testedCommit='1'*40,
                  inputDigests={'source.kt':'a'*64}, records=[{'caseId': k} for k in sorted(CASE_IDS)])
    manifest = dict(testedCommit=bundle['testedCommit'], inputDigests=copy.deepcopy(bundle['inputDigests']))
    return bundle, manifest


@pytest.mark.parametrize('mutation,reason', [
    ('commit', 'stale_native_commit'), ('source', 'stale_native_sources'), ('empty_sources', 'stale_native_sources'),
    ('producer', 'native_probe_producer'), ('schema', 'native_probe_producer'), ('extra', 'native_probe_fields'),
    ('missing', 'missing_duplicate_or_unexpected_native_cases'), ('duplicate', 'missing_duplicate_or_unexpected_native_cases'),
    ('unknown', 'missing_duplicate_or_unexpected_native_cases'), ('summary_only', 'native_record_fields')])
def test_untrusted_or_stale_probe_never_reduces_missing_evidence(mutation, reason):
    bundle, manifest = fixture()
    if mutation == 'commit': bundle['testedCommit'] = '2'*40
    elif mutation == 'source': bundle['inputDigests']['source.kt'] = 'b'*64
    elif mutation == 'empty_sources': bundle['inputDigests'] = manifest['inputDigests'] = {}
    elif mutation == 'producer': bundle['producer'] = 'allTestsPassed'
    elif mutation == 'schema': bundle['schema'] = 'unknown'
    elif mutation == 'extra': bundle['releaseReady'] = True
    elif mutation == 'missing': bundle['records'].pop()
    elif mutation == 'duplicate': bundle['records'][1] = bundle['records'][0]
    elif mutation == 'unknown': bundle['records'][0]['caseId'] = 'unknown'
    with pytest.raises(ValueError, match=reason): validate_probe(bundle, manifest, [], {})


def test_supported_witness_does_not_follow_spoofed_case_id_or_loosen_requirements():
    case = dict(caseId='athletics|0|1|DEFAULT|CREATION|NONE|EXISTING|FIXED', fromRank=0, toRank=1, optionId='DEFAULT', discountConditions='NONE',
                instance='EXISTING', parameters={'semanticClass':'FIXED'}, ownedPrerequisites=[], evidenceStatus='CONFIRMED')
    definition = dict(ref={'packId':'dubl-3.69','entryId':'athletics'},kind='SKILL',sourceStatus='Confirmed',
                      repeatPolicy={'type':'Single'},parameterDefinitions=[],abilityOptionIds=[],
                      requirements={'type':'AllOf','children':[],'noRequirementsStatement':'Exact source rule'})
    assert execution_requirement(case, definition)['expectedOutcome'] == 'APPLIED'
    for key, value in [('toRank',2),('discountConditions','SELF_TAUGHT'),('instance','SECOND_DISTINCT'),
                       ('parameters',{'semanticClass':'RENAMED_SAME_ID'}),('evidenceStatus','SOURCE_UNVERIFIED')]:
        changed = {**case, key:value}
        assert execution_requirement(changed, definition)['expectedOutcome'] == 'UNRESOLVED'
    changed = copy.deepcopy(definition);changed['requirements']['children'] = [{'type':'Unresolved'}]
    assert execution_requirement(case, changed)['expectedOutcome'] == 'UNRESOLVED'
