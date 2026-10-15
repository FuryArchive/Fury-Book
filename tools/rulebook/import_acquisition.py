"""Deterministic acquisition IR. Runtime consumes the declared FCP payload, never this parser.

The committed T01 evidence is a source excerpt set, not a price oracle input:
anchors/expected costs are deliberately never read. Structural definitions supply IDs
only; unsupported wording and unverified modules remain explicit blocking nodes.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import itertools
import json
import re
from collections import defaultdict
from pathlib import Path

try:
    from .acquisition_coverage_records import (load_source_rules, validate_source_rules, authority_revision,
        resolution_revision, FIXED, CARRY, INITIAL)
except ImportError:  # Direct CLI invocation.
    from acquisition_coverage_records import (load_source_rules, validate_source_rules, authority_revision,
        resolution_revision, FIXED, CARRY, INITIAL)

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
# These cited cards carry a persistent selection or explicit repeat permission;
# their instances require a parameter contract, rather than a scalar rank track.
PARAMETERIZED_DEVELOPMENTS = {'feat_cd965f927a426cc2', WEAPON_SPECIALIZATION,
    'feat_54bec8a33d2f92ba', 'feat_7fc6fc6e63da9688', 'feat_a834e752f2e3132b',
    'feat_a06e094956b0c61e', 'feat_dee6d91d0a987242', 'feat_e15a39ad2d1a7aaa',
    'feat_3acde4a62c69d01d'}


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
    # A trailing punctuation separator does not introduce a missing requirement.
    # Preserve genuinely blank input and empty internal atoms as unresolved.
    while len(parts) > 1 and not parts[-1].strip():
        parts.pop()
    return parts


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def source_ref(evidence, paragraph=None, text=None):
    meta = evidence['provenance']
    p = evidence['sourceEvidence'].get(f'core:P{paragraph}', {})
    return {'documentId': meta['documentId'], 'documentVersion': meta['revisionId'],
            'sheet': p.get('tabId'), 'paragraph': paragraph,
            'originalText': p.get('text', '') if text is None else text}


def native_source_ref(provenance, excerpt):
    """Native cell paths/offsets stay in the bounded canonical evidence record."""
    version = (provenance.get('revisionId') or provenance.get('documentVersion') or
               excerpt.get('documentVersion') or 'excerpt-sha256:'+excerpt['textSha256'])
    return {'documentId': provenance['documentId'], 'documentVersion': version,
            'sheet': excerpt['tabId'], 'paragraph': int(excerpt['path'].split('/')[1]),
            'originalText': excerpt['text']}


def validate_native_excerpt(excerpt):
    if (not re.fullmatch(r'(?:p|paragraphs)/\d+(?:/r\d+c\d+/\d+)*', excerpt['path']) or
        not isinstance(excerpt['startIndex'], int) or excerpt['startIndex'] < 0 or
        hashlib.sha256(excerpt['text'].encode()).hexdigest() != excerpt['textSha256']):
        raise ValueError('Invalid native source excerpt locator or checksum')


def validate_native_development_facts(data):
    if data['schemaVersion'] != 1 or not data['devOnly']:
        raise ValueError('Unsupported native development source schema')
    seen = set()
    for fact in data['accessPrices']:
        ref = fact['definitionRef']; key = (ref['packId'], ref['entryId'])
        if key in seen or ref['packId'] != CORE or not ref['entryId'].startswith('access_'):
            raise ValueError('Duplicate or invalid native access binding')
        seen.add(key); validate_native_excerpt(fact['nameSource'])
        if fact['nameSource']['text'].strip() != fact['name'] or not fact['options']:
            raise ValueError('Native access name or options differ from exact source')
        option_ids = set()
        for option in fact['options']:
            validate_native_excerpt(option['source']); validate_native_excerpt(option['price'])
            if not option['id'] or option['id'] in option_ids or not re.fullmatch(r'\d+', option['price']['text'].strip()):
                raise ValueError('Invalid native access option or price literal')
            option_ids.add(option['id'])
    for excerpt in data['accessPricePermission']:
        validate_native_excerpt(excerpt)
    if not all('при создании персонажа' in e['text'].lower() for e in data['accessPricePermission']):
        raise ValueError('Native access purchase stage requires explicit source')
    seen = set()
    for fact in data['skillSpecializations']:
        if fact['entryId'] in seen:
            raise ValueError('Duplicate native skill identity binding')
        seen.add(fact['entryId']); validate_native_excerpt(fact['evidence'])
        for excluded in fact.get('excludedLabels', []):
            validate_native_excerpt(excluded['evidence'])
            if excluded['label'].casefold() not in excluded['evidence']['text'].casefold():
                raise ValueError('Excluded fixed skill name is absent from exact source')
    if data.get('resistanceParameters'):
        fact = data['resistanceParameters']
        validate_native_excerpt(fact['permission']); validate_native_excerpt(fact['categorySource'])
        if ('каждый раз выбирая новый тип сопротивления' not in fact['permission']['text'] or
            fact['categorySource']['text'] != 'Стихийный урон'):
            raise ValueError('Resistance parameter repeat/domain permission differs from source')
        for choice in fact['choices']:
            validate_native_excerpt(choice['evidence'])
        if len({c['id'] for c in fact['choices']}) != len(fact['choices']):
            raise ValueError('Duplicate resistance parameter identity')
    for fact in data.get('coreCards', []):
        for field in ('title', 'price', 'ranks', 'requirement', 'repeatEvidence'):
            validate_native_excerpt(fact[field])
        if fact.get('stagePermission'):
            validate_native_excerpt(fact['stagePermission'])
            if 'с разрешения Мастера' not in fact['stagePermission']['text']:
                raise ValueError('Native core card stage permission differs from source')
        if (fact['title']['text'] != fact['name'] or
            not re.fullmatch(r'Стоимость:\s*\d+', fact['price']['text']) or
            not re.fullmatch(r'Ранги:\s*\d+', fact['ranks']['text'])):
            raise ValueError('Native core card identity, price or rank differs from literal')
    for fact in data.get('generalSkills', []):
        validate_native_excerpt(fact['evidence'])
        if 'общее умение' not in fact['evidence']['text']:
            raise ValueError('Native scalar skill identity lacks explicit general-skill source')
    for fact in data.get('legacyDuplicates', []):
        for field in ('title', 'legacyTitle', 'price', 'ranks', 'requirement', 'benefit'):
            validate_native_excerpt(fact[field])
        if fact['title']['text'] != fact['canonicalName']:
            raise ValueError('Legacy duplicate canonical source title differs')
    return data


def load_native_development_facts(repo_root):
    path = Path(repo_root)/'rulesets/dubl-3.69/acquisition/native_development_prices.json'
    return validate_native_development_facts(read(path)) if path.exists() else None


def native_occurrence_overlay(evidence, repo_root):
    """Bind reviewed native occurrences without rewriting the immutable audit corpus."""
    path = Path(repo_root)/'rulesets/dubl-3.69/acquisition/native_occurrence_bindings.json'
    if not path.exists():
        return evidence, {'version': '3.69', 'entries': []}
    data = read(path); result = copy.deepcopy(evidence); supplement = []
    if data['sourceProvenance'] != {k: evidence['provenance'][k] for k in ('documentId', 'revisionId')}:
        raise ValueError('Native occurrence source revision differs from immutable audit')
    by_source = {o['sourceId']: o for o in result['sourceOccurrences']}
    seen = set(); definition_ids = {d['id'] for d in result['definitions']}
    for binding in data['bindings']:
        source_id = binding['sourceId']; entry_id = binding['entryId']
        if source_id in seen or source_id not in by_source:
            raise ValueError('Duplicate or missing native source occurrence binding')
        seen.add(source_id); occurrence = by_source[source_id]
        if occurrence['sourceRef'] != binding['sourceRefs'] or occurrence.get('runtimeId'):
            raise ValueError('Native occurrence binding would overwrite a prior match')
        for locator in binding['nativeLocators']:
            excerpt = evidence['sourceEvidence'][locator['sourceEvidenceRef']]
            if (excerpt['sha256'] != locator['exactTextSha256'] or excerpt['tabId'] != locator['tabId'] or
                excerpt['startIndex'] != locator['startIndex']):
                raise ValueError('Native occurrence binding source locator/checksum differs')
        occurrence.update(runtimeId=entry_id, matchStatus='matched')
        if not binding['newDefinition']:
            if entry_id not in definition_ids:
                raise ValueError('Native occurrence alias points to absent canonical identity')
            continue
        if entry_id in definition_ids:
            raise ValueError('New native definition duplicates canonical identity')
        definition_ids.add(entry_id)
        text = occurrence['fullRule'].replace('\v', '\n')
        price = re.findall(r'^Стоимость:\s*(\d+)\s*$', text, re.M)
        ranks = re.findall(r'^Ранг(?:и)?:\s*(\d+)\s*$', text, re.M)
        requirements = re.findall(r'^Требовани[ея]:\s*(.*)$', text, re.M)
        if len(price) != 1 or len(ranks) > 1 or len(requirements) != 1:
            raise ValueError('Native supplemental card price/rank/requirements need review')
        maximum = int(ranks[0]) if ranks else None
        benefit = text.split('Выгода:', 1)[1].strip() if 'Выгода:' in text else ''
        record = {'id': entry_id, 'name': binding['name'], 'section': 'Ветки способностей',
                  'category': binding['category'], 'tags': [binding['category']],
                  'costType': 'xp', 'cost': int(price[0]), 'ranks': maximum,
                  'requirements': requirements[0].strip(), 'benefit': benefit,
                  'accessId': binding['accessId'], 'sourceRefs': binding['sourceRefs']}
        if maximum is None:
            record.update(incomplete=True, mechanicsConflict='В актуальном источнике не указано максимальное число рангов.')
        supplement.append(record)
        result['definitions'].append({'id': entry_id, 'name': binding['name'], 'kind': 'development',
            'pack': CORE, 'included': True, 'rankBounds': [0, maximum],
            'rankBasis': 'SOURCE_RANK_LITERAL' if maximum is not None else 'SOURCE_OMITS_RANK_FIELD'})
    return result, {'version': '3.69', 'entries': supplement}


def build_native_development_supplement(repo_root):
    repo_root = Path(repo_root)
    evidence = read(repo_root/'rulesets/dubl-3.69/acquisition/source_questions.json')
    payload = native_occurrence_overlay(evidence, repo_root)[1]
    if (repo_root/'rulesets/dubl-3.69/acquisition/native_drawback_rules.json').exists():
        try:
            from .native_drawback_rules import native_drawback_runtime_entries
        except ImportError:
            from native_drawback_rules import native_drawback_runtime_entries
        payload['entries'].extend(native_drawback_runtime_entries(repo_root))
    if (repo_root/'rulesets/dubl-3.69/acquisition/native_weapon_proficiencies.json').exists():
        try:
            from .native_weapon_proficiencies import native_weapon_proficiency_payload
        except ImportError:
            from native_weapon_proficiencies import native_weapon_proficiency_payload
        payload['entries'].extend(native_weapon_proficiency_payload(repo_root)['entries'])
    if len({e['id'] for e in payload['entries']}) != len(payload['entries']):
        raise ValueError('Duplicate canonical native core supplement display identity')
    return payload


def apply_native_requirement_bindings(definitions, repo_root):
    path = Path(repo_root)/'rulesets/dubl-3.69/acquisition/native_dependency_bindings.json'
    if not path.exists():
        return definitions
    data = read(path); provenance = data['sourceProvenance']; bindings = {}
    for binding in data['bindings']:
        key = (binding['ownerId'], binding['nodeId'], binding['exactText'])
        if key in bindings or not binding['evidence']:
            raise ValueError('Duplicate or uncited native dependency binding')
        for excerpt in binding['evidence']:
            validate_native_excerpt(excerpt)
        bindings[key] = binding
    seen = set()
    def replacement_tree(value, node_id, source):
        result = {**value, 'nodeId': node_id, 'sourceRef': source}
        if 'children' in value:
            result['children'] = [replacement_tree(child, node_id+'/native/'+str(i), source)
                                  for i, child in enumerate(value['children'])]
        for key in ('predicate', 'conflicting'):
            if key in value:
                result[key] = replacement_tree(value[key], node_id+'/native/'+key, source)
        return result
    def transform(owner, value):
        key = (owner, value['nodeId'], value['sourceRef']['originalText'])
        if key in bindings:
            seen.add(key); binding = bindings[key]
            return replacement_tree(binding['replacement'], value['nodeId'], native_source_ref(provenance, binding['evidence'][0]))
        result = dict(value)
        if 'children' in value:
            result['children'] = [transform(owner, child) for child in value['children']]
        for field in ('predicate', 'conflicting'):
            if field in value:
                result[field] = transform(owner, value[field])
        return result
    for definition in definitions:
        if definition['ref']['packId'] == CORE:
            definition['requirements'] = transform(definition['ref']['entryId'], definition['requirements'])
    missing = set(bindings) - seen
    if missing:
        raise ValueError('Native dependency binding no longer matches exact owner/node/text: '+repr(sorted(missing)[:3]))
    by_id = {d['ref']['entryId']: d for d in definitions if d['ref']['packId'] == CORE}
    for choice in data.get('canonicalSpecializations', []):
        if choice['definitionId'] not in ('knowledge_template', 'performance_template', 'profession_template'):
            continue
        definition = by_id[choice['definitionId']]
        parameter = next(p for p in definition['parameterDefinitions'] if p['key'] == 'specialization')
        for excerpt in choice['evidence']:
            validate_native_excerpt(excerpt)
        meta = dict(choice.get('sourceProvenance', provenance)); excerpt = choice['evidence'][0]
        for key in ('documentId', 'revisionId', 'documentVersion'):
            if key in excerpt:
                meta[key] = excerpt[key]
        existing = next((c for c in parameter.get('choices', []) if c['id'] == choice['valueId']), None)
        if existing:
            if existing['label'] != choice['label']:
                raise ValueError('Native specialization choice label conflicts')
        else:
            parameter.setdefault('choices', []).append({'id': choice['valueId'], 'label': choice['label'],
                'sourceRef': native_source_ref(meta, excerpt)})
    return definitions


def apply_native_parameter_rules(definitions, repo_root):
    """Consume exact source choices; unimplemented selection contracts stay explicit."""
    path = Path(repo_root)/'rulesets/dubl-3.69/acquisition/native_parameter_rules.json'
    if not path.exists():
        return definitions
    data = read(path)
    if data['schemaVersion'] != 1 or not data['devOnly']:
        raise ValueError('Unsupported native parameter source schema')
    for excerpt in data['evidence'].values():
        validate_native_excerpt(excerpt)
    provenance = data['sourceProvenance']
    by_id = {d['ref']['entryId']: d for d in definitions if d['ref']['packId'] == CORE}
    seen = set()
    for record in data['records']:
        ident = record['ownerId']
        if ident in seen or ident not in by_id or not record['evidence']:
            raise ValueError('Duplicate, missing or uncited native parameter owner')
        seen.add(ident); item = by_id[ident]
        if item['name'] != record['name'].removesuffix(' (доступ)') or item['maxRank'] != record['maxRank']:
            raise ValueError('Native parameter identity/rank differs from exact canonical binding')
        def citation(ref):
            excerpt = data['evidence'][ref]
            meta = dict(record.get('sourceProvenance', provenance))
            for key in ('documentId', 'revisionId', 'documentVersion'):
                if key in excerpt:
                    meta[key] = excerpt[key]
            return native_source_ref(meta, excerpt)
        source = citation(record['evidence'][0])
        parameters = []; parameter_keys = set()
        for parameter in record['parameterDefinitions']:
            key = parameter['key']; domain = parameter['domain']
            if (not key or key in parameter_keys or
                not 1 <= parameter.get('requiredAtRank', 1) <= record['maxRank']):
                raise ValueError('Invalid or duplicate native rank parameter')
            parameter_keys.add(key)
            if domain['type'] == 'StableIds':
                values = domain['valueIds']
                if not values or any(not isinstance(v, str) or not v for v in values) or len(set(values)) != len(values):
                    raise ValueError('Invalid native stable parameter domain')
            elif domain['type'] not in ('UserDefinedIds', 'Unresolved'):
                raise ValueError('Unsupported native parameter domain')
            result = {k: copy.deepcopy(parameter[k]) for k in ('key', 'domain', 'label', 'requiredAtRank',
                      'selectionSource', 'uniqueWithinGroup') if k in parameter}
            if parameter.get('displayNameKey') or parameter.get('displayLabelField'):
                result['displayNameKey'] = parameter.get('displayNameKey', parameter.get('displayLabelField'))
            if parameter.get('identityDomain'):
                result['identityDomain'] = parameter['identityDomain']
            elif key == 'organizationId':
                result['identityDomain'] = 'organization'
            elif key == 'weaponId':
                result['identityDomain'] = 'weapon-kind'
            if parameter.get('choices'):
                choices = []
                for choice in parameter['choices']:
                    evidence_refs = choice.get('evidence', [])
                    if not evidence_refs:
                        raise ValueError('Native parameter choice lacks exact evidence')
                    choice_source = citation(evidence_refs[0])
                    output_choice = {'id': choice['id'], 'label': choice['label'], 'sourceRef': choice_source}
                    weapon_contract = record.get('weaponSelectionContract', {})
                    if key == weapon_contract.get('weaponParameterKey'):
                        classification = weapon_contract['knownWeaponClassById'].get(choice['id'])
                        if classification is None:
                            raise ValueError('Known weapon choice lacks exact source classification')
                        output_choice['parameterValues'] = {weapon_contract['classificationParameterKey']: classification}
                    choices.append(output_choice)
                if len({c['id'] for c in choices}) != len(choices):
                    raise ValueError('Duplicate native parameter choice identity')
                if domain['type'] == 'StableIds' and any(c['id'] not in domain['valueIds'] for c in choices):
                    raise ValueError('Native choice is outside its stable parameter domain')
                result['choices'] = choices
            parameters.append(result)
        if (record['repeatPolicy']['type'] == 'DistinctParameter' and
            not set(record['repeatPolicy']['parameterKeys']) <= parameter_keys):
            raise ValueError('Native repeat policy references an undefined parameter')
        item.update(parameterDefinitions=parameters, repeatPolicy=copy.deepcopy(record['repeatPolicy']))
        item['classificationSources']['native:parameter-identity'] = source
        if any(p.get('selectionSource', {}).get('type') == 'OrderedOwnedInstances' for p in parameters):
            item['nativeAdditionalRequirements'] = [n for n in item.get('nativeAdditionalRequirements', [])
                if n.get('reason') != 'technical-missing-ordered-combo-parameter-contract']
            def without_technical_combo(tree):
                if tree.get('reason') == 'technical-missing-ordered-combo-parameter-contract':
                    return None
                result = copy.deepcopy(tree)
                if 'children' in result:
                    result['children'] = [child for value in result['children']
                        if (child := without_technical_combo(value)) is not None]
                return result
            if 'nativeRequirements' in item:
                pruned = without_technical_combo(item['nativeRequirements'])
                if pruned is None:
                    raise ValueError('Ordered Combo source admission cannot consist solely of a technical blocker')
                item['nativeRequirements'] = pruned
        if record.get('requirementsOverride') is not None:
            item['nativeRequirements'] = complete_native_requirement_tree(record['requirementsOverride'], ident+'/requirements', source)
        if record.get('selectedInstanceRequirement'):
            selected = record['selectedInstanceRequirement']
            item.setdefault('nativeAdditionalRequirements', []).append(node('SelectedInstance', ident+'/selected-instance', source,
                parameterKey=selected['parameterKey'],
                predicate=node('SkillFamily', ident+'/selected-instance/rank', source,
                    packId=CORE, familyId=selected['familyId'], operator='GE', operand=selected['minimumRank'])))
        if record.get('matchingBranchAccess'):
            matching = record['matchingBranchAccess']
            item.setdefault('nativeAdditionalRequirements', []).append(node('BranchAccess', ident+'/matching-access', source,
                packId=CORE, branchId=matching['branchId'], operator='EQ', operand=True,
                parameterBindings={key: key for key in matching['parameterKeys']}))
        weapon_kinds = record.get('weaponKindSelectionContract')
        if weapon_kinds is not None:
            keys = [p['key'] for p in parameters if p.get('selectionSource', {}).get('type') == 'OwnedWeaponKinds']
            if (len(keys) != weapon_kinds['countPerInstance'] or
                    not weapon_kinds['requiresActualProficiency'] or
                    not weapon_kinds['additionalInstancesAddNewKinds']):
                raise ValueError('Native repeated weapon spell lacks exact mastered/new-kind contract')
            item['newParameterValueRequirement'] = {'parameterKeys': keys, 'minimumNewValues': 1,
                                                     'sourceRef': source}
        grant = record.get('includedGrant')
        if grant is not None:
            keys = grant.get('parameterKeys') or [grant['parameterKey']]
            grant_refs = [ref for ref in record['evidence'] if any(text in data['evidence'][ref]['text']
                for text in ('получаете одну ремесленную специализацию', 'стоимостью сотворения 0'))]
            if not grant_refs or grant.get('chargeXP') != '0':
                raise ValueError('Included grant lacks an explicit source gift statement')
            rules = []
            for key in keys:
                parameter = next(p for p in record['parameterDefinitions'] if p['key'] == key)
                targets = {c['id']: {'packId': CORE, 'entryId': c.get('grantsDefinitionId', c['id'])}
                           for c in parameter['choices']}
                rules.append({'sourceRef': citation(grant_refs[0]), 'parameterKey': key,
                    'activateAtRank': parameter.get('requiredAtRank', 1), 'targetByValue': targets,
                    'targetRank': 1, 'requireNewOwnership': False, 'retainsSourceEligibility': True})
            item['includedGrants'] = rules
        pending = [key for key in ('includedGrantPerRank',) if key in record]
        if pending:
            item.setdefault('nativeAdditionalRequirements', []).append(node('Unresolved', ident+'/parameter-contract', source,
                reason='UNIMPLEMENTED_CONFIRMED_PARAMETER_CONTRACT: '+','.join(pending)))
        for ref in record['evidence']:
            item['classificationSources']['native:parameter:'+ref] = citation(ref)
    return definitions


def apply_native_development_prices(definitions, facts):
    """Confirm only cited price facets; admission and finalized policy stay independent."""
    if facts is None:
        return definitions
    by_id = {d['ref']['entryId']: d for d in definitions if d['ref']['packId'] == CORE}
    provenance = facts['sourceProvenance']
    permission = native_source_ref(provenance, facts['accessPricePermission'][-1])
    for fact in facts['accessPrices']:
        item = by_id.get(fact['definitionRef']['entryId'])
        if item is None or item['ref'] != fact['definitionRef'] or item['name'] != fact['name']:
            raise ValueError('Native access binding differs from canonical identity')
        options = {o['id']: {'type': 'Fixed', 'xp': '0', 'ap': str(int(o['price']['text'])),
                             'sourceRef': native_source_ref(provenance, o['price'])}
                   for o in fact['options']}
        if len(options) == 1:
            creation = next(iter(options.values()))
            item['abilityOptionIds'] = []
        else:
            creation = {'type': 'ByOption', 'options': options,
                        'sourceRef': native_source_ref(provenance, fact['nameSource'])}
            item['abilityOptionIds'] = list(options)
        item['priceRule'] = {'type': 'Conditional', 'sourceRef': permission,
            'condition': node('CreationPhase', item['ref']['entryId']+'/price/creation', permission,
                              operator='EQ', operand='Creating'),
            'whenTrue': creation,
            'whenFalse': {'type': 'Unknown', 'reason': 'Unconfirmed post-creation access purchase policy', 'sourceRef': permission}}
        item.update(maxRank=1, sourceRef=native_source_ref(provenance, fact['nameSource']),
                    sourceStatus='Confirmed', requirementSource=permission, generalPurchasePermission=True)
        # Access itself is a binary rank, independent of its downstream XP card.
        # These two roots additionally choose a persistent weapon/organization.
        if item['ref']['entryId'] in ('access_3080526640119bbc', 'access_b832538f3a803048'):
            item['sourceStatus'] = 'NeedsResolution'
            key = 'weaponId' if item['ref']['entryId'] == 'access_3080526640119bbc' else 'organizationId'
            item['parameterDefinitions'] = [{'key': key, 'domain': {'type': 'Unresolved',
                'reason': 'Native access source requires an explicit persistent '+key+' selection'}}]
        else:
            item['repeatPolicy'] = {'type': 'Single'}
        for option in fact['options']:
            item['classificationSources']['option:'+option['id']] = native_source_ref(provenance, option['source'])
            for facet in ('source', 'price'):
                excerpt = option[facet]
                item['classificationSources']['native:'+excerpt['path']+'@'+str(excerpt['startIndex'])] = native_source_ref(provenance, excerpt)
    for fact in facts['skillSpecializations']:
        item = by_id.get(fact['entryId'])
        if item is None or item['kind'] != 'SKILL':
            raise ValueError('Native skill identity differs from canonical definition')
        source = native_source_ref(provenance, fact['evidence'])
        item.update(repeatPolicy={'type': 'DistinctParameter', 'parameterKeys': ['specialization'], 'requireUnique': True},
                    parameterDefinitions=[{'key': key, 'domain': {'type': 'UserDefinedIds'}}
                                          for key in ('specialization', 'specializationName')],
                    sourceStatus='Confirmed', requirementSource=source, generalPurchasePermission=True)
        item.pop('identityQuestion', None)
        if fact.get('excludedLabels'):
            item['parameterDefinitions'][0]['excludedLabels'] = [
                {'label': excluded['label'], 'sourceRef': native_source_ref(provenance, excluded['evidence'])}
                for excluded in fact['excludedLabels']]
        item['classificationSources']['native:independent-specialization'] = source
    for fact in facts.get('generalSkills', []):
        item = by_id[fact['entryId']]
        if item['kind'] != 'SKILL':
            raise ValueError('Native general skill differs from canonical definition kind')
        source = native_source_ref(provenance, fact['evidence'])
        item.update(repeatPolicy={'type': 'Single'}, parameterDefinitions=[], sourceStatus='Confirmed',
                    requirementSource=source, generalPurchasePermission=True)
        item.pop('identityQuestion', None)
        item['classificationSources']['native:general-skill'] = source
    if facts.get('resistanceParameters'):
        fact = facts['resistanceParameters']; item = by_id[fact['entryId']]
        if item['repeatPolicy'] != {'type': 'DistinctParameter', 'parameterKeys': [fact['parameterKey']], 'requireUnique': True}:
            raise ValueError('Native resistance parameter binding differs from source repeat policy')
        item['parameterDefinitions'] = [{'key': fact['parameterKey'],
            'domain': {'type': 'StableIds', 'valueIds': [c['id'] for c in fact['choices']]},
            'choices': [{'id': c['id'], 'label': c['label'], 'sourceRef': native_source_ref(provenance, c['evidence'])}
                        for c in fact['choices']]}]
        item['classificationSources']['native:resistance-repeat'] = native_source_ref(provenance, fact['permission'])
        item['classificationSources']['native:resistance-domain'] = native_source_ref(provenance, fact['categorySource'])
    for fact in facts.get('coreCards', []):
        item = by_id[fact['entryId']]
        if item['name'] != fact['name'] or item['kind'] != 'DEVELOPMENT':
            raise ValueError('Native core card binding differs from canonical identity')
        price_source = native_source_ref(provenance, fact['price'])
        item.update(sourceStatus='NeedsResolution', sourceRef=native_source_ref(provenance, fact['title']),
            maxRank=int(fact['ranks']['text'].split(':', 1)[1]),
            requirementsText=fact['requirement']['text'].split(':', 1)[1].strip(),
            requirementSource=native_source_ref(provenance, fact['requirement']),
            priceRule={'type': 'TransitionTable', 'sourceRef': price_source, 'transitions': [
                {'fromRank': 0, 'toRank': 1, 'xp': fact['price']['text'].split(':', 1)[1].strip(),
                 'ap': '0', 'sourceRef': price_source}]})
        for field in ('ranks', 'repeatEvidence'):
            item['classificationSources']['native:'+field] = native_source_ref(provenance, fact[field])
        if fact.get('stagePermission'):
            stage_source = native_source_ref(provenance, fact['stagePermission'])
            ident = item['ref']['entryId']
            item.setdefault('nativeAdditionalRequirements', []).append(node('AnyOf', ident+'/native-stage', stage_source,
                children=[node('CreationPhase', ident+'/native-stage/creating', stage_source, operator='EQ', operand='Creating'),
                          node('ExternalEvidence', ident+'/native-stage/gm', stage_source,
                               evidenceKey='gm-contacts-purchase', scopeId=ident, operator='EQ', operand=True)]))
    for fact in facts.get('legacyDuplicates', []):
        legacy = by_id[fact['legacyEntryId']]; canonical = by_id[fact['canonicalEntryId']]
        if legacy['name'] != fact['legacyTitle']['text'] or canonical['name'] != fact['canonicalName']:
            raise ValueError('Legacy duplicate source identity differs from exact bound titles')
        source = native_source_ref(provenance, fact['title'])
        price_source = native_source_ref(provenance, fact['price'])
        xp = fact['price']['text'].split(':', 1)[1].strip()
        legacy.update(sourceStatus='NeedsResolution', sourceRef=source, maxRank=int(fact['ranks']['text'].split(':', 1)[1]),
                      repeatPolicy={'type': 'Single'}, parameterDefinitions=[],
                      legacyAliasOf=copy.deepcopy(canonical['ref']),
                      nativeRequirements=node('Unresolved', legacy['ref']['entryId']+'/legacy-duplicate', source,
                          reason='LEGACY_DUPLICATE_DEFINITION_USE_CANONICAL:'+canonical['ref']['entryId']),
                      priceRule={'type': 'Fixed', 'xp': xp, 'ap': '0', 'sourceRef': price_source})
        canonical.setdefault('nativeAdditionalRequirements', []).append(node('Exclusive', canonical['ref']['entryId']+'/legacy-duplicate', source,
            conflicting=node('OwnedRank', canonical['ref']['entryId']+'/legacy-duplicate/owned', source,
                **legacy['ref'], operator='GE', operand=1)))
        for item in (legacy, canonical):
            item['classificationSources']['native:legacy-duplicate'] = source
    return definitions


def node(kind, node_id, source, **fields):
    return {'type': kind, 'nodeId': node_id, 'sourceRef': source, **fields}


def complete_native_requirement_tree(value, node_id, source):
    """Authored native replacements may omit repeated IDs/citations on children."""
    result = dict(value)
    result.setdefault('nodeId', node_id)
    result.setdefault('sourceRef', source)
    for key in ('children',):
        if key in value:
            result[key] = [complete_native_requirement_tree(child, result['nodeId']+'/'+str(i), result['sourceRef'])
                           for i, child in enumerate(value[key])]
    for key in ('predicate', 'conflicting'):
        if key in value:
            result[key] = complete_native_requirement_tree(value[key], result['nodeId']+'/'+key, result['sourceRef'])
    return result


def parse_requirement(text, node_id, source, definitions):
    """Conservative grammar: ordered conjunctions containing explicit OR groups.

    Commas in the named martial style list belong to its OR group; the tail after
    the final 'или' alternative belongs to AND. Parentheses/unknown lexical forms
    are never stripped to obtain a convenient match.
    """
    raw = text.strip().rstrip('.')
    local = dict(source, originalText=text)
    raw = re.sub(r'^Требовани[ея]:\s*', '', raw, flags=re.I)
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
    if len(parts) > 1 or (len(parts) == 1 and parts[0] != raw):
        return node('AllOf', node_id, local, children=[sub(x, i) for i, x in enumerate(parts)])
    if 'и/или' in raw:
        return node('Unresolved', node_id, local, reason='unresolved-qualified-or-compound-requirement')
    if ' или ' in raw:
        return node('AnyOf', node_id, local, children=[sub(x, i) for i, x in enumerate(raw.split(' или '))])
    two_attributes = re.fullmatch(r'Любые две характеристики\s+(\d+)(?:\s+ранг(?:а|ов)?)?', raw, re.I)
    if two_attributes:
        rank = int(two_attributes[1])
        return node('AnyOf', node_id, local, children=[
            node('AllOf', node_id+'/'+first+'+'+second, local, children=[
                node('Attribute', node_id+'/'+first+'+'+second+'/'+key, local, attributeId=key, operand=rank, operator='GE')
                for key in (first, second)])
            for first, second in itertools.combinations(ATTRIBUTES.values(), 2)])
    two_skills = re.fullmatch(r'Любые два умения\s+(\d+)(?:\s+ранг(?:а|ов)?)?', raw, re.I)
    two_knowledge = re.fullmatch(r'Два и более Знаний\s+(\d+)\s+ранг(?:а|ов)?', raw, re.I)
    if two_skills or two_knowledge:
        rank = int((two_skills or two_knowledge)[1])
        predicate = sub(f'Любое умение {rank}' if two_skills else f'Знание (Любое) {rank}', 'candidate')
        def retain_original(n):
            n['sourceRef'] = local
            for child in n.get('children', []): retain_original(child)
        retain_original(predicate)
        return node('DistinctCount', node_id, local, predicate=predicate, uniqueness='Instance', operator='GE', operand=2)
    any_attribute = re.fullmatch(r'Любая характеристика\s+(\d+)(?:\s+ранг(?:а|ов)?)?', raw, re.I)
    if any_attribute:
        return node('AnyOf', node_id, local, children=[
            node('Attribute', node_id+'/'+key, local, attributeId=key, operand=int(any_attribute[1]), operator='GE')
            for key in ATTRIBUTES.values()])
    any_skill = re.fullmatch(r'Любое умение\s+(\d+)(?:\s+ранг(?:а|ов)?)?', raw, re.I)
    if any_skill:
        members = [d for d in definitions if d['kind'] == 'SKILL' and d['ref']['packId'] == CORE]
        if not members:
            return node('Unresolved', node_id, local, reason='missing-any-skill-universe')
        return node('AnyOf', node_id, local, children=[
            node('SkillFamily', node_id+'/'+d['ref']['entryId'], local, packId=CORE,
                 familyId=d['ref']['entryId'], operator='GE', operand=int(any_skill[1]))
            for d in members])
    knowledge = re.fullmatch(r'Знани[ея]\s+(\d+)\s*\(Любое\)', raw, re.I)
    if not knowledge:
        knowledge = re.fullmatch(r'Знани[ея]\s*\(Любое\)\s+(\d+)(?:\s+ранг(?:а|ов)?)?', raw, re.I)
    if knowledge:
        return node('SkillFamily', node_id, local, packId=CORE, familyId=KNOWLEDGE_FAMILY,
                    operator='GE', operand=int(knowledge[1]))
    performance = re.fullmatch(r'Исполнение\s*\(Любое\)\s+(\d+)(?:\s+ранг(?:а|ов)?)?', raw, re.I)
    if performance:
        # P10542/P10547 admit any performance, including fixed Vocal and
        # parameterized instances. Never equate a user-edited name with a family.
        members = ('performance_vocal', 'performance_template')
        present = {d['ref']['entryId'] for d in definitions
                   if d['kind'] == 'SKILL' and d['ref']['packId'] == CORE}
        if not set(members) <= present:
            return node('Unresolved', node_id, local, reason='missing-performance-family-universe')
        return node('AnyOf', node_id, local, children=[
            node('SkillFamily', node_id+'/'+member, local, packId=CORE, familyId=member,
                 operator='GE', operand=int(performance[1])) for member in members])
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
    if raw in ('Только при создании персонажа', 'только при создании'):
        return node('CreationPhase', node_id, local, operand='Creating', operator='EQ')
    explicit_spell = raw.lower().startswith('заклинание ')
    if explicit_spell:
        raw = raw[len('заклинание '):].strip(' «»"')
    match = re.fullmatch(r'(.+?)\s+(\d+)', raw)
    label, rank = (match[1], int(match[2])) if match else (raw, 1)
    attribute_label = 'Телосложение' if label == 'Тело' else label
    if attribute_label in ATTRIBUTES and match:
        return node('Attribute', node_id, local, attributeId=ATTRIBUTES[attribute_label], operand=rank, operator='GE')
    # Closed lexical spellings only. Never turn an arbitrary fuzzy name into a prerequisite.
    if label.lower() == 'ул. инициатива':
        label = 'Улучшенная инициатива'
    if label == 'Ремонт':
        label = 'Инженерное дело (Ремонт)'
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


def has_confirmed_initial_rank(definition):
    """A cited first purchase proves rank one without inventing a maximum."""
    source = definition.get('classificationSources', {}).get('purchase:initial-rank-confirmed')
    return bool(definition.get('maxRank') is None and
        definition.get('repeatPolicy') == {'type': 'Single'} and
        definition.get('sourceStatus') in ('Confirmed', 'NeedsResolution') and isinstance(source, dict) and
        source.get('documentId') and not source['documentId'].startswith('unverified:') and
        source.get('documentVersion') and source.get('originalText', '').strip())


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
                    if not (0 <= n['operand'] <= 1 and any(has_confirmed_initial_rank(t) for t in targets)):
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
    native = load_native_development_facts(repo_root)
    access_prices = {f['definitionRef']['entryId']: f for f in native['accessPrices']} if native else {}
    magic_access = {}
    if (Path(repo_root)/'rulesets/dubl-3.69/acquisition/native_magic_rules.json').exists():
        try:
            from .native_magic_rules import load_native_magic_rules, _source as magic_source
        except ImportError:
            from native_magic_rules import load_native_magic_rules, _source as magic_source
        magic = load_native_magic_rules(repo_root)
        magic_access = {f['ref']['entryId']: f for f in magic['bindings'] if f['kind'] == 'ACCESS'}
    for e in result.get('entries', []):
        if e['id'] in access_prices:
            fact = access_prices[e['id']]
            if fact['name'] != e['name']:
                raise ValueError('Native display access price identity differs')
            e['abilityOptions'] = [{'source': option['source']['text'], 'value': int(option['price']['text'])}
                                   for option in fact['options']]
            e['acquisitionSourceRef'] = native_source_ref(native['sourceProvenance'], fact['nameSource'])
        if e['id'] in magic_access:
            fact = magic_access[e['id']]
            if e['name'] != fact['name']:
                raise ValueError('Native magic display access identity differs')
            e['abilityOptions'] = [{'source': magic['evidence'][fact['evidence'][3]]['text'], 'value': fact['ap']}]
            e['acquisitionSourceRef'] = magic_source(magic, fact['evidence'][0])
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
    rule_path = repo_root/'rulesets/dubl-3.69/acquisition/source_rules.json'
    source_rules = load_source_rules(repo_root) if rule_path.exists() else None
    if source_rules:
        evidence = validate_source_rules(source_rules, evidence, read(rule_path.with_name('source_review_excerpts.json')))
    evidence, native_supplement = native_occurrence_overlay(evidence, repo_root)
    diagnostic_sha = hashlib.sha256((repo_root/'rulesets/dubl-3.69/resolutions.json').read_bytes()).hexdigest()
    resolution = resolution_revision(source_rules, diagnostic_sha)
    def interpreted(source, ruling):
        return dict(source, resolutionId=ruling, resolutionRevision=resolution)
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
    for record in native_supplement['entries']:
        runtime[record['id']] = (CORE, record)
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
            m = re.search(r'^Ранг(?:и)?:\s*(\d+)\s*$', o['fullRule'].replace('\v', '\n'), re.M)
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
            reqs = [(int(ref.split('P')[1]), line) for ref in o['sourceRef']
                    for line in evidence['sourceEvidence'][ref]['text'].splitlines() if re.match(r'^Требовани[ея]:', line)]
            if len(reqs) != 1:
                item['requirementsText'] = ''
            if len(reqs) == 1:
                p, text = reqs[0]; item['requirementsText'] = text.split(':', 1)[1].strip(); item['requirementSource'] = source_ref(evidence, p)
            prices = [(int(ref.split('P')[1]), line) for ref in o['sourceRef']
                      for line in evidence['sourceEvidence'][ref]['text'].splitlines() if re.fullmatch(r'Стоимость:\s*\d+\s*', line)]
            if len(prices) == 1:
                p, text = prices[0]; price = {'xp': str(int(text.split(':',1)[1])), 'ap': '0', 'sourceRef': source_ref(evidence, p)}
                if maximum == 1:
                    item['priceRule'] = {'type': 'Fixed', **price}
                else:
                    item['priceRule'] = {'type': 'TransitionTable', 'transitions': [{'fromRank': 0, 'toRank': 1, **price}], 'sourceRef': price['sourceRef']}
            if d['id'] == 'feat_7fc6fc6e63da9688' and 'каждый раз выбирая новый тип сопротивления' in o['fullRule']:
                item['repeatPolicy'] = {'type': 'DistinctParameter', 'parameterKeys': ['resistanceType'], 'requireUnique': True}
                item['parameterDefinitions'] = [{'key': 'resistanceType', 'domain': {'type': 'Unresolved', 'reason': 'Source type-to-stable-ID mapping requires review'}}]
            if d['rankBasis'] == 'SOURCE_OMITS_RANK_FIELD':
                if len(prices) != 1 or item['priceRule']['type'] != 'TransitionTable':
                    raise ValueError('Unranked native card lacks a confirmed initial purchase transition')
                item['repeatPolicy'] = {'type': 'Single'}
                item['classificationSources']['purchase:initial-rank-confirmed'] = item['priceRule']['sourceRef']
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
        if source_rules and d['id'] == 'access_17f99cc35c559d82':
            later = copy.deepcopy(item['priceRule']['whenTrue'])
            later['sourceRef'] = interpreted(later['sourceRef'], CARRY)
            for price in later['options'].values():
                price['sourceRef'] = interpreted(price['sourceRef'], CARRY)
            item['priceRule']['whenFalse'] = later
        definitions.append(item)
    if source_rules:
        fixed_rule = next(r for r in source_rules['rules'] if r['id'] == FIXED)
        fixed_prices = {r['definitionRef']['entryId']: r for r in fixed_rule['fact']['prices']}
        for item in definitions:
            literal = fixed_prices.get(item['ref']['entryId'])
            if literal is not None:
                rule = item['priceRule']
                first = rule if rule['type'] == 'Fixed' else rule['transitions'][0]
                if (item['kind'] != 'DEVELOPMENT' or item['ref'] != literal['definitionRef'] or
                    item['maxRank'] != literal['maxRank'] or
                    (first['xp'], first['ap']) != (literal['unitXP'], literal['unitAP'])):
                    raise ValueError('Author per-rank binding differs from exact source literal')
                item['priceRule'] = {'type': 'PerRank', 'xp': literal['unitXP'], 'ap': literal['unitAP'],
                                     'sourceRef': interpreted(first['sourceRef'], FIXED)}
            for rule in source_rules['rules']:
                if rule['authority'] != 'SOURCE_DERIVED' or rule['runtimeBinding'] != 'BOUND':
                    continue
                refs = rule['scope'].get('definitionRefs', []) + ([rule['scope']['owner']] if 'owner' in rule['scope'] else [])
                if item['ref'] in refs:
                    citation = rule['sources'][0]
                    item['classificationSources']['source-rule:'+rule['id']] = source_ref(evidence, citation['paragraph'])
    definitions = apply_native_development_prices(definitions, load_native_development_facts(repo_root))
    # A finite source-ranked feat is one scalar rank track unless its cited
    # purchase wording selects independently repeated instances/parameters.
    # This classifies identity only; requirement diagnostics remain blockers.
    for item in definitions:
        if (item['ref']['entryId'] in PARAMETERIZED_DEVELOPMENTS and
            item['repeatPolicy']['type'] == 'Unresolved' and not item['parameterDefinitions']):
            item['parameterDefinitions'] = [{'key': 'specialization', 'domain': {'type': 'Unresolved',
                'reason': 'Q_SOURCE_PARAMETER_IDENTITY: persistent purchase selection requires a typed binding'}}]
        if (item['kind'] == 'DEVELOPMENT' and item['sourceStatus'] != 'Unavailable' and
            not item['ref']['entryId'].startswith('access_') and
            item.get('maxRank') is not None and item['maxRank'] > 0 and
            item['ref']['entryId'] not in PARAMETERIZED_DEVELOPMENTS and
            item['repeatPolicy']['type'] == 'Unresolved' and not item['parameterDefinitions'] and
            item['abilityOptionIds'] == []):
            item['repeatPolicy'] = {'type': 'Single'}
            rank_refs = sorted({ref for source_occurrence in occurrence[item['ref']['entryId']]
                for ref in source_occurrence['sourceRef']
                if any(re.fullmatch(r'Ранг(?:и)?:\s*'+str(item['maxRank'])+r'\s*', line)
                       for line in evidence['sourceEvidence'][ref]['text'].splitlines())})
            if not rank_refs:
                raise ValueError('Scalar development identity lacks exact source rank citation')
            item['classificationSources']['identity:finite-rank-track'] = source_ref(evidence, int(rank_refs[0].split('P')[1]))
    if (repo_root/'tools/rulebook/native_magic_rules.py').exists():
        try:
            from .native_magic_rules import apply_native_magic_rules
        except ImportError:
            from native_magic_rules import apply_native_magic_rules
        definitions = apply_native_magic_rules(definitions, repo_root, resolution)
    if (repo_root/'tools/rulebook/native_martial_rules.py').exists():
        try:
            from .native_martial_rules import apply_native_martial_rules
        except ImportError:
            from native_martial_rules import apply_native_martial_rules
        definitions = apply_native_martial_rules(definitions, repo_root, resolution)
    if (repo_root/'rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json').exists():
        try:
            from .native_magic_supplement import apply_native_magic_supplement
        except ImportError:
            from native_magic_supplement import apply_native_magic_supplement
        definitions = apply_native_magic_supplement(definitions, repo_root)
    definitions = apply_native_parameter_rules(definitions, repo_root)
    if (repo_root/'rulesets/dubl-3.69/acquisition/native_weapon_proficiencies.json').exists():
        try:
            from .native_weapon_proficiencies import apply_native_weapon_proficiencies
        except ImportError:
            from native_weapon_proficiencies import apply_native_weapon_proficiencies
        definitions = apply_native_weapon_proficiencies(definitions, repo_root)
    if (repo_root/'rulesets/dubl-3.69/acquisition/native_drawback_rules.json').exists():
        try:
            from .native_drawback_rules import apply_native_drawback_rules
        except ImportError:
            from native_drawback_rules import apply_native_drawback_rules
        definitions = apply_native_drawback_rules(definitions, repo_root, resolution)
    for item in definitions:
        id = item['ref']['entryId']; src = item.pop('requirementSource', item['sourceRef'])
        native_requirements = item.pop('nativeRequirements', None)
        if native_requirements is not None:
            tree = complete_native_requirement_tree(native_requirements, id+'/requirements', src)
        elif item.pop('duplicateSourceContext', False):
            tree = node('Unresolved', id+'/duplicate-context', src,
                        reason='unmapped-duplicate-source-context: inherited admission/branch context requires reconciliation')
        elif item.pop('generalPurchasePermission', False):
            tree = node('AllOf', id+'/requirements', src, children=[],
                noRequirementsStatement='The cited general purchase rule explicitly permits this rank/access purchase; this exact bound source entry states no additional prerequisite.')
        elif item.get('identityQuestion'):
            tree = node('Unresolved', id+'/requirements', src, reason=item.pop('identityQuestion'))
        else:
            tree = parse_requirement(item['requirementsText'], id+'/requirements', src, definitions)
        for internal in ('generalPurchasePermission', 'identityQuestion', 'duplicateSourceContext'):
            item.pop(internal, None)
        # The root keeps exact wording/prefix for provenance, children keep atom wording.
        tree['sourceRef'] = src
        if item['sourceStatus'] == 'Unavailable':
            blocker = node('Unresolved', id+'/source', src, reason='source-unverified')
            if tree['type'] == 'AllOf' and tree.get('children'):
                tree['children'].append(blocker)
            else:
                tree = node('AllOf', id+'/requirements', src, children=[tree, blocker])
        native_additional = item.pop('nativeAdditionalRequirements', [])
        if native_additional:
            tree = node('AllOf', id+'/with-native-conditions', src, children=[tree,
                *[complete_native_requirement_tree(value, id+'/native-additional/'+str(i), src)
                  for i, value in enumerate(native_additional)]])
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
    if (repo_root/'rulesets/dubl-3.69/acquisition/native_magic_supplement_rules.json').exists():
        try:
            from .native_magic_supplement import apply_native_magic_supplement_bindings
        except ImportError:
            from native_magic_supplement import apply_native_magic_supplement_bindings
        definitions = apply_native_magic_supplement_bindings(definitions, repo_root)
    definitions = apply_native_requirement_bindings(definitions, repo_root)
    if (repo_root/'rulesets/dubl-3.69/acquisition/native_weapon_proficiencies.json').exists():
        try:
            from .native_weapon_proficiencies import apply_native_weapon_requirement_bindings
        except ImportError:
            from native_weapon_proficiencies import apply_native_weapon_requirement_bindings
        definitions = apply_native_weapon_requirement_bindings(definitions, repo_root)
    diagnostics = diagnose_catalog(definitions)
    definitions = apply_requirement_diagnostics(definitions, diagnostics)
    result = {}
    for pack in (CORE, CHI):
        payload = {'schemaVersion': 1, 'resolutionRevision': resolution,
                   'definitions': [d for d in definitions if d['ref']['packId'] == pack],
                   'diagnostics': [d for d in diagnostics if d['packId'] == pack]}
        if source_rules:
            payload['sourceRuleRevision'] = authority_revision(source_rules, 'SOURCE_DERIVED')
            if pack == CORE:
                payload['creationFundingPolicy'] = {
                    'ruleId': INITIAL, 'xpPerAP': '1000', 'rounding': 'FLOOR',
                    'trigger': 'GENUINELY_NEW_CHARACTER_INITIALIZATION', 'repeat': 'NEVER',
                    'appliesToExistingOrMigrated': False, 'ongoingXPGrantAP': False,
                    'sourceRef': interpreted(source_ref(evidence, 11903), INITIAL)}
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
