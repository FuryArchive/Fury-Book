"""Bound current native martial fields to explicit stable catalog identities.

Only acquisition excerpts are retained. A missing native revision is labelled
with the captured native-text hash, never presented as a Docs revision token.
Style spellings form a closed reviewed lexicon; unknown labels stay unresolved.
"""
from __future__ import annotations

import copy
import hashlib
import json
import re
from pathlib import Path

CORE = 'dubl-3.69'


def load_native_martial_rules(repo_root):
    path = Path(repo_root) / 'rulesets/dubl-3.69/acquisition/native_martial_rules.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    validate_native_martial_rules(data)
    return data


def _plain(text):
    return text.replace('\v', '\n')


def _requirement_shape(tree):
    """Compare typed predicates, excluding only their locators and AND/OR order."""
    result = {k: copy.deepcopy(v) for k, v in tree.items()
              if k not in ('nodeId', 'sourceRef', 'children', 'predicate', 'conflicting', 'noRequirementsEvidence')}
    if 'children' in tree:
        children = {_requirement_shape(c) for c in tree['children']}
        result['children'] = sorted(children)
    for key in ('predicate', 'conflicting'):
        if key in tree:
            result[key] = _requirement_shape(tree[key])
    return json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def _purchase_variants_agree(record, data, definitions, aliases, references, specializations, bindings):
    variants = record.get('nativeVariants', [])
    if not variants or any((v['xp'], v['maxRank']) != (record['xp'], record['maxRank']) for v in variants):
        return False
    # A table containing a technique under its style is an index, not a new
    # declaration that the detailed card's additional prerequisites disappear.
    detailed = [v for v in variants if not v.get('parentStyleRequirement')]
    if not detailed:
        return False
    shapes = set()
    for variant in detailed:
        source = _source(data, variant['requirementEvidence'][0] if variant['requirementEvidence'] else variant['titleEvidence'])
        tree = parse_native_martial_requirement(variant['requirementsText'], record['ref']['entryId']+'/variant',
            source, definitions, aliases, references, specializations, bindings)
        shapes.add(_requirement_shape(tree))
    return len(shapes) == 1


