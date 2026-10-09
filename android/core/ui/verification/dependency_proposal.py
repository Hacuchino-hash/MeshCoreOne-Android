# AndroidOnly: WP-304 Exact ephemeral Linux owned-lock DATA proposal; raw evidence precedes validation and never auto-persists.
from __future__ import annotations

import argparse
from dataclasses import asdict
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller import ci
from controller.ci_environment import candidate_environment, file_sha256, toolchain_lock, verify_wrapper, write_json
from controller.errors import PortError
from controller.gates import Binding, policy_revision
from controller.model import git, load_manifest
from controller.provision import provision
from controller.runtime_inputs import verify_committed_inputs
from controller.schema import decode_json, load_json
from controller.workflows import parse_yaml, validate_boundary

REPOSITORY = "cbattlegear/MeshCoreOne-Android"
BRANCH = "cbattlegear-refactored-spoon"
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
SOURCE_TREE = "8918fdc604341e6996a68c88f6bb1c02b9c2f87e"
MANIFEST = "ceb84b5e26fcc9ece5c0b3fb6c68b4d2965f9f24114fa81b7434ff73d1ed7904"
POLICY = "f52513bf818fffb013758e9abe48023816976a39b9bce9331b1f317cd828bb7f"
OWNER_LOCK = "android/core/ui/gradle.lockfile"
ROOT_LOCK = "android/gradle/dependency-locks/core-ui.lockfile"
ROOT_LOCK_BLOB = "566089f945f40442b8c0980aabee409c1de2c6da"
ROOT_LOCK_SHA = "95055e812451d9906683f36ee3e46373dc5fe5424fa833f02163bb13f78f1c96"
SETTINGS_BOOKKEEPING = "android/settings-gradle.lockfile"
CONFIGURATION_NAME = re.compile(r"[A-Za-z][A-Za-z0-9_-]{0,127}")
WORKFLOW = ".github/workflows/android-shared-ui-dependency-generation.yml"
TASK = ":core:ui:resolveSharedUiDependencies"
GRAPH = "android/core/ui/build/reports/wp304/dependency-graphs.tsv"
CONFIGURATIONS = "android/core/ui/build/reports/wp304/resolution-configurations.txt"
ALIGNMENT = "android/core/ui/build/reports/wp304/unit-compile-alignment.tsv"
TEST_CONTEXT_INPUT = "android/core/ui/verification/test-context-memberships.json"
TEST_CONTEXT_SHA = "3f13f1854a310cfd58e54e1512b9e472a22697118a62331b2a296c12fbc6cec2"
TEST_CONTEXT_RECEIPT_SHA = "84a8153f2c8e0544051304dec244c8cf310e03b97296dc50aad4d947c8ccf65d"
COMPILE_ALIGNMENT = {
    "androidx.core:core": "1.16.0",
    "androidx.core:core-ktx": "1.16.0",
    "androidx.lifecycle:lifecycle-livedata-core": "2.9.4",
    "androidx.lifecycle:lifecycle-viewmodel": "2.9.4",
    "androidx.lifecycle:lifecycle-viewmodel-android": "2.9.4",
    "androidx.lifecycle:lifecycle-viewmodel-ktx": "2.9.4",
    "androidx.lifecycle:lifecycle-viewmodel-savedstate": "2.9.4",
}
VM = "-Xms64m -Xmx512m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"
REQUIRED_INPUTS = {
    WORKFLOW, "android/core/ui/verification/dependency_proposal.py",
    "android/core/ui/verification/test_dependency_proposal.py",
    "android/core/ui/build.gradle.kts", ROOT_LOCK, TEST_CONTEXT_INPUT,
    "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/DeviceSettingsFaults.kt",
    "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/MessagingFaults.kt",
    "docs/android/evidence/WP-304/collect_evidence.py",
    "docs/android/evidence/WP-304/source_inventory.py",
    "android/gradlew", "android/gradle/wrapper/gradle-wrapper.jar",
    "android/gradle/wrapper/gradle-wrapper.properties",
    "android/gradle/libs.versions.toml", "android/gradle/verification-metadata.xml",
    "android/build-logic/convention/src/main/kotlin/com/meshcoreone/buildlogic/BuildConventions.kt",
    "android/build-logic/convention/src/main/kotlin/com/meshcoreone/buildlogic/ModuleGraph.kt",
    "android/build-logic/convention/src/main/kotlin/com/meshcoreone/buildlogic/ScaffoldSchema.kt",
    "tools/android-port/controller/toolchain-pins.json", "tools/android-port/controller/provision.py",
    "tools/android-port/controller/ci.py", "tools/android-port/controller/ci_environment.py",
    "tools/android-port/controller/runtime_inputs.py",
    "android/scaffold/check_environment.py", "android/scaffold/environment-allowlist.json",
}


