"""Current source literals at learning-band and rune-grade boundaries."""
import copy
import functools
import json
from pathlib import Path

import pytest

from tools.rulebook.import_acquisition import build_acquisition_catalogs
from tools.rulebook.native_magic_supplement import (
    load_native_magic_supplement, native_magic_development_payload,
    correct_native_magic_equipment_payload, correct_native_magic_skills_payload,
)

ROOT = Path(__file__).resolve().parents[2]


@functools.lru_cache(maxsize=1)
def definitions():
    return {d['ref']['entryId']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']}


def nodes(tree):
    yield tree
    for child in tree.get('children', []):
        yield from nodes(child)


def test_all_native_supplement_entities_have_stable_source_backed_definitions():
    facts = load_native_magic_supplement(ROOT)
    assert len(facts['records']) == 56
    assert len({r['id'] for r in facts['records']}) == 56
    for record in facts['records']:
        d = definitions()[record['id']]
        assert d['kind'] == record['kind']
        assert d['sourceRef']['documentId'] == '1BGajeMuh0clSbybt0IGyqv2_6zxdKEtHvnjh_TbKqGg'


def test_blood_spell_learning_prices_use_literal_band_endpoints():
    for ident, mana, xp in [
        ('spell_7909b6bb4b35000e', 3, '10'), ('spell_40392e41df01ed4a', 3, '10'),
        ('spell_196299759d9ed334', 3, '10'), ('spell_27ebe68f0d4f4358', 8, '20'),
        ('spell_f2ccfb746efecd5a', 17, '50'), ('spell_d6ad8d35a9ff0e51', 17, '50')]:
        d = definitions()[ident]
        assert d['priceRule']['type'] == 'Fixed'
        assert d['priceRule']['xp'] == xp
        assert d['requirements']['type'] == 'NumericProperty'
        assert (d['requirements']['propertyId'], d['requirements']['operand']) == ('MAGIC_POWER', mana)


def test_rune_grade_never_becomes_purchase_rank_or_spell_learning_price():
    runes = [r for r in load_native_magic_supplement(ROOT)['records'] if 'runeGrade' in r]
    assert len(runes) == 12
    assert {r['runeGrade'] for r in runes} == {1, 2, 3}
    for r in runes:
        d = definitions()[r['id']]
        assert (d['kind'], d['maxRank'], d['repeatPolicy']) == ('DEVELOPMENT', 1, {'type': 'Single'})
        assert (d['priceRule']['type'], d['priceRule']['xp'], d['priceRule']['ap']) == ('Fixed', '20', '0')
        assert d['requirements']['type'] == 'AllOf' and d['requirements']['children'] == []


def test_blood_currency_is_explicit_choice_without_conversion_or_unproven_higher_rank_cost():
    for r in load_native_magic_supplement(ROOT)['records']:
        if r['kind'] != 'DEVELOPMENT' or 'runeGrade' in r:
            continue
        d = definitions()[r['id']]
        assert d['abilityOptionIds'] == ['xp', 'ap']
        assert 'за опыт или очки способностей' in d['classificationSources']['payment:transition-options']['originalText']
        assert d['priceRule']['type'] == 'ByOption'
        for key in ['xp', 'ap']:
            rule = d['priceRule']['options'][key]
            literal = rule if r['maxRank'] == 1 else rule['transitions'][0]
            assert literal['xp'] == (r['price']['literalAmount'] if key == 'xp' else '0')
            assert literal['ap'] == (r['price']['literalAmount'] if key == 'ap' else '0')
            if r['maxRank'] > 1:
                assert rule['type'] == 'TransitionTable'
                assert len(rule['transitions']) == 1
                assert (literal['fromRank'], literal['toRank']) == (0, 1)
    vessel = definitions()['feat_0f2944736b20e934']
    assert vessel['repeatPolicy'] == {'type': 'Independent'}
    assert {k: v for k, v in vessel['instanceCountLimit'].items() if k != 'sourceRef'} == {
        'attributeId': 'CON', 'numerator': 1, 'denominator': 2}


def test_runology_uses_ordinary_skill_prices_and_its_explicit_intellect_attribute():
    d = definitions()['runology']
    assert d['maxRank'] == 10
    assert d['priceRule']['type'] == 'Conditional'
    assert d['priceRule']['whenTrue']['transitions'][0]['xp'] == '10'
    assert d['priceRule']['whenFalse']['whenTrue']['transitions'][0]['xp'] == '5'
    assert 'Самоучка' not in d['sourceRef']['originalText']
    payload = correct_native_magic_skills_payload({'skills': []}, ROOT)
    assert payload['skills'][0]['defaultAttributeHint'] == 'INTELLIGENCE'
    assert payload['skills'][0]['untrained'] == 'UNSPECIFIED'


def test_supplement_display_merges_are_idempotent_and_kind_correct():
    development = native_magic_development_payload(ROOT)
    assert len(development['entries']) == 22
    spells = correct_native_magic_equipment_payload({'spells': [], 'gear': []}, ROOT)
    assert len(spells['spells']) == 33
    assert correct_native_magic_equipment_payload(spells, ROOT) == spells
    skills = correct_native_magic_skills_payload({'skills': []}, ROOT)
    assert correct_native_magic_skills_payload(skills, ROOT) == skills
    master = definitions()['feat_a08f840bb3a709ed']
    assert any(n['type'] == 'LearnedSpell' and n['entryId'] == 'spell_0f68b25bdd0697a9'
               for n in nodes(master['requirements']))
