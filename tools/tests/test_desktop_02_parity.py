import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTROLLER = ROOT / 'app/src/main/java/com/furybook/android/state/CharacterController.kt'
SESSION = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/state/CharacterSession.kt'
APPLICATION = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/application'


class Desktop02ParityTest(unittest.TestCase):
    def test_shared_character_session_is_internal_implementation_detail(self):
        self.assertTrue(SESSION.exists(), 'CharacterSession must exist in shared commonMain')
        self.assertIn('internal class CharacterSession', SESSION.read_text(encoding='utf-8'))

    def test_android_controller_is_backed_by_shared_application_not_raw_session(self):
        controller = CONTROLLER.read_text(encoding='utf-8')
        self.assertIn('private fun createApplication(canonical: Boolean) = DublApplication(', controller)
        self.assertIn('private var application = createApplication(capabilities.localDurable || initialCanonicalStates.isNotEmpty() || legacyGate?.readOnly == true)', controller)
        self.assertIn('val isCanonicalProtected: Boolean get() = capabilities.localDurable || canonicalStates.isNotEmpty() || legacyGate?.readOnly == true', controller)
        self.assertIn('writePolicy = if(canonical) CanonicalWritePolicy.Protected else CanonicalWritePolicy.LegacyIsolated', controller)
        self.assertNotIn('CharacterSession', controller)
        self.assertNotIn('session.', controller)
        self.assertNotIn('fun updateActive(', controller)
        for capability in ('character', 'skills', 'development', 'magic', 'equipment', 'sheet'):
            self.assertIn(f'application.{capability}', controller)

    def test_shared_session_and_application_have_no_platform_imports(self):
        paths = [SESSION, *sorted(APPLICATION.glob('*.kt'))]
        for path in paths:
            text = path.read_text(encoding='utf-8')
            for forbidden in ('java.', 'javax.', 'android.', 'androidx.'):
                self.assertNotIn(f'import {forbidden}', text, path.name)


if __name__ == '__main__':
    unittest.main()
