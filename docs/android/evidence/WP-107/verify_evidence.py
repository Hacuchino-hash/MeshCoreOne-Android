"""AndroidOnly: WP-107 Complete source/case/XML/APK evidence, never a parity gate or authority receipt."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


DIRECTORY = Path(__file__).resolve().parent
REPO = DIRECTORY.parents[3]
sys.path.insert(0, str(REPO / "tools" / "android-port"))
from controller.gates import policy_revision
from controller.model import load_manifest
from controller.paths import validate_writes
from controller.schema import load_json
from portmap import port_map

BASE_OUTPUTS = {
    "0394b83c9b47fa0d7198e2d631f7ddb313cd370d": DIRECTORY,
    "dc15f1ba445acf3230383ea68d4827c592f3fafa": DIRECTORY / "reconciled-dc15",
}
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
SESSION_SUITES = {
    "CrossComponentSessionCasesTest": 12,
    "OriginalChannelCasesTest": 17,
    "OriginalContactsAndSettingsCasesTest": 2,
    "OriginalCorrelationCasesTest": 32,
    "OriginalLifecycleCasesTest": 10,
    "OriginalMessageCasesTest": 12,
    "ProtocolErrorReferenceTest": 7,
    "SessionBoundaryCasesTest": 48,
    "SessionCompoundAndBoundaryTest": 27,
    "SessionOperationCasesTest": 26,
    "SessionReferenceVectorsTest": 31,
    "SessionRoleConsumerTest": 2,
    "SessionTcpIntegrationTest": 3,
}
CROSS_CASES = {
    "ProtocolBugFixTests::requestNeighbours rejects a short public key before sending()",
    "ProtocolBugFixTests::requestStatus throws device error when error response received()",
    "RequestRegionsIntegrationTests::device error propagates correctly()",
    "RequestRegionsIntegrationTests::full two-phase flow returns parsed regions()",
    "RequestRegionsIntegrationTests::requestRegions preserves an unmodeled raw type byte in the temp write()",
    "RequestRegionsIntegrationTests::temporarily sets zero-hop before sending for flood-routed contact()",
    "RequestRegionsIntegrationTests::timeout when no binaryResponse arrives()",
    "V112ProtocolTests::contactManager tracks contactDeleted()",
    "V112ProtocolTests::contactManager tracks contactsFull()",
    "EventDispatcherFilteredSubscriptionTests::filtered subscription receives only matching events()",
    "EventDispatcherFilteredSubscriptionTests::filtered subscription survives flood of non-matching events()",
    "LegacyUpdateContactHardeningTests::changeContactFlags with a NaN coordinate does not trap and clamps the frame()",
}


def git(*arguments):
    return subprocess.run(["git", "--no-pager", "-C", str(REPO), *arguments],
                          capture_output=True, text=True, check=True, timeout=30).stdout


def sha(data):
    return hashlib.sha256(data).hexdigest()


def canonical(path):
    return path.read_bytes().replace(b"\r\n", b"\n")


def parse_reports(directory, report_prefix="junit/protocol/"):
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        raise ValueError("Missing mandatory protocol XML")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites, identities = [], {}
    for path in files:
        root = ET.parse(path).getroot()
        cases = root.findall("testcase")
        observed = {
            "tests": len(cases),
            "failures": sum(child.find("failure") is not None for child in cases),
            "errors": sum(child.find("error") is not None for child in cases),
            "skipped": sum(child.find("skipped") is not None for child in cases),
        }
        if root.tag != "testsuite" or observed != {key: int(root.attrib[key]) for key in totals}:
            raise ValueError("Malformed suite/counters: " + path.name)
        if not cases or any(observed[key] for key in ("failures", "errors", "skipped")):
            raise ValueError("Zero, failed, errored or skipped mandatory suite: " + path.name)
        for case in cases:
            identity = (case.attrib["classname"], case.attrib["name"])
            if identity in identities:
                raise ValueError("Duplicate runner case: " + str(identity))
            identities[identity] = "passed"
        for key in totals:
            totals[key] += observed[key]
        suites.append({"suite": root.attrib["name"], **observed,
                       "report": report_prefix + path.name, "sha256_lf": sha(canonical(path))})
    session = {entry["suite"].rsplit(".", 1)[1]: entry["tests"] for entry in suites if ".protocol.session." in entry["suite"]}
    if session != SESSION_SUITES or totals["tests"] != 4674 or len(files) != 56:
        raise ValueError("Missing/stale complete 4445-baseline plus 229-session evidence")
    return totals, suites, identities, files


def case_map(manifest, identities):
    originals = load_json(REPO / "docs" / "android" / "test-cases.json")
    primary = {entry["path"]: entry for entry in manifest.data["inventory"]}
    names = {name: suite for suite, name in identities if ".protocol.session." in suite}
    records = []
    for entry in originals["entries"]:
        for case in entry["cases"]:
            owned = primary[entry["path"]]["primary_owner"] == "WP-107"
            if not owned and case["id"] not in CROSS_CASES:
                continue
            if case["id"] not in names:
                raise ValueError("Unexecuted original declaration: " + case["id"])
            records.append({
                "case_id": case["id"], "parameter_family": case["parameter_family"],
                "source_path": entry["path"], "source_blob_sha": entry["blob_sha"],
                "primary_source_owner_unchanged": primary[entry["path"]]["primary_owner"],
                "wp107_owned": owned, "disposition": "ported-and-executed-real-component",
                "junit_class": names[case["id"]], "junit_case_name": case["id"],
            })
    if sum(entry["wp107_owned"] for entry in records) != 73 or len(records) != 85:
        raise ValueError("Original declaration/family accounting changed")
    if {entry["case_id"] for entry in records if not entry["wp107_owned"]} != CROSS_CASES:
        raise ValueError("The twelve genuine deferred integration seams are incomplete")
    return {
        "source_sha": SOURCE, "owned_declared": 73, "owned_executed": 73,
        "cross_component_declared": 12, "cross_component_executed": 12,
        "reviewed_exclusions_claimed": 0, "ignored_or_disabled_tests": 0,
        "global_catalog_modified": False, "cases": records,
        "supplementary_native_case_names": sorted(name for name in names if name.startswith("WP-107::")),
    }


def source_map(manifest):
    outputs = port_map(manifest)
    declaration = re.compile(
        r"^\s*(?:(?:public|private|fileprivate|internal|static|mutating|nonisolated)\s+)*"
        r"(?:(actor|class|protocol|enum|struct|func|var|let|case)\s+([A-Za-z_]\w*)|(init)\s*\()"
    )
    records = []
    for entry in manifest.inputs("WP-107", False):
        content = git("show", SOURCE + ":" + entry["path"])
        declarations = []
        lines = content.splitlines()
        for index, line in enumerate(lines):
            match = declaration.match(line)
            if not match:
                continue
            signature = line.strip()
            if match[1] == "func" and "(" in line and ")" not in line:
                for continuation in lines[index + 1:]:
                    signature += " " + continuation.strip()
                    if ")" in continuation:
                        break
            declarations.append({"kind": match[1] or "init", "name": match[2] or "init",
                                 "line": index + 1, "source_declaration": signature})
        linked = [record["implementation"] for record in outputs if entry["path"] in record["sources"]]
        if entry["path"].endswith("/ProtocolError.swift"):
            linked += ["android/core/protocol/src/main/kotlin/com/meshcoreone/android/core/protocol/model/PacketCodes.kt",
                       "android/core/protocol/src/main/kotlin/com/meshcoreone/android/core/protocol/config/MeshCoreException.kt"]
            disposition = "reuse-canonical-six-typed-codes-GPL-prose-test-only-WP-304-app-mapping-pending"
        elif entry["kind"] == "support":
            linked += ["android/core/protocol/src/test/kotlin/com/meshcoreone/android/core/protocol/session/SessionTestSupport.kt"]
            disposition = "native-test-equivalent-recording-and-gated-real-session-not-a-production-success-stub"
        else:
            disposition = "ported-real-session-component" if entry["kind"] == "production" else "original-cases-executed"
        if not linked:
            raise ValueError("Unmapped owned input: " + entry["path"])
        records.append({"source_path": entry["path"], "source_blob_sha": entry["blob_sha"],
                        "kind": entry["kind"], "disposition": disposition, "implementations": sorted(set(linked)),
                        "declarations_and_bindings": declarations})
    kinds = {kind: sum(entry["kind"] == kind for entry in records) for kind in ("production", "test", "support")}
    if kinds != {"production": 24, "test": 13, "support": 1}:
        raise ValueError("Incomplete primary ownership")
    return {"source_sha": SOURCE, "primary_inputs": kinds,
            "scope": "Declaration/default/overload accountability; execution and deviations are separate proof",
            "inputs": records}


def fingerprint():
    files = sorted((REPO / "android" / "core" / "protocol" / "src").rglob("*.kt"))
    entries = [{"path": path.relative_to(REPO).as_posix(), "sha256_lf": sha(canonical(path))} for path in files]
    return {"files": entries, "sha256": sha(json.dumps(entries, sort_keys=True, separators=(",", ":")).encode())}


def artifact(path):
    return {"path": path.relative_to(REPO).as_posix(), "size_bytes": path.stat().st_size, "sha256": sha(path.read_bytes())}


def integration_reports(output, write):
    from controller.module_junit import module_sources

    committed = module_sources(REPO)
    modules = {
        "model": ("test", 166),
        "contracts": ("test", 4),
        "database": ("testDebugUnitTest", 45),
        "l10n": ("testDebugUnitTest", 25),
    }
    result = {}
    for module, (task, expected_count) in modules.items():
        directory = REPO / "android" / "core" / module / "build" / "test-results" / task
        files = sorted(directory.glob("TEST-*.xml"))
        if not files:
            raise ValueError("Missing current-base integration XML: " + module)
        identities, reports = set(), []
        for path in files:
            root = ET.parse(path).getroot()
            cases = root.findall("testcase")
            if root.tag != "testsuite" or not cases or len(cases) != int(root.attrib["tests"]):
                raise ValueError("Malformed integration XML: " + path.name)
            if any(int(root.attrib[key]) for key in ("failures", "errors", "skipped")):
                raise ValueError("Nonpassing integration XML: " + path.name)
            for case in cases:
                if any(case.find(kind) is not None for kind in ("failure", "error", "skipped")):
                    raise ValueError("Nonpassing integration case: " + path.name)
                identity = (case.attrib["classname"], case.attrib["name"])
                if identity in identities:
                    raise ValueError("Duplicate integration case: " + str(identity))
                identities.add(identity)
            raw = path.read_bytes()
            stored = raw.replace(b"\r\n", b"\n")
            target = output / "junit" / "integration" / module / path.name
            if write:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(stored)
            elif canonical(target) != stored:
                raise ValueError("Stored current-base module XML differs from actual runner")
            reports.append({
                "path": target.relative_to(REPO).as_posix(), "size_bytes": len(stored), "sha256": sha(stored),
                "original_runner_size_bytes": len(raw), "original_runner_sha256": sha(raw),
            })
        if len(identities) != expected_count:
            raise ValueError(f"Incomplete current-base {module} unit discovery: {len(identities)}")
        result[module] = {
            "tests": expected_count, "failed": 0, "errors": 0, "skipped": 0,
            "complete_raw_reports": reports,
            "candidate_input_blobs": committed.get("core/" + module),
            "input_binding_scope": "committed generic module inputs" if module != "contracts" else "existing neutral composite suite",
        }
    return result


def capture(base, write):
    if base not in BASE_OUTPUTS:
        raise ValueError("Expected coordinator-verified actual merged integration base")
    output = BASE_OUTPUTS[base]
    manifest = load_manifest(REPO)
    changed = set(git("diff", "--name-only", base).splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
    wp = next(entry for entry in manifest.data["work_packages"] if entry["id"] == "WP-107")
    validate_writes(wp["write_paths"], sorted(changed))
    if manifest.data["reference"]["commit"] != SOURCE:
        raise ValueError("Source drift")
    prefix = "" if output == DIRECTORY else output.relative_to(DIRECTORY).as_posix() + "/"
    totals, suites, identities, files = parse_reports(
        REPO / "android" / "core" / "protocol" / "build" / "test-results" / "test", prefix + "junit/protocol/",
    )
    cases, sources = case_map(manifest, identities), source_map(manifest)
    apk = REPO / "android" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    with zipfile.ZipFile(apk) as archive:
        dex = b"".join(archive.read(name) for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name))
        required = ["MeshCoreSession", "SessionCore", "ContactManager", "RequestResponseSerializer"]
        if any(("Lcom/meshcoreone/android/core/protocol/session/" + name + ";").encode() not in dex for name in required):
            raise ValueError("Actual session implementation missing from APK DEX")
        forbidden = ["ProtocolErrorReferenceTest", "SessionRadioTransport", "RecordingSession", "SessionTcpIntegrationTest"]
        if any(name.encode() in dex for name in forbidden):
            raise ValueError("Test/reference implementation was packaged")
        for prose in ("Command not supported by device firmware.", "Device storage is full."):
            if prose.encode() in dex:
                raise ValueError("GPL test-reference app prose leaked into MIT runtime")
        if "assets/licenses/BouncyCastle-MIT.txt" not in archive.namelist():
            raise ValueError("Existing crypto runtime notice absent")
    reports = []
    for path in files:
        original = path.read_bytes()
        target = output / "junit" / "protocol" / path.name
        stored = original.replace(b"\r\n", b"\n")
        reports.append({
            "path": target.relative_to(REPO).as_posix(), "size_bytes": len(stored), "sha256": sha(stored),
            "original_runner_size_bytes": len(original), "original_runner_sha256": sha(original),
        })
        if write:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(stored)
        elif target.read_bytes().replace(b"\r\n", b"\n") != stored:
            raise ValueError("Stored complete XML differs from current actual runner")
    graph = REPO / "android" / "build" / "reports" / "scaffold" / "module-graph.tsv"
    runtime = graph.with_name("runtime-dependencies.tsv")
    if not graph.exists() or not runtime.exists():
        raise ValueError("Missing strict actual graph/runtime evidence")
    saved_reports = {}
    for name, path in (("strict_graph", graph), ("strict_runtime", runtime)):
        target = output / "reports" / path.name
        data = canonical(path)
        if write:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        elif canonical(target) != data:
            raise ValueError("Stored graph/runtime differs from the actual strict report")
        saved_reports[name] = {"path": target.relative_to(REPO).as_posix(), "size_bytes": len(data), "sha256": sha(data),
                               "original_runner_size_bytes": path.stat().st_size, "original_runner_sha256": sha(path.read_bytes())}
    result = {
        "schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-107",
        "base_sha": base, "source_sha": SOURCE, "source_tree_sha": manifest.data["reference"]["tree_sha"],
        "manifest_sha256": manifest.sha256,
        "semantic_policy_revision": policy_revision(manifest, load_json(REPO / "docs" / "android" / "automation-policy.json")),
        "scope": "Actual local Windows JVM/loopback/static-debug-APK evidence; not hosted/hardware/license/gate authority",
        "protocol": {**totals, "suites": suites, "baseline_cases": 4445, "session_cases": 229},
        "kotlin_inputs": fingerprint(), "raw_reports": reports, "apk": artifact(apk),
        "apk_session_classes": required, "test_classes_and_GPL_reference_prose_packaged": False,
        "strict_graph": {**saved_reports["strict_graph"], "edge_rows": len(graph.read_text().splitlines()) - 1},
        "strict_runtime": {**saved_reports["strict_runtime"], "artifact_rows": len(runtime.read_text().splitlines()) - 1},
        "commands": [
            {"command": "launcher :core:protocol:test --tests com.meshcoreone.android.core.protocol.session.* --dependency-verification strict --no-build-cache --quiet", "result": "passed", "tests": 229},
            {"command": "launcher :core:protocol:test validateModuleGraph runtimeDependencyInventory resolveScaffoldDependencies --dependency-verification strict --no-build-cache --rerun-tasks --quiet", "result": "passed", "tests": 4674},
            {"command": "launcher :app:assembleDebug --dependency-verification strict --no-build-cache --quiet", "result": "passed"},
            {"command": "python .\\android\\scaffold\\inspect_apk.py", "result": "passed"},
        ],
        "launcher_options": "-ConstrainedMemory -BuildHeap 640m; one worker/in-process Kotlin/512m build metaspace/256m test heap and metaspace/SerialGC/two processors",
    }
    if output != DIRECTORY:
        result["commands"] = [
            {"command": "launcher :core:protocol:test --dependency-verification strict --no-build-cache --quiet",
             "result": "passed", "tests": 4674},
            {"command": "launcher validateModuleGraph runtimeDependencyInventory resolveScaffoldDependencies --dependency-verification strict --no-build-cache --quiet",
             "result": "passed"},
            {"command": "launcher :core:model:test :core:contracts:test --dependency-verification strict --no-build-cache --quiet",
             "result": "passed", "tests": 170},
            {"command": "launcher :core:database:verifyDomainRoomTests --dependency-verification strict --no-build-cache --quiet",
             "result": "failed-after-45-passing-Room-cases", "failure": "Merged WP-201 collector rejects WP-107 candidate paths at report line127"},
            {"command": "launcher :core:l10n:testDebugUnitTest --dependency-verification strict --no-build-cache --quiet",
             "result": "passed", "tests": 25},
            {"command": "launcher :app:assembleDebug --dependency-verification strict --no-build-cache --quiet",
             "result": "passed"},
            {"command": "python .\\android\\scaffold\\inspect_apk.py", "result": "passed"},
        ]
        result["integration_module_units"] = integration_reports(output, write)
        result["root_aggregate"] = {
            "result": "blocked-on-unowned-merged-collector-guard",
            "path": "docs/android/evidence/WP-201/collect_evidence.py",
            "base_input_blob": git("rev-parse", base + ":docs/android/evidence/WP-201/collect_evidence.py").strip(),
            "reason": "Root verification invokes a WP-201-only candidate-write guard which rejects correctly leased session changes.",
            "shared_guard_removed_or_bypassed": False,
            "coordinator_amendment_requested": True,
            "normal_current_head_root_CI_pass_claimed": False,
        }
        historical = load_json(DIRECTORY / "local-results.json")
        if historical["kotlin_inputs"] != result["kotlin_inputs"]:
            raise ValueError("Unexpected protocol source/test drift during base-only reconciliation")
        if load_json(DIRECTORY / "source-cases.json") != cases:
            raise ValueError("Original 85-case source catalog changed during base reconciliation")
        result["historical_evidence"] = {
            "base_sha": historical["base_sha"],
            "head_sha": "ce93bfaa22b438aed7e8b7a903a611270a985cd3",
            "record": "docs/android/evidence/WP-107/local-results.json",
            "protocol_inputs_byte_identical": True,
            "current_base_outputs_are_independent": True,
        }
    for name, value in (("source-cases.json", cases), ("source-map.json", sources), ("local-results.json", result)):
        expected = json.dumps(value, indent=2, sort_keys=True) + "\n"
        target = output / name
        if write:
            target.write_text(expected, encoding="utf-8", newline="\n")
        elif target.read_text(encoding="utf-8") != expected:
            raise ValueError("Stale/malformed per-WP evidence: " + name)
    print(json.dumps({"result": "verified" if output == DIRECTORY else "local-component-verified-root-aggregate-blocked",
                      "protocol_tests": totals["tests"], "session_tests": 229,
                      "owned_originals": 73, "cross_component_originals": 12, "suites": len(suites)}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    if arguments.write == arguments.check:
        parser.error("Choose exactly one of --write or --check")
    capture(arguments.base_sha, arguments.write)
