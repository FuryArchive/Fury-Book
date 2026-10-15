"""Acquisition authority and immutable expectation views, never a runtime price oracle.

Source facts and user interpretations have separate identities. Historical matrix
bytes remain the independent baseline; an explicit price-only overlay cannot grant
admission, resolve identity, or certify execution/platform coverage.
"""
from __future__ import annotations
import copy
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path

CORE = 'dubl-3.69'
FIXED = 'UR_FIXED_DEVELOPMENT_PRICE_PER_RANK'
CARRY = 'UR_CREATION_BALANCE_CARRYOVER_SAME_PRICE_CURRENCY'
INITIAL = 'UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP'
SOURCE_IDS = {'AR_NATURAL_BEAUTY_CREATION_ONLY', 'AR_SELF_TAUGHT_EXACT_SKILL_STEPS',
              'AR_DRAWBACK_STAGE_AND_REWARD', 'AR_SMETANIE_BULL_RUSH_TWO'}
AUTHOR_IDS = {FIXED, CARRY, INITIAL}
BASE_HASHES = {'source_questions.json': 'ec2701ef566800da3009cd50d5eb46e6f1a7c37cc5d2369bb141327bb10f1499',
              'coverage_manifest.json': '32853e608d1d2477a66f96825dff77ace39a6440b51cd66d35571cbb1901019c',
              'expected_prices.json': '1694a9a2bde72e97abbc6a2936d43ced685d2f8a05afc8df81590227a63e3485'}