def require(condition, message):
    if not condition:
        raise PortError("WP-304 dependency proposal: " + message)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def validate_identity(event, environment, checkout):
    require(environment.get("GITHUB_EVENT_NAME") == "pull_request", "only the ordinary PR event is admitted")
    require(environment.get("GITHUB_REPOSITORY") == REPOSITORY, "foreign repository")
    require(environment.get("GITHUB_ACTOR") == "cbattlegear"
        and environment.get("GITHUB_TRIGGERING_ACTOR", environment.get("GITHUB_ACTOR")) == "cbattlegear", "unknown execution actor")
    require(event.get("number") == 33, "wrong reserved PR")
    pr = event.get("pull_request")
    require(isinstance(pr, dict) and pr.get("number", 33) == 33 and pr.get("state") == "open"
        and not pr.get("merged", False), "missing/closed/merged PR identity")
    require(pr.get("title", "").startswith("[WP-304] ") and pr.get("head", {}).get("ref") == BRANCH,
        "wrong owning branch/title")
    require(pr.get("base", {}).get("ref") == "main", "wrong intended base")
    for side in ("head", "base"):
        value = pr.get(side, {})
        require(value.get("repo", {}).get("full_name") == REPOSITORY, "fork/foreign PR")
        require(re.fullmatch(r"[0-9a-f]{40}", value.get("sha", "")), "missing immutable head/base")
    require(pr["head"]["sha"] == checkout, "stale checkout")
    for name in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"):
        require(re.fullmatch(r"[1-9][0-9]*", environment.get(name, "")), "missing positive run/attempt")
    return pr