def validate_native_martial_rules(data):
    if data['schemaVersion'] != 1 or not data['devOnly']:
        raise ValueError('Unsupported native martial rules schema')
    if not re.fullmatch(r'[0-9a-f]{64}', data['source']['nativeParagraphContentSha256']):
        raise ValueError('Missing native martial capture hash')
    evidence = data['evidence']
    for ref, e in evidence.items():
        if (not e['tabId'] or not 0 <= e['startIndex'] < e['endIndex'] or
                hashlib.sha256(e['text'].encode()).hexdigest() != e['textSha256']):
            raise ValueError('Native martial excerpt coordinates/hash mismatch: ' + ref)
    seen = set()
    for r in data['records']:
        identity = (r['ref']['packId'], r['ref']['entryId'])
        if identity in seen or identity[0] != CORE:
            raise ValueError('Duplicate or foreign native martial identity')
        seen.add(identity)
        text = _plain(evidence[r['priceEvidence']]['text'])
        price = re.search(r'Стоимость\s*:\s*(\d+)', text, re.I)
        if price is None:
            price = re.fullmatch(r'\s*(\d+)\s*,\s*\d+\s*ранг[а-я]*\s*', text)
        if price is None or int(price[1]) != r['xp']:
            raise ValueError('Native martial price differs from literal: ' + identity[1])
        if r['rankEvidence'] is None:
            if r['maxRank'] is not None or not r.get('missingRankSource'):
                raise ValueError('Native martial omitted rank was silently supplied: ' + identity[1])
        else:
            rank_text = _plain(evidence[r['rankEvidence']]['text'])
            rank = re.search(r'Ранг(?:и|ов)?\s*:\s*(\d+)', rank_text, re.I)
            if rank is None:
                rank = re.fullmatch(r'\s*\d+\s*,\s*(\d+)\s*ранг[а-я]*\s*', rank_text)
            if rank is None or int(rank[1]) != r['maxRank'] or r['maxRank'] < 1:
                raise ValueError('Native martial rank differs from literal: ' + identity[1])
        refs = [ref for ref in [r['titleEvidence'], r['priceEvidence'], r['rankEvidence'],
                                *r['requirementEvidence']] if ref is not None]
        if any(ref not in evidence for ref in refs):
            raise ValueError('Missing native martial excerpt')
        if not r.get('parentStyleRequirement'):
            requirements, mandatory = [], []
            for ref in r['requirementEvidence']:
                for line in _plain(evidence[ref]['text']).splitlines():
                    m = re.match(r'Требовани[ея]\s*:\s*(.*)', line)
                    if m:
                        requirements.append(m[1].strip(' .'))
                    if line.startswith('Обязательное условие:'):
                        mandatory.append(line.split(':', 1)[1].strip(' .'))
            expected = '; '.join([*requirements, *mandatory]).strip(' .')
            if re.sub(r'[ \t]+', ' ', expected) != re.sub(r'[ \t]+', ' ', r['requirementsText'].strip(' .')):
                raise ValueError('Native martial requirement differs from literal: ' + identity[1])
        if 'runtimeEntry' in r:
            runtime = r['runtimeEntry']
            if (runtime['id'] != identity[1] or runtime['name'] != r['name'] or
                    runtime['cost'] != r['xp'] or runtime['ranks'] != (r['maxRank'] if r['maxRank'] is not None else 1) or
                    runtime['requirements'] != r['requirementsText']):
                raise ValueError('Native martial runtime fields differ from acquisition facts')
        if r.get('initialRankPurchaseConfirmed'):
            marker = r['initialRankPurchaseConfirmed']
            if (identity[1] != 'martial_technique_6e7e3bdb291edb76' or r['xp'] != 100 or
                    r['priceEvidence'] != 't.vojedk94bfa5:4608' or r['maxRank'] is not None or
                    r['rankEvidence'] is not None or not r.get('missingRankSource') or
                    marker['priceEvidence'] != r['priceEvidence'] or
                    (marker['fromRank'], marker['toRank']) != (0, 1) or
                    r.get('repeatPolicy', {'type': 'Single'}) != {'type': 'Single'} or
                    r.get('parameterDefinitions') or any(r.get(key) for key in (
                        'sourceConflict', 'sourceDraft', 'missingBenefitSource', 'technicalMissingContract'))):
                raise ValueError('Unreviewed native martial initial-rank purchase marker')
        for variant in r.get('nativeVariants', []):
            text = _plain(evidence[variant['priceEvidence']]['text'])
            price = re.search(r'Стоимость\s*:\s*(\d+)', text, re.I)
            if price is None:
                price = re.fullmatch(r'\s*(\d+)\s*,\s*\d+\s*ранг[а-я]*\s*', text)
            if price is None or int(price[1]) != variant['xp']:
                raise ValueError('Native martial variant price differs from literal')
            if variant['rankEvidence'] is not None:
                text = _plain(evidence[variant['rankEvidence']]['text'])
                rank = re.search(r'Ранг(?:и|ов)?\s*:\s*(\d+)', text, re.I)
                if rank is None:
                    rank = re.fullmatch(r'\s*\d+\s*,\s*(\d+)\s*ранг[а-я]*\s*', text)
                if rank is None or int(rank[1]) != variant['maxRank']:
                    raise ValueError('Native martial variant rank differs from literal')
            elif variant['maxRank'] is not None:
                raise ValueError('Native martial variant rank was silently supplied')
    styles = {r['ref']['entryId'] for r in data['records'] if r['kind'] == 'STYLE'}
    aliases = set()
    for alias in data['styleAliases']:
        if alias['exactText'] in aliases or alias['entryId'] not in styles:
            raise ValueError('Invalid native martial style identity binding')
        aliases.add(alias['exactText'])
        if not alias['evidence'] or any(ref not in evidence for ref in alias['evidence']):
            raise ValueError('Native martial alias lacks target evidence')
    for binding in data.get('coreReferenceBindings', []):
        e = binding['targetEvidence']
        target_name = binding.get('canonicalName', binding['exactText'])
        if (e['text'] != target_name or not e['documentId'] or not e['revisionId'] or
                hashlib.sha256(e['text'].encode()).hexdigest() != e['textSha256']):
            raise ValueError('Native martial core reference binding lacks exact evidence')
        if binding.get('matchBasis') == 'EXACT_WORDS_CASE_ONLY' and target_name.casefold() != binding['exactText'].casefold():
            raise ValueError('Native martial case-only binding changes source words')
    for binding in data.get('namedSpecializationBindings', []):
        if not any(binding['exactText'] in evidence[ref]['text'] for ref in binding['evidence']):
            raise ValueError('Native martial named specialization lacks exact evidence')
        for e in binding['targetEvidence']:
            if hashlib.sha256(e['text'].encode()).hexdigest() != e['textSha256']:
                raise ValueError('Native martial specialization target evidence hash mismatch')
    bindings = set()
    records = {r['ref']['entryId']: r for r in data['records']}
    for binding in data.get('requirementBindings', []):
        key = (binding['ownerId'], binding['exactText'])
        if key in bindings or binding['ownerId'] not in records:
            raise ValueError('Duplicate or missing native martial requirement binding owner')
        bindings.add(key)
        owner = records[binding['ownerId']]
        wordings = [owner['requirementsText'], *[v['requirementsText'] for v in owner.get('nativeVariants', [])]]
        if not any(binding['exactText'] in text for text in wordings):
            raise ValueError('Native martial binding differs from literal owner requirement')
        if not binding['evidence'] or any(ref not in evidence for ref in binding['evidence']):
            raise ValueError('Native martial requirement binding lacks exact evidence')
        target = binding.get('targetEvidence')
        if target and hashlib.sha256(target['text'].encode()).hexdigest() != target['textSha256']:
            raise ValueError('Native martial requirement binding target evidence hash mismatch')
    for record in data['records']:
        for variant in record.get('nativeVariants', []):
            if any(ref not in evidence for ref in variant.get('benefitEvidence', [])):
                raise ValueError('Native martial effect variant lacks exact evidence')
        if record.get('repeatPolicy'):
            if not record.get('repeatEvidence') or any(ref not in evidence for ref in record['repeatEvidence']):
                raise ValueError('Native martial repeat policy lacks exact evidence')
    if 'nativePurchaseInventory' in data:
        included = [row['priceEvidence'] for row in data['nativePurchaseInventory']]
        excluded = [row['priceEvidence'] for row in data['excludedNativePricedOccurrences']]
        if len(set(included + excluded)) != len(included + excluded):
            raise ValueError('Native martial price occurrence counted more than once')
        if len(included + excluded) != data['inventorySummary']['nativePricedOccurrences']:
            raise ValueError('Native martial occurrence inventory count differs')


