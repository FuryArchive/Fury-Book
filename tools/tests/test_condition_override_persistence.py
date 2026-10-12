from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
DESKTOP = ROOT / "shared/src/desktopMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/ConditionOverridePersistenceHarness.kt"


def test_desktop_condition_overrides_and_custom_conditions_round_trip():
    kotlinc = shutil.which("kotlinc")
    assert kotlinc is not None
    # Keep the actual shared acquisition/application dependency closure in this legacy regression.
    sources = sorted(path for path in SHARED.rglob("*.kt") if "/ui/" not in path.as_posix())
    sources += [
        DESKTOP / "com/furybook/dubl/data/DesktopCharacterStore.kt",
        DESKTOP / "com/furybook/dubl/data/DesktopCharacterExtrasStore.kt",
        DESKTOP / "com/furybook/dubl/data/DesktopAcquisitionStore.kt",
        HARNESS,
    ]
    coroutines = Path(kotlinc).resolve().parents[1] / "lib/kotlinx-coroutines-core-jvm.jar"
    assert coroutines.is_file(), "Kotlin distribution must include its coroutine runtime"
    with tempfile.TemporaryDirectory() as td:
        jar = Path(td) / "condition-override-persistence.jar"
        compiled = subprocess.run([kotlinc, "-cp", str(coroutines), *map(str, sources), "-include-runtime", "-d", str(jar)], cwd=ROOT, capture_output=True, text=True)
        assert compiled.returncode == 0, compiled.stderr
        result = subprocess.run(["java", "-cp", os.pathsep.join((str(jar), str(coroutines))), "com.furybook.dubl.data.ConditionOverridePersistenceHarnessKt"], cwd=ROOT, capture_output=True, text=True)
        assert result.returncode == 0, result.stderr + result.stdout
        assert "CONDITION_OVERRIDE_PERSISTENCE_OK" in result.stdout


def test_android_extras_repository_persists_condition_overlay():
    text = (ROOT / "app/src/main/java/com/furybook/android/data/CharacterSheetExtrasRepository.kt").read_text()
    assert "condition_overrides" in text
    assert "custom_conditions" in text
    assert "conditionOverrides" in text
    assert "customConditions" in text