def parse_lock(text):
    rows, configurations = {}, {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        require(line.count("=") == 1, "malformed lock row")
        coordinate, values = line.split("=")
        require(coordinate == "empty" or re.fullmatch(r"[\w.-]+:[\w.-]+:[\w.+-]+", coordinate), "malformed coordinate")
        require(coordinate not in rows, "duplicate lock coordinate")
        names = values.split(",")
        require(names and len(names) == len(set(names)) and all(CONFIGURATION_NAME.fullmatch(value) for value in names),
            "malformed/duplicate configuration")
        rows[coordinate] = set(names)
        for name in names:
            configurations.setdefault(name, set())
            if coordinate != "empty":
                configurations[name].add(coordinate)
    require(rows and any(configurations.values()), "missing/zero lock state")
    require(not any(configurations[name] for name in rows.get("empty", set())), "empty/populated configuration conflict")
    return configurations


def admitted_coordinates(raw):
    tree = ET.fromstring(raw)
    namespace = "{https://schema.gradle.org/dependency-verification}"
    require(tree.tag == namespace + "verification-metadata", "unknown verification namespace")
    require(tree.findtext(namespace + "configuration/" + namespace + "verify-metadata") == "true", "disabled metadata verification")
    admitted = set()
    for component in tree.findall(namespace + "components/" + namespace + "component"):
        coordinate = ":".join(component.get(field, "") for field in ("group", "name", "version"))
        checksums = component.findall(namespace + "artifact/" + namespace + "sha256")
        require(checksums and all(re.fullmatch(r"[0-9a-f]{64}", value.get("value", "")) for value in checksums),
            "missing/malformed existing checksum admission")
        require(coordinate not in admitted, "duplicate verification coordinate")
        admitted.add(coordinate)
    require(admitted, "zero verification inputs")
    return admitted


def parse_graph(text, expected):
    lines = text.splitlines()
    require(lines and lines[0] == "module\tconfiguration\tkind\tcomponent" and len(lines) > 1, "missing/malformed/zero graph")
    configurations = {}
    for line in lines[1:]:
        parts = line.split("\t")
        require(len(parts) == 4 and parts[0] == ":core:ui" and CONFIGURATION_NAME.fullmatch(parts[1])
            and parts[1] in expected, "foreign module/configuration")
        require(parts[2] == "selected" and parts[3], "unresolved/failed graph is not a generated proposal")
        configuration = configurations.setdefault(parts[1], set())
        require(parts[3] not in configuration, "duplicate selected component")
        configuration.add(parts[3])
    require(set(configurations) == set(expected), "missing configuration graph")
    return {name: {coordinate for coordinate in values if not coordinate.startswith("project ")}
        for name, values in configurations.items()}


def parse_approved_test_context_memberships(raw):
    require((len(raw), sha(raw)) in {
        (3794, TEST_CONTEXT_SHA), (3908, TEST_CONTEXT_RECEIPT_SHA),
    }, "approved test-context data changed")
    value = decode_json(raw.decode("utf8"))
    rows = value["allowed_new_test_context_memberships"]
    pairs = {(row["configuration"], row["coordinate"]) for row in rows}
    require(len(rows) == len(pairs) == 26 and {
        name: sum(configuration == name for configuration, _ in pairs)
        for name in ("debugUnitTestCompileClasspath", "debugUnitTestRuntimeClasspath", "debugUnitTestLintChecksClasspath")
    } == {"debugUnitTestCompileClasspath": 10, "debugUnitTestRuntimeClasspath": 8, "debugUnitTestLintChecksClasspath": 8},
        "missing/extra approved test-context membership")
    return pairs


def approved_test_context_memberships():
    path = ROOT / TEST_CONTEXT_INPUT
    require(path.is_file() and not path.is_symlink(), "missing/linked approved test-context data")
    return parse_approved_test_context_memberships(path.read_bytes())


def validate_delta(seed_text, prior_text, generated_text, graph_text, config_text, verification):
    expected = config_text.splitlines()
    require(expected and len(expected) == len(set(expected))
        and all(CONFIGURATION_NAME.fullmatch(name) for name in expected), "missing/zero/malformed configuration roster")
    seed = parse_lock(seed_text)
    before = parse_lock(prior_text) if prior_text is not None else seed
    after = parse_lock(generated_text)
    graph = parse_graph(graph_text, expected)
    require(set(after) == set(expected) | set(before), "lock contains an unexecuted configuration or omitted prior state")
    require(set(seed) <= set(after), "original seed configurations missing: " + ", ".join(sorted(set(seed) - set(after))))
    admitted = admitted_coordinates(verification)
    approved_contexts = approved_test_context_memberships()
    seed_versions = {}
    for values in seed.values():
        for coordinate in values:
            name, version = coordinate.rsplit(":", 1)
            seed_versions.setdefault(name, set()).add(version)
    for name, values in before.items():
        require(name in after and values <= after[name], "removed prior owned component/configuration")
    delta, context_admissions = [], []
    for name, values in after.items():
        require(values <= admitted, "unadmitted coordinate/version in " + name + ": " + ", ".join(sorted(values - admitted)))
        if name in graph:
            require(values == graph[name], "generated lock and actual selected graph differ")
        else:
            require(values == before[name], "unexecuted prior configuration changed")
        require(seed.get(name, set()) <= values if name in expected else True, "incumbent seed component/configuration removed")
        for coordinate in values:
            artifact, version = coordinate.rsplit(":", 1)
            if artifact in seed_versions and version not in seed_versions[artifact]:
                source_members = {member for member in seed.get(name, set()) if member.rsplit(":", 1)[0] == artifact}
                prior_members = {member for member in before.get(name, set()) if member.rsplit(":", 1)[0] == artifact}
                require((name, coordinate) in approved_contexts and not source_members
                    and prior_members in (set(), {coordinate}),
                    "incumbent version changed outside exact non-replacing test-context membership: " + name + " " + coordinate)
                if not prior_members:
                    context_admissions.append({"configuration": name, "coordinate": coordinate})
        delta.append({"configuration": name, "before": sorted(before.get(name, set())),
            "seed": sorted(seed.get(name, set())), "after": sorted(values),
            "added_vs_prior": sorted(values - before.get(name, set())),
            "added_vs_seed": sorted(values - seed.get(name, set()))})
    return {"configuration_count": len(after), "selected_component_rows": sum(len(x) for x in graph.values()),
        "configurations": delta, "test_context_data": {"path": TEST_CONTEXT_INPUT, "canonical_lf_sha256": TEST_CONTEXT_SHA,
            "parent_crlf_receipt_sha256": TEST_CONTEXT_RECEIPT_SHA},
        "new_test_context_memberships": sorted(context_admissions, key=lambda row: (row["configuration"], row["coordinate"]))}

def validate_compile_alignment(seed_text, alignment_text):
    seed = parse_lock(seed_text).get("debugUnitTestRuntimeClasspath", set())
    source = {}
    for artifact, version in COMPILE_ALIGNMENT.items():
        candidates = {coordinate for coordinate in seed if coordinate.rsplit(":", 1)[0] == artifact}
        require(candidates == {artifact + ":" + version}, "missing/ambiguous/changed runtime alignment seed: " + artifact)
        source[artifact] = next(iter(candidates))
    lines = alignment_text.splitlines()
    require(lines and lines[0] == "artifact\tsourceConfiguration\tsourceCoordinate\tconfiguration\trequested\tselected"
        and len(lines) > 1, "missing/malformed/zero actual compile alignment")
    observed = set()
    for line in lines[1:]:
        fields = line.split("\t")
        require(len(fields) == 6 and fields[0] in source and fields[1] == "debugUnitTestRuntimeClasspath"
            and fields[2] == source[fields[0]] and fields[3] == "debugUnitTestCompileClasspath"
            and fields[4].startswith(fields[0] + ":") and fields[5] == source[fields[0]], "unadmitted compile alignment edge")
        observed.add(fields[0])
    require(observed == set(source), "missing executed compile alignment")
    return {"artifacts": source, "actual_resolved_edges": len(lines) - 1}


def validate_command(command, state):
    require(state.get("host") == "linux", "wrong execution host")
    expected = [str(ROOT / "android" / "gradlew"), "-p", str(ROOT / "android"), TASK,
        "--write-locks", "--dependency-verification", "strict", "--no-build-cache", "--no-daemon",
        "--max-workers=1", "-Pkotlin.compiler.execution.strategy=in-process",
        "--project-cache-dir", str(Path(state["private_root"]) / "project-ui-proposal"), "--console=plain", "--quiet"]
    require(command == expected, "changed resolver/task/flags/cache")
    return command


def command(state):
    values = [str(ROOT / "android" / "gradlew"), "-p", str(ROOT / "android"), TASK,
        "--write-locks", "--dependency-verification", "strict", "--no-build-cache", "--no-daemon",
        "--max-workers=1", "-Pkotlin.compiler.execution.strategy=in-process",
        "--project-cache-dir", str(Path(state["private_root"]) / "project-ui-proposal"), "--console=plain", "--quiet"]
    return validate_command(values, state)

def validate_budget(environment):
    require(environment.get("JAVA_OPTS") == "-Xms32m -Xmx128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"
        and environment.get("GRADLE_OPTS") == '-Dorg.gradle.jvmargs="' + VM + '"', "changed admitted VM cap")


def validate_workflow(text):
    value = parse_yaml(text)
    validate_boundary(value, text)
    require(set(value.get("on", {})) == {"pull_request"} and set(value.get("jobs", {})) == {"generate"}, "extra event/job")
    job = value["jobs"]["generate"]
    require(job.get("runs-on") == "ubuntu-24.04" and job.get("timeout-minutes") == "20", "changed runner/budget")
    require(job.get("name") == "WP-304 owned UI lock proposal", "candidate cannot impersonate a gate")
    condition = " ".join(job.get("if", "").split())
    require(condition == "github.repository == 'cbattlegear/MeshCoreOne-Android' && github.event.pull_request.number == 33 && "
        "github.event.pull_request.head.repo.full_name == github.repository && github.event.pull_request.head.ref == "
        "'cbattlegear-refactored-spoon' && startsWith(github.event.pull_request.title, '[WP-304] ')", "wrong PR scope")
    steps = job["steps"]
    require(len(steps) == 6, "extra/missing candidate step")
    require(steps[0].get("uses") == "actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683"
        and steps[0].get("with") == {"ref": "${{ github.event.pull_request.head.sha }}", "fetch-depth": "0", "persist-credentials": "false"},
        "wrong checkout/persisted credentials")
    require(steps[1].get("uses") == "actions/setup-python@a26af69be951a213d495a4c3e4e4022e16d87065"
        and steps[1].get("with") == {"python-version": "3.12.4", "architecture": "x64"}, "wrong pinned Python")
    runs = [step.get("run") for step in steps if "run" in step]
    require(runs == [
        "python -m pip install --disable-pip-version-check --require-hashes --no-deps --only-binary=:all: "
            "-r tools/android-port/controller/requirements-ci.txt --quiet",
        "python -B android/core/ui/verification/dependency_proposal.py --self-test --workflow-check",
        'python -B android/core/ui/verification/dependency_proposal.py --run --root "${{ runner.temp }}/wp304-ui-generation"',
    ] and not any("if" in step for step in steps if "run" in step), "changed/conditional candidate command")
    upload = steps[-1]
    require(upload.get("uses") == "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02"
        and upload.get("if") == "${{ always() }}" and upload.get("with") == {
            "name": "wp304-ui-lock-proposal-${{ github.run_id }}-${{ github.run_attempt }}",
            "path": "${{ runner.temp }}/wp304-ui-generation/proposal", "if-no-files-found": "error",
            "retention-days": "7", "include-hidden-files": "false",
        }, "missing/unbounded failure retention")
    return {"result": "valid-owned-data-proposal-boundary", "mandatory_ci_or_gate": False}


def snapshot():
    files = [path.decode() for path in git(ROOT, "ls-files", "-z").split(b"\0") if path]
    ignored_locks = [
        path.relative_to(ROOT).as_posix() for path in (ROOT / "android").rglob("*.lockfile")
        if not any(part in {"build", ".gradle"} for part in path.relative_to(ROOT).parts)
    ]
    files = sorted(set(files) | set(ignored_locks))
    before = {}
    for relative in files:
        if relative == OWNER_LOCK:
            continue
        path = ROOT.joinpath(*relative.split("/"))
        require(path.is_file() and not path.is_symlink(), "missing/linked tracked input")
        raw = path.read_bytes()
        tracked = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "--verify", "HEAD:" + relative],
            capture_output=True, text=True, check=False)
        before[relative] = {"bytes": len(raw), "sha256": sha(raw),
            "git_blob": tracked.stdout.strip() if tracked.returncode == 0 else None}
        if relative in REQUIRED_INPUTS:
            require(before[relative]["git_blob"] is not None
                and git(ROOT, "hash-object", "--", str(path)).decode().strip() == before[relative]["git_blob"],
                "executable/build/reader checkout bytes differ from exact head")
    require(REQUIRED_INPUTS <= before.keys(), "missing committed executable/build/seed/provisioner/reader input")
    return before


