"""AndroidOnly: WP-208 Frozen source accounting, pre-validation raw retention and fail-closed assertion evidence."""

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
TREE = "8918fdc604341e6996a68c88f6bb1c02b9c2f87e"
MANIFEST = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
POLICY = "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"
SPECIFICATION_BASE = "7e2835bad2c03dfb5a088063655f9fc4dbafd00f"
PACKAGE = "com.meshcoreone.android.core.services.messaging."
SERVICES = Path("android") / "core" / "services"
MESSAGING = Path("src") / "test" / "kotlin" / "com" / "meshcoreone" / "android" / "core" / "services" / "messaging"
ROOM = Path("android") / "core" / "data" / "src" / "test" / "kotlin" / "com" / "meshcoreone" / "android" / "core" / "data" / "messaging" / "MessagingRoomIntegrationTest.kt"
ROOM_CLASS = "com.meshcoreone.android.core.data.messaging.MessagingRoomIntegrationTest"
ORIGINAL_ROOM_CASES = {
    "freshAndRecoveredAttemptsUseTheActualCommittedCounterAndWireTimestamp",
    "warmUpPurgesOnlyLegacyNullAndForgottenRadiosAndPreservesCurrentZeroRows",
    "coldStoreReopenRecoversTheSameRadioMessageAndPendingSendWithoutRewritingSortDate",
    "actualHydrationDrainsOneRadioInFIFOOrderAndLeavesTheOtherPartitionUntouched",
    "actualBackupConsumerBackfillsOnlyNilIncomingKeysAndUsesOutgoingUUIDIdentity",
    "deliveredManualRetryRowDoesNotEmitAnotherPacketOrLoseItsPersistedStatus",
    "twoRadioGenerationsDoNotCloseTheProcessStoreOrDuplicateMonitors",
    "actualClosedRepositoryFailureCannotAdvanceAttemptCountOrSendOnTheWire",
    "acceptedBlockedInsertFinishesBeforeShutdownAndSuccessorHydration",
    "acceptedInsertStorageFailureIsIncludedInShutdownInsteadOfBeingLostAfterItReturns",
    "realAckDuringBlockedRetryStatusPreventsAnotherWireAttempt",
    "trueAckAndFinalReadFailureRecoverOneResendClaimWithoutRecountingANewClaim",
    "persistedCallbackUsesRealRoomAndCanCloseWithoutJoiningItsOwnSend",
    "unacknowledgedResendRetiredByFailAllDoesNotIncrementTheActualRoomCount",
    "actualRoomExpiryCannotTurnMissingAckTrackingIntoAConfirmedResend",
    "genuineRoomAckRetiresItsLookupBeforeAcceptanceAndCountsExactlyOneResend",
    "manualPollingRoomConsumerCanCloseWithAnExplicitUnfinishedRecordReport",
    "livePollingRoomConsumerCanAwaitItsOwnGenerationCloseWithoutLeakingHandlers",
}
SHARED = [
    Path("android/core/model/src/main/kotlin/com/meshcoreone/android/core/model/DeduplicationKey.kt"),
    Path("android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/MessagingContracts.kt"),
    Path("android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/MessagingFaults.kt"),
    Path("android/core/data/src/main/kotlin/com/meshcoreone/android/core/data/repository/RepositoryPolicies.kt"),
    ROOM,
]
QUOTED = r'("(?:[^"\\]|\\.)*")'
ORIGINAL = re.compile(r'original\(\s*' + QUOTED + r'\s*,\s*' + QUOTED + r'(?:\s*,\s*' + QUOTED + r')?(?:\s*,\s*' + QUOTED + r')?')
NATIVE = re.compile(r'native\(\s*' + QUOTED)
ROW = re.compile(r" \[row=(\d+)\]$")
HEX = re.compile(r"^[0-9a-f]{40}$")


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args]).decode("utf-8").strip()


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()).hexdigest()

def load_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate JSON field: " + key)
            result[key] = value
        return result
    def invalid_constant(value):
        raise ValueError("Non-finite JSON value: " + value)
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 16 * 1048576, "Missing/unsafe JSON input")
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique, parse_constant=invalid_constant)


