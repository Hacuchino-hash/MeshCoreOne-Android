import fcntl
import json
import os
from pathlib import Path
import subprocess
import sys

work = Path("/home/cbattagler/meshcoreone-work")
repo = work / "local-checks/repos/ef5b4cfe42514537"
with (work / "local-checks/gradle.lock").open("w") as lane:
    fcntl.flock(lane, fcntl.LOCK_EX)
    if not (repo / ".git/meshcore-local-snapshot").is_dir():
        raise RuntimeError("Not the official managed snapshot")
    os.chdir(repo)
    subprocess.run(["git", "diff", "--exit-code", "HEAD"], check=True)
    sys.path.insert(0, str(repo / "tools/android-port"))
    from controller.ci_environment import candidate_environment, verify_wrapper
    state = json.loads((work / "toolchain/environment.json").read_text())
    state["private_root"] = str(work / "local-checks/private")
    environment = candidate_environment(state)
    environment["PATH"] = os.pathsep.join([
        str(Path(state["java_home"]) / "bin"), str(Path(sys.executable).parent),
        environment.get("PATH", ""),
    ])
    environment["GRADLE_USER_HOME"] = str(work / ".gradle-fast")
    environment["GRADLE_OPTS"] = '-Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8"'
    verify_wrapper()
    command = [
        str(repo / "android/gradlew"), "-p", str(repo / "android"),
        ":core:l10n:clean", "--dependency-verification", "strict",
        "--console=plain", "--no-parallel", "--max-workers=4",
    ]
    print("Exact scoped build-output invalidation, not test evidence:", command, flush=True)
    subprocess.run(command, env=environment, check=True)
    subprocess.run(["git", "diff", "--exit-code", "HEAD"], check=True)
    if (repo / "android/core/l10n/build").exists():
        raise RuntimeError("Localization build outputs were not cleaned")
