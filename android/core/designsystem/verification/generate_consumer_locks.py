# AndroidOnly: WP-301 Auxiliary ephemeral generation of the exact admitted consumer lock proposal.
from __future__ import annotations

import argparse
from dataclasses import asdict
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
sys.path.insert(0, str(ROOT / "docs" / "android" / "evidence" / "WP-301"))
from controller import ci
from controller.ci_environment import candidate_environment, file_sha256, toolchain_lock, verify_wrapper, write_json
from controller.errors import PortError
from controller.gates import Binding, policy_revision
from controller.model import git, load_manifest
from controller.provision import provision
from controller.runtime_inputs import verify_committed_inputs
from controller.workflows import parse_yaml, validate_boundary
import verify_consumer_locks as locks

WORKFLOW = ROOT / ".github" / "workflows" / "android-theme-dependency-generation.yml"
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
MANIFEST = "ceb84b5e26fcc9ece5c0b3fb6c68b4d2965f9f24114fa81b7434ff73d1ed7904"
POLICY = "45df0b4e40b210780e072e58fd935cb0d5abf2d29341703b4ac486c9596b56e0"
TASK = ":core:designsystem:resolveAdmittedThemeConsumerGraphs"


def require(condition, message):
    if not condition:
        raise PortError("WP-301 generated lock proposal: " + message)


def validate_workflow(text):
    value = parse_yaml(text)
    validate_boundary(value, text)
    require(set(value.get("on", {})) == {"pull_request"}, "only the approved ordinary candidate event is allowed")
    require(set(value.get("jobs", {})) == {"generate"}, "exactly one auxiliary job is required")
    job = value["jobs"]["generate"]
    require(job.get("runs-on") == "ubuntu-24.04" and job.get("timeout-minutes") == "20", "changed runner or budget")
    require(job.get("if") == "${{ github.event.pull_request.number == 24 && startsWith(github.event.pull_request.title, '[WP-301]') }}",
        "changed authorized PR scope")
    require(job.get("name") == "WP-301 generated lock proposal", "auxiliary cannot impersonate mandatory CI")
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
        "python android/core/designsystem/verification/generate_consumer_locks.py --self-test --workflow-check",
        'python android/core/designsystem/verification/generate_consumer_locks.py --run --root "${{ runner.temp }}/wp301-generation"',
    ], "missing or modified exact helper validation/generation commands")
    upload = next(step for step in steps if step.get("uses", "").startswith("actions/upload-artifact@"))
    require(upload["with"]["path"] == "${{ runner.temp }}/wp301-generation/proposal"
        and upload["with"]["if-no-files-found"] == "error", "unbounded or success-shaped upload")
    return {"result": "valid-auxiliary-candidate-boundary", "mandatory_ci_replaced": False}


def identity():
    require(os.environ.get("GITHUB_EVENT_NAME") == "pull_request", "unknown execution event")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf8"))
    pr = event["pull_request"]
    require(event["number"] == 24 and pr["state"] == "open" and not pr.get("merged", False)
        and pr["base"]["ref"] == "main" and pr["title"].startswith("[WP-301]")
        and pr["head"]["ref"] == "cbattlegear-native-themes-and-identity"
        and pr["head"]["repo"]["full_name"] == "cbattlegear/MeshCoreOne-Android", "unapproved candidate identity")
    current = ci.execution_identity()
    require(current is not None, "missing immutable run identity")
    manifest = load_manifest(ROOT)
    policy = json.loads((ROOT / "docs" / "android" / "automation-policy.json").read_text(encoding="utf8"))
    binding = dict(current["binding"], work_package="WP-301")
    subprocess.run(["git", "-C", str(ROOT), "merge-base", "--is-ancestor", binding["base_sha"],
        binding["head_sha"]], check=True, capture_output=True)
    require(binding["source_sha"] == SOURCE and manifest.sha256 == MANIFEST
        and binding["policy_revision"] == POLICY and policy_revision(manifest, policy) == POLICY, "changed source/manifest/policy")
    require(git(ROOT, "rev-parse", SOURCE + "^{tree}").decode().strip() == "8918fdc604341e6996a68c88f6bb1c02b9c2f87e",
        "changed frozen source tree")
    return {"binding": asdict(Binding.parse(binding)), "run_id": current["run_id"], "run_attempt": current["run_attempt"]}


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
    verify_committed_inputs(ROOT)
    validate_workflow(WORKFLOW.read_text(encoding="utf8"))
    verify_wrapper()
    before = set(git(ROOT, "diff", "--name-only", "HEAD").decode().splitlines())
    require(not before and not git(ROOT, "ls-files", "--others", "--exclude-standard").strip(),
        "candidate checkout must be clean before actual generation")
    proposal = root / "proposal"
    proposal.mkdir(parents=True)
    record = {"schema_version": 1, "work_package": "WP-301", "scope": "Actual generated lock bytes as a data proposal; no automatic persistence, suite, device, gate or merge acceptance.",
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
        graph = ROOT / "android" / "core" / "designsystem" / "build" / "reports" / "wp301" / "admitted-consumer-graphs.tsv"
        require(graph.is_file() and graph.stat().st_size > 0, "actual selected graph is missing")
        shutil.copyfile(graph, proposal / "admitted-consumer-graphs.tsv")
        record["generated_locks"] = records
        record["input_blobs"] = {
            path: git(ROOT, "rev-parse", bound["binding"]["head_sha"] + ":" + path).decode().strip()
            for path in ("android/core/designsystem/build.gradle.kts", "android/gradle/verification-metadata.xml",
                "android/gradle/libs.versions.toml", ".github/workflows/android-theme-dependency-generation.yml")
        }
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
            path = Path(__file__).with_name("test_generate_consumer_locks.py")
            spec = importlib.util.spec_from_file_location("wp301_generation_tests", path)
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
