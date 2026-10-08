"""AndroidOnly: WP-218 Full raw retention and immutable, fail-closed original-family execution."""

import argparse
from dataclasses import asdict
import hashlib
import json
from pathlib import Path
import re
import platform
import sys
import unittest
import uuid
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools" / "android-port"))

from controller.errors import PortError
from controller.gates import Binding, policy_revision
from controller.model import git, load_manifest, tree
from controller.module_junit import bounded_directory, linked, safe_reports
from controller.schema import decode_json, fields, positive_integer
from controller.test_runner import run_suite
from test_inventory import parse_file
import source_bindings

SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
SOURCE_TREE = "8918fdc604341e6996a68c88f6bb1c02b9c2f87e"
APPROVED_BASE = "7e2835bad2c03dfb5a088063655f9fc4dbafd00f"
REPOSITORY = "cbattlegear/MeshCoreOne-Android"
EVIDENCE = "docs/android/evidence/WP-218/"
REPORTS = {
    "services": "android/core/services/build/test-results/test",
    "app": "android/app/build/test-results/testDebugUnitTest",
}
PREFIXES = {
    "services": "com.meshcoreone.android.core.services.content.",
    "app": "com.meshcoreone.android.app.content.",
}
NATIVE_INPUTS = ("android/core/services/", "android/app/")
TEST_INPUTS = (
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/content/",
    "android/app/src/test/kotlin/com/meshcoreone/android/app/content/",
)
CONTROL_INPUTS = {
    "docs/android/port-manifest.json", "docs/android/port-manifest.schema.json",
    "docs/android/test-cases.json", "docs/android/automation-policy.json",
    EVIDENCE + "collect_evidence.py", EVIDENCE + "source_bindings.py",
    EVIDENCE + "test_content_evidence.py",
    "android/gradle/dependency-locks/core-services.lockfile",
    "android/gradle/dependency-locks/app.lockfile",
    "android/gradle/libs.versions.toml", "android/gradle/verification-metadata.xml",
    "android/build-logic/convention/src/main/kotlin/com/meshcoreone/buildlogic/BuildConventions.kt",
}
TEXT = {".kt", ".kts", ".py", ".json", ".xml", ".swift", ".txt", ".toml", ".lockfile"}
MAX_REPORT_BYTES = 8 * 1024 * 1024
MAX_RAW_BYTES = 128 * 1024 * 1024
MAX_REPORTS = 4096
SCOPE = "Actual source-family/native execution; not WP acceptance, independent review, hardware or legal approval."


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def canonical(path, raw):
    return raw.replace(b"\r\n", b"\n") if Path(path).suffix in TEXT else raw


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


def input_paths(entries, manifest_data):
    owned = [item for item in manifest_data["inventory"] if item["primary_owner"] == "WP-218"]
    native = {
        path for path in entries if path.startswith(NATIVE_INPUTS)
        and (path.endswith("build.gradle.kts") or "/src/main/" in path or "/src/test/" in path)
    }
    dependencies = {
        path for path in entries
        if path.startswith(("android/core/protocol/", "android/core/model/", "android/core/contracts/"))
        and (path.endswith("build.gradle.kts") or "/src/main/" in path)
    }
    return sorted(CONTROL_INPUTS | native | dependencies | {item["path"] for item in owned})


