"""Independent source literals: regressions must not derive expected trees from the importer."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def build():
    from tools.rulebook.import_acquisition import build_acquisition_catalogs
    return build_acquisition_catalogs(ROOT)


def entries():
    return {e['ref']['entryId']: e for p in build().values() for e in p['definitions']}


def nodes(node):
    yield node
    for child in node.get('children', []):
        yield from nodes(child)


def test_actual_source_corrections_and_parent_question():
    es = entries()
    block = es['feat_fcdd74622d21435d']['requirements']
    assert block['type'] == 'AllOf'
    assert [n['type'] for n in block['children']] == ['AnyOf', 'AnyOf']
    assert [(n['attributeId'], n['operand']) for n in block['children'][0]['children']] == [('DEX', 3), ('SPD', 3)]
    assert block['sourceRef']['paragraph'] == 11012
    assert any(n.get('attributeId') == 'CON' and n['operand'] == 3 for n in nodes(es['feat_eaa70b65b370825b']['requirements']))
    sprint = list(nodes(es['feat_bc96393fb969a8e3']['requirements']))
    assert {(n['attributeId'], n['operand']) for n in sprint if n['type'] == 'Attribute'} == {('DEX', 3), ('STR', 3)}
    eagle = es['feat_9012c539238a1e6f']
    assert eagle['priceRule']['type'] == 'Fixed'
    assert eagle['priceRule']['xp'] == '45'
    assert eagle['priceRule']['sourceRef']['paragraph'] == 13387
    assert any(n.get('reason') == 'Q_EAGLE_PARENT_ACCESS' for n in nodes(eagle['requirements']))


def test_display_corrections_remove_only_obsolete_price_incomplete():
    from tools.rulebook.import_acquisition import correct_development_payload
    p = ROOT / 'shared/src/commonMain/resources/fcp/dubl-3.69/content/development_special_catalog.json'
    corrected = correct_development_payload(json.loads(p.read_text()), ROOT)
    eagle = next(e for e in corrected['entries'] if e['id'] == 'feat_9012c539238a1e6f')
    assert eagle['cost'] == 45
    assert not eagle.get('incomplete', False)
    original = json.loads(p.read_text())
    unrelated = next(e for e in original['entries'] if e.get('incomplete') and e['id'] != eagle['id'])
    assert next(e for e in corrected['entries'] if e['id'] == unrelated['id']) == unrelated


def test_martial_tail_preserved():
    es = entries()
    for entry, tail in [('martial_technique_e1110cd9a24338fd', 'Удар по нервам'), ('martial_technique_880f59e730436e3f', 'Мощный удар')]:
        tree = es[entry]['requirements']
        assert tree['type'] == 'AllOf'
        assert any(n['type'] == 'AnyOf' for n in nodes(tree))
        assert any(n['sourceRef']['originalText'] == tail for n in tree['children'])
        assert any(n['type'] == 'Unresolved' and 'source-unverified' in n['reason'] for n in nodes(tree))


def test_spell_reference_not_feat_alias():
    from tools.rulebook.import_acquisition import parse_requirement
    refs = [{'ref': {'packId': 'p', 'entryId': 'spell'}, 'name': 'Призыв оружия', 'kind': 'SPELL', 'maxRank': 1},
            {'ref': {'packId': 'p', 'entryId': 'feat'}, 'name': 'Призыв оружия', 'kind': 'DEVELOPMENT', 'maxRank': 1}]
    source = {'documentId': 'book', 'documentVersion': '1', 'originalText': 'Призыв оружия'}
    ambiguous = parse_requirement('Призыв оружия', 'node', source, refs)
    assert ambiguous['type'] == 'Unresolved'
    spell = parse_requirement('Заклинание Призыв оружия', 'node', source, refs)
    assert spell['type'] == 'LearnedSpell' and spell['entryId'] == 'spell'
    only_spell = parse_requirement('Призыв оружия', 'node', source, refs[:1])
    assert only_spell['type'] == 'LearnedSpell'
    actual = entries()['feat_5368c7691988fd41']['requirements']
    assert any(n['type'] == 'LearnedSpell' and n['entryId'] == 'spell_e3bebd434e638daa' for n in nodes(actual))
    assert any(n['type'] == 'Unresolved' and n['reason'] == 'source-unverified' for n in nodes(actual))


def test_smetanie_not_above_max_but_world_burner_is():
    payload = build()['dubl-3.69']
    es = {e['ref']['entryId']: e for e in payload['definitions']}
    assert es['feat_73b6672717f97e05']['maxRank'] == 2
    assert not any(d['kind'] == 'rank-out-of-range' and d['definitionId'] == 'feat_2cda0df5bcdba24b' for d in payload['diagnostics'])
    assert any(d['kind'] == 'rank-out-of-range' and d['definitionId'] == 'feat_1beadb2cd06442f9' for d in payload['diagnostics'])
    assert any(n['type'] == 'Unresolved' and 'rank-out-of-range' in n['reason'] for n in nodes(es['feat_1beadb2cd06442f9']['requirements']))


def test_diagnostics_missing_ambiguous_duplicate_cycles_and_options():
    from tools.rulebook.import_acquisition import diagnose_catalog
    src = {'documentId': 'b', 'documentVersion': '1', 'originalText': 'rule'}
    def entry(id, target, rank=1):
        return {'ref': {'packId': 'p', 'entryId': id}, 'maxRank': 1, 'abilityOptionIds': [],
                'requirements': {'type': 'OwnedRank', 'nodeId': id, 'sourceRef': src, 'packId': 'p', 'entryId': target, 'operand': rank, 'operator': 'GE'}}
    ds = [entry('a', 'b'), entry('b', 'a'), entry('missing', 'z'), entry('rank', 'a', 2), entry('a', 'b')]
    option = entry('option', 'a'); option['requirements'].update(type='BranchAccess', branchId='a', accessOptionId='unknown'); ds.append(option)
    kinds = {d['kind'] for d in diagnose_catalog(ds)}
    assert {'duplicate-definition', 'missing-reference', 'dependency-cycle', 'rank-out-of-range', 'option-out-of-range'} <= kinds


def test_manifest_payload_is_reproducible_and_no_unknown_free_purchase():
    payloads = build()
    assert payloads == build()
    for pack, payload in payloads.items():
        base = ROOT / 'shared/src/commonMain/resources/fcp' / pack
        assert json.loads((base / 'content/acquisition_catalog.json').read_text()) == payload
        assert any(e['kind'] == 'dubl.acquisition' and e['path'] == 'content/acquisition_catalog.json' for e in json.loads((base / 'manifest.json').read_text())['entries'])
        for e in payload['definitions']:
            if e['sourceStatus'] == 'Unavailable':
                assert e['priceRule']['type'] == 'Unknown'
                assert any(n['type'] == 'Unresolved' for n in nodes(e['requirements']))


def test_tables_are_imported_from_source_rows_without_using_expected_price_oracles():
    es = entries()
    strength = es['attribute:STRENGTH']
    table = strength['priceRule']['transitions']
    assert next(t for t in table if t['fromRank'] == 0 and t['toRank'] == 1)['xp'] == '55'
    assert next(t for t in table if t['fromRank'] == -1 and t['toRank'] == 0)['xp'] == '50'
    skill = es['athletics']['priceRule']
    assert next(t for t in skill['transitions'] if t['toRank'] == 2)['xp'] == '20'
    assert skill['sourceRef']['paragraph'] == 355


def test_general_skill_and_attribute_purchase_rules_do_not_invent_missing_prerequisites():
    es = entries()
    for id, paragraph in [('athletics', 203), ('attribute:STRENGTH', 62)]:
        req = es[id]['requirements']
        assert req['type'] == 'AllOf' and req['children'] == []
        assert req['noRequirementsStatement']
        assert req['sourceRef']['paragraph'] == paragraph
        assert es[id]['priceRule']['type'] == 'TransitionTable'
