"""Bounded native drawback acquisitions and their explicit stage-dependent grants.

The negative amounts here are the current book's creation rewards per rank.
They do not establish a general negative-price or reverse-purchase convention.
"""
from __future__ import annotations

import copy
import hashlib
import json
import re
from pathlib import Path

CORE = 'dubl-3.69'
ATTRIBUTES = {'Сила': 'STR', 'Телосложение': 'CON', 'Ловкость': 'DEX', 'Скорость': 'SPD',
              'Интеллект': 'INT', 'Воля': 'WIL', 'Харизма': 'CHA', 'Восприятие': 'PER'}


def _literal_requirement(text):
    if text == '—':
        return {'type': 'AllOf', 'children': [],
                'noRequirementsStatement': 'The exact current native card explicitly states Требование: —.'}
    if ' или ' in text:
        return {'type': 'AnyOf', 'children': [_literal_requirement(value) for value in text.split(' или ')]}
    if ' и ' in text and not text.endswith(' и меньше'):
        return {'type': 'AllOf', 'children': [_literal_requirement(value) for value in text.split(' и ')]}
    match = re.fullmatch(r'(\w+)\s*(<|>)\s*(\d+)', text)
    if match:
        return {'type': 'Attribute', 'attributeId': ATTRIBUTES[match[1]],
                'operator': {'<': 'LT', '>': 'GT'}[match[2]], 'operand': int(match[3])}
    match = re.fullmatch(r'(\w+)\s+(\d+) и меньше', text)
    if match:
        return {'type': 'Attribute', 'attributeId': ATTRIBUTES[match[1]],
                'operator': 'LE', 'operand': int(match[2])}
    raise ValueError('Unsupported exact native drawback requirement: ' + text)


def load_native_drawback_rules(repo_root):
    path = Path(repo_root) / 'rulesets/dubl-3.69/acquisition/native_drawback_rules.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    validate_native_drawback_rules(data)
    return data


def validate_native_drawback_rules(data):
    if data['schemaVersion'] != 1 or not data['devOnly']:
        raise ValueError('Unsupported native drawback rules schema')
    provenance = data['sourceProvenance']
    if not provenance['revisionId'] or provenance['tabId'] != 't.jaljeq1rpes0':
        raise ValueError('Native drawback source revision/tab is missing')
    evidence = data['evidence']
    for ref, excerpt in evidence.items():
        if (not re.fullmatch(r'p/\d+', excerpt['path']) or
                excerpt['tabId'] != provenance['tabId'] or
                not 0 <= excerpt['startIndex'] < excerpt['endIndex'] or
                hashlib.sha256(excerpt['text'].encode()).hexdigest() != excerpt['textSha256']):
            raise ValueError('Native drawback excerpt coordinates/hash mismatch: ' + ref)
    general = evidence[data['policyEvidence']['general']]['text']
    event = evidence[data['policyEvidence']['eventOnly']]['text']
    if ('за каждый ранг' not in general or 'нужны только при создании персонажа' not in general or
            'нельзя взять при создании персонажа' not in event or 'За них не даётся опыт' not in event):
        raise ValueError('Native drawback stage/reward policy differs from source')
    seen, prices = set(), set()
    for record in data['records']:
        if record['id'] in seen:
            raise ValueError('Duplicate native drawback identity')
        seen.add(record['id'])
        if record['name'] != evidence[record['titleEvidence']]['text']:
            raise ValueError('Native drawback identity differs from exact title')
        card = evidence[record['cardEvidence']]['text']
        fields = dict(re.match(r'([^:]+):\s*(.*)', line).groups()
                      for line in card.split('\v') if re.match(r'([^:]+):\s*(.*)', line))
        cost = None if fields['Стоимость'] == '—' else int(fields['Стоимость'])
        if cost != record['signedCostXP'] or (cost is not None and cost >= 0):
            raise ValueError('Native drawback signed price differs from literal')
        if (record['eventOnly'] != (cost is None) or record['maxRank'] != int(fields['Ранги']) or
                record['maxRank'] < 1 or record['requirementsText'] != fields['Требование'] or
                record['creationRequirementTree'] != _literal_requirement(fields['Требование'])):
            raise ValueError('Native drawback rank/stage/requirement differs from literal')
        if record['cardEvidence'] in prices:
            raise ValueError('Native drawback price occurrence counted twice')
        prices.add(record['cardEvidence'])
        if record['repeatPolicy'] != {'type': 'Single'}:
            raise ValueError('Native drawback has no explicit repeated-instance permission')
        if any(ref not in evidence for ref in record['evidence']):
            raise ValueError('Native drawback card lacks exact evidence')
        runtime = record['runtimeEntry']
        if (runtime['id'] != record['id'] or runtime['name'] != record['name'] or
                runtime['cost'] != (cost if cost is not None else 0) or
                runtime['ranks'] != record['maxRank'] or runtime['requirements'] != fields['Требование']):
            raise ValueError('Native drawback display differs from source fields')
        if 'buybackPolicy' in record and record['buybackPolicy']['xpAmount'] is not None:
            raise ValueError('Native one-eye buyback price is not specified')
        for target in record.get('forbiddenNewDevelopmentPurchases', []):
            if 'не можете брать способность «' + target['name'] + '»' not in card:
                raise ValueError('Native drawback ability restriction lacks exact words')
    summary = data['inventorySummary']
    if (summary['canonicalRecords'] != len(seen) or
            summary['signedXPCardCount'] != sum(r['signedCostXP'] is not None for r in data['records']) or
            summary['eventOnlyCardCount'] != sum(r['eventOnly'] for r in data['records'])):
        raise ValueError('Native drawback occurrence inventory differs')


