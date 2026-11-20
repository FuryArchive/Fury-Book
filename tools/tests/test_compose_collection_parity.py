from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKILLS = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorSkillsStep.kt').read_text(encoding='utf-8')
SKILL_MODELS = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorModels.kt').read_text(encoding='utf-8')
# Stage 4: the Навыки tab is gone; its catalog, filters and ЦИ techniques live in the editor's Навыки step.
PERKS = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorPerkModels.kt').read_text(encoding='utf-8')
CHI = (ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor/EditorChiCard.kt').read_text(encoding='utf-8')


def test_skills_expose_all_categories_and_group_results():
    # Stage 3: the editor's Умения step filters by every category and groups by attribute.
    assert 'SkillCategory.entries.forEach' in SKILLS
    assert 'category: SkillCategory? = null' in SKILL_MODELS
    assert 'sheetSkillsByAttribute(host' in SKILL_MODELS

def test_development_has_available_filter_and_android_grouping_semantics():
    assert 'EditorPerkFilter.AVAILABLE' in PERKS
    # Groups come from the shared index (category, or the branch for special perks), as the old tabs grouped them.
    assert 'indexed.groupName' in PERKS and 'DevelopmentScreenIndex(catalog)' in PERKS
    assert 'filterNot { it.incomplete }' not in PERKS
    assert 'MagicEquipmentRules.BASE_MANA_ENTRY_ID' in PERKS


def test_chi_search_and_available_filter_cover_techniques():
    assert 'query: String = ""' in CHI and 'availableOnly: Boolean = false' in CHI
    assert 'availableOnly && !availability.unlocked' in CHI
    assert 'technique.school' in CHI