def _source(data, ref):
    e = data['evidence'][ref]
    meta = data['source']
    version = meta.get('revisionId') or 'native-text-sha256:' + meta['nativeParagraphContentSha256']
    return {'documentId': meta['documentId'], 'documentVersion': version,
            'sheet': e['tabId'], 'paragraph': None, 'originalText': e['text']}


def native_martial_runtime_entries(repo_root):
    """Only new source-backed cards; callers merge by stable ID for UI payloads."""
    return [copy.deepcopy(r['runtimeEntry']) for r in load_native_martial_rules(repo_root)['records']
            if 'runtimeEntry' in r]


def _supported_combo_contract(data, repo_root):
    """Recognize only the implemented, exact source-bounded ordered contract."""
    ident = 'martial_technique_c4972b9f356e9d3d'
    path = Path(repo_root) / 'rulesets/dubl-3.69/acquisition/native_parameter_rules.json'
    if not path.exists():
        return False
    parameters = json.loads(path.read_text(encoding='utf-8'))
    records = [r for r in parameters['records'] if r['ownerId'] == ident]
    if len(records) != 1:
        return False
    record = records[0]
    fields = record.get('parameterDefinitions', [])
    if len(fields) != 1:
        return False
    field = fields[0]
    key = field['key']
    source = field.get('selectionSource', {})
    binding = next((b for b in data.get('requirementBindings', [])
                    if b['ownerId'] == ident and b['exactText'] == 'Любые два приема'), None)
    if binding is None:
        return False
    expected = {(n['packId'], n['entryId']) for n in binding['tree']['predicate']['children']}
    actual = {(n['packId'], n['entryId']) for n in source.get('definitions', [])}
    display_key = field.get('displayNameKey', field.get('displayLabelField'))
    return (record.get('maxRank') == 3 and
            record.get('repeatPolicy') == {'type': 'DistinctParameter', 'parameterKeys': [key], 'requireUnique': True} and
            field.get('domain') == {'type': 'UserDefinedIds'} and field.get('requiredAtRank', 1) == 1 and
            bool(display_key) and display_key != key and
            source.get('type') == 'OrderedOwnedInstances' and source.get('minimumRank') == 1 and
            source.get('baseLength') == 1 and source.get('plusTargetRank') is True and
            len(source.get('definitions', [])) == len(expected) and actual == expected)