def validate_settings_bookkeeping(raw):
    if raw is None:
        return
    require(0 < len(raw) <= 4096, "empty/excessive settings bookkeeping")
    text = raw.decode("utf8")
    require("\x00" not in text, "invalid settings bookkeeping text")
    entries = [line.strip() for line in text.splitlines() if line.strip() and not line.strip().startswith("#")]
    require(entries == ["empty=incomingCatalogForLibs0"], "settings catalog has remote/unknown/duplicate entries")


def retain_settings_bookkeeping(repository, output, phase):
    require(phase in {"before", "after"}, "unknown bookkeeping phase")
    source = repository / SETTINGS_BOOKKEEPING
    if not source.exists():
        require(not source.is_symlink(), "linked missing settings bookkeeping")
        return None
    require(source.is_file() and not source.is_symlink() and not any(parent.is_symlink() for parent in source.parents),
        "linked/invalid settings bookkeeping")
    with source.open("rb") as stream:
        raw = stream.read(4097)
    require(len(raw) <= 4096, "excessive settings bookkeeping")
    (output / ("settings-bookkeeping-" + phase + ".lockfile")).write_bytes(raw)
    return raw


def validate_writes(before, after, changes, settings_before=None, settings_after=None):
    for inputs, raw in ((before, settings_before), (after, settings_after)):
        entry = inputs.get(SETTINGS_BOOKKEEPING)
        require((entry is not None) == (raw is not None), "missing raw settings bookkeeping binding")
        validate_settings_bookkeeping(raw)
        if entry is not None:
            require(entry["bytes"] == len(raw) and entry["sha256"] == sha(raw) and entry["git_blob"] is None,
                "settings bookkeeping bytes differ or became tracked")
    require(SETTINGS_BOOKKEEPING not in before or SETTINGS_BOOKKEEPING in after, "settings bookkeeping removed")
    require({key: value for key, value in before.items() if key != SETTINGS_BOOKKEEPING}
        == {key: value for key, value in after.items() if key != SETTINGS_BOOKKEEPING},
        "unowned tracked/root/shared input changed")
    require(all(line[3:] == OWNER_LOCK and line[:2] in {"??", " M", "M "} for line in changes), "unknown/untracked candidate write")


