"""Dev-only independent acquisition corpus. Integrity is not release readiness.

Rebuild from committed evidence: --rebuild. No price is read from runtime code or
catalog costs. The source corpus records manually reviewed tables/anchors;
structural catalog bounds/options are candidates only until source verified.
"""
from __future__ import annotations

import argparse
import hashlib
from collections import Counter
import itertools
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'rulesets/dubl-3.69/acquisition'
COUNTS = dict(sourceOccurrences=536, matchedOccurrences=524,
              uniqueMatchedRuntimeIds=517, developmentWithChi=796,
              developmentWithoutChi=768)
EXPECTED_FIELDS = ('expectedCostXP', 'expectedCostAP', 'expectedRemainingXP',
                   'expectedRemainingAP', 'expectedUndoXP', 'expectedUndoAP')


def contexts(definition: dict):
    """Full finite product plus explicit unresolved-domain sentinel cells."""
    lower, upper = definition['rankBounds']
    transitions = [(a, b) for a in range(lower, upper + 1)
                   for b in range(max(a, 1), upper + 1)]
    if definition['kind'] == 'attribute':
        transitions = [(a, b) for a in range(lower, upper + 1)
                       for b in range(a, upper + 1)]
        transitions += [(0, b) for b in range(lower, 0)]
    if definition['unknownRankMaximum']:
        transitions.append(('UNKNOWN', 'UNKNOWN'))
    for (a, b), option, phase, discount, instance, parameter_class in itertools.product(
            transitions, definition['optionIds'], definition['creationPhases'],
            definition['discountContexts'], definition['instanceClasses'],
            definition['parameterClasses']):
        yield dict(fromRank=a, toRank=b, optionId=option, creationPhase=phase,
                   discountConditions=discount, instance=instance,
                   parameters={'semanticClass': parameter_class})


def case_id(definition_id: str, context: dict) -> str:
    parts = [definition_id, str(context['fromRank']), str(context['toRank']),
             context['optionId'], context['creationPhase'], context['discountConditions'],
             context['instance'], context['parameters']['semanticClass']]
    return '|'.join(parts)


def oracle_cost(definition: dict, context: dict, rules: dict):
    """Only independently reviewed source price rules are confirmed."""
    a, b = context['fromRank'], context['toRank']
    if context['discountConditions'] == 'UNRESOLVED_SOURCE_DISCOUNT':
        return None
    if not isinstance(a, int) or not isinstance(b, int):
        return None
    if context['instance'] != 'EXISTING' or context['parameters']['semanticClass'] != 'FIXED':
        return None
    kind = definition['kind']
    if kind == 'attribute' and b >= a:
        return sum(rules['attributeSteps'][str(r)] for r in range(a + 1, b + 1)), 0, 'attribute_table'
    if kind == 'skill' and definition['id'] in rules['reviewedSkillIds']:
        steps = [rules['skillSteps'][str(r)] for r in range(a + 1, b + 1)]
        if context['creationPhase'] == 'AFTER_CREATION' and context['discountConditions'] == 'SELF_TAUGHT':
            steps = [price // 2 if rank <= 2 else price
                     for rank, price in zip(range(a + 1, b + 1), steps)]
        return sum(steps), 0, 'skill_table_and_self_taught'
    return None


def build_corpus(source: dict):
    definitions, rules = source['definitions'], source['reviewedRules']
    cases = []
    for definition in definitions:
        if not definition['included']:
            continue
        for context in contexts(definition):
            ident = case_id(definition['id'], context)
            price = oracle_cost(definition, context, rules)
            questions = list(definition['blockingQuestions']) if price is None else []
            if context['discountConditions'] == 'UNRESOLVED_SOURCE_DISCOUNT':
                questions.append('Q_SOURCE_DISCOUNT_COMBINATIONS')
            if price is None and not questions:
                questions = ['Q_SOURCE_PRICE_CONTEXT']
            record = dict(caseId=ident, sourceId=definition['sourceIds'],
                          sourceRef=definition['sourceRefs'],
                          definitionRef=definition['id'],
                          **context, ownedPrerequisites=([{'selfTaughtLegallyOwned': True}]
                                                        if context['discountConditions'] == 'SELF_TAUGHT' else []),
                          openingXP=10000, openingAP=100,
                          evidenceStatus='CONFIRMED' if price else 'SOURCE_UNVERIFIED',
                          blockingQuestion=questions, priceRule=price[2] if price else None)
            for field in EXPECTED_FIELDS:
                record[field] = None
            if price:
                xp, ap, _ = price
                record.update(expectedCostXP=xp, expectedCostAP=ap,
                              expectedRemainingXP=10000-xp, expectedRemainingAP=100-ap,
                              expectedUndoXP=10000, expectedUndoAP=100)
            cases.append(record)
    cases.extend(source['anchors'])
    manifest = dict(schemaVersion=1, devOnly=True, counts=COUNTS,
                    sourceProvenance=source['provenance'], sourceOccurrences=source['sourceOccurrences'],
                    catalogIds=[d['id'] for d in definitions], definitions=definitions,
                    reviewedRules=rules, sourceEvidence=source['sourceEvidence'],
                    questions=source['questions'], anchors=source['anchors'],
                    scopeNote=source['scopeNote'])
    return dict(schemaVersion=1, devOnly=True, cases=cases), manifest