def correct_native_martial_payload(payload, repo_root):
    """Promote bounded native acquisition fields while preserving existing effects."""
    data = load_native_martial_rules(repo_root)
    supported_combo = _supported_combo_contract(data, repo_root)
    result = copy.deepcopy(payload)
    entries = result.setdefault('entries', [])
    by_id = {entry['id']: entry for entry in entries}
    for record in data['records']:
        ident = record['ref']['entryId']
        if ident not in by_id:
            if 'runtimeEntry' not in record:
                raise ValueError('Existing martial runtime identity disappeared: ' + ident)
            entry = copy.deepcopy(record['runtimeEntry'])
            entries.append(entry)
            by_id[ident] = entry
        entry = by_id[ident]
        if entry['name'] != record['name']:
            raise ValueError('Native martial runtime identity/name mismatch: ' + ident)
        entry.update(cost=record['xp'], costType='xp', ranks=record['maxRank'] if record['maxRank'] is not None else 1,
                     requirements=record['requirementsText'])
        true_source_blocker = any(record.get(key) for key in (
            'sourceConflict', 'sourceDraft', 'missingBenefitSource', 'missingRankSource'))
        obsolete_combo_blocker = ident == 'martial_technique_c4972b9f356e9d3d' and supported_combo
        technical_blocker = record.get('technicalMissingContract') and not obsolete_combo_blocker
        if true_source_blocker or technical_blocker:
            entry.update(incomplete=True, mechanicsConflict=record.get('sourceConflict') or record.get('sourceDraft') or
                         ('Требуется поддержка выбора упорядоченной комбинации приёмов.' if technical_blocker else
                          'В актуальном источнике отсутствует обязательное поле ранга или эффекта.'))
        elif obsolete_combo_blocker:
            entry.pop('incomplete', None)
            entry.pop('mechanicsConflict', None)
        source_ref = (record['requirementEvidence'] or [record['titleEvidence']])[0]
        entry['acquisitionSourceRef'] = _source(data, source_ref)
        entry['sourceRefs'] = list(dict.fromkeys([
            *entry.get('sourceRefs', []),
            *['native-martial:' + ref for ref in [record['titleEvidence'], record['priceEvidence'],
                                                 record['rankEvidence'], *record['requirementEvidence']] if ref is not None],
        ]))
    return result