def capture(repo, output, *, invocation=None, pretest=None, modules=("services", "app")):
    repo, output = repo.resolve(), output.resolve()
    bounded_directory(output, output.parent)
    if repo == output or repo.is_relative_to(output):
        raise PortError("Raw evidence output would overwrite the owning repository")
    if output.exists():
        raise PortError("Raw content snapshot must be newly created")
    output.mkdir(parents=True)
    head = git(repo, "rev-parse", "HEAD").decode("ascii").strip()
    entries = tree(repo, head)
    records, total, retention_errors = [], 0, []
    for module in modules:
        if module not in REPORTS:
            raise PortError("Unknown raw content module")
        directory = repo / REPORTS[module]
        try:
            files = safe_reports(directory, repo)
        except PortError as error:
            retention_errors.append(str(error))
            continue
        if len(files) > MAX_REPORTS:
            raise PortError("Too many raw JUnit reports")
        for path in files:
            if path.stat().st_size > MAX_REPORT_BYTES:
                raise PortError("Oversized raw JUnit report: " + path.name)
            raw = path.read_bytes()
            total += len(raw)
            if total > MAX_RAW_BYTES:
                raise PortError("Raw JUnit snapshot exceeds its byte bound")
            destination = output / "junit" / module / path.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(raw)
            records.append({
                "module": module, "source": path.relative_to(repo).as_posix(),
                "path": destination.relative_to(output).as_posix(), "size_bytes": len(raw), "sha256": sha(raw),
            })
    manifest_data = decode_json((repo / "docs/android/port-manifest.json").read_text(encoding="utf-8"))
    inputs = {}
    for path in input_paths(entries, manifest_data):
        source = repo / path
        bounded_directory(source.parent, repo)
        if linked(source) or not source.is_file():
            raise PortError("Missing/unsafe raw compiled/control input: " + path)
        raw = source.read_bytes()
        destination = output / "inputs" / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(raw)
        inputs[path] = {"blob_sha": entries.get(path), "sha256": sha(raw), "size_bytes": len(raw)}
    invocation_record = None
    if invocation is not None:
        if linked(invocation) or not invocation.is_file() or invocation.stat().st_size > 65535:
            raise PortError("Missing/unsafe/oversized actual executor invocation")
        raw = invocation.read_bytes()
        (output / "invocation.json").write_bytes(raw)
        invocation_record = {"sha256": sha(raw), "size_bytes": len(raw)}
    pretest_record = None
    if pretest is not None:
        if linked(pretest) or not pretest.is_file() or pretest.stat().st_size > 2 * 1024 * 1024:
            raise PortError("Missing/unsafe pre-test content binding")
        raw = pretest.read_bytes()
        (output / "pretest-binding.json").write_bytes(raw)
        pretest_record = {"sha256": sha(raw), "size_bytes": len(raw)}
    value = {
        "schema_version": 1, "work_package": "WP-218", "head_sha": head,
        "scope": "Complete raw produced bytes retained before JUnit parsing or any verification verdict.",
        "inputs": inputs, "reports": records, "invocation": invocation_record,
        "retention_errors": retention_errors,
        "pretest": pretest_record,
    }
    write_json(output / "raw-snapshot.json", value)
    if retention_errors:
        raise PortError("Missing/unsafe mandatory raw reports; available bytes retained: " + "; ".join(retention_errors))
    return value


def immutable_inputs(repo, snapshot, output, manifest):
    head = snapshot["head_sha"]
    entries = tree(repo, head)
    expected = input_paths(entries, manifest.data)
    if set(snapshot["inputs"]) != set(expected):
        raise PortError("Missing/extra raw compiled/control inputs")
    if git(repo, "ls-files", "--others", "-z", "--", *[path.rstrip("/") for path in TEST_INPUTS],
           "android/core/services/src/main", "android/app/src/main").strip(b"\0"):
        raise PortError("Uncommitted compiled content inputs")
    result = {}
    for path in expected:
        record = snapshot["inputs"][path]
        raw = (output / "inputs" / path).read_bytes()
        if record != {"blob_sha": entries.get(path), "sha256": sha(raw), "size_bytes": len(raw)} or path not in entries:
            raise PortError("Stale/tampered raw compiled input: " + path)
        committed = git(repo, "cat-file", "blob", entries[path])
        if canonical(path, raw) != canonical(path, committed):
            raise PortError("Compiled/evidence checkout differs from its immutable Git input: " + path)
        result[path] = raw
    return result


def prepare_binding(repo, invocation, output):
    if platform.system() != "Linux":
        raise PortError("Content pre-test binding must be prepared by the actual Linux executor")
    raw = invocation.read_bytes()
    value = decode_json(raw.decode("utf-8"))
    fields(value, {"schema_version", "stage", "identity", "host"}, label="actual pre-test executor invocation")
    if value["schema_version"] != 1 or value["host"] != "linux" or value["stage"] != "verify":
        raise PortError("Only the declared actual Linux verify executor can prepare content evidence")
    head = git(repo, "rev-parse", "HEAD").decode("ascii").strip()
    manifest = load_manifest(repo)
    entries = tree(repo, head)
    paths = input_paths(entries, manifest.data)
    if any(path not in entries for path in paths) or git(repo, "diff", "HEAD", "--name-only", "--", *paths).strip():
        raise PortError("Pre-test compiled/control inputs must match the actual immutable candidate")
    policy = decode_json((repo / "docs/android/automation-policy.json").read_text(encoding="utf-8"))
    record = {
        "schema_version": 1, "execution_kind": "local" if value["identity"] is None else "hosted",
        "local_run_id": str(uuid.uuid4()) if value["identity"] is None else None,
        "started_at": datetime.now(timezone.utc).isoformat(),
        "head_sha": head, "tree_sha": git(repo, "rev-parse", head + "^{tree}").decode("ascii").strip(),
        "approved_base_sha": APPROVED_BASE, "source_sha": SOURCE, "manifest_sha256": manifest.sha256,
        "policy_revision": policy_revision(manifest, policy), "forwarded_invocation_sha256": sha(raw),
        "inputs": {path: entries[path] for path in paths},
        "scope": "Actual pre-test local/hosted candidate identity; local runs have no GitHub/cold-cache authority.",
    }
    if git(repo, "merge-base", APPROVED_BASE, head).decode("ascii").strip() != APPROVED_BASE:
        raise PortError("Candidate lost the explicitly approved work-package base")
    if output.exists():
        raise PortError("Pre-test binding must not overwrite an earlier invocation")
    output.parent.mkdir(parents=True, exist_ok=True)
    write_json(output, record)
    return record


