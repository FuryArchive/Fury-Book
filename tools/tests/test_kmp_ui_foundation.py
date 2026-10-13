from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'shared' / 'src' / 'commonMain' / 'kotlin'
DESKTOP = ROOT / 'desktopApp' / 'src' / 'main' / 'kotlin'


class KmpUiFoundationTest(unittest.TestCase):
    def test_shared_theme_and_primitives_exist(self):
        expected = [
            SHARED / 'com/furybook/ui/theme/Color.kt',
            SHARED / 'com/furybook/ui/theme/Theme.kt',
            SHARED / 'com/furybook/ui/theme/Type.kt',
            SHARED / 'com/furybook/ui/components/DublCard.kt',
            SHARED / 'com/furybook/ui/components/DublStatCard.kt',
            SHARED / 'com/furybook/ui/components/DublResourceCard.kt',
            SHARED / 'com/furybook/ui/layout/DublLayoutPolicy.kt',
        ]
        missing = [str(path.relative_to(ROOT)) for path in expected if not path.exists()]
        self.assertEqual([], missing, 'Missing shared UI foundation files: ' + ', '.join(missing))

    def test_shared_palette_contains_canonical_android_tokens(self):
        color_file = SHARED / 'com/furybook/ui/theme/Color.kt'
        self.assertTrue(color_file.exists(), 'Shared Color.kt must exist')
        text = color_file.read_text(encoding='utf-8')
        for token in [
            'DublSurfaceInset', 'DublAccentStrong', 'DublFocus', 'DublGold',
            'DublHealth', 'DublStamina', 'DublMana', 'DublCustomResource',
        ]:
            self.assertIn(f'val {token}', text)

    def test_layout_policy_uses_approved_breakpoints(self):
        policy = SHARED / 'com/furybook/ui/layout/DublLayoutPolicy.kt'
        self.assertTrue(policy.exists(), 'Shared layout policy must exist')
        text = policy.read_text(encoding='utf-8')
        self.assertIn('COMPACT_MAX_WIDTH_DP = 899', text)
        self.assertIn('WIDE_MIN_WIDTH_DP = 1320', text)
        self.assertIn('DublLayoutClass.COMPACT', text)
        self.assertIn('DublLayoutClass.NORMAL', text)
        self.assertIn('DublLayoutClass.WIDE', text)

    def test_desktop_shell_exists_and_has_no_horizontal_scroll(self):
        main = DESKTOP / 'com/furybook/desktop/Main.kt'
        self.assertTrue(main.exists(), 'Desktop Main.kt must exist')
        text = main.read_text(encoding='utf-8')
        self.assertNotIn('horizontalScroll(', text)
        screen_text = '\n'.join(path.read_text(encoding='utf-8') for path in (DESKTOP / 'com/furybook/desktop/screens').glob('*.kt'))
        self.assertTrue('verticalScroll(' in text or 'LazyColumn(' in screen_text)
        self.assertIn('layoutClassForWidth', text)
        state = (DESKTOP / 'com/furybook/desktop/DesktopAppState.kt').read_text(encoding='utf-8')
        self.assertNotIn('InMemoryCharacterStore', text + state)
        self.assertIn('DesktopCharacterStore', state)
        self.assertNotRegex(text, r'(?m)^\s*snapshot = snapshot\s*$')
        self.assertNotIn('DublLayoutClass.WIDE,\n            ->', text)
        self.assertNotIn('        }\n        }\n        Spacer(Modifier.weight(1f))', text)

    def test_desktop_declares_material3_dependency(self):
        build = (ROOT / 'desktopApp' / 'build.gradle.kts').read_text(encoding='utf-8')
        self.assertIn('org.jetbrains.compose.material3:material3:1.12.0-alpha03', build)

    def test_shared_theme_keeps_canonical_material_roles(self):
        theme = SHARED / 'com/furybook/ui/theme/Theme.kt'
        text = theme.read_text(encoding='utf-8')
        self.assertIn('secondaryContainer = Color(0xFF2D271E)', text)
        self.assertIn('onSecondaryContainer = DublText', text)
        self.assertIn('outlineVariant = DublBorder', text)

    def test_android_theme_imports_resolve_from_shared_theme_package(self):
        declarations = ''
        theme_dir = SHARED / 'com/furybook/ui/theme'
        for path in theme_dir.glob('*.kt'):
            declarations += path.read_text(encoding='utf-8') + '\n'

        app_root = ROOT / 'app' / 'src' / 'main'
        missing = []
        prefix = 'import com.furybook.ui.theme.'
        for path in app_root.rglob('*.kt'):
            for line in path.read_text(encoding='utf-8').splitlines():
                if not line.startswith(prefix):
                    continue
                name = line.removeprefix(prefix).strip()
                if f'val {name}' not in declarations and f'fun {name}(' not in declarations:
                    missing.append(f'{path.relative_to(ROOT)} -> {name}')
        self.assertEqual([], missing, 'Android theme imports missing from shared: ' + ', '.join(missing))

    def test_android_does_not_keep_duplicate_shared_theme_files(self):
        android_theme_dir = ROOT / 'app' / 'src' / 'main' / 'java' / 'com/furybook/android/ui/theme'
        duplicates = [name for name in ('Color.kt', 'Theme.kt', 'Type.kt') if (android_theme_dir / name).exists()]
        self.assertEqual([], duplicates, 'Theme sources must have a single shared declaration site')


if __name__ == '__main__':
    unittest.main()
