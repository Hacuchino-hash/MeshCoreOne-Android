"""AndroidOnly: WP-203 Original source bindings and complete raw native backup evidence."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
BASE = "e697823c8937eb5b12a40362ca2c5aae4c45f56a"
PACKAGE = "com.meshcoreone.android.core.data.backup."
TESTS = ROOT / "android" / "core" / "data" / "src" / "test" / "kotlin" / "com" / "meshcoreone" / "android" / "core" / "data" / "backup"
REPORTS = ROOT / "android" / "core" / "data" / "build" / "test-results" / "testDebugUnitTest"
DECLARATION = re.compile(r'(?P<annotations>(?:\s*@(?:Test\b|OriginalCase\("(?:[^"\\]|\\.)*"\))\s*)+)\s*fun\s+(?P<method>\w+)\s*\(')
BINDING = re.compile(r'@OriginalCase\("((?:[^"\\]|\\.)*)"\)')
MAX_XML = 8 * 1024 * 1024


def git(*arguments):
    return subprocess.check_output(["git", "--no-pager", "-C", str(ROOT), *arguments])


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def inventory():
    manifest = json.loads((ROOT / "docs" / "android" / "port-manifest.json").read_text(encoding="utf8"))
    catalog = json.loads((ROOT / "docs" / "android" / "test-cases.json").read_text(encoding="utf8"))
    if manifest["reference"]["commit"] != SOURCE or catalog["source_sha"] != SOURCE:
        raise ValueError("Frozen reference pin drift")
    owned = [entry for entry in manifest["inventory"] if entry["primary_owner"] == "WP-203"]
    if {kind: sum(row["kind"] == kind for row in owned) for kind in ("production", "test", "support")} != {
        "production": 9, "test": 4, "support": 2,
    }:
        raise ValueError("Primary WP-203 input scope drift")
    paths = {row["path"] for row in owned if row["kind"] == "test"}
    cases = [{"source": row["path"], "blob_sha": row["blob_sha"], **case}
             for row in catalog["entries"] if row["path"] in paths for case in row["cases"]]
    if len(cases) != 171 or len({case["id"] for case in cases}) != 171:
        raise ValueError("Original backup declarations missing or duplicated")
    for row in owned:
        if git("rev-parse", f"{SOURCE}:{row['path']}").decode().strip() != row["blob_sha"]:
            raise ValueError("Frozen primary blob mismatch")
        expected = git("show", f"{SOURCE}:{row['path']}").replace(b"\r\n", b"\n")
        actual = ROOT.joinpath(*row["path"].split("/")).read_bytes().replace(b"\r\n", b"\n")
        if expected != actual:
            raise ValueError("Frozen primary bytes changed: " + row["path"])
    return owned, cases


def declarations():
    found, native, inputs = {}, set(), []
    for path in sorted(TESTS.glob("*.kt")):
        raw = path.read_bytes()
        text = raw.decode("utf8")
        rows = [match for match in DECLARATION.finditer(text) if "@Test" in match["annotations"]]
        if not rows:
            continue
        classes = re.findall(r"^class\s+(\w+)\b", text, re.MULTILINE)
        if len(classes) != 1:
            raise ValueError("Ambiguous owning backup test class")
        classname = PACKAGE + classes[0]
        relative = path.relative_to(ROOT).as_posix()
        inputs.append({"path": relative, "sha256": digest(raw), "canonical_lf_sha256": digest(raw.replace(b"\r\n", b"\n")),
                       "git_blob": git("hash-object", "--", str(path)).decode().strip()})
        for row in rows:
            identity = (classname, row["method"])
            if identity in native:
                raise ValueError("Duplicate declared native backup case")
            native.add(identity)
            for encoded in BINDING.findall(row["annotations"]):
                case = json.loads('"' + encoded + '"')
                if case in found:
                    raise ValueError("Duplicate original binding: " + case)
                found[case] = {"class": classname, "name": row["method"], "native_source": relative}
    if not native:
        raise ValueError("Zero declared native backup assertions")
    return found, native, inputs


def bounded_xml(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_XML:
        raise ValueError("Unsafe or oversized native JUnit")
    with path.open("rb") as stream:
        raw = stream.read(MAX_XML + 1)
    normalized = raw.replace(b"\0", b"").upper()
    if len(raw) > MAX_XML or b"<!DOCTYPE" in normalized or b"<!ENTITY" in normalized:
        raise ValueError("Unsafe or oversized native JUnit")
    return raw


def suites(expected):
    files = sorted(REPORTS.glob("TEST-*.xml"))
    if not files:
        raise ValueError("Missing actual native backup test reports")
    cases, reports, module_cases = {}, [], set()
    for path in files:
        raw = bounded_xml(path)
        root = ET.fromstring(raw)
        nodes = root.findall("testcase")
        if root.tag != "testsuite" or not nodes:
            raise ValueError("Malformed or zero-test module suite")
        counts = {key: int(root.attrib[key]) for key in ("tests", "failures", "errors", "skipped")}
        if counts != {"tests": len(nodes), "failures": 0, "errors": 0, "skipped": 0}:
            raise ValueError("Failed/skipped/mismatched actual module suite")
        owned = 0
        for node in nodes:
            identity = (node.attrib["classname"], node.attrib["name"])
            if not all(identity) or identity in module_cases:
                raise ValueError("Missing/duplicate actual module identity")
            if any(node.find(tag) is not None for tag in ("failure", "error", "skipped")):
                raise ValueError("Actual failed/skipped case cannot be evidence")
            module_cases.add(identity)
            if identity[0].startswith(PACKAGE):
                if identity not in expected:
                    raise ValueError("Unexpected/stale native backup case")
                cases[identity] = {"class": identity[0], "name": identity[1], "outcome": "passed"}
                owned += 1
        if owned:
            if owned != len(nodes):
                raise ValueError("Ambiguous mixed ownership suite")
            reports.append({"path": path.relative_to(ROOT).as_posix(), "size": len(raw), "sha256": digest(raw), "tests": owned})
    if set(cases) != expected:
        raise ValueError("Incomplete actual backup suite: " + repr(sorted(expected - set(cases))))
    return cases, reports, len(module_cases)


def committed_revisions():
    head = git("rev-parse", "HEAD").decode().strip()
    base = git("merge-base", "HEAD", "origin/main").decode().strip()
    if not all(re.fullmatch("[0-9a-f]{40}", value) for value in (base, head)):
        raise ValueError("Non-immutable candidate base/HEAD")
    return base, head


def collect():
    owned, originals = inventory()
    bindings, expected, inputs = declarations()
    if set(bindings) != {case["id"] for case in originals}:
        raise ValueError("Missing/unknown original backup dispositions: " + repr(sorted({case["id"] for case in originals} - set(bindings))))
    executed, reports, module_count = suites(expected)
    base, head = committed_revisions()
    for case in originals:
        binding = bindings[case["id"]]
        case.update(binding)
        case["executed"] = executed[(binding["class"], binding["name"])]
    changed = git("status", "--porcelain=v1", "--untracked-files=all", "--",
                  "android/core/data", "android/core/database", "docs/android/evidence/WP-203",
                  "docs/android/evidence/WP-202").decode().strip()
    return {
        "schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-203",
        "initial_base_sha": BASE, "base_sha": base, "head_sha": head, "source_sha": SOURCE,
        "scope": "actual native backup assertions only; cross-direction oracle and formal acceptance are separate",
        "binding_state": "working-tree-not-exact-head" if changed else "exact-committed-head",
        "primary_inputs": owned, "original_cases": originals, "test_inputs": inputs,
        "junit_suites": reports, "native_cases": list(executed.values()),
        "discovery": {"discovered": len(executed), "passed": len(executed), "failures": 0, "errors": 0, "skipped": 0},
        "whole_module_discovered": module_count,
    }


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory-only", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    if args.inventory_only:
        _, originals = inventory()
        bindings, native, _ = declarations()
        expected = {case["id"] for case in originals}
        if set(bindings) - expected:
            raise ValueError("Unknown original binding")
        value = {"scope": "inventory only, not executed assertions", "originals": len(originals), "bound": len(bindings),
                 "native_declared": len(native), "unbound": sorted(expected - set(bindings))}
    else:
        value = collect()
    output = json.dumps(value, indent=2, ensure_ascii=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf8")
    else:
        print(output, end="")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, KeyError, ET.ParseError, subprocess.SubprocessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        sys.exit(2)
