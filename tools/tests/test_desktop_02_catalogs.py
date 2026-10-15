import json
import unittest
from pathlib import Path
from tools.tests.native_runtime_catalog_expectations import assert_native_runtime_ids

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / 'shared/src/commonMain/resources/fcp/dubl-3.69/content'
CHI = ROOT / 'shared/src/commonMain/resources/fcp/dubl-chi-3.69/content'
DEV_LAYERS = {
    'development_regular_catalog.json': (CORE, 287),
    'development_special_catalog.json': (CORE, 304),
    'development_ability_roots_catalog.json': (CORE, 41),
    'development_martial_catalog.json': (CORE, 501),
    'development_native_supplement_catalog.json': (CORE, 64),
    'development_native_magic_catalog.json': (CORE, 22),
    'development_chi_catalog.json': (CHI, 27),
    'development_magic_catalog.json': (CORE, 15),
    'development_catalog.json': (CORE, 0),
}
PAYLOADS = [
    (CORE, 'conditions_catalog.json'),
    *((root, name) for name, (root, _) in DEV_LAYERS.items()),
    (CHI, 'chi_catalog.json'),
    (CORE, 'magic_equipment_catalog.json'),
    (CORE, 'skill_effects_catalog.json'),
]

class Desktop02CatalogsTest(unittest.TestCase):
    def test_fcp_owned_resources_are_the_canonical_catalog_payloads(self):
        for root, name in PAYLOADS:
            self.assertTrue((root / name).exists(), f'missing FCP-owned resource {root.name}/{name}')

    def test_catalog_counts_match_rulebook_promoted_runtime(self):
        development_total = 0
        for name, (root, expected) in DEV_LAYERS.items():
            payload = json.loads((root / name).read_text())
            self.assertEqual(len(payload['entries']), expected, name)
            assert_native_runtime_ids(name, payload['entries'])
            development_total += expected
        self.assertEqual(development_total, 1261)

        chi = json.loads((CHI / 'chi_catalog.json').read_text())
        magic = json.loads((CORE / 'magic_equipment_catalog.json').read_text())
        effects = json.loads((CORE / 'skill_effects_catalog.json').read_text())
        self.assertEqual(len(chi['schools']), 9)
        self.assertEqual(len(chi['techniques']), 68)
        self.assertEqual(len(magic['spells']), 298)
        self.assertEqual(len(magic['gear']), 260)
        self.assertEqual(len(effects['effects']), 283)

    def test_common_parser_source_exists(self):
        parser = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/data/CatalogData.kt'
        self.assertTrue(parser.exists())
        text = parser.read_text()
        for symbol in ['parseConditionCatalog', 'parseDevelopmentCatalog', 'parseChiCatalog', 'parseMagicEquipmentCatalog', 'parseSkillEffectCatalog']:
            self.assertIn(f'fun {symbol}', text)

if __name__ == '__main__':
    unittest.main()