def parse_native_martial_requirement(text, node_id, source, definitions, style_aliases, core_references=None,
                                    named_specializations=None, authored_atoms=None):
    try:
        from .import_acquisition import conjunction_parts, node, parse_requirement
    except ImportError:  # Direct tools/rulebook/import_acquisition.py invocation.
        from import_acquisition import conjunction_parts, node, parse_requirement

    raw = re.sub(r'[ \t]+', ' ', text.strip().rstrip('.'))
    local = dict(source, originalText=text)

    def sub(value, suffix):
        return parse_native_martial_requirement(value.strip(), node_id + '/' + str(suffix),
                                                source, definitions, style_aliases, core_references,
                                                named_specializations, authored_atoms)

    def style(label, suffix):
        src = dict(source, originalText=label)
        if label == 'Любое':
            candidates = sorted(set(style_aliases.values()))
            return node('AnyOf', node_id + '/' + suffix, src, children=[
                node('OwnedRank', node_id + '/' + suffix + '/' + target, src,
                     packId=CORE, entryId=target, operator='GE', operand=1)
                for target in candidates])
        target = style_aliases.get(label)
        if target is None:
            return node('Unresolved', node_id + '/' + suffix, src,
                        reason='missing-native-martial-style:' + label)
        return node('OwnedRank', node_id + '/' + suffix, src,
                    packId=CORE, entryId=target, operator='GE', operand=1)

    if raw.startswith('Боевые искусства:'):
        body = raw.split(':', 1)[1].strip()
        if ';' in body:
            listed, tail = body.split(';', 1)
            tails = [x.strip() for x in conjunction_parts(tail) if x.strip()]
            labels = [x.strip() for x in re.split(r',| или ', listed) if x.strip()]
        else:
            tokens = [x.strip() for x in re.split(r',| или ', body) if x.strip()]
            # A closed style label extends the OR list. A following non-style
            # atom is an AND tail, e.g. Содзицу, Ураганная атака.
            boundary = next((i for i, x in enumerate(tokens)
                             if x != 'Любое' and x not in style_aliases), len(tokens))
            labels, tails = tokens[:boundary], tokens[boundary:]
        if not labels:
            return node('Unresolved', node_id, local, reason='unmapped-native-martial-list-boundary')
        choices = [style(label, 'style/' + str(i)) for i, label in enumerate(labels)]
        group = node('AnyOf', node_id + '/styles', local, children=choices)
        return node('AllOf', node_id, local,
                    children=[group] + [sub(value, 'tail/' + str(i)) for i, value in enumerate(tails)])
    parts = conjunction_parts(raw)
    if len(parts) > 1 or (parts and parts[0] != raw):
        return node('AllOf', node_id, local, children=[sub(value, i) for i, value in enumerate(parts)])
    if raw.startswith('Боевые искусства (') and raw.endswith(')'):
        return style(raw[len('Боевые искусства ('):-1], 'style')
    if raw.startswith('Кунг-фу: ') and raw[len('Кунг-фу: '):] in style_aliases:
        return style(raw[len('Кунг-фу: '):], 'style')
    if raw in style_aliases:
        target = style_aliases[raw]
        return node('OwnedRank', node_id, local, packId=CORE, entryId=target, operator='GE', operand=1)
    if raw in (core_references or {}):
        return node('OwnedRank', node_id, local, packId=CORE,
                    entryId=core_references[raw], operator='GE', operand=1)
    if raw in (named_specializations or {}):
        binding = named_specializations[raw]
        return node('Specialization', node_id, local, packId=CORE, entryId=binding['entryId'],
                    qualifier={'key': binding['parameterKey'], 'valueId': binding['valueId']},
                    operator='GE', operand=binding['minimumRank'])
    if raw in (authored_atoms or {}):
        def materialize(tree, suffix):
            result = copy.deepcopy(tree)
            result.update(nodeId=node_id + suffix, sourceRef=local)
            if 'children' in result:
                result['children'] = [materialize(child, suffix + '/' + str(i))
                                      for i, child in enumerate(result['children'])]
            if 'predicate' in result:
                result['predicate'] = materialize(result['predicate'], suffix + '/candidate')
            return result
        return materialize(authored_atoms[raw], '')
    if raw == 'Штука с парированием':
        return node('Unresolved', node_id, local,
                    reason='native-source-draft-no-definition:Штука с парированием')
    return parse_requirement(raw, node_id, local, definitions)


