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
    assert eagle['priceRule']['type'] == 'PerRank'
    assert eagle['priceRule']['sourceRef']['resolutionId'] == 'UR_FIXED_DEVELOPMENT_PRICE_PER_RANK'
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
    skill = es['athletics']['priceRule']['whenTrue']
    assert next(t for t in skill['transitions'] if t['toRank'] == 2)['xp'] == '20'
    assert skill['sourceRef']['paragraph'] == 355


def test_general_skill_and_attribute_purchase_rules_do_not_invent_missing_prerequisites():
    es = entries()
    for id, paragraph in [('athletics', 203), ('attribute:STRENGTH', 62)]:
        req = es[id]['requirements']
        assert req['type'] == 'AllOf' and req['children'] == []
        assert req['noRequirementsStatement']
        assert req['sourceRef']['paragraph'] == paragraph
        assert es[id]['priceRule']['type'] == ('Conditional' if id == 'athletics' else 'TransitionTable')


def test_invalid_or_alternative_stays_local_and_potential_scc_does_not_block():
    from tools.rulebook.import_acquisition import apply_requirement_diagnostics, diagnose_catalog
    source = {'documentId': 'book', 'documentVersion': '1', 'originalText': 'A or B'}
    def owned(node, entry):
        return {'type': 'OwnedRank', 'nodeId': node, 'sourceRef': source, 'packId': 'p', 'entryId': entry, 'operator': 'GE', 'operand': 1}
    a = {'ref': {'packId': 'p', 'entryId': 'a'}, 'maxRank': 1, 'requirements': {
        'type': 'AnyOf', 'nodeId': 'a', 'sourceRef': source, 'children': [
            {'type': 'Attribute', 'nodeId': 'str', 'sourceRef': source, 'attributeId': 'STR', 'operator': 'GE', 'operand': 1}, owned('bad', 'missing'), owned('a/b', 'b')]}}
    b = {'ref': {'packId': 'p', 'entryId': 'b'}, 'maxRank': 1, 'requirements': owned('b/a', 'a')}
    diagnostics = diagnose_catalog([a, b])
    fixed = apply_requirement_diagnostics([a, b], diagnostics)
    assert fixed[0]['requirements']['type'] == 'AnyOf'
    assert [n['type'] for n in fixed[0]['requirements']['children']] == ['Attribute', 'Unresolved', 'OwnedRank']
    assert fixed[1]['requirements']['type'] == 'OwnedRank'
    assert any(d['kind'] == 'dependency-cycle' for d in diagnostics)
    actual = entries()['feat_963c20ec0c14be88']['requirements']
    heavy = [n for n in nodes(actual) if n['type'] == 'AnyOf' and any('Мастер тяжелого оружия' in x.get('reason', '') for x in n['children'])]
    assert len(heavy) == 1
    assert not any('Мастер тяжелого оружия' in n.get('reason', '') for n in actual.get('children', []))


def test_source_knowledge_weapon_qualifier_and_distinct_cries_are_typed():
    es = entries()
    knowledge = es['feat_cd965f927a426cc2']['requirements']
    assert knowledge['type'] == 'SkillFamily'
    assert (knowledge['familyId'], knowledge['operand']) == ('knowledge', 4)
    assert knowledge['sourceRef']['paragraph'] == 10530
    assert 'knowledge' in es['knowledge_academic']['familyIds']
    assert 'knowledge' in es['knowledge_template']['familyIds']
    master = es['feat_a834e752f2e3132b']['requirements']
    atoms = list(nodes(master))
    spec = next(n for n in atoms if n['type'] == 'Specialization')
    assert spec['entryId'] == 'feat_2334f8b24105194c'
    assert spec['qualifier'] == {'key': 'weaponTypeId', 'valueId': 'melee'}
    assert {'feat_e22fc0ae2cd61f3a', 'feat_fcdd74622d21435d'} <= {n.get('entryId') for n in atoms}
    assert any(n['type'] == 'SkillFamily' and n['familyId'] == 'melee_weapon' and n['operand'] == 6 for n in atoms)
    cry_ids = {e['ref']['entryId'] for e in es.values() if 'combat-cry' in e['categoryIds']}
    assert len(cry_ids) == 11
    for id, count in [('feat_8b6f3261d7c5c8ea', 2), ('feat_127f2d478d76e66d', 3)]:
        counter = next(n for n in nodes(es[id]['requirements']) if n['type'] == 'DistinctCount')
        assert (counter['uniqueness'], counter['operator'], counter['operand']) == ('Definition', 'GE', count)
        assert counter['predicate']['type'] == 'AnyOf'
        assert {n['entryId'] for n in counter['predicate']['children']} == cry_ids
        assert all(n['operator'] == 'GE' and n['operand'] == 1 for n in counter['predicate']['children'])


