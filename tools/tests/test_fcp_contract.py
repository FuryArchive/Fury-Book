import hashlib
import json
from pathlib import Path
import tempfile
import zipfile

import pytest

from tools.fcp.build_fcp import build_fcp, declared_paths, load_manifest

ROOT = Path(__file__).resolve().parents[2]
CORE_PACK = ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69"
CHI_PACK = ROOT / "shared/src/commonMain/resources/fcp/dubl-chi-3.69"
LEGACY_ROOT = ROOT / "shared/src/commonMain/resources"


def test_core_and_optional_chi_are_separate_fury_content_packs():
    core = load_manifest(CORE_PACK)
    chi = load_manifest(CHI_PACK)

    assert core["id"] == "dubl-3.69"
    assert chi["id"] == "dubl-chi-3.69"
    assert chi["dependencies"] == [{"id": "dubl-3.69", "version": "3.69"}]
    assert "dubl.chi" not in {entry["kind"] for entry in core["entries"]}
    assert "dubl.chi" in {entry["kind"] for entry in chi["entries"]}
    assert not (CORE_PACK / "content/chi_catalog.json").exists()
    assert not (CORE_PACK / "content/development_chi_catalog.json").exists()
    assert (CHI_PACK / "content/chi_catalog.json").is_file()
    assert (CHI_PACK / "content/development_chi_catalog.json").is_file()

    ui = {item["surface"]: item for item in chi["ui"]}
    assert ui["character.resources"]["binding"] == "dubl.chi"
    assert ui["character.resource-settings"]["component"] == "resource-toggle"
    assert ui["development.tabs"]["component"] == "development-browser"
    assert ui["character.economy"]["component"] == "xp-line"

    for pack, manifest in ((CORE_PACK, core), (CHI_PACK, chi)):
        for path in declared_paths(pack, manifest):
            assert (pack / path).is_file()


def test_catalogs_no_longer_live_as_loose_shared_resources():
    for name in (
        "skills_catalog.json", "conditions_catalog.json", "development_catalog.json",
        "acquisition_catalog.json", "legacy-v11-catalog.json",
        "development_regular_catalog.json", "development_special_catalog.json",
        "development_ability_roots_catalog.json", "development_martial_catalog.json",
        "development_chi_catalog.json", "development_magic_catalog.json",
        "chi_catalog.json", "magic_equipment_catalog.json", "skill_effects_catalog.json",
    ):
        assert not (LEGACY_ROOT / name).exists()


def test_fcp_builder_is_deterministic_for_core_and_chi():
    for pack in (CORE_PACK, CHI_PACK):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            first = tmp / "first.fcp"
            second = tmp / "second.fcp"
            first_hash = build_fcp(pack, first)
            second_hash = build_fcp(pack, second)
            assert first.read_bytes() == second.read_bytes()
            assert first_hash == second_hash == hashlib.sha256(first.read_bytes()).hexdigest()
            manifest = json.loads((pack / "manifest.json").read_text(encoding="utf-8"))
            expected = {"manifest.json", "checksums.sha256"} | {entry["path"] for entry in manifest["entries"]}
            with zipfile.ZipFile(first) as archive:
                assert set(archive.namelist()) == expected


def test_generic_fcp_runtime_does_not_depend_on_dubl():
    content_root = ROOT / "shared/src/commonMain/kotlin/com/furybook/content"
    for path in content_root.rglob("*.kt"):
        assert "com.furybook.dubl" not in path.read_text(encoding="utf-8")


def test_fury_book_mounts_chi_from_real_pack_activation_and_ui_contributions():
    android_app = (ROOT / "app/src/main/java/com/furybook/android/ui/DublApp.kt").read_text(encoding="utf-8")
    android_state = (ROOT / "app/src/main/java/com/furybook/android/data/AndroidContentPackState.kt").read_text(encoding="utf-8")
    android_overview = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt").read_text(encoding="utf-8")
    android_dev = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt").read_text(encoding="utf-8")
    desktop_state = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt").read_text(encoding="utf-8")
    desktop_sheet = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt").read_text(encoding="utf-8")
    desktop_dev = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt").read_text(encoding="utf-8")

    assert "AndroidContentPackState.isChiEnabled" in android_app
    assert "AndroidContentPackState.setPackEnabled" in android_app
    assert "DublUiRenderModels.resourceMeters(content.composition, character)" in android_overview
    assert "DublUiRenderModels.resourceToggles(content.composition, character)" in android_overview
    assert "FcpUiSurface.DEVELOPMENT_TABS" in android_dev
    assert "FcpUiComponent.DEVELOPMENT_BROWSER" in android_dev
    assert "Preferences.userRoot().node" in desktop_state
    assert "setContentPackActive" in desktop_state
    assert "resourceMeterModels" in desktop_sheet
    assert "resourceToggleModels" in desktop_sheet
    assert "FcpUiSurface.DEVELOPMENT_TABS" in desktop_dev
    assert "FcpUiComponent.DEVELOPMENT_BROWSER" in desktop_dev


