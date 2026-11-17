# UI Revamp Stage 1 — Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the "Fury HUD" fonts, tokens, theme and shared components, and apply the theme to all three apps so the existing screens pick up the new fonts and colours.

**Architecture:** Everything lives in `shared` under `com.furybook.ui.*`. Existing `Dubl*` colour names keep their names and change value, so the ~20k lines of platform UI reskin without edits. New components are stateless Compose functions with any non-trivial logic in pure, unit-tested helpers.

**Tech Stack:** Kotlin Multiplatform 2.4.20, Compose Multiplatform 1.12.0, Material3 1.12.0-alpha03, Compose `components-resources` 1.12.0, `fonttools` (one-off, to cut static font instances).

**Spec:** `docs/superpowers/specs/2026-11-17-ui-revamp-design.md`

This is stage 1 of 7. Stages 2–7 (screens) get their own plans.

## Global Constraints

- Rules, formulas, persistence, snapshot schema, transfer format, cloud protocol: untouched.
- No file under `com.furybook.dubl.model`, `.application`, `.data`, `.state` or `com.furybook.core` changes in this stage.
- `shared/commonMain` must not import `com.furybook.android.*` or `com.furybook.desktop.*`.
- Fonts: Unbounded (headings, numbers) and Onest (body), bundled, OFL, Cyrillic.
- Palette (exact): background `#08080A`, surface `#111114`, raised `#16161A`, inset `#08080A`, border `#26262C`, strong border `#34343A`, text `#F4F1EC`, muted `#B4B1AC`, crimson `#E5384F`, deep crimson `#B82840`, gold `#F5C04E`.
- Radii 6dp (controls) and 8dp (tiles, panels). Minimum touch target 44dp.
- Text on a filled surface must reach 4.5:1 contrast. White text goes on deep crimson, never on crimson.
- Maven Central rate-limits the dev sandbox, so Gradle cannot run there. Local verification is `python3 -m pytest -q tools/tests` compared against the baseline below; Gradle verification is GitHub Actions on a pull request from `ui-revamp`.
- Python baseline before this stage: 37 failed, 6 errors, 328 passed (most failures need `kotlinc`, which the sandbox lacks). No test may move from passed to failed.

## Review Focus

1. **Web shows blank or tofu text while fonts load.** Expect the existing loading spinner until fonts are ready. Pinned in Task 3.
2. **A resource with maximum 0, or current above maximum.** Expect an empty or full bar, never a crash or overflow. Pinned in Task 4.
3. **Long Russian labels in tiles and chips** ("Телосложение", "Инженерное дело (Ремонт)"). Expect ellipsis on one line, not a broken layout. Pinned in Task 4 and Task 5.
4. **Roll breakdown with negative or zero modifiers.** Expect "− 2", and zero terms omitted. Pinned in Task 7.
5. **Old screens that hard-code `Color.White` on `DublAccent`.** Crimson is now lighter, so such text can fail contrast. Pinned in Task 2 (audit step).

---

### Task 1: Bundle fonts as shared Compose resources

**Files:**
- Create: `shared/src/commonMain/composeResources/font/unbounded_bold.ttf`, `unbounded_black.ttf`, `onest_regular.ttf`, `onest_medium.ttf`, `onest_semibold.ttf`, `onest_bold.ttf`
- Create: `shared/src/commonMain/composeResources/font/OFL-Unbounded.txt`, `OFL-Onest.txt`
- Create: `tools/fonts/build_fonts.py`
- Modify: `shared/build.gradle.kts`
- Test: `tools/tests/test_fury_fonts.py`

**Interfaces:**
- Produces: generated accessors `com.furybook.shared.generated.resources.Res.font.unbounded_bold`, `.unbounded_black`, `.onest_regular`, `.onest_medium`, `.onest_semibold`, `.onest_bold`.

- [ ] **Step 1: Write the failing test**

