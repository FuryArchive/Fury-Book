from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROMOTED = ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content/conditions_catalog.json"
KOTLIN = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/model/CharacterSheetExtras.kt"
ANDROID = ROOT / "app/src/main/java/com/furybook/android/ui/AndroidSheetHost.kt"
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet/SheetConditionsDialog.kt"


def _enum_titles() -> list[str]:
    text = KOTLIN.read_text(encoding="utf-8")
    block = text.split("enum class CharacterConditionId", 1)[1].split("enum class CharacterSheetResourceId", 1)[0]
    return re.findall(r'^\s*[A-Z_]+\(\s*"([^"]+)"\s*\)', block, flags=re.M)


def test_promoted_rulebook_conditions_match_runtime_condition_ids_and_own_the_text():
    payload = json.loads(PROMOTED.read_text(encoding="utf-8"))
    source_titles = [item["name"] for item in payload["conditions"]]
    assert source_titles == _enum_titles()
    assert len(source_titles) == len(set(source_titles)) == 23
    assert all(item["description"].strip() for item in payload["conditions"])
    assert all(ref.startswith("core:") for item in payload["conditions"] for ref in item["sourceRefs"])

    enum_text = KOTLIN.read_text(encoding="utf-8")
    assert "rulesSummary" not in enum_text
    assert "В разделе состояний отдельно не расписан" not in enum_text


def test_android_and_desktop_condition_details_use_shared_condition_catalog():
    # Stage 2b: the shared conditions popup reads the host catalog; Android's host loads the bundled one.
    android = ANDROID.read_text(encoding="utf-8")
    sheet = SHEET.read_text(encoding="utf-8")
    assert "ConditionCatalogRepository" in android
    assert "host.conditionCatalog.summary(condition)" in sheet
    assert ".rulesSummary" not in android
    assert ".rulesSummary" not in sheet
