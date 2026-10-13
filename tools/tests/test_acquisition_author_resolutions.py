"""Independent literals and immutable inputs for the three explicit author rulings."""
import copy
import hashlib
import json
from pathlib import Path
import pytest

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'rulesets/dubl-3.69/acquisition'
FIXED = 'UR_FIXED_DEVELOPMENT_PRICE_PER_RANK'
CARRY = 'UR_CREATION_BALANCE_CARRYOVER_SAME_PRICE_CURRENCY'
INITIAL = 'UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP'


def test_author_prices_and_source_only_creation_restriction():
    from tools.rulebook.import_acquisition import build_acquisition_catalogs
    payload = build_acquisition_catalogs(ROOT)['dubl-3.69']
    entries = {d['ref']['entryId']: d for d in payload['definitions']}
    price = entries['feat_7fc6fc6e63da9688']['priceRule']
    assert price['type'] == 'PerRank'
    assert (price['xp'], price['ap']) == ('20', '0')
    assert price['sourceRef']['resolutionId'] == FIXED
    beauty = entries['feat_65e4315227fdc855']
    assert (beauty['priceRule']['type'], beauty['priceRule']['xp']) == ('PerRank', '30')
    assert [n['type'] for n in beauty['requirements']['children']] == ['Attribute', 'CreationPhase']
    assert beauty['requirements']['children'][1]['operand'] == 'Creating'
    assert 'resolutionId' not in beauty['requirements']['children'][1]['sourceRef']
    access = entries['access_17f99cc35c559d82']['priceRule']
    for option, ap in [('morale', '1'), ('chi', '2')]:
        first, later = (access[k]['options'][option] for k in ['whenTrue', 'whenFalse'])
        assert (first['xp'], first['ap']) == (later['xp'], later['ap']) == ('0', ap)
        assert 'resolutionId' not in first['sourceRef']
        assert later['sourceRef']['resolutionId'] == CARRY
    policy = payload['creationFundingPolicy']
    assert policy['ruleId'] == INITIAL
    assert policy['xpPerAP'] == '1000'
    assert policy['sourceRef']['paragraph'] == 11903
    assert policy['sourceRef']['resolutionId'] == INITIAL


def test_exact_immutable_inputs_and_separate_author_records():
    expected = {'source_questions.json': 'ec2701ef566800da3009cd50d5eb46e6f1a7c37cc5d2369bb141327bb10f1499',
                'coverage_manifest.json': '32853e608d1d2477a66f96825dff77ace39a6440b51cd66d35571cbb1901019c',
                'expected_prices.json': '1694a9a2bde72e97abbc6a2936d43ced685d2f8a05afc8df81590227a63e3485'}
    for name, sha in expected.items():
        assert hashlib.sha256((DATA/name).read_bytes()).hexdigest() == sha
    assert hashlib.sha256((DATA.parent/'resolutions.json').read_bytes()).hexdigest() == '4d8546b753f44172d2bd7eff4725817383b06d5f744e0746b8f866275ef8d586'
    assert (DATA/'source_rules.json').exists(), 'The explicit author/source records must exist'
    from tools.rulebook.acquisition_coverage_records import load_source_rules
    records = load_source_rules(ROOT)
    assert len(records['rules']) == 7
    author = {r['id']: r for r in records['rules'] if r['authority'] == 'USER_RULING'}
    assert set(author) == {FIXED, CARRY, INITIAL}
    assert author[CARRY]['approvalReference']['messageIds'][-1] == 'Sentinel_496e683f65d0819188c901fc23970cf7'
    excerpts = json.loads((DATA/'source_review_excerpts.json').read_text())['sourceEvidence']
    assert len(excerpts) == 76
    assert {f'core:P{p}' for p in [11903, 14075, 14077, 14078, 14181, 14182]} <= set(excerpts)
    from tools.rulebook.acquisition_audit_projection import validate_projection
    assert validate_projection(ROOT)['additionalExcerpts'] == 70
    assert excerpts['core:P11903']['sha256'] == 'f3b4ccae764f982dd7db5eb152f1736a48f43ccdc671a75c0a3c59858e59ccbc'


def test_effective_overlay_exact_subset_preserves_baseline_and_unknowns():
    import tools.rulebook.acquisition_coverage_records as records
    assert hasattr(records, 'load_effective_expected_prices'), 'An independent immutable price-only overlay is required'
    base = records.read(DATA/'expected_prices.json')
    manifest = records.read(DATA/'coverage_manifest.json')
    rules = records.load_source_rules(ROOT)
    updates = records.read(DATA/'author_expected_price_updates.json')
    prior = records.semantic_sha(base)
    effective = records.load_effective_expected_prices(base, manifest, rules, updates)
    assert records.semantic_sha(base) == prior
    assert [c['caseId'] for c in effective['cases']] == [c['caseId'] for c in base['cases']]
    assert len(effective['cases']) == 83818
    assert len(updates['updates']) == 3922
    assert sum(c['evidenceStatus'] == 'CONFIRMED' for c in effective['cases']) == 46073
    assert sum(c['evidenceStatus'] == 'SOURCE_UNVERIFIED' for c in effective['cases']) == 37745
    by_id = {c['caseId']: c for c in effective['cases']}
    for option, ap in [('morale', 1), ('chi', 2)]:
        row = by_id[f'combat_cries_access_{option}_postcreation_unknown']
        assert (row['expectedCostXP'], row['expectedCostAP']) == (0, ap)
        assert (row['expectedRemainingXP'], row['expectedRemainingAP']) == (100, 10-ap)
        assert row['priceAuthority'] == 'USER_RULING'
        assert row['originalBlockingQuestions'] == ['Q_access_17f99cc35c559d82']
        assert row['expectedApply'] == 'BLOCKED_SOURCE'  # A known price is not admission evidence.
    for row in effective['cases']:
        if row['discountConditions'] == 'UNRESOLVED_SOURCE_DISCOUNT':
            assert row['evidenceStatus'] == 'SOURCE_UNVERIFIED'
    bad = copy.deepcopy(updates)
    bad['updates'].pop(next(iter(bad['updates'])))
    with pytest.raises(ValueError): records.load_effective_expected_prices(base, manifest, rules, bad)
    bad = copy.deepcopy(updates)
    bad['updates'][next(iter(bad['updates']))]['expectedCostXP'] += 1
    with pytest.raises(ValueError): records.load_effective_expected_prices(base, manifest, rules, bad)
    bad = copy.deepcopy(updates)
    bad['updates'][next(iter(bad['updates']))]['openingAP'] = 999
    with pytest.raises(ValueError): records.load_effective_expected_prices(base, manifest, rules, bad)