def _source(data, ref):
    excerpt = data['evidence'][ref]
    meta = data['sourceProvenance']
    return {'documentId': meta['documentId'], 'documentVersion': meta['revisionId'],
            'sheet': excerpt['tabId'], 'paragraph': None, 'originalText': excerpt['text']}


def _node(kind, ident, source, **fields):
    return {'type': kind, 'nodeId': ident, 'sourceRef': source, **fields}


def _materialize(tree, ident, source):
    result = copy.deepcopy(tree)
    result.update(nodeId=ident, sourceRef=source)
    if 'children' in result:
        result['children'] = [_materialize(child, ident + '/' + str(index), source)
                              for index, child in enumerate(result['children'])]
    return result


def native_drawback_runtime_entries(repo_root):
    data = load_native_drawback_rules(repo_root)
    result = []
    for record in data['records']:
        entry = copy.deepcopy(record['runtimeEntry'])
        entry['acquisitionSourceRef'] = _source(data, record['cardEvidence'])
        result.append(entry)
    return result


def apply_native_drawback_rules(definitions, repo_root, resolution_revision=None):
    data = load_native_drawback_rules(repo_root)
    by_id = {d['ref']['entryId']: d for d in definitions if d['ref']['packId'] == CORE}
    general = _source(data, data['policyEvidence']['general'])
    event = _source(data, data['policyEvidence']['eventOnly'])
    for record in data['records']:
        ident = record['id']
        if ident in by_id:
            raise ValueError('Native drawback collides with canonical identity: ' + ident)
        card = _source(data, record['cardEvidence'])
        title = _source(data, record['titleEvidence'])
        citations = {'native-drawback:' + ref: _source(data, ref)
                     for ref in [*record['evidence'], *data['policyEvidence'].values()]}
        if record['eventOnly']:
            requirements = _node('AllOf', ident + '/event', event, children=[
                _node('CreationPhase', ident + '/event/finalized', event, operator='EQ', operand='Finalized'),
                _node('ExternalEvidence', ident + '/event/confirmed', event,
                      evidenceKey='drawback-story-event', scopeId=ident, operator='EQ', operand=True)])
            price = {'type': 'Fixed', 'xp': '0', 'ap': '0', 'sourceRef': event}
        else:
            requirements = _node('AnyOf', ident + '/stage', general, children=[
                _node('CreationPhase', ident + '/stage/finalized', general, operator='EQ', operand='Finalized'),
                _node('AllOf', ident + '/stage/creating', general, children=[
                    _node('CreationPhase', ident + '/stage/creating/phase', general,
                          operator='EQ', operand='Creating'),
                    _materialize(record['creationRequirementTree'], ident + '/stage/creating/card', card)])])
            price = {'type': 'Conditional', 'condition': _node('CreationPhase', ident + '/price/creating', general,
                       operator='EQ', operand='Creating'),
                     'whenTrue': {'type': 'PerRank', 'xp': str(record['signedCostXP']), 'ap': '0', 'sourceRef': card},
                     'whenFalse': {'type': 'PerRank', 'xp': '0', 'ap': '0', 'sourceRef': general},
                     'sourceRef': general}
        item = {'ref': {'packId': CORE, 'entryId': ident}, 'name': record['name'], 'kind': 'DEVELOPMENT',
                'maxRank': record['maxRank'], 'repeatPolicy': {'type': 'Single'},
                'familyIds': ['drawback'], 'categoryIds': ['drawback'], 'classificationSources': citations,
                'parameterDefinitions': copy.deepcopy(record.get('parameterDefinitions', [])),
                'abilityOptionIds': [], 'sourceStatus': 'Confirmed', 'sourceRef': title,
                'requirementsText': record['requirementsText'], 'requirementSource': card,
                'nativeRequirements': requirements, 'priceRule': price}
        for parameter in item['parameterDefinitions']:
            parameter['sourceRef'] = card
        definitions.append(item)
        by_id[ident] = item
    # These clauses prohibit a new purchase of the named ability. They do not
    # delete an already owned ability when a later injury grants a drawback.
    for record in data['records']:
        for target in record.get('forbiddenNewDevelopmentPurchases', []):
            item = by_id.get(target['entryId'])
            if item is None or item['kind'] != 'DEVELOPMENT' or item['name'] != target['name']:
                raise ValueError('Native drawback restriction target identity differs: ' + target['entryId'])
            card = _source(data, record['cardEvidence'])
            ident = item['ref']['entryId'] + '/drawback/' + record['id']
            item.setdefault('nativeAdditionalRequirements', []).append(
                _node('Exclusive', ident, card, conflicting=_node('OwnedRank', ident + '/owned', card,
                    packId=CORE, entryId=record['id'], operator='GE', operand=1)))
            item['classificationSources']['native-drawback:' + record['cardEvidence']] = card
    return definitions
