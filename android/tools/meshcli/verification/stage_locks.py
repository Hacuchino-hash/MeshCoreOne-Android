"""AndroidOnly: WP-109 Stage unchanged Gradle-generated baseline locks into the owned module before regeneration."""

import hashlib
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]
MODULE = ROOT / "android" / "tools" / "meshcli"
INITIAL = "3da3a73b8481c49d035486924a813b331bf184d0"

for source, name in (
    ("android/gradle/dependency-locks/tools-meshcli.lockfile", "meshcli.lockfile"),
    ("android/gradle/dependency-locks/tools-meshcli-buildscript.lockfile", "meshcli-buildscript.lockfile"),
):
    data = subprocess.check_output(["git", "-C", str(ROOT), "show", f"{INITIAL}:{source}"])
    target = MODULE / "gradle" / "dependency-locks" / name
    if target.exists():
        if target.read_bytes().replace(b"\r\n", b"\n") != data:
            raise SystemExit("BLOCKED: existing owned lock differs; use the declared scoped Gradle generator")
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print(name, hashlib.sha256(data).hexdigest(), "unchanged generated initial lock, no dependency/version amendment")
