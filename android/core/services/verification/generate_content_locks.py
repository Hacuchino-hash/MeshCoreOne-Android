# AndroidOnly: WP-218 Auxiliary ephemeral generation of the exact admitted content lock proposal.
from __future__ import annotations

import argparse
from dataclasses import asdict
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
sys.path.insert(0, str(ROOT / "docs" / "android" / "evidence" / "WP-218"))
from controller import ci
from controller.ci_environment import candidate_environment, file_sha256, toolchain_lock, verify_wrapper, write_json
from controller.errors import PortError
from controller.gates import Binding, policy_revision
from controller.model import git, load_manifest
from controller.provision import provision
from controller.runtime_inputs import verify_committed_inputs
from controller.workflows import parse_yaml, validate_boundary
import verify_content_locks as locks

WORKFLOW = ROOT / ".github" / "workflows" / "android-content-dependency-generation.yml"
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
MANIFEST = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
POLICY = "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"
TASK = ":core:services:resolveContentDependencies"
GRAPH_DIRECTORY = ROOT / "android" / "core" / "services" / "build" / "wp218" / "content-dependencies"
# Owned WP-218 helper/test/evidence paths that bind this exact generation run to the exact
# committed bytes it is executing from -- never an unrelated/stale cached copy.
OWNED_INPUT_PATHS = (
    "android/core/services/verification/generate_content_locks.py",
    "android/core/services/verification/test_generate_content_locks.py",
    "docs/android/evidence/WP-218/verify_content_locks.py",
    "docs/android/evidence/WP-218/test_content_locks.py",
    "docs/android/evidence/WP-218/dependency-amendment-request.json",
)
# Convention-plugin source that configures core:services (mesh.jvm.library); not already covered
# by controller.runtime_inputs.FIXED_INPUTS, so it is bound here instead of amending that shared
# WP-003 module.
CONVENTION_INPUT_PATHS = (
    "android/build-logic/convention/build.gradle.kts",
    "android/build-logic/convention/src/main/kotlin/com/meshcoreone/buildlogic/BuildConventions.kt",
)


def require(condition, message):
    if not condition:
        raise PortError("WP-218 generated lock proposal: " + message)


def validate_workflow(text):
    value = parse_yaml(text)
    validate_boundary(value, text)
    require(set(value.get("on", {})) == {"pull_request"}, "only the approved ordinary candidate event is allowed")
    require(set(value.get("jobs", {})) == {"generate"}, "exactly one auxiliary job is required")
    job = value["jobs"]["generate"]
    require(job.get("runs-on") == "ubuntu-24.04" and job.get("timeout-minutes") == "20", "changed runner or budget")
    require(job.get("if") == "${{ github.event.pull_request.number == 25 && startsWith(github.event.pull_request.title, '[WP-218]') }}",
        "changed authorized PR scope")
    require(job.get("name") == "WP-218 generated lock proposal", "auxiliary cannot impersonate mandatory CI")
    steps = job["steps"]
    require(len(steps) == 6, "extra or missing auxiliary step")
    checkout = next(step for step in steps if step.get("uses", "").startswith("actions/checkout@"))
    require(checkout["with"].get("ref") == "${{ github.event.pull_request.head.sha }}"
        and checkout["with"].get("fetch-depth") == "0", "candidate checkout is not exact and complete")
    setup = next(step for step in steps if step.get("uses", "").startswith("actions/setup-python@"))
    require(setup["with"] == {"python-version": "3.12.4", "architecture": "x64"}, "changed Python input")
    runs = [step.get("run", "") for step in steps if "run" in step]
    require(len(runs) == 3 and not any("if" in step for step in steps if "run" in step), "conditional or extra candidate action")
    require(runs == [
        "python -m pip install --disable-pip-version-check --require-hashes --no-deps --only-binary=:all: "
            "-r tools/android-port/controller/requirements-ci.txt --quiet",
        "python android/core/services/verification/generate_content_locks.py --self-test --workflow-check",
        'python android/core/services/verification/generate_content_locks.py --run --root "${{ runner.temp }}/wp218-generation"',
    ], "missing or modified exact helper validation/generation commands")
    upload = next(step for step in steps if step.get("uses", "").startswith("actions/upload-artifact@"))
    require(upload["with"]["path"] == "${{ runner.temp }}/wp218-generation/proposal"
        and upload["with"]["if-no-files-found"] == "error", "unbounded or success-shaped upload")
    return {"result": "valid-auxiliary-candidate-boundary", "mandatory_ci_replaced": False}