def test_unknown_weapon_qualifier_does_not_discard_other_conjuncts():
    from tools.rulebook.import_acquisition import parse_requirement
    source = {'documentId': 'book', 'documentVersion': '1', 'originalText': 'qualifier'}
    tree = parse_requirement('Специализация в оружии (непроверенное), Сила 3', 'q', source, list(entries().values()))
    assert tree['type'] == 'AllOf'
    assert [n['type'] for n in tree['children']] == ['Unresolved', 'Attribute']


def test_actual_perfect_roots_have_source_backed_exclusion_not_child_classification():
    es = entries()
    roots = {'feat_1bacaebcd614c5c5': 13624, 'feat_7c06b44361f99e86': 13655,
             'feat_07a5933de10322e2': 13708}
    expected = 'Совершенные способности могут усиливать, менять и дополнять имеющиеся способности. Один персонаж может иметь не более одной совершенной способности.'
    for id, paragraph in roots.items():
        root = es[id]
        assert 'perfect-root' in root['categoryIds']
        assert root['classificationSources']['category:perfect-root']['paragraph'] == paragraph
        exclusions = [n for n in nodes(root['requirements']) if n['type'] == 'Exclusive']
        assert len(exclusions) == 1
        exclusion = exclusions[0]
        assert exclusion['sourceRef']['paragraph'] == 13618
        assert exclusion['sourceRef']['sheet'] == 't.hwzq55hph1xp'
        assert exclusion['sourceRef']['originalText'] == expected
        assert {n['entryId'] for n in exclusion['conflicting']['children']} == roots.keys() - {id}
        assert all(n['type'] == 'OwnedRank' and n['operator'] == 'GE' and n['operand'] == 1
                   for n in exclusion['conflicting']['children'])
    assert {id for id, e in es.items() if 'perfect-root' in e['categoryIds']} == roots.keys()
    assert not any(d['kind'] == 'dependency-cycle' and d['definitionId'] in roots
                   for d in build()['dubl-3.69']['diagnostics'])


def test_price_whitespace_preserves_literal_and_raw_provenance():
    es = entries()
    for ident, price, paragraph in [('feat_3233f2da5c693e34', '40', 10419),
                                   ('feat_a4724bc98b972fd7', '100', 13374),
                                   ('feat_ff626e2585de2dc7', '30', 12121)]:
        rule = es[ident]['priceRule']
        assert rule['type'] != 'Unknown'
        row = rule['transitions'][0] if rule['type'] == 'TransitionTable' else rule
        assert row['xp'] == price
        assert row['sourceRef']['originalText'].endswith(' ')


def test_self_taught_is_explicit_source_backed_conditional_price():
    rule = entries()['athletics']['priceRule']
    assert rule['type'] == 'Conditional'
    assert rule['condition']['type'] == 'CreationPhase'
    assert rule['condition']['operand'] == 'Creating'
    assert rule['whenTrue']['transitions'][0]['xp'] == '10'
    discounted = rule['whenFalse']
    assert discounted['condition']['entryId'] == 'feat_dd0ba74aedebce10'
    assert [t['xp'] for t in discounted['whenTrue']['transitions'][:3]] == ['5', '10', '30']
    assert discounted['sourceRef']['paragraph'] == 11655