```python
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / 'shared/src/commonMain/composeResources/font'
NAMES = ['unbounded_bold', 'unbounded_black', 'onest_regular', 'onest_medium', 'onest_semibold', 'onest_bold']

def test_static_fonts_are_bundled_with_licences():
    for name in NAMES:
        path = FONTS / f'{name}.ttf'
        assert path.exists(), name
        assert 20_000 < path.stat().st_size < 400_000, name
    assert (FONTS / 'OFL-Unbounded.txt').exists()
    assert (FONTS / 'OFL-Onest.txt').exists()

def test_fonts_cover_cyrillic():
    from fontTools.ttLib import TTFont
    for name in NAMES:
        cmap = TTFont(FONTS / f'{name}.ttf').getBestCmap()
        for ch in 'ШершеньЁё0123456789+−':
            assert ord(ch) in cmap, (name, ch)

def test_shared_declares_compose_resources():
    build = (ROOT / 'shared/build.gradle.kts').read_text(encoding='utf-8')
    assert 'org.jetbrains.compose.components:components-resources:1.12.0' in build
    assert 'publicResClass = true' in build
    assert 'packageOfResClass = "com.furybook.shared.generated.resources"' in build
```

- [ ] **Step 2: Run it** — `python3 -m pytest -q tools/tests/test_fury_fonts.py` → FAIL (files missing).

- [ ] **Step 3: Write `tools/fonts/build_fonts.py`**

Downloads `Unbounded[wght].ttf` and `Onest[wght].ttf` plus each `OFL.txt` from `https://raw.githubusercontent.com/google/fonts/main/ofl/{unbounded,onest}/`, then uses `fontTools.varLib.instancer.instantiateVariableFont(font, {"wght": w})` to write static instances: Unbounded 700 → `unbounded_bold`, 900 → `unbounded_black`; Onest 400/500/600/700 → `onest_regular/medium/semibold/bold`. If U+2212 (−) is missing from a font, map it to the hyphen-minus glyph in the cmap. Run it once and commit the outputs.

- [ ] **Step 4: Edit `shared/build.gradle.kts`**

Add `implementation("org.jetbrains.compose.components:components-resources:1.12.0")` to `commonMain.dependencies`; add `androidResources { enable = true }` inside the `android { }` block; add at top level:

```kotlin
compose.resources {
    publicResClass = true
    packageOfResClass = "com.furybook.shared.generated.resources"
}
```

- [ ] **Step 5: Run the test** → PASS.
- [ ] **Step 6: Commit** — `feat(ui): bundle Unbounded and Onest as shared resources`

### Task 2: Tokens — colours, shapes, spacing

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/ui/theme/Color.kt`
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/theme/Tokens.kt`
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/theme/Contrast.kt`
- Modify: `shared/src/commonMain/kotlin/com/furybook/ui/theme/Theme.kt` (colour scheme only)
- Test: `shared/src/commonTest/kotlin/com/furybook/ui/theme/FuryTokensTest.kt`

**Interfaces:**
- Produces: existing names re-valued — `DublBackground=#08080A`, `DublSurface=#111114`, `DublSurfaceRaised=#16161A`, `DublSurfaceInset=#08080A`, `DublBorder=#26262C`, `DublText=#F4F1EC`, `DublMuted=#B4B1AC`, `DublAccent=#E5384F`, `DublAccentStrong=#FF6B7F`, `DublAccentSoft=#3A1119`, `DublFocus=#FF95A3`, `DublGold=#F5C04E`, `DublHealth=#E5384F`, `DublStamina=#F5C04E`, `DublMana=#6F98D6`, `DublCustomResource=#65B4A4`, `DublDanger=#FF95A3`.
- Produces: new `val FuryBorderStrong = Color(0xFF34343A)`, `val FuryAccentFill = Color(0xFFB82840)`, `val FuryAccentFillPressed = Color(0xFF8E1B30)`, `val FuryOnAccentFill = Color.White`.
- Produces: `object FuryShapes { val control = RoundedCornerShape(6.dp); val panel = RoundedCornerShape(8.dp); val sheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) }`, `object FurySpacing { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }`, `val FuryMinTouch = 44.dp`.
- Produces: `fun contrastRatio(foreground: Color, background: Color): Double` (WCAG relative luminance).

