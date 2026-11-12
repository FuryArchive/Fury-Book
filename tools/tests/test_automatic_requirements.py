"""Runtime regression tests against the real shared rules and bundled catalogs."""
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
    sources += [shared / "dubl/data/CatalogData.kt", ROOT / "tools/tests/kotlin/AutomaticRequirementsHarness.kt"]
    with tempfile.TemporaryDirectory() as temporary:
        jar = Path(temporary) / "requirements.jar"
        compile_result = subprocess.run(
            [compiler, "-language-version", "2.0", *map(str, sources), "-include-runtime", "-d", str(jar)],
            cwd=ROOT, capture_output=True, text=True, timeout=180,
        )
        assert compile_result.returncode == 0, compile_result.stdout + compile_result.stderr
        result = subprocess.run(["java", "-jar", str(jar), *args], cwd=ROOT, capture_output=True, text=True, timeout=60)
        print(result.stdout)
        assert result.returncode == 0, result.stdout + result.stderr
        return result.stdout


def test_automatic_requirements():
    assert "AUTOMATIC_REQUIREMENTS_OK" in run_harness()


if __name__ == "__main__":
    import sys
    run_harness(*sys.argv[1:])