def invocation_binding(repo, snapshot, output, manifest, policy):
    if snapshot["invocation"] is None:
        raise PortError("Actual executor invocation/base/head/run binding is mandatory")
    raw = (output / "invocation.json").read_bytes()
    if snapshot["invocation"] != {"sha256": sha(raw), "size_bytes": len(raw)}:
        raise PortError("Actual executor invocation bytes changed")
    value = decode_json(raw.decode("utf-8"))
    fields(value, {"schema_version", "stage", "identity", "host"}, label="actual content invocation")
    if type(value["schema_version"]) is not int or value["schema_version"] != 1 or value["stage"] != "verify" or value["host"] != "linux":
        raise PortError("Content acceptance requires the declared actual Linux verify invocation")
    identity = value["identity"]
    if identity is None:
        if platform.system() != "Linux" or snapshot.get("pretest") is None:
            raise PortError("Local content evidence requires the actual pre-test Linux candidate binding")
        pretest_raw = (output / "pretest-binding.json").read_bytes()
        if snapshot["pretest"] != {"sha256": sha(pretest_raw), "size_bytes": len(pretest_raw)}:
            raise PortError("Pre-test binding bytes changed")
        pretest = decode_json(pretest_raw.decode("utf-8"))
        fields(pretest, {
            "schema_version", "execution_kind", "local_run_id", "started_at", "head_sha", "tree_sha",
            "approved_base_sha", "source_sha", "manifest_sha256", "policy_revision",
            "forwarded_invocation_sha256", "inputs", "scope",
        }, label="pre-test local content binding")
        try:
            local_id = str(uuid.UUID(pretest["local_run_id"]))
        except (ValueError, TypeError, AttributeError) as error:
            raise PortError("Local content run has no actual invocation UUID") from error
        if (
            pretest["schema_version"] != 1 or pretest["execution_kind"] != "local"
            or local_id != pretest["local_run_id"] or pretest["head_sha"] != snapshot["head_sha"]
            or pretest["tree_sha"] != git(repo, "rev-parse", snapshot["head_sha"] + "^{tree}").decode("ascii").strip()
            or pretest["approved_base_sha"] != APPROVED_BASE or pretest["source_sha"] != SOURCE
            or pretest["manifest_sha256"] != manifest.sha256
            or pretest["policy_revision"] != policy_revision(manifest, policy)
            or pretest["forwarded_invocation_sha256"] != sha(raw)
            or pretest["inputs"] != {path: record["blob_sha"] for path, record in snapshot["inputs"].items()}
        ):
            raise PortError("Stale/mismatched actual local pre-test code/tree/source/input binding")
        return {
            "binding": asdict(Binding(REPOSITORY, "WP-218", APPROVED_BASE, snapshot["head_sha"],
                                     SOURCE, manifest.sha256, policy_revision(manifest, policy))),
            "execution_kind": "local", "local_run_id": local_id, "run_id": None, "run_attempt": None,
            "host": "linux", "scope": "Local warm candidate execution only; no hosted/cold-cache acceptance.",
        }
    fields(identity, {"binding", "run_id", "run_attempt"}, label="actual content run")
    bound = Binding.parse(identity["binding"])
    positive_integer(identity["run_id"], "Actual content run")
    positive_integer(identity["run_attempt"], "Actual content run attempt")
    if (
        bound.repository != REPOSITORY or bound.work_package != "WP-003"
        or bound.head_sha != snapshot["head_sha"] or bound.source_sha != SOURCE
        or bound.manifest_sha256 != manifest.sha256 or bound.policy_revision != policy_revision(manifest, policy)
    ):
        raise PortError("Stale/mismatched content repository/head/source/manifest/policy binding")
    if git(repo, "rev-parse", "--verify", bound.base_sha + "^{commit}").decode("ascii").strip() != bound.base_sha:
        raise PortError("Content invocation base is not an immutable commit")
    if git(repo, "merge-base", bound.base_sha, bound.head_sha).decode("ascii").strip() != bound.base_sha:
        raise PortError("Content invocation base is not an ancestor of the actual candidate")
    if git(repo, "merge-base", APPROVED_BASE, bound.head_sha).decode("ascii").strip() != APPROVED_BASE:
        raise PortError("Content candidate lost the approved owning-worktree lineage")
    return {"binding": asdict(bound), "run_id": identity["run_id"], "run_attempt": identity["run_attempt"], "host": value["host"]}