def seed_initial_owned_lock(repository, output):
    source = repository / ROOT_LOCK
    target = repository / OWNER_LOCK
    require(source.is_file() and not source.is_symlink() and not target.is_symlink(), "linked/missing seed or owned lock")
    raw = source.read_bytes()
    require(len(raw) == 47512 and sha(raw) == ROOT_LOCK_SHA, "frozen ROOT UI seed bytes changed")
    if target.exists():
        require(target.is_file(), "invalid existing owned lock path")
        prior = target.read_bytes()
        parse_lock(prior.decode("utf8"))
        return prior, {
            "seeded_from_root": False,
            "preexisting_prior": {"bytes": len(prior), "sha256": sha(prior)},
            "resolution_baseline": "preexisting-owned-state",
        }
    require(target.parent.is_dir() and not any(parent.is_symlink() for parent in target.parents), "invalid owned target directory")
    shutil.copyfile(source, target)
    require(target.read_bytes() == raw, "initial owned seed bytes differ from actual frozen ROOT file")
    shutil.copyfile(target, output / "seeded-owned-gradle.lockfile")
    return None, {
        "seeded_from_root": True,
        "preexisting_prior": None,
        "resolution_baseline": "byte-exact-frozen-ROOT-state",
        "source_path": ROOT_LOCK,
        "source_blob": ROOT_LOCK_BLOB,
        "source_bytes": len(raw),
        "source_sha256": sha(raw),
        "target_path": OWNER_LOCK,
        "target_seed_bytes": len(raw),
        "target_seed_sha256": sha(raw),
    }


