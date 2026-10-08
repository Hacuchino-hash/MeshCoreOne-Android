"""AndroidOnly: WP-003 Complete, nonzero scaffold reports and exact-run aggregation."""

import csv
import re
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

from .ci_environment import file_sha256
from .apk_alignment import elf_load_alignment
from .errors import PortError
from .gates import Binding
from .schema import fields, load_json, positive_integer

SUITES = {
    "build-logic": ("build-logic/convention/build/test-results/test", 31),
    "contracts": ("core/contracts/build/test-results/test", 4),
    "app": ("app/build/test-results/testDebugUnitTest", 10),
    "room-verification": ("scaffold/room-verification/build/test-results/testDebugUnitTest", 2),
}
LINT_TARGETS = (
    "app", "benchmark", "scaffold/room-verification",
    *("core/" + name for name in (
        "database", "datastore", "data", "ble", "connectivity",
        "designsystem", "ui", "maps", "l10n", "testing",
    )),
    *("feature/" + name for name in ("onboarding", "chats", "nodes", "remotenodes", "map", "tools", "settings")),
    *("platform/" + name for name in ("notifications", "widgets", "shortcuts", "translation")),
)
STAGES = {"verify", "standalone", "assemble", "lint"}
PYTHON_MINIMUMS = {"controller": 111, "scaffold": 15}
APK_FIELDS = {
    "scope", "artifact", "sha256", "size_bytes", "package", "min_sdk", "target_sdk",
    "permissions", "pinned_notices", "verification_fixture_packaged", "native_libraries",
    "native_16kb_compatibility_verified", "static_16kb_alignment_verified",
    "elf_pt_load_alignment", "physical_device_or_native_runtime_verified",
}


def read_xml(path: Path):
    if not path.is_file() or path.is_symlink() or path.stat().st_size > 8 * 1024 * 1024:
        raise PortError(f"Missing/unsafe/oversized mandatory XML report: {path.name}")
    data = path.read_bytes()
    if len(data) > 8 * 1024 * 1024 or b"<!DOCTYPE" in data or b"<!ENTITY" in data:
        raise PortError("Oversized/unsafe evidence XML")
    try:
        return ET.fromstring(data)
    except ET.ParseError as error:
        raise PortError(f"Malformed evidence XML: {path.name}") from error


def counts(value: dict, *, minimum=1):
    fields(value, {"discovered", "run", "passed", "failed", "errors", "skipped"}, label="test discovery")
    if any(type(number) is not int or number < 0 for number in value.values()):
        raise PortError("Malformed test-discovery counts")
    if (
        value["discovered"] < minimum or value["run"] != value["discovered"]
        or value["passed"] != value["discovered"]
        or value["failed"] or value["errors"] or value["skipped"]
    ):
        raise PortError("Missing, zero, reduced, failed or skipped mandatory assertions")
    return value


def suite_counts(directory: Path, minimum: int):
    files = sorted(directory.glob("TEST-*.xml"))
    if not files:
        raise PortError(f"Missing mandatory test reports: {directory}")
    total = 0
    identities = set()
    for path in files:
        suite = read_xml(path)
        if suite.tag != "testsuite":
            raise PortError("Malformed JUnit suite")
        actual = suite.findall("testcase")
        try:
            declared = {name: int(suite.attrib[name]) for name in ("tests", "failures", "errors", "skipped")}
        except (KeyError, ValueError) as error:
            raise PortError("Missing/malformed JUnit counts") from error
        if declared != {"tests": len(actual), "failures": 0, "errors": 0, "skipped": 0} or not actual:
            raise PortError("Zero, failed, skipped or inconsistent JUnit suite")
        for case in actual:
            identity = (case.get("classname"), case.get("name"))
            if not all(identity) or identity in identities:
                raise PortError("Missing/duplicate actual test-case identity")
            identities.add(identity)
            if any(case.find(outcome) is not None for outcome in ("failure", "error", "skipped")):
                raise PortError("Actual JUnit case failed/skipped despite claimed counts")
        total += len(actual)
    return counts({
        "discovered": total, "run": total, "passed": total,
        "failed": 0, "errors": 0, "skipped": 0,
    }, minimum=minimum)


