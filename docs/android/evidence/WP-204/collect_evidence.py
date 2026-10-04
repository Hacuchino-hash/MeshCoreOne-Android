"""WP-204 exact frozen source/case and raw JUnit collector; never publishes a gate or invents a pass."""

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
MODULE = ROOT / "android" / "core" / "datastore"
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
INITIAL_BASE = "dc15f1ba445acf3230383ea68d4827c592f3fafa"
LEASE = "autonomous-WP-204-dc15f1ba"
PINNED_MANIFEST = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
PINNED_POLICY = "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"
PACKAGE = "com.meshcoreone.android.core.datastore."
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.errors import PortError


class EvidenceFailure(ValueError):
    pass


def require(condition, description):
    if not condition:
        raise EvidenceFailure(description)


def git(*arguments):
    return subprocess.check_output(["git", "-C", str(ROOT), *arguments])


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def inventory():
    sys.path.insert(0, str(ROOT / "tools" / "android-port"))
    from controller.gates import policy_revision
    from controller.model import load_manifest

    manifest = load_manifest(ROOT)
    policy = json.loads((ROOT / "docs" / "android" / "automation-policy.json").read_text(encoding="utf8"))
    require(manifest.sha256 == PINNED_MANIFEST, "Frozen manifest revision changed")
    require(policy_revision(manifest, policy) == PINNED_POLICY, "Frozen trusted policy revision changed")
    owned = [entry for entry in manifest.data["inventory"] if entry["primary_owner"] == "WP-204"]
    require(len(owned) == 8, "Expected exactly five production and three test inputs")
    inputs = []
    for entry in owned:
        raw = git("show", f"{SOURCE}:{entry['path']}")
        local = ROOT.joinpath(*entry["path"].split("/")).read_bytes()
        require(raw.replace(b"\r\n", b"\n") == local.replace(b"\r\n", b"\n"), "Reference checkout drift: " + entry["path"])
        require(git("rev-parse", f"{SOURCE}:{entry['path']}").decode().strip() == entry["blob_sha"], "Frozen input blob mismatch")
        outputs = []
        for file in sorted((MODULE / "src").rglob("*.kt")):
            text = file.read_text(encoding="utf8")
            if f"// PortedFrom: {entry['path']}@{SOURCE}" in text:
                outputs.append(file.relative_to(ROOT).as_posix())
        require(outputs, "Missing primary source mapping: " + entry["path"])
        inputs.append({**entry, "source_sha256": digest(raw), "native_files": outputs})

    catalog = json.loads((ROOT / "docs" / "android" / "test-cases.json").read_text(encoding="utf8"))
    require(catalog["source_sha"] == SOURCE, "Original catalog reference mismatch")
    tests = {entry["path"] for entry in owned if entry["kind"] == "test"}
    expected = {
        case["id"]: {"source": entry["path"], "blob_sha": entry["blob_sha"], **case}
        for entry in catalog["entries"] if entry["path"] in tests for case in entry["cases"]
    }
    require(len(expected) == 34, "Expected every original declaration, not a generated filename count")
    detail_path = "docs/android/evidence/WP-004/inventory-details.json"
    raw_details = git("show", f"{INITIAL_BASE}:{detail_path}")
    actual_details = ROOT.joinpath(*detail_path.split("/")).read_bytes()
    require(raw_details.replace(b"\r\n", b"\n") == actual_details.replace(b"\r\n", b"\n"), "Trusted original family inventory drift")
    details = json.loads(raw_details)
    for entry in details["files"]:
        if entry["path"] not in tests:
            continue
        for case in entry["cases"]:
            require(case["parameter_family"] == expected[case["id"]]["parameter_family"], "Original family digest mismatch")
            expected[case["id"]]["inputs"] = case["inputs"]
    return inputs, expected


def declared_tests():
    methods = set()
    original = {}
    for file in sorted((MODULE / "src" / "test" / "kotlin").rglob("*.kt")):
        text = file.read_text(encoding="utf8")
        require("@Ignore" not in text, "An ignored suite is not evidence")
        classes = re.findall(r"^class\s+(\w+Test)\b", text, re.MULTILINE)
        for name in classes:
            for method in re.findall(r"@Test\s+fun\s+(\w+)\s*\(", text):
                identity = PACKAGE + name, method
                require(identity not in methods, "Duplicate native test identity")
                methods.add(identity)
            pattern = r'@OriginalCase\("([^"\n]+)"(?:,\s*(\d+))?\)\s+@Test\s+fun\s+(\w+)\s*\('
            matches = list(re.finditer(pattern, text))
            for index, match in enumerate(matches):
                source, parameters, method = match.groups()
                require(source not in original, "Duplicate original case binding: " + source)
                end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
                body = text[match.end():end]
                original[source] = {
                    "native_class": PACKAGE + name, "native_method": method,
                    "parameter_count": int(parameters or "1"),
                    "literal_parameter_rows": [
                        json.loads("[" + row + "]") for row in re.findall(r"\blistOf\(([^()]*)\)", body)
                        if row.strip()
                    ],
                }
    require(methods, "No committed unit tests discovered from module sources")
    return methods, original


