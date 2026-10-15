import json
from pathlib import Path
from tools.tests.native_runtime_catalog_expectations import assert_native_runtime_ids

ROOT = Path(__file__).resolve().parents[2]
CORE_PACK = ROOT / 'shared/src/commonMain/resources/fcp/dubl-3.69'
CHI_PACK = ROOT / 'shared/src/commonMain/resources/fcp/dubl-chi-3.69'
CORE = CORE_PACK / 'content'
CHI = CHI_PACK / 'content'
ANDROID_ASSETS = ROOT / 'app/src/main/assets'
APP_BUILD = ROOT / 'app/build.gradle.kts'
ANDROID_DATA = ROOT / 'app/src/main/java/com/furybook/android/data'

CATALOGS = {
    'development_regular_catalog.json': (CORE, ('entries', 287)),
    'development_special_catalog.json': (CORE, ('entries', 304)),
    'development_ability_roots_catalog.json': (CORE, ('entries', 41)),
    'development_martial_catalog.json': (CORE, ('entries', 501)),
    'development_native_supplement_catalog.json': (CORE, ('entries', 64)),
    'development_native_magic_catalog.json': (CORE, ('entries', 22)),
    'development_chi_catalog.json': (CHI, ('entries', 27)),
    'development_magic_catalog.json': (CORE, ('entries', 15)),
    'development_catalog.json': (CORE, ('entries', 0)),
    'chi_catalog.json': (CHI, (('schools', 9), ('techniques', 68))),
    'magic_equipment_catalog.json': (CORE, (('spells', 298), ('gear', 260))),
    'skill_effects_catalog.json': (CORE, ('effects', 283)),
}


def test_fcp_content_is_single_physical_canonical_copy_with_pack_ownership():
    manifests = {
        CORE: json.loads((CORE_PACK / 'manifest.json').read_text(encoding='utf-8')),
        CHI: json.loads((CHI_PACK / 'manifest.json').read_text(encoding='utf-8')),
    }
    declared = {root: {Path(entry['path']).name for entry in manifest['entries']} for root, manifest in manifests.items()}

    for name, (root, _) in CATALOGS.items():
        assert name in declared[root]
        assert (root / name).exists()
        assert not (ANDROID_ASSETS / name).exists()

    assert 'assets.srcDir(rootProject.file("shared/src/commonMain/resources"))' in APP_BUILD.read_text(encoding='utf-8')


def test_android_repositories_compose_core_and_optional_chi_fcp():
    provider = (ANDROID_DATA / 'AndroidDublFcp.kt').read_text(encoding='utf-8')
    assert 'DublFcp.open(' in provider
    assert 'DublChiFcp.open(' in provider

    development = (ANDROID_DATA / 'DevelopmentCatalogRepository.kt').read_text(encoding='utf-8')
    assert 'load(includeChi: Boolean = false)' in development
    assert 'AndroidDublFcp.chiLoader(appContext).loadDevelopment()' in development

    chi = (ANDROID_DATA / 'ChiCatalogRepository.kt').read_text(encoding='utf-8')
    assert 'AndroidDublFcp.chiLoader(appContext).loadChi()' in chi


def test_canonical_catalog_counts_and_ids_are_locked():
    roots = {name: json.loads((root / name).read_text(encoding='utf-8')) for name, (root, _) in CATALOGS.items()}
    for name, (_, expectation) in CATALOGS.items():
        root = roots[name]
        pairs = (expectation,) if isinstance(expectation[0], str) else expectation
        for key, count in pairs:
            assert len(root[key]) == count, (name, key, len(root[key]))
            ids = [item['id'] for item in root[key]]
            assert len(ids) == len(set(ids))
            if key == 'entries':
                assert_native_runtime_ids(name, root[key])


def test_importers_write_to_owning_fcp_by_default():
    for path in (ROOT / 'tools/import_android_0_4_content.py', ROOT / 'tools/import_android_0_5_skill_effects.py'):
        source = path.read_text(encoding='utf-8')
        assert 'shared/src/commonMain/resources/fcp/dubl-3.69/content/' in source
        assert 'app/src/main/assets/' not in source

    source = (ROOT / 'tools/import_android_0_5_chi.py').read_text(encoding='utf-8')
    assert 'shared/src/commonMain/resources/fcp/dubl-chi-3.69/content/development_chi_catalog.json' in source
    assert 'shared/src/commonMain/resources/fcp/dubl-chi-3.69/content/chi_catalog.json' in source