- [ ] **Step 1: Write the failing test**

```kotlin
class FuryTokensTest {
    @Test fun paletteMatchesSpec() {
        assertEquals(Color(0xFF08080A), DublBackground)
        assertEquals(Color(0xFFE5384F), DublAccent)
        assertEquals(Color(0xFFB82840), FuryAccentFill)
        assertEquals(Color(0xFFF5C04E), DublGold)
    }
    @Test fun textPairsReachAA() {
        assertTrue(contrastRatio(DublText, DublBackground) >= 4.5)
        assertTrue(contrastRatio(DublMuted, DublSurfaceRaised) >= 4.5)
        assertTrue(contrastRatio(FuryOnAccentFill, FuryAccentFill) >= 4.5)
        assertTrue(contrastRatio(DublAccentStrong, DublSurface) >= 4.5)
    }
    @Test fun whiteOnCrimsonIsNotUsedAsAFill() {
        assertTrue(contrastRatio(Color.White, DublAccent) < 4.5)
    }
}
```

- [ ] **Step 2: Implement** the values and helpers above. In `Theme.kt` set `primary = FuryAccentFill`, `onPrimary = FuryOnAccentFill`, `primaryContainer = DublAccentSoft`, `secondaryContainer = Color(0xFF2A2208)`, `outlineVariant = DublBorder`, and keep every other role mapped as it is today.
- [ ] **Step 3: Audit** — `grep -rn "DublAccent\b\|DesktopAccent\b" app desktopApp webApp --include=*.kt | grep -i "background\|containerColor"`; for each hit where white text sits on the fill, swap the fill to `FuryAccentFill`. List the edited lines in the commit body.
- [ ] **Step 4: Run** `python3 -m pytest -q tools/tests` → no test leaves the baseline's passed set.
- [ ] **Step 5: Commit** — `feat(ui): Fury HUD colour, shape and spacing tokens`

### Task 3: Typography and theme wiring on all three shells

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/furybook/ui/theme/Type.kt`, `Theme.kt`
- Modify: `desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt` (`DesktopVisualTheme`, lines 95–125)
- Modify: `webApp/src/jsMain/kotlin/com/furybook/web/Main.kt` (its `DesktopVisualTheme`, and `WebRoot` boot gate)
- Test: `tools/tests/test_fury_typography.py`

**Interfaces:**
- Consumes: `Res.font.*` from Task 1.
- Produces: `@Composable fun furyDisplayFamily(): FontFamily` (Unbounded 700, 900), `@Composable fun furyBodyFamily(): FontFamily` (Onest 400–700), `@Composable fun furyTypography(): Typography`, `@Composable fun furyFontsReady(): Boolean`.
- Type scale: `displayLarge` Unbounded 900 48/48sp; `headlineMedium` Unbounded 900 28/32; `headlineSmall` Unbounded 700 22/26; `titleLarge` Unbounded 700 18/24; `titleMedium` Onest 700 16/22; `titleSmall` Onest 700 14/20; `bodyLarge` Onest 400 16/24; `bodyMedium` Onest 400 14/20; `bodySmall` Onest 400 12/16; `labelLarge` Onest 600 14/18; `labelMedium` Onest 600 12/16; `labelSmall` Unbounded 700 10/14 with 0.06em tracking.
- `DublTheme` keeps its name and signature and now passes `furyTypography()`.

- [ ] **Step 1: Write the failing test** (source contract, since Compose cannot run in the sandbox)

```python
def test_theme_uses_bundled_families():
    type_kt = read('shared/src/commonMain/kotlin/com/furybook/ui/theme/Type.kt')
    for token in ['Res.font.unbounded_black', 'Res.font.unbounded_bold', 'Res.font.onest_regular',
                  'Res.font.onest_medium', 'Res.font.onest_semibold', 'Res.font.onest_bold',
                  'fun furyTypography()', 'fun furyFontsReady()']:
        assert token in type_kt
    assert 'typography = furyTypography()' in read('shared/src/commonMain/kotlin/com/furybook/ui/theme/Theme.kt')

