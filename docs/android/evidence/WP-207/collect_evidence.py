"""AndroidOnly: WP-207 Fail-closed frozen original-family and complete JVM JUnit reader."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
MODULE = ROOT / "android" / "core" / "runtime"
SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
MANIFEST = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
CASE = re.compile(r'original\("([^"]+)",\s*"([^"]+)"(?:,\s*"([^"]+)")?', re.MULTILINE)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args]).decode().strip()


def read_junit(directory, record_root=ROOT):
    require(directory.resolve().is_relative_to(record_root.resolve()), "JUnit input escapes its declared root")
    files = sorted(directory.glob("TEST-*.xml"))
    require(files, "Missing full JVM runtime JUnit")
    native = {}
    reports = []
    for path in files:
        require(path.is_file() and not path.is_symlink(), "Linked/non-file JUnit is not evidence")
        raw = path.read_bytes()
        require(len(raw) <= 16 * 1_048_576 and b"<!DOCTYPE" not in raw and b"<!ENTITY" not in raw, "Unsafe/oversized JUnit input")
        root = ET.fromstring(raw)
        nodes = root.findall("testcase")
        require(root.tag == "testsuite" and nodes, "Malformed/zero runtime JUnit")
        require(int(root.get("tests", "-1")) == len(nodes), "JUnit discovered/testcase mismatch")
        for field in ("failures", "errors", "skipped"):
            require(int(root.get(field, "-1")) == 0, "Failed/skipped full runtime suite")
        for node in nodes:
            require(not any(node.find(field) is not None for field in ("failure", "error", "skipped")),
                    "Failed/skipped testcase hidden by aggregate")
            name = node.get("name", "")
            class_name = node.get("classname", "")
            require(name and class_name.startswith("com.meshcoreone.android.core.runtime.") and name not in native,
                    "Missing/duplicate/foreign testcase identity")
            native[name] = class_name
        reports.append({
            "path": path.relative_to(record_root).as_posix(),
            "sha256": hashlib.sha256(raw).hexdigest(), "size_bytes": len(raw), "discovered": len(nodes),
        })
    return native, reports


def collect():
    manifest = json.loads((ROOT / "docs/android/port-manifest.json").read_text(encoding="utf-8"))
    semantic = hashlib.sha256(json.dumps(manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()).hexdigest()
    require(semantic == MANIFEST, "Frozen semantic manifest drift")
    require(manifest["reference"]["commit"] == SOURCE, "Frozen source drift")
    inventory = json.loads((ROOT / "docs/android/test-cases.json").read_text(encoding="utf-8"))
    owned = {entry["path"]: entry for entry in manifest["inventory"] if entry["primary_owner"] == "WP-207"}
    source_cases = {}
    for entry in inventory["entries"]:
        if entry["path"] not in owned:
            continue
        for case in entry["cases"]:
            require(case["id"] not in source_cases, "Duplicate frozen family")
            source_cases[case["id"]] = {
                "source_path": entry["path"], "source_blob": entry["blob_sha"],
                "parameter_family": case["parameter_family"],
            }
    require(len(source_cases) == 154, "Expected all 154 original declaration/parameter families")
    for path, entry in owned.items():
        actual = git("hash-object", str(ROOT.joinpath(*path.split("/"))))
        require(actual == entry["blob_sha"], "Frozen source input changed: " + path)
    declarations = {}
    input_paths = [MODULE / "build.gradle.kts", MODULE / "gradle.lockfile"]
    for path in sorted((MODULE / "src").rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        require("@Disabled" not in text and "@Ignore" not in text, "Disabled runtime assertion")
        require("// PortedFrom:" in text or "// AndroidOnly: WP-207" in text, "Missing Kotlin provenance")
        input_paths.append(path)
        if "src/test/" not in path.as_posix():
            continue
        for suite, name, signature in CASE.findall(text):
            identity = f"{suite}::{name}{signature or '()'}"
            require(identity not in declarations, "Duplicate native original family: " + identity)
            declarations[identity] = path.relative_to(ROOT).as_posix()
    require(set(declarations) == set(source_cases), "Missing/extra original families: " +
            repr(sorted(set(source_cases) - set(declarations))) + " / " +
            repr(sorted(set(declarations) - set(source_cases))))
    native, reports = read_junit(MODULE / "build/test-results/test")
    for identity in source_cases:
        matches = [name for name in native if name == identity or name.startswith(identity + " ")]
        require(len(matches) == 1, "Original family did not actually execute exactly once: " + identity)
        source_cases[identity].update({"native_source": declarations[identity], "junit_case": matches[0]})
    require(len(native) > 154, "Native lifecycle/cancellation regression assertions are mandatory")
    inputs = []
    for path in input_paths:
        require(path.is_file(), "Missing owned runtime lock/source input: " + str(path))
        raw = path.read_bytes()
        inputs.append({"path": path.relative_to(ROOT).as_posix(), "git_blob": git("hash-object", str(path)),
                       "sha256": hashlib.sha256(raw).hexdigest()})
    return {
        "schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-207",
        "head_sha": git("rev-parse", "HEAD"), "source_sha": SOURCE, "manifest_sha256": MANIFEST,
        "scope": "Full current pure-JVM runtime assertions; not Android hardware, complete graph or formal gate acceptance",
        "counts": {"discovered": len(native), "passed": len(native), "failed": 0, "errors": 0, "skipped": 0},
        "original_families": source_cases, "input_blobs": inputs, "raw_junit": reports,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = collect()
    if args.output is not None:
        require(args.output.resolve().is_relative_to(ROOT / "docs/android/evidence/WP-207") or
                not args.output.resolve().is_relative_to(ROOT), "Evidence output is outside the owning lease")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({"work_package": result["work_package"], "original_families": len(result["original_families"]),
                      "counts": result["counts"]}))


if __name__ == "__main__":
    main()
