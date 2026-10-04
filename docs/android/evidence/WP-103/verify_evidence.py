"""AndroidOnly: WP-103 derive/check actual local discovery and source dispositions, not a gate receipt."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


DIRECTORY = Path(__file__).resolve().parent
REPO = DIRECTORY.parents[3]
sys.path.insert(0, str(REPO / "tools" / "android-port"))

from controller.gates import policy_revision
from controller.model import load_manifest
from controller.paths import permits
from controller.schema import load_json
from portmap import port_map


BASE = "eaf0fdb956afcb20e2de3d7d6550e0cbeeb50730"
PARSER_SUITES = {
    "CrossComponentParserCasesTest": 50,
    "OriginalBugFixCasesTest": 34,
    "OriginalPythonReferenceCasesTest": 28,
    "OriginalRegionsCasesTest": 21,
    "OriginalResponseCasesTest": 74,
    "OriginalRxLogCasesTest": 41,
    "ParserBoundaryTest": 1704,
    "PythonReferenceVectorsTest": 1479,
    "TransportParserIntegrationTest": 2,
}
DEFERRED_SESSION = {
    "ProtocolBugFixTests::requestNeighbours rejects a short public key before sending()",
    "ProtocolBugFixTests::requestStatus throws device error when error response received()",
    "RequestRegionsIntegrationTests::device error propagates correctly()",
    "RequestRegionsIntegrationTests::full two-phase flow returns parsed regions()",
    "RequestRegionsIntegrationTests::requestRegions preserves an unmodeled raw type byte in the temp write()",
    "RequestRegionsIntegrationTests::temporarily sets zero-hop before sending for flood-routed contact()",
    "RequestRegionsIntegrationTests::timeout when no binaryResponse arrives()",
}
DEFERRED_CONTACT_MANAGER = {
    "V112ProtocolTests::contactManager tracks contactDeleted()",
    "V112ProtocolTests::contactManager tracks contactsFull()",
}
ROUND_TRIP_BUILDERS = {
    "setRadio with repeat appends byte", "setRadio without repeat no extra byte",
    "setRadio rounds frequency to the nearest kHz",
    "setRadio clamps out-of-range frequency and bandwidth instead of trapping",
    "setTime saturates out-of-range dates instead of trapping",
    "setTxPower positive power packet format", "setTxPower negative power packet format",
    "getRepeatFreq packet format",
}


def git(*arguments):
    return subprocess.run(
        ["git", "--no-pager", "-C", str(REPO), *arguments],
        capture_output=True, text=True, check=True, timeout=30,
    ).stdout


def canonical_hash(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def read_results():
    directory = REPO / "android" / "core" / "protocol" / "build" / "test-results" / "test"
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        raise ValueError("Missing mandatory protocol JUnit XML")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites, cases = [], {}
    for path in files:
        node = ET.parse(path).getroot()
        if node.tag != "testsuite" or any(key not in node.attrib for key in totals):
            raise ValueError("Malformed mandatory JUnit counters: " + path.name)
        counts = {key: int(node.attrib[key]) for key in totals}
        children = node.findall("testcase")
        observed = {
            "tests": len(children),
            "failures": sum(bool(child.findall("failure")) for child in children),
            "errors": sum(bool(child.findall("error")) for child in children),
            "skipped": sum(bool(child.findall("skipped")) for child in children),
        }
        if counts != observed or counts["tests"] <= 0 or any(counts[key] for key in ("failures", "errors", "skipped")):
            raise ValueError("Zero, malformed, failed or skipped mandatory suite: " + path.name)
        for child in children:
            identity = (child.attrib["classname"], child.attrib["name"])
            if identity in cases:
                raise ValueError("Duplicate JUnit case identity")
            cases[identity] = "passed"
        for key in totals:
            totals[key] += counts[key]
        suites.append({
            "suite": node.attrib["name"], **counts,
            "report": path.relative_to(REPO).as_posix(), "report_sha256_lf": canonical_hash(path),
        })
    parsers = {s["suite"].rsplit(".", 1)[1]: s["tests"] for s in suites if ".protocol.parser." in s["suite"]}
    if parsers != PARSER_SUITES or totals["tests"] != 4082 or len(files) != 35:
        raise ValueError("The exact current-base 4082-case protocol runner evidence is incomplete or stale")
    parser_names = {name for (suite, name) in cases if ".protocol.parser." in suite}
    return totals, suites, parser_names


def source_cases(manifest, executed):
    inventory = load_json(REPO / "docs" / "android" / "test-cases.json")
    owned = {entry["path"] for entry in manifest.inputs("WP-103", False) if entry["kind"] == "test"}
    records = []
    for entry in inventory["entries"]:
        name = Path(entry["path"]).name
        for case in entry["cases"]:
            identity = case["id"]
            selected = entry["path"] in owned
            if name == "RoundTripTests.swift":
                selected = identity.removeprefix("RoundTripTests::").removesuffix("()") not in ROUND_TRIP_BUILDERS
            elif name == "V112ProtocolTests.swift":
                selected = "packet builder" not in identity and not identity.startswith((
                    "V112ProtocolTests::setAutoAddConfig",
                ))
            elif name == "MeshEventErrorCodeTests.swift":
                selected = identity == "MeshEventErrorCodeTests::PacketParser maps a RESP_CODE_ERR frame to .error with the firmware sub-code()"
            if not selected:
                continue
            if identity in executed:
                disposition, follow_up = "ported-and-executed", None
            elif identity in DEFERRED_SESSION:
                disposition, follow_up = "unexecuted-real-session-integration", "WP-107"
            elif identity in DEFERRED_CONTACT_MANAGER:
                disposition, follow_up = "unexecuted-real-contact-manager-integration", "WP-107/WP-109"
            else:
                raise ValueError("Unaccounted original case: " + identity)
            records.append({
                "source_path": entry["path"], "source_blob_sha": entry["blob_sha"],
                "case_id": identity, "parameter_family": case["parameter_family"],
                "primary_source_owner_unchanged": next(e["primary_owner"] for e in manifest.data["inventory"] if e["path"] == entry["path"]),
                "wp103_owned": entry["path"] in owned, "disposition": disposition, "follow_up": follow_up,
                "junit_case_name": identity if identity in executed else None,
            })
    owned_records = [entry for entry in records if entry["wp103_owned"]]
    if len(owned_records) != 205 or sum(e["disposition"] == "ported-and-executed" for e in owned_records) != 198:
        raise ValueError("Owned original-case inventory/discovery changed")
    if len(records) != 257 or sum(e["disposition"] == "ported-and-executed" for e in records) != 248:
        raise ValueError("The WP-104/WP-106 parser seams are not completely accounted for")
    if set(e["case_id"] for e in records if e["follow_up"]) != DEFERRED_SESSION | DEFERRED_CONTACT_MANAGER:
        raise ValueError("Changed unexecuted integration disposition")
    return {
        "scope": "Original assertions and source families, not file/header counts or a completion receipt",
        "source_sha": manifest.data["reference"]["commit"], "owned_declared": 205, "owned_executed": 198,
        "cross_component_declared": 52, "cross_component_executed": 50,
        "unexecuted_session": sorted(DEFERRED_SESSION), "unexecuted_contact_manager": sorted(DEFERRED_CONTACT_MANAGER),
        "ignored_or_disabled_tests": 0, "reviewed_exclusions_claimed": 0, "cases": records,
    }


def source_map(manifest):
    mappings = port_map(manifest)
    declaration = re.compile(
        r"^\s*(?:(?:public|private|fileprivate|internal|static|mutating)\s+)*"
        r"(?:(enum|struct|func|var|let|case)\s+([A-Za-z_]\w*)|(init)\s*\()"
    )
    result = []
    for entry in manifest.inputs("WP-103", False):
        if entry["kind"] != "production":
            continue
        content = git("show", manifest.data["reference"]["commit"] + ":" + entry["path"])
        declarations = []
        for number, line in enumerate(content.splitlines(), 1):
            match = declaration.match(line)
            if match:
                declarations.append({
                    "kind": match[1] or "init", "name": match[2] or "init",
                    "line": number, "source_declaration": line.strip(),
                })
        outputs = [m["implementation"] for m in mappings if entry["path"] in m["sources"] and "/src/main/" in m["implementation"]]
        if not outputs:
            raise ValueError("Unmapped owned production input: " + entry["path"])
        result.append({
            "source_path": entry["path"], "source_blob_sha": entry["blob_sha"], "implementations": outputs,
            "disposition": "reuse-merged-WP-106-canonical-values" if entry["path"].endswith("/RxLogTypes.swift") else "ported-native-parser",
            "declarations_and_bindings": declarations,
        })
    if len(result) != 19:
        raise ValueError("Owned production inventory changed")
    return {
        "scope": "All original named declarations/bindings; mapping is traceability, not acceptance",
        "source_sha": manifest.data["reference"]["commit"], "owned_production_files": 19, "inputs": result,
        "read_only_consumer_context": [
            "MeshCore/Sources/MeshCore/Session/MeshCoreSession.swift",
            "MeshCore/Sources/MeshCore/Session/MeshCoreSession+BinaryProtocol.swift",
            "MeshCore/Sources/MeshCore/Session/MeshCoreSession+Regions.swift",
            "MC1Services/Sources/MC1Services/Services/RxLogService+RegionResolution.swift",
        ],
    }


def report_file(relative, expected_header, expected_rows):
    path = REPO / relative
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0] != expected_header or len(lines) - 1 != expected_rows:
        raise ValueError("Missing or malformed required graph/dependency report: " + str(relative))
    return {"path": path.relative_to(REPO).as_posix(), "rows": len(lines) - 1, "sha256_lf": canonical_hash(path)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--write", action="store_true")
    action.add_argument("--check", action="store_true")
    parser.add_argument("--base-sha", required=True)
    args = parser.parse_args()
    if args.base_sha != BASE:
        raise ValueError("An explicit, actually verified base is required; do not rebind stale evidence")
    git("merge-base", "--is-ancestor", BASE, "HEAD")
    manifest = load_manifest(REPO)
    changed = set(git("diff", "--name-only", BASE).splitlines()) | set(git("ls-files", "--others", "--exclude-standard").splitlines())
    for path in changed:
        if not permits(manifest.wp("WP-103")["write_paths"], path):
            raise ValueError("Unleased candidate write: " + path)
    totals, suites, executed = read_results()
    mappings = source_map(manifest)
    cases = source_cases(manifest, executed)
    native = REPO / "android" / "core" / "protocol" / "src"
    fingerprints = {}
    for path in sorted(native.rglob("*.kt")):
        if path.parent.name == "parser":
            fingerprints[path.relative_to(REPO).as_posix()] = canonical_hash(path)
    if len(fingerprints) != 18:
        raise ValueError("Missing parser production/test input fingerprints")
    graph = report_file(
        Path("android") / "build" / "reports" / "scaffold" / "module-graph.tsv",
        "consumer\tproducer\tconfiguration", 470,
    )
    dependencies = report_file(
        Path("android") / "build" / "reports" / "scaffold" / "runtime-dependencies.tsv",
        "artifact\tdeclared_license\tlicense_url\tlicense_pom\tlicense_pom_sha256\tlegal_gate", 113,
    )
    apk = REPO / "android" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError("The actual debug APK is absent")
    results = {
        "schema_version": 1, "scope": "Local Windows native parser proof, not parity-review, activation, legal, hardware or signing approval",
        "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-103",
        "base_sha": BASE, "source_sha": manifest.data["reference"]["commit"], "manifest_sha256": manifest.sha256,
        "semantic_policy_revision": policy_revision(manifest, load_json(REPO / "docs" / "android" / "automation-policy.json")),
        "head_binding": "Normal exact-head PR checks bind the committed head; Kotlin input fingerprints avoid a self-referential evidence commit.",
        "protocol": totals, "parser_cases": 3433, "unchanged_merged_baseline_cases": 649, "junit_suites": suites,
        "input_fingerprints_sha256_lf": fingerprints, "module_graph": graph, "runtime_dependency_inventory": dependencies,
        "debug_apk": {
            "path": apk.relative_to(REPO).as_posix(), "bytes": apk.stat().st_size,
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "device_execution_claimed": False,
        },
        "acceptance": {
            "WP-103-behavior": "Verified native parser scope against the canonical event vocabulary",
            "WP-103-boundaries": "Verified original boundaries, explicit request context and supplementary vectors",
            "WP-103-source-test-parity": "198 owned original cases execute; seven owned real-Session scenarios remain WP-107, not excluded or counted as passed",
        },
        "unproven": [
            "Seven original Session scenarios and two cross-component ContactManager scenarios",
            "The two WP-106 Session.events(filter:) wrappers remain WP-107",
            "Actual iOS/macOS execution, physical API31/API37 and radio behavior",
            "Exact Foundation locale collation outside the documented native presentation adaptation",
            "Human legal/signing/release gates and unconfigured privileged publishers",
        ],
    }
    outputs = {"source-map.json": mappings, "source-cases.json": cases, "local-results.json": results}
    for name, value in outputs.items():
        path = DIRECTORY / name
        content = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("ascii")
        if args.write:
            path.write_bytes(content)
        elif path.read_bytes().replace(b"\r\n", b"\n") != content:
            raise ValueError("Stale or changed local evidence: " + name)
    print(json.dumps({
        "result": "written" if args.write else "checked", "protocol": totals, "parser_cases": 3433,
        "owned_original_cases": 205, "executed_owned_original_cases": 198,
        "executed_cross_component_cases": 50, "source_files": 19,
        "unexecuted_session_cases": 7, "unexecuted_contact_manager_cases": 2,
        "protected_or_unleased_candidate_writes": 0, "gate_receipt_claimed": False,
    }, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, ET.ParseError, subprocess.SubprocessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        sys.exit(2)
