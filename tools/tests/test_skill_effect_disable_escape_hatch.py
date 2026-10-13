from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / "shared/src/commonMain/kotlin"
HARNESS = ROOT / "tools/tests/kotlin/SkillEffectDisableHarness.kt"


def test_skill_effect_automation_can_be_disabled_per_character_and_persists():
    from tools.tests.kotlin_harness_support import run_harness
    result = run_harness(HARNESS)
    assert 'SKILL_EFFECT_DISABLE_OK' in result.stdout


def test_android_and_desktop_expose_skill_effect_automation_escape_hatch():
    android_controller = (ROOT / "app/src/main/java/com/furybook/android/state/CharacterController.kt").read_text(encoding="utf-8")
    android_skills = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/SkillsScreen.kt").read_text(encoding="utf-8")
    desktop_skills = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/SkillsScreen.kt").read_text(encoding="utf-8")
    assert "setSkillEffectEnabled" in android_controller
    assert "Автоматизация правил" in android_skills
    assert "setSkillEffectEnabled(effect.id, enabled)" in android_skills
    assert "Автоматизация правил" in desktop_skills
    assert "setSkillEffectEnabled(effect.id, enabled)" in desktop_skills