def validate_matrix(matrix: dict, manifest: dict) -> list[dict]:
    """Return structural/source integrity errors. Blocked cells are not errors."""
    errors = []
    def error(code, ident):
        entry = dict(code=code, caseId=ident)
        if entry not in errors:
            errors.append(entry)
    if matrix.get('schemaVersion') != 1 or manifest.get('schemaVersion') != 1:
        error('SCHEMA_VERSION', '$manifest')
    definitions = manifest.get('definitions', [])
    catalog_ids = manifest.get('catalogIds', [])
    if Counter(catalog_ids) != Counter(d['id'] for d in definitions):
        error('CATALOG_ID_DRIFT', '$catalog')
    occurrences = manifest.get('sourceOccurrences', [])
    matched = [s for s in occurrences if s.get('runtimeId')]
    development = [d for d in definitions if d['kind'] == 'development']
    actual = dict(sourceOccurrences=len(occurrences), matchedOccurrences=len(matched),
                  uniqueMatchedRuntimeIds=len({s['runtimeId'] for s in matched}),
                  developmentWithChi=len(development),
                  developmentWithoutChi=sum(d['activeWithoutChi'] for d in development))
    if actual != COUNTS or manifest.get('counts') != COUNTS:
        error('SOURCE_COUNT_DRIFT', '$source')
    if len({s['sourceId'] for s in occurrences}) != len(occurrences):
        error('DUPLICATE_SOURCE', '$source')
    required = {}
    for definition in definitions:
        if not definition['included']:
            if not definition.get('exclusionReason'):
                error('UNEXPLAINED_EXCLUSION', definition['id'])
            continue
        if (definition.get('creationPhases') != ['CREATION', 'AFTER_CREATION'] or
                definition.get('discountContexts') != ['NONE', 'SELF_TAUGHT', 'UNRESOLVED_SOURCE_DISCOUNT']):
            error('MISSING_CONTEXT_DOMAIN', definition['id'])
        for context in contexts(definition):
            required[case_id(definition['id'], context)] = (definition, context)
    anchors = {c['caseId']: c for c in manifest.get('anchors', [])}
    supplied = Counter(c.get('caseId') for c in matrix.get('cases', []))
    for ident in sorted(required.keys() | anchors.keys()):
        if ident not in supplied:
            error('MISSING_CASE', ident)
    for ident, count in supplied.items():
        if count > 1:
            error('DUPLICATE_CASE', ident)
        if ident not in required and ident not in anchors:
            error('UNEXPECTED_CASE', ident)
    questions = {q['id'] for q in manifest.get('questions', []) if q['status'] == 'OPEN'}
    for occurrence in occurrences:
        if not occurrence['runtimeId'] and (not occurrence.get('blockingQuestion') or
                any(q not in questions for q in occurrence['blockingQuestion'])):
            error('MISSING_BLOCKING_QUESTION', occurrence['sourceId'])
    evidence_records = manifest.get('sourceEvidence', {})
    evidence = set(evidence_records)
    for ref, excerpt in evidence_records.items():
        if 'sha256' in excerpt and hashlib.sha256(excerpt['text'].encode()).hexdigest() != excerpt['sha256']:
            error('SOURCE_EVIDENCE_DRIFT', ref)
        if any(part not in evidence for part in excerpt.get('paragraphRefs', [])):
            error('SOURCE_REF_MISMATCH', ref)
    for case in matrix.get('cases', []):
        ident = case.get('caseId')
        if ident not in required and ident not in anchors:
            continue
        if ident in anchors:
            template = anchors[ident]
            if case != template:
                error('ANCHOR_MISMATCH', ident)
            confirmed = template['evidenceStatus'] == 'CONFIRMED'
            expected_price = (template['expectedCostXP'], template['expectedCostAP'])
        else:
            definition, context = required[ident]
            price = oracle_cost(definition, context, manifest['reviewedRules'])
            confirmed = price is not None
            expected_price = price[:2] if price else None
            if any(case.get(k) != v for k, v in context.items()) or case.get('definitionRef') != definition['id']:
                error('CONTEXT_MISMATCH', ident)
            if case.get('sourceRef') != definition['sourceRefs'] or case.get('sourceId') != definition['sourceIds']:
                error('SOURCE_REF_MISMATCH', ident)
        status = case.get('evidenceStatus')
        if status not in ('CONFIRMED', 'SOURCE_UNVERIFIED'):
            error('INVALID_EVIDENCE_STATUS', ident)
        if status == 'CONFIRMED':
            if not confirmed or not case.get('sourceRef') or any(ref not in evidence for ref in case['sourceRef']):
                error('SOURCE_UNVERIFIED', ident)
            if case.get('blockingQuestion'):
                error('CONFIRMED_WITH_BLOCKER', ident)
            if expected_price and (case.get('expectedCostXP'), case.get('expectedCostAP')) != expected_price:
                error('PRICE_ORACLE_MISMATCH', ident)
            values = [case.get(k) for k in EXPECTED_FIELDS] + [case.get('openingXP'), case.get('openingAP')]
            if any(type(v) is not int for v in values):
                error('INVALID_AMOUNT', ident)
                continue
            if (case['expectedRemainingXP'] != case['openingXP']-case['expectedCostXP'] or
                    case['expectedRemainingAP'] != case['openingAP']-case['expectedCostAP'] or
                    case['expectedUndoXP'] != case['openingXP'] or case['expectedUndoAP'] != case['openingAP']):
                error('BALANCE_MISMATCH', ident)
        else:
            if not case.get('blockingQuestion') or any(q not in questions for q in case['blockingQuestion']):
                error('MISSING_BLOCKING_QUESTION', ident)
            if any(case.get(k) is not None for k in EXPECTED_FIELDS):
                error('SOURCE_UNVERIFIED', ident)
    return errors


