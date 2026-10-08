#!/usr/bin/env bash
# AndroidOnly: WP-003 Serialized Linux snapshots with persistent Gradle caches.
set -euo pipefail
bundle="$1"
revision="$2"
key="$3"
tool="$4"
stages="$5"
overlay="$6"
work="${ANDROID_LOCAL_HOME:-$HOME/meshcoreone-work}"
mkdir -p "$work/local-checks"
echo "Waiting for shared local Gradle lane (other workstreams may be running)..."
exec 9>"$work/local-checks/gradle.lock"
flock 9
repo="$work/local-checks/repos/$key"
# Import from ext4 rather than repeatedly seeking through the Windows mount.
cp "$bundle" "$work/local-checks/input.bundle"
bundle="$work/local-checks/input.bundle"
if [[ ! -e "$repo" ]]; then
  git clone --no-hardlinks "$bundle" "$repo"
  mkdir "$repo/.git/meshcore-local-snapshot"
fi
[[ -d "$repo/.git/meshcore-local-snapshot" ]] || {
  echo "BLOCKED: refusing to overwrite an unmanaged snapshot directory: $repo" >&2
  exit 1
}
if ! git -C "$repo" cat-file -e "$revision^{commit}" 2>/dev/null; then
  echo "Importing new local Git objects..."
  git -C "$repo" fetch --quiet "$bundle" '+refs/*:refs/local-input/*'
fi
git -C "$repo" checkout --detach --force "$revision"
py="${ANDROID_LOCAL_PYTHON:-$work/venv312/bin/python}"
[[ -x "$py" ]] || { echo "BLOCKED: provision pinned Python at $py" >&2; exit 1; }
if [[ -n "$overlay" ]]; then
  "$py" - "$repo" "$overlay" <<'PY'
import json
from pathlib import Path
import sys
import tarfile
repo, overlay = map(Path, sys.argv[1:])
with tarfile.open(overlay / "working-tree.tar") as archive:
    archive.extractall(repo, filter="data")
for name in json.loads((overlay / "deleted.json").read_text(encoding="utf-8")):
    (repo / name).unlink()
PY
  # Match Git for Windows' clean conversion; CRLF checkout bytes must not become
  # synthetic changes to the read-only Swift pin.
  git -C "$repo" -c core.autocrlf=input add --all
  git -C "$repo" -c user.name=LocalChecks -c user.email=local-checks@localhost \
    -c core.hooksPath=/dev/null -c commit.gpgsign=false commit --allow-empty -m "Local working-tree snapshot"
fi
# Windows checkouts can contain CRLF. This changes only the generated Linux copy.
sed -i 's/\r$//' "$repo/android/gradlew"
chmod +x "$repo/android/gradlew"
output="$(mktemp -d "$work/local-checks/run-XXXXXXXX")"
echo "Local reports: $output"
"$py" "$tool/fast.py" --repo "$repo" --work "$work" --output "$output" --stages "$stages"
