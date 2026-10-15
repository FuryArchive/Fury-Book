import copy
import unittest
from pathlib import Path

from tools.rulebook.native_drawback_rules import (
    apply_native_drawback_rules, load_native_drawback_rules,
    native_drawback_runtime_entries, validate_native_drawback_rules,
)

ROOT = Path(__file__).resolve().parents[2]


class NativeDrawbackRulesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = load_native_drawback_rules(ROOT)

    def definitions(self):
        targets = {t['entryId']: t['name'] for r in self.data['records']
                   for t in r.get('forbiddenNewDevelopmentPurchases', [])}
        result = [{'ref': {'packId': 'dubl-3.69', 'entryId': ident}, 'name': name,
                   'kind': 'DEVELOPMENT', 'classificationSources': {}}
                  for ident, name in targets.items()]
        apply_native_drawback_rules(result, ROOT)
        return {item['name']: item for item in result}

    def test_signed_rewards_are_per_rank_only_during_creation(self):
        definitions = self.definitions()
        thin = definitions['Тощий']
        self.assertEqual(3, thin['maxRank'])
        price = thin['priceRule']
        self.assertEqual(('Conditional', 'Creating'), (price['type'], price['condition']['operand']))
        self.assertEqual(('PerRank', '-30'), (price['whenTrue']['type'], price['whenTrue']['xp']))
        self.assertEqual('0', price['whenFalse']['xp'])
        requirement = thin['nativeRequirements']
        self.assertEqual('Finalized', requirement['children'][0]['operand'])
        attr = requirement['children'][1]['children'][1]
        self.assertEqual(('CON', 'LT', 5), (attr['attributeId'], attr['operator'], attr['operand']))
        sickness = definitions['Болезненность']['nativeRequirements']['children'][1]['children'][1]
        self.assertEqual(('CON', 'LE', 3), (sickness['attributeId'], sickness['operator'], sickness['operand']))

    def test_event_cards_require_finalized_and_confirmed_event_without_xp_reward(self):
        item = self.definitions()['Потеря руки']
        self.assertEqual(('Fixed', '0'), (item['priceRule']['type'], item['priceRule']['xp']))
        phase, event = item['nativeRequirements']['children']
        self.assertEqual('Finalized', phase['operand'])
        self.assertEqual('ExternalEvidence', event['type'])
        self.assertEqual('drawback-story-event', event['evidenceKey'])

    def test_choice_cards_remain_single_and_buyback_amount_is_unspecified(self):
        definitions = self.definitions()
        for name in ('Увечье', 'Зависимость', 'Фобия'):
            item = definitions[name]
            self.assertEqual({'type': 'Single'}, item['repeatPolicy'])
            self.assertEqual(1, len(item['parameterDefinitions']))
            self.assertTrue(item['parameterDefinitions'][0]['displayNameKey'])
        one_eye = next(r for r in self.data['records'] if r['name'] == 'Одноглазый')
        self.assertEqual(1, one_eye['buybackPolicy']['minimumRemainingRank'])
        self.assertIsNone(one_eye['buybackPolicy']['xpAmount'])

    def test_exact_source_fields_and_card_inventory_are_validated(self):
        self.assertEqual(47, self.data['inventorySummary']['signedXPCardCount'])
        self.assertEqual(5, self.data['inventorySummary']['eventOnlyCardCount'])
        self.assertEqual(52, len(native_drawback_runtime_entries(ROOT)))
        changed = copy.deepcopy(self.data)
        changed['records'][0]['signedCostXP'] = -200
        with self.assertRaisesRegex(ValueError, 'signed price'):
            validate_native_drawback_rules(changed)
        changed = copy.deepcopy(self.data)
        changed['records'].append(copy.deepcopy(changed['records'][0]))
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            validate_native_drawback_rules(changed)


if __name__ == '__main__':
    unittest.main()
