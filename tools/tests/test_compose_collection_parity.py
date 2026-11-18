from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKILLS = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillsStep.kt').read_text(encoding='utf-8')
SKILL_MODELS = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorModels.kt').read_text(encoding='utf-8')
DEV = (ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt').read_text(encoding='utf-8')


def test_skills_expose_all_categories_and_group_results():
    # Stage 3: the editor's Умения step filters by every category and groups by attribute.
    assert 'SkillCategory.entries.forEach' in SKILLS
    assert 'category: SkillCategory? = null' in SKILL_MODELS
    assert 'sheetSkillsByAttribute(host' in SKILL_MODELS

def test_development_has_available_filter_and_android_grouping_semantics():
    assert 'availableOnly' in DEV
    assert 'Доступно сейчас' in DEV
    assert 'fun branchName(' in DEV
    assert 'groupBy(::branchName)' in DEV
    assert 'groupBy { it.category.ifBlank { "Общие" } }' in DEV
    assert 'groupBy { it.category.ifBlank { "Боевые искусства" } }' in DEV
    assert '&& !entry.incomplete' not in DEV
    assert 'filterNot { it.incomplete }' not in DEV
    assert 'MagicEquipmentRules.BASE_MANA_ENTRY_ID' in DEV


def test_chi_search_and_available_filter_cover_techniques():
    assert 'filteredChiTechniques' in DEV
    assert 'chiRules.availability(technique).unlocked' in DEV
    assert 'groupBy { it.school }' in DEV
