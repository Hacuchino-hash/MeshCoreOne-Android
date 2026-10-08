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
BOOTSTRAP_RECEIPT = "docs/android/evidence/WP-208/services-bootstrap-carry.json"
BOOTSTRAP_RECEIPT_COMMIT = "ba9de4e3c07e1fd142431e6f722123e3ecbb79ea"
CONTROL_INPUTS = [
    BOOTSTRAP_RECEIPT, "docs/android/port-manifest.json", "docs/android/automation-policy.json",
    "docs/android/test-cases.json", "docs/android/evidence/WP-208/collect_evidence.py",
    "tools/android-port/controller/verification_config.py",
]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.errors import PortError
from controller.model import load_manifest
from controller.verification_config import content_scope_revisions


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


def current_revisions(repo=ROOT):
    try:
        return content_scope_revisions(load_manifest(repo),
            load_json(repo / "docs" / "android" / "automation-policy.json"))
    except PortError as failure:
        raise ValueError("Frozen manifest/policy lineage drift: " + str(failure)) from failure


def bootstrap_contract(current, historical):
    marker = "// WP-218 diagnostic-only test-failure logging:"
    extension_marker = "// AndroidOnly: WP-218 diagnostic-only raw-failure printer,"
    require(historical.count(marker) == 1, "Malformed historical Services bootstrap")
    prefix, suffix = historical.split(marker, 1)
    if current == historical:
        return
    require(current.count("import java.io.ByteArrayOutputStream\n") == 1 and
            current.count(extension_marker) == 1 and current.count(marker) == 1,
            "Unowned/malformed Services producer extension")
    projected = current.replace("import java.io.ByteArrayOutputStream\n", "", 1)
    actual_prefix, extension = projected.split(extension_marker, 1)
    extension, actual_suffix = extension.split(marker, 1)
    require(actual_prefix == prefix and actual_suffix == suffix,
            "Original Services dependency/resolver/diagnostic consumer contract changed")
    require(not re.search(r"\b(?:dependencies|configurations|plugins|repositories|resolutionStrategy|"
                         r"setActions|setDependencies|onlyIf|exclude|ignoreFailures)\b", extension),
            "Services extension changes original dependency/execution contract")
    tasks = re.findall(r"val (\w+) by tasks\.registering\(Exec::class\)", extension)
    require(tasks == ["prepareContentInvocation", "retainContentServiceReports",
                      "verifyContentEvidenceReader", "verifyContentTests", "printServicesFailureDiagnostics"],
            "Unapproved Services producer task set")
    for statement in (
        '.resolve("WP-218").resolve("collect_evidence.py")',
        'providers.gradleProperty("meshCliInvocationFile")',
        'tasks.named<Test>("test") {\n    dependsOn(prepareContentInvocation)',
        '"--prepare-binding"', '"--retain-only", "services"', '"--self-test"',
        'dependsOn("test", ":app:testDebugUnitTest", verifyContentEvidenceReader)',
        'rootProject.tasks.named("verifyScaffoldTests") { dependsOn(verifyContentTests) }',
        'tasks.named("check") { dependsOn(verifyContentTests) }',
        'tasks.named("test") { finalizedBy(retainContentServiceReports) }',
    ):
        require(statement in extension, "Missing mandatory Services content producer contract: " + statement)