def original_cases(manifest, raw_inputs):
    if manifest.data["reference"]["commit"] != SOURCE or manifest.data["reference"]["tree_sha"] != SOURCE_TREE:
        raise PortError("Frozen content behavior/oracle source changed")
    owned = [item for item in manifest.data["inventory"] if item["primary_owner"] == "WP-218"]
    if len(owned) != 34 or sum(item["kind"] == "test" for item in owned) != 12:
        raise PortError("Frozen WP-218 source/test/resource ownership changed")
    catalog = decode_json(raw_inputs["docs/android/test-cases.json"].decode("utf-8"))
    fields(catalog, {"schema_version", "source_sha", "entries"}, label="original content catalog")
    if type(catalog["schema_version"]) is not int or catalog["schema_version"] != 1 or catalog["source_sha"] != SOURCE:
        raise PortError("Stale original content catalog")
    paths = [entry["path"] for entry in catalog["entries"]]
    if len(set(paths)) != len(paths):
        raise PortError("Duplicate original catalog path")
    by_path = {entry["path"]: entry for entry in catalog["entries"]}
    result = []
    for item in owned:
        raw = canonical(item["path"], raw_inputs[item["path"]])
        blob = hashlib.sha1(b"blob " + str(len(raw)).encode("ascii") + b"\0" + raw).hexdigest()
        if blob != item["blob_sha"]:
            raise PortError("Frozen original content input changed: " + item["path"])
        if item["kind"] != "test":
            continue
        entry = by_path.get(item["path"])
        if entry is None or entry["blob_sha"] != item["blob_sha"] or entry["has_assertions"] is not True:
            raise PortError("Missing/changed original content test blob")
        parsed = parse_file(raw.decode("utf-8"))
        cases = parsed["cases"]
        if [{"id": case["id"], "parameter_family": case["parameter_family"]} for case in cases] != entry["cases"]:
            raise PortError("Original content declarations/parameter families differ from their pinned catalog")
        for case in cases:
            if case["assertion_status"] not in ("direct", "helper"):
                raise PortError("Original content case has no actual assertions")
            result.append({"source": item["path"], "blob_sha": item["blob_sha"], **case})
    keys = {(case["scope"], case["method"]) for case in result}
    if len(result) != 154 or len(keys) != 154 or (set(source_bindings.ALIASES) | set(source_bindings.BLOCKED)) - keys:
        raise PortError("Missing/extra frozen source154 declaration or binding")
    return result


