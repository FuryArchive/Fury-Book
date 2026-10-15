"""Apply bounded authenticated magic/Chi acquisition excerpts, never legacy prices.

The JSON retains acquisition fields and native coordinates, not book effects. A
numeric card cost establishes its first purchase only. Further rank prices need
an explicit each-rank statement; no author ruling is widened by this importer.
"""
from __future__ import annotations

import hashlib
import copy
import json
import re
from pathlib import Path


def load_native_magic_rules(repo_root):
    path = Path(repo_root) / 'rulesets/dubl-3.69/acquisition/native_magic_rules.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    validate_native_magic_rules(data)
    return data


def _texts(data, refs):
    return [data['evidence'][ref]['text'] for ref in refs]


def validate_native_magic_rules(data):
    if data['schemaVersion'] != 1:
        raise ValueError('Unsupported native magic acquisition evidence schema')
    for ref, excerpt in data['evidence'].items():
        if hashlib.sha256(excerpt['text'].encode()).hexdigest() != excerpt['textSha256']:
            raise ValueError(f'Native magic excerpt hash mismatch: {ref}')
        if excerpt['source'] not in data['sources'] or not excerpt['tabId'] or not excerpt['path']:
            raise ValueError(f'Native magic excerpt source coordinates missing: {ref}')
        if not 0 <= excerpt['startIndex'] < excerpt['endIndex']:
            raise ValueError(f'Invalid native magic excerpt range: {ref}')
    for band in data['spellLearningBands']:
        texts = _texts(data, band['evidence'])
        if texts != [f"{band['minimumMana']}-{band['maximumMana']}", str(band['xp'])]:
            raise ValueError('Native spell learning band differs from literal table cells')
    seen = set()
    for fact in data['bindings']:
        key = (fact['ref']['packId'], fact['ref']['entryId'])
        if key in seen:
            raise ValueError(f'Duplicate native magic binding: {key}')
        seen.add(key)
        texts = _texts(data, fact['evidence'])
        joined = '\n'.join(texts)
        if fact['kind'] == 'SPELL':
            for mana in fact['manaValues']:
                if not any(re.fullmatch(rf'Стоимость:\s*{mana}\s*', t) for t in texts):
                    raise ValueError('Native spell mana differs from retained literal')
                prices = [b['xp'] for b in data['spellLearningBands']
                          if b['minimumMana'] <= mana <= b['maximumMana']]
                if prices != [fact['xp']]:
                    raise ValueError('Native spell learning price differs from source table')
        elif fact['kind'] == 'ACCESS':
            if str(fact['ap']) not in texts or 'Ценность' not in texts:
                raise ValueError('Native magic access AP differs from table cell')
        elif fact.get('school'):
            if fact['ap'] != 1 or 'потратить 1 очко способностей' not in joined:
                raise ValueError('Native Chi school price differs from source statement')
        else:
            unit = fact.get('xp', 0) or fact.get('ap', 0)
            if not (re.search(rf'Стоимость:\s*{unit}(?:\s|$)', joined)
                    or re.search(rf'стоимость {unit}(?:\s|,)', joined)
                    or re.search(rf'стоит {unit} опыта', joined)):
                raise ValueError(f'Native acquisition price lacks literal support: {key}')
        if fact.get('unitPerRank') and not re.search(
                r'за ранг|Каждый ранг.*стоит|каждый ранг.*стоит|каждый из которых стоит', joined):
            raise ValueError(f'Native per-rank price lacks explicit each-rank support: {key}')