def services_producer(repo=ROOT):
    receipt = load_json(repo / BOOTSTRAP_RECEIPT)
    original = subprocess.check_output(["git", "-C", str(repo), "cat-file", "blob",
                                       BOOTSTRAP_RECEIPT_COMMIT + ":" + BOOTSTRAP_RECEIPT])
    require(json.loads(original) == receipt, "Historical bootstrap receipt was changed/relabelled")
    head = git(repo, "rev-parse", "HEAD")
    require(len(receipt["files"]) == 2 and receipt["producer_work_package"] == "WP-218",
            "Wrong Services bootstrap producer")
    for row in receipt["files"]:
        name = row["path"]
        raw = subprocess.check_output(["git", "-C", str(repo), "cat-file", "blob",
                                       receipt["carry_commit"] + ":" + name])
        require(git(repo, "rev-parse", receipt["carry_commit"] + ":" + name) == row["carried_blob"] and
                len(raw) == row["lf_bytes"] and hashlib.sha256(raw).hexdigest() == row["lf_sha256"],
                "Historical bootstrap blob does not match its original receipt")
        current = repo.joinpath(*name.split("/")).read_bytes()
        committed = subprocess.check_output(["git", "-C", str(repo), "cat-file", "blob", head + ":" + name])
        require(current == committed, "Current compiled Services producer bytes differ from actual HEAD")
        if name == "android/core/services/build.gradle.kts":
            bootstrap_contract(current.decode("utf-8"), raw.decode("utf-8"))
        else:
            require(current == raw, "Frozen Services lock changed")
    lock = receipt["data_local_lock"]
    require(git(repo, "hash-object", str(repo.joinpath(*lock["path"].split("/")))) == lock["blob"],
            "Frozen local Data lock changed")
    return {"historical_receipt_commit": BOOTSTRAP_RECEIPT_COMMIT,
            "historical_carry_commit": receipt["carry_commit"], "current_head_sha": head}


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
    revisions = current_revisions(repo)
    semantics = {key: value for key, value in policy.items() if key not in {
        "dispatch_mode", "paused", "activation_approved", "pending_capabilities",
    }}
    require(digest({"manifest": MANIFEST, "policy": semantics, "source": SOURCE}) == POLICY, "Frozen policy drift")
    producer = services_producer(repo)
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
    ] + [path.as_posix() for path in SHARED] + CONTROL_INPUTS + [
        "android/core/services/build.gradle.kts", "android/gradle/dependency-locks/core-services.lockfile",
    ]
    return {
        "source_sha": SOURCE, "source_tree": TREE, **revisions, "services_producer": producer,
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


def capture(output, invocation=None, repo=ROOT, *, local=False):
    require(output.is_absolute(), "Raw evidence output must be an explicit absolute directory")
    output.mkdir(parents=True, exist_ok=True)
    head = git(repo, "rev-parse", "HEAD")
    require(HEX.fullmatch(head), "Missing exact candidate HEAD")
    metadata = {"schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-208",
                "head_sha": head, "source_sha": SOURCE, "manifest_sha256": None, "policy_revision": None,
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
            actual = git(repo, "hash-object", "--no-filters", str(path))
            expected = git(repo, "rev-parse", head + ":" + name)
            metadata["input_blobs"].append({"path": name, "git_blob": actual, "expected_blob": expected,
                                           "matches_head": actual == expected, **record(path, repo)})
    root_lock = repo / "android" / "gradle" / "dependency-locks" / "core-services.lockfile"
    if root_lock.is_file():
        name = root_lock.relative_to(repo).as_posix()
        expected = git(repo, "rev-parse", head + ":" + name)
        actual = git(repo, "hash-object", "--no-filters", str(root_lock))
        metadata["input_blobs"].append({"path": name, "git_blob": actual, "expected_blob": expected,
                                       "matches_head": actual == expected, **record(root_lock, repo)})
    for name in CONTROL_INPUTS:
        try:
            path = repo.joinpath(*name.split("/"))
            input_record = record(path, repo)
            actual = git(repo, "hash-object", "--no-filters", str(path))
            expected = git(repo, "rev-parse", head + ":" + name)
            metadata["input_blobs"].append({"path": name, "git_blob": actual, "expected_blob": expected,
                                           "matches_head": actual == expected, **input_record})
        except (ValueError, OSError, subprocess.CalledProcessError) as failure:
            metadata["capture_errors"].append("Missing/unsafe/uncommitted control input " + name + ": " + str(failure))
    try:
        metadata.update(current_revisions(repo))
    except (ValueError, OSError, KeyError) as failure:
        metadata["capture_errors"].append("Invalid current catalog lineage: " + str(failure))
    if metadata["invocation"] is not None:
        try:
            retained = load_json(output / "invocation.json")
            if local:
                local_invocation(retained)
            else:
                original = executor_identity(retained)
                metadata["execution_expected"] = {"run_id": original["run_id"], "run_attempt": original["run_attempt"],
                                                   "base_sha": original["binding"]["base_sha"],
                                                   "head_sha": original["binding"]["head_sha"]}
        except (ValueError, OSError, KeyError) as failure:
            metadata["capture_errors"].append("Malformed retained executor identity: " + str(failure))
    snapshot = output / "raw-capture.json"
    snapshot.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return metadata


def local_invocation(invocation):
    require(isinstance(invocation, dict) and invocation.get("schema_version") == 1 and
            invocation.get("stage") == "verify" and invocation.get("host") == "linux" and
            "identity" in invocation and invocation["identity"] is None,
            "Explicit local execution cannot borrow hosted or malformed invocation identity")


def executor_identity(invocation, revisions=None):
    revisions = current_revisions() if revisions is None else revisions
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
            binding.get("source_sha") == SOURCE and binding.get("manifest_sha256") == revisions["manifest_sha256"] and
            binding.get("policy_revision") == revisions["policy_revision"], "Malformed executor binding")
    return identity


def validate_capture(output, accounting, require_hosted=True, *, expected_identity=None):
    revisions = {key: accounting[key] for key in ("manifest_sha256", "policy_revision")}
    snapshot = load_json(output / "raw-capture.json")
    require(snapshot.get("schema_version") == 1 and snapshot.get("repository") == "cbattlegear/MeshCoreOne-Android" and snapshot.get("work_package") == "WP-208" and
            HEX.fullmatch(snapshot.get("head_sha", "")) and snapshot.get("source_sha") == SOURCE and
            snapshot.get("manifest_sha256") == revisions["manifest_sha256"] and
            snapshot.get("policy_revision") == revisions["policy_revision"], "Malformed capture binding")
    require(not snapshot.get("capture_errors"), "Raw capture contains unsafe/missing inputs")
    require(snapshot["head_sha"] == accounting["services_producer"]["current_head_sha"],
            "Captured reports belong to a stale current Services producer")
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
        identity = executor_identity(invocation, revisions)
        require(isinstance(expected_identity, dict), "Missing independently expected executor identity")
        executor_identity({"schema_version": 1, "stage": "verify", "host": "linux", "identity": expected_identity}, revisions)
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
        "head_sha": snapshot["head_sha"], "source_sha": SOURCE, **revisions,
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
    parser.add_argument("--local", action="store_true")
    parser.add_argument("--expected-head")
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
        if args.local:
            if args.invocation is not None:
                require(args.invocation.is_absolute(), "Local controller invocation must be absolute")
                local_invocation(load_json(args.invocation))
            require(isinstance(args.expected_head, str) and HEX.fullmatch(args.expected_head) and
                    git(ROOT, "rev-parse", "HEAD") == args.expected_head, "Local execution needs its exact committed HEAD")
        else:
            require(args.expected_head is None, "A worker HEAD override cannot replace hosted provider identity")
        capture(output, args.invocation, local=args.local)
        if args.capture_only:
            print("Raw failure/success inputs retained; no success validation performed")
            return 0
        if args.local:
            result = validate_capture(output, source_accounting(), require_hosted=False)
            require(result["head_sha"] == args.expected_head, "Local reports belong to another commit")
            result["evidence_kind"] = "local-native"
            result["scope"] = "Exact local JVM/simulated SDK31 Room assertions; no hosted/provider, hardware or gate authority"
            result_name = "local-assertions.json"
        else:
            require(args.invocation is not None and args.invocation.is_absolute(),
                    "The actual absolute executor invocation is required")
            expected_identity = executor_identity(load_json(args.invocation))
            result = validate_capture(output, source_accounting(), expected_identity=expected_identity)
            result_name = "assertions.json"
        (output / result_name).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result["counts"]))
        return 0
    except (ValueError, OSError, KeyError, ET.ParseError, subprocess.CalledProcessError) as failure:
        print("BLOCKED: " + str(failure), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
