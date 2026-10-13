import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/content"
CHI = ROOT / "shared/src/commonMain/resources/fcp/dubl-chi-3.69/content"
CORE_MANIFEST = ROOT / "shared/src/commonMain/resources/fcp/dubl-3.69/manifest.json"
CHI_MANIFEST = ROOT / "shared/src/commonMain/resources/fcp/dubl-chi-3.69/manifest.json"
CONFIG = ROOT / "rulesets/dubl-3.69/config.json"
CATALOG_DATA = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/data/CatalogData.kt"
DUBL_FCP_LOADER = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublFcpCatalogLoader.kt"
DUBL_CHI_LOADER = ROOT / "shared/src/commonMain/kotlin/com/furybook/dubl/content/DublChiFcpCatalogLoader.kt"
ANDROID_REPO = ROOT / "app/src/main/java/com/furybook/android/data/DevelopmentCatalogRepository.kt"
DESKTOP_LOADER = ROOT / "shared/src/desktopMain/kotlin/com/furybook/desktop/data/DesktopCatalogLoader.kt"

LAYERS = (
    ("development_regular", "development_regular_catalog.json", 287, "core", CORE),
    ("development_special", "development_special_catalog.json", 304, "core", CORE),
    ("development_ability_roots", "development_ability_roots_catalog.json", 41, "core", CORE),
    ("development_martial", "development_martial_catalog.json", 122, "melee", CORE),
    ("development_chi", "development_chi_catalog.json", 27, "melee", CHI),
    ("development_magic", "development_magic_catalog.json", 15, "core", CORE),
)


def _catalog(name: str, root: Path = CORE):
    return json.loads((root / name).read_text(encoding="utf-8"))


def test_all_development_domains_are_source_generated_and_owned_by_declared_fcp():
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    total = 0
    for domain, filename, expected_count, source, root in LAYERS:
        meta = config["domains"][domain]
        assert meta["status"] == "source_generated"
        assert meta["sources"] == [source]
        pack = "dubl-chi-3.69" if root == CHI else "dubl-3.69"
        assert meta["runtimeArtifact"] == f"shared/src/commonMain/resources/fcp/{pack}/content/{filename}"
        catalog = _catalog(filename, root)
        assert len(catalog["entries"]) == expected_count
        total += expected_count
    assert total == 796
    assert _catalog("development_catalog.json")["entries"] == []


def test_promoted_regular_development_keeps_rulebook_mechanics_and_explicit_unresolved_fallback():
    regular = _catalog("development_regular_catalog.json")
    by_name = {entry["name"]: entry for entry in regular["entries"]}

    blocking = by_name["Блокирование"]
    assert blocking["requirements"] == "Ловкость 3 или Скорость 3; Холодное оружие 3 или Рукопашный бой 3"

    last_stand = by_name["Сражаться до последнего"]
    assert last_stand["ranks"] == 1
    assert last_stand["incomplete"] is True
    assert "не указан ранг" in last_stand["mechanicsConflict"]
    assert "совместимости" in last_stand["conflictNote"]


def test_promoted_special_development_keeps_clean_source_text_and_real_conflicts_explicit():
    special = _catalog("development_special_catalog.json")
    by_id = {entry["id"]: entry for entry in special["entries"]}

    assert by_id["feat_a35cd71644363fca"]["benefit"] == "Вы получаете +1 за ранг к бегу если двигаетесь в сторону видимого противника."
    assert by_id["feat_e04b4d619a503480"]["benefit"].endswith("эффект энтропийного резонанса срабатывает дважды.")
    assert by_id["feat_29ff1cb23df7879d"]["benefit"] == "Вы способны превращаться в животных."
    assert by_id["feat_ab0ad31f46407f46"]["benefit"] == "Время превращения в зверя неограниченно."
    assert by_id["feat_1d9d4f8e6d850cae"]["benefit"].endswith("цель становится изумленной на 1 раунд.")
    assert by_id["feat_7bc39bc9c44dd41e"]["benefit"].endswith("4. Воля")

    killing = by_id["feat_52a4d6cee2756b48"]
    assert "не крит а сразу полуторный урон" in killing["benefit"]
    assert killing["incomplete"] is True
    assert "противореч" in killing["mechanicsConflict"].casefold()
    assert "локаль" in killing["conflictNote"].casefold()


