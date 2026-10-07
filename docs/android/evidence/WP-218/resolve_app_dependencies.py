"""AndroidOnly: WP-218 Actual local App-only admitted HTTP lock proposal, never a test receipt."""

import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.ci_environment import candidate_environment, file_sha256, toolchain_lock, verify_wrapper
from controller.errors import PortError
from controller.model import git
from controller.schema import decode_json

LOCK = "android/gradle/dependency-locks/app.lockfile"
TASK = ":app:resolveContentHttpDependencies"
UPDATE = (
    "com.squareup.okhttp3:okhttp", "com.squareup.okhttp3:okhttp-android",
    "com.squareup.okio:okio", "com.squareup.okio:okio-jvm",
    "androidx.annotation:annotation", "androidx.annotation:annotation-jvm",
    "androidx.startup:startup-runtime",
)
CONFIGURATIONS = {
    "debugCompileClasspath", "debugRuntimeClasspath", "debugUnitTestCompileClasspath",
    "debugUnitTestRuntimeClasspath", "debugAndroidTestCompileClasspath", "releaseCompileClasspath",
    "releaseRuntimeClasspath", "debugLintChecksClasspath", "debugUnitTestLintChecksClasspath",
    "debugAndroidTestLintChecksClasspath", "releaseLintChecksClasspath",
}
NEW_VERSIONS = {
    "com.squareup.okhttp3:okhttp": "5.5.0", "com.squareup.okhttp3:okhttp-android": "5.5.0",
    "com.squareup.okio:okio": "3.18.1", "com.squareup.okio:okio-jvm": "3.18.1",
    "androidx.annotation:annotation": "1.10.0", "androidx.annotation:annotation-jvm": "1.10.0",
    "androidx.startup:startup-runtime": "1.2.0",
}


def lock_rows(raw):
    rows = {}
    for line in raw.decode("utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        component, configurations = line.split("=", 1)
        if component in rows:
            raise PortError("Duplicate generated App lock component: " + component)
        rows[component] = set(configurations.split(",")) if configurations else set()
    return rows


def verify_delta(before, after):
    old, new = lock_rows(before), lock_rows(after)
    for component, configurations in new.items():
        if component in old and configurations == old[component]:
            continue
        incumbent_subset = component in old and configurations <= old[component]
        if component != "empty" and not incumbent_subset:
            group, name, version = component.split(":")
            if NEW_VERSIONS.get(group + ":" + name) != version:
                raise PortError("Unadmitted App HTTP lock component/version: " + component)
        previous = old.get(component, set())
        if (configurations ^ previous) - CONFIGURATIONS:
            raise PortError("App HTTP lock changed an unadmitted configuration: " + component)
    for component, configurations in old.items():
        if new.get(component) == configurations:
            continue
        if component == "empty":
            if (configurations ^ new.get(component, set())) - CONFIGURATIONS:
                raise PortError("App HTTP empty lock row changed an unadmitted configuration")
            continue
        group, name, _ = component.split(":")
        if group + ":" + name not in NEW_VERSIONS:
            raise PortError("App HTTP lock removed/changed an unrelated incumbent: " + component)
        if (configurations - new.get(component, set())) - CONFIGURATIONS:
            raise PortError("App HTTP version update escaped its admitted classpaths")
    if not any(component.startswith("com.squareup.okhttp3:okhttp-android:5.5.0") for component in new):
        raise PortError("Actual resolved Android OkHttp variant is missing")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        if platform.system() != "Linux" or platform.python_version() != toolchain_lock()["python"]:
            raise PortError("Only the approved actual Linux/Python toolchain can generate the App lock")
        if not args.output.is_absolute() or args.output.exists() or args.output.resolve().is_relative_to(ROOT):
            raise PortError("App dependency proposal output must be new, absolute and outside the snapshot")
        if git(ROOT, "status", "--porcelain").strip():
            raise PortError("App dependency generation requires the actual clean immutable snapshot")
        state = decode_json(args.state.read_text(encoding="utf-8"))
        environment = candidate_environment(state)
        environment["PATH"] = ":".join([
            str(Path(state["java_home"]) / "bin"), str(Path(sys.executable).parent), environment.get("PATH", ""),
        ])
        environment["GRADLE_USER_HOME"] = str(args.work / ".gradle-fast")
        verify_wrapper()
        before = (ROOT / LOCK).read_bytes()
        head = git(ROOT, "rev-parse", "HEAD").decode("ascii").strip()
        args.output.mkdir(parents=True)
        command = [
            str(ROOT / "android" / "gradlew"), "-p", str(ROOT / "android"), TASK,
            "--update-locks", ",".join(UPDATE), "--dependency-verification", "strict",
            "--console=plain", "--no-parallel", "--max-workers=4", "--build-cache",
        ]
        print("Executing actual App-only resolver:", " ".join(command), flush=True)
        with (args.output / "resolver.log").open("w", encoding="utf-8") as log:
            with subprocess.Popen(command, cwd=ROOT, env=environment, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True) as process:
                for line in process.stdout:
                    print(line, end="", flush=True)
                    log.write(line)
                code = process.wait()
        if code:
            raise PortError("Actual App HTTP resolver failed: " + str(code))
        after = (ROOT / LOCK).read_bytes()
        verify_delta(before, after)
        changed = git(ROOT, "diff", "--name-only", "HEAD").decode("utf-8").splitlines()
        if changed != [LOCK]:
            raise PortError("Actual resolver changed more than the one admitted App lock: " + repr(changed))
        shutil.copyfile(ROOT / LOCK, args.output / "app.lockfile")
        graph = ROOT / "android/app/build/reports/wp218/http-dependencies"
        if {path.stem for path in graph.glob("*.tsv")} != CONFIGURATIONS:
            raise PortError("Missing/extra actual resolved HTTP graph")
        shutil.copytree(graph, args.output / "graphs")
        (args.output / "generation.json").write_text(json.dumps({
            "schema_version": 1, "work_package": "WP-218", "head_sha": head,
            "tree_sha": git(ROOT, "rev-parse", head + "^{tree}").decode().strip(),
            "source_sha": "db14559b39d32322b06477c6ae676112f583db50",
            "command": command, "configurations": sorted(CONFIGURATIONS),
            "previous_sha256": hashlib.sha256(before).hexdigest(),
            "generated_sha256": file_sha256(args.output / "app.lockfile"),
            "scope": "Actual mechanically generated admitted App lock proposal; no test/legal/hardware acceptance.",
        }, indent=2) + "\n", encoding="utf-8")
        return 0
    except (PortError, OSError, ValueError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
