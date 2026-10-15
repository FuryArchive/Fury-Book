"""Import bounded current Blood/rune acquisition facts and their display cards.

The native rune grade describes casting, not an acquisition rank. Blood currency
alternatives are explicit player choices and never imply an XP/AP conversion.
"""
from __future__ import annotations

import copy
import hashlib
import json
import re
from pathlib import Path


def load_native_magic_supplement(repo_root):
    path = Path(repo_root) / 'rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    if data['schemaVersion'] != 1:
        raise ValueError('Unsupported native magic supplement schema')
    for ref, excerpt in data['evidence'].items():
        if hashlib.sha256(excerpt['text'].encode()).hexdigest() != excerpt['textSha256']:
            raise ValueError('Native magic supplement excerpt hash mismatch: ' + ref)
        if not re.fullmatch(r'paragraphs/\d+', excerpt['path']) or excerpt['startIndex'] < 0:
            raise ValueError('Native magic supplement coordinates invalid: ' + ref)
    ids = set()
    for record in data['records']:
        if record['id'] in ids:
            raise ValueError('Duplicate native magic supplement ID: ' + record['id'])
        ids.add(record['id'])
        texts = [data['evidence'][ref]['text'] for ref in record['evidence']]
        joined = '\n'.join(texts)
        if not record['evidence'] or record['maxRank'] < 1:
            raise ValueError('Native magic supplement card lacks bounded identity/rank')
        price = record['price']
        if record['kind'] == 'SPELL':
            if record['maxRank'] != 1 or record['fields']['Стоимость'] != record['castingCostRaw']:
                raise ValueError('Native Blood spell casting fields disagree')
            mana = re.match(r'\d+', record['castingCostRaw'])
            if mana is None or int(mana[0]) != record['castingMana']:
                raise ValueError('Native Blood spell mana differs from the literal casting field')
            if record['fields']['Описание'] not in joined or record['castingCostRaw'] not in joined:
                raise ValueError('Native Blood spell fields lack literal support')
        elif 'runeGrade' in record:
            if (record['maxRank'] != 1 or price.get('xp') != '20' or price.get('ap') != '0'
                    or 'Стоимость изучения одной руны — 20 опыта' not in joined):
                raise ValueError('Native rune learning price/identity differs from source')
            if any(value not in texts for value in [record['runeGlyph'], str(record['runeGrade']),
                    record['name'].removeprefix('Руна: '), record['keyword'], record['benefit']]):
                raise ValueError('Native rune grade/name/effect differs from exact table cells')
        elif record['kind'] == 'SKILL':
            if 'Рунология можно повышать как обычное умение' not in joined:
                raise ValueError('Native Runology ordinary-skill policy lacks literal support')
            if record['defaultAttributeId'] != 'INT' or 'Проверка: Интеллект + Рунология (навык)' not in joined:
                raise ValueError('Native Runology attribute lacks exact source support')
        else:
            amount = price['literalAmount']
            if not re.search(r'\(' + re.escape(amount) + r'\)', joined):
                raise ValueError('Native Blood perk amount differs from source literal')
            if not re.search(r'Ранги:\s*' + str(record['maxRank']) + r'(?:\s|$)', joined):
                raise ValueError('Native Blood perk rank differs from source literal')
            if 'за опыт или очки способностей' not in joined:
                raise ValueError('Native Blood perk currency alternatives lack source permission')
            if price['alternatives'] != [dict(id='xp', currency='XP', amount=amount),
                                         dict(id='ap', currency='AP', amount=amount)]:
                raise ValueError('Native Blood perk alternatives differ from exact literal amount')
    return data


def _source(data, ref):
    e = data['evidence'][ref]
    meta = data['sourceProvenance']
    return {'documentId': e.get('documentId', meta['documentId']),
            'documentVersion': e.get('revisionId') or meta['revisionId'],
            'sheet': e['tabId'], 'paragraph': int(e['path'].split('/')[1]),
            'originalText': e['text']}


def _node(kind, ident, source, **fields):
    return {'type': kind, 'nodeId': ident, 'sourceRef': source, **fields}


def _remint(value, old_id, new_id):
    if isinstance(value, list):
        return [_remint(v, old_id, new_id) for v in value]
    if isinstance(value, dict):
        return {k: (v.replace(old_id + '/', new_id + '/', 1) if k == 'nodeId' and isinstance(v, str)
                    else _remint(v, old_id, new_id)) for k, v in value.items()}
    return value


