"""Acquisition checks against retained native rules, independent of legacy prices."""
import unittest
from pathlib import Path

from tools.rulebook.native_magic_rules import apply_native_magic_rules, load_native_magic_rules

ROOT = Path(__file__).resolve().parents[2]


class NativeMagicRulesTest(unittest.TestCase):
    def definition(self, entry, kind='DEVELOPMENT', pack='dubl-3.69'):
        return {'ref': {'packId': pack, 'entryId': entry}, 'kind': kind,
                'name': 'placeholder', 'sourceStatus': 'Unavailable',
                'sourceRef': {}, 'requirementsText': '', 'classificationSources': {},
                'priceRule': {'type': 'Unknown', 'reason': 'source-unverified'}}

    def test_native_spell_learning_has_price_and_single_learned_identity(self):
        d = self.definition('spell_54234bfde5761792', 'SPELL')
        apply_native_magic_rules([d], ROOT, 'test')
        self.assertEqual({'type': 'Single'}, d['repeatPolicy'])
        self.assertEqual(1, d['maxRank'])
        self.assertEqual(('Fixed', '20', '0'), tuple(d['priceRule'][k] for k in ('type', 'xp', 'ap')))
        self.assertEqual(('NumericProperty', 'MAGIC_POWER', 6),
                         tuple(d['nativeRequirements'][k] for k in ('type', 'propertyId', 'operand')))
        self.assertEqual('Confirmed', d['sourceStatus'])

    def test_missing_or_conflicting_native_spell_never_inherits_legacy_price(self):
        for entry in ('spell_375ac5a4182a072c', 'magic-school:разум'):
            d = self.definition(entry, 'SCHOOL' if entry.startswith('magic') else 'SPELL')
            apply_native_magic_rules([d], ROOT, 'test')
            self.assertEqual('Unknown', d['priceRule']['type'])

    def test_only_explicit_each_rank_statement_prices_repeats(self):
        a = self.definition('feat_bf7b04eb68961725')
        apply_native_magic_rules([a], ROOT, 'test')
        # Mana's own wording explicitly states 100 XP for every rank.
        self.assertEqual('PerRank', a['priceRule']['type'])
        data = load_native_magic_rules(ROOT)
        ranked = next(x for x in data['bindings'] if x['kind'] == 'DEVELOPMENT'
                      and x['ref']['packId'] == 'dubl-3.69' and x['maxRank'] > 1
                      and not x.get('unitPerRank'))
        d = self.definition(ranked['ref']['entryId'])
        apply_native_magic_rules([d], ROOT, 'test')
        self.assertEqual('TransitionTable', d['priceRule']['type'])
        self.assertEqual([(0, 1)], [(x['fromRank'], x['toRank']) for x in d['priceRule']['transitions']])

    def test_chi_reserve_uses_explicit_50_xp_each_rank_maximum_10(self):
        d = self.definition('chi:bonus-ranks', 'CHI_BONUS', 'dubl-chi-3.69')
        apply_native_magic_rules([d], ROOT, 'test')
        self.assertEqual((10, 'PerRank', '50'), (d['maxRank'], d['priceRule']['type'], d['priceRule']['xp']))

    def test_retained_excerpt_tampering_fails_before_import(self):
        data = load_native_magic_rules(ROOT)
        ref = next(iter(data['evidence']))
        data['evidence'][ref]['text'] += ' changed'
        from tools.rulebook.native_magic_rules import validate_native_magic_rules
        with self.assertRaisesRegex(ValueError, 'hash'):
            validate_native_magic_rules(data)

    def test_same_name_cards_keep_native_branch_context(self):
        unrelated = self.definition('feat_5d529855eb3823a5')
        actual = self.definition('feat_9501c95b22d02ede')
        first = self.definition('feat_9297f6d3c3564ee5')
        second = self.definition('feat_6e5f776e24effe94')
        apply_native_magic_rules([unrelated, actual, first, second], ROOT, 'test')
        self.assertEqual('Unknown', unrelated['priceRule']['type'])
        self.assertEqual('Анимагия 9', actual['requirementsText'])
        self.assertEqual(('35', '15'), (first['priceRule']['xp'], second['priceRule']['transitions'][0]['xp']))

    def test_spell_variants_select_exact_school_and_mana_context(self):
        d = self.definition('spell_7a56d81779a3591b', 'SPELL')
        apply_native_magic_rules([d], ROOT, 'test')
        self.assertEqual('10', d['priceRule']['xp'])
        self.assertEqual(2, d['nativeRequirements']['operand'])
        self.assertTrue(any(x['originalText'] == 'Школа: Разум' for x in d['classificationSources'].values()))


if __name__ == '__main__':
    unittest.main()
