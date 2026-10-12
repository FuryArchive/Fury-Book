"""Deterministic acquisition IR. Runtime consumes the declared FCP payload, never this parser.

The committed T01 evidence is a source excerpt set, not a price oracle input:
anchors/expected costs are deliberately never read. Structural definitions supply IDs
only; unsupported wording and unverified modules remain explicit blocking nodes.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path

CORE = 'dubl-3.69'
CHI = 'dubl-chi-3.69'
KINDS = {'development': 'DEVELOPMENT', 'attribute': 'ATTRIBUTE', 'skill': 'SKILL',
         'mana_rank': 'MANA', 'magic_school': 'SCHOOL', 'spell': 'SPELL', 'chi_bonus_rank': 'CHI_BONUS'}
ATTRIBUTES = {'Сила': 'STR', 'Ловкость': 'DEX', 'Телосложение': 'CON', 'Скорость': 'SPD',
              'Восприятие': 'PER', 'Интеллект': 'INT', 'Воля': 'WIL', 'Харизма': 'CHA'}
CORRECTIONS = {'feat_fcdd74622d21435d': 11012, 'feat_eaa70b65b370825b': 11791,
               'feat_bc96393fb969a8e3': 11797}


# Explicit stable semantic IDs, independent of display-name spelling.
KNOWLEDGE_FAMILY = 'knowledge'
KNOWLEDGE_MEMBERS = {'knowledge_academic': 274, 'knowledge_template': 278}
COMBAT_CRY_CATEGORY = 'combat-cry'
WEAPON_SPECIALIZATION = 'feat_2334f8b24105194c'
COLD_WEAPON_QUALIFIER = {'key': 'weaponTypeId', 'valueId': 'melee'}
BRANCH_BINDINGS = {'Боевые крики': 'access_17f99cc35c559d82'}
# Reviewed source roots in the perfect-ability section, not all descendants or name matches.
PERFECT_ROOT_CATEGORY = 'perfect-root'
PERFECT_ROOTS = {'feat_1bacaebcd614c5c5': 13624, 'feat_7c06b44361f99e86': 13655,
                 'feat_07a5933de10322e2': 13708}
PERFECT_LIMIT_PARAGRAPH = 13618


def conjunction_parts(text):
    parts = []; start = 0; depth = 0
    for i, char in enumerate(text):
        if char == '(': depth += 1
        elif char == ')':
            depth -= 1
            if depth < 0: return [text]
        elif char in ',;' and depth == 0:
            parts.append(text[start:i]); start = i + 1
    if depth: return [text]
    parts.append(text[start:])
    return parts


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def source_ref(evidence, paragraph=None, text=None):
    meta = evidence['provenance']
    p = evidence['sourceEvidence'].get(f'core:P{paragraph}', {})
    return {'documentId': meta['documentId'], 'documentVersion': meta['revisionId'],
            'sheet': p.get('tabId'), 'paragraph': paragraph,
            'originalText': p.get('text', '') if text is None else text}


def node(kind, node_id, source, **fields):
    return {'type': kind, 'nodeId': node_id, 'sourceRef': source, **fields}


def parse_requirement(text, node_id, source, definitions):
    """Conservative grammar: ordered conjunctions containing explicit OR groups.

    Commas in the named martial style list belong to its OR group; the tail after
    the final 'или' alternative belongs to AND. Parentheses/unknown lexical forms
    are never stripped to obtain a convenient match.
    """
    raw = text.strip().rstrip('.')
    local = dict(source, originalText=text)
    def sub(value, suffix):
        return parse_requirement(value.strip(), f'{node_id}/{suffix}', source, definitions)
    if raw in ('-', '—'):
        return node('AllOf', node_id, local, children=[], noRequirementsStatement='Source explicitly states no requirements')
    if not raw:
        return node('Unresolved', node_id, local, reason='missing-requirement-source')
    if raw.startswith('Боевые искусства:'):
        body = raw.split(':', 1)[1].strip()
        if ';' in body:
            styles, tail = body.split(';', 1)
            tails = [x.strip() for x in tail.split(',')]
        elif ' или ' in body:
            before, after = body.rsplit(' или ', 1)
            last, *tails = after.split(',')
            styles = before + ', ' + last
        else:
            return node('Unresolved', node_id, local, reason='ambiguous-martial-list-boundary')
        choices = [sub(x, f'style/{i}') for i, x in enumerate(re.split(r',| или ', styles))]
        return node('AllOf', node_id, local, children=[node('AnyOf', node_id+'/styles', local, children=choices)] + [sub(x, f'tail/{i}') for i, x in enumerate(tails)])
    # Split only outside qualifiers; one unsupported atom never discards siblings.
    parts = conjunction_parts(raw)
    if len(parts) > 1:
        return node('AllOf', node_id, local, children=[sub(x, i) for i, x in enumerate(parts)])
    if 'и/или' in raw:
        return node('Unresolved', node_id, local, reason='unresolved-qualified-or-compound-requirement')
    if ' или ' in raw:
        return node('AnyOf', node_id, local, children=[sub(x, i) for i, x in enumerate(raw.split(' или '))])
    knowledge = re.fullmatch(r'Знание\s+(\d+)\s*\(Любое\)', raw, re.I)
    if knowledge:
        return node('SkillFamily', node_id, local, packId=CORE, familyId=KNOWLEDGE_FAMILY,
                    operator='GE', operand=int(knowledge[1]))
    specialization = re.fullmatch(r'Специализация в оружии(?:\s*\(([^)]+)\))?', raw, re.I)
    if specialization:
        qualifier = specialization[1]
        if qualifier is not None and qualifier.lower() != 'холодное':
            return node('Unresolved', node_id, local, reason='unverified-weapon-qualifier:'+qualifier)
        return node('Specialization', node_id, local, packId=CORE, entryId=WEAPON_SPECIALIZATION,
                    operator='GE', operand=1, qualifier=COLD_WEAPON_QUALIFIER if qualifier else None)
    cries = re.fullmatch(r'Любые (два|три) боевых крика', raw, re.I)
    if cries:
        members = [d for d in definitions if COMBAT_CRY_CATEGORY in d.get('categoryIds', [])]
        if not members:
            return node('Unresolved', node_id, local, reason='missing-category-members:'+COMBAT_CRY_CATEGORY)
        predicate = node('AnyOf', node_id+'/candidates', local, children=[
            node('OwnedRank', node_id+'/candidate/'+d['ref']['entryId'], local, **d['ref'], operator='GE', operand=1)
            for d in members])
        return node('DistinctCount', node_id, local, predicate=predicate, uniqueness='Definition',
                    operator='GE', operand={'два': 2, 'три': 3}[cries[1].lower()])
    if raw in BRANCH_BINDINGS:
        return node('BranchAccess', node_id, local, packId=CORE, branchId=BRANCH_BINDINGS[raw], operator='EQ', operand=True)
    if '(' in raw or ')' in raw:
        return node('Unresolved', node_id, local, reason='unresolved-qualified-or-compound-requirement')
    if raw == 'Только при создании персонажа':
        return node('CreationPhase', node_id, local, operand='Creating', operator='EQ')
    explicit_spell = raw.lower().startswith('заклинание ')
    if explicit_spell:
        raw = raw[len('заклинание '):].strip(' «»"')
    match = re.fullmatch(r'(.+?)\s+(\d+)', raw)
    label, rank = (match[1], int(match[2])) if match else (raw, 1)
    if label in ATTRIBUTES and match:
        return node('Attribute', node_id, local, attributeId=ATTRIBUTES[label], operand=rank, operator='GE')
    candidates = [d for d in definitions if d['name'] == label and (not explicit_spell or d['kind'] == 'SPELL')]
    if len(candidates) != 1:
        return node('Unresolved', node_id, local, reason=('missing-reference:' if not candidates else 'ambiguous-reference:') + label)
    target = candidates[0]
    ref = target['ref']
    if target['kind'] == 'SPELL':
        if match:
            return node('Unresolved', node_id, local, reason='spell-rank-not-defined:'+label)
        return node('LearnedSpell', node_id, local, **ref, operator='EQ', operand=True)
    if target['kind'] == 'SKILL':
        return node('SkillFamily', node_id, local, packId=ref['packId'], familyId=ref['entryId'], operator='GE', operand=rank)
    return node('OwnedRank', node_id, local, **ref, operator='GE', operand=rank)


def walk(tree, include_exclusions=True):
    yield tree
    for child in tree.get('children', []):
        yield from walk(child, include_exclusions)
    for key in ('predicate', 'conflicting'):
        if key in tree and (key != 'conflicting' or include_exclusions):
            yield from walk(tree[key], include_exclusions)


def diagnose_catalog(definitions):
    """Keep exact references; detect all SCCs including self loops, without flattening OR."""
    index = defaultdict(list)
    diagnostics = []
    def emit(kind, definition, n, message):
        diagnostics.append({'kind': kind, 'definitionId': definition['ref']['entryId'],
                            'packId': definition['ref']['packId'], 'nodeId': n.get('nodeId'),
                            'message': message, 'sourceRef': n.get('sourceRef')})
    for d in definitions:
        index[(d['ref']['packId'], d['ref']['entryId'])].append(d)
    for values in index.values():
        if len(values) > 1:
            emit('duplicate-definition', values[0], values[0]['requirements'], 'Duplicate stable definition identity')
    edges = defaultdict(set)
    for d in definitions:
        own = (d['ref']['packId'], d['ref']['entryId'])
        dependencies = {n['nodeId'] for n in walk(d['requirements'], include_exclusions=False)}
        for n in walk(d['requirements']):
            if n['type'] == 'Unresolved':
                emit(n['reason'].split(':')[0], d, n, n['reason'])
            field = {'OwnedRank': 'entryId', 'Specialization': 'entryId', 'LearnedSpell': 'entryId', 'SkillFamily': 'familyId', 'BranchAccess': 'branchId'}.get(n['type'])
            if not field:
                continue
            key = (n['packId'], n[field]); targets = index.get(key, [])
            family = n['type'] == 'SkillFamily' and not targets
            if family:
                targets = [d for d in definitions if d['ref']['packId'] == n['packId'] and n[field] in d.get('familyIds', [])]
            if not targets:
                emit('missing-reference', d, n, ':'.join(key)); continue
            if len(targets) != 1 and not family:
                emit('ambiguous-reference', d, n, ':'.join(key))
            if n['nodeId'] in dependencies:
                edges[own].update((t['ref']['packId'], t['ref']['entryId']) for t in targets)
            target = targets[0]
            if n['type'] in ('OwnedRank', 'SkillFamily', 'Specialization'):
                maxima = [t.get('maxRank') for t in targets]
                maximum = max((v for v in maxima if v is not None), default=None)
                if family and any(v is not None and 0 <= n['operand'] <= v for v in maxima):
                    continue
                if maximum is None or (family and None in maxima):
                    emit('unresolved-reference-rank', d, n, ':'.join(key))
                elif n['operand'] < 0 or n['operand'] > maximum:
                    emit('rank-out-of-range', d, n, f"{n['operand']} outside 0..{maximum}: {key}")
            if n.get('accessOptionId') is not None:
                options = target.get('abilityOptionIds')
                if options is None or n['accessOptionId'] not in options:
                    emit('option-out-of-range', d, n, str(n['accessOptionId']))
    # Tarjan, stable traversal for reproducible diagnostics.
    count = 0; indices = {}; low = {}; stack = []; active = set()
    def visit(v):
        nonlocal count
        indices[v] = low[v] = count; count += 1; stack.append(v); active.add(v)
        for w in sorted(edges[v]):
            if w not in indices:
                visit(w); low[v] = min(low[v], low[w])
            elif w in active:
                low[v] = min(low[v], indices[w])
        if low[v] == indices[v]:
            component = []
            while True:
                w = stack.pop(); active.remove(w); component.append(w)
                if w == v: break
            if len(component) > 1 or v in edges[v]:
                for key in sorted(component):
                    emit('dependency-cycle', index[key][0], index[key][0]['requirements'], repr(sorted(component)))
    for key in sorted(index):
        if key not in indices: visit(key)
    return diagnostics


def apply_requirement_diagnostics(definitions, diagnostics):
    """Replace only the invalid atom. Potential SCCs do not prove infeasibility."""
    result = copy.deepcopy(definitions)
    local_kinds = {'missing-reference', 'ambiguous-reference', 'rank-out-of-range',
                   'option-out-of-range', 'unresolved-reference-rank'}
    for d in result:
        related = [x for x in diagnostics if (x['packId'], x['definitionId']) == (d['ref']['packId'], d['ref']['entryId'])]
        by_node = defaultdict(list)
        for diagnostic in related:
            if diagnostic['kind'] in local_kinds:
                by_node[diagnostic['nodeId']].append(diagnostic)
        def transform(n):
            if n['nodeId'] in by_node and n['type'] != 'Unresolved':
                return node('Unresolved', n['nodeId'], n['sourceRef'], reason='; '.join(
                    x['kind']+':'+x['message'] for x in by_node[n['nodeId']]))
            for field in ('children',):
                if field in n: n[field] = [transform(child) for child in n[field]]
            for field in ('predicate', 'conflicting'):
                if field in n: n[field] = transform(n[field])
            return n
        d['requirements'] = transform(d['requirements'])
        if any(x['kind'] == 'duplicate-definition' for x in related):
            src = d['requirements']['sourceRef']; id = d['ref']['entryId']
            d['requirements'] = node('AllOf', id+'/duplicate', src, children=[d['requirements'],
                node('Unresolved', id+'/duplicate-blocker', src, reason='duplicate-definition')])
    return result


def correct_development_payload(payload, repo_root):
    result = copy.deepcopy(payload)
    evidence = read(Path(repo_root)/'rulesets/dubl-3.69/acquisition/source_questions.json')
    for e in result.get('entries', []):
        p = CORRECTIONS.get(e['id'])
        if p:
            e['requirements'] = evidence['sourceEvidence'][f'core:P{p}']['text'].split(':', 1)[1].strip()
            e['acquisitionSourceRef'] = source_ref(evidence, p)
        if e['id'] == 'feat_9012c539238a1e6f':
            text = evidence['sourceEvidence']['core:P13387']['text']
            e['cost'] = int(text.split(':', 1)[1])
            e['acquisitionSourceRef'] = source_ref(evidence, 13387)
            # Remove only the obsolete cost-only diagnostic, never another field's blocker.
            if e.get('mechanicsConflict') == 'В рулбуке не определены поля: cost. Значения сохранены только для совместимости.':
                for field in ('incomplete', 'mechanicsConflict', 'conflictNote'): e.pop(field, None)
    return result


def duplicate_price_evidence(occurrences: list, evidence: dict):
    """Only agree on the price; never select one duplicate's inherited admission context."""
    if len(occurrences) < 2:
        return None
    sources, amounts, ranks = [], [], []
    for occurrence in occurrences:
        if occurrence['matchStatus'] != 'matched' or any(ref not in evidence['sourceEvidence'] for ref in occurrence['sourceRef']):
            return None
        rows = [(ref, evidence['sourceEvidence'][ref]['text']) for ref in occurrence['sourceRef']]
        prices = [(ref, re.fullmatch(r'Стоимость:\s*(\d+)\s*', text)) for ref, text in rows]
        prices = [(ref, match) for ref, match in prices if match]
        if len(prices) != 1:
            return None
        sources.append(prices[0][0]); amounts.append(int(prices[0][1][1]))
        caps = [re.fullmatch(r'Ранг(?:и)?:\s*(\d+)\s*', text) for _, text in rows]
        caps = [int(match[1]) for match in caps if match]
        ranks.append(caps[0] if len(caps) == 1 else None)
    if len(set(amounts)) != 1:
        return None
    return {'xp': str(amounts[0]), 'sources': sources,
            'maxRank': ranks[0] if len(set(ranks)) == 1 else None}


