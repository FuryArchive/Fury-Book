"""Independent dev-only oracle validation; never calls runtime price code."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'rulesets/dubl-3.69/acquisition'


def corpus():
    from tools.rulebook.validate_acquisition_matrix import validate_matrix  # required gate
    return (json.loads((DATA / 'expected_prices.json').read_text()),
            json.loads((DATA / 'coverage_manifest.json').read_text()))


def validate(matrix, manifest):
    from tools.rulebook.validate_acquisition_matrix import validate_matrix
    return validate_matrix(matrix, manifest)


def test_every_catalog_variant_has_independent_oracle():
    matrix, manifest = corpus()
    assert not validate(matrix, manifest)
    option_index = next(i for i, c in enumerate(matrix['cases']) if c['optionId'] != 'DEFAULT')
    for index in (0, option_index, len(matrix['cases']) - 1):
        reduced = copy.deepcopy(matrix)
        removed = reduced['cases'].pop(index)
        assert {'code': 'MISSING_CASE', 'caseId': removed['caseId']} in validate(reduced, manifest)


def test_unknown_price_cannot_be_confirmed_zero():
    matrix, manifest = corpus()
    case = next(c for c in matrix['cases'] if c['evidenceStatus'] == 'SOURCE_UNVERIFIED')
    case['evidenceStatus'] = 'CONFIRMED'
    case.update(expectedCostXP=0, expectedCostAP=0, expectedRemainingXP=case['openingXP'],
                expectedRemainingAP=case['openingAP'], expectedUndoXP=case['openingXP'],
                expectedUndoAP=case['openingAP'])
    assert {'code': 'SOURCE_UNVERIFIED', 'caseId': case['caseId']} in validate(matrix, manifest)


def test_source_counts_consistent():
    matrix, manifest = corpus()
    assert manifest['counts'] == {'sourceOccurrences': 536, 'matchedOccurrences': 524,
                                  'uniqueMatchedRuntimeIds': 517, 'developmentWithChi': 796,
                                  'developmentWithoutChi': 768}
    assert not validate(matrix, manifest)
    manifest['sourceOccurrences'].pop()
    assert any(e['code'] == 'SOURCE_COUNT_DRIFT' for e in validate(matrix, manifest))


def test_new_catalog_id_drift_is_separate_from_source_counts():
    matrix, manifest = corpus()
    manifest['catalogIds'].append('new-unreviewed-definition')
    errors = validate(matrix, manifest)
    assert any(e['code'] == 'CATALOG_ID_DRIFT' for e in errors)
    assert not any(e['code'] == 'SOURCE_COUNT_DRIFT' for e in errors)


def test_confirmed_oracles_require_source_and_balances():
    matrix, manifest = corpus()
    case = next(c for c in matrix['cases'] if c['evidenceStatus'] == 'CONFIRMED')
    case['expectedRemainingXP'] += 1
    assert {'code': 'BALANCE_MISMATCH', 'caseId': case['caseId']} in validate(matrix, manifest)


def test_blocked_cells_must_have_open_questions_and_no_price():
    matrix, manifest = corpus()
    case = next(c for c in matrix['cases'] if c['evidenceStatus'] == 'SOURCE_UNVERIFIED')
    case['blockingQuestion'] = []
    assert {'code': 'MISSING_BLOCKING_QUESTION', 'caseId': case['caseId']} in validate(matrix, manifest)


def test_duplicate_case_rejected():
    matrix, manifest = corpus()
    matrix['cases'].append(copy.deepcopy(matrix['cases'][0]))
    assert {'code': 'DUPLICATE_CASE', 'caseId': matrix['cases'][0]['caseId']} in validate(matrix, manifest)


def test_approved_numerical_anchors_are_exact():
    matrix, _ = corpus()
    anchors = {c['caseId']: c for c in matrix['cases'] if '|' not in c['caseId']}
    expected = {'block_dex3_speed2': (20, 100, 80), 'block_dex2_speed3': (20, 100, 80),
                'marathon_con4': (30, 100, 70), 'sprinter_dex3_str3': (30, 100, 70),
                'eagle_price_only': (45, 100, 55), 'knowledge_first_instance': (50, 150, 100),
                'knowledge_second_instance': (50, 100, 50), 'resistance_first': (20, 100, 80),
                'resistance_second_type': (20, 80, 60), 'skill_rank1_creation': (10, 100, 90),
                'skill_rank1_after_creation': (5, 100, 95), 'receipt_immutable': (20, 100, 80)}
    for ident, values in expected.items():
        case = anchors[ident]
        assert (case['expectedCostXP'], case['openingXP'], case['expectedRemainingXP']) == values
        assert case['expectedUndoXP'] == case['openingXP']
    assert anchors['block_dex2_speed2']['expectedApply'] == 'BLOCKED_REQUIREMENTS'
    assert anchors['eagle_unknown_parent_access']['evidenceStatus'] == 'SOURCE_UNVERIFIED'
    assert anchors['eagle_unknown_parent_access']['knownUnchangedXP'] == 100
    assert [x['balanceXP'] for x in anchors['legacy_self_taught']['scenario']] == [100, 100, 95, 95]


def test_unknown_domains_remain_explicit_blocked_cells():
    matrix, manifest = corpus()
    assert any(c['fromRank'] == 'UNKNOWN' for c in matrix['cases'])
    cells = [c for c in matrix['cases'] if c['discountConditions'] == 'UNRESOLVED_SOURCE_DISCOUNT']
    assert cells and all(c['evidenceStatus'] == 'SOURCE_UNVERIFIED' for c in cells)
    assert all('Q_SOURCE_DISCOUNT_COMBINATIONS' in c['blockingQuestion'] for c in cells)
    for definition in manifest['definitions']:
        if definition['included'] and definition['parameterClasses'] != ['FIXED']:
            assert definition['parameterClasses'] == ['SAME_ID', 'RENAMED_SAME_ID', 'OTHER_ID',
                                                       'EMPTY', 'UNKNOWN', 'TWO_DISTINCT']
            assert definition['instanceClasses'] == ['FIRST', 'SECOND_DISTINCT',
                                                     'REPEAT_SAME_FORBIDDEN', 'EXISTING']


def test_committed_source_can_rebuild_without_workspace_audit_paths():
    from tools.rulebook.validate_acquisition_matrix import build_corpus
    source = json.loads((DATA / 'source_questions.json').read_text())
    matrix, manifest = corpus()
    assert build_corpus(source) == (matrix, manifest)


def test_confirmed_price_cannot_drift_with_runtime_snapshot():
    matrix, manifest = corpus()
    case = next(c for c in matrix['cases'] if c['evidenceStatus'] == 'CONFIRMED'
                and '|' in c['caseId'] and c['expectedCostXP'] > 0)
    case['expectedCostXP'] = 0
    case['expectedRemainingXP'] = case['openingXP']
    assert {'code': 'PRICE_ORACLE_MISMATCH', 'caseId': case['caseId']} in validate(matrix, manifest)


def test_source_excerpt_tampering_is_detected():
    matrix, manifest = corpus()
    ref, excerpt = next((ref, text) for ref, text in manifest['sourceEvidence'].items()
                        if 'sha256' in text)
    excerpt['text'] += ' changed'
    assert {'code': 'SOURCE_EVIDENCE_DRIFT', 'caseId': ref} in validate(matrix, manifest)


def test_manifest_cannot_silently_drop_creation_or_discount_domain():
    matrix, manifest = corpus()
    definition = next(d for d in manifest['definitions'] if d['included'])
    definition['discountContexts'] = ['NONE']
    assert {'code': 'MISSING_CONTEXT_DOMAIN', 'caseId': definition['id']} in validate(matrix, manifest)


def test_unmapped_source_occurrences_have_explicit_open_questions():
    matrix, manifest = corpus()
    occurrence = next(s for s in manifest['sourceOccurrences'] if not s['runtimeId'])
    occurrence['blockingQuestion'] = []
    assert {'code': 'MISSING_BLOCKING_QUESTION', 'caseId': occurrence['sourceId']} in validate(matrix, manifest)


def test_discounted_cell_requires_legally_owned_self_taught_evidence():
    matrix, manifest = corpus()
    case = next(c for c in matrix['cases'] if c['caseId'] ==
                'athletics|0|1|DEFAULT|AFTER_CREATION|SELF_TAUGHT|EXISTING|FIXED')
    case['ownedPrerequisites'] = []
    assert {'code': 'PREREQUISITE_EVIDENCE_MISMATCH', 'caseId': case['caseId']} in validate(matrix, manifest)


def test_discounted_cell_requires_trusted_price_rule():
    matrix, manifest = corpus()
    case = next(c for c in matrix['cases'] if c['caseId'] ==
                'athletics|0|1|DEFAULT|AFTER_CREATION|SELF_TAUGHT|EXISTING|FIXED')
    case['priceRule'] = 'unrelated_rule'
    assert {'code': 'PRICE_RULE_MISMATCH', 'caseId': case['caseId']} in validate(matrix, manifest)


def test_literal_source_expansion_is_independent_and_exact():
    matrix, manifest = corpus()
    prices = manifest['reviewedRules']['developmentFirstRanks']
    assert len(prices) == 517
    assert prices['feat_a4724bc98b972fd7']['xp'] == 100
    for ident, expected in [('feat_fcdd74622d21435d', 20), ('feat_3233f2da5c693e34', 40)]:
        row = next(c for c in matrix['cases'] if c['caseId'] ==
                   f'{ident}|0|1|DEFAULT|AFTER_CREATION|NONE|EXISTING|FIXED')
        assert row['evidenceStatus'] == 'CONFIRMED'
        assert row['expectedCostXP'] == expected
    prices['feat_a4724bc98b972fd7']['xp'] = 99
    assert any(e['code'] == 'REVIEWED_SOURCE_PRICE_MISMATCH' for e in validate(matrix, manifest))


def test_release_gate_is_strict_without_weakening_integrity():
    import subprocess
    import sys
    result = subprocess.run([sys.executable, '-m', 'tools.rulebook.validate_acquisition_matrix',
                             '--require-confirmed'], cwd=ROOT, capture_output=True, text=True)
    assert result.returncode == 1
    summary = json.loads(result.stdout.splitlines()[0])
    assert summary['integrityErrors'] == 0
    assert summary['releaseReady'] is False
    assert summary['blocked'] > 0


def test_oracle_never_invents_discount_rounding():
    from tools.rulebook.validate_acquisition_matrix import oracle_cost
    _, manifest = corpus()
    definition = next(d for d in manifest['definitions'] if d['id'] == 'athletics')
    rules = copy.deepcopy(manifest['reviewedRules'])
    rules['skillSteps']['1'] = 11
    context = dict(fromRank=0, toRank=1, creationPhase='AFTER_CREATION',
                   discountConditions='SELF_TAUGHT', instance='EXISTING', parameters={'semanticClass': 'FIXED'})
    assert oracle_cost(definition, context, rules) is None


def test_template_skill_quotes_retain_price_only_scope_and_source_identity():
    matrix, manifest = corpus()
    for ident, paragraph, name in [('knowledge_template', 278, 'Знание (Любое)'),
                                   ('performance_template', 291, 'Исполнение (Любое)'),
                                   ('profession_template', 320, 'Профессия'),
                                   ('craft_template', 324, 'Ремесло')]:
        assert manifest['sourceEvidence'][f'core:P{paragraph}']['text'] == name
        assert ident in manifest['reviewedRules']['reviewedSkillIds']
        for parameter in ['SAME_ID', 'RENAMED_SAME_ID', 'OTHER_ID', 'EMPTY', 'UNKNOWN', 'TWO_DISTINCT']:
            case = next(c for c in matrix['cases'] if c['caseId'] ==
                        f'{ident}|0|1|DEFAULT|AFTER_CREATION|SELF_TAUGHT|FIRST|{parameter}')
            assert case['evidenceStatus'] == 'CONFIRMED'
            assert case['evidenceScope'] == 'PRICE_ONLY'
            assert case['expectedApply'] == 'CONDITIONAL_ON_CONFIRMED_ADMISSION_AND_IDENTITY'
            assert case['expectedCostXP'] == 5
            assert case['expectedRemainingXP'] == 9995
    case['evidenceScope'] = 'APPLY_ELIGIBLE'
    assert any(e['code'] == 'PRICE_SCOPE_MISMATCH' for e in validate(matrix, manifest))


def test_combat_cry_access_literal_creation_options_and_unknown_postcreation():
    matrix, manifest = corpus()
    rows = {c['caseId']: c for c in matrix['cases']}
    for option, ap in [('morale', 1), ('chi', 2)]:
        row = rows[f'combat_cries_access_{option}_creation']
        assert (row['expectedCostXP'], row['expectedCostAP'], row['expectedRemainingXP'], row['expectedRemainingAP']) == (0, ap, 100, 10-ap)
        assert row['sourceRef'] == ['core:P11901','core:P11906'] + [f'core:P{p}' for p in range(12100,12108)]
        assert row['evidenceStatus'] == 'CONFIRMED'
        unknown = rows[f'combat_cries_access_{option}_postcreation_unknown']
        assert unknown['evidenceStatus'] == 'SOURCE_UNVERIFIED'
        assert unknown['expectedCostAP'] is None and unknown['expectedCostXP'] is None
        assert unknown['blockingQuestion'] == ['Q_access_17f99cc35c559d82']
    assert not validate(matrix, manifest)