def test_duplicate_occurrences_confirm_price_without_choosing_admission_context():
    import copy
    from tools.rulebook.import_acquisition import duplicate_price_evidence
    source = json.loads((ROOT / 'rulesets/dubl-3.69/acquisition/source_questions.json').read_text())
    occurrences = [o for o in source['sourceOccurrences'] if o['runtimeId'] == 'feat_a1b2117d80ed94e3']
    price = duplicate_price_evidence(occurrences, source)
    assert price['xp'] == '40'
    assert len(price['sources']) == 2
    assert price['maxRank'] == 1
    es = entries()
    definition = es['feat_a1b2117d80ed94e3']
    assert definition['sourceStatus'] == 'NeedsResolution'
    assert definition['priceRule']['xp'] == '40'
    assert definition['requirements']['type'] == 'Unresolved'
    assert 'duplicate-source-context' in definition['requirements']['reason']
    assert len([key for key in definition['classificationSources'] if key.startswith('price:')]) == 2
    mutated = copy.deepcopy(source)
    price_ref = price['sources'][1]
    mutated['sourceEvidence'][price_ref]['text'] = 'Стоимость: 41'
    assert duplicate_price_evidence(occurrences, mutated) is None
    mutated = copy.deepcopy(source)
    rank_ref = next(ref for ref in occurrences[1]['sourceRef'] if mutated['sourceEvidence'][ref]['text'].startswith('Ранги:'))
    mutated['sourceEvidence'][rank_ref]['text'] = 'Ранги: 2'
    assert duplicate_price_evidence(occurrences, mutated)['maxRank'] is None
    # Requirement/branch differences cannot become silently chosen admission rules.
    altered = copy.deepcopy(occurrences)
    altered[1]['fullRule'] += '\n[Different branch]\nТребование: another condition'
    assert duplicate_price_evidence(altered, source)['xp'] == '40'


def test_runtime_import_is_independent_of_oracle_expectations(monkeypatch):
    import copy
    import tools.rulebook.import_acquisition as importer
    baseline = build()
    original_read = importer.read
    def altered(path):
        value = original_read(path)
        if Path(path).name == 'source_questions.json':
            value = copy.deepcopy(value)
            value['reviewedRules'] = {'inventedExpectedXP': 999999}
            value['anchors'] = []
        return value
    monkeypatch.setattr(importer, 'read', altered)
    assert build() == baseline


def test_undefined_discount_rounding_only_omits_affected_transition(monkeypatch):
    import copy
    import tools.rulebook.import_acquisition as importer
    original_read = importer.read
    def altered(path):
        value = original_read(path)
        if Path(path).name == 'source_questions.json':
            value = copy.deepcopy(value)
            value['sourceEvidence']['core:P360']['text'] = '11'
            import hashlib
            value['sourceEvidence']['core:P360']['sha256'] = hashlib.sha256(b'11').hexdigest()
        return value
    monkeypatch.setattr(importer, 'read', altered)
    import pytest
    with pytest.raises(ValueError, match='Exact source citation mismatch'):
        entries()
    # Hypothetical odd source without current approved sidecars still has no rounding fallback.
    # This is deliberately not a source-confirmed current catalog.
    monkeypatch.setattr(importer, 'load_source_rules', lambda _: None)
    rule = entries()['athletics']['priceRule']
    assert rule['whenTrue']['transitions'][0]['xp'] == '11'
    discounted = rule['whenFalse']['whenTrue']['transitions']
    assert not any(t['toRank'] == 1 for t in discounted)
    assert next(t for t in discounted if t['toRank'] == 2)['xp'] == '10'


def test_combat_cry_access_preserves_source_creation_and_author_postcreation_options():
    entry = entries()['access_17f99cc35c559d82']
    assert entry['maxRank'] == 1
    assert entry['repeatPolicy'] == {'type': 'Single'}
    assert entry['sourceStatus'] == 'NeedsResolution'
    assert entry['abilityOptionIds'] == ['morale', 'chi']
    assert entry['requirements']['type'] == 'OwnedRank'
    assert entry['requirements']['entryId'] == 'feat_a700b29906107b2b'
    assert entry['requirements']['sourceRef']['paragraph'] == 12107
    rule = entry['priceRule']
    assert rule['condition']['type'] == 'CreationPhase' and rule['condition']['operand'] == 'Creating'
    assert rule['sourceRef']['paragraph'] == 11906
    assert rule['whenFalse']['type'] == 'ByOption'
    assert [(key, rule['whenFalse']['options'][key]['xp'], rule['whenFalse']['options'][key]['ap']) for key in ['morale', 'chi']] == [('morale', '0', '1'), ('chi', '0', '2')]
    assert rule['whenFalse']['sourceRef']['resolutionId'] == 'UR_CREATION_BALANCE_CARRYOVER_SAME_PRICE_CURRENCY'
    options = rule['whenTrue']['options']
    assert [(key, options[key]['ap'], options[key]['sourceRef']['paragraph']) for key in ['morale', 'chi']] == [('morale','1',12104),('chi','2',12106)]