def test_shells_do_not_override_font_sizes_and_web_waits_for_fonts():
    for path in ['desktopApp/src/main/kotlin/com/furybook/desktop/Main.kt', 'webApp/src/jsMain/kotlin/com/furybook/web/Main.kt']:
        assert 'typography.headlineMedium.copy(' not in read(path)
    assert 'furyFontsReady()' in read('webApp/src/jsMain/kotlin/com/furybook/web/Main.kt')
```

- [ ] **Step 2: Implement.** `furyFontsReady()` returns true once every `Font(Res.font.x)` has resolved (on JS use `preloadFont(Res.font.x)` states; on other targets it is immediately true — use `expect`/`actual` only if the common API cannot express it). `DesktopVisualTheme` on Desktop and Web drops its `typography.copy(...)` block and its colour overrides that merely restate the scheme. In `WebRoot`, show the existing loading screen while `!furyFontsReady()`.
- [ ] **Step 3: Run** the new test → PASS, then the full Python suite against the baseline.
- [ ] **Step 4: Commit** — `feat(ui): Fury HUD typography applied on Android, Desktop and Web`

### Task 4: Components — button, tile, resource bar

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/components/FuryButton.kt`, `FuryTile.kt`, `FuryBar.kt`
- Modify: `shared/src/commonMain/kotlin/com/furybook/ui/components/DublCard.kt`, `DublStatCard.kt`, `DublResourceCard.kt` (use `FuryShapes.panel`, solid `DublBorder`, `FuryBar`)
- Test: `shared/src/commonTest/kotlin/com/furybook/ui/components/FuryBarLogicTest.kt`

**Interfaces:**
- Produces:
  - `enum class FuryButtonStyle { Primary, Secondary, Ghost, Danger, OnAccent }`
  - `@Composable fun FuryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: FuryButtonStyle = FuryButtonStyle.Secondary, enabled: Boolean = true)` — height ≥ `FuryMinTouch`, `FuryShapes.control`; Primary = `FuryAccentFill` + white; Secondary = 2dp `FuryBorderStrong` outline; Ghost = text only; Danger = text in `DublDanger`; OnAccent = black fill for use on the crimson tray.
  - `@Composable fun FuryIconButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit)` — 44dp square, `label` is the content description.
  - `@Composable fun FuryTile(label: String, value: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, compact: Boolean = false)` — label in `labelSmall` muted, value in `displayLarge` (`headlineMedium` when compact), both single line with ellipsis.
  - `data class FuryBarModel(val fraction: Float, val segments: Int, val filledSegments: Int)`
  - `fun furyBarModel(current: Int, maximum: Int, segmentLimit: Int = 10): FuryBarModel` — clamps `current` to `0..max(maximum, 0)`; `segments = maximum` when `maximum in 1..segmentLimit`, else `0` (continuous bar); `fraction = 0f` when `maximum <= 0`.
  - `@Composable fun FuryBar(current: Int, maximum: Int, color: Color, modifier: Modifier = Modifier, height: Dp = 14.dp)`

- [ ] **Step 1: Write the failing test**

```kotlin
class FuryBarLogicTest {
    @Test fun zeroMaximumIsEmptyAndContinuous() = assertEquals(FuryBarModel(0f, 0, 0), furyBarModel(5, 0))
    @Test fun currentAboveMaximumClamps() = assertEquals(FuryBarModel(1f, 3, 3), furyBarModel(9, 3))
    @Test fun negativeCurrentClamps() = assertEquals(FuryBarModel(0f, 3, 0), furyBarModel(-2, 3))
    @Test fun largeMaximumIsContinuous() = assertEquals(FuryBarModel(0.5f, 0, 0), furyBarModel(16, 32))
    @Test fun smallMaximumIsSegmented() = assertEquals(FuryBarModel(2f / 3f, 3, 2), furyBarModel(2, 3))
}
```

- [ ] **Step 2: Implement** the three files and restyle the three existing cards.
- [ ] **Step 3: Run** the Python suite against the baseline (the Kotlin test runs in CI).
- [ ] **Step 4: Commit** — `feat(ui): Fury button, tile and resource bar`

