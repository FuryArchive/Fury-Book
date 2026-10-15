"""Bounded assertions retain exact source text, not invented acquisition coverage."""
import copy
from pathlib import Path
import pytest
from tools.rulebook.acquisition_coverage_records import (
    read, load_source_rules, validate_source_rules, authority_revision, resolution_revision,
    FIXED, CARRY, INITIAL, SOURCE_IDS, AUTHOR_IDS,
)
ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT/'rulesets/dubl-3.69/acquisition'


def inputs():
    return (read(DATA/'source_rules.json'), read(DATA/'source_questions.json'), read(DATA/'source_review_excerpts.json'))


def test_exact_source_assertions_preserve_drawback_stage_and_separate_authority():
    records, source, excerpts = inputs()
    merged = validate_source_rules(records, source, excerpts)
    by_id = {r['id']: r for r in records['rules']}
    assert {r['id'] for r in records['rules'] if r['authority'] == 'SOURCE_DERIVED'} == SOURCE_IDS
    assert 'только при создании' in merged['sourceEvidence']['core:P11670']['text']
    drawback = by_id['AR_DRAWBACK_STAGE_AND_REWARD']
    assert drawback['scope'] == {'type': 'CURRENT_NATIVE_DRAWBACK_SET', 'facet': 'DRAWBACK_STAGE_REWARD'}
    assert drawback['runtimeBinding'] == 'BOUND_NATIVE_CURRENT'
    assert drawback['assertions'][1]['signedCostXP'] == '-20'
    assert drawback['assertions'][1]['creationBalanceDeltaXP'] == '20'
    assert drawback['assertions'][1]['laterRewardXP'] == '0'
    assert '\x0b' in merged['sourceEvidence']['core:P14078']['text']
    assert all('approvalReference' not in by_id[i] for i in SOURCE_IDS)
    assert all('approvalReference' in by_id[i] for i in AUTHOR_IDS)
    assert all(q['status'] == 'OPEN' for q in source['questions'])


@pytest.mark.parametrize('mutation', [
    lambda r: r['rules'][0]['scope'].update(facet='ALL'),
    lambda r: r['rules'][0]['assertions'][0].update(phase='Finalized'),
    lambda r: r['rules'][1]['assertions'][0]['steps'][0].update(discountedXP='0'),
    lambda r: r['rules'][2]['assertions'][0].update(laterRewardXP='20'),
    lambda r: r['rules'][3]['assertions'][0].update(minimum=1),
    lambda r: r['rules'][0]['sources'][0].update(startIndex=0),
    lambda r: r['rules'][0]['sources'][0].update(textSha256='0'*64),
    lambda r: r['rules'][0].update(authority='USER_RULING'),
    lambda r: r['rules'][4]['fact']['prices'][0].update(unitXP='0'),
    lambda r: r['rules'][5]['fact'].update(conversion=True),
    lambda r: r['rules'][6]['fact'].update(appliesToExistingOrMigrated=True),
    lambda r: r['rules'].append(copy.deepcopy(r['rules'][0])),
    lambda r: r['questionDispositions'][0].update(questionId='invented-question'),
    lambda r: r['questionDispositions'].append(copy.deepcopy(r['questionDispositions'][0])),
])
def test_malformed_overbroad_or_false_authority_fails(mutation):
    records, source, excerpts = inputs()
    mutation(records)
    with pytest.raises(ValueError): validate_source_rules(records, source, excerpts)


def test_source_and_user_revision_domains_do_not_masquerade_as_each_other():
    records = load_source_rules(ROOT)
    source_before = authority_revision(records, 'SOURCE_DERIVED')
    author_before = authority_revision(records, 'USER_RULING')
    changed = copy.deepcopy(records); changed['rules'][0]['statement'] += ' Exact source assertion.'
    assert authority_revision(changed, 'SOURCE_DERIVED') != source_before
    assert authority_revision(changed, 'USER_RULING') == author_before
    changed = copy.deepcopy(records); changed['rules'][4]['statement'] += ' Explicit approved interpretation.'
    assert authority_revision(changed, 'SOURCE_DERIVED') == source_before
    assert authority_revision(changed, 'USER_RULING') != author_before
    assert resolution_revision(None, 'historical-diagnostic-hash') == 'historical-diagnostic-hash'
    assert resolution_revision(records, 'historical-diagnostic-hash') != 'historical-diagnostic-hash'