def compare_discovery_tsv(report: Path, result: dict):
    if not report.is_file() or report.is_symlink() or report.stat().st_size > 65535:
        raise PortError("Missing/unsafe/oversized scaffold discovery TSV")
    with report.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != ["suite", "discovered", "passed", "failed", "errors", "skipped"]:
            raise PortError("Malformed scaffold discovery output contract")
        rows = list(reader)
    if len(rows) != len(SUITES) or {r["suite"] for r in rows} != set(SUITES):
        raise PortError("Missing/duplicate mandatory scaffold suites")
    for row in rows:
        if set(row) != set(reader.fieldnames) or any(value is None for value in row.values()):
            raise PortError("Malformed scaffold discovery TSV row")
        try:
            actual = {name: int(row[name]) for name in ("discovered", "passed", "failed", "errors", "skipped")}
        except (ValueError, TypeError, KeyError) as error:
            raise PortError("Malformed scaffold discovery TSV counts") from error
        if actual != {name: result[row["suite"]][name] for name in actual}:
            raise PortError("Scaffold discovery TSV disagrees with actual JUnit cases")


def collect_suites(android: Path):
    result = {name: suite_counts(android / path, minimum) for name, (path, minimum) in SUITES.items()}
    compare_discovery_tsv(android / "build" / "reports" / "scaffold" / "test-discovery.tsv", result)
    return result


def lint_evidence(report: Path):
    root = read_xml(report)
    if (
        root.tag != "issues" or set(root.attrib) != {"format", "by"}
        or root.get("format") != "6" or not root.get("by", "").startswith("lint ")
        or any(child.tag != "issue" for child in root)
    ):
        raise PortError("Malformed Android Lint XML")
    issues = root.findall("issue")
    if any(issue.get("severity") in ("Error", "Fatal") for issue in issues):
        raise PortError("Android lint contains actual errors")
    if any(
        issue.get("severity") not in ("Warning", "Information")
        or not issue.get("id") or not issue.get("message")
        for issue in issues
    ):
        raise PortError("Unknown/missing lint severity")
    return {"sha256": file_sha256(report), "warnings": len(issues)}


def collect_lint(android: Path):
    reports = {}
    for target in LINT_TARGETS:
        report = android / target / "build" / "reports" / "lint-results-debug.xml"
        reports[target] = lint_evidence(report)
    return reports


def lint_bundle_path(target: str):
    return "lint/" + target.replace("/", "--") + ".xml"


def report_tsv(path: Path, columns: list[str]):
    if not path.is_file() or path.is_symlink() or path.stat().st_size > 8 * 1024 * 1024:
        raise PortError("Missing/unsafe/oversized mandatory TSV report")
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != columns:
            raise PortError("Malformed mandatory TSV header")
        rows = list(reader)
    if not rows or len(rows) > 100000:
        raise PortError("Empty/oversized mandatory TSV report")
    for row in rows:
        if set(row) != set(columns) or any(not isinstance(v, str) or not v.strip() for v in row.values()):
            raise PortError("Malformed mandatory TSV row")
    if len({tuple(row[column] for column in columns) for row in rows}) != len(rows):
        raise PortError("Duplicate mandatory TSV row")
    return rows


def validate_graph_runtime(root: Path):
    graph = report_tsv(root / "module-graph.tsv", ["consumer", "producer", "configuration"])
    module = re.compile(r":[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*")
    if any(module.fullmatch(row["consumer"]) is None or module.fullmatch(row["producer"]) is None
           or any(character.isspace() for character in row["configuration"]) for row in graph):
        raise PortError("Malformed actual module-graph identifiers")
    runtime = report_tsv(root / "runtime-dependencies.tsv", [
        "artifact", "declared_license", "license_url", "license_pom", "license_pom_sha256", "legal_gate",
    ])
    if len({row["artifact"] for row in runtime}) != len(runtime):
        raise PortError("Duplicate runtime component")
    for row in runtime:
        if (
            row["artifact"].count(":") != 2 or row["license_pom"].count(":") != 2
            or re.fullmatch(r"[0-9a-f]{64}", row["license_pom_sha256"]) is None
            or not row["license_url"].startswith(("https://", "http://"))
            or row["legal_gate"] != "human-review-pending"
        ):
            raise PortError("Malformed runtime license provenance or invented legal approval")


# Exact merged debug-APK permissions (WP-206 core:connectivity plus the scaffold receiver permission).
EXPECTED_APK_PERMISSIONS = [
    "android.permission.BLUETOOTH_CONNECT",
    "android.permission.BLUETOOTH_SCAN",
    "android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.ACCESS_LOCAL_NETWORK",
    "com.meshcoreone.android.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
]