def run(root):
    require(platform.system() == "Linux" and platform.machine() == "x86_64", "only admitted ephemeral Linux x64")
    require(platform.python_version() == toolchain_lock()["python"], "wrong pinned Python")
    require(root.is_absolute() and not root.exists() and not root.resolve().is_relative_to(ROOT.resolve())
        and not ROOT.resolve().is_relative_to(root.resolve()) and not any(parent.is_symlink() for parent in root.parents),
        "root must be new external unlinked private output")
    output = root / "proposal"
    output.mkdir(parents=True)
    record = {"schema_version": 1, "work_package": "WP-304", "scope": "Generated owned lock DATA proposal only, never automatic persistence/test/native/gate/merge approval.",
        "result": "blocked-before-resolution", "raw_preserved_before_validation": True}
    failure = None
    try:
        event_path = os.environ.get("GITHUB_EVENT_PATH")
        require(event_path is not None, "missing ordinary execution identity")
        head = git(ROOT, "rev-parse", "HEAD").decode().strip()
        pr = validate_identity(load_json(Path(event_path)), dict(os.environ), head)
        actual = ci.execution_identity()
        require(actual is not None, "missing immutable run binding")
        manifest = load_manifest(ROOT)
        policy = load_json(ROOT / "docs" / "android" / "automation-policy.json")
        require(manifest.sha256 == MANIFEST and policy_revision(manifest, policy) == POLICY
            and manifest.data["reference"]["commit"] == SOURCE, "source/manifest/policy drift")
        require(git(ROOT, "rev-parse", SOURCE + "^{tree}").decode().strip() == SOURCE_TREE, "source tree drift")
        subprocess.run(["git", "-C", str(ROOT), "merge-base", "--is-ancestor", pr["base"]["sha"], head], check=True, capture_output=True)
        record.update({"binding": asdict(Binding(REPOSITORY, "WP-304", pr["base"]["sha"], head, SOURCE, MANIFEST, POLICY)),
            "run_id": actual["run_id"], "run_attempt": actual["run_attempt"], "actor": os.environ["GITHUB_ACTOR"]})
        require(not git(ROOT, "status", "--porcelain=v1", "--untracked-files=all").strip(), "candidate starts dirty")
        validate_workflow((ROOT / WORKFLOW).read_text(encoding="utf8"))
        verify_committed_inputs(ROOT)
        verify_wrapper()
        before = snapshot()
        settings_before = retain_settings_bookkeeping(ROOT, output, "before")
        validate_writes(before, before, [], settings_before, settings_before)
        seed = ROOT / ROOT_LOCK
        require(git(ROOT, "rev-parse", "HEAD:" + ROOT_LOCK).decode().strip() == ROOT_LOCK_BLOB
            and sha(seed.read_bytes().replace(b"\r\n", b"\n")) == ROOT_LOCK_SHA, "frozen ROOT UI seed changed")
        existing = ROOT / OWNER_LOCK
        prior, migration = seed_initial_owned_lock(ROOT, output)
        record["inputs_before"] = before
        record["initial_owned_state_migration"] = migration
        record["prior_owned_lock"] = None if prior is None else {"bytes": len(prior), "sha256": sha(prior),
            "git_blob": git(ROOT, "rev-parse", "HEAD:" + OWNER_LOCK).decode().strip()}
        write_json(output / "before.json", record)
        if prior is not None:
            (output / "prior-gradle.lockfile").write_bytes(prior)
        shutil.copyfile(seed, output / "root-ui-seed.lockfile")
        state = provision(root / "toolchain", accept_sdk_license=True)
        record["toolchain_provenance"] = state["provenance"]
        environment = candidate_environment(state)
        environment["JAVA_OPTS"] = "-Xms32m -Xmx128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"
        environment["GRADLE_OPTS"] = '-Dorg.gradle.jvmargs="' + VM + '"'
        validate_budget(environment)
        ci.execute([sys.executable, str(ROOT / "android" / "scaffold" / "check_environment.py")],
            environment, output / "preflight.log", timeout=60)
        record["command"] = command(state)
        record["host"] = "linux"
        record["limits"] = {"job_minutes": 20, "resolver_seconds": 900, "build_heap_mib": 512,
            "build_metaspace_mib": 512, "workers": 1, "compiler_execution": "in-process"}
        try:
            ci.execute(record["command"], environment, output / "resolver.log", timeout=900)
        except (PortError, OSError, subprocess.SubprocessError) as error:
            failure = error
        # Copy all produced bytes before inspecting success, including a failed resolver's partial graph/lock.
        for relative, name in ((OWNER_LOCK, "gradle.lockfile"), (GRAPH, "dependency-graphs.tsv"),
                (CONFIGURATIONS, "resolution-configurations.txt"), (ALIGNMENT, "unit-compile-alignment.tsv")):
            path = ROOT / relative
            if path.is_file() and not path.is_symlink():
                shutil.copyfile(path, output / name)
        settings_after = retain_settings_bookkeeping(ROOT, output, "after")
        record["inputs_after"] = snapshot()
        record["changed_paths"] = git(ROOT, "status", "--porcelain=v1", "--untracked-files=all").decode().splitlines()
        record["artifacts"] = {path.name: {"bytes": path.stat().st_size, "sha256": file_sha256(path)}
            for path in output.iterdir() if path.is_file()}
        record["result"] = "raw-produced-unvalidated"
        record["settings_bookkeeping"] = {
            "path": SETTINGS_BOOKKEEPING,
            "before": before.get(SETTINGS_BOOKKEEPING),
            "after": record["inputs_after"].get(SETTINGS_BOOKKEEPING),
            "scope": "Only absent or comments plus empty=incomingCatalogForLibs0; raw bytes retained, never committed.",
        }
        write_json(output / "raw-generation.json", record)
        validate_writes(before, record["inputs_after"], record["changed_paths"], settings_before, settings_after)
        require(failure is None, "actual resolver failed: " + str(failure))
        require(all((output / name).is_file() for name in ("gradle.lockfile", "dependency-graphs.tsv", "resolution-configurations.txt")),
            "missing actual generated output")
        record["unit_compile_alignment"] = validate_compile_alignment(seed.read_text(encoding="utf8"),
            (output / "unit-compile-alignment.tsv").read_text(encoding="utf8"))
        record["delta"] = validate_delta(seed.read_text(encoding="utf8"), prior.decode("utf8") if prior is not None else None,
            (output / "gradle.lockfile").read_text(encoding="utf8"),
            (output / "dependency-graphs.tsv").read_text(encoding="utf8"),
            (output / "resolution-configurations.txt").read_text(encoding="utf8"),
            (ROOT / "android" / "gradle" / "verification-metadata.xml").read_bytes())
        record["result"] = "actual-generated-owned-byte-proposal"
    except (PortError, OSError, ValueError, KeyError, ET.ParseError, subprocess.SubprocessError) as error:
        record["error"] = str(error)
        raise
    finally:
        write_json(output / "generation.json", record)
    return record


def self_tests():
    path = Path(__file__).with_name("test_dependency_proposal.py")
    require(path.is_file(), "missing helper regressions")
    spec = importlib.util.spec_from_file_location("wp304_dependency_tests", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    suite = unittest.defaultTestLoader.loadTestsFromModule(module)
    require(suite.countTestCases() > 0, "zero helper regressions")
    tested = unittest.TextTestRunner(verbosity=1).run(suite)
    require(tested.wasSuccessful() and not tested.skipped, "failed/error/skipped helper regressions")


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
            self_tests()
        if args.workflow_check:
            print(json.dumps(validate_workflow((ROOT / WORKFLOW).read_text(encoding="utf8"))))
        if args.run:
            require(args.root is not None, "explicit private root required")
            run(args.root)
        return 0
    except (PortError, OSError, ValueError, KeyError, ET.ParseError, subprocess.SubprocessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