def record(path, root):
    require(path.is_file() and not path.is_symlink(), "Missing/linked evidence input: " + str(path))
    require(path.resolve().is_relative_to(root.resolve()), "Evidence path escapes its declared root")
    raw = path.read_bytes()
    return {"path": path.relative_to(root).as_posix(), "size_bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def declarations(directory):
    originals, natives = {}, {}
    for path in sorted(directory.glob("*.kt"), key=lambda value: value.name):
        text = path.read_text(encoding="utf-8")
        require("@Disabled" not in text and "@Ignore" not in text, "Disabled mandatory messaging case")
        require("// PortedFrom:" in text or "// AndroidOnly: WP-208" in text, "Missing test provenance")
        for suite, name, signature, row in ORIGINAL.findall(text):
            identity = json.loads(suite) + "::" + json.loads(name) + (json.loads(signature) if signature else "()")
            expanded = identity + (json.loads(row) if row else "")
            require(expanded not in originals, "Duplicate expanded original identity: " + expanded)
            originals[expanded] = {"family": identity, "test_source": path.name}
        for name in NATIVE.findall(text):
            identity = "WP-208::" + json.loads(name)
            require(identity not in natives, "Duplicate native identity: " + identity)
            natives[identity] = path.name
    return originals, natives


def source_accounting(repo=ROOT):
    manifest = load_json(repo / "docs" / "android" / "port-manifest.json")
    policy = load_json(repo / "docs" / "android" / "automation-policy.json")
    require(digest(manifest) == MANIFEST, "Frozen manifest drift")
    semantics = {key: value for key, value in policy.items() if key not in {
        "dispatch_mode", "paused", "activation_approved", "pending_capabilities",
    }}
    require(digest({"manifest": MANIFEST, "policy": semantics, "source": SOURCE}) == POLICY, "Frozen policy drift")
    require(manifest["reference"]["commit"] == SOURCE and git(repo, "rev-parse", SOURCE + "^{tree}") == TREE, "Frozen reference drift")
    work = manifest["work_packages"]
    require(len(work) == 65 and sum(len(wp["depends_on"]) for wp in work) == 185 and
            sum(wp["human_gate"] for wp in work) == 8 and all(wp["planned_state"] == "pending" for wp in work),
            "Frozen ownership/dependency/gate states changed")
    owned = {entry["path"]: entry for entry in manifest["inventory"] if entry["primary_owner"] == "WP-208"}
    require(len(owned) == 34, "Expected all 34 primary inputs")
    for name, entry in owned.items():
        path = repo.joinpath(*name.split("/"))
        require(git(repo, "hash-object", "--path=" + name, str(path)) == entry["blob_sha"], "Frozen primary changed: " + name)
    catalog = load_json(repo / "docs" / "android" / "test-cases.json")
    require(catalog["source_sha"] == SOURCE, "Frozen test source drift")
    families = {}
    for entry in catalog["entries"]:
        if entry["path"] not in owned:
            continue
        require(entry["blob_sha"] == owned[entry["path"]]["blob_sha"], "Catalog/source blob mismatch")
        for case in entry["cases"]:
            require(case["id"] not in families, "Duplicate frozen original family")
            families[case["id"]] = {**case, "source_path": entry["path"], "source_blob": entry["blob_sha"]}
    require(len(families) == 128, "Expected all 128 original declaration/parameter families")
    originals, natives = declarations(repo / SERVICES / MESSAGING)
    require(len(originals) == 131, "Expected 131 expanded original cases")
    require({row["family"] for row in originals.values()} == set(families), "Missing/extra original family assertions")
    multiplicity = Counter(row["family"] for row in originals.values())
    for identity, case in families.items():
        expected = 1 if case["parameter_family"] == "single" else 4
        require(multiplicity[identity] == expected, "Original parameter family was not fully expanded: " + identity)
        if expected == 4:
            require({int(ROW.search(name).group(1)) for name, row in originals.items() if row["family"] == identity} == {0, 1, 2, 3},
                    "Duplicate/missing original argument row")
    require(len(natives) >= 53, "Lowered mandatory native regression floor")
    policy_path = SHARED[3]
    old = git(repo, "show", SPECIFICATION_BASE + ":" + policy_path.as_posix()) + "\n"
    now = (repo / policy_path).read_text(encoding="utf-8")
    begin, end = "internal object RepositoryDeduplicationKey {", "internal data class InboundHop"
    require(old.split(begin, 1)[0] == now.split(begin, 1)[0] and
            old.split(end, 1)[1] == now.split(end, 1)[1], "Non-dedup shared repository policy changed")
    room_text = (repo / ROOM).read_text(encoding="utf-8")
    require("@Ignore" not in room_text and "@Disabled" not in room_text, "Disabled mandatory Room consumer")
    room_cases = re.findall(r"@Test\s+fun\s+(\w+)\(", room_text)
    require(len(room_cases) >= 18 and len(set(room_cases)) == len(room_cases), "Missing/duplicate real Room consumer declarations")
    require(ORIGINAL_ROOM_CASES.issubset(room_cases), "Removed an original mandatory Room consumer")
    implementation_inputs = [
        path.relative_to(repo).as_posix()
        for path in sorted((repo / SERVICES / "src").rglob("*.kt"), key=lambda value: value.as_posix())
        if PACKAGE.replace(".", "/").rstrip("/") in path.as_posix()
    ] + [path.as_posix() for path in SHARED]
    return {
        "source_sha": SOURCE, "source_tree": TREE, "manifest_sha256": MANIFEST, "policy_revision": POLICY,
        "primary_inputs": list(owned.values()), "original_families": families,
        "expanded_originals": originals, "native_regressions": natives, "room_consumers": room_cases,
        "implementation_inputs": implementation_inputs,
        "scope": "Authored original assertion accounting; no Kotlin execution or formal parity acceptance",
    }


def read_junit(directory, *, minimum=1):
    files = sorted(directory.glob("TEST-*.xml"), key=lambda path: path.name)
    require(files, "Missing mandatory full JUnit: " + str(directory))
    cases, total = {}, 0
    for path in files:
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 16 * 1048576, "Unsafe JUnit file")
        raw = path.read_bytes()
        upper = raw.replace(b"\x00", b"").upper()
        require(b"<!DOCTYPE" not in upper and b"<!ENTITY" not in upper, "Unsafe XML declaration")
        root = ET.fromstring(raw)
        require(root.tag == "testsuite", "Malformed JUnit root")
        nodes = root.findall("testcase")
        require(nodes and str(len(nodes)) == root.get("tests"), "Zero/mismatched discovery")
        for field in ("failures", "errors", "skipped"):
            require(root.get(field) == "0", "Failed/skipped mandatory full suite")
        for node in nodes:
            require(not any(node.find(field) is not None for field in ("failure", "error", "skipped")), "Hidden failed/skipped case")
            name, classname = node.get("name"), node.get("classname")
            require(name and classname, "Missing assertion identity")
            identity = (classname, name)
            require(identity not in cases, "Duplicate assertion identity")
            cases[identity] = path.name
        total += len(nodes)
    require(total >= minimum, "Lowered mandatory full-module discovery floor")
    return cases, {"discovered": total, "passed": total, "failed": 0, "errors": 0, "skipped": 0}


