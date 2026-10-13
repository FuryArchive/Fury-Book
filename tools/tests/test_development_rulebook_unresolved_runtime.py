import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/development_regular_catalog.json"


def entries_by_id():
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    return {entry["id"]: entry for entry in data["entries"]}


def test_blocking_uses_rulebook_requirement_as_canonical_mechanics():
    entry = entries_by_id()["feat_fcdd74622d21435d"]

    assert entry["requirements"] == "Ловкость 3 или Скорость 3; Холодное оружие 3 или Рукопашный бой 3"
    assert not entry.get("incomplete", False)
    assert not entry.get("mechanicsConflict", "")
    assert not entry.get("conflictNote", "")


def test_last_stand_keeps_legacy_rank_but_marks_missing_rulebook_rank_as_unresolved():
    entry = entries_by_id()["feat_42473bc0735b2186"]

    assert entry["ranks"] == 1
    assert entry["incomplete"] is True
    assert entry["mechanicsConflict"] == "В DUBL 3.69 не указан ранг"
    assert "1" in entry["conflictNote"]
    assert "локальн" in entry["conflictNote"].lower()