def build_acquisition_catalogs(repo_root):
    repo_root = Path(repo_root)
    evidence = read(repo_root/'rulesets/dubl-3.69/acquisition/source_questions.json')
    for p in evidence['sourceEvidence'].values():
        if 'sha256' in p and hashlib.sha256(p['text'].encode()).hexdigest() != p['sha256']:
            raise ValueError('Source excerpt checksum mismatch')
    runtime = {}; occurrence = defaultdict(list)
    for o in evidence['sourceOccurrences']:
        if o.get('runtimeId'): occurrence[o['runtimeId']].append(o)
    for pack in (CORE, CHI):
        root = repo_root/'shared/src/commonMain/resources/fcp'/pack
        manifest = read(root/'manifest.json')
        for entry in sorted(manifest['entries'], key=lambda e: (e.get('order', 0), e['path'])):
            if entry['kind'] == 'dubl.development':
                for d in correct_development_payload(read(root/entry['path']), repo_root)['entries']:
                    runtime[d['id']] = (pack, d)
            elif entry['kind'] == 'dubl.skills':
                for d in read(root/entry['path'])['skills']:
                    runtime[d['id']] = (pack, d)
            elif entry['kind'] == 'dubl.magic-equipment':
                for d in read(root/entry['path'])['spells']:
                    runtime[d['id']] = (pack, d)
    definitions = []
    for d in evidence['definitions']:
        if d['kind'] not in KINDS or not d['included']: continue
        pack, r = runtime.get(d['id'], (d['pack'], {}))
        if d['kind'] == 'chi_bonus_rank': pack = CHI
        occurrences = occurrence[d['id']]
        o = occurrences[0] if len(occurrences) == 1 else None
        confirmed = o is not None and o['matchStatus'] == 'matched'
        # A unique stable binding still needs retained and verified source excerpts.
        if confirmed:
            confirmed = all(ref in evidence['sourceEvidence'] for ref in o['sourceRef'])
        source = source_ref(evidence, int(o['sourceRef'][0].split('P')[1])) if confirmed else {
            'documentId': 'unverified:'+pack, 'documentVersion': '3.69',
            'originalText': r.get('requirements', ''), 'sheet': ','.join(r.get('sourceRefs', [])) or None, 'paragraph': None}
        maximum = d['rankBounds'][1] if d['rankBasis'] == 'SOURCE_TABLE' else None
        if confirmed:
            m = re.search(r'^Ранг(?:и)?:\s*(\d+)\s*$', o['fullRule'], re.M)
            if m: maximum = int(m[1])
        item = {'ref': {'packId': pack, 'entryId': d['id']}, 'name': d['name'], 'kind': KINDS[d['kind']],
                'maxRank': maximum, 'repeatPolicy': {'type': 'Unresolved', 'reason': 'Q_SOURCE_PARAMETER_IDENTITY'},
                'familyIds': [], 'categoryIds': [], 'classificationSources': {},
                'parameterDefinitions': [], 'abilityOptionIds': None if r.get('abilityOptions') else [],
                'sourceStatus': 'NeedsResolution' if confirmed else 'Unavailable', 'sourceRef': source,
                'requirementsText': r.get('requirements', ''),
                'priceRule': {'type': 'Unknown', 'reason': 'source-unverified' if not confirmed else 'Q_SOURCE_PRICE_CONTEXT', 'sourceRef': source}}
        duplicate_price = duplicate_price_evidence(occurrences, evidence)
        if duplicate_price:
            # All numerical citations survive. Branch/admission context has not
            # been reconciled here and remains an explicit local extraction gap.
            citations = {ref: source_ref(evidence, int(ref.split('P')[1])) for ref in duplicate_price['sources']}
            first = next(iter(citations.values()))
            item.update(sourceStatus='NeedsResolution', sourceRef=first, maxRank=duplicate_price['maxRank'],
                        duplicateSourceContext=True, requirementsText='')
            item['classificationSources'].update({'price:'+ref: value for ref, value in citations.items()})
            literal = {'xp': duplicate_price['xp'], 'ap': '0', 'sourceRef': first}
            item['priceRule'] = ({'type': 'Fixed', **literal} if duplicate_price['maxRank'] == 1 else
                {'type': 'TransitionTable', 'sourceRef': first, 'transitions': [{'fromRank': 0, 'toRank': 1, **literal}]})
        if d['id'] in KNOWLEDGE_MEMBERS:
            item['familyIds'] = [KNOWLEDGE_FAMILY]
            item['classificationSources']['family:'+KNOWLEDGE_FAMILY] = source_ref(evidence, KNOWLEDGE_MEMBERS[d['id']])
        if d['id'] in PERFECT_ROOTS:
            root_paragraph = PERFECT_ROOTS[d['id']]
            root_ref = f'core:P{root_paragraph}'
            limit_ref = f'core:P{PERFECT_LIMIT_PARAGRAPH}'
            if not confirmed or root_ref not in o['sourceRef'] or evidence['sourceEvidence'][root_ref]['tabId'] != evidence['sourceEvidence'][limit_ref]['tabId']:
                raise ValueError(f'Perfect root source binding requires review: {d["id"]}')
            item['categoryIds'].append(PERFECT_ROOT_CATEGORY)
            item['classificationSources']['category:'+PERFECT_ROOT_CATEGORY] = source_ref(evidence, root_paragraph)
        if confirmed:
            tag_refs = [ref for ref in o['sourceRef'] if evidence['sourceEvidence'][ref]['text'].strip() == '[Боевой крик]']
            if tag_refs:
                item['categoryIds'] = [COMBAT_CRY_CATEGORY]
                item['classificationSources']['category:'+COMBAT_CRY_CATEGORY] = source_ref(evidence, int(tag_refs[0].split('P')[1]))
            reqs = [(int(ref.split('P')[1]), evidence['sourceEvidence'][ref]['text']) for ref in o['sourceRef'] if re.match(r'^Требовани[ея]:', evidence['sourceEvidence'][ref]['text'])]
            if len(reqs) != 1:
                item['requirementsText'] = ''
            if len(reqs) == 1:
                p, text = reqs[0]; item['requirementsText'] = text.split(':', 1)[1].strip(); item['requirementSource'] = source_ref(evidence, p)
            prices = [(int(ref.split('P')[1]), evidence['sourceEvidence'][ref]['text']) for ref in o['sourceRef'] if re.fullmatch(r'Стоимость:\s*\d+\s*', evidence['sourceEvidence'][ref]['text'])]
            if len(prices) == 1:
                p, text = prices[0]; price = {'xp': str(int(text.split(':',1)[1])), 'ap': '0', 'sourceRef': source_ref(evidence, p)}
                if maximum == 1:
                    item['priceRule'] = {'type': 'Fixed', **price}
                else:
                    item['priceRule'] = {'type': 'TransitionTable', 'transitions': [{'fromRank': 0, 'toRank': 1, **price}], 'sourceRef': price['sourceRef']}
            if d['id'] == 'feat_7fc6fc6e63da9688' and 'каждый раз выбирая новый тип сопротивления' in o['fullRule']:
                item['repeatPolicy'] = {'type': 'DistinctParameter', 'parameterKeys': ['resistanceType'], 'requireUnique': True}
                item['parameterDefinitions'] = [{'key': 'resistanceType', 'domain': {'type': 'Unresolved', 'reason': 'Source type-to-stable-ID mapping requires review'}}]
        if d['kind'] in ('attribute', 'skill') and d['rankBasis'] == 'SOURCE_TABLE':
            start, stop, heading = (67, 112, 63) if d['kind'] == 'attribute' else (359, 389, 355)
            transitions = []
            for paragraph in range(start, stop, 3):
                text = evidence['sourceEvidence'][f'core:P{paragraph}']['text']
                match = re.fullmatch(r'(-?\d+) → (-?\d+)', text.strip())
                if not match:
                    raise ValueError(f'Unrecognized acquisition price row core:P{paragraph}')
                cost_text = evidence['sourceEvidence'][f'core:P{paragraph + 1}']['text'].strip()
                if not re.fullmatch(r'\d+', cost_text):
                    raise ValueError(f'Unrecognized acquisition price core:P{paragraph + 1}')
                transitions.append({'fromRank': int(match[1]), 'toRank': int(match[2]),
                    'xp': cost_text, 'ap': '0', 'sourceRef': source_ref(evidence, paragraph + 1)})
            item['priceRule'] = {'type': 'TransitionTable', 'transitions': transitions,
                                 'sourceRef': source_ref(evidence, heading)}
            if d['kind'] == 'skill':
                # Source P11655 halves ranks <=2; P11656 excludes creation. These
                # are explicit predicates for the shared evaluator, not runtime IDs
                # or expected prices obtained from the test oracle.
                discount_source = source_ref(evidence, 11655)
                phase_source = source_ref(evidence, 11656)
                assert 'до 2 ранга включительно' in discount_source['originalText']
                assert 'Не работает при создании персонажа' in phase_source['originalText']
                discounted = []
                for row in transitions:
                    if row['toRank'] <= 2:
                        xp = int(row['xp'])
                        if xp % 2:
                            # No rounding convention is stated. Omit only this
                            # discounted row so its quote is Unknown; retain the
                            # base price and all exactly representable rows.
                            continue
                        discounted.append({**row, 'xp': str(xp // 2), 'sourceRef': discount_source})
                    else:
                        discounted.append(row)
                base = item['priceRule']
                owned = node('OwnedRank', d['id']+'/price/self-taught', discount_source,
                    packId=CORE, entryId='feat_dd0ba74aedebce10', operator='GE', operand=1)
                item['priceRule'] = {'type': 'Conditional', 'sourceRef': phase_source,
                    'condition': node('CreationPhase', d['id']+'/price/creation', phase_source,
                                      operator='EQ', operand='Creating'),
                    'whenTrue': base,
                    'whenFalse': {'type': 'Conditional', 'condition': owned, 'sourceRef': discount_source,
                        'whenTrue': {'type': 'TransitionTable', 'transitions': discounted, 'sourceRef': discount_source},
                        'whenFalse': base}}
            item['sourceRef'] = source_ref(evidence, heading)
            item['sourceStatus'] = 'NeedsResolution'
            # Explicit general purchase permission, not an absent display field.
            permission = 62 if d['kind'] == 'attribute' else 203
            item['requirementSource'] = source_ref(evidence, permission)
            item['requirementsText'] = evidence['sourceEvidence'][f'core:P{permission}']['text']
            item['generalPurchasePermission'] = not r.get('template', False)
            if not r.get('template', False):
                # A fixed attribute/skill has one value whose rank is raised;
                # it is not a new independently specialized instance.
                item['repeatPolicy'] = {'type': 'Single'}
                item['sourceStatus'] = 'Confirmed'
            if r.get('template'):
                item['identityQuestion'] = 'Q_SOURCE_PARAMETER_IDENTITY'
                item['parameterDefinitions'] = [{'key': 'specialization', 'domain': {
                    'type': 'Unresolved', 'reason': 'Q_SOURCE_PARAMETER_IDENTITY'}}]
        if d['id'] == 'access_17f99cc35c559d82':
            # Reviewed binary access, not a ranked feat. These table rows identify
            # the two sources; numeric legacy option positions are not identities.
            expected = {12100: 'Боевые крики', 12103: 'Мораль', 12104: '1',
                        12105: 'Ци', 12106: '2', 12107: 'Требование: Командирский голос'}
            for paragraph, text in expected.items():
                if source_ref(evidence, paragraph)['originalText'].strip() != text:
                    raise ValueError('Combat cry access source binding requires review')
            permission = source_ref(evidence, 11906)
            if 'при создании персонажа' not in permission['originalText']:
                raise ValueError('Combat cry access purchase phase requires review')
            item.update(maxRank=1, repeatPolicy={'type': 'Single'}, abilityOptionIds=['morale', 'chi'],
                        sourceStatus='NeedsResolution', sourceRef=source_ref(evidence, 12100),
                        requirementsText='Командирский голос', requirementSource=source_ref(evidence, 12107))
            item['classificationSources'].update({
                'access:permission': permission, 'access:creation': source_ref(evidence, 11901),
                'option:morale': source_ref(evidence, 12103), 'option:chi': source_ref(evidence, 12105)})
            options = {key: {'type': 'Fixed', 'xp': '0', 'ap': str(ap), 'sourceRef': source_ref(evidence, paragraph)}
                       for key, ap, paragraph in [('morale', 1, 12104), ('chi', 2, 12106)]}
            item['priceRule'] = {'type': 'Conditional', 'sourceRef': permission,
                'condition': node('CreationPhase', d['id']+'/price/creation', permission, operator='EQ', operand='Creating'),
                'whenTrue': {'type': 'ByOption', 'options': options, 'sourceRef': source_ref(evidence, 12102)},
                'whenFalse': {'type': 'Unknown', 'reason': 'Unconfirmed post-creation access purchase policy', 'sourceRef': permission}}
        definitions.append(item)
    for item in definitions:
        id = item['ref']['entryId']; src = item.pop('requirementSource', item['sourceRef'])
        if item.pop('duplicateSourceContext', False):
            tree = node('Unresolved', id+'/duplicate-context', src,
                        reason='unmapped-duplicate-source-context: inherited admission/branch context requires reconciliation')
        elif item.pop('generalPurchasePermission', False):
            tree = node('AllOf', id+'/requirements', src, children=[],
                noRequirementsStatement='The cited general rule explicitly permits purchase of this fixed attribute/skill rank for XP; no additional entry prerequisite is stated.')
        elif item.get('identityQuestion'):
            tree = node('Unresolved', id+'/requirements', src, reason=item.pop('identityQuestion'))
        else:
            tree = parse_requirement(item['requirementsText'], id+'/requirements', src, definitions)
        # The root keeps exact wording/prefix for provenance, children keep atom wording.
        tree['sourceRef'] = src
        if item['sourceStatus'] == 'Unavailable':
            blocker = node('Unresolved', id+'/source', src, reason='source-unverified')
            if tree['type'] == 'AllOf' and tree.get('children'):
                tree['children'].append(blocker)
            else:
                tree = node('AllOf', id+'/requirements', src, children=[tree, blocker])
        _, r = runtime.get(id, ('', {}))
        if r.get('accessId'):
            access = node('BranchAccess', id+'/access', src, packId=CORE, branchId=r['accessId'], operator='EQ', operand=True)
            tree = node('AllOf', id+'/with-access', src, children=[tree, access])
        if id == 'feat_9012c539238a1e6f':
            tree['children'].append(node('Unresolved', id+'/parent-access', src, reason='Q_EAGLE_PARENT_ACCESS'))
        if id in PERFECT_ROOTS:
            limit_source = source_ref(evidence, PERFECT_LIMIT_PARAGRAPH)
            rivals = [node('OwnedRank', id+'/exclusive/rival/'+rival, limit_source,
                           packId=CORE, entryId=rival, operator='GE', operand=1)
                      for rival in PERFECT_ROOTS if rival != id]
            exclusion = node('Exclusive', id+'/exclusive', limit_source,
                             conflicting=node('AnyOf', id+'/exclusive/rivals', limit_source, children=rivals))
            tree = node('AllOf', id+'/with-exclusion', tree['sourceRef'], children=[tree, exclusion])
        item['requirements'] = tree
    diagnostics = diagnose_catalog(definitions)
    definitions = apply_requirement_diagnostics(definitions, diagnostics)
    resolution = hashlib.sha256((repo_root/'rulesets/dubl-3.69/resolutions.json').read_bytes()).hexdigest()
    result = {}
    for pack in (CORE, CHI):
        payload = {'schemaVersion': 1, 'resolutionRevision': resolution,
                   'definitions': [d for d in definitions if d['ref']['packId'] == pack],
                   'diagnostics': [d for d in diagnostics if d['packId'] == pack]}
        payload['catalogRevision'] = hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        result[pack] = payload
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo-root', type=Path, default=Path('.'))
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    for pack, payload in build_acquisition_catalogs(args.repo_root).items():
        path = args.output_dir/pack/'acquisition_catalog.json'; path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    main()