### Task 5: Components — chips, stepper, text field, tabs

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/components/FuryChipGroup.kt`, `FuryStepper.kt`, `FuryTextField.kt`, `FuryTabs.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/ui/components/FuryStepperLogicTest.kt`

**Interfaces:**
- Produces:
  - `data class FuryChoice<T>(val value: T, val label: String, val enabled: Boolean = true)`
  - `@Composable fun <T> FuryChipGroup(choices: List<FuryChoice<T>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier, columns: Int = 4)` — equal-width grid, selected chip filled `FuryAccentFill` with white bold text, labels single line with ellipsis.
  - `@Composable fun <T> FuryMultiChipGroup(choices: List<FuryChoice<T>>, selected: Set<T>, onToggle: (T) -> Unit, modifier: Modifier = Modifier, columns: Int = 4)`
  - `data class FuryStepperState(val canDecrease: Boolean, val canIncrease: Boolean)`
  - `fun furyStepperState(value: Int, min: Int, max: Int, enabled: Boolean = true): FuryStepperState`
  - `@Composable fun FuryStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 0, max: Int = Int.MAX_VALUE, enabled: Boolean = true, decreaseLabel: String = "Уменьшить", increaseLabel: String = "Увеличить")`
  - `@Composable fun FuryTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, error: String? = null, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, visualTransformation: VisualTransformation = VisualTransformation.None)` — label above the field, field fills its width, error text below in `DublDanger`.
  - `data class FuryTab<T>(val value: T, val label: String)`
  - `@Composable fun <T> FuryTopTabs(tabs: List<FuryTab<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier)` and `@Composable fun <T> FuryBottomTabs(tabs: List<FuryTab<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier)` — bottom tabs are 60dp tall with a 3dp crimson top marker on the selected tab.

- [ ] **Step 1: Write the failing test**

```kotlin
class FuryStepperLogicTest {
    @Test fun atMinimumCannotDecrease() = assertEquals(FuryStepperState(false, true), furyStepperState(0, 0, 5))
    @Test fun atMaximumCannotIncrease() = assertEquals(FuryStepperState(true, false), furyStepperState(5, 0, 5))
    @Test fun disabledBlocksBoth() = assertEquals(FuryStepperState(false, false), furyStepperState(2, 0, 5, enabled = false))
    @Test fun valueOutsideRangeStillAllowsMovingBackIn() = assertEquals(FuryStepperState(true, false), furyStepperState(9, 0, 5))
}
```

- [ ] **Step 2: Implement.**
- [ ] **Step 3: Run** the Python suite against the baseline.
- [ ] **Step 4: Commit** — `feat(ui): Fury chips, stepper, text field and tabs`

### Task 6: Adaptive dialog scaffold

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/components/FuryDialogScaffold.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/ui/components/FuryDialogPolicyTest.kt`

**Interfaces:**
- Consumes: `DublLayoutClass`, `layoutClassForWidth` (existing), `FuryButton` (Task 4).
- Produces:
  - `enum class FuryDialogPresentation { Centered, BottomSheet }`
  - `fun furyDialogPresentation(layout: DublLayoutClass, widthDp: Int): FuryDialogPresentation` — `BottomSheet` when `widthDp < 600`, else `Centered`.
  - `data class FuryDialogAction(val label: String, val onClick: () -> Unit, val enabled: Boolean = true)`
  - `@Composable fun FuryDialogScaffold(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, primary: FuryDialogAction? = null, secondary: FuryDialogAction? = null, destructive: FuryDialogAction? = null, maxWidth: Dp = 560.dp, content: @Composable ColumnScope.() -> Unit)` — centred panel (`FuryShapes.panel`) or bottom sheet (`FuryShapes.sheet`, drag handle); close button top right labelled "Закрыть"; content scrolls; footer row is destructive (left, Danger style), spacer, secondary, primary (Primary style). Escape and scrim click call `onDismiss`.

- [ ] **Step 1: Write the failing test**

