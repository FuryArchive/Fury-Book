from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/linux-appimage.yml'
SHEET = ROOT / 'desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt'
PACKAGER = ROOT / 'packaging/linux/build-appimage.sh'


def read(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_release_workflow_builds_compose_desktop_appimage():
    text = read(WORKFLOW)
    assert 'packaging/linux/build-appimage.sh' in text
    assert 'build-portable-appimage.sh' not in text
    assert 'test_compose_desktop_parity.py' in text
    assert 'test_compose_release_ready.py' in text
    assert ':shared:desktopTest' in text
    assert ':desktopApp:compileKotlin' in text


def test_compose_packager_uses_real_desktop_distributable():
    text = read(PACKAGER)
    assert ':desktopApp:createDistributable' in text
    assert 'desktopApp/build/compose/binaries/main/app/FuryBook' in text
    assert 'appimagetool' in text


def test_character_sheet_portrait_is_not_embedded_swing_ui():
    text = read(SHEET)
    assert 'SwingPanel' not in text
    assert 'javax.swing' not in text
    assert 'loadImageBitmap' in text
    assert 'Image(' in text


def test_appimage_appdir_contains_desktop_icon_and_diricon():
    text = read(PACKAGER)
    assert 'fury-icon.png' in text
    assert 'fury-book.png' in text
    assert 'hicolor/512x512/apps/fury-book.png' in text
    assert 'ln -s fury-book.png "$APPDIR/.DirIcon"' in text
    assert '.DirIcon' in text
    assert 'Icon=fury-book' in text


def test_release_workflow_runs_all_compose_parity_guards():
    text = read(WORKFLOW)
    for test_name in (
        'test_build_version_matrix.py',
        'test_compose_destructive_and_validation_parity.py',
        'test_compose_development_purchase_parity.py',
        'test_compose_development_chi_parity.py',
        'test_compose_development_chi_backend_parity.py',
        'test_compose_magic_parity.py',
        'test_magic_backend_parity.py',
        'test_equipment_backend_parity.py',
        'test_ruleset_parity_contract.py',
        'test_rules_boundary_contract.py',
        'test_rulebook_core_skills_rolls_contract.py',
        'test_desktop_persistence.py',
        'test_compose_state_observation.py',
        'test_compose_android_062_sheet_roll_parity.py',
        'test_desktop_ui_structural_redesign.py',
        'test_shared_application_lock.py',
        'test_shared_application_golden.py',
    ):
        assert test_name in text
    assert ':shared:desktopTest' in text


def test_linux_compose_ci_uses_project_jvm_toolchain_version():
    text = read(WORKFLOW)
    assert "java-version: '17'" in text


def test_release_workflow_smoke_tests_built_appimage_in_clean_home():
    text = read(WORKFLOW)
    assert 'xvfb-run' in text
    assert 'APPIMAGE_EXTRACT_AND_RUN=1' in text
    assert 'mktemp -d' in text
    assert 'timeout 8s' in text


def test_compose_sources_do_not_import_internal_layout_weight_symbol():
    kotlin_roots = [ROOT / 'desktopApp/src/main/kotlin']
    offenders = []
    for kotlin_root in kotlin_roots:
        for path in kotlin_root.rglob('*.kt'):
            if 'import androidx.compose.foundation.layout.weight' in read(path):
                offenders.append(str(path.relative_to(ROOT)))
    assert not offenders, 'invalid explicit weight imports: ' + ', '.join(offenders)