def identity():
    require(os.environ.get("GITHUB_EVENT_NAME") == "pull_request", "unknown execution event")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf8"))
    pr = event["pull_request"]
    require(event["number"] == 25 and pr["state"] == "open" and not pr.get("merged", False)
        and pr["base"]["ref"] == "main" and pr["title"].startswith("[WP-218]")
        and pr["head"]["ref"] == "cbattlegear-bookish-funicular"
        and pr["head"]["repo"]["full_name"] == "cbattlegear/MeshCoreOne-Android", "unapproved candidate identity")
    current = ci.execution_identity()
    require(current is not None, "missing immutable run identity")
    manifest = load_manifest(ROOT)
    policy = json.loads((ROOT / "docs" / "android" / "automation-policy.json").read_text(encoding="utf8"))
    binding = dict(current["binding"], work_package="WP-218")
    subprocess.run(["git", "-C", str(ROOT), "merge-base", "--is-ancestor", binding["base_sha"],
        binding["head_sha"]], check=True, capture_output=True)
    require(binding["source_sha"] == SOURCE and manifest.sha256 == MANIFEST
        and binding["policy_revision"] == POLICY and policy_revision(manifest, policy) == POLICY, "changed source/manifest/policy")
    require(git(ROOT, "rev-parse", SOURCE + "^{tree}").decode().strip() == "8918fdc604341e6996a68c88f6bb1c02b9c2f87e",
        "changed frozen source tree")
    return {"binding": asdict(Binding.parse(binding)), "run_id": current["run_id"], "run_attempt": current["run_attempt"]}


def verify_local_identity(bound):
    head = bound["binding"]["head_sha"]
    # The lock-admission baseline (verify_content_locks.BASE) must be an actual ancestor of this
    # candidate head -- never an unrelated/newer commit silently substituted as "the" baseline.
    subprocess.run(["git", "-C", str(ROOT), "merge-base", "--is-ancestor", locks.BASE, head],
        check=True, capture_output=True)
    for relative in OWNED_INPUT_PATHS:
        committed = git(ROOT, "cat-file", "blob", f"{head}:{relative}")
        local = (ROOT / relative).read_bytes()
        require(hashlib.sha256(local).digest() == hashlib.sha256(committed).digest(),
            "stale or tampered owned input differs from the committed candidate tree: " + relative)


def collect_dependency_graph(proposal):
    # GRAPH_DIRECTORY is the single fixed location the resolveContentDependencies Gradle task
    # writes to (see android/core/services/build.gradle.kts); never read from anywhere else.
    destination = proposal / "graph"
    destination.mkdir()
    records = []
    for name in sorted(locks.CONFIGURATIONS):
        origin = GRAPH_DIRECTORY / f"{name}.tsv"
        require(origin.is_file(), "missing actual resolved dependency graph for configuration: " + name)
        rows = [line for line in origin.read_text(encoding="utf8").splitlines() if line.strip()]
        require(len(rows) > 1, "empty actual resolved dependency graph for configuration: " + name)
        require(not any(row.split("\t")[-1].startswith("unresolved:") for row in rows[1:]),
            "unresolved component present in the actual dependency graph for configuration: " + name)
        target = destination / origin.name
        shutil.copyfile(origin, target)
        require(origin.read_bytes() == target.read_bytes(), "proposal graph bytes differ from the actual resolved graph")
        records.append({"configuration": name, "artifact_path": "graph/" + target.name,
            "bytes": target.stat().st_size, "sha256": file_sha256(target), "rows": len(rows) - 1})
    require({record["configuration"] for record in records} == locks.CONFIGURATIONS,
        "missing admitted configuration in the actual resolved dependency graph")
    return records


def command(state):
    require(state["host"] == "linux", "generation is bounded to the single Linux runner")
    return [str(ROOT / "android" / "gradlew"), "-p", str(ROOT / "android"), TASK,
        "--write-locks", "--dependency-verification", "strict", "--no-daemon", "--console=plain",
        "--max-workers=1", "-Pkotlin.compiler.execution.strategy=in-process", "-PscaffoldTestHeap=256m",
        "--project-cache-dir", str(Path(state["private_root"]) / "project-root"), "--quiet"]


def check_changed_paths(before):
    after = set(git(ROOT, "diff", "--name-only", "HEAD").decode().splitlines())
    admitted = {f"android/gradle/dependency-locks/{name.removeprefix(':').replace(':', '-')}.lockfile"
        for name in locks.MODULES}
    require(not before and after <= admitted, "unknown tracked-file write")
    require(not git(ROOT, "ls-files", "--others", "--exclude-standard").strip(), "unknown untracked candidate write")
    return sorted(after)


def verified_resolution(before):
    changed = check_changed_paths(before)
    return changed, locks.check()