def _write_manifest(directory: Path, payload: dict) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "manifest.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def test_builder_validates_composition_metadata_before_packaging():
    source = json.loads((CHI_PACK / "manifest.json").read_text(encoding="utf-8"))

    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)

        duplicate_claims = json.loads(json.dumps(source))
        duplicate_claims["claims"] = duplicate_claims["claims"] * 2
        _write_manifest(tmp / "claims", duplicate_claims)
        with pytest.raises(ValueError, match="duplicate FCP content claim"):
            load_manifest(tmp / "claims")

        duplicate_ui = json.loads(json.dumps(source))
        duplicate_ui["ui"] = duplicate_ui["ui"] + [duplicate_ui["ui"][0]]
        _write_manifest(tmp / "ui", duplicate_ui)
        with pytest.raises(ValueError, match="duplicate FCP UI contribution id"):
            load_manifest(tmp / "ui")

        self_dependency = json.loads(json.dumps(source))
        self_dependency["dependencies"] = [{"id": self_dependency["id"], "version": self_dependency["version"]}]
        _write_manifest(tmp / "dependency", self_dependency)
        with pytest.raises(ValueError, match="cannot depend on itself"):
            load_manifest(tmp / "dependency")

        unknown_renderer = json.loads(json.dumps(source))
        unknown_renderer["ui"][0]["surface"] = "future.surface"
        unknown_renderer["ui"][0]["component"] = "future-widget"
        _write_manifest(tmp / "unknown-renderer", unknown_renderer)
        with pytest.raises(ValueError, match="unsupported FCP UI surface/component"):
            load_manifest(tmp / "unknown-renderer")

        unknown_property = json.loads(json.dumps(source))
        unknown_property["ui"][1]["properties"]["accent"] = "fury.accent"
        _write_manifest(tmp / "unknown-property", unknown_property)
        with pytest.raises(ValueError, match="unsupported FCP UI properties"):
            load_manifest(tmp / "unknown-property")

        unknown_token = json.loads(json.dumps(source))
        unknown_token["ui"][0]["properties"]["icon"] = "future-icon"
        _write_manifest(tmp / "unknown-token", unknown_token)
        with pytest.raises(ValueError, match="unsupported FCP UI icon token"):
            load_manifest(tmp / "unknown-token")


def test_pack_manager_ui_is_driven_by_generic_composition():
    android = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/CharactersScreen.kt").read_text(encoding="utf-8")
    desktop = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharactersScreen.kt").read_text(encoding="utf-8")
    composition = (ROOT / "shared/src/commonMain/kotlin/com/furybook/content/FcpComposition.kt").read_text(encoding="utf-8")

    assert "displayedComposition.available.forEach" in android
    assert "composition.available.forEach" in desktop
    assert "requiredPackIds" in android and "requiredPackIds" in desktop
    assert "inactiveClaims" in composition
    assert "content claim conflict" in composition
    assert "UI contribution conflict" in composition


def test_external_dubl_development_packs_have_real_activation_path():
    addon = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublDevelopmentAddon.kt").read_text(encoding="utf-8")
    android_state = (ROOT / "app/src/main/java/com/furybook/android/data/AndroidContentPackState.kt").read_text(encoding="utf-8")
    android_repo = (ROOT / "app/src/main/java/com/furybook/android/data/DevelopmentCatalogRepository.kt").read_text(encoding="utf-8")
    desktop_state = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt").read_text(encoding="utf-8")

    assert 'const val ENTRY_KIND = "dubl.development"' in addon
    assert "content claims are not supported" in addon
    assert "external UI contributions are not supported" in addon
    assert "cannot add Chi development" in addon
    assert "DublDevelopmentAddon::isSupported" in android_state
    assert "validateExternalActivation" in android_state
    assert "AndroidFcpInstaller.openInstalled" in android_repo
    assert "DublDevelopmentAddon.load" in android_repo
    assert "DublDevelopmentAddon::isSupported" in desktop_state
    assert "DesktopFcpInstaller.openInstalled" in desktop_state
    assert "composeCanonicalDevelopmentCatalog" in desktop_state