def capture(output, invocation=None, repo=ROOT):
    require(output.is_absolute(), "Raw evidence output must be an explicit absolute directory")
    output.mkdir(parents=True, exist_ok=True)
    head = git(repo, "rev-parse", "HEAD")
    require(HEX.fullmatch(head), "Missing exact candidate HEAD")
    metadata = {"schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-208",
                "head_sha": head, "source_sha": SOURCE, "manifest_sha256": MANIFEST, "policy_revision": POLICY,
                "invocation": None, "execution_expected": None, "raw_junit": {}, "input_blobs": [], "capture_errors": []}
    if invocation is not None:
        if invocation.is_file() and not invocation.is_symlink():
            destination = output / "invocation.json"
            shutil.copyfile(invocation, destination)
            metadata["invocation"] = record(destination, output)
        else:
            metadata["capture_errors"].append("Missing/linked executor invocation")
    for module, task in (("services", "test"), ("data", "testDebugUnitTest")):
        source = repo / "android" / "core" / module / "build" / "test-results" / task
        destination = output / "junit" / module
        destination.mkdir(parents=True, exist_ok=True)
        records = []
        for path in sorted(source.glob("TEST-*.xml"), key=lambda value: value.name):
            if not path.is_file() or path.is_symlink() or path.stat().st_size > 16 * 1048576:
                metadata["capture_errors"].append("Unsafe raw JUnit input: " + module + ":" + path.name)
                continue
            copied = destination / path.name
            shutil.copyfile(path, copied)
            records.append(record(copied, output))
        metadata["raw_junit"][module] = records
    for module in ("protocol", "model", "contracts", "services", "data"):
        prefix = f"android/core/{module}/"
        for name in git(repo, "ls-tree", "-r", "--name-only", head, prefix).splitlines():
            if "/src/" not in name and not name.endswith(("build.gradle.kts", "gradle.lockfile")):
                continue
            path = repo.joinpath(*name.split("/"))
            actual = git(repo, "hash-object", "--path=" + name, str(path))
            expected = git(repo, "rev-parse", head + ":" + name)
            metadata["input_blobs"].append({"path": name, "git_blob": actual, "expected_blob": expected,
                                           "matches_head": actual == expected, **record(path, repo)})
    root_lock = repo / "android" / "gradle" / "dependency-locks" / "core-services.lockfile"
    if root_lock.is_file():
        name = root_lock.relative_to(repo).as_posix()
        expected = git(repo, "rev-parse", head + ":" + name)
        actual = git(repo, "hash-object", "--path=" + name, str(root_lock))
        metadata["input_blobs"].append({"path": name, "git_blob": actual, "expected_blob": expected,
                                       "matches_head": actual == expected, **record(root_lock, repo)})
    if metadata["invocation"] is not None:
        try:
            original = executor_identity(load_json(output / "invocation.json"))
            metadata["execution_expected"] = {"run_id": original["run_id"], "run_attempt": original["run_attempt"],
                                               "base_sha": original["binding"]["base_sha"],
                                               "head_sha": original["binding"]["head_sha"]}
        except (ValueError, OSError, KeyError) as failure:
            metadata["capture_errors"].append("Malformed retained executor identity: " + str(failure))
    snapshot = output / "raw-capture.json"
    snapshot.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return metadata


