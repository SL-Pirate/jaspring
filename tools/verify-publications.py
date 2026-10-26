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


def verify(repository, version, require_signatures):
    artifacts = {"jaspring-core", "jaspring-spring-boot-starter"}
    group = repository / "dev/isira"
    require(group.is_dir(), f"Missing repository group: {group}")
    require({path.name for path in group.iterdir() if path.is_dir()} == artifacts,
            "Only core and the Spring Boot starter should be published")
    if version is None:
        version = ET.parse(group / "jaspring-core/maven-metadata.xml").findtext("versioning/latest")
        require(version, "No latest core version in repository metadata")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}

    for artifact in sorted(artifacts):
        directory = group / artifact / version
        require(directory.is_dir(), f"Missing version directory: {directory}")
        file_version = version
        if version.endswith("-SNAPSHOT"):
            snapshot = ET.parse(directory / "maven-metadata.xml").getroot().find("versioning/snapshot")
            file_version = f"{version.removesuffix('SNAPSHOT')}{snapshot.findtext('timestamp')}-{snapshot.findtext('buildNumber')}"
        base = f"{artifact}-{file_version}"
        for suffix in (".pom", ".module", ".jar", "-sources.jar", "-javadoc.jar"):
            path = directory / (base + suffix)
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
        else:
            require("polyglot" in dependencies and "js-community" in dependencies, "Missing GraalJS dependencies")

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
                    resource = ("META-INF/jaspring/THIRD-PARTY-LICENSES.txt" if artifact == "jaspring-core"
                                else "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
                    require(resource in jar.namelist(), f"Missing runtime resource {resource}")
                elif suffix == "-sources.jar":
                    require(any(name.endswith(".java") for name in jar.namelist()), f"Empty sources jar: {artifact}")
        print(f"Verified dev.isira:{artifact}:{version}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path(__file__).resolve().parents[1] / "build/repo")
    parser.add_argument("--version", help="Version to verify; defaults to the most recently published core version")
    parser.add_argument("--require-signatures", action="store_true", help="Require signatures; does not cryptographically verify them")
    arguments = parser.parse_args()
    verify(arguments.repository, arguments.version, arguments.require_signatures)