def apply_native_magic_supplement(definitions, repo_root):
    try:
        from .native_magic_rules import load_native_magic_rules, _source as magic_source
    except ImportError:
        from native_magic_rules import load_native_magic_rules, _source as magic_source
    data = load_native_magic_supplement(repo_root)
    magic = load_native_magic_rules(repo_root)
    existing = {d['ref']['entryId'] for d in definitions}
    ordinary_skill = next(d for d in definitions if d['ref']['entryId'] == 'athletics')
    for record in data['records']:
        ident = record['id']
        if ident in existing:
            raise ValueError('Native supplement collides with canonical acquisition identity: ' + ident)
        source = _source(data, record['evidence'][0])
        item = {'ref': {'packId': 'dubl-3.69', 'entryId': ident}, 'name': record['name'],
                'kind': record['kind'], 'maxRank': record['maxRank'],
                'repeatPolicy': {'type': 'Single'}, 'familyIds': [], 'categoryIds': [],
                'classificationSources': {'native:' + ref: _source(data, ref) for ref in record['evidence']},
                'parameterDefinitions': [], 'abilityOptionIds': [], 'sourceStatus': 'Confirmed',
                'sourceRef': source, 'requirementsText': record.get('requirementsText', ''),
                'requirementSource': source}
        if record['kind'] == 'SKILL':
            item['priceRule'] = _remint(copy.deepcopy(ordinary_skill['priceRule']), 'athletics', ident)
            item['classificationSources'].update(copy.deepcopy(ordinary_skill['classificationSources']))
            item['generalPurchasePermission'] = True
        elif record['kind'] == 'SPELL':
            mana = record['castingMana']
            band = next(b for b in magic['spellLearningBands'] if b['minimumMana'] <= mana <= b['maximumMana'])
            if str(band['xp']) != record['price']['xp']:
                raise ValueError('Native Blood spell learning band differs from literal casting mana')
            price_src = magic_source(magic, band['evidence'][1])
            admission = magic_source(magic, magic['manaReserveEvidence']['learningPowerAdmission'])
            item['priceRule'] = {'type': 'Fixed', 'xp': str(band['xp']), 'ap': '0', 'sourceRef': price_src}
            item['nativeRequirements'] = _node('NumericProperty', ident+'/native/learning-power', admission,
                propertyId='MAGIC_POWER', operator='GE', operand=mana)
            item['classificationSources']['native:learning-power'] = admission
            item['classificationSources']['native:learning-band'] = price_src
        elif 'runeGrade' in record:
            item['priceRule'] = {'type': 'Fixed', 'xp': '20', 'ap': '0', 'sourceRef': source}
            item['generalPurchasePermission'] = True
        else:
            amount = record['price']['literalAmount']
            options = {}
            for choice in record['price']['alternatives']:
                literal = {'xp': amount if choice['id'] == 'xp' else '0',
                           'ap': amount if choice['id'] == 'ap' else '0', 'sourceRef': source}
                options[choice['id']] = ({'type': 'Fixed', **literal} if record['maxRank'] == 1 else
                    {'type': 'TransitionTable', 'transitions': [{'fromRank': 0, 'toRank': 1, **literal}],
                     'sourceRef': source})
                label = 'опыт' if choice['id'] == 'xp' else 'очки способностей'
                permission = next(_source(data, ref) for ref in record['evidence']
                                  if 'за опыт или очки способностей' in data['evidence'][ref]['text'])
                item['classificationSources']['option:'+choice['id']] = dict(permission, originalText=label)
                item['classificationSources']['payment:transition-options'] = permission
            item['abilityOptionIds'] = ['xp', 'ap']
            item['priceRule'] = {'type': 'ByOption', 'options': options, 'sourceRef': source}
            if record['repeatPolicy']['type'] != 'Single':
                limit = record['repeatPolicy']['instanceCountLimit']
                if limit != {'type': 'AttributeDivisor', 'attributeId': 'CON', 'divisor': 2,
                             'exactInequality': 'count*2<=effectiveCON'}:
                    raise ValueError('Unsupported native Blood repeat-count source policy')
                item['repeatPolicy'] = {'type': 'Independent'}
                item['instanceCountLimit'] = {'sourceRef': source, 'attributeId': 'CON',
                                              'numerator': 1, 'denominator': 2}
                item['classificationSources']['native:instance-limit'] = source
        definitions.append(item)
        existing.add(ident)
    return definitions