MEMBER_SHA = '238a93086f22b04b1f8b26a018a0c8c5b70815c67d8976c468dabbb7a4e14fa9'
ARCHIVE_SHA = 'be8e3f90db91b27f282b4ce3eb78c0b27818b4cc2ff60becccc6b52ce27f54f3'
DECISION_SHA = 'db37a6bf4810f1d4bb96ece90c5695ac50e16d7e228f794484538669b5a049a7'
MONEY_FIELDS = ('expectedCostXP', 'expectedCostAP', 'expectedRemainingXP', 'expectedRemainingAP', 'expectedUndoXP', 'expectedUndoAP')
EXTRA_HASHES = {11903: 'f3b4ccae764f982dd7db5eb152f1736a48f43ccdc671a75c0a3c59858e59ccbc',
14075: 'a1ae742fa651a99978706e56fcb3f6ac374c61396833a827722f2c34910c5dab',
14077: 'c1c7b61e1809bac10255c82e9b3abf98e6bff21b98b666bec33458f96229105f',
14078: '961094bfe7f18af62fe262f97d06d28bb544eb841bbad9340125a54a3a5f756c',
14181: '825e90b08d847ac0e6df3a2f3c833c488fe0019bc1a7246d377f3b918acbd02b',
14182: '36f5d4605567d6c3553b6bbcb3c7efbbc0092847adf533d9053908715142b38a'}


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def semantic_sha(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def read(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('Duplicate JSON key: '+key)
            result[key] = value
        return result
    return json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=unique)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def definition_ref(entry):
    return {'packId': CORE, 'entryId': entry}


def derive_fixed_development_prices(source):
    """Source literals only. Never inspect reviewedRules, anchors or expected costs."""
    occurrences = defaultdict(list)
    for occurrence in source['sourceOccurrences']:
        if occurrence.get('runtimeId'):
            occurrences[occurrence['runtimeId']].append(occurrence)
    result = []
    for definition in sorted(source['definitions'], key=lambda d: d['id']):
        if not definition['included'] or definition['kind'] != 'development':
            continue
        rows = occurrences[definition['id']]
        if not rows or any(o['matchStatus'] != 'matched' for o in rows):
            continue
        prices, maxima, price_refs, rank_refs = [], [], [], []
        valid = True
        for occurrence in rows:
            excerpts = [(ref, source['sourceEvidence'].get(ref, {}).get('text', '')) for ref in occurrence['sourceRef']]
            p = [(ref, re.fullmatch(r'Стоимость:\s*(\d+)\s*', text)) for ref, text in excerpts]
            p = [(ref, int(match[1])) for ref, match in p if match]
            r = [(ref, re.fullmatch(r'Ранг(?:и)?:\s*(\d+)\s*', text)) for ref, text in excerpts]
            r = [(ref, int(match[1])) for ref, match in r if match]
            if len(p) != 1 or len(r) != 1 or r[0][1] < 1:
                valid = False
                break
            price_refs.append(p[0][0]); prices.append(p[0][1]); rank_refs.append(r[0][0]); maxima.append(r[0][1])
        if not valid or len(set(prices)) != 1 or len(set(maxima)) != 1:
            continue
        result.append({'definitionRef': {'packId': definition['pack'], 'entryId': definition['id']},
                       'unitXP': str(prices[0]), 'unitAP': '0', 'maxRank': maxima[0],
                       'sourceIds': sorted(o['sourceId'] for o in rows),
                       'priceRefs': sorted(price_refs), 'rankRefs': sorted(rank_refs)})
    return result


def source_citation(source, key):
    excerpt = source['sourceEvidence'][key]
    return {'excerptRef': key, 'documentId': source['provenance']['documentId'],
            'revisionId': source['provenance']['revisionId'], 'archiveSha256': ARCHIVE_SHA,
            'member': 'source/furybook_current_corebook.json', 'memberSha256': MEMBER_SHA,
            **{k: excerpt[k] for k in ('paragraph', 'tabId', 'startIndex', 'endIndex')},
            'textSha256': excerpt['sha256']}


def authority_revision(records, authority):
    rules = sorted((r for r in records['rules'] if r['authority'] == authority), key=lambda r: r['id'])
    ids = {r['id'] for r in rules}
    dispositions = sorted((d for d in records['questionDispositions'] if d.get('ruleId') in ids), key=lambda d: d['id'])
    payload = {'rules': rules, 'questionDispositions': dispositions}
    if authority == 'SOURCE_DERIVED':
        payload.update(sourceSnapshotSha256=records['sourceSnapshotSha256'], sourceProvenance=records['sourceProvenance'])
    return semantic_sha(payload)


def resolution_revision(records, diagnostic_sha):
    if not records or not any(r['authority'] == 'USER_RULING' for r in records['rules']):
        return diagnostic_sha
    return semantic_sha({'schema': 'acquisition-authority-v1', 'diagnosticResolutionsSha256': diagnostic_sha,
                         'acquisitionUserRulingsSha256': authority_revision(records, 'USER_RULING')})


def validate_source_rules(records, source, excerpts):
    require(set(records) == {'schemaVersion', 'devOnly', 'ruleset', 'sourceSnapshotSha256', 'sourceProvenance', 'rules', 'questionDispositions', 'pendingDecisions'}, 'Unexpected source-rule fields')
    require(records['schemaVersion'] == 1 and records['devOnly'] is True and records['ruleset'] == CORE, 'Invalid source-rule schema')
    require(records['sourceSnapshotSha256'] == source['sourceSnapshotSha256'] == MEMBER_SHA, 'Source snapshot mismatch')
    expected_provenance = {k: source['provenance'][k] for k in ('documentId', 'revisionId')}
    require(records['sourceProvenance'] == expected_provenance, 'Source provenance mismatch')
    require(set(excerpts) == {'schemaVersion', 'devOnly', 'sourceProvenance', 'archiveLibraryVersion', 'archiveSha256', 'member', 'memberSha256', 'derivation', 'sourceEvidence', 'projectionSha256'}, 'Unexpected excerpt fixture fields')
    require(excerpts['archiveLibraryVersion'] == 1, 'Unexpected source archive version')
    require(excerpts['schemaVersion'] == 1 and excerpts['devOnly'] is True and excerpts['sourceProvenance'] == expected_provenance, 'Excerpt provenance mismatch')
    require(excerpts['archiveSha256'] == ARCHIVE_SHA and excerpts['memberSha256'] == MEMBER_SHA and excerpts['member'] == 'source/furybook_current_corebook.json', 'Excerpt archive mismatch')
    extra = excerpts['sourceEvidence']
    require(not (set(extra) & set(source['sourceEvidence'])), 'Existing source excerpt duplicated')
    require(excerpts['projectionSha256'] == semantic_sha(extra), 'Excerpt projection mismatch')
    locations = {11903: ('t.z1o9nhkkrzeb', 334, 440), 14075: ('t.jaljeq1rpes0', 12, 419), 14077: ('t.jaljeq1rpes0', 443, 456), 14078: ('t.jaljeq1rpes0', 457, 731), 14181: ('t.jaljeq1rpes0', 16211, 16251), 14182: ('t.jaljeq1rpes0', 16252, 16399)}
    for p, sha in EXTRA_HASHES.items():
        row = extra.get(f'core:P{p}', {})
        require(row.get('paragraph') == p and row.get('sha256') == sha and tuple(row.get(k) for k in ('tabId', 'startIndex', 'endIndex')) == locations[p], 'Required exact excerpt missing or changed')
    merged = copy.deepcopy(source)
    merged['sourceEvidence'].update(extra)
    for row in extra.values():
        require(set(row) == {'text', 'startIndex', 'endIndex', 'tabId', 'namedStyleType', 'isListItem', 'paragraph', 'sha256'}, 'Unexpected excerpt projection fields')
    for key, row in merged['sourceEvidence'].items():
        if 'sha256' in row:
            require(hashlib.sha256(row['text'].encode()).hexdigest() == row['sha256'], 'Excerpt checksum mismatch: '+key)
    definitions = {(d['pack'], d['id']) for d in source['definitions'] if d['included']}
    ids = [r['id'] for r in records['rules']]
    require(len(ids) == len(set(ids)), 'Duplicate rule ID')
    require(set(ids) == SOURCE_IDS | AUTHOR_IDS, 'Unexpected or missing bounded authority rule')
    require(records['pendingDecisions'] == [], 'The three author decisions are resolved')
    fixed_rows = derive_fixed_development_prices(source)
    require(len(fixed_rows) == 517, 'Changed independently source-priced development set')
    assertions = set()
    by_id = {r['id']: r for r in records['rules']}
    for r in records['rules']:
        authority = 'USER_RULING' if r['id'] in AUTHOR_IDS else 'SOURCE_DERIVED'
        require(r['authority'] == authority, 'Authority mismatch')
        common = {'id', 'authority', 'topic', 'statement', 'scope', 'nonClaims', 'testRefs'}
        allowed = common | ({'approvalReference', 'questionReference', 'fact', 'bookEvidence'} if authority == 'USER_RULING' else {'assertions', 'sources', 'runtimeBinding'})
        require(set(r) == allowed, 'Unexpected authority record fields')
        require(bool(r['testRefs']) and all('::' in ref for ref in r['testRefs']) and bool(r['nonClaims']), 'Missing named test/authority boundary')
        citations = r['bookEvidence'] if authority == 'USER_RULING' else r['sources']
        require(bool(citations), 'Missing exact book context')
        for citation in citations:
            key = citation.get('excerptRef')
            require(key in merged['sourceEvidence'] and citation == source_citation(merged, key), 'Exact source citation mismatch')
        refs = r['scope'].get('definitionRefs', [])
        require(all(set(ref) == {'packId', 'entryId'} and (ref['packId'], ref['entryId']) in definitions for ref in refs), 'Unknown bound DefinitionRef')
        if authority == 'USER_RULING':
            approval = r['approvalReference']
            expected_ids = ['Sentinel_7fbeab50f9e881919ebc6f622695ba12']
            if r['id'] == CARRY:
                expected_ids.append('Sentinel_496e683f65d0819188c901fc23970cf7')
            require(approval['messageIds'] == expected_ids and bool(approval['decision']), 'Missing explicit author approval')
            decisions = {FIXED: 'RANKED_FEAT_PRICE', CARRY: 'POST_CREATION_BRANCH_ACCESS', INITIAL: 'INITIAL_AP_CAMPAIGN_POLICY'}
            require(r['questionReference'] == {'decisionId': decisions[r['id']], 'artifactSha256': DECISION_SHA}, 'Author question artifact mismatch')
        else:
            require(r['runtimeBinding'] == ('BOUND_NATIVE_CURRENT' if r['id'] == 'AR_DRAWBACK_STAGE_AND_REWARD' else 'BOUND'), 'Incorrect source runtime binding')
            for assertion in r['assertions']:
                require(assertion['id'] not in assertions, 'Duplicate assertion ID'); assertions.add(assertion['id'])
    fixed = by_id[FIXED]
    require(fixed['fact'] == {'type': 'FIXED_DEVELOPMENT_PER_RANK', 'prices': fixed_rows, 'rankDelta': 'POSITIVE_INTEGER', 'modifiers': 'NO_EXPANSION'}, 'Invalid fixed DEVELOPMENT interpretation')
    require(fixed['scope'] == {'type': 'SOURCE_PRICED_DEVELOPMENT_SET', 'facet': 'PRICE_ONLY'}, 'Overbroad per-rank scope')
    require(by_id[CARRY]['scope'] == {'type': 'KNOWN_ACCESS_OPTIONS', 'definitionRefs': [definition_ref('access_17f99cc35c559d82')], 'options': ['morale', 'chi'], 'phase': 'Finalized', 'facet': 'PRICE_ONLY'}, 'Overbroad carryover binding')
    require(by_id[CARRY]['fact'] == {'type': 'BALANCE_CARRYOVER_SAME_PRICE_CURRENCY', 'forfeitUnspentXP': False, 'forfeitUnspentAP': False, 'conversion': False, 'options': {'morale': {'xp': '0', 'ap': '1'}, 'chi': {'xp': '0', 'ap': '2'}}, 'explicitCreationOnlyRestrictionsRemain': True}, 'Invalid carryover interpretation')
    require(by_id[INITIAL]['scope'] == {'type': 'GENUINELY_NEW_CHARACTER_INITIALIZATION', 'facet': 'INITIAL_FUNDING'}, 'Overbroad initialization scope')
    require(by_id[INITIAL]['fact'] == {'type': 'INITIAL_AP_ONCE', 'xpPerAP': '1000', 'rounding': 'FLOOR', 'trigger': 'GENUINELY_NEW_CHARACTER_INITIALIZATION', 'repeat': 'NEVER', 'appliesToExistingOrMigrated': False, 'ongoingXPGrantAP': False}, 'Invalid initial funding interpretation')
    questions = {q['id'] for q in source['questions']}
    disposition_ids = set()
    for d in records['questionDispositions']:
        require(set(d) == {'id', 'questionId', 'facet', 'scope', 'status', 'ruleId', 'reason', 'remainingFacets'}, 'Unexpected disposition fields')
        require(d['id'] not in disposition_ids, 'Duplicate disposition ID'); disposition_ids.add(d['id'])
        require(d['questionId'] in questions and d['ruleId'] in by_id, 'Unknown historical question/rule')
        require(d['scope'] == by_id[d['ruleId']]['scope'] and d['facet'] == d['scope']['facet'], 'Overbroad disposition')
        require(d['status'] == ('USER_RESOLVED' if d['ruleId'] in AUTHOR_IDS else 'SOURCE_CONFIRMED'), 'Wrong effective facet authority')
        require(bool(d['remainingFacets']), 'Unrelated facets must remain visible')
    validate_bounded_source_assertions(by_id, source)
    require(len(records['questionDispositions']) == 7 and {d['ruleId'] for d in records['questionDispositions']} == set(by_id), 'Missing bounded facet dispositions')
    return merged


def load_source_rules(repo_root):
    data = Path(repo_root)/'rulesets/dubl-3.69/acquisition'
    require(hashlib.sha256((data/'source_questions.json').read_bytes()).hexdigest() == BASE_HASHES['source_questions.json'], 'Immutable source corpus bytes changed')
    records = read(data/'source_rules.json')
    validate_source_rules(records, read(data/'source_questions.json'), read(data/'source_review_excerpts.json'))
    return records

BASE_SEMANTIC_HASHES = {'expected_prices.json': '198738b173f606de029d68ed521450a21d7ae6253d902c7b4fc590e63f1caf9f',
                       'coverage_manifest.json': '2bab1c419877a79d3703f50b32dd4d044143d6d887eb5d79edeae98eea3bb16b',
                       'source_questions.json': 'acb503a3a10b0b89ca4abc8a8385e6eb459fc66546c6b05f52a72690ca0c581a'}


def authorized_price_updates(base_matrix, base_manifest, source_rules):
    """Independent retained source expectations, not a call to importer/quote/catalog."""
    rules = {r['id']: r for r in source_rules['rules']}
    prices = {p['definitionRef']['entryId']: p for p in rules[FIXED]['fact']['prices']}
    definitions = {d['id']: d for d in base_manifest['definitions']}
    reviewed = base_manifest['reviewedRules']['developmentFirstRanks']
    require(set(prices) == set(reviewed), 'Author price set differs from independent reviewed literals')
    for entry, row in prices.items():
        require(int(row['unitXP']) == reviewed[entry]['xp'] and row['unitAP'] == '0' and
                row['priceRefs'] == sorted(reviewed[entry]['sourceRefs']) and
                row['maxRank'] == definitions[entry]['rankBounds'][1] and not definitions[entry]['unknownRankMaximum'],
                'Author literal/max differs from independent expectation evidence')
    updates = {}
    for case in base_matrix['cases']:
        ident = case['definitionRef']
        price = prices.get(ident) if isinstance(ident, str) else None
        xp = ap = None
        ruling = FIXED
        if price and case['evidenceStatus'] == 'SOURCE_UNVERIFIED' and case['discountConditions'] in ('NONE', 'SELF_TAUGHT'):
            a, b = case['fromRank'], case['toRank']
            if type(a) is int and type(b) is int and 0 <= a < b <= price['maxRank']:
                xp, ap = reviewed[ident]['xp'] * (b-a), 0
        for option, cost in [('morale', 1), ('chi', 2)]:
            if case['caseId'] == f'combat_cries_access_{option}_postcreation_unknown':
                require(ident == {'id': 'access_17f99cc35c559d82', 'pack': CORE, 'catalogVersion': '3.69', 'resolutionVersion': 1} and
                        case['optionId'] == option and case['creationPhase'] == 'AFTER_CREATION' and
                        case['openingXP'] == 100 and case['openingAP'] == 10, 'Changed immutable access anchor')
                xp, ap, ruling = 0, cost, CARRY
        if xp is not None:
            updates[case['caseId']] = {'rawCaseSha256': semantic_sha(case), 'rulingId': ruling,
                'sourceRef': copy.deepcopy(case['sourceRef']), 'sourceId': copy.deepcopy(case['sourceId']),
                'priceAuthority': 'USER_RULING', 'evidenceScope': 'PRICE_ONLY',
                'originalBlockingQuestions': copy.deepcopy(case['blockingQuestion']),
                'originalExpectedApply': case.get('expectedApply'),
                'expectedCostXP': xp, 'expectedCostAP': ap,
                'expectedRemainingXP': case['openingXP']-xp, 'expectedRemainingAP': case['openingAP']-ap,
                'expectedUndoXP': case['openingXP'], 'expectedUndoAP': case['openingAP']}
    require(len(updates) == 3922 and sum(u['rulingId'] == FIXED for u in updates.values()) == 3920,
            'Unexpected authorized price subset')
    return dict(sorted(updates.items()))


def load_effective_expected_prices(baseMatrix, baseManifest, sourceRules, authorUpdates):
    """Return a detached view. Price confirmation never erases non-price obligations."""
    require(set(authorUpdates) == {'schemaVersion', 'devOnly', 'baseFileSha256', 'baseSemanticSha256',
        'baseCaseCount', 'authorRulingsSha256', 'updates'}, 'Unexpected overlay fields')
    require(authorUpdates['schemaVersion'] == 1 and authorUpdates['devOnly'] is True, 'Invalid overlay schema')
    require(authorUpdates['baseFileSha256'] == BASE_HASHES and authorUpdates['baseSemanticSha256'] == BASE_SEMANTIC_HASHES,
            'Overlay base identity mismatch')
    require(semantic_sha(baseMatrix) == BASE_SEMANTIC_HASHES['expected_prices.json'] and
            semantic_sha(baseManifest) == BASE_SEMANTIC_HASHES['coverage_manifest.json'], 'Changed immutable base')
    cases = baseMatrix['cases']
    require(authorUpdates['baseCaseCount'] == len(cases) == 83818 and len({c['caseId'] for c in cases}) == 83818,
            'Missing, duplicate or rekeyed base cases')
    require(authorUpdates['authorRulingsSha256'] == authority_revision(sourceRules, 'USER_RULING'), 'Overlay author identity mismatch')
    expected = authorized_price_updates(baseMatrix, baseManifest, sourceRules)
    require(authorUpdates['updates'] == expected, 'Overlay differs from independently authorized price-only subset')
    effective = copy.deepcopy(baseMatrix)
    for case in effective['cases']:
        patch = expected.get(case['caseId'])
        if patch:
            for field in MONEY_FIELDS:
                case[field] = patch[field]
            case.update(evidenceStatus='CONFIRMED', priceAuthority='USER_RULING', rulingId=patch['rulingId'],
                        priceRule='author_fixed_per_rank' if patch['rulingId'] == FIXED else 'author_same_access_currency',
                        originalBlockingQuestions=copy.deepcopy(case['blockingQuestion']), rawCaseSha256=patch['rawCaseSha256'])
    require(sum(c['evidenceStatus'] == 'CONFIRMED' for c in effective['cases']) == 46073 and
            sum(c['evidenceStatus'] == 'SOURCE_UNVERIFIED' for c in effective['cases']) == 37745, 'Unexpected effective totals')
    effective['authorityProvenance'] = {'baseFileSha256': copy.deepcopy(BASE_HASHES),
        'overlaySha256': semantic_sha(authorUpdates), 'authorRulingsSha256': authorUpdates['authorRulingsSha256'],
        'baseConfirmed': 42151, 'baseUnverified': 41667, 'authorPriceConfirmations': 3922}
    return effective


def validate_bounded_source_assertions(rules, source):
    """The small closed vocabulary cannot authorize arbitrary source-like executable facts."""
    beauty = rules['AR_NATURAL_BEAUTY_CREATION_ONLY']
    require(beauty['scope'] == {'type': 'DEFINITION', 'definitionRefs': [definition_ref('feat_65e4315227fdc855')],
        'sourceIds': ['ordinary:t.9xkxyxwswfn9:56902'], 'facet': 'ADMISSION_STAGE'}, 'Overbroad creation-only assertion')
    require(beauty['assertions'] == [{'id': 'natural-beauty-stage', 'type': 'CREATION_ONLY', 'phase': 'Creating',
        'attributeId': 'CHA', 'minimum': 4}], 'Changed source creation-only fact')
    self_taught = rules['AR_SELF_TAUGHT_EXACT_SKILL_STEPS']
    skill_refs = [definition_ref(d['id']) for d in source['definitions'] if d['included'] and d['kind'] == 'skill' and d['rankBasis'] == 'SOURCE_TABLE']
    require(len(skill_refs) == 27 and sorted(self_taught['scope']['definitionRefs'], key=canonical) == sorted(skill_refs, key=canonical), 'Changed retained skill-price scope')
    require({k:v for k,v in self_taught['scope'].items() if k != 'definitionRefs'} == {'type': 'SKILL_DISCOUNT',
        'owner': definition_ref('feat_dd0ba74aedebce10'), 'sourceIds': ['ordinary:t.9xkxyxwswfn9:56370'], 'facet': 'DISCOUNT_PRICE'}, 'Overbroad discount scope')
    require(self_taught['assertions'] == [{'id': 'self-taught-exact-steps', 'type': 'EXACT_SKILL_DISCOUNT', 'phase': 'Finalized',
        'requiresOwnedRank': 1, 'steps': [{'fromRank': 0, 'toRank': 1, 'baseXP': '10', 'discountedXP': '5', 'ap': '0'},
        {'fromRank': 1, 'toRank': 2, 'baseXP': '20', 'discountedXP': '10', 'ap': '0'}]}], 'Changed exact source discount facts')
    drawbacks = rules['AR_DRAWBACK_STAGE_AND_REWARD']
    require(drawbacks['scope'] == {'type': 'CURRENT_NATIVE_DRAWBACK_SET', 'facet': 'DRAWBACK_STAGE_REWARD'}, 'Changed bounded native drawback scope')
    require(drawbacks['assertions'] == [
        {'id': 'drawback-general', 'type': 'DRAWBACK_POLICY', 'creationRewardPerRank': True, 'laterRewardXP': '0', 'prerequisitesPhase': 'Creating'},
        {'id': 'drawback-illness-illustration', 'type': 'SOURCE_ILLUSTRATION', 'name': 'Болезненность', 'maxRank': 1, 'signedCostXP': '-20',
         'creationBalanceDeltaXP': '20', 'laterRewardXP': '0', 'creationAttributeId': 'CON', 'creationMaximum': 3},
        {'id': 'drawback-event', 'type': 'EVENT_DRAWBACK_POLICY', 'creationAllowed': False, 'rewardXP': '0'}], 'Changed source-derived drawback fact')
    smetanie = rules['AR_SMETANIE_BULL_RUSH_TWO']
    require(smetanie['scope'] == {'type': 'DEFINITION', 'definitionRefs': [definition_ref('feat_2cda0df5bcdba24b'), definition_ref('feat_73b6672717f97e05')],
        'sourceIds': ['ordinary:t.9xkxyxwswfn9:41315', 'ordinary:t.9xkxyxwswfn9:41452'], 'facet': 'PREREQUISITE_RANK'}, 'Overbroad prerequisite source scope')
    require(smetanie['assertions'] == [{'id': 'smetanie-prerequisite', 'type': 'OWNED_RANK', 'definitionRef': definition_ref('feat_73b6672717f97e05'),
        'minimum': 2, 'unarmedSkillMinimum': 3, 'referencedMaximum': 2}], 'Changed exact source prerequisite')
    expected = {'AR_NATURAL_BEAUTY_CREATION_ONLY': set(range(11667,11674)),
                'AR_SELF_TAUGHT_EXACT_SKILL_STEPS': set(range(11651,11657)) | set(range(359,365)),
                'AR_DRAWBACK_STAGE_AND_REWARD': {14075,14077,14078,14181,14182},
                'AR_SMETANIE_BULL_RUSH_TWO': set(range(11253,11263)),
                CARRY: {11901,11906,12100,12102,12103,12104,12105,12106,12107}, INITIAL: {11901,11903,11906},
                FIXED: {int(ref.split('P')[1]) for row in rules[FIXED]['fact']['prices'] for ref in row['priceRefs']+row['rankRefs']}}
    for rule_id, paragraphs in expected.items():
        citations = rules[rule_id]['sources' if rule_id in SOURCE_IDS else 'bookEvidence']
        require(len(citations) == len(paragraphs) and {c['paragraph'] for c in citations} == paragraphs, 'Missing/expanded source assertion context')
