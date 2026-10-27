#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
mkdir -p "$repo_dir/build"
run_dir=$(mktemp -d "$repo_dir/build/ci-podman.XXXXXX")
workspace="$run_dir/workspace"

# Copy tracked working-tree files, including edits, without host caches or credentials.
python3 - "$repo_dir" "$workspace" <<'PY'
from pathlib import Path
import shutil
import subprocess
import sys

source, destination = map(Path, sys.argv[1:])
tracked = subprocess.check_output(["git", "-C", str(source), "ls-files", "-z"]).decode().split("\0")
for relative in set(tracked) | {"tools/ci/Containerfile", "tools/ci/run.sh"}:
    if not relative:
        continue
    path = source / relative
    if not path.is_file():
        continue
    target = destination / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, target)
PY

image=localhost/jaspring-ci:local
build_args=()
if [[ -n "${GRAALVM_URL:-}" ]]; then
    build_args+=(--build-arg "GRAALVM_URL=$GRAALVM_URL")
fi
podman build "${build_args[@]}" -t "$image" -f "$repo_dir/tools/ci/Containerfile" "$repo_dir/tools/ci"

printf 'Build snapshot and reports: %s\n' "$workspace"
podman run --rm \
    -v "$workspace:/workspace:Z" \
    -v jaspring-ci-repro-gradle:/gradle-cache \
    "$image" bash -c '
        set -euo pipefail
        java -version
        if (( $# )); then
            ./gradlew "$@" --no-daemon --console=plain
        else
            ./gradlew build publishAllPublicationsToBuildRepository --no-daemon --console=plain
            python3 tools/verify-publications.py
            PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools -p "test_update_readme_versions.py"
        fi
    ' -- "$@"
