import copy
import unittest
from pathlib import Path

from tools.rulebook.native_martial_rules import (
    apply_native_martial_rules, load_native_martial_rules,
    parse_native_martial_requirement, validate_native_martial_rules, correct_native_martial_payload,
)

ROOT = Path(__file__).resolve().parents[2]


class NativeMartialRulesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = load_native_martial_rules(ROOT)
        cls.aliases = {a['exactText']: a['entryId'] for a in cls.data['styleAliases']}

    def test_all_existing_identities_get_native_prices_and_new_references_get_cards(self):
        definitions = [
            {'ref': dict(r['ref']), 'name': r['name'], 'kind': 'DEVELOPMENT',
             'sourceStatus': 'Unavailable', 'classificationSources': {}}
            for r in self.data['records'] if 'runtimeEntry' not in r]
        original = {d['ref']['entryId'] for d in definitions}
        self.assertEqual(122, len(original))
        apply_native_martial_rules(definitions, ROOT, 'test')
        self.assertEqual(len(self.data['records']), len(definitions))
        self.assertTrue(original <= {d['ref']['entryId'] for d in definitions})
        for d in definitions:
            self.assertIn(d['sourceStatus'], ('Confirmed', 'NeedsResolution'))
            self.assertIn(d['priceRule']['type'], ('Fixed', 'TransitionTable', 'Unknown'))
            if d['maxRank'] is not None:
                self.assertGreater(d['maxRank'], 0)
            self.assertTrue(d['sourceRef']['documentVersion'].startswith('native-text-sha256:'))
            for citation in d['classificationSources'].values():
                self.assertTrue(citation.get('paragraph') is None or isinstance(citation['paragraph'], int))

    def test_style_alternatives_keep_the_non_style_and_tail(self):
        source = {'documentId': 'test', 'documentVersion': 'test', 'originalText': ''}
        definitions = [{'ref': {'packId': 'dubl-3.69', 'entryId': 'hurricane'},
                        'name': 'Ураганная атака', 'kind': 'DEVELOPMENT'}]
        tree = parse_native_martial_requirement(
            'Боевые искусства: Каратэ, Содзицу, Ураганная атака', 'owner/requirements',
            source, definitions, self.aliases)
        self.assertEqual('AllOf', tree['type'])
        self.assertEqual('AnyOf', tree['children'][0]['type'])
        self.assertEqual(2, len(tree['children'][0]['children']))
        self.assertEqual('hurricane', tree['children'][1]['entryId'])

    def test_closed_spelling_and_actual_draft_remain_unresolved(self):
        source = {'documentId': 'test', 'documentVersion': 'test', 'originalText': ''}
        for text in ('Боевые искусства: Кунг фу', 'Штука с парированием'):
            tree = parse_native_martial_requirement(text, 'owner/requirements', source, [], self.aliases)
            self.assertEqual('Unresolved', tree['type'])
        draft = parse_native_martial_requirement(
            'Штука с парированием', 'owner/requirements', source, [], self.aliases)
        self.assertEqual('native-source-draft-no-definition:Штука с парированием', draft['reason'])

    def test_excerpt_and_numeric_fact_tampering_fail(self):
        data = copy.deepcopy(self.data)
        next(iter(data['evidence'].values()))['text'] += ' changed'
        with self.assertRaisesRegex(ValueError, 'hash'):
            validate_native_martial_rules(data)
        data = copy.deepcopy(self.data)
        data['records'][0]['xp'] += 1
        with self.assertRaisesRegex(ValueError, 'price'):
            validate_native_martial_rules(data)

    def test_conflicting_and_omitted_source_fields_stay_blocked(self):
        definitions = [
            {'ref': dict(r['ref']), 'name': r['name'], 'kind': 'DEVELOPMENT',
             'sourceStatus': 'Unavailable', 'classificationSources': {}}
            for r in self.data['records'] if 'runtimeEntry' not in r]
        apply_native_martial_rules(definitions, ROOT, 'test')
        by_id = {d['ref']['entryId']: d for d in definitions}
        from tools.rulebook.import_acquisition import walk
        for record in self.data['records']:
            item = by_id[record['ref']['entryId']]
            reasons = [n['reason'] for n in walk(item['nativeRequirements']) if n['type'] == 'Unresolved']
            if record.get('conflictingPrice'):
                self.assertEqual('Unknown', item['priceRule']['type'])
                self.assertIn('conflicting-current-native-martial-variants', reasons)
            if record.get('missingRankSource'):
                self.assertIsNone(item['maxRank'])
                if record.get('initialRankPurchaseConfirmed'):
                    self.assertNotIn('missing-current-native-martial-rank-maximum', reasons)
                else:
                    self.assertIn('missing-current-native-martial-rank-maximum', reasons)

    def test_native_price_occurrences_cannot_be_counted_twice(self):
        data = copy.deepcopy(self.data)
        self.assertEqual(907, data['inventorySummary']['nativePricedOccurrences'])
        data['nativePurchaseInventory'].append(copy.deepcopy(data['nativePurchaseInventory'][0]))
        with self.assertRaisesRegex(ValueError, 'counted more than once'):
            validate_native_martial_rules(data)

    def test_native_style_count_excludes_stances_and_combo_repeat_stays_explicit(self):
        bindings = {(b['ownerId'], b['exactText']): b for b in self.data['requirementBindings']}
        gan = bindings[('martial_technique_6e7e3bdb291edb76', '6 приёмов стиля')]['tree']
        self.assertEqual(('DistinctCount', 'Definition', 6),
                         (gan['type'], gan['uniqueness'], gan['operand']))
        members = {n['entryId'] for n in gan['predicate']['children']}
        self.assertEqual(10, len(members))
        self.assertNotIn('martial_technique_5dc9304819dcb93b', members)  # Two-pistol stance.
        combo = next(r for r in self.data['records'] if r['name'] == 'Комбо')
        self.assertEqual('Unresolved', combo['repeatPolicy']['type'])
        self.assertIn('сколько угодно раз', self.data['evidence'][combo['repeatEvidence'][1]]['text'])

    def test_material_effect_variants_are_blocked_and_editorial_rewording_is_reviewed(self):
        records = {r['name']: r for r in self.data['records'] if r['kind'] == 'STYLE'}
        self.assertTrue(records['Кунг-фу Шао-Линь']['conflictingEffect'])
        self.assertIn('sourceConflict', records['Кабан'])
        self.assertEqual('EDITORIAL_REWORDING', records['Кот']['effectVariantReview']['status'])
        self.assertNotIn('sourceConflict', records['Кот'])

    def test_owner_local_spelling_binding_does_not_become_global_alias(self):
        binding = next(b for b in self.data['requirementBindings'] if b['exactText'] == 'Двойной орёл')
        source = {'documentId': 'test', 'documentVersion': 'test', 'originalText': ''}
        tree = parse_native_martial_requirement(
            'Двойной орёл', 'owner/requirements', source, [], self.aliases,
            authored_atoms={binding['exactText']: binding['tree']})
        self.assertEqual('martial_technique_aa92cd307a053bce', tree['entryId'])
        unbound = parse_native_martial_requirement(
            'Двойной орёл', 'other/requirements', source, [], self.aliases)
        self.assertEqual('Unresolved', unbound['type'])

    def test_supported_combo_contract_clears_only_obsolete_technical_display_flags(self):
        payload = {'entries': [copy.deepcopy(r.get('runtimeEntry', {
            'id': r['ref']['entryId'], 'name': r['name']})) for r in self.data['records']]}
        old = next(e for e in payload['entries'] if e['name'] == 'Комбо')
        old.update(incomplete=True, mechanicsConflict='obsolete technical placeholder')
        result = correct_native_martial_payload(payload, ROOT)
        combo = next(e for e in result['entries'] if e['name'] == 'Комбо')
        self.assertNotIn('incomplete', combo)
        self.assertNotIn('mechanicsConflict', combo)
        self.assertTrue(old['incomplete'])  # Promotion does not mutate its input.
        self.assertTrue(next(e for e in result['entries'] if e['name'] == 'Кабан')['incomplete'])

    def test_one_reviewed_priced_card_confirms_only_its_initial_rank_without_a_maximum(self):
        definitions = [{'ref': dict(r['ref']), 'name': r['name'], 'kind': 'DEVELOPMENT',
                        'sourceStatus': 'Unavailable', 'classificationSources': {}}
                       for r in self.data['records'] if 'runtimeEntry' not in r]
        apply_native_martial_rules(definitions, ROOT, 'test')
        by_id = {d['ref']['entryId']: d for d in definitions}
        item = by_id['martial_technique_6e7e3bdb291edb76']
        self.assertIsNone(item['maxRank'])
        self.assertEqual({'type': 'Single'}, item['repeatPolicy'])
        self.assertEqual([], item['parameterDefinitions'])
        self.assertEqual('Стоимость: 100\vТребование: Ган-Ката, 6 приёмов стиля, Стрельба 6, '
                         'Рукопашный бой 5, Ловкость 6, Восприятие 6.\vОграничение: 1 раз в день.',
                         item['classificationSources']['purchase:initial-rank-confirmed']['originalText'])
        self.assertEqual([(0, 1, '100')], [(t['fromRank'], t['toRank'], t['xp'])
                         for t in item['priceRule']['transitions']])
        from tools.rulebook.import_acquisition import walk
        nodes = list(walk(item['nativeRequirements']))
        self.assertTrue(any(n['type'] == 'DistinctCount' and n['operand'] == 6 for n in nodes))
        self.assertFalse(any(n['type'] == 'Unresolved' and n['reason'] ==
                             'missing-current-native-martial-rank-maximum' for n in nodes))
        jump = by_id['martial_technique_eca5dd325af3332b']
        self.assertNotIn('purchase:initial-rank-confirmed', jump['classificationSources'])
        self.assertTrue(any(n['type'] == 'Unresolved' and n['reason'] ==
                            'conflicting-current-native-martial-variants'
                            for n in walk(jump['nativeRequirements'])))


if __name__ == '__main__':
    unittest.main()