def junit(snapshot, output):
    cases, totals = {}, {}
    for record in snapshot["reports"]:
        fields(record, {"module", "source", "path", "size_bytes", "sha256"}, label="raw content report")
        module = record["module"]
        filename = Path(record["source"]).name
        if (
            module not in REPORTS or not filename.startswith("TEST-") or not filename.endswith(".xml")
            or record["source"] != REPORTS[module] + "/" + filename
            or record["path"] != "junit/" + module + "/" + filename
        ):
            raise PortError("Unexpected/escaping raw content report path")
        report = output / record["path"]
        bounded_directory(report.parent, output)
        if linked(report) or not report.is_file() or report.stat().st_size > MAX_REPORT_BYTES:
            raise PortError("Missing/unsafe/oversized retained JUnit")
        raw = report.read_bytes()
        if sha(raw) != record["sha256"] or len(raw) != record["size_bytes"]:
            raise PortError("Retained raw JUnit changed")
        declarations = raw.replace(b"\0", b"").upper()
        if b"<!DOCTYPE" in declarations or b"<!ENTITY" in declarations:
            raise PortError("Unsafe raw JUnit declarations")
        try:
            root = ET.fromstring(raw)
        except ET.ParseError as error:
            raise PortError("Malformed raw JUnit: " + record["path"]) from error
        nodes = root.findall("testcase")
        try:
            counters = {name: int(root.attrib[name]) for name in ("tests", "failures", "errors", "skipped")}
        except (KeyError, ValueError) as error:
            raise PortError("Malformed/missing actual JUnit counters") from error
        if root.tag != "testsuite" or not nodes or counters != {"tests": len(nodes), "failures": 0, "errors": 0, "skipped": 0}:
            raise PortError("Zero, failed, skipped or inconsistent actual content JUnit")
        for node in nodes:
            key = (record["module"], node.get("classname"), node.get("name"))
            if not all(key) or key in cases or any(node.find(kind) is not None for kind in ("failure", "error", "skipped")):
                raise PortError("Missing/duplicate/failed/skipped actual native content case")
            cases[key] = {"module": key[0], "class": key[1], "name": key[2], "report": record["path"]}
        totals[record["module"]] = totals.get(record["module"], 0) + len(nodes)
    if set(totals) != set(REPORTS) or not all(totals.values()):
        raise PortError("Missing mandatory App/Services raw discovery")
    return cases, totals


def require_native_discovery(raw_inputs, cases):
    declared = 0
    for path, raw in raw_inputs.items():
        if not path.startswith(TEST_INPUTS) or not path.endswith(".kt"):
            continue
        text = raw.decode("utf-8")
        annotations = re.findall(r"^\s*@Test(?:\s|$)", text, re.MULTILINE)
        names = re.findall(r"@Test\s+fun\s+(?:`([^`]+)`|(\w+))\s*\(", text)
        if len(annotations) != len(names):
            raise PortError("Unaccounted/unsupported native content test declaration: " + path)
        package = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
        classes = re.findall(r"\bclass\s+(\w+)", text)
        if names and (package is None or not classes):
            raise PortError("Missing native content test package/class")
        for first, second in names:
            method = first or second
            sdks = configured_sdks(text)
            hits = [
                key for key in cases
                if key[1] in {package[1] + "." + name for name in classes}
                and runtime_method(key[2]) == method
            ]
            if len(hits) != (len(sdks) or 1):
                raise PortError("Missing/ambiguous actual declared native content test: " + path + "::" + method)
            if sdks and {runtime_sdk(key[2], max(sdks)) for key in hits} != sdks:
                raise PortError("Missing/reduced actual native content platform API: " + path + "::" + method)
            declared += len(hits)
    if declared == 0:
        raise PortError("Zero discovered committed native content assertions")
    return declared


def configured_sdks(text):
    match = re.search(r"@Config\s*\(\s*sdk\s*=\s*\[([\d,\s]+)\]", text)
    return {int(value) for value in re.findall(r"\d+", match[1])} if match else set()


def runtime_method(name):
    return re.sub(r"\[\d+\]$", "", name.removesuffix("()"))


def runtime_sdk(name, default):
    match = re.search(r"\[(\d+)\]$", name.removesuffix("()"))
    return int(match[1]) if match else default


def native_method(raw_inputs, native_class, method):
    files = [
        (path, raw.decode("utf-8")) for path, raw in raw_inputs.items()
        if path.startswith(TEST_INPUTS) and path.endswith(".kt")
    ]
    hits = []
    for path, text in files:
        package = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
        classes = re.findall(r"\bclass\s+(\w+)", text)
        if package is None or native_class not in {package[1] + "." + name for name in classes}:
            continue
        names = re.findall(r"@Test\s+fun\s+(?:`([^`]+)`|(\w+))\s*\(", text)
        if method in {first or second for first, second in names}:
            hits.append((path, text))
    if len(hits) != 1:
        raise PortError("Missing/ambiguous committed native assertion binding: " + native_class + "::" + method)
    return hits[0]