def validate_apk_inspection(value: dict, root: Path):
    fields(value, APK_FIELDS, label="actual APK inspection")
    if (
        value["artifact"] != "android/app/build/outputs/apk/debug/app-debug.apk"
        or value["package"] != "com.meshcoreone.android.debug"
        or type(value["min_sdk"]) is not int or value["min_sdk"] != 31
        or type(value["target_sdk"]) is not int or value["target_sdk"] != 37
        or value["verification_fixture_packaged"] is not False
        or value["static_16kb_alignment_verified"] is not True
        or value["native_16kb_compatibility_verified"] is not False
        or value["physical_device_or_native_runtime_verified"] is not False
        or value["permissions"] != EXPECTED_APK_PERMISSIONS
        or value["pinned_notices"] != ["assets/licenses/GPL-3.0.txt", "assets/licenses/MeshCore-MIT.txt", "assets/licenses/Apache-2.0.txt"]
        or not isinstance(value["scope"], str) or not value["scope"].strip()
        or type(value["size_bytes"]) is not int or value["size_bytes"] < 1
        or not isinstance(value["sha256"], str) or re.fullmatch(r"[0-9a-f]{64}", value["sha256"]) is None
    ):
        raise PortError("Missing/mismatched typed debug APK inspection")
    apk = root / "debug.apk"
    if apk.stat().st_size != value["size_bytes"] or file_sha256(apk) != value["sha256"]:
        raise PortError("Inspected APK size/digest disagrees with the uploaded bytes")
    try:
        with zipfile.ZipFile(apk) as archive:
            members = archive.infolist()
            if (
                len(members) > 50000 or len({m.filename for m in members}) != len(members)
                or sum(m.file_size for m in members) > 512 * 1024 * 1024
                or any(m.file_size > 128 * 1024 * 1024 for m in members)
                or archive.testzip() is not None
            ):
                raise PortError("APK archive is malformed/oversized/duplicated")
            names = archive.namelist()
            if "AndroidManifest.xml" not in names or "classes.dex" not in names:
                raise PortError("Actual APK manifest/dex is missing")
            if any(name not in names or not archive.read(name) for name in value["pinned_notices"]):
                raise PortError("Actual APK notices are missing")
            for member in members:
                if re.fullmatch(r"classes\d*\.dex", member.filename):
                    data = archive.read(member)
                    if any(prefix in data for prefix in (
                        b"com/meshcoreone/android/scaffold/roomverification",
                        b"com/meshcoreone/android/core/testing",
                    )):
                        raise PortError("Test fixture/helper is packaged despite claimed absence")
            libraries = [name for name in names if name.startswith("lib/") and name.endswith(".so")]
            actual = {name: elf_load_alignment(archive.read(name)) for name in libraries}
    except zipfile.BadZipFile as error:
        raise PortError("Uploaded debug APK is not a valid ZIP archive") from error
    if value["native_libraries"] != libraries or value["elf_pt_load_alignment"] != actual:
        raise PortError("Actual APK native libraries/alignment disagree with inspection")


def artifact_record(root: Path, path: Path):
    if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root.resolve()):
        raise PortError("Evidence artifact escapes its bounded bundle")
    return {"path": path.relative_to(root).as_posix(), "size": path.stat().st_size, "sha256": file_sha256(path)}