def _source(data, ref):
    excerpt = data['evidence'][ref]
    meta = data['sources'][excerpt['source']]
    # Some connected Docs omit revisionId. Label the excerpt-set hash explicitly;
    # never claim that it was a native revision token.
    version = meta.get('revisionId') or 'excerpt-set-sha256:' + hashlib.sha256(
        json.dumps({k: v for k, v in data['evidence'].items() if v['source'] == excerpt['source']},
                   ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    return {'documentId': meta['documentId'], 'documentVersion': version,
            'sheet': excerpt['tabId'], 'paragraph': excerpt['paragraph'],
            'originalText': excerpt['text']}


def _node(kind, id, src, **fields):
    return {'type': kind, 'nodeId': id, 'sourceRef': src, **fields}


def _native_tree(tree, source):
    result = copy.deepcopy(tree)
    def attach(node, fallback):
        node.setdefault('nodeId', fallback)
        atom_text = node.get('sourceRef', {}).get('originalText', source['originalText'])
        node['sourceRef'] = dict(source, originalText=atom_text)
        if node['type'] == 'MagicPower':
            node.update(type='NumericProperty', propertyId='MAGIC_POWER')
        for index, child in enumerate(node.get('children', [])):
            attach(child, node['nodeId'] + '/' + str(index))
        for key in ('predicate', 'conflicting', 'condition'):
            if key in node:
                attach(node[key], node['nodeId'] + '/' + key)
    attach(result, result.get('nodeId', 'native/requirements'))
    return result


def apply_native_magic_rules(definitions, repo_root, resolution_revision):
    data = load_native_magic_rules(repo_root)
    by_ref = {(f['ref']['packId'], f['ref']['entryId']): f for f in data['bindings']}
    school_refs = [f['ref'] for f in data['bindings'] if f.get('school')]
    for item in definitions:
        fact = by_ref.get((item['ref']['packId'], item['ref']['entryId']))
        if fact is None:
            continue
        if item['kind'] != ('DEVELOPMENT' if fact['kind'] == 'ACCESS' else fact['kind']):
            raise ValueError('Native magic acquisition kind differs from structural identity')
        refs = fact['evidence']
        src = _source(data, refs[0])
        texts = _texts(data, refs)
        item.update(sourceRef=src, sourceStatus='Confirmed', maxRank=fact['maxRank'],
                    repeatPolicy={'type': 'Single'}, abilityOptionIds=[])
        item.pop('duplicateSourceContext', None)
        for ref in refs:
            item.setdefault('classificationSources', {})['native:' + ref] = _source(data, ref)
        if fact['kind'] == 'SPELL':
            mana = fact['manaValues'][0]
            band = next(b for b in data['spellLearningBands'] if b['minimumMana'] <= mana <= b['maximumMana'])
            price_src = _source(data, band['evidence'][1])
            item['priceRule'] = {'type': 'Fixed', 'xp': str(fact['xp']), 'ap': '0', 'sourceRef': price_src}
            admission = _source(data, data['manaReserveEvidence']['learningPowerAdmission'])
            item.update(requirementsText=admission['originalText'], requirementSource=admission)
            item['nativeRequirements'] = _node('NumericProperty',
                item['ref']['entryId'] + '/native/learning-power', admission,
                propertyId='MAGIC_POWER', operator='GE', operand=mana)
            item['classificationSources']['native:learning-power'] = admission
            for ref in band['evidence']:
                item['classificationSources']['native:' + ref] = _source(data, ref)
            continue
        price_index = next((i for i, text in enumerate(texts)
                            if re.match(r'Стоимость:', text) or 'стоимость' in text
                            or 'стоит' in text or 'потратить 1 очко' in text), 0)
        price_src = _source(data, refs[price_index])
        literal = {'xp': str(fact.get('xp', 0)), 'ap': str(fact.get('ap', 0)), 'sourceRef': price_src}
        item['priceRule'] = ({'type': 'PerRank', **literal} if fact.get('unitPerRank') else
                             {'type': 'Fixed', **literal} if fact['maxRank'] == 1 else
                             {'type': 'TransitionTable', 'transitions': [
                                 {'fromRank': 0, 'toRank': 1, **literal}], 'sourceRef': price_src})
        item['requirementsText'] = fact.get('requirementsText', '')
        req_index = next((i for i, text in enumerate(texts) if text.startswith(('Требование:', 'Требования:'))), 0)
        item['requirementSource'] = _source(data, refs[req_index])
        if fact.get('requirementTree'):
            item['nativeRequirements'] = _native_tree(fact['requirementTree'], item['requirementSource'])
        if fact.get('generalPurchasePermission'):
            reviewed = fact.get('completeCardReview', {})
            if (not reviewed.get('noAdditionalPurchasePrerequisiteStated')
                    or not {'Стоимость', 'Ранги', 'Эффект'}.issubset(reviewed.get('fields', []))):
                raise ValueError('Native no-additional-prerequisite review is not bounded to a complete priced card')
            item['nativeRequirements'] = _node('AllOf', item['ref']['entryId'] + '/native/purchase', price_src,
                children=[], noRequirementsStatement='The complete current priced/ranked Основы Ци card was reviewed; no additional acquisition prerequisite is stated.')
        if fact.get('globalInnerAdmissionEvidence'):
            permission = _source(data, fact['globalInnerAdmissionEvidence'])
            item.setdefault('nativeAdditionalRequirements', []).append(_node('OwnedRank',
                item['ref']['entryId'] + '/native/inner-admission', permission,
                packId='dubl-chi-3.69', entryId='chi_development_6b44546d9d5fa11b', operator='GE', operand=1))
        id = item['ref']['entryId']
        if fact['kind'] == 'ACCESS':
            permission = _source(data, data['accessCreationPermission'])
            item['classificationSources']['native:access-creation-permission'] = permission
            phase = _node('CreationPhase', id + '/native/creating', permission, operator='EQ', operand='Creating')
            unknown = _node('Unresolved', id + '/native/later', permission,
                            reason='Current source states creation access; post-creation access purchase permission is not stated')
            item['nativeRequirements'] = _node('AnyOf', id + '/native/access', src, children=[phase, unknown])
            item['priceRule'] = {'type': 'Conditional', 'sourceRef': src, 'condition': phase,
                                 'whenTrue': {'type': 'Fixed', **literal},
                                 'whenFalse': {'type': 'Unknown', 'sourceRef': src,
                                               'reason': 'Post-creation magic access price policy is not stated'}}
        if fact.get('manaPolicy'):
            phase = _node('CreationPhase', id + '/native/creating', src, operator='EQ', operand='Creating')
            approval = _node('ExternalEvidence', id + '/native/gm-increase', price_src,
                             evidenceKey='gm-mana-reserve-increase', scopeId='mana:rank', operator='EQ', operand=True)
            item['nativeRequirements'] = _node('AnyOf', id + '/native/permission', price_src,
                                                children=[phase, approval])
            item['requirementSource'] = price_src
        if id in ('mana:rank', 'feat_bf7b04eb68961725'):
            item['classificationSources']['economic:mana-reserve'] = _source(
                data, data['manaReserveEvidence']['eachRankAndMaximum'])
            if id == 'mana:rank':
                item['classificationSources']['mana:first-power'] = _source(
                    data, data['manaReserveEvidence']['firstPowerGrant'])
        if fact.get('school'):
            # The first school has the stated one-AP price. The source requires
            # permission and an unspecified additional price for syncretism.
            others = [_node('OwnedRank', id + '/native/rival/' + r['entryId'], src,
                            packId=r['packId'], entryId=r['entryId'], operator='GE', operand=1)
                      for r in school_refs if r != item['ref']]
            exclusive = _node('Exclusive', id + '/native/one-school', price_src,
                              conflicting=_node('AnyOf', id + '/native/rivals', price_src, children=others))
            approval = _node('ExternalEvidence', id + '/native/syncretism-permission', price_src,
                             evidenceKey='gm-chi-syncretism', scopeId=id, operator='EQ', operand=True)
            item.setdefault('nativeAdditionalRequirements', []).append(_node('AnyOf',
                id + '/native/school-permission', price_src, children=[exclusive, approval]))
            item['priceRule'] = {'type': 'Conditional', 'sourceRef': price_src, 'condition': exclusive,
                                 'whenTrue': {'type': 'Fixed', **literal},
                                 'whenFalse': {'type': 'Unknown', 'sourceRef': price_src,
                                               'reason': 'Source requires an additional syncretism price but does not state its amount'}}
    return definitions