def apply_native_martial_rules(definitions, repo_root, resolution_revision):
    data = load_native_martial_rules(repo_root)
    by_id = {item['ref']['entryId']: item for item in definitions if item['ref']['packId'] == CORE}
    for record in data['records']:
        ident = record['ref']['entryId']
        if ident not in by_id:
            if 'runtimeEntry' not in record:
                raise ValueError('Existing martial identity disappeared: ' + ident)
            item = {'ref': dict(record['ref']), 'name': record['name'], 'kind': 'DEVELOPMENT',
                    'familyIds': [], 'categoryIds': [], 'classificationSources': {},
                    'parameterDefinitions': [], 'abilityOptionIds': []}
            definitions.append(item)
            by_id[ident] = item
        item = by_id[ident]
        if item['kind'] != 'DEVELOPMENT' or item['name'] != record['name']:
            raise ValueError('Native martial stable identity/name mismatch: ' + ident)
        src = _source(data, record['titleEvidence'])
        price_src = _source(data, record['priceEvidence'])
        req_src = _source(data, record['requirementEvidence'][0]) if record['requirementEvidence'] else src
        item.update(sourceStatus='Confirmed', sourceRef=src, maxRank=record['maxRank'],
                    repeatPolicy=copy.deepcopy(record.get('repeatPolicy', {'type': 'Single'})),
                    requirementsText=record['requirementsText'],
                    requirementSource=req_src, abilityOptionIds=[])
        item.pop('duplicateSourceContext', None)
        for ref in [ref for ref in [record['titleEvidence'], record['priceEvidence'], record['rankEvidence'],
                                   *record['requirementEvidence']] if ref is not None]:
            item.setdefault('classificationSources', {})['native-martial:' + ref] = _source(data, ref)
        for variant in record.get('nativeVariants', []):
            for ref in [ref for ref in [variant['titleEvidence'], variant['priceEvidence'], variant['rankEvidence'],
                                       *variant['requirementEvidence'], *variant.get('benefitEvidence', [])]
                        if ref is not None]:
                item['classificationSources']['native-martial:' + ref] = _source(data, ref)
        for ref in record.get('repeatEvidence', []):
            item['classificationSources']['native-martial:' + ref] = _source(data, ref)
        literal = {'xp': str(record['xp']), 'ap': '0', 'sourceRef': price_src}
        if record.get('initialRankPurchaseConfirmed'):
            if item['repeatPolicy'] != {'type': 'Single'} or item['parameterDefinitions']:
                raise ValueError('Native martial initial rank cannot infer a persistent/repeat identity')
            item['classificationSources']['purchase:initial-rank-confirmed'] = price_src
        # A card price establishes the first rank. No retained native excerpt
        # states that the price repeats for every rank of these martial cards.
        item['priceRule'] = ({'type': 'Unknown', 'reason': 'conflicting-current-native-martial-prices',
                              'sourceRef': price_src} if record.get('conflictingPrice') else
                             {'type': 'Fixed', **literal} if record['maxRank'] == 1 else
                             {'type': 'TransitionTable', 'transitions': [
                                 {'fromRank': 0, 'toRank': 1, **literal}], 'sourceRef': price_src})
    aliases = {a['exactText']: a['entryId'] for a in data['styleAliases']}
    core_references = {a['exactText']: a['entryId'] for a in data.get('coreReferenceBindings', [])}
    named_specializations = {a['exactText']: a for a in data.get('namedSpecializationBindings', [])}
    techniques_by_name = {}
    for candidate in data['records']:
        if candidate['kind'] == 'TECHNIQUE':
            techniques_by_name.setdefault(candidate['name'], []).append(candidate)
    repeated_names = {name: candidates for name, candidates in techniques_by_name.items() if len(candidates) > 1}
    record_contexts = {r['ref']['entryId']: {v['styleName'] for v in r.get('nativeVariants', [])}
                       for r in data['records']}
    authored_bindings = {}
    for binding in data.get('requirementBindings', []):
        authored_bindings.setdefault(binding['ownerId'], {})[binding['exactText']] = binding['tree']
        for ref in binding['evidence']:
            by_id[binding['ownerId']]['classificationSources']['native-martial:' + ref] = _source(data, ref)
        if binding.get('targetEvidence'):
            proof = binding['targetEvidence']
            by_id[binding['ownerId']]['classificationSources']['native-martial-target:' + proof['textSha256']] = {
                'documentId': proof['documentId'], 'documentVersion': proof['revisionId'],
                'sheet': proof['tabId'], 'paragraph': None, 'originalText': proof['text']}
    for record in data['records']:
        item = by_id[record['ref']['entryId']]
        # A repeated technique title is disambiguated only by an exact shared
        # native style context. Different techniques in unrelated schools retain
        # different IDs; a remaining multi-match is still source-unresolved.
        contexts = record_contexts[record['ref']['entryId']]
        context_references = dict(core_references)
        for name, candidates in repeated_names.items():
            local = [r for r in candidates if contexts.intersection(record_contexts[r['ref']['entryId']])]
            if len(local) == 1:
                context_references[name] = local[0]['ref']['entryId']
        tree = parse_native_martial_requirement(
            item['requirementsText'], item['ref']['entryId'] + '/requirements',
            item['requirementSource'], definitions, aliases, context_references, named_specializations,
            authored_bindings.get(record['ref']['entryId'], {}))
        if record.get('alternativeParentStyleIds'):
            from_types = [{'type': 'OwnedRank', 'nodeId': item['ref']['entryId'] + '/parent-style/' + target,
                           'sourceRef': item['requirementSource'], 'packId': CORE, 'entryId': target,
                           'operator': 'GE', 'operand': 1} for target in record['alternativeParentStyleIds']]
            tree = {'type': 'AnyOf', 'nodeId': item['ref']['entryId'] + '/requirements',
                    'sourceRef': item['requirementSource'], 'children': from_types}
        purchase_agrees = _purchase_variants_agree(record, data, definitions, aliases, context_references,
            named_specializations, authored_bindings.get(record['ref']['entryId'], {})) if record.get('sourceConflict') else True
        full_effects = {v.get('benefitSha256') for v in record.get('nativeVariants', []) if not v.get('parentStyleRequirement')}
        if record.get('sourceConflict') and purchase_agrees and len(full_effects) > 1:
            item['classificationSources']['effect:conflicting-martial-descriptions'] = item['sourceRef']
            item['sourceStatus'] = 'NeedsResolution'
        if record.get('missingBenefitSource'):
            item['classificationSources']['effect:missing-martial-description'] = item['sourceRef']
            item['sourceStatus'] = 'NeedsResolution'
        blockers = []
        for key, reason in [('sourceConflict', 'conflicting-current-native-martial-variants'),
                            ('sourceDraft', 'current-native-martial-draft-card'),
                            ('missingBenefitSource', 'missing-current-native-martial-benefit'),
                            ('missingRankSource', 'missing-current-native-martial-rank-maximum'),
                            ('technicalMissingContract', 'technical-missing-ordered-combo-parameter-contract')]:
            if record.get(key):
                if key == 'sourceConflict' and purchase_agrees:
                    continue
                if key == 'missingBenefitSource':
                    continue
                if key == 'missingRankSource' and record.get('initialRankPurchaseConfirmed'):
                    continue
                blockers.append({'type': 'Unresolved', 'nodeId': item['ref']['entryId'] + '/native/' + key,
                                 'sourceRef': item['sourceRef'], 'reason': reason})
        if record.get('missingRankSource'):
            item['sourceStatus'] = 'NeedsResolution'
        if blockers:
            item['sourceStatus'] = 'NeedsResolution'
            tree = {'type': 'AllOf', 'nodeId': item['ref']['entryId'] + '/native/requirements',
                    'sourceRef': item['requirementSource'], 'children': [tree, *blockers]}
        item['nativeRequirements'] = tree
    return definitions
