from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_android_branding_uses_fury_book_without_changing_stable_application_id():
    gradle = text("app/build.gradle.kts")
    manifest = text("app/src/main/AndroidManifest.xml")

    assert 'applicationId = "com.dubl.character.android"' in gradle
    assert 'resValue("string", "app_name", "Fury Book")' in gradle
    assert 'Fury Book Dev' not in gradle
    assert 'versionNameSuffix = "-dev"' not in gradle
    assert gradle.count('resValue("string", "app_name", "Fury Book")') == 3
    assert 'android:icon="@mipmap/ic_launcher"' in manifest
    assert 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest
    assert (ROOT / "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").is_file()
    assert (ROOT / "app/src/main/res/mipmap-anydpi/ic_launcher.xml").is_file()
    assert (ROOT / "app/src/main/res/mipmap-anydpi/ic_launcher_round.xml").is_file()
    assert (ROOT / "app/src/main/res/drawable/ic_fury_foreground.xml").is_file()


def test_desktop_window_and_distribution_are_fury_book_branded_with_custom_icon():
    main = text("desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt")
    gradle = text("desktopApp/build.gradle.kts")
    icon = ROOT / "desktopApp/src/main/resources/fury-icon.svg"

    assert 'title = "Fury Book — DUBL 3.69"' in main
    assert 'Text(\n            "Fury Book"' in main
    assert 'painterResource("fury-icon.svg")' in main
    assert 'packageName = "FuryBook"' in gradle
    assert 'FURY_BOOK_VERSION' in gradle
    assert 'DUBL_VERSION' in gradle
    assert 'windows {' in gradle and 'fury-icon.ico' in gradle
    assert 'linux {' in gradle and 'fury-icon.png' in gradle
    assert icon.is_file()
    assert (ROOT / "desktopApp/src/main/resources/fury-icon.ico").is_file()
    assert (ROOT / "desktopApp/src/main/resources/fury-icon.png").is_file()
    assert '<svg' in icon.read_text(encoding="utf-8")


def test_linux_appimage_uses_fury_book_name_and_icon_but_preserves_data_identity():
    build = text("packaging/linux/build-appimage.sh")
    workflow = text(".github/workflows/linux-appimage.yml")
    store = text("shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopCharacterStore.kt")

    assert 'Fury-Book-${VERSION}-linux-${ARCH}.AppImage' in build
    assert 'Name=Fury Book' in build
    assert 'Comment=Fury Book tabletop RPG character and rules platform' in build
    assert 'Exec=fury-book' in build
    assert 'Icon=fury-book' in build
    assert 'dist/Fury-Book-${FURY_BOOK_DESKTOP_VERSION}-linux-x86_64.AppImage' in workflow
    assert 'name: Fury-Book-Linux-Desktop' in workflow
    assert 'resolve("dubl-character")' in store


def test_release_artifacts_use_fury_book_product_name_while_ruleset_identity_stays_dubl():
    android_ci = text(".github/workflows/android-ci.yml")
    release = text(".github/workflows/release.yml")
    ruleset = text("shared/src/commonMain/kotlin/com/furybook/dubl/model/RulesetModels.kt")

    assert 'Fury-Book-Android.apk' in android_ci
    assert 'Fury-Book-Android-dev.apk' not in android_ci
    assert 'Fury-Book-${{ needs.validate.outputs.version }}-Android.apk' in release
    assert '--title "Fury Book $VERSION"' in release
    assert 'const val ID = "dubl"' in ruleset
    assert 'const val VERSION = "3.69"' in ruleset
