"""WP-202 exact original-case/raw-Room evidence; never a gate or acceptance publisher."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
OUT = Path(__file__).resolve().parent
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
INITIAL_BASE = "dc15f1ba445acf3230383ea68d4827c592f3fafa"
BASE = "2cf00464950e1fb9aae0dd913402eb3e12dc0044"
TESTS = ROOT / "android" / "core" / "data" / "src" / "test" / "kotlin"
REPORTS = ROOT / "android" / "core" / "data" / "build" / "test-results" / "testDebugUnitTest"
PREFIX = "com.meshcoreone.android.core.data.repository."
DECLARATION = re.compile(r"(?P<annotations>(?:\s*@(?:Test\b|OriginalCase\([^\n]*\))\s*)+)\s*fun\s+(?P<method>\w+)\s*\(")
ORIGINAL = re.compile(r'@OriginalCase\("([^"\n]+)"(?:,\s*"([^"\n]+)")?\)')
REVIEWED_MIGRATIONS = "coordinator-reviewed-native-equivalent-Apple-historical-only-exclusion-execution-pending"
MAX_JUNIT_BYTES = 8 * 1024 * 1024


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def digest(data):
    return hashlib.sha256(data).hexdigest()


def inventory():
    manifest = json.loads((ROOT / "docs" / "android" / "port-manifest.json").read_text(encoding="utf8"))
    catalog = json.loads((ROOT / "docs" / "android" / "test-cases.json").read_text(encoding="utf8"))
    if catalog["source_sha"] != SOURCE or manifest["reference"]["commit"] != SOURCE:
        raise ValueError("Reference pin drift")
    owned = [entry for entry in manifest["inventory"] if entry["primary_owner"] == "WP-202"]
    kinds = {kind: sum(entry["kind"] == kind for entry in owned) for kind in ("production", "test", "support")}
    if kinds != {"production": 26, "test": 13, "support": 3}:
        raise ValueError("Primary ownership changed")
    paths = {entry["path"] for entry in owned if entry["kind"] == "test"}
    cases = [
        {"source": entry["path"], "blob_sha": entry["blob_sha"], **case}
        for entry in catalog["entries"] if entry["path"] in paths for case in entry["cases"]
    ]
    if len(cases) != 208 or len({case["id"] for case in cases}) != 208:
        raise ValueError("Missing/duplicate original declarations")
    for entry in owned:
        if git("rev-parse", f"{SOURCE}:{entry['path']}").decode().strip() != entry["blob_sha"]:
            raise ValueError("Primary source blob mismatch: " + entry["path"])
        raw = git("show", f"{SOURCE}:{entry['path']}")
        actual = ROOT.joinpath(*entry["path"].split("/")).read_bytes()
        if raw.replace(b"\r\n", b"\n") != actual.replace(b"\r\n", b"\n"):
            raise ValueError("Immutable primary source drift: " + entry["path"])
    return owned, cases


def test_declarations():
    for path in sorted(TESTS.rglob("*.kt")):
        content = path.read_text(encoding="utf8")
        classes = re.findall(r"^\s*class\s+(\w+)\s*:", content, re.MULTILINE)
        if not classes:
            continue
        if len(classes) != 1:
            raise ValueError("Ambiguous testcase class: " + path.name)
        for declaration in DECLARATION.finditer(content):
            if "@Test" not in declaration["annotations"]:
                continue
            yield path, PREFIX + classes[0], declaration


def bindings():
    found = {}
    for path, classname, declaration in test_declarations():
        for identity, disposition in ORIGINAL.findall(declaration["annotations"]):
            if identity in found:
                raise ValueError("Duplicate original binding: " + identity)
            found[identity] = {
                "class": classname, "name": declaration["method"],
                "evidence_kind": disposition or "source-behavior",
                "path": path.relative_to(ROOT).as_posix(),
            }
    return found


def require_complete_native_cases(actual):
    declared = [(classname, declaration["method"]) for _, classname, declaration in test_declarations()]
    if not declared or len(declared) != len(set(declared)):
        raise ValueError("Zero/duplicate declared native test identities")
    expected = set(declared)
    if set(actual) != expected:
        raise ValueError("Incomplete or stale native suite; missing=" + repr(sorted(expected - set(actual))) +
                         "; unexpected=" + repr(sorted(set(actual) - expected)))
    return len(expected)


def migration_dispositions(originals):
    path = OUT / "migration-dispositions.json"
    proposal = json.loads(path.read_text(encoding="utf8"))
    if proposal["status"] != REVIEWED_MIGRATIONS or proposal["reference_sha"] != SOURCE:
        raise ValueError("Migration disposition lacks the bounded coordinator decision")
    decision = proposal["coordinator_decision"]
    if decision["session"] != "bcb17a74-5fa6-47d0-a4be-b6b595e20559" or decision["reviewed_checkpoint"] != "db02af0b50c4221d95d71616b0ce1c9f491a97c0":
        raise ValueError("Migration decision session/checkpoint mismatch")
    expected = {case["id"]: case for case in originals}
    proposed = {}
    native_path = ROOT.joinpath(*proposal["native_test_file"].split("/"))
    native_text = native_path.read_text(encoding="utf8")
    for case in proposal["cases"]:
        identity = case["id"]
        if identity in proposed or identity not in expected:
            raise ValueError("Missing/duplicate/unknown migration proposal identity")
        if any(case[field] != expected[identity][field] for field in ("source", "blob_sha", "parameter_family")):
            raise ValueError("Migration disposition source/blob/family mismatch")
        if not re.search(r"@Test\s+fun\s+" + re.escape(case["native_method"]) + r"\s*\(", native_text):
            raise ValueError("Proposed native assertion is absent: " + identity)
        if not all(case.get(field) for field in ("source_behavior", "native_assertions", "rationale", "proposed_disposition")):
            raise ValueError("Missing case-specific migration rationale")
        proposed[identity] = case
    if len(proposed) != 18:
        raise ValueError("Incomplete eighteen-case migration proposal")
    return proposed


def bounded_junit_bytes(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_JUNIT_BYTES:
        raise ValueError("Unsafe or oversized JUnit: " + path.name)
    with path.open("rb") as stream:
        raw = stream.read(MAX_JUNIT_BYTES + 1)
    # Declaration tokens remain ASCII after removing UTF-16/32 interleaving zero bytes.
    declaration_bytes = raw.replace(b"\x00", b"").upper()
    if len(raw) > MAX_JUNIT_BYTES or b"<!DOCTYPE" in declaration_bytes or b"<!ENTITY" in declaration_bytes:
        raise ValueError("Unsafe or oversized JUnit: " + path.name)
    return raw


def raw_suites():
    files = sorted(REPORTS.glob("TEST-*.xml"))
    if not files:
        raise ValueError("Missing actual native testDebugUnitTest XML")
    cases, suites = {}, []
    for path in files:
        raw = bounded_junit_bytes(path)
        root = ET.fromstring(raw)
        nodes = root.findall("testcase")
        if root.tag != "testsuite" or not nodes:
            raise ValueError("Malformed/zero native suite: " + path.name)
        declared = {key: int(root.attrib[key]) for key in ("tests", "failures", "errors", "skipped")}
        if declared != {"tests": len(nodes), "failures": 0, "errors": 0, "skipped": 0}:
            raise ValueError("Failed/skipped/inconsistent native suite: " + path.name)
        for node in nodes:
            identity = (node.attrib["classname"], node.attrib["name"])
            if not all(identity) or identity in cases or not identity[0].startswith(PREFIX):
                raise ValueError("Missing/duplicate/foreign native case identity")
            if any(node.find(kind) is not None for kind in ("failure", "error", "skipped")):
                raise ValueError("Raw case failed/skipped despite suite counts")
            cases[identity] = {"class": identity[0], "name": identity[1], "outcome": "passed"}
        suites.append({"path": path.relative_to(ROOT).as_posix(), "sha256": digest(raw), "cases": len(nodes)})
    return cases, suites


def report():
    owned, originals = inventory()
    mapped = bindings()
    expected = {case["id"] for case in originals}
    unknown = set(mapped) - expected
    if unknown:
        raise ValueError("Uncataloged original bindings: " + repr(sorted(unknown)))
    migrations = migration_dispositions(originals)
    proposal = json.loads((OUT / "migration-dispositions.json").read_text(encoding="utf8"))
    for identity, case in migrations.items():
        if identity in mapped:
            raise ValueError("Reviewed native adaptation cannot be relabeled literal source behavior: " + identity)
        mapped[identity] = {
            "class": proposal["native_test_class"], "name": case["native_method"],
            "path": proposal["native_test_file"],
            "evidence_kind": "coordinator-reviewed-native-equivalent-and-Apple-historical-only-exclusion",
            "disposition": case,
        }
    missing = expected - set(mapped)
    if missing:
        raise ValueError("Unresolved original/native-equivalent dispositions: " + repr(sorted(missing)))
    native, suites = raw_suites()
    require_complete_native_cases(native)
    for case in originals:
        binding = mapped[case["id"]]
        identity = (binding["class"], binding["name"])
        if identity not in native:
            raise ValueError("Missing actual passed assertion: " + case["id"])
        case.update(binding)
        case["native_test"] = native[identity]
    implementation_paths = git("ls-files", "--", "android/core/data/src/main/kotlin",
                               "android/core/data/src/test/kotlin").decode().splitlines()
    source_map = []
    for entry in owned:
        outputs = []
        for relative in implementation_paths:
            text = ROOT.joinpath(*relative.split("/")).read_text(encoding="utf8")
            if f"// PortedFrom: {entry['path']}@{SOURCE}" in text:
                outputs.append(relative)
        if entry["path"] == "MC1Services/Sources/MC1Services/Services/PersistenceStore+Migration.swift":
            source_map.append({**entry, "implementation_files": [proposal["native_test_file"]],
                               "evidence_kind": "eighteen-coordinator-reviewed-v1-adaptations-not-Apple-upgrades",
                               "disposition_file": "docs/android/evidence/WP-202/migration-dispositions.json"})
        elif not outputs:
            raise ValueError("Missing primary input implementation/disposition: " + entry["path"])
        else:
            source_map.append({**entry, "implementation_files": outputs,
                               "evidence_kind": "traceability-only-see-actual-original-and-native-assertions"})
    schema_path = ROOT / "android" / "core" / "database" / "schemas" / (
        "com.meshcoreone.android.core.database.MeshCoreDatabase") / "1.json"
    raw = schema_path.read_bytes()
    schema = json.loads(raw)["database"]
    if schema["version"] != 1 or len(schema["entities"]) != 17 or schema["identityHash"] != "847554c8d7ca15f1f881ba6bccce4121":
        raise ValueError("Initial native schema drift")
    return {
        "schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-202",
        "initial_base_sha": INITIAL_BASE, "base_sha": BASE,
        "head_sha": git("rev-parse", "HEAD").decode().strip(), "source_sha": SOURCE,
        "scope": "actual local Room/JUnit only; not formal review, hardware, signing or compatible backup restore",
        "primary_inputs": source_map, "original_cases": originals, "junit_suites": suites,
        "native_cases": list(native.values()),
        "discovery": {"discovered": len(native), "passed": len(native), "failed": 0, "errors": 0, "skipped": 0},
        "schema": {"version": 1, "entities": 17, "identity_hash": schema["identityHash"],
                   "canonical_lf_sha256": digest(raw.replace(b"\r\n", b"\n"))},
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory-only", action="store_true")
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    if args.inventory_only:
        _, cases = inventory()
        mapped = bindings()
        proposed = migration_dispositions(cases)
        missing = [case["id"] for case in cases if case["id"] not in mapped]
        if set(missing) != set(proposed):
            raise ValueError("Unbound declarations do not match the exact pending migration proposal")
        print(json.dumps({"scope": "source/annotation inventory only; no executed assertions",
                          "originals": len(cases), "bound": len(mapped),
                          "coordinator_reviewed_native_dispositions": len(proposed),
                          "bounded_code_adaptation_decision": True,
                          "native_execution_established": False,
                          "execution_pending": missing}, indent=2))
        return
    result = report()
    if args.write:
        (OUT / "local-evidence.json").write_text(json.dumps(result, indent=2, ensure_ascii=True) + "\n", encoding="utf8")
    print(json.dumps({"result": "passed", "discovery": result["discovery"], "originals": len(result["original_cases"])}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, ET.ParseError, subprocess.SubprocessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        sys.exit(2)