def run(root):
    require(platform.system() == "Linux" and platform.machine() == "x86_64", "wrong isolated execution host")
    require(platform.python_version() == toolchain_lock()["python"], "wrong pinned Python runtime")
    require(root.is_absolute() and not root.exists() and not root.resolve().is_relative_to(ROOT.resolve())
        and not ROOT.resolve().is_relative_to(root.resolve()), "root must be new, bounded and external")
    bound = identity()
    verify_local_identity(bound)
    verify_committed_inputs(ROOT)
    validate_workflow(WORKFLOW.read_text(encoding="utf8"))
    verify_wrapper()
    before = set(git(ROOT, "diff", "--name-only", "HEAD").decode().splitlines())
    require(not before and not git(ROOT, "ls-files", "--others", "--exclude-standard").strip(),
        "candidate checkout must be clean before actual generation")
    proposal = root / "proposal"
    proposal.mkdir(parents=True)
    record = {"schema_version": 1, "work_package": "WP-218", "scope": "Actual generated lock bytes as a data proposal; no automatic persistence, suite, device, gate or merge acceptance.",
        **bound, "host": "linux", "result": "blocked-before-resolution"}
    try:
        state = provision(root / "toolchain", accept_sdk_license=True)
        record["toolchain_provenance"] = state["provenance"]
        environment = candidate_environment(state)
        environment["JAVA_OPTS"] = "-Xms32m -Xmx128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"
        environment["GRADLE_OPTS"] = '-Dorg.gradle.jvmargs="-Xms64m -Xmx512m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"'
        ci.execute([sys.executable, str(ROOT / "android" / "scaffold" / "check_environment.py")],
            environment, proposal / "preflight.log", timeout=60)
        actual = command(state)
        record["command"] = actual
        ci.execute(actual, environment, proposal / "resolver.log", timeout=900)
        record["dependency_graph"] = collect_dependency_graph(proposal)
        record["changed_paths"], delta = verified_resolution(before)
        record["delta"] = delta
        records = []
        destination = proposal / "locks"
        destination.mkdir()
        for item in delta["locks"]:
            source = ROOT.joinpath(*PurePosixPath(item["path"]).parts)
            target = destination / source.name
            shutil.copyfile(source, target)
            require(source.read_bytes() == target.read_bytes(), "proposal bytes differ from actual generated lock")
            records.append({"path": item["path"], "artifact_path": "locks/" + target.name,
                "bytes": target.stat().st_size, "sha256": file_sha256(target)})
        record["generated_locks"] = records
        record["input_blobs"] = {
            path: git(ROOT, "rev-parse", bound["binding"]["head_sha"] + ":" + path).decode().strip()
            for path in ("android/core/services/build.gradle.kts", "android/gradle/verification-metadata.xml",
                "android/gradle/libs.versions.toml", ".github/workflows/android-content-dependency-generation.yml",
                "tools/android-port/controller/toolchain-pins.json", "android/gradle/wrapper/gradle-wrapper.properties",
                "android/scaffold/check_environment.py", *OWNED_INPUT_PATHS, *CONVENTION_INPUT_PATHS)
        }
        initial_lock_path = "android/gradle/dependency-locks/core-services.lockfile"
        record["initial_lock_blob"] = {"path": initial_lock_path, "base_sha": locks.BASE,
            "blob": git(ROOT, "rev-parse", locks.BASE + ":" + initial_lock_path).decode().strip()}
        record["result"] = "actual-generated-byte-proposal"
    except (PortError, ValueError, OSError, KeyError, subprocess.SubprocessError) as error:
        record["error"] = str(error)
        raise
    finally:
        write_json(proposal / "generation.json", record)
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--workflow-check", action="store_true")
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--root", type=Path)
    args = parser.parse_args()
    try:
        require(args.self_test or args.workflow_check or args.run, "explicit bounded mode required")
        if args.self_test:
            path = Path(__file__).with_name("test_generate_content_locks.py")
            spec = importlib.util.spec_from_file_location("wp218_generation_tests", path)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            suite = unittest.defaultTestLoader.loadTestsFromModule(module)
            require(suite.countTestCases() > 0, "zero helper cases")
            tested = unittest.TextTestRunner(verbosity=1).run(suite)
            require(tested.wasSuccessful() and not tested.skipped, "failed/skipped helper cases")
        if args.workflow_check:
            print(json.dumps(validate_workflow(WORKFLOW.read_text(encoding="utf8")), sort_keys=True))
        if args.run:
            require(args.root is not None, "explicit root required")
            run(args.root)
        return 0
    except (PortError, ValueError, OSError, KeyError, StopIteration, subprocess.SubprocessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