def validate_result(value: dict, root: Path, binding: Binding, run_id: int, run_attempt: int, host: str):
    fields(value, {
        "schema_version", "binding", "run_id", "run_attempt", "host", "scope",
        "stages", "python", "suites", "standalone", "lint", "apk", "artifacts",
        "module_unit_tests",
    }, label="scaffold CI result")
    if (
        type(value["schema_version"]) is not int or value["schema_version"] != 2
        or Binding.parse(value["binding"]) != binding or value["host"] != host
        or value["run_id"] != run_id or value["run_attempt"] != run_attempt
    ):
        raise PortError("CI evidence belongs to a different repository/base/head/source/policy/run/host")
    if not isinstance(value["stages"], dict) or set(value["stages"]) != STAGES:
        raise PortError("Missing required build stage")
    if any(v != "success" for v in value["stages"].values()):
        raise PortError("Failed/cancelled/skipped build stage is not success")
    if (
        not isinstance(value["python"], dict) or not isinstance(value["suites"], dict)
        or set(value["python"]) != set(PYTHON_MINIMUMS) or set(value["suites"]) != set(SUITES)
    ):
        raise PortError("Missing required Python/Kotlin discovery")
    for name, (_, minimum) in SUITES.items():
        counts(value["suites"][name], minimum=minimum)
    counts(value["standalone"], minimum=31)
    for name, minimum in PYTHON_MINIMUMS.items():
        counts(value["python"][name], minimum=minimum)
    if not isinstance(value["lint"], dict) or set(value["lint"]) != set(LINT_TARGETS):
        raise PortError("Missing mandatory Android lint target")
    if not isinstance(value["artifacts"], list) or not value["artifacts"]:
        raise PortError("Missing immutable evidence artifacts")
    seen = set()
    for record in value["artifacts"]:
        fields(record, {"path", "size", "sha256"}, label="CI artifact")
        path = record["path"]
        if (
            not isinstance(path, str) or "\\" in path or path.startswith("/")
            or ".." in Path(path).parts or ":" in path or path in seen
            or not isinstance(record["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", record["sha256"])
            or type(record["size"]) is not int or record["size"] < 0
        ):
            raise PortError("Unsafe/duplicate/malformed CI artifact")
        seen.add(path)
        if artifact_record(root, root / path) != record:
            raise PortError("Evidence artifact content/size/checksum mismatch")
    required = {"debug.apk", "test-discovery.tsv", "module-graph.tsv", "runtime-dependencies.tsv", "apk-inspection.json"}
    if not required.issubset(seen):
        raise PortError("Missing mandatory debug/report artifacts")
    actual_suites = {name: suite_counts(root / "junit" / "composite" / name, minimum)
                     for name, (_, minimum) in SUITES.items()}
    actual_standalone = suite_counts(root / "junit" / "standalone" / "build-logic", 31)
    if actual_suites != value["suites"] or actual_standalone != value["standalone"]:
        raise PortError("Claimed discovery disagrees with complete raw JUnit cases/outcomes")
    compare_discovery_tsv(root / "test-discovery.tsv", actual_suites)
    validate_graph_runtime(root)
    raw_paths = {path.relative_to(root).as_posix() for path in (root / "junit").rglob("TEST-*.xml")}
    raw_paths |= {lint_bundle_path(target) for target in LINT_TARGETS}
    if not raw_paths.issubset(seen):
        raise PortError("Raw mandatory JUnit/lint report is absent from the digest-bound artifact set")
    from .ci_environment import REPO
    from .module_junit import validate_module_tests

    validate_module_tests(value["module_unit_tests"], root, REPO, binding.head_sha)
    for target in LINT_TARGETS:
        claimed = value["lint"][target]
        fields(claimed, {"sha256", "warnings"}, label="typed lint evidence")
        if (
            type(claimed["warnings"]) is not int or claimed["warnings"] < 0
            or not isinstance(claimed["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", claimed["sha256"])
            or lint_evidence(root / lint_bundle_path(target)) != claimed
        ):
            raise PortError("Typed lint result disagrees with the actual raw XML/hash")
    inspection = load_json(root / "apk-inspection.json", maximum_bytes=65535)
    if inspection != value["apk"]:
        raise PortError("Raw APK inspection is stale or disagrees with the CI result")
    validate_apk_inspection(inspection, root)
    return value


def aggregate(needs: dict, directory: Path, binding: Binding, run_id: int, run_attempt: int):
    positive_integer(run_id, "Actual workflow run")
    positive_integer(run_attempt, "Actual workflow attempt")
    if set(needs) != {"build"} or needs["build"].get("result") != "success":
        raise PortError("Required build job failed, cancelled, skipped or absent")
    paths = sorted(directory.rglob("ci-result.json"))
    if len(paths) != 1:
        raise PortError("Exactly one Linux immutable CI result artifact is mandatory")
    results = {}
    for path in paths:
        value = load_json(path)
        host = value.get("host")
        if host != "linux" or host in results:
            raise PortError("Missing/duplicate required execution host")
        results[host] = validate_result(value, path.parent, binding, run_id, run_attempt, host)
    if set(results) != {"linux"}:
        raise PortError("The Linux execution host is required")
    return {
        "result": "success", "binding": __import__("dataclasses").asdict(binding),
        "run_id": run_id, "run_attempt": run_attempt,
        "hosts": {host: {
            "kotlin_assertions": sum(c["passed"] for c in value["suites"].values()),
            "standalone_assertions": value["standalone"]["passed"],
            "module_unit_assertions": sum(item["counts"]["passed"] for item in value["module_unit_tests"].values()),
            "python_assertions": sum(c["passed"] for c in value["python"].values()),
            "apk_sha256": value["apk"]["sha256"],
        } for host, value in results.items()},
        "scope": "scaffold build evidence only; not parity-review, gate-integrity, human approval or WP completion",
    }
