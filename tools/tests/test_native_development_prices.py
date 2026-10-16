"""Current native source prices stay independent from admission and historical ledgers."""
import copy
import hashlib
import json
from pathlib import Path

import pytest

from tools.rulebook.import_acquisition import build_acquisition_catalogs

ROOT = Path(__file__).resolve().parents[2]


def test_native_access_prices_preserve_choice_and_do_not_invent_finalized_price():
    definitions = {d['ref']['entryId']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']}
    berserk = definitions['access_5b50c9c8ead88fd5']
    assert berserk['abilityOptionIds'] == ['morale', 'mastery-morale']
    assert berserk['priceRule']['type'] == 'Conditional'
    assert berserk['priceRule']['condition']['operand'] == 'Creating'
    options = berserk['priceRule']['whenTrue']['options']
    assert {key: (value['xp'], value['ap']) for key, value in options.items()} == {'morale': ('0', '1'), 'mastery-morale': ('0', '2')}
    assert berserk['priceRule']['whenFalse']['type'] == 'Unknown'
    assert options['mastery-morale']['sourceRef']['sheet'] == 't.z1o9nhkkrzeb'
    assert options['mastery-morale']['sourceRef']['paragraph'] == 337
    assert options['mastery-morale']['sourceRef']['originalText'] == '2'
    assert berserk['sourceStatus'] == 'Confirmed'
    assert berserk['repeatPolicy'] == {'type': 'Single'}
    assert berserk['requirements']['type'] == 'AllOf'
    assert berserk['requirements']['children'] == []
    assert definitions['access_11f3a49ee9802ad0']['priceRule']['whenTrue']['ap'] == '2'
    assert definitions['access_b832538f3a803048']['priceRule']['whenTrue']['ap'] == '1'


def _nodes(node):
    yield node
    for child in node.get('children', []):
        yield from _nodes(child)


def test_only_proven_independent_skill_templates_are_enabled():
    definitions = {d['ref']['entryId']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']}
    for entry_id in ('knowledge_template', 'performance_template', 'profession_template'):
        definition = definitions[entry_id]
        assert definition['repeatPolicy'] == {'type': 'DistinctParameter', 'parameterKeys': ['specialization'], 'requireUnique': True}
        assert [(p['key'], p['domain']['type']) for p in definition['parameterDefinitions']] == [('specialization', 'UserDefinedIds'), ('specializationName', 'UserDefinedIds')]
        assert definition['requirements']['type'] == 'AllOf'
        assert definition['requirements']['children'] == []
        assert definition['sourceStatus'] == 'Confirmed'
        # Existing source price table and self-taught phase gate remain intact.
        rule = definition['priceRule']
        assert rule['type'] == 'Conditional'
        assert rule['whenTrue']['transitions'][0]['xp'] == '10'
    craft = definitions['craft_template']
    assert craft['repeatPolicy'] == {'type': 'Single'}
    assert craft['parameterDefinitions'] == []
    assert craft['sourceStatus'] == 'Confirmed'
    assert 'общее умение' in craft['classificationSources']['native:general-skill']['originalText']


def test_native_prices_reject_bad_checksum_duplicate_binding_and_non_numeric_price():
    from tools.rulebook.import_acquisition import load_native_development_facts, validate_native_development_facts
    original = load_native_development_facts(ROOT)
    for mutation in (
        lambda data: data['accessPrices'][0]['options'][0]['price'].update(textSha256='0' * 64),
        lambda data: data['accessPrices'].append(copy.deepcopy(data['accessPrices'][0])),
        lambda data: data['accessPrices'][0]['options'][0]['price'].update(text='-'),
    ):
        data = copy.deepcopy(original)
        mutation(data)
        with pytest.raises(ValueError):
            validate_native_development_facts(data)


def test_missing_native_cards_are_generated_once_without_inventing_rank_maxima():
    from tools.rulebook.import_acquisition import build_native_development_supplement, native_occurrence_overlay
    data = ROOT / 'rulesets/dubl-3.69/acquisition/source_questions.json'
    before = data.read_bytes()
    assert hashlib.sha256(before).hexdigest() == 'ec2701ef566800da3009cd50d5eb46e6f1a7c37cc5d2369bb141327bb10f1499'
    original = json.loads(before)
    overlay, supplemental = native_occurrence_overlay(original, ROOT)
    display = build_native_development_supplement(ROOT)
    assert all(e in display['entries'] for e in supplemental['entries'])
    assert len(supplemental['entries']) == 11
    assert sum(e['ranks'] is None for e in supplemental['entries']) == 8
    expected = {'Акробатическое уклонение': 30, 'Бег по стенам': 30, 'Ласточкин хвост': 40,
                'Отскок': 30, 'Прыжок мангуста': 30, 'Бросок кобры': 30, 'Удар падающей звезды': 40,
                'Хлесткая подсечка': 40, 'Ударное скольжение': 40, 'Сальто-атака': 40, 'Боевой азарт': 50}
    assert {e['name']: e['cost'] for e in supplemental['entries']} == expected
    assert len({e['id'] for e in supplemental['entries']}) == 11
    malformed = next(o for o in overlay['sourceOccurrences'] if o['sourceId'] == 'ordinary:t.9xkxyxwswfn9:14380')
    assert malformed['name'] == 'и' and malformed['runtimeId'] == 'feat_ae89314bf1944366'
    assert not next(o for o in original['sourceOccurrences'] if o['sourceId'] == malformed['sourceId'])['runtimeId']
    assert data.read_bytes() == before
    definitions = {d['ref']['entryId']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']}
    for record in supplemental['entries']:
        definition = definitions[record['id']]
        assert definition['maxRank'] == record['ranks']
        if record['ranks'] is None:
            assert definition['repeatPolicy'] == {'type': 'Single'}
            assert definition['classificationSources']['purchase:initial-rank-confirmed']['originalText']
            assert definition['priceRule']['transitions'] == [dict(definition['priceRule']['transitions'][0],
                fromRank=0, toRank=1, xp=str(record['cost']), ap='0')]
            assert not any(n.get('reason') == 'missing-source-rank-maximum' for n in _nodes(definition['requirements']))
        else:
            assert definition['repeatPolicy'] == {'type': 'Single'}
            assert definition['priceRule']['xp'] == '40'
    assert {d['ref']['entryId'] for d in definitions.values() if 'purchase:initial-rank-confirmed' in d['classificationSources']} == {
        record['id'] for record in supplemental['entries'] if record['ranks'] is None} | {
            'martial_technique_6e7e3bdb291edb76', 'martial_technique_880f59e730436e3f',
            'martial_technique_fcbf7ab842c6741f'}


def test_finite_source_rank_tracks_are_scalar_without_enabling_parameterized_variants():
    definitions = {d['ref']['entryId']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']}
    for entry_id in ('feat_e05a30e954c90cd6', 'feat_fcdd74622d21435d'):
        assert definitions[entry_id]['repeatPolicy'] == {'type': 'Single'}
        assert 'identity:finite-rank-track' in definitions[entry_id]['classificationSources']
    weapon = definitions['feat_2334f8b24105194c']
    assert weapon['repeatPolicy'] == {'type': 'Single'}
    assert {p['key'] for p in weapon['parameterDefinitions']} == {'weaponId', 'weaponTypeId'}
    weapon_parameter = next(p for p in weapon['parameterDefinitions'] if p['key'] == 'weaponId')
    assert weapon_parameter['displayNameKey'] == 'weaponName'
    assert all(c['parameterValues']['weaponTypeId'] in {'melee', 'thrown', 'ranged'}
               for c in weapon_parameter['choices'])
    assert not any('weaponSelectionContract' in n.get('reason', '') for n in _nodes(weapon['requirements']))
    assert definitions['feat_cd965f927a426cc2']['repeatPolicy']['type'] == 'DistinctParameter'
    assert definitions['feat_7fc6fc6e63da9688']['repeatPolicy']['type'] == 'DistinctParameter'


def test_eight_confirmed_initial_ranks_satisfy_rank_one_references_without_inventing_caps():
    from tools.rulebook.import_acquisition import diagnose_catalog, has_confirmed_initial_rank
    catalog = build_acquisition_catalogs(ROOT)['dubl-3.69']
    definitions = {d['ref']['entryId']: d for d in catalog['definitions']}
    initial = [d for d in definitions.values() if has_confirmed_initial_rank(d) and not d['ref']['entryId'].startswith('martial_')]
    assert len(initial) == 8
    for target in initial:
        assert target['maxRank'] is None
        for rank in [1, 2]:
            buyer = {'ref': {'packId': 'test', 'entryId': 'buyer'}, 'maxRank': 1, 'abilityOptionIds': [],
                     'requirements': {'type': 'OwnedRank', 'nodeId': 'buyer/rank', 'sourceRef': target['sourceRef'],
                                      **target['ref'], 'operator': 'GE', 'operand': rank}}
            kinds = {d['kind'] for d in diagnose_catalog([target, buyer]) if d['definitionId'] == 'buyer'}
            assert ('unresolved-reference-rank' in kinds) == (rank == 2)
            assert 'rank-out-of-range' not in kinds
    for entry in ['feat_eef2c7391e1bfbe0', 'feat_72e1af25e3a0cea4']:
        assert any(n['type'] == 'OwnedRank' and n.get('entryId') == 'feat_8786ba2fa2651690'
                   for n in _nodes(definitions[entry]['requirements']))


def test_native_supplement_is_one_declared_canonical_fcp_payload():
    from tools.rulebook.import_acquisition import build_native_development_supplement
    pack = ROOT / 'shared/src/commonMain/resources/fcp/dubl-3.69'
    path = 'content/development_native_supplement_catalog.json'
    entries = json.loads((pack / 'manifest.json').read_text())['entries']
    assert sum(e['path'] == path and e['kind'] == 'dubl.development' for e in entries) == 1
    assert json.loads((pack / path).read_text()) == build_native_development_supplement(ROOT)


def test_current_magic_access_table_drops_only_the_obsolete_trickster_option():
    from tools.rulebook.import_acquisition import correct_development_payload
    p = ROOT / 'shared/src/commonMain/resources/fcp/dubl-3.69/content/development_ability_roots_catalog.json'
    payload = correct_development_payload(json.loads(p.read_text()), ROOT)
    trickster = next(e for e in payload['entries'] if e['id'] == 'access_5a507d1433ae0b0d')
    assert trickster['abilityOptions'] == [{'source': 'Сверхъестественное', 'value': 2}]
    assert trickster['acquisitionSourceRef']['documentId'] == '1BGajeMuh0clSbybt0IGyqv2_6zxdKEtHvnjh_TbKqGg'


def test_resistance_repeat_parameters_use_exact_source_elemental_domain():
    definitions = {d['ref']['entryId']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']}
    resistance = definitions['feat_7fc6fc6e63da9688']
    parameter = resistance['parameterDefinitions'][0]
    assert parameter['key'] == 'resistanceType'
    assert parameter['domain'] == {'type': 'StableIds', 'valueIds': ['fire', 'cold', 'acid', 'electricity', 'poison', 'sonic']}
    assert [(c['id'], c['label']) for c in parameter['choices']] == [
        ('fire', 'Огонь'), ('cold', 'Холод'), ('acid', 'Кислота'),
        ('electricity', 'Электричество'), ('poison', 'Яд'), ('sonic', 'Звук')]
    assert resistance['priceRule']['type'] == 'PerRank'
    assert resistance['priceRule']['xp'] == '20'
    assert resistance['maxRank'] == 2
    assert all(c['sourceRef']['sheet'] == 't.sb8vo7vvy7e7' for c in parameter['choices'])


def test_each_native_core_access_table_price_and_option_is_exact():
    definitions = {d['name']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']
                   if d['ref']['entryId'].startswith('access_')}
    expected = {
        'Авангард': 2, 'Акробатика': 1, 'Берсерк': {'morale': 1, 'mastery-morale': 2},
        'Боевая фокусировка': {'mastery': 1, 'chi': 2}, 'Боевой азарт': {'morale': 1, 'mastery': 2},
        'Броненосец': 1, 'Второе дыхание': 1, 'Гнев': {'morale': 1, 'mastery-morale': 2},
        'Громила': {'extraordinary': 1, 'augmentation': 1}, 'Дуэлянт': 1, 'Изувер': 1,
        'Капканщик / Мастер ловушек': 2, 'Мастер-лучник': 1, 'Мастер-метатель': 1,
        'Мастер оружия': 1, 'Мастер-ремесленник': 1, 'Мастер щитов': 2,
        'Неистовство': {'morale': 1, 'mastery-morale': 2}, 'Охотник за головами': 1,
        'Пироманьяк': 1, 'Подлая атака': 1, 'Последний выживший': 2,
        'Сверхчеловеческие рефлексы': {'augmentation': 2, 'chi': 2}, 'Связи': 1,
        'Скаут': 1, 'Совершенная точность': 1, 'Телохранитель': 1, 'Удача': 2,
        'Чемпион': 2, 'Шестое чувство': 2, 'Штурмовик': 2, 'Киберпанк': 1, 'Хакер': 1,
    }
    assert len(expected) == 33
    for name, price in expected.items():
        definition = definitions[name]
        rule = definition['priceRule']
        assert rule['type'] == 'Conditional'
        assert rule['whenFalse']['type'] == 'Unknown'
        creation = rule['whenTrue']
        if isinstance(price, dict):
            assert creation['type'] == 'ByOption'
            assert {key: value['ap'] for key, value in creation['options'].items()} == {key: str(value) for key, value in price.items()}
            assert all(value['xp'] == '0' for value in creation['options'].values())
            assert definition['abilityOptionIds'] == list(price)
        else:
            assert (creation['type'], creation['xp'], creation['ap']) == ('Fixed', '0', str(price))
            assert definition['abilityOptionIds'] == []


def test_legacy_flavor_title_duplicate_has_known_price_without_a_second_purchase():
    definitions = {d['ref']['entryId']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']}
    legacy = definitions['feat_1a9f2f0b9135c3d8']; canonical = definitions['feat_b8989e97e58accba']
    assert legacy['priceRule']['xp'] == '30'
    assert legacy['legacyAliasOf'] == canonical['ref']
    assert legacy['requirements']['type'] == 'Unresolved'
    assert legacy['requirements']['reason'] == 'LEGACY_DUPLICATE_DEFINITION_USE_CANONICAL:feat_b8989e97e58accba'
    assert canonical['name'] == 'Монстровед'
    assert any(n['type'] == 'Exclusive' and n['conflicting'].get('entryId') == legacy['ref']['entryId']
               for n in _nodes(canonical['requirements']))
