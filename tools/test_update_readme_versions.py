import importlib.util
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("update_readme_versions", Path(__file__).with_name("update-readme-versions.py"))
updater = importlib.util.module_from_spec(spec)
spec.loader.exec_module(updater)


class UpdateReadmeVersionsTest(unittest.TestCase):
    def testUpdatesOnlyJaspringInstallationVersions(self):
        text = '''implementation("dev.isira:jaspring-spring-boot-starter:0.1.0")
id("dev.isira.jaspring") version "VERSION"
<dependency>
    <groupId>dev.isira</groupId>
    <artifactId>jaspring-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
id("org.springframework.boot") version "4.1.2"
<dependency><groupId>other</groupId><artifactId>other</artifactId><version>0.1.0</version></dependency>
Example command: -PreleaseVersion=0.1.0
'''
        expected = text.replace('starter:0.1.0', 'starter:0.2.0-rc.1').replace('"VERSION"', '"0.2.0-rc.1"')
        expected = expected.replace('    <version>0.1.0</version>', '    <version>0.2.0-rc.1</version>')
        self.assertEqual(expected, updater.rewrite_versions(text, "0.2.0-rc.1"))

    def testRejectsSnapshotsAndMalformedVersions(self):
        for version in ("", "latest", "0.2", "0.2.0-SNAPSHOT", "0.2.0\n", "0.2.0\"bad"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                updater.rewrite_versions('id("dev.isira.jaspring") version "0.1.0"', version)

    def testCheckDoesNotWriteAndUpdatesAreIdempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            original = 'id("dev.isira.jaspring") version "0.1.0"\n'
            for relative in updater.README_PATHS:
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(original)
            with self.assertRaises(ValueError):
                updater.update(root, "0.2.0", check=True)
            self.assertTrue(all((root / relative).read_text() == original for relative in updater.README_PATHS))
            updater.update(root, "0.2.0")
            times = [(root / relative).stat().st_mtime_ns for relative in updater.README_PATHS]
            updater.update(root, "0.2.0")
            updater.update(root, "0.2.0", check=True)
            self.assertEqual(times, [(root / relative).stat().st_mtime_ns for relative in updater.README_PATHS])

    def testMissingSnippetsPreventPartialUpdates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / updater.README_PATHS[0]
            original = 'id("dev.isira.jaspring") version "0.1.0"\n'
            first.write_text(original)
            second = root / updater.README_PATHS[1]
            second.parent.mkdir(parents=True)
            second.write_text("No installation snippets\n")
            with self.assertRaises(ValueError):
                updater.update(root, "0.2.0")
            self.assertEqual(original, first.read_text())


if __name__ == "__main__":
    unittest.main()
