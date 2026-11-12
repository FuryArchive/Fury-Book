"""Runtime regressions against real shared Kotlin rules and bundled catalogs."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


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
    return "\n".join(output)


def test_automatic_requirements():
    result = run_harness()
    assert "AUTOMATIC_REQUIREMENTS_OK" in result
    assert "AUTOMATIC_REQUIREMENT_EDGES_OK" in result


if __name__ == "__main__":
    import sys
    run_harness(*sys.argv[1:])