```kotlin
class FuryDialogPolicyTest {
    @Test fun phoneWidthUsesBottomSheet() = assertEquals(FuryDialogPresentation.BottomSheet, furyDialogPresentation(DublLayoutClass.COMPACT, 390))
    @Test fun narrowDesktopWindowStaysCentered() = assertEquals(FuryDialogPresentation.Centered, furyDialogPresentation(DublLayoutClass.COMPACT, 720))
    @Test fun wideUsesCentered() = assertEquals(FuryDialogPresentation.Centered, furyDialogPresentation(DublLayoutClass.WIDE, 1400))
}
```

- [ ] **Step 2: Implement** with `androidx.compose.ui.window.Dialog` and `BoxWithConstraints` (available on all three targets).
- [ ] **Step 3: Run** the Python suite against the baseline.
- [ ] **Step 4: Commit** — `feat(ui): adaptive Fury dialog scaffold`

### Task 7: Roll tray view

**Files:**
- Create: `shared/src/commonMain/kotlin/com/furybook/ui/components/FuryRollTray.kt`
- Test: `shared/src/commonTest/kotlin/com/furybook/ui/components/FuryRollTrayModelTest.kt`

**Interfaces:**
- Produces (display-only; no dice are rolled and no rule is evaluated here):
  - `data class FuryRollTerm(val label: String, val value: Int)`
  - `data class FuryRollView(val title: String, val dice: List<Int>, val terms: List<FuryRollTerm>, val total: Int, val outcome: String? = null)`
  - `data class FuryRollHistoryEntry(val title: String, val total: Int)`
  - `fun furyRollBreakdown(view: FuryRollView): String` — dice sum first, then each non-zero term as `" + 8 Ловкость"` or `" − 2 Поправка"` (U+2212); zero terms omitted.
  - `@Composable fun FuryRollTray(view: FuryRollView?, history: List<FuryRollHistoryEntry>, onRollAgain: () -> Unit, modifier: Modifier = Modifier, emptyText: String = "Нажмите на характеристику или умение, чтобы бросить", controls: @Composable RowScope.() -> Unit = {})` — `FuryAccentFill` panel, total in `displayLarge`, white dice squares, `controls` slot for advantage/hindrance/modifier, "Бросить снова" as `FuryButtonStyle.OnAccent`, up to five history rows.

- [ ] **Step 1: Write the failing test**

```kotlin
class FuryRollTrayModelTest {
    private val base = FuryRollView("Холодное оружие", listOf(4, 5), listOf(FuryRollTerm("Ловкость", 8), FuryRollTerm("ранг", 7)), 24)
    @Test fun listsDiceSumThenTerms() = assertEquals("9 + 8 Ловкость + 7 ранг", furyRollBreakdown(base))
    @Test fun negativeTermUsesMinusSign() =
        assertEquals("9 + 8 Ловкость − 2 Поправка", furyRollBreakdown(base.copy(terms = listOf(FuryRollTerm("Ловкость", 8), FuryRollTerm("Поправка", -2)))))
    @Test fun zeroTermsAreOmitted() =
        assertEquals("9 + 8 Ловкость", furyRollBreakdown(base.copy(terms = listOf(FuryRollTerm("Ловкость", 8), FuryRollTerm("ранг", 0)))))
    @Test fun noDiceShowsTermsOnly() = assertEquals("0 + 8 Ловкость", furyRollBreakdown(base.copy(dice = emptyList(), terms = listOf(FuryRollTerm("Ловкость", 8)))))
}
```

- [ ] **Step 2: Implement.**
- [ ] **Step 3: Run** the Python suite against the baseline.
- [ ] **Step 4: Commit** — `feat(ui): Fury roll tray view`

### Task 8: CI verification

- [ ] **Step 1:** Push `ui-revamp` and open a draft pull request to `main` (needs the owner's go-ahead; CI only runs on pull requests).
- [ ] **Step 2:** Wait for Android CI, Web CI, Linux Desktop, Windows Desktop and requirements workflows. Expected: all green. Fix compile errors on the branch; do not touch rules code to get there.
- [ ] **Step 3:** Hand the owner the Linux AppImage and Android APK artifacts from the run to look at.
