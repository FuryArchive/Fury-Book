import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEV_REGULAR = ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/development_regular_catalog.json"
DEV_FILES = [
    ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/development_regular_catalog.json",
    ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/development_special_catalog.json",
    ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/development_ability_roots_catalog.json",
    ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/development_martial_catalog.json",
    ROOT / "shared/src/commonMain/resources/fcp/dubl-chi-3.69/content/development_chi_catalog.json",
    ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/development_magic_catalog.json",
    ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/development_catalog.json",
]
MAGIC = ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/magic_equipment_catalog.json"


class Android06ContentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.entries = []
        for path in DEV_FILES:
            cls.entries.extend(json.loads(path.read_text(encoding="utf-8"))["entries"])
        cls.by_name = {entry["name"]: entry for entry in cls.entries}
        cls.magic_root = json.loads(MAGIC.read_text(encoding="utf-8"))
        cls.spells = cls.magic_root["spells"]
        cls.spells_by_name = {spell["name"]: spell for spell in cls.spells}

    def test_crafter_branch_keeps_reported_entries(self):
        for name in ("Импровизированные инструменты", "Мастерская на коленке"):
            self.assertIn(name, self.by_name)
            self.assertEqual("Мастер-ремесленник", self.by_name[name]["category"])

    def test_blocking_uses_rulebook_requirement(self):
        self.assertEqual(
            "Ловкость 3 или Скорость 3; Холодное оружие 3 или Рукопашный бой 3",
            self.by_name["Блокирование"]["requirements"],
        )

    def test_recovered_rulebook_entries_exist(self):
        expected = {
            "Сражаться до последнего",
            "Оборонительный хват",
            "Защита двумя оружиями",
            "Мастер дубильщик",
            "Зодчий",
            "Шеф-повар",
            "Зельевар",
            "Монстровед",
            "Ускоренное восприятие",
        }
        self.assertTrue(expected.issubset(self.by_name), expected - self.by_name.keys())

    def test_corrupted_neighbor_entries_are_clean(self):
        self.assertNotIn("Сражаться до последнего", self.by_name["Выхватывание"].get("benefit", ""))
        self.assertNotIn("Оборонительный хват", self.by_name["Совершенное парирование"].get("benefit", ""))
        self.assertNotIn("Защита двумя оружиями", self.by_name["Синхронный удар"].get("benefit", ""))
        self.assertNotIn("Монстровед", self.by_name["Углубленное изучение"].get("benefit", ""))
        self.assertNotIn("Ускоренное восприятие", self.by_name["Отбивание пуль"].get("benefit", ""))

    def test_battle_magic_archmage_spells_are_present(self):
        expected = {
            "Клинок Короля-Ворона",
            "Копьё Последней Надежды",
            "Секира Ледяного Гиганта",
            "Лук Эльфийского Принца",
            "Щит Утерянного Легиона (реакция)",
        }
        self.assertTrue(expected.issubset(self.spells_by_name), expected - self.spells_by_name.keys())
        for name in expected:
            self.assertEqual("Боевая магия", self.spells_by_name[name]["school"])

    def test_catalog_ids_are_unique(self):
        dev_ids = [entry["id"] for entry in self.entries]
        spell_ids = [spell["id"] for spell in self.spells]
        self.assertEqual(len(dev_ids), len(set(dev_ids)))
        self.assertEqual(len(spell_ids), len(set(spell_ids)))


if __name__ == "__main__":
    unittest.main()