def test_promoted_ability_roots_keep_source_semantics_and_draft_markers():
    roots = _catalog("development_ability_roots_catalog.json")
    assert all(entry["section"] == "Особые способности" for entry in roots["entries"])

    battle_rush = next(entry for entry in roots["entries"] if entry["name"] == "Боевой азарт")
    assert battle_rush["requirements"] == ""
    assert battle_rush["benefit"] == ""

    connections = next(entry for entry in roots["entries"] if entry["name"] == "Связи")
    assert connections["abilityOptions"] == [{"source": "???", "value": 1}]
    assert connections["repeatable"] is True
    assert connections["incomplete"] is True
    assert "локаль" in connections["conflictNote"].casefold()


def test_martial_chi_and_magic_development_have_expected_sections_and_counts():
    martial = _catalog("development_martial_catalog.json")
    chi_dev = _catalog("development_chi_catalog.json", CHI)
    magic = _catalog("development_magic_catalog.json")
    assert len(martial["entries"]) == 122
    assert all(entry["section"] == "Боевые искусства" for entry in martial["entries"])
    assert len(chi_dev["entries"]) == 27
    assert all(entry["section"] == "ЦИ" for entry in chi_dev["entries"])
    assert len(magic["entries"]) == 15
    assert all(entry["section"] == "Магические навыки" for entry in magic["entries"])


def test_chi_catalog_itself_is_promoted_into_optional_fcp_from_melee_rulebook():
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    meta = config["domains"]["chi"]
    assert meta["status"] == "source_generated"
    assert meta["sources"] == ["melee"]
    assert meta["runtimeArtifact"] == "shared/src/commonMain/resources/fcp/dubl-chi-3.69/content/chi_catalog.json"
    chi = _catalog("chi_catalog.json", CHI)
    assert len(chi["schools"]) == 9
    assert len(chi["techniques"]) == 68


def test_android_and_desktop_compose_core_and_optional_development_layers():
    parser = CATALOG_DATA.read_text(encoding="utf-8")
    core_loader = DUBL_FCP_LOADER.read_text(encoding="utf-8")
    chi_loader = DUBL_CHI_LOADER.read_text(encoding="utf-8")
    android = ANDROID_REPO.read_text(encoding="utf-8")
    desktop = DESKTOP_LOADER.read_text(encoding="utf-8")
    core_manifest = json.loads(CORE_MANIFEST.read_text(encoding="utf-8"))
    chi_manifest = json.loads(CHI_MANIFEST.read_text(encoding="utf-8"))

    assert "fun mergeDevelopmentCatalogs" in parser

    core_development = [
        entry["path"].removeprefix("content/")
        for entry in sorted(core_manifest["entries"], key=lambda item: (item["order"], item["path"]))
        if entry["kind"] == "dubl.development"
    ]
    chi_development = [
        entry["path"].removeprefix("content/")
        for entry in sorted(chi_manifest["entries"], key=lambda item: (item["order"], item["path"]))
        if entry["kind"] == "dubl.development"
    ]
    assert core_development == [
        "development_regular_catalog.json",
        "development_special_catalog.json",
        "development_ability_roots_catalog.json",
        "development_martial_catalog.json",
        "development_magic_catalog.json",
        "development_catalog.json",
    ]
    assert chi_development == ["development_chi_catalog.json"]

    assert 'pack.readAll("dubl.development")' in core_loader
    assert 'pack.readAll("dubl.development")' in chi_loader
    assert "AndroidDublFcp.loader(appContext)" in android
    assert ".loadDevelopment()" in android
    assert "AndroidDublFcp.chiLoader(appContext).loadDevelopment()" in android
    assert "AndroidDublFcp.chiLoader(appContext).claimedDevelopmentIds()" in android
    assert "DublDevelopmentAddon.load" in android
    assert "AndroidFcpInstaller.openInstalled" in android
    assert "DublFcp.open(" in desktop
    assert "DublChiFcp.open(" in desktop
    assert "mergeDevelopmentCatalogs(core, chiFcp.loadDevelopment())" in desktop
