"""Keep Jaspring installation snippets aligned with an explicit release version."""

import argparse
from pathlib import Path
import re


README_PATHS = ("README.md", "jaspring-gradle-plugin/README.md")
VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?")
PATTERNS = (
    re.compile(r'(dev\.isira:jaspring-(?:core|spring-boot-starter|gradle-plugin):)[^\s"\x27`]+'),
    re.compile(r'(id\("dev\.isira\.jaspring"\)\s+version\s+")[^"]+'),
    re.compile(r'(<dependency>\s*<groupId>dev\.isira</groupId>\s*'
               r'<artifactId>jaspring-(?:core|spring-boot-starter|gradle-plugin)</artifactId>\s*'
               r'<version>)[^<]+'),
)


def rewrite_versions(text, version):
    if not VERSION.fullmatch(version) or version.endswith("-SNAPSHOT"):
        raise ValueError("Provide a release version such as 0.1.0 or 0.2.0-rc.1, without -SNAPSHOT")
    count = 0
    for pattern in PATTERNS:
        text, replacements = pattern.subn(lambda match: match[1] + version, text)
        count += replacements
    if count == 0:
        raise ValueError("No Jaspring installation versions found; check the README snippet format")
    return text


def update(root, version, check=False):
    # Read and validate every document before changing any of them.
    changes = []
    for relative in README_PATHS:
        path = root / relative
        original = path.read_text(encoding="utf-8")
        updated = rewrite_versions(original, version)
        if original != updated:
            changes.append((path, updated))
    if check and changes:
        raise ValueError("README versions are stale. Run ./gradlew updateReadmeVersions "
                         f"-PreleaseVersion={version} and commit the updated READMEs before publishing.")
    for path, updated in changes:
        path.write_text(updated, encoding="utf-8")
        print(f"Updated {path.relative_to(root)} to {version}")
    if not changes:
        print(f"README installation versions already match {version}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--check", action="store_true", help="Fail on stale versions without modifying files")
    args = parser.parse_args()
    try:
        update(args.root, args.version, args.check)
    except (ValueError, OSError) as error:
        parser.exit(1, f"{error}\n")
