"""Runtime regressions against real shared Kotlin rules and bundled catalogs."""
from pathlib import Path
import json
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
INVENTORY = ROOT / "tools/tests/fixtures/manual_requirement_inventory.json"


def validate_coverage(output):
    inventory = json.loads(INVENTORY.read_text(encoding="utf-8"))
    remaining = inventory["remaining"]
    expected = {entry["id"] for entry in remaining}
    assert len(expected) == len(remaining), "Duplicate entries in manual-requirement inventory"
    assert all(entry["reason"].strip() for entry in remaining), "Every residual requirement needs a reason"
    actual = {line.split(" | ")[1] for line in output.splitlines() if line.startswith("MANUAL | ")}
    summary = re.search(r"REQUIREMENTS_AUDIT entries=(\d+) manualEntries=(\d+)", output)
    assert summary is not None, "Audit did not produce a coverage summary"
    assert int(summary[1]) >= inventory["minimum_catalog_entries"], "Bundled catalog was unexpectedly reduced"
    assert int(summary[2]) == len(actual), "Audit count and entries disagree"
    assert actual == expected, (
        f"Manual prerequisite inventory changed. New/unclassified: {sorted(actual - expected)}; "
        f"resolved/removed (update inventory after verification): {sorted(expected - actual)}"
    )
    print(f"COVERAGE_GATE_OK baseline={inventory['baseline_manual_entries']} remaining={len(actual)}")


def run_harness(*args):
    compiler = shutil.which("kotlinc")
    assert compiler is not None, "Kotlin compiler is required; do not silently skip rules tests"
    shared = ROOT / "shared/src/commonMain/kotlin/com/furybook"
    sources = sorted((shared / "dubl/model").glob("*.kt"))
    sources += sorted((shared / "core/json").glob("*.kt"))
    sources += [shared / "dubl/data/CatalogData.kt"]
    sources += sorted((ROOT / "tools/tests/kotlin").glob("AutomaticRequirement*.kt"))
    output = []
    with tempfile.TemporaryDirectory() as temporary:
        jar = Path(temporary) / "requirements.jar"
        result = subprocess.run(
            [compiler, "-language-version", "2.0", *map(str, sources), "-include-runtime", "-d", str(jar)],
            cwd=ROOT, capture_output=True, text=True, timeout=180,
        )
        assert result.returncode == 0, result.stdout + result.stderr
        classes = ["AutomaticRequirementsHarnessKt"]
        if "--audit" not in args:
            classes.append("AutomaticRequirementEdgesKt")
        for main_class in classes:
            result = subprocess.run(
                ["java", "-cp", str(jar), "com.furybook.dubl.model." + main_class, *args],
                cwd=ROOT, capture_output=True, text=True, timeout=60,
            )
            print(result.stdout)
            assert result.returncode == 0, result.stdout + result.stderr
            output.append(result.stdout)
    combined = "\n".join(output)
    if "--audit" in args:
        validate_coverage(combined)
    return combined


def test_automatic_requirements():
    result = run_harness()
    assert "AUTOMATIC_REQUIREMENTS_OK" in result
    assert "AUTOMATIC_REQUIREMENT_EDGES_OK" in result


def test_bundled_requirement_coverage():
    run_harness("--audit")


if __name__ == "__main__":
    import sys
    run_harness(*sys.argv[1:])
