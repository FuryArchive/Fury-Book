from pathlib import Path

# Stage 3: the Умения tab is gone; what only it could do lives in the editor's skill panel and the roll panel.
ROOT = Path(__file__).resolve().parents[2]
DESKTOP_MAIN = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt"
WEB_MAIN = ROOT / "webApp/src/jsMain/kotlin/com/furybook/web/Main.kt"
ANDROID_APP = ROOT / "app/src/main/java/com/furybook/android/ui/DublApp.kt"
OLD_SCREENS = [
    ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/SkillsScreen.kt",
    ROOT / "webApp/src/jsMain/kotlin/com/furybook/desktop/screens/SkillsScreen.kt",
    ROOT / "app/src/main/java/com/furybook/android/ui/screens/SkillsScreen.kt",
]
EDITOR = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_no_skills_section_on_any_platform():
    for path in (DESKTOP_MAIN, WEB_MAIN):
        assert "DesktopSection.SKILLS" not in read(path), path.name
    assert "AppSection.SKILLS" not in read(ANDROID_APP)
    assert 'SKILLS("Умения")' not in read(ANDROID_APP)


def test_old_skills_screens_are_gone():
    for path in OLD_SCREENS:
        assert not path.exists(), path


def test_moved_functions_live_in_the_editor():
    panel = read(EDITOR / "EditorSkillPanel.kt")
    for call in (
        "resetSkillDefinitionOverrides", "setSkillEffectEnabled", "hideSkill",
        "deleteDynamicSkill", "setSkillAttributes", "setSkillModifier", "setSkillUntrainedOverride",
        "setSkillCategoryOverride", "restoreAllSkills", "restoreSkill",
    ):
        assert call in panel, call
    draft = read(EDITOR / "EditorSkillTextDraft.kt")
    for call in ("setSkillFormulaNote", "setSkillNameOverride", "setSkillDescriptionOverride", "setSkillAutoOverrides"):
        assert call in draft, call