def junit(directory):
    files = sorted(directory.glob("TEST-*.xml"))
    require(files, "Missing actual testDebugUnitTest XML; declarations are not executed passes")
    cases = {}
    reports = []
    for file in files:
        raw = file.read_bytes()
        require(len(raw) <= 16 * 1_048_576 and b"<!DOCTYPE" not in raw.upper() and b"<!ENTITY" not in raw.upper(),
                "Oversized or unsafe raw JUnit")
        root = ET.fromstring(raw)
        require(root.tag == "testsuite", "Malformed JUnit root")
        nodes = root.findall("testcase")
        require(nodes and int(root.attrib["tests"]) == len(nodes), "Missing/zero/mismatched actual testcase nodes")
        for outcome in ("failures", "errors", "skipped"):
            require(int(root.attrib[outcome]) == 0, "A failed, errored or skipped case is not a pass")
        for node in nodes:
            require(not any(node.find(kind) is not None for kind in ("failure", "error", "skipped")), "Nonpassing actual case")
            key = node.attrib["classname"], node.attrib["name"]
            require(key not in cases, "Duplicate raw JUnit case")
            cases[key] = {
                "class": key[0], "name": key[1], "outcome": "passed",
                "junit_path": file.relative_to(ROOT).as_posix(), "junit_sha256": digest(raw),
            }
        reports.append({
            "path": file.relative_to(ROOT).as_posix(), "sha256": digest(raw), "bytes": len(raw), "cases": len(nodes),
        })
    return cases, reports


def collect(report_directory=None):
    inputs, expected = inventory()
    methods, original = declared_tests()
    require(set(original) == set(expected), "Missing or extra frozen source-case declarations")
    require(sum(case["parameter_count"] for case in original.values()) == 41, "Missing original parameter scenarios")
    for identity, specification in expected.items():
        family = specification["inputs"]
        binding = original[identity]
        count = family["declared_count"] if family is not None else 1
        require(binding["parameter_count"] == count, "Original parameter-family count mismatch: " + identity)
        if family is not None:
            require(len(family["axes"]) == 1, "Unsupported original family axis")
            rows = [json.loads(row) for row in family["axes"][0]["declared_rows"]]
            require(rows in binding["literal_parameter_rows"], "Actual Kotlin family does not retain source parameter rows: " + identity)
    cases, reports = junit(report_directory or MODULE / "build" / "test-results" / "testDebugUnitTest")
    require(methods == set(cases), "Complete native source test set differs from actual raw JUnit")
    source_cases = []
    for identity, specification in expected.items():
        binding = original[identity]
        hit = cases.get((binding["native_class"], binding["native_method"]))
        require(hit is not None, "Missing original family execution: " + identity)
        source_cases.append({**specification, **binding, "native_test": hit})
    input_files = [MODULE / "build.gradle.kts", MODULE / "gradle.lockfile"] + sorted(
        file for file in (MODULE / "src").rglob("*") if file.is_file()
    )
    native_inputs = [{
        "path": file.relative_to(ROOT).as_posix(), "bytes": file.stat().st_size,
        "canonical_lf_sha256": digest(file.read_bytes().replace(b"\r\n", b"\n")),
    } for file in input_files]
    paths = [row["path"] for row in native_inputs]
    committed = not git("diff", "--name-only", "HEAD", "--", *paths).strip() and not git(
        "ls-files", "--others", "--exclude-standard", "--", *paths,
    ).strip()
    return {
        "schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-204",
        "owner": "data-persistence-engineer", "lease": LEASE,
        "app_session": "00cbcd6f-1b3b-4e40-8d44-2ce4216eb72e",
        "native_project_alias": "13956b48-f450-42b5-8c63-778fae11425d",
        "project": "663db92c-ed50-4a77-aded-bda85a7c503a",
        "branch": git("branch", "--show-current").decode().strip(),
        "initial_lease_base_sha": INITIAL_BASE, "observed_head_sha": git("rev-parse", "HEAD").decode().strip(),
        "source_sha": SOURCE, "manifest_sha256": PINNED_MANIFEST, "policy_revision": PINNED_POLICY,
        "inputs": inputs, "source_cases": source_cases, "junit_suites": reports,
        "native_cases": list(cases.values()),
        "native_input_files": native_inputs,
        "native_inputs_committed_at_observed_head": committed,
        "discovery": {
            "declared_native_cases": len(methods), "discovered_passed": len(cases), "failed": 0, "errors": 0, "skipped": 0,
            "original_declarations": 34, "parameter_expanded_scenarios": 41,
        },
        "scope": "actual DataStore/JCA/SDK31 shadow assertions; not real Android Keystore hardware, API37 device, "
                 "full bidirectional backup, license approval, hosted-run authority or a protected gate receipt",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    try:
        result = collect()
        if args.write:
            (OUT / "local-evidence.json").write_text(json.dumps(result, indent=2, ensure_ascii=True) + "\n", encoding="utf8")
            raw_directory = OUT / "local-junit"
            raw_directory.mkdir(exist_ok=True)
            expected_raw = set()
            for suite in result["junit_suites"]:
                source = ROOT.joinpath(*suite["path"].split("/"))
                expected_raw.add(source.name)
                (raw_directory / source.name).write_bytes(source.read_bytes())
                require(digest((raw_directory / source.name).read_bytes()) == suite["sha256"],
                        "Retained raw local JUnit changed")
            require({file.name for file in raw_directory.glob("TEST-*.xml")} == expected_raw,
                    "Unexpected/stale retained local suite")
        print(json.dumps({"result": "passed", "discovery": result["discovery"]}, indent=2))
        return 0
    except (PortError, EvidenceFailure, OSError, ValueError, KeyError, ET.ParseError, subprocess.SubprocessError) as failure:
        print(f"BLOCKED: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
