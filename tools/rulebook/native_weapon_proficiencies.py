"""Source-backed creation weapon groups; ordinary purchase price stays unknown."""
from __future__ import annotations

import copy
import hashlib
import json
import re
from pathlib import Path


def load_native_weapon_proficiencies(repo_root):
    data = json.loads((Path(repo_root) / 'rulesets/dubl-3.69/acquisition/native_weapon_proficiencies.json').read_text(encoding='utf-8'))
    if data['schemaVersion'] != 1:
        raise ValueError('Unsupported native weapon proficiency schema')
    for ref, e in data['evidence'].items():
        if (hashlib.sha256(e['text'].encode()).hexdigest() != e['textSha256'] or
                not re.fullmatch(r'(?:p|paragraphs)/\d+(?:/r\d+c\d+/\d+)*', e['path']) or e['startIndex'] < 0):
            raise ValueError('Native weapon proficiency excerpt mismatch: ' + ref)
    policy = data['creationIncludedGrant']
    text = '\n'.join(data['evidence'][r]['text'] for r in policy['evidence'])
    if (policy['creationPhase'] != 'Creating' or policy['ranksPerGrant'] != 2 or
            policy['chargeXP'] != '0' or policy['chargeAP'] != '0' or
            'при создании персонажа' not in text.casefold() or 'каждые 2 ранга' not in text):
        raise ValueError('Native weapon group creation grant differs from literal permission')
    if set(policy['sourceSkillIds']) != {'shooting', 'melee_weapon', 'throwing', 'unarmed'}:
        raise ValueError('Native weapon grant skill context differs from joint source heading')
    ids = [g['id'] for g in data['groups']]
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate source weapon group identity')
    for group in data['groups']:
        if group['combatSkillId'] is not None and group['combatSkillId'] not in policy['sourceSkillIds']:
            raise ValueError('Unknown source weapon group combat context')
        for ref in group['evidence']:
            if ref not in data['evidence']:
                raise ValueError('Weapon group lacks exact source evidence')
    return data


def _source(data, ref):
    e = data['evidence'][ref]
    meta = dict(data['sourceProvenance'])
    for key in ['documentId', 'revisionId', 'documentVersion']:
        if key in e:
            meta[key] = e[key]
    version = meta.get('revisionId') or meta.get('documentVersion') or 'excerpt-sha256:'+e['textSha256']
    return {'documentId': meta['documentId'], 'documentVersion': version,
            'sheet': e['tabId'], 'paragraph': int(e['path'].split('/')[1]), 'originalText': e['text']}


