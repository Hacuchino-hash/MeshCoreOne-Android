"""AndroidOnly: WP-207 Bounded read-only-token hosted generation of an owned lock DATA proposal."""

import argparse
from dataclasses import asdict
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.ci import execution_identity, execute, preflight
from controller.ci_environment import candidate_environment, file_sha256, toolchain_lock, write_json
from controller.errors import PortError
from controller.gates import Binding, policy_revision
from controller.model import git, load_manifest
from controller.schema import load_json

OWNER_LOCK = "android/core/runtime/gradle.lockfile"
BASE_LOCK = "android/gradle/dependency-locks/core-runtime.lockfile"
BRANCH = "cbattlegear-connection-runtime-ownership"
MANIFEST = "ceb84b5e26fcc9ece5c0b3fb6c68b4d2965f9f24114fa81b7434ff73d1ed7904"
POLICY = "45df0b4e40b210780e072e58fd935cb0d5abf2d29341703b4ac486c9596b56e0"
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
NEW_COORDINATES = {
    "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2",
    "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.2",
}
TEST_CONFIGURATIONS = {"testCompileClasspath", "testRuntimeClasspath"}
VERIFICATION_NS = {"v": "https://schema.gradle.org/dependency-verification"}


def require(condition, message):
    if not condition:
        raise PortError(message)


