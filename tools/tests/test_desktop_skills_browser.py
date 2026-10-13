from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKILLS = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/SkillsScreen.kt'


def read(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def section(src: str, start: str, end: str) -> str:
    return src.split(start, 1)[1].split(end, 1)[0]


def test_skills_browser_uses_compact_toolbar_and_two_column_grid():
    src = read(SKILLS)
    root = section(src, 'fun SkillsScreen(', '@Composable\nprivate fun SkillInspector')
    assert 'BoxWithConstraints' in root
    assert 'LazyVerticalGrid' in root
    assert 'GridCells.Fixed(columns)' in root
    assert 'val columns = if (maxWidth >= 980.dp) 2 else 1' in root
    assert 'DesktopSkillsToolbar(' in root
    assert 'DesktopSkillCard(' in root
    assert 'Text("Настроить")' not in root


def test_skill_card_prioritizes_bonus_rank_cost_and_roll_action():
    src = read(SKILLS)
    card = section(src, 'private fun DesktopSkillCard(', '@Composable\nprivate fun SkillInspector')
    assert 'bonus: String' in card
    assert 'nextRankCost: Int?' in card
    assert 'DesktopSmallAction(' in card
    assert 'label = "Бросок"' in card
    assert 'maxLines = 1' in card
    assert '"ранг $rank"' in card


def test_clicking_skill_opens_non_modal_right_side_inspector():
    src = read(SKILLS)
    root = section(src, 'fun SkillsScreen(', '@Composable\nprivate fun SkillInspector')
    inspector = section(src, 'private fun SkillInspector(', '@Composable\nprivate fun HiddenSkillsDialog')
    assert 'selectedSkillId' in root
    assert 'SkillInspector(' in root
    assert 'Modifier.widthIn(min = 340.dp, max = 430.dp)' in root
    assert 'FuryDialog(' not in inspector
    assert 'verticalScroll' in inspector
    assert 'RankStepper(' in inspector
    assert 'state.setSkillAttributes(' in inspector
    assert 'state.setSkillModifier(' in inspector
    assert 'state.setSkillNameOverride(' in inspector
    assert 'state.hideSkill(skill.id)' in inspector
    assert 'state.resetSkillDefinitionOverrides(skill.id)' in inspector
    assert 'state.deleteDynamicSkill(skill.id)' in inspector


def test_skills_browser_contract_is_wired_into_desktop_ci():
    import subprocess
    # The old export-only PROJECT_FILES.txt is not a repository source contract.
    project_files = subprocess.check_output(['git', 'ls-files', '--', 'tools/tests/test_desktop_skills_browser.py'], cwd=ROOT, text=True)
    linux_ci = read(ROOT / '.github/workflows/linux-appimage.yml')
    release_ci = read(ROOT / '.github/workflows/release.yml')
    token = 'tools/tests/test_desktop_skills_browser.py'
    assert token in project_files
    assert token in linux_ci
    assert token in release_ci
