"""Bulk-rank UI wiring and real catalog/application regressions, not an Android crash reproduction."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import pytest

ROOT = Path(__file__).resolve().parents[2]
SCREENS = [
    ("app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt", 1, "currentRank"),
    ("desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt", 2, "rank"),
    ("webApp/src/jsMain/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt", 2, "rank"),
]


@pytest.mark.parametrize("path,call_sites,rank_name", SCREENS)
def test_every_inspector_layout_wires_bulk_ranks_to_the_existing_preview(path, call_sites, rank_name):
    text = (ROOT / path).read_text(encoding="utf-8")
    callbacks = re.findall(r"onAcquireAllRanks = \{ optionIndex ->(.*?)\n\s*\},", text, re.S)
    assert len(callbacks) == call_sites, "Missing bulk-rank callback in an inspector layout"
    for callback in callbacks:
        assert "acquisitionRequest = DevelopmentAcquisitionRequest.single(" in callback
        assert "targetRank = entry.maxRank" in callback
        assert "optionIndex = optionIndex" in callback
        assert "setDevelopmentRank" not in callback
        assert "enforceBudget = false" not in callback
    assert "onAcquireAllRanks: (Int) -> Unit" in text
    assert f"if (entry.maxRank - {rank_name} > 1)" in text
    assert "onClick = { onAcquireAllRanks(optionIndex) }" in text
    assert "Взять все оставшиеся ранги" in text


def test_sequential_and_bulk_ranks_against_real_catalog_and_application():
    compiler = shutil.which("kotlinc")
    assert compiler, "Kotlin compiler is required for runtime regressions"
    shared = ROOT / "shared/src/commonMain/kotlin/com/furybook"
    sources = sorted((shared / "dubl/model").glob("*.kt"))
    sources += sorted((shared / "core/json").glob("*.kt"))
    sources += [shared / path for path in [
        "dubl/data/CatalogData.kt", "dubl/data/CharacterStore.kt", "dubl/data/SnapshotCodec.kt",
        "dubl/state/CharacterSession.kt", "dubl/application/ApplicationUndoManager.kt",
        "dubl/application/DevelopmentApplication.kt",
    ]]
    sources.append(ROOT / "tools/tests/kotlin/DevelopmentBulkRanksHarness.kt")
    with tempfile.TemporaryDirectory() as directory:
        jar = Path(directory) / "bulk-ranks.jar"
        compile_result = subprocess.run(
            [compiler, "-language-version", "2.0", *map(str, sources), "-include-runtime", "-d", str(jar)],
            cwd=ROOT, capture_output=True, text=True, timeout=180,
        )
        assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
        result = subprocess.run(["java", "-Xmx96m", "-jar", str(jar)], cwd=ROOT, capture_output=True, text=True, timeout=90)
        assert result.returncode == 0, result.stdout + result.stderr
        assert "DEVELOPMENT_BULK_RANKS_OK scenarios=14" in result.stdout
        print(result.stdout)
