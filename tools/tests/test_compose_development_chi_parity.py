from pathlib import Path

# Stage 4: the Навыки tab is gone. Its ЦИ parity (economy from the pack, the pack-mounted ЦИ tab, automatic access,
# bounded resource controls, requirement/unlock navigation) now lives in the shared editor and the sheet's ЦИ panel.
ROOT = Path(__file__).resolve().parents[2]
EDITOR = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/editor'
SHEET = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/ui/sheet'
WORKFLOW = ROOT / '.github/workflows/linux-appimage.yml'


def read(path: Path) -> str:
    assert path.exists(), f'missing {path}'
    return path.read_text(encoding='utf-8')


def test_development_summary_uses_pack_composed_character_economy():
    perks = read(EDITOR / 'EditorPerkModels.kt')
    mounts = read(SHEET / 'SheetEditModels.kt')
    assert 'includeChi = sheetChiEconomyMounted(host)' in perks
    assert 'FcpUiSurface.CHARACTER_ECONOMY' in mounts and 'FcpUiComponent.XP_LINE' in mounts
    assert 'economy.remainingXp' in perks
    assert 'economy.abilityPointsRemaining' in perks
    assert 'fun editorCatalogFor(' in read(EDITOR / 'EditorCatalogs.kt')


def test_chi_tab_is_mounted_only_from_fcp_ui_contribution():
    perks = read(EDITOR / 'EditorPerkModels.kt')
    assert 'FcpUiSurface.DEVELOPMENT_TABS' in perks
    assert 'FcpUiComponent.DEVELOPMENT_BROWSER' in perks
    assert 'FcpOrderedUiItem(chi.order' in perks
    assert 'tabs.forEach' in read(EDITOR / 'EditorPerksStep.kt')


def test_chi_card_matches_android_automatic_access_semantics():
    card = read(EDITOR / 'EditorChiCard.kt')
    assert 'c.chiAutomaticAccess' in card
    assert 'c.chiProgressionBonus' in card
    assert 'c.chiBaseMaximum' in card
    assert 'card.active' in card and 'if (!card.locked)' in card
    assert 'Ресурс открыт способностью «Внутренняя ЦИ»' in card


def test_chi_resource_controls_are_bounded_and_explain_cost_formula():
    card = read(EDITOR / 'EditorChiCard.kt')
    panel = read(SHEET / 'SheetChiPanel.kt')
    assert 'enabled = panel.current > 0' in panel
    assert 'enabled = panel.current < panel.maximum' in panel
    assert 'CharacterEconomy.CHI_BONUS_RANK_XP' in card
    assert 'card.bonusRanks < EDITOR_CHI_MAX_BONUS_RANKS' in card
    assert 'Максимум: база ${c.chiBaseMaximum} + запас ${c.chiBonusRanks} + развитие ${c.chiProgressionBonus}' in card


def test_development_details_can_follow_requirements_and_children():
    perks = read(EDITOR / 'EditorPerkModels.kt')
    step = read(EDITOR / 'EditorPerksStep.kt')
    assert 'targetEntryId' in perks
    assert 'planner.unlocks(entry.id).map { EditorPerkLink(' in perks
    assert 'state.openPerk(context, link.entryId)' in step
    assert 'onOpen: (String) -> Unit' in step


def test_linux_release_gate_runs_development_chi_parity_guard():
    text = read(WORKFLOW)
    assert 'test_compose_development_chi_parity.py' in text


def test_owned_entries_surface_manual_or_failed_requirements_like_android():
    perks = read(EDITOR / 'EditorPerkModels.kt')
    assert 'RequirementStatus.MANUAL' in perks
    assert 'it.status != RequirementStatus.OK' in perks
    assert 'val warn = eval.rank > 0' in perks


def test_character_sheet_development_details_wires_navigation_callback():
    screen = read(SHEET / 'SheetScreen.kt')
    assert 'SheetDevelopmentDetailsDialog(' in screen
    assert 'state.openEditor(forceBasics = false, entryId = dialog.entryId)' in screen
