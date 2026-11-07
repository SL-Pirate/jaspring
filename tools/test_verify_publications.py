import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


spec = importlib.util.spec_from_file_location("verify_publications", Path(__file__).with_name("verify-publications.py"))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)
NAMESPACES = {"m": "http://maven.apache.org/POM/4.0.0"}


def dependency(group, artifact, scope="runtime"):
    element = ET.Element("dependency")
    for name, value in (("groupId", group), ("artifactId", artifact), ("scope", scope)):
        if value is not None:
            ET.SubElement(element, f"{{{NAMESPACES['m']}}}{name}").text = value
    return element


class VerifyPluginDependenciesTest(unittest.TestCase):
    def setUp(self):
        self.dependencies = [
            dependency("com.google.code.gson", "gson"),
            dependency("org.apache.commons", "commons-compress"),
        ]

    def testAcceptsJnpmBuildLibraries(self):
        verifier.verify_plugin_dependencies(self.dependencies, NAMESPACES)

    def testRejectsJaspringRuntimeLibraries(self):
        for artifact in ("jaspring-core", "jaspring-browser-runtime", "jaspring-typescript", "jaspring-spring-boot-starter"):
            with self.subTest(artifact=artifact), self.assertRaisesRegex(SystemExit, "dev.isira:" + artifact):
                verifier.verify_plugin_dependencies(self.dependencies + [dependency("dev.isira", artifact)], NAMESPACES)

    def testRejectsTestLibrariesEvenWithRuntimeScope(self):
        with self.assertRaisesRegex(SystemExit, "org.junit.jupiter:junit-jupiter"):
            verifier.verify_plugin_dependencies(
                self.dependencies + [dependency("org.junit.jupiter", "junit-jupiter")], NAMESPACES)

    def testChecksTheGroupEvenWhenAnArtifactNameMatches(self):
        with self.assertRaisesRegex(SystemExit, "other.group:gson"):
            verifier.verify_plugin_dependencies(self.dependencies + [dependency("other.group", "gson")], NAMESPACES)

    def testRejectsMissingBuildLibraries(self):
        for dependencies in ([], self.dependencies[:1], self.dependencies[1:]):
            with self.subTest(dependencies=dependencies), self.assertRaisesRegex(SystemExit, "Missing Gradle plugin"):
                verifier.verify_plugin_dependencies(dependencies, NAMESPACES)

    def testBuildLibrariesMustRemainRuntimeDependencies(self):
        for scope in (None, "compile", "provided", "test"):
            with self.subTest(scope=scope), self.assertRaisesRegex(SystemExit, "runtime scope"):
                verifier.verify_plugin_dependencies(
                    [dependency("com.google.code.gson", "gson", scope), self.dependencies[1]], NAMESPACES)


if __name__ == "__main__":
    unittest.main()