def apply_native_magic_supplement_bindings(definitions, repo_root):
    """Resolve exact owner-local spell qualifications from native card evidence."""
    data = load_native_magic_supplement(repo_root)
    authored = {r['id']: r.get('exactRequirementBindings', []) for r in data['records']}
    for item in definitions:
        bindings = authored.get(item['ref']['entryId'], [])
        for binding in bindings:
            hits = []
            def replace(value):
                if not isinstance(value, dict):
                    return value
                if value.get('sourceRef', {}).get('originalText') == binding['exactText']:
                    hits.append(value)
                    result = copy.deepcopy(binding['replacement'])
                    result.update(nodeId=value['nodeId'], sourceRef=value['sourceRef'])
                    return result
                return {k: [replace(v) for v in v] if isinstance(v, list)
                        else replace(v) if isinstance(v, dict) else v for k, v in value.items()}
            item['requirements'] = replace(item['requirements'])
            if len(hits) != 1:
                raise ValueError('Native magic supplement exact atom must match once: ' + binding['exactText'])
    return definitions


def native_magic_development_payload(repo_root):
    data = load_native_magic_supplement(repo_root)
    entries = []
    for r in data['records']:
        if r['kind'] != 'DEVELOPMENT':
            continue
        refs = [_source(data, ref) for ref in r['evidence']]
        rune = 'runeGrade' in r
        entry = {'id': r['id'], 'name': r['name'], 'section': r['section'], 'category': r['category'],
                 'cost': 20 if rune else int(r['price']['literalAmount']),
                 'costType': 'xp' if rune else 'choice', 'ranks': r['maxRank'],
                 'requirements': r.get('requirementsText', ''), 'benefit': r['benefit'],
                 'sourceRefs': [f"native:{s['documentId']}:{s['sheet']}:{s['paragraph']}" for s in refs],
                 'acquisitionSourceRef': refs[0]}
        if not rune:
            entry['abilityOptions'] = [{'source': 'Опыт', 'value': entry['cost']},
                                       {'source': 'Очки способностей', 'value': entry['cost']}]
            entry['notes'] = 'Оплата на выбор: опыт или очки способностей (стоимость указана в скобках).'
        else:
            entry.update(runeGlyph=r['runeGlyph'], runeGrade=r['runeGrade'], keyword=r['keyword'])
        entries.append(entry)
    return {'version': '3.69', 'entries': entries}


def correct_native_magic_equipment_payload(payload, repo_root):
    result = copy.deepcopy(payload)
    data = load_native_magic_supplement(repo_root)
    known = {s['id'] for s in result['spells']}
    for r in data['records']:
        if r['kind'] != 'SPELL':
            continue
        f = r['fields']
        entry = {'id': r['id'], 'name': r['name'], 'section': r['section'], 'category': r['category'],
                 'cost': r['castingMana'], 'school': r['schoolText'], 'manaText': r['castingCostRaw'],
                 'time': f.get('Время сотворения', ''), 'range': f.get('Дальность', ''),
                 'area': f.get('Область', ''), 'action': f.get('Действие', ''),
                 'duration': f.get('Длительность', ''), 'description': f['Описание'],
                 'enhancement': f.get('Усиление', ''), 'acquisitionSourceRef': _source(data, r['evidence'][0])}
        if r['id'] not in known:
            result['spells'].append(entry)
            known.add(r['id'])
        else:
            index = next(i for i, s in enumerate(result['spells']) if s['id'] == r['id'])
            result['spells'][index] = entry
    return result


def correct_native_magic_skills_payload(payload, repo_root):
    result = copy.deepcopy(payload)
    data = load_native_magic_supplement(repo_root)
    r = next(r for r in data['records'] if r['kind'] == 'SKILL')
    entry = {'id': r['id'], 'name': r['name'],
             'description': 'Активация вязей и идентификация незнакомых рун.',
             'category': 'KNOWLEDGE', 'defaultAttributeHint': 'INTELLIGENCE',
             'untrained': 'UNSPECIFIED', 'auto6': '', 'auto12': '', 'template': False,
             'sourceRefs': [f"native:{data['sourceProvenance']['documentId']}:{data['evidence'][ref]['tabId']}:{data['evidence'][ref]['path']}" for ref in r['evidence']],
             'acquisitionSourceRef': _source(data, r['evidence'][0])}
    result['skills'] = [s for s in result['skills'] if s['id'] != r['id']] + [entry]
    return result
