"""Bulk-rank UI wiring and real catalog/application regressions, not an Android crash reproduction."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import pytest

ROOT = Path(__file__).resolve().parents[2]
# Stage 4: the Навыки tabs are gone; «Взять все оставшиеся ранги» lives in the editor's perk detail (all platforms).
EDITOR = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor"


def test_editor_perk_detail_wires_bulk_ranks_through_the_planner():
    models = (EDITOR / "EditorPerkModels.kt").read_text(encoding="utf-8")
    step = (EDITOR / "EditorPerksStep.kt").read_text(encoding="utf-8")
    assert "entry.id, targetRank = max, optionIndex = eval.option, choiceSelections = choiceSelections" in models
    assert "max - rank >= 2" in models
    assert "allRanksAction = allRanks?.let { EditorPerkAction.Acquire(allRanksRequest) }" in models
    assert '"Взять все оставшиеся ранги · ' in models
    assert "applyPerkAction(host, state, detail.entryId, detail.name, allRanks" in step
    # Through the planner and acquireDevelopment, never a plain rank set.
    actions = (EDITOR / "EditorPerkActions.kt").read_text(encoding="utf-8")
    assert "host.acquireDevelopment(action.request)" in actions


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