def apply_native_weapon_proficiencies(definitions, repo_root):
    data = load_native_weapon_proficiencies(repo_root)
    fact = data['definition']; ident = fact['id']
    if any(d['ref']['entryId'] == ident for d in definitions):
        raise ValueError('Duplicate canonical weapon proficiency template')
    source = _source(data, fact['evidence'][0])
    policy = data['creationIncludedGrant']
    policy_source = _source(data, policy['evidence'][1])
    choices = []
    for group in data['groups']:
        choice = {'id': group['id'], 'label': group['label'], 'sourceRef': _source(data, group['evidence'][0])}
        if group.get('weaponKinds'):
            kinds = []
            for kind in group['weaponKinds']:
                kind_source = kind.get('sourceRef')
                if kind_source is None:
                    kind_source = _source(data, kind['evidence'][0])
                kinds.append({'id': kind['id'], 'label': kind['label'], 'sourceRef': kind_source})
            choice['weaponKinds'] = kinds
        choices.append(choice)
    item = {'ref': {'packId': 'dubl-3.69', 'entryId': ident}, 'name': fact['name'], 'kind': 'DEVELOPMENT',
            'maxRank': 1, 'repeatPolicy': {'type': 'DistinctParameter', 'parameterKeys': ['groupId'], 'requireUnique': True},
            'familyIds': ['weapon-proficiency'], 'categoryIds': [], 'abilityOptionIds': [],
            'parameterDefinitions': [{'key': 'groupId', 'label': 'Группа оружия', 'domain': {'type': 'UserDefinedIds'},
                'displayNameKey': 'groupName', 'identityDomain': 'weapon-proficiency-group', 'choices': choices}],
            'sourceStatus': 'Confirmed', 'sourceRef': source, 'requirementsText': source['originalText'],
            'classificationSources': {'native:'+ref: _source(data, ref) for ref in fact['evidence']},
            'priceRule': {'type': 'Unknown', 'sourceRef': source, 'reason': fact['paidPrice']['reason']},
            'nativeRequirements': {'type': 'AllOf', 'nodeId': ident+'/requirements', 'sourceRef': policy_source,
                'children': [], 'noRequirementsStatement': 'The native creation rule explicitly grants a chosen weapon group; no additional target admission prerequisite is stated. Ordinary purchase price is not stated.'}}
    item['classificationSources']['weapon-proficiency'] = policy_source
    definitions.append(item)
    gm_ref = next(ref for ref in fact['evidence'] if 'мастер' in data['evidence'][ref]['text'].casefold())
    for d in definitions:
        if d['ref']['entryId'] not in policy['sourceSkillIds']:
            continue
        allowed = [g['id'] for g in data['groups'] if g['combatSkillId'] == d['ref']['entryId']]
        for rank in range(2, d['maxRank'] + 1, 2):
            d.setdefault('includedGrants', []).append({'sourceRef': policy_source,
                'parameterKey': 'weapon-proficiency-rank-'+str(rank), 'activateAtRank': rank,
                'targetByValue': {}, 'targetDefinition': item['ref'], 'targetRank': 1,
                'requireNewOwnership': False, 'retainsSourceEligibility': True, 'creationOnly': True,
                'targetParameterChoices': {'groupId': allowed},
                'customTargetEvidence': {'parameterKey': 'groupId', 'evidenceKey': 'gm-weapon-proficiency-group',
                    'sourceRef': _source(data, gm_ref),
                    'allowedNamedGroupIds': [g['id'] for g in data['groups'] if g['combatSkillId'] is None]}})
        d['classificationSources']['native:creation-weapon-groups'] = policy_source
    return definitions


def apply_native_weapon_requirement_bindings(definitions, repo_root):
    data = load_native_weapon_proficiencies(repo_root)
    by_id = {d['ref']['entryId']: d for d in definitions}
    for binding in data['requirementBindings']:
        item = by_id[binding['ownerId']]
        hits = []
        def replace(tree):
            if not isinstance(tree, dict):
                return tree
            if (tree.get('nodeId') == binding['nodeId'] and
                    tree.get('sourceRef', {}).get('originalText') == binding['exactText']):
                hits.append(tree)
                replacement = copy.deepcopy(binding['replacement'])
                replacement.update(nodeId=tree['nodeId'], sourceRef=tree['sourceRef'])
                return replacement
            return {k: [replace(c) for c in v] if isinstance(v, list)
                    else replace(v) if isinstance(v, dict) else v for k, v in tree.items()}
        item['requirements'] = replace(item['requirements'])
        if len(hits) != 1:
            raise ValueError('Native weapon exact owner/atom binding must match once: ' + binding['ownerId'])
    return definitions


def native_weapon_proficiency_payload(repo_root):
    data = load_native_weapon_proficiencies(repo_root)
    fact = data['definition']
    return {'version': '3.69', 'entries': [{'id': fact['id'], 'name': fact['name'],
        'section': 'Владение оружием', 'category': 'Владение оружием', 'cost': None, 'costType': 'xp',
        'ranks': 1, 'requirements': '', 'benefit': 'Владение выбранной группой оружия.',
        'notes': 'При создании персонажа — одна группа за каждые 2 ранга боевого умения. Обычная цена покупки в источнике не указана.',
        'incomplete': True, 'repeatable': True, 'acquisitionSourceRef': _source(data, fact['evidence'][0])}]}