def executor_identity(invocation):
    require(isinstance(invocation, dict) and invocation.get("schema_version") == 1 and invocation.get("stage") == "verify" and
            invocation.get("host") == "linux", "Wrong execution schema/stage/host")
    identity = invocation.get("identity")
    require(isinstance(identity, dict) and set(identity) == {"binding", "run_id", "run_attempt"} and
            type(identity.get("run_id")) is int and identity["run_id"] > 0 and
            type(identity.get("run_attempt")) is int and identity["run_attempt"] > 0, "Missing actual run/attempt")
    binding = identity.get("binding")
    require(isinstance(binding, dict) and set(binding) == {
        "repository", "work_package", "base_sha", "head_sha", "source_sha", "manifest_sha256", "policy_revision",
    } and binding.get("repository") == "cbattlegear/MeshCoreOne-Android" and binding.get("work_package") == "WP-003" and
            isinstance(binding.get("base_sha"), str) and HEX.fullmatch(binding["base_sha"]) and
            isinstance(binding.get("head_sha"), str) and HEX.fullmatch(binding["head_sha"]) and
            binding.get("source_sha") == SOURCE and binding.get("manifest_sha256") == MANIFEST and
            binding.get("policy_revision") == POLICY, "Malformed executor binding")
    return identity


def validate_capture(output, accounting, require_hosted=True, *, expected_identity=None):
    snapshot = load_json(output / "raw-capture.json")
    require(snapshot.get("schema_version") == 1 and snapshot.get("repository") == "cbattlegear/MeshCoreOne-Android" and snapshot.get("work_package") == "WP-208" and
            HEX.fullmatch(snapshot.get("head_sha", "")) and snapshot.get("source_sha") == SOURCE and
            snapshot.get("manifest_sha256") == MANIFEST and snapshot.get("policy_revision") == POLICY, "Malformed capture binding")
    require(not snapshot.get("capture_errors"), "Raw capture contains unsafe/missing inputs")
    input_names = [row["path"] for row in snapshot["input_blobs"]]
    require(input_names and len(set(input_names)) == len(input_names) and
            set(accounting["implementation_inputs"]).issubset(input_names), "Missing/duplicate committed implementation inputs")
    require(all(row.get("matches_head") is True and row.get("git_blob") == row.get("expected_blob") for row in snapshot["input_blobs"]),
            "Execution inputs did not match committed candidate HEAD")
    for module in ("services", "data"):
        require(snapshot["raw_junit"].get(module), "Missing mandatory raw module: " + module)
        require({row["path"] for row in snapshot["raw_junit"][module]} ==
                {path.relative_to(output).as_posix() for path in (output / "junit" / module).glob("TEST-*.xml")},
                "Unrecorded/absent full raw report")
        for expected in snapshot["raw_junit"][module]:
            require(record(output / expected["path"], output) == expected, "Changed raw evidence")
    identity = None
    if require_hosted:
        require(snapshot["invocation"], "Missing actual Linux invocation; old/local proof is not acceptance")
        require(record(output / snapshot["invocation"]["path"], output) == snapshot["invocation"], "Changed invocation")
        invocation = load_json(output / "invocation.json")
        identity = executor_identity(invocation)
        require(isinstance(expected_identity, dict), "Missing independently expected executor identity")
        executor_identity({"schema_version": 1, "stage": "verify", "host": "linux", "identity": expected_identity})
        require(identity == expected_identity, "Different/stale provider execution identity")
        binding = expected_identity["binding"]
        expected_execution = snapshot.get("execution_expected")
        require(expected_execution == {
            "run_id": expected_identity["run_id"], "run_attempt": expected_identity["run_attempt"],
            "base_sha": binding["base_sha"], "head_sha": binding["head_sha"],
        }, "Different/stale captured run or attempt")
        require(binding["repository"] == snapshot["repository"] and binding["head_sha"] == snapshot["head_sha"],
                "Stale/malformed run binding")
    jvm, jvm_counts = read_junit(output / "junit" / "services")
    native, native_counts = read_junit(output / "junit" / "data", minimum=369)
    messaging = {name for (classname, name) in jvm if classname.startswith(PACKAGE)}
    expected = set(accounting["expanded_originals"]) | set(accounting["native_regressions"])
    require(messaging == expected, "Missing/extra executed messaging identities: " +
            repr(sorted(expected - messaging)) + " / " + repr(sorted(messaging - expected)))
    require(sum(classname.startswith(PACKAGE) for classname, _ in jvm) == len(expected), "Duplicated messaging identity across classes")
    consumers = {name for classname, name in native if classname == ROOM_CLASS}
    require(consumers == set(accounting["room_consumers"]), "Missing/extra actual Room consumer execution")
    return {
        "schema_version": 1, "repository": snapshot["repository"], "work_package": "WP-208",
        "head_sha": snapshot["head_sha"], "source_sha": SOURCE, "manifest_sha256": MANIFEST, "policy_revision": POLICY,
        "execution_identity": identity, "counts": {
            "original_families": len(accounting["original_families"]), "expanded_original_cases": len(accounting["expanded_originals"]),
            "messaging": {"discovered": len(expected), "passed": len(expected), "failed": 0, "errors": 0, "skipped": 0},
            "full_services": jvm_counts, "full_data": native_counts, "actual_room_consumers": len(consumers),
        },
        "raw_capture_sha256": record(output / "raw-capture.json", output)["sha256"],
        "scope": "Exact candidate Linux/JVM and simulated SDK31 Room evidence; no hardware, signing, full graph or human gate authority",
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-only", action="store_true")
    parser.add_argument("--capture-only", action="store_true")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--invocation", type=Path)
    args = parser.parse_args(argv)
    try:
        if args.source_only:
            accounting = source_accounting()
            print(json.dumps({"original_families": len(accounting["original_families"]), "expanded_originals": len(accounting["expanded_originals"]),
                              "native_regressions": len(accounting["native_regressions"]), "room_consumers": len(accounting["room_consumers"]),
                              "kotlin_execution_verified": False}))
            return 0
        output = args.output
        if output is None:
            declared = os.environ.get("ANDROID_CI_OUTPUT")
            require(declared, "Explicit raw output/ANDROID_CI_OUTPUT is required")
            output = Path(declared) / "wp208"
        capture(output, args.invocation)
        if args.capture_only:
            print("Raw failure/success inputs retained; no success validation performed")
            return 0
        require(args.invocation is not None and args.invocation.is_absolute(),
                "The actual absolute executor invocation is required")
        expected_identity = executor_identity(load_json(args.invocation))
        result = validate_capture(output, source_accounting(), expected_identity=expected_identity)
        (output / "assertions.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result["counts"]))
        return 0
    except (ValueError, OSError, KeyError, ET.ParseError, subprocess.CalledProcessError) as failure:
        print("BLOCKED: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
