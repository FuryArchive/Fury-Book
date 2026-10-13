from tools.tests.kotlin_harness_support import assert_application_route
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "app/src/main/java/com/furybook/android/ui/screens/SkillsScreen.kt"
DESKTOP = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/SkillsScreen.kt"
CONTROLLER = ROOT / "app/src/main/java/com/furybook/android/state/CharacterController.kt"


def test_android_skill_settings_exposes_local_override_and_reset():
    text = ANDROID.read_text(encoding="utf-8")
    assert "Локальные правки" in text
    assert "setSkillUntrainedOverride" in text
    assert "setSkillAutoOverrides" in text
    assert "resetSkillDefinitionOverrides" in text
    assert "Сбросить к рулбуку" in text


def test_desktop_skill_settings_exposes_local_override_and_reset():
    text = DESKTOP.read_text(encoding="utf-8")
    assert "Локальные правки" in text
    assert "setSkillUntrainedOverride" in text
    assert "setSkillAutoOverrides" in text
    assert "resetSkillDefinitionOverrides" in text
    assert "К рулбуку" in text


def test_android_controller_delegates_override_mutations_to_shared_application():
    text = CONTROLLER.read_text(encoding="utf-8")
    for method in (
        "setSkillNameOverride",
        "setSkillDescriptionOverride",
        "setSkillCategoryOverride",
        "setSkillUntrainedOverride",
        "setSkillAutoOverrides",
        "resetSkillDefinitionOverrides",
    ):
        mapping = {
            "setSkillNameOverride": "application.skills.setNameOverride",
            "setSkillDescriptionOverride": "application.skills.setDescriptionOverride",
            "setSkillCategoryOverride": "application.skills.setCategoryOverride",
            "setSkillUntrainedOverride": "application.skills.setUntrainedOverride",
            "setSkillAutoOverrides": "application.skills.setAutoOverrides",
            "resetSkillDefinitionOverrides": "application.skills.resetDefinitionOverrides",
        }
        assert_application_route(text, method, mapping[method])
