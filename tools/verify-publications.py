"""Check the built Maven repository without contacting Central or using credentials."""

import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def verify_file(path, require_signatures):
    require(path.is_file() and path.stat().st_size > 0, f"Missing artifact: {path}")
    for algorithm in ("md5", "sha1"):
        checksum = Path(f"{path}.{algorithm}")
        require(checksum.is_file(), f"Missing checksum: {checksum}")
        require(checksum.read_text().strip() == hashlib.new(algorithm, path.read_bytes()).hexdigest(),
                f"Invalid checksum: {checksum}")
    if require_signatures:
        signature = Path(f"{path}.asc")
        require(signature.is_file() and "BEGIN PGP SIGNATURE" in signature.read_text(),
                f"Missing armored signature: {signature}")


def published_version(directory, version):
    if not version.endswith("-SNAPSHOT"):
        return version
    snapshot = ET.parse(directory / "maven-metadata.xml").getroot().find("versioning/snapshot")
    return f"{version.removesuffix('SNAPSHOT')}{snapshot.findtext('timestamp')}-{snapshot.findtext('buildNumber')}"


def verify(repository, version, require_signatures):
    artifacts = {"jaspring-core", "jaspring-browser-runtime", "jaspring-spring-boot-starter", "jaspring-gradle-plugin"}
    group = repository / "dev/isira"
    require(group.is_dir(), f"Missing repository group: {group}")
    require({path.name for path in group.iterdir() if path.is_dir()} == artifacts | {"jaspring"},
            "Only core, the browser runtime, the Spring Boot starter, Gradle plugin and its marker group should be published")
    if version is None:
        version = ET.parse(group / "jaspring-core/maven-metadata.xml").findtext("versioning/latest")
        require(version, "No latest core version in repository metadata")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}

    for artifact in sorted(artifacts):
        directory = group / artifact / version
        require(directory.is_dir(), f"Missing version directory: {directory}")
        file_version = published_version(directory, version)
        base = f"{artifact}-{file_version}"
        for suffix in (".pom", ".module", ".jar", "-sources.jar", "-javadoc.jar"):
            path = directory / (base + suffix)
            verify_file(path, require_signatures)

        pom = ET.parse(directory / (base + ".pom")).getroot()
        for element, expected in (("groupId", "dev.isira"), ("artifactId", artifact), ("version", version)):
            require(pom.findtext(f"m:{element}", namespaces=ns) == expected, f"Incorrect {element}: {artifact}")
        for element in ("name", "description", "url", "licenses/license/name", "licenses/license/url",
                        "developers/developer/id", "developers/developer/name", "developers/developer/url",
                        "scm/url", "scm/connection", "scm/developerConnection"):
            value = pom.findtext("/".join("m:" + part for part in element.split("/")), namespaces=ns)
            require(value and value.strip(), f"Missing POM {element}: {artifact}")

        dependencies = {
            dependency.findtext("m:artifactId", namespaces=ns): dependency
            for dependency in pom.findall("m:dependencies/m:dependency", ns)
        }
        require(all(dependency.findtext("m:scope", namespaces=ns) != "test" for dependency in dependencies.values()),
                f"Test dependency leaked into {artifact}")
        if artifact == "jaspring-spring-boot-starter":
            core = dependencies.get("jaspring-core")
            require(core is not None and core.findtext("m:version", namespaces=ns) == version,
                    "Starter must depend on the matching core version")
            require("spring-boot-starter-websocket" not in dependencies, "WebSocket support must remain optional")
        elif artifact == "jaspring-core":
            require("polyglot" in dependencies and "js-community" in dependencies, "Missing GraalJS dependencies")
        elif artifact == "jaspring-browser-runtime":
            core = dependencies.get("jaspring-core")
            require(core is not None and core.findtext("m:version", namespaces=ns) == version,
                    "The browser runtime must depend on the matching core version")
            require(set(dependencies) == {"jaspring-core"},
                    "The browser runtime depends on core only; the Web Image API is a GraalVM JDK module")
        else:
            require(not dependencies, "The Gradle plugin must not depend on Jaspring's runtime or test libraries")

        metadata = json.loads((directory / (base + ".module")).read_text())
        require(all(metadata["component"].get(key) == value for key, value in
                    (("group", "dev.isira"), ("module", artifact), ("version", version))),
                f"Incorrect Gradle module coordinates: {artifact}")

        for suffix, required in ((".jar", "META-INF/LICENSE"), ("-sources.jar", "META-INF/LICENSE"),
                                 ("-javadoc.jar", "index.html")):
            with ZipFile(directory / (base + suffix)) as jar:
                require(required in jar.namelist(), f"Missing {required} in {artifact}{suffix}")
                if suffix == ".jar":
                    require(any(name.endswith(".class") for name in jar.namelist()), f"Empty binary jar: {artifact}")
                    resource = {
                        "jaspring-core": "META-INF/jaspring/THIRD-PARTY-LICENSES.txt",
                        "jaspring-browser-runtime": "dev/isira/jaspring/browser/runtime/WebClient.class",
                        "jaspring-spring-boot-starter": "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                        "jaspring-gradle-plugin": "META-INF/gradle-plugins/dev.isira.jaspring.properties",
                    }[artifact]
                    require(resource in jar.namelist(), f"Missing runtime resource {resource}")
                    if artifact == "jaspring-gradle-plugin":
                        require("implementation-class=dev.isira.jaspring.gradle.JaspringPlugin" in jar.read(resource).decode(),
                                "Incorrect Gradle plugin implementation class")
                elif suffix == "-sources.jar":
                    require(any(name.endswith(".java") for name in jar.namelist()), f"Empty sources jar: {artifact}")
        print(f"Verified dev.isira:{artifact}:{version}")

    marker = "dev.isira.jaspring.gradle.plugin"
    marker_group = group / "jaspring"
    require({path.name for path in marker_group.iterdir() if path.is_dir()} == {marker},
            "Unexpected artifacts in plugin marker group")
    directory = marker_group / marker / version
    path = directory / f"{marker}-{published_version(directory, version)}.pom"
    verify_file(path, require_signatures)
    pom = ET.parse(path).getroot()
    for element, expected in (("groupId", "dev.isira.jaspring"), ("artifactId", marker),
                              ("version", version), ("packaging", "pom")):
        require(pom.findtext(f"m:{element}", namespaces=ns) == expected, f"Incorrect marker {element}")
    dependencies = pom.findall("m:dependencies/m:dependency", ns)
    require(len(dependencies) == 1, "Plugin marker must have one implementation dependency")
    for element, expected in (("groupId", "dev.isira"), ("artifactId", "jaspring-gradle-plugin"), ("version", version)):
        require(dependencies[0].findtext(f"m:{element}", namespaces=ns) == expected,
                f"Incorrect plugin marker dependency {element}")
    print(f"Verified dev.isira.jaspring:{marker}:{version}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1] / "build/repo")
    parser.add_argument("--version", help="Version to verify; defaults to the most recently published core version")
    parser.add_argument("--require-signatures", action="store_true", help="Require signatures; does not cryptographically verify them")
    arguments = parser.parse_args()
    verify(arguments.repository, arguments.version, arguments.require_signatures)
