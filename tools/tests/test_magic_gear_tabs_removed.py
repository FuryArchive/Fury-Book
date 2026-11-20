from pathlib import Path

# Stage 5: the Магия and Снаряжение tabs are gone. Casting and carried gear live on the sheet; managing magic
# and gear lives in the editor's Магия and Снаряжение steps. Phones have no bottom bar.
ROOT = Path(__file__).resolve().parents[2]
DESKTOP_MAIN = ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt"
WEB_MAIN = ROOT / "webApp/src/jsMain/kotlin/com/furybook/web/Main.kt"
ANDROID_APP = ROOT / "app/src/main/java/com/furybook/android/ui/DublApp.kt"
SHELL = ROOT / "shared/src/commonMain/kotlin/com/furybook/ui/components/FuryShell.kt"
EDITOR = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor"
SHEET = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet"
OLD_FILES = [
    ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/MagicScreen.kt",
    ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/EquipmentScreen.kt",
    ROOT / "webApp/src/jsMain/kotlin/com/furybook/desktop/screens/MagicScreen.kt",
    ROOT / "webApp/src/jsMain/kotlin/com/furybook/desktop/screens/EquipmentScreen.kt",
    ROOT / "app/src/main/java/com/furybook/android/ui/screens/MagicScreen.kt",
    ROOT / "app/src/main/java/com/furybook/android/ui/screens/EquipmentScreen.kt",
]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_no_magic_or_gear_section_on_any_platform():
    for path in (DESKTOP_MAIN, WEB_MAIN):
        text = read(path)
        assert 'MAGIC("Магия")' not in text, path.name
        assert 'EQUIPMENT("Снаряжение")' not in text, path.name
        assert "MagicScreen" not in text and "EquipmentScreen" not in text, path.name
    android = read(ANDROID_APP)
    assert "AppSection.MAGIC" not in android and "AppSection.INVENTORY" not in android
    assert "MagicScreen" not in android and "EquipmentScreen" not in android


def test_old_magic_and_gear_screens_are_gone():
    for path in OLD_FILES:
        assert not path.exists(), path


def test_phones_get_no_bottom_bar():
    shell = read(SHELL)
    assert "furyBottomTabs(sections).isNotEmpty()" in shell
    for path in (DESKTOP_MAIN, WEB_MAIN, ANDROID_APP):
        assert "primary = false" in read(path), path.name


def test_moved_functions_live_in_shared_code():
    magic = read(EDITOR / "EditorMagicStep.kt") + read(EDITOR / "EditorMagicActions.kt") + read(EDITOR / "EditorMagicDialogs.kt")
    for call in ("setMagicManaRank", "setMagicSchoolPower", "addMagicSchool", "updateMagicSchool", "removeMagicSchool",
                 "addCatalogSpell", "addCustomSpell", "updateSpell", "setSpellLearned", "removeSpell"):
        assert call in magic, call
    gear = read(EDITOR / "EditorGearStep.kt") + read(EDITOR / "EditorMagicActions.kt")
    for call in ("setGearLoadAutomatic", "setGearManualLoad", "addCatalogGear", "addCustomGear", "updateGearItem",
                 "setGearItemCarried", "removeGearItem"):
        assert call in gear, call
    assert "syncCatalogGearLoads" in read(SHEET / "SheetScreen.kt")
    spells = read(SHEET / "SheetSpells.kt")
    assert "CharacterSheetResourceId.MANA" in spells
    assert "setGearItemQuantity" in read(SHEET / "SheetGear.kt")
