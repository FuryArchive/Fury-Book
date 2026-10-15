"""Creation rank grants do not manufacture a paid weapon proficiency price."""
import functools
from pathlib import Path

from tools.rulebook.import_acquisition import build_acquisition_catalogs
from tools.rulebook.native_weapon_proficiencies import load_native_weapon_proficiencies

ROOT = Path(__file__).resolve().parents[2]


@functools.lru_cache(maxsize=1)
def definitions():
    return {d['ref']['entryId']: d for d in build_acquisition_catalogs(ROOT)['dubl-3.69']['definitions']}


def walk(value):
    if isinstance(value, dict):
        yield value
        for v in value.values():
            yield from walk(v)
    elif isinstance(value, list):
        for v in value:
            yield from walk(v)


def test_proficiency_has_named_group_identity_and_no_invented_paid_price():
    d = definitions()['weapon_proficiency_template']
    assert d['repeatPolicy'] == {'type': 'DistinctParameter', 'parameterKeys': ['groupId'], 'requireUnique': True}
    p = d['parameterDefinitions'][0]
    assert (p['key'], p['displayNameKey'], p['identityDomain']) == ('groupId', 'groupName', 'weapon-proficiency-group')
    assert p['domain'] == {'type': 'UserDefinedIds'}
    assert len(p['choices']) == 16
    assert d['priceRule']['type'] == 'Unknown'
    assert d['priceRule']['reason'] == 'NO_PAID_WEAPON_PROFICIENCY_PRICE_IN_CURRENT_SOURCE'
    assert d['requirements']['type'] == 'AllOf' and d['requirements']['children'] == []


def test_free_groups_are_creation_only_every_two_combat_skill_ranks_with_context():
    for ident in ['shooting', 'melee_weapon', 'throwing', 'unarmed']:
        grants = definitions()[ident]['includedGrants']
        assert [g['activateAtRank'] for g in grants] == [2, 4, 6, 8, 10]
        for g in grants:
            assert g['creationOnly'] is True
            assert g['targetDefinition'] == {'packId': 'dubl-3.69', 'entryId': 'weapon_proficiency_template'}
            assert g['targetByValue'] == {}
            assert g['requireNewOwnership'] is False
            assert g['retainsSourceEligibility'] is True
            assert g['customTargetEvidence']['evidenceKey'] == 'gm-weapon-proficiency-group'
            assert g['customTargetEvidence']['allowedNamedGroupIds'] == ['improvised']
    shooting = definitions()['shooting']['includedGrants'][0]['targetParameterChoices']['groupId']
    assert shooting == ['bows', 'crossbows', 'shotguns', 'rifles', 'pistols', 'assault-rifles', 'launchers']
    assert 'polearms' not in shooting
    assert definitions()['unarmed']['includedGrants'][0]['targetParameterChoices']['groupId'] == []


def test_actual_named_weapon_types_and_repeated_summon_contract_are_retained():
    choices = definitions()['weapon_proficiency_template']['parameterDefinitions'][0]['choices']
    bow = next(c for c in choices if c['id'] == 'bows')
    assert {'Короткий лук', 'Длинный лук'} <= {k['label'] for k in bow['weaponKinds']}
    assert all(k['sourceRef']['documentId'] for c in choices for k in c.get('weaponKinds', []))
    summon = definitions()['spell_e3bebd434e638daa']
    assert [p['selectionSource'] for p in summon['parameterDefinitions']] == [
        {'type': 'OwnedWeaponKinds'}, {'type': 'OwnedWeaponKinds'}]
    assert summon['newParameterValueRequirement']['parameterKeys'] == ['weaponKind1', 'weaponKind2']
    assert summon['newParameterValueRequirement']['minimumNewValues'] == 1


def test_all_generated_source_paragraphs_are_nullable_integers():
    for d in definitions().values():
        for obj in walk(d):
            if 'documentId' in obj and 'originalText' in obj:
                assert obj.get('paragraph') is None or type(obj['paragraph']) is int


def test_combo_ordered_selection_removes_only_its_implemented_technical_blocker():
    d = definitions()['martial_technique_c4972b9f356e9d3d']
    assert d['repeatPolicy']['type'] == 'DistinctParameter'
    parameter = d['parameterDefinitions'][0]
    assert parameter['selectionSource']['type'] == 'OrderedOwnedInstances'
    assert parameter['displayNameKey'] == 'comboSequenceNames'
    assert not any(n.get('reason') == 'technical-missing-ordered-combo-parameter-contract' for n in walk(d['requirements']))
    assert any(n.get('type') == 'DistinctCount' and n.get('operand') == 2 for n in walk(d['requirements']))
    assert d['priceRule']['type'] == 'TransitionTable'
    assert len(d['priceRule']['transitions']) == 1
