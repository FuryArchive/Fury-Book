import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

class Desktop02PersistenceTest(unittest.TestCase):
    def test_shared_schema11_codec_exists(self):
        path = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/data/SnapshotCodec.kt'
        self.assertTrue(path.exists())
        text = path.read_text()
        self.assertIn('const val SCHEMA = 11', text)
        self.assertIn('fun encode(', text)
        self.assertIn('fun decode(', text)
        self.assertIn('ruleset', text)

    def test_android_repository_delegates_to_shared_codec(self):
        text = (ROOT / 'app/src/main/java/com/furybook/android/data/CharacterRepository.kt').read_text()
        self.assertIn('CachedSnapshotCodec', text)
        self.assertIn('codec.decode(raw)', text)
        self.assertIn('codec.encode(snapshot)', text)
        self.assertNotIn('org.json', text)

    def test_desktop_file_store_exists_and_uses_atomic_replace(self):
        path = ROOT / 'shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopCharacterStore.kt'
        self.assertTrue(path.exists())
        text = path.read_text()
        self.assertIn('class DesktopCharacterStore', text)
        self.assertIn('SnapshotCodec.decode', text)
        self.assertIn('SnapshotCodec.encode', text)
        self.assertIn('ATOMIC_MOVE', text)

    def test_extras_storage_boundary_exists(self):
        interface = ROOT / 'shared/src/commonMain/kotlin/com/furybook/dubl/data/CharacterExtrasStore.kt'
        desktop = ROOT / 'shared/src/desktopMain/kotlin/com/furybook/dubl/data/DesktopCharacterExtrasStore.kt'
        self.assertTrue(interface.exists())
        self.assertTrue(desktop.exists())

if __name__ == '__main__':
    unittest.main()