def test_chi_ui_mounts_use_generic_fcp_surface_contract():
    ui_contract = (ROOT / "shared/src/commonMain/kotlin/com/furybook/content/FcpUiContract.kt").read_text(encoding="utf-8")
    chi_loader = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublChiFcpCatalogLoader.kt").read_text(encoding="utf-8")
    bindings = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublUiBindings.kt").read_text(encoding="utf-8")
    screens = [
        ROOT / "app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt",
        ROOT / "app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt",
        ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt",
        ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt",
    ]

    assert "object FcpUiSurface" in ui_contract
    assert "object FcpUiComponent" in ui_contract
    assert "fun FcpComposition.ui(" in ui_contract
    assert 'const val CHI = "dubl.chi"' in bindings
    assert "object DublUiRegistry" in bindings
    assert "enum class DublUiFeature" in bindings
    assert "DublChiUi" not in chi_loader
    for path in screens:
        text = path.read_text(encoding="utf-8")
        assert "DublChiUi" not in text
        assert "DublUiBinding" not in text
        assert "DublUiFeature.CHI" in text
        if "app/src/main" in path.as_posix():
            assert "DublUiRegistry.mounts(content.composition," in text
        else:
            assert "uiMounts(" in text
        assert ".forFeature(" in text


def test_resource_meter_uses_manifest_presentation_tokens_on_both_platforms():
    android = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt").read_text(encoding="utf-8")
    desktop = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt").read_text(encoding="utf-8")
    contract = (ROOT / "shared/src/commonMain/kotlin/com/furybook/content/FcpUiContract.kt").read_text(encoding="utf-8")

    models = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublUiRenderModels.kt").read_text(encoding="utf-8")

    assert "FcpUiProperty" in contract
    assert "FcpUiIconToken" in contract
    assert "FcpUiAccentToken" in contract
    assert ".presentation()" in models
    assert "model.presentation" in android
    assert "fcpIconGlyph" in android
    assert "fcpAccentColor" in android
    assert "model.presentation" in desktop
    assert "fcpDesktopIcon" in desktop
    assert "fcpDesktopAccent" in desktop


def test_fcp_ui_order_controls_host_tab_and_resource_positions():
    android_overview = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt").read_text(encoding="utf-8")
    android_dev = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt").read_text(encoding="utf-8")
    desktop_sheet = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt").read_text(encoding="utf-8")
    desktop_dev = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt").read_text(encoding="utf-8")
    contract = (ROOT / "shared/src/commonMain/kotlin/com/furybook/content/FcpUiContract.kt").read_text(encoding="utf-8")

    assert "object FcpUiHostOrder" in contract
    assert "fun <T> orderedUiItems" in contract
    assert "model.presentation.order" in android_overview
    assert "orderedUiItems(" in android_overview
    assert "chiTabUi.order" in android_dev
    assert "items(developmentTabs)" in android_dev
    assert "FcpOrderedUiItem(presentation.order" in desktop_sheet
    assert "orderedUiItems(tileItems)" in desktop_sheet
    assert "chiTabUi.order" in desktop_dev
    assert "developmentTabs.forEach" in desktop_dev


def test_resource_toggle_and_development_browser_use_fcp_presentation():
    android_overview = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt").read_text(encoding="utf-8")
    android_dev = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt").read_text(encoding="utf-8")
    desktop_sheet = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt").read_text(encoding="utf-8")
    desktop_dev = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt").read_text(encoding="utf-8")

    assert "mountedResourceToggles" in android_overview
    assert "mountedToggles.forEach" in android_overview
    assert "val presentation = model.presentation" in android_overview
    assert "fcpIconGlyph(presentation.icon)" in android_overview

    assert "chiTabPresentation = chiTabUi?.presentation()" in android_dev
    assert "chiTabPresentation?.label" in android_dev
    assert "fcpDevelopmentIcon(chiTabPresentation?.icon)" in android_dev
    assert 'properties?.get("icon")' not in android_dev

    assert "mountedToggles = state.resourceToggleModels()" in desktop_sheet
    assert "mountedToggles.forEach" in desktop_sheet
    assert "val presentation = model.presentation" in desktop_sheet
    assert "fcpDesktopIcon(token)" in desktop_sheet

    assert "chiTabPresentation = chiTabUi?.presentation()" in desktop_dev
    assert "chiTabPresentation?.label" in desktop_dev
    assert "fcpDevelopmentIcon(chiTabPresentation?.icon)" in desktop_dev