def catalog_drift(manifest: dict) -> list[dict]:
    """Read identity from FCP manifests only; prices never enter this check."""
    current = set()
    fcp = ROOT / 'shared/src/commonMain/resources/fcp'
    for pack_id in ('dubl-3.69', 'dubl-chi-3.69'):
        pack = fcp / pack_id
        descriptor = json.loads((pack / 'manifest.json').read_text())
        for entry in descriptor['entries']:
            document = json.loads((pack / entry['path']).read_text())
            if entry['kind'] == 'dubl.development':
                current.update(row['id'] for row in document['entries'])
            elif entry['kind'] == 'dubl.skills':
                current.update(row['id'] for row in document['skills'])
            elif entry['kind'] == 'dubl.magic-equipment':
                current.update(row['id'] for row in document['spells'])
    baseline = {d['id'] for d in manifest['definitions'] if d['kind'] in ('development', 'skill', 'spell')}
    return [dict(code='CATALOG_ID_DRIFT', caseId=ident) for ident in sorted(current ^ baseline)]


def write_json(path, value):
    # One readable record per line keeps the exhaustive dev corpus reviewable.
    if 'cases' in value:
        rows = ',\n'.join(json.dumps(row, ensure_ascii=False, separators=(',', ':')) for row in value['cases'])
        path.write_text('{\n  "schemaVersion":1,\n  "devOnly":true,\n  "cases":[\n' + rows + '\n]}\n', encoding='utf-8')
    else:
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--matrix', type=Path, default=DATA / 'expected_prices.json')
    parser.add_argument('--manifest', type=Path, default=DATA / 'coverage_manifest.json')
    parser.add_argument('--rebuild', action='store_true', help='use committed source_questions.json only')
    args = parser.parse_args()
    if args.rebuild:
        matrix, manifest = build_corpus(json.loads((DATA / 'source_questions.json').read_text()))
        write_json(args.matrix, matrix)
        write_json(args.manifest, manifest)
    matrix, manifest = json.loads(args.matrix.read_text()), json.loads(args.manifest.read_text())
    errors = validate_matrix(matrix, manifest) + catalog_drift(manifest)
    confirmed = sum(c['evidenceStatus'] == 'CONFIRMED' for c in matrix['cases'])
    blocked = len(matrix['cases']) - confirmed
    print(json.dumps(dict(counts=manifest['counts'], cases=len(matrix['cases']), confirmed=confirmed,
                          blocked=blocked, integrityErrors=len(errors), releaseReady=False), ensure_ascii=False))
    for error in errors:
        print(json.dumps(error, ensure_ascii=False))
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
