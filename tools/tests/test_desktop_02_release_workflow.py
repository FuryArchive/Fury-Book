from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/linux-appimage.yml'
PACKAGER = ROOT / 'packaging/linux/build-appimage.sh'


def test_linux_release_builds_verified_compose_parity_runtime():
    text = WORKFLOW.read_text(encoding='utf-8')
    assert 'packaging/linux/build-appimage.sh' in text
    assert 'build-portable-appimage.sh' not in text
    assert 'tools/tests/test_desktop_02_parity.py' in text
    assert 'tools/tests/test_compose_desktop_parity.py' in text
    assert 'tools/tests/test_compose_release_ready.py' in text


def test_compose_appimage_packager_builds_desktopapp_distributable():
    text = PACKAGER.read_text(encoding='utf-8')
    assert ':desktopApp:createDistributable' in text
    assert 'desktopApp/build/compose/binaries/main/app/FuryBook' in text
    assert 'appimagetool' in text