def test_raw_fcp_binding_is_confined_to_shared_dubl_registry():
    binding_registry = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublUiBindings.kt").read_text(encoding="utf-8")
    platform_files = [
        ROOT / "app/src/main/java/com/furybook/android/data/AndroidContentPackState.kt",
        ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt",
        ROOT / "app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt",
        ROOT / "app/src/main/java/com/furybook/android/ui/screens/FeatsScreen.kt",
        ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt",
        ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/DevelopmentScreen.kt",
    ]

    assert '"dubl.chi"' in binding_registry
    assert "DublUiRegistry.mounts" not in binding_registry
    assert "DublUiBinding.CHI" in binding_registry
    for path in platform_files:
        text = path.read_text(encoding="utf-8")
        assert '"dubl.chi"' not in text
        assert "DublUiBinding" not in text


def test_runtime_and_builder_declare_same_current_ui_capability_boundary():
    runtime = (ROOT / "shared/src/commonMain/kotlin/com/furybook/content/FcpUiContract.kt").read_text(encoding="utf-8")
    builder = (ROOT / "tools/fcp/build_fcp.py").read_text(encoding="utf-8")
    archive = (ROOT / "shared/src/commonMain/kotlin/com/furybook/content/FcpArchive.kt").read_text(encoding="utf-8")
    chi_loader = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublChiFcpCatalogLoader.kt").read_text(encoding="utf-8")

    for token in (
        "character.resources", "resource-meter",
        "character.resource-settings", "resource-toggle",
        "development.tabs", "development-browser",
        "character.economy", "xp-line",
        "fury.accent", "chi",
    ):
        assert token in runtime
        assert token in builder

    assert "FcpUiHostCapabilities.requireSupported(manifest)" in archive
    assert "FcpUiHostCapabilities.requireSupported(pack.manifest)" in chi_loader


def test_resource_surfaces_render_from_typed_dubl_models_without_chi_special_cases():
    models = (ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublUiRenderModels.kt").read_text(encoding="utf-8")
    android_state = (ROOT / "app/src/main/java/com/furybook/android/data/AndroidContentPackState.kt").read_text(encoding="utf-8")
    desktop_state = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/DesktopAppState.kt").read_text(encoding="utf-8")
    android = (ROOT / "app/src/main/java/com/furybook/android/ui/screens/OverviewScreen.kt").read_text(encoding="utf-8")
    desktop = (ROOT / "desktopApp/src/main/kotlin/com/furybook/desktop/screens/CharacterSheetScreen.kt").read_text(encoding="utf-8")

    assert "data class DublResourceMeterModel" in models
    assert "data class DublResourceToggleModel" in models
    assert "DublUiRenderModels.resourceMeters" in android_state
    assert "DublUiRenderModels.resourceToggles" in android_state
    assert "DublUiRenderModels.resourceMeters" in desktop_state
    assert "DublUiRenderModels.resourceToggles" in desktop_state

    assert "DublUiRenderModels.resourceMeters(content.composition, character)" in android
    assert "DublUiRenderModels.resourceToggles(content.composition, character)" in android
    assert "CharacterSheetResourceId.CHI" not in android
    assert "chiResourcePresentation" not in android
    assert "chiResourceSettingsPresentation" not in android

    assert "resourceMeterModels" in desktop
    assert "resourceToggleModels" in desktop
    assert "CharacterSheetResourceId.CHI" not in desktop
    assert "chiResourcePresentation" not in desktop
    assert "chiTogglePresentation" not in desktop


def test_frozen_legacy_migration_snapshot_is_declared_and_keeps_provenance():
    runtime = CORE_PACK / "content/legacy-v11-catalog.json"
    development = ROOT / "rulesets/dubl-3.69/acquisition/legacy-v11-catalog.json"
    assert runtime.read_bytes() == development.read_bytes()
    payload = json.loads(runtime.read_text(encoding="utf-8"))
    assert payload["baseline"] == "f47a5256a66ddc5e1b7b5c1e204e57279fc7227f"
    assert len(payload["developments"]) == 796
    eagle = [row for row in payload["developments"] if row["id"] == "feat_9012c539238a1e6f"]
    assert len(eagle) == 1 and eagle[0]["cost"] == 0
    assert payload["inactiveChiClaims"] == ["martial_technique_08d81ce45b880c24"]
    assert payload["skillRankCosts"] == [0, 10, 30, 60, 100, 150, 210, 280, 360, 450, 550]
    assert all(not source["path"].startswith("/") and len(source["sha256"]) == 64 for source in payload["sources"])
    manifest = load_manifest(CORE_PACK)
    assert [entry["path"] for entry in manifest["entries"] if entry["kind"] == "dubl.legacy-acquisition-v11"] == ["content/legacy-v11-catalog.json"]