def source_execution(originals, cases, raw_inputs):
    result = []
    for original in originals:
        binding, blocker = source_bindings.target(original)
        row = {
            "source": original["source"], "blob_sha": original["blob_sha"], "id": original["id"],
            "parameter_family": original["parameter_family"], "inputs": original["inputs"],
            "native_cases": [], "blocker": blocker,
        }
        if binding is not None:
            module, class_name, method = binding
            native_class = PREFIXES[module] + class_name
            hits = [
                value for (candidate_module, candidate_class, name), value in cases.items()
                if candidate_module == module and candidate_class == native_class
                and runtime_method(name) == method
            ]
            path, text = native_method(raw_inputs, native_class, method)
            sdks = configured_sdks(text)
            if len(hits) != (len(sdks) or 1):
                row["blocker"] = "Missing/ambiguous executed original native case: " + native_class + "::" + method
            else:
                if original["inputs"] is not None:
                    axes = original["inputs"]["axes"]
                    if len(axes) != 1 or axes[0]["kind"] != "literal-collection":
                        raise PortError("Unsupported original content parameter-family proof")
                    declaration = re.search(r"fun\s+`" + re.escape(method) + r"`\s*\(", text)
                    body = text[declaration.end():].split("@Test", 1)[0]
                    values = re.search(r"for\s*\(\s*extension\s+in\s+listOf\(([^)]*)\)", body)
                    if values is None or re.findall(r'"[^"]*"', values[1]) != axes[0]["declared_rows"]:
                        raise PortError("Missing/reduced original image-extension parameter rows")
                row["native_cases"] = [
                    {**hit, "input": path, "platform_api": runtime_sdk(hit["name"], max(sdks)) if sdks else None}
                    for hit in hits
                ]
        result.append(row)
    return result


def verify(repo, output, snapshot):
    manifest = load_manifest(repo)
    raw_inputs = immutable_inputs(repo, snapshot, output, manifest)
    policy = decode_json(raw_inputs["docs/android/automation-policy.json"].decode("utf-8"))
    invocation = invocation_binding(repo, snapshot, output, manifest, policy)
    originals = original_cases(manifest, raw_inputs)
    cases, counts = junit(snapshot, output)
    declared = require_native_discovery(raw_inputs, cases)
    executions = source_execution(originals, cases, raw_inputs)
    gaps = [row for row in executions if row["blocker"] is not None]
    value = {
        "schema_version": 1, "work_package": "WP-218", "scope": SCOPE,
        "invocation": invocation, "head_sha": snapshot["head_sha"], "source_sha": SOURCE,
        "result": "BLOCKED" if gaps else "PASS", "inputs": snapshot["inputs"], "raw_reports": snapshot["reports"],
        "native_counts": counts, "original_families": len(originals),
        "declared_content_tests_executed": declared,
        "executed_families": len(originals) - len(gaps), "source_cases": executions,
    }
    write_json(output / "content-result.json", value)
    if gaps:
        raise PortError(f"{len(gaps)} of 154 original content families remain blocked: " +
                        "; ".join(row["id"] + ": " + row["blocker"] for row in gaps))
    return value


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--invocation", type=Path)
    parser.add_argument("--pretest", type=Path)
    parser.add_argument("--prepare-binding", action="store_true")
    parser.add_argument("--retain-only", choices=tuple(REPORTS))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.self_test:
            suite = unittest.defaultTestLoader.discover(str(Path(__file__).parent), pattern="test_content_evidence.py")
            if run_suite(suite, verbosity=1):
                return 2
            if args.output is None:
                return 0
        if args.output is None or not args.output.is_absolute():
            raise PortError("Explicit absolute content raw output is mandatory")
        if args.prepare_binding:
            if args.invocation is None:
                raise PortError("Actual executor invocation is required before native tests")
            prepared = prepare_binding(args.repo, args.invocation, args.output)
            print(json.dumps({key: prepared[key] for key in (
                "execution_kind", "local_run_id", "head_sha", "tree_sha", "source_sha",
            )} | {"inputs_bound_before_tests": len(prepared["inputs"])}))
            return 0
        modules = (args.retain_only,) if args.retain_only else tuple(REPORTS)
        snapshot = capture(args.repo, args.output, invocation=args.invocation, pretest=args.pretest, modules=modules)
        if args.retain_only:
            print(json.dumps({"raw_snapshot": str(args.output / "raw-snapshot.json"),
                              "reports_retained": len(snapshot["reports"]), "verification_verdict": None}))
            return 0
        if args.output.resolve().is_relative_to(args.repo.resolve()):
            raise PortError("Source verification output must be outside product/build inputs")
        value = verify(args.repo, args.output, snapshot)
        print(json.dumps({"result": value["result"], "original_families": value["original_families"],
                          "native_counts": value["native_counts"], "scope": SCOPE}))
        return 0
    except (PortError, OSError, UnicodeError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