def test_closed_source_any_attribute_and_any_skill_requirements():
    from tools.rulebook.import_acquisition import parse_requirement
    source={'documentId':'book','documentVersion':'1','originalText':'exact'}
    defs=[{'ref':{'packId':'dubl-3.69','entryId':'athletics'},'name':'Атлетика','kind':'SKILL'},
          {'ref':{'packId':'dubl-3.69','entryId':'knowledge_template'},'name':'Знание (Любое)','kind':'SKILL'},
          {'ref':{'packId':'dubl-3.69','entryId':'feat'},'name':'Не умение','kind':'DEVELOPMENT'}]
    attr=parse_requirement('Любая характеристика 4','a',source,defs)
    assert attr['type']=='AnyOf' and len(attr['children'])==8
    assert {x['attributeId'] for x in attr['children']}=={'STR','DEX','CON','SPD','PER','INT','WIL','CHA'}
    assert all(x['operand']==4 for x in attr['children'])
    skills=parse_requirement('Любое умение 4','s',source,defs)
    assert skills['type']=='AnyOf'
    assert {x['familyId'] for x in skills['children']}=={'athletics','knowledge_template'}
    assert parse_requirement('Любые три умения 5 ранга','s',source,defs)['type']=='Unresolved'


def test_closed_abbreviations_and_any_knowledge_keep_original_source_text():
    from tools.rulebook.import_acquisition import parse_requirement
    source={'documentId':'book','documentVersion':'1','originalText':'exact'}
    defs=[{'ref':{'packId':'dubl-3.69','entryId':'initiative'},'name':'Улучшенная инициатива','kind':'DEVELOPMENT'},
          {'ref':{'packId':'dubl-3.69','entryId':'engineering_repair'},'name':'Инженерное дело (Ремонт)','kind':'SKILL'}]
    for raw in ('ул. Инициатива 1','Ул. Инициатива 2'):
        n=parse_requirement(raw,'n',source,defs)
        assert n['type']=='OwnedRank' and n['entryId']=='initiative'
        assert n['sourceRef']['originalText']==raw
    n=parse_requirement('Тело 4','n',source,defs)
    assert n['type']=='Attribute' and n['attributeId']=='CON' and n['operand']==4
    n=parse_requirement('Ремонт 3','n',source,defs)
    assert n['type']=='SkillFamily' and n['familyId']=='engineering_repair' and n['operand']==3
    for raw in ('Знание (Любое) 5','Знания (Любое) 5 рангов','Знание 5 (Любое)'):
        n=parse_requirement(raw,'n',source,defs)
        assert n['type']=='SkillFamily' and n['familyId']=='knowledge' and n['operand']==5
    assert parse_requirement('Знание (Тайное) 5','n',source,defs)['type']=='Unresolved'


def test_two_distinct_skills_knowledge_and_attribute_pairs_preserve_source():
    from tools.rulebook.import_acquisition import parse_requirement
    source={'documentId':'book','documentVersion':'1','originalText':'exact'}
    defs=[{'ref':{'packId':'dubl-3.69','entryId':'athletics'},'name':'Атлетика','kind':'SKILL'}]
    raw='Требование: Два и более Знаний 4 ранга'
    knowledge=parse_requirement(raw,'k',source,defs)
    assert knowledge['type']=='DistinctCount' and knowledge['uniqueness']=='Instance'
    assert knowledge['operand']==2 and knowledge['predicate']['operand']==4
    assert knowledge['predicate']['familyId']=='knowledge'
    assert knowledge['sourceRef']['originalText']==raw
    skills=parse_requirement('Любые два умения 5 ранг','s',source,defs)
    assert skills['type']=='DistinctCount' and skills['predicate']['type']=='AnyOf'
    pairs=parse_requirement('Любые две характеристики 5','a',source,defs)
    assert pairs['type']=='AnyOf' and len(pairs['children'])==28
    assert all(len(x['children'])==2 and len({y['attributeId'] for y in x['children']})==2 for x in pairs['children'])
    assert all(y['operand']==5 for x in pairs['children'] for y in x['children'])