def parse_lock(text):
    coordinates, empty = {}, set()
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        require(line.count("=") == 1, "Malformed owned lock row")
        coordinate, raw_configs = line.split("=")
        configs = raw_configs.split(",")
        require(all(re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", item) for item in configs), "Invalid lock configuration")
        require(len(configs) == len(set(configs)), "Duplicate lock configuration")
        if coordinate == "empty":
            require(not empty, "Duplicate empty lock declaration")
            empty = set(configs)
            continue
        require(re.fullmatch(r"[\w.-]+:[\w.-]+:[\w.+-]+", coordinate), "Malformed dependency coordinate")
        require(coordinate not in coordinates, "Duplicate lock coordinate")
        coordinates[coordinate] = set(configs)
    require(coordinates and empty, "Missing/zero owned dependency lock")
    require(not empty.intersection(set().union(*coordinates.values())), "Configuration both empty and populated")
    return coordinates, empty


def validate_delta(before_text, after_text, verification):
    before, empty_before = parse_lock(before_text)
    after, empty_after = parse_lock(after_text)
    require(empty_before == empty_after, "Unapproved empty configuration change")
    require(set(before).issubset(after), "Removed admitted dependency")
    for coordinate, configurations in before.items():
        require(configurations == after[coordinate], "Changed admitted coordinate/configurations: " + coordinate)
    new = set(after) - set(before)
    require(new.issubset(NEW_COORDINATES), "Unapproved new dependency/version")
    metadata = ET.fromstring(verification)
    require(metadata.tag == "{https://schema.gradle.org/dependency-verification}verification-metadata",
            "Unknown Gradle verification metadata namespace")
    require(metadata.findtext("v:configuration/v:verify-metadata", namespaces=VERIFICATION_NS) == "true",
            "Artifact metadata verification must stay enabled")
    admitted = set()
    for component in metadata.findall("v:components/v:component", VERIFICATION_NS):
        coordinates = f"{component.get('group')}:{component.get('name')}:{component.get('version')}"
        checksums = component.findall("v:artifact/v:sha256", VERIFICATION_NS)
        require(all(re.fullmatch(r"[0-9a-f]{64}", item.get("value", "")) for item in checksums),
                "Malformed artifact checksum")
        if checksums:
            require(coordinates not in admitted, "Duplicate admitted metadata coordinate")
            admitted.add(coordinates)
    require(new.issubset(admitted), "New coordinate is not already checksum admitted")
    for coordinate in new:
        require(after[coordinate].issubset(TEST_CONFIGURATIONS), "New dependency leaked outside owned tests")
    return [{"coordinate": coordinate, "configurations": sorted(after[coordinate])} for coordinate in sorted(new)]


def validate_identity(event, environment, checkout):
    require(environment.get("GITHUB_EVENT_NAME") == "pull_request", "Only ordinary PR dependency proposals are authorized")
    require(environment.get("GITHUB_REPOSITORY") == "cbattlegear/MeshCoreOne-Android", "Unexpected proposal repository")
    pr = event.get("pull_request")
    require(isinstance(pr, dict), "Missing PR identity")
    require(pr.get("title", "").startswith("[WP-207] "), "Wrong bounded WP title")
    require(pr.get("head", {}).get("ref") == BRANCH, "Wrong owning branch")
    for side in ("head", "base"):
        require(pr.get(side, {}).get("repo", {}).get("full_name") == "cbattlegear/MeshCoreOne-Android", "Fork/foreign proposal is not authorized")
        require(re.fullmatch(r"[0-9a-f]{40}", pr.get(side, {}).get("sha", "")), "Missing full base/head")
    require(pr["head"]["sha"] == checkout, "Checkout is not exact proposal head")
    for name in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"):
        require(re.fullmatch(r"[1-9][0-9]*", environment.get(name, "")), "Missing positive run identity")
    return pr


def snapshot_inputs():
    paths = git(ROOT, "ls-files", "-z").split(b"\0")
    return {raw.decode(): {"git_blob": hashlib.sha1(
        b"blob " + str(len(data := ROOT.joinpath(*raw.decode().split("/")).read_bytes())).encode() + b"\0" + data,
    ).hexdigest(), "sha256": hashlib.sha256(data).hexdigest()}
        for raw in paths if raw and raw.decode() != OWNER_LOCK}


def proposal(state_path, output):
    require(platform.system() == "Linux" and platform.machine() == "x86_64", "Only the admitted ephemeral Linux host is authorized")
    require(platform.python_version() == "3.12.4", "Wrong pinned proposal Python")
    require(output.is_absolute() and not output.resolve().is_relative_to(ROOT), "Proposal must be an isolated DATA output")
    require(not output.exists() or not any(output.iterdir()), "Proposal output must start empty")
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    require(event_path is not None, "Unknown workflow identity")
    checkout = git(ROOT, "rev-parse", "HEAD").decode().strip()
    pr = validate_identity(load_json(Path(event_path)), dict(os.environ), checkout)
    actual = execution_identity()
    require(actual is not None, "Missing actual CI execution identity")
    manifest = load_manifest(ROOT)
    policy = load_json(ROOT / "docs" / "android" / "automation-policy.json")
    require(manifest.sha256 == MANIFEST and policy_revision(manifest, policy) == POLICY, "Trusted semantic policy drift")
    require(manifest.data["reference"]["commit"] == SOURCE, "Frozen source changed")
    binding = asdict(Binding(
        "cbattlegear/MeshCoreOne-Android", "WP-207", pr["base"]["sha"], checkout, SOURCE, MANIFEST, POLICY,
    ))
    state = load_json(state_path)
    output.mkdir(parents=True, exist_ok=True)
    before = snapshot_inputs()
    base_text = (ROOT / BASE_LOCK).read_text(encoding="utf-8")
    existing = ROOT / OWNER_LOCK
    if existing.exists():
        validate_delta(base_text, existing.read_text(encoding="utf-8"),
                       (ROOT / "android/gradle/verification-metadata.xml").read_bytes())
    preflight(state, output)
    environment = candidate_environment(state)
    private = Path(state["private_root"]) / "project-runtime-proposal"
    require(not private.exists(), "Proposal requires a fresh private project cache")
    command = [
        str(ROOT / "android/gradlew"), "-p", str(ROOT / "android"),
        ":core:runtime:resolveRuntimeDependencies",
        "--write-locks", "--dependency-verification", "strict", "--no-daemon", "--no-build-cache",
        "--max-workers=1", "--project-cache-dir", str(private), "--console=plain", "--quiet",
        "-Pkotlin.compiler.execution.strategy=in-process",
    ]
    execute(command, environment, output / "gradle-proposal.log", timeout=900)
    require(snapshot_inputs() == before, "Dependency generation modified unowned tracked inputs")
    changes = git(ROOT, "status", "--porcelain=v1", "--untracked-files=all").decode().splitlines()
    require(all(line[3:] == OWNER_LOCK for line in changes), "Unexpected generation write outside owned lock")
    require(existing.is_file() and not existing.is_symlink(), "Missing/linked owned lock output")
    delta = validate_delta(base_text, existing.read_text(encoding="utf-8"),
                           (ROOT / "android/gradle/verification-metadata.xml").read_bytes())
    graph = ROOT / "android/core/runtime/build/reports/wp207/dependency-graphs.tsv"
    require(graph.is_file() and graph.stat().st_size > 0, "Missing actual owned resolution graph")
    rows = graph.read_text(encoding="utf-8").splitlines()
    require(rows[0] == "module\tconfiguration\tcomponent" and len(rows) > 1, "Malformed/zero graph")
    require(all(row.split("\t", 1)[0] == ":core:runtime" for row in rows[1:]), "Foreign module resolution")
    shutil.copyfile(existing, output / "gradle.lockfile")
    shutil.copyfile(graph, output / "dependency-graphs.tsv")
    write_json(output / "binding.json", {
        "schema_version": 1, "binding": binding, "run_id": actual["run_id"], "run_attempt": actual["run_attempt"],
        "scope": "Owned dependency lock DATA proposal only; no source assertions, native cases or gate acceptance",
        "command": command, "inputs": before, "delta": delta,
        "artifacts": {name: {"sha256": file_sha256(output / name), "size_bytes": (output / name).stat().st_size}
                      for name in ("gradle.lockfile", "dependency-graphs.tsv", "gradle-proposal.log")},
    })
    print(json.dumps({"scope": "WP-207 dependency DATA proposal, not test/acceptance", "delta": delta}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        proposal(args.state, args.output)
    except (PortError, OSError, ValueError, ET.ParseError, subprocess.SubprocessError) as failure:
        print("BLOCKED: " + str(failure), file=sys.stderr)
        raise SystemExit(1) from failure


if __name__ == "__main__":
    main()
