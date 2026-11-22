from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# Stage 3: local overrides live in the editor's skill panel (shared by all platforms).
PANEL = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillPanel.kt"
DRAFT = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillTextDraft.kt"
CONTROLLER = ROOT / "app/src/main/java/com/furybook/android/state/CharacterController.kt"




def test_editor_skill_panel_exposes_local_override_and_reset():
    panel = PANEL.read_text(encoding="utf-8")
    draft = DRAFT.read_text(encoding="utf-8")
    assert "Локальные правки" in panel
    assert "setSkillUntrainedOverride" in panel
    assert "setSkillCategoryOverride" in panel
    assert "resetSkillDefinitionOverrides" in panel
    assert "К рулбуку" in panel
    assert "setSkillAutoOverrides" in draft
    assert "setSkillNameOverride" in draft


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
        assert mapping[method] in text
