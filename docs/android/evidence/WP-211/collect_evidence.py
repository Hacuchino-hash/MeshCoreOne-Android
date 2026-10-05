"""AndroidOnly: WP-211 Frozen-family, complete raw-JUnit and immutable-input evidence reader."""

import argparse
import hashlib
import json
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
OUT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.gates import policy_revision
from controller.model import Manifest
from controller.schema import load_json

SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
MANIFEST = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
POLICY = "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"
BASE = "7e2835bad2c03dfb5a088063655f9fc4dbafd00f"
LEASE = "autonomous-WP-211-d147865c"
DEVICE = "com.meshcoreone.android.core.services.device."
ROOM_CLASS = "com.meshcoreone.android.core.data.repository.DeviceSettingsRoomTest"
PACKAGE = Path("com") / "meshcoreone" / "android" / "core" / "services" / "device"
MAIN = ROOT / "android" / "core" / "services" / "src" / "main" / "kotlin" / PACKAGE
TEST = ROOT / "android" / "core" / "services" / "src" / "test" / "kotlin" / PACKAGE
ROOM_PATH = Path("android") / "core" / "data" / "src" / "test" / "kotlin"
ROOM_PATH = ROOM_PATH / "com" / "meshcoreone" / "android" / "core" / "data" / "repository" / "DeviceSettingsRoomTest.kt"
QUOTED = r'"(?:\\.|[^"\\])*"'
ORIGINAL = re.compile(rf'\boriginal(?:Async)?\(\s*({QUOTED})\s*,\s*({QUOTED})(?:\s*,\s*({QUOTED}))?')
NATIVE = re.compile(rf'\bnative(?:Case|Async)\(\s*({QUOTED})')
ROOM_BINDING = re.compile(rf'@DeviceSettingsSourceCase\(({QUOTED})\)\s*@Test\s+fun\s+(\w+)\s*\(')
ROOM_METHOD = re.compile(r'@Test\s+fun\s+(\w+)\s*\(')
MAX_XML_BYTES = 16 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args]).decode("utf-8").strip()


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def string(literal):
    require("${" not in literal, "Dynamic original suite/name is not an immutable identity")
    return json.loads(literal)


def frozen_families():
    manifest = Manifest(load_json(ROOT / "docs" / "android" / "port-manifest.json"), {}, ROOT)
    policy = load_json(ROOT / "docs" / "android" / "automation-policy.json")
    require(manifest.sha256 == MANIFEST and policy_revision(manifest, policy) == POLICY, "Manifest/policy drift")
    require(manifest.data["reference"]["commit"] == SOURCE, "Frozen source revision drift")
    primary = {entry["path"]: entry for entry in manifest.data["inventory"] if entry["primary_owner"] == "WP-211"}
    require(len(primary) == 24 and sum(e["kind"] == "test" for e in primary.values()) == 13, "Primary input ownership drift")
    for path, entry in primary.items():
        require(git("rev-parse", SOURCE + ":" + path) == entry["blob_sha"], "Frozen primary blob drift: " + path)
        require(git("hash-object", str(ROOT.joinpath(*path.split("/")))) == entry["blob_sha"], "Source checkout drift: " + path)
    details = load_json(ROOT / "docs" / "android" / "evidence" / "WP-004" / "inventory-details.json")
    require(details["source_sha"] == SOURCE, "Original parameter inventory source drift")
    families = {}
    for file in details["files"]:
        if file["path"] not in primary or file["kind"] != "test":
            continue
        require(file["blob_sha"] == primary[file["path"]]["blob_sha"], "Original family blob drift")
        for case in file["cases"]:
            require(case["id"] not in families, "Duplicate original family")
            inputs = case["inputs"]
            rows = inputs["axes"][0]["declared_rows"] if inputs else None
            if inputs:
                require(len(inputs["axes"]) == 1 and inputs["combination"] == "collection", "Undeclared parameter-family adaptation")
                require(inputs["declared_count"] == len(rows) and len(set(rows)) == len(rows), "Malformed parameter rows")
            families[case["id"]] = {
                "source_path": file["path"], "source_blob": file["blob_sha"],
                "parameter_family": case["parameter_family"], "rows": rows,
            }
    require(len(families) == 163, "All 163 original families are mandatory")
    require(sum(len(case["rows"]) if case["rows"] else 1 for case in families.values()) == 220,
            "All 220 expanded original cases are mandatory")
    return primary, families


def source_map():
    primary, families = frozen_families()
    declarations, native = {}, {}
    for path in sorted(TEST.glob("*.kt")):
        text = path.read_text(encoding="utf-8")
        require("@Disabled" not in text and "@Ignore" not in text, "Disabled mandatory assertion")
        require("// PortedFrom:" in text or "// AndroidOnly: WP-211" in text, "Missing test provenance")
        for suite, name, signature in ORIGINAL.findall(text):
            suffix = "()"
            if signature:
                value = json.loads(signature)
                require(value.endswith(" [${preset.rawValue}]"), "Unknown dynamic family display signature")
                suffix = value.split(" [", 1)[0]
            identity = string(suite) + "::" + string(name) + suffix
            require(identity in families and identity not in declarations, "Unknown/duplicate original binding: " + identity)
            require(bool(signature) == bool(families[identity]["rows"]), "Original parameter family was collapsed")
            declarations[identity] = {"native_source": path.relative_to(ROOT).as_posix(), "runner": "services"}
        for literal in NATIVE.findall(text):
            identity = "WP-211::" + string(literal)
            require(identity not in native, "Duplicate native regression declaration")
            native[identity] = path.relative_to(ROOT).as_posix()
    room_text = (ROOT / ROOM_PATH).read_text(encoding="utf-8")
    require("// PortedFrom:" in room_text and "@Ignore" not in room_text, "Room provenance/disabled assertion")
    for literal, method in ROOM_BINDING.findall(room_text):
        identity = string(literal)
        require(identity in families and identity not in declarations, "Unknown/duplicate Room original binding")
        declarations[identity] = {"native_source": ROOM_PATH.as_posix(), "runner": "room", "method": method}
    require(set(declarations) == set(families), "Missing originals: " + repr(sorted(set(families) - set(declarations))))
    require(len(native) >= 40, "Native failure/cancellation/capability assertions must not be lowered")
    room_methods = set(ROOM_METHOD.findall(room_text))
    require(len(room_methods) == len(ROOM_METHOD.findall(room_text)) and len(room_methods) >= 12,
            "Missing/duplicate original and native Room consumers")
    for identity, metadata in declarations.items():
        families[identity].update(metadata)
    return primary, families, native, room_methods


def junit(directory, prefix):
    files = sorted(directory.glob("TEST-*.xml"))
    require(files, "Missing complete actual JUnit directory: " + str(directory))
    cases = {}
    for path in files:
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= MAX_XML_BYTES, "Unsafe/oversized JUnit")
        raw = path.read_bytes()
        tokens = raw.replace(b"\x00", b"").upper()
        require(b"<!DOCTYPE" not in tokens and b"<!ENTITY" not in tokens, "Unsafe JUnit declaration")
        root = ET.fromstring(raw)
        nodes = root.findall("testcase")
        require(root.tag == "testsuite" and nodes, "Malformed/zero actual suite")
        require(int(root.get("tests", "-1")) == len(nodes), "JUnit counter/testcase mismatch")
        for key in ("failures", "errors", "skipped"):
            require(int(root.get(key, "-1")) == 0, "Failed/error/skipped suite: " + path.name)
        for node in nodes:
            identity = (node.get("classname", ""), node.get("name", ""))
            require(identity[0].startswith(prefix) and identity[1] and identity not in cases, "Foreign/duplicate/missing testcase identity")
            require(not any(node.find(key) is not None for key in ("failure", "error", "skipped")), "Hidden unsuccessful testcase")
            cases[identity] = path.name
    return cases


def inputs():
    roots = [str(Path("android") / "core" / module) for module in
             ("services", "contracts", "model", "protocol", "data", "database", "datastore")]
    roots += [str(Path("android") / "gradle"), str(Path("android") / "build-logic"),
              str(Path("android") / "build.gradle.kts"), str(Path("android") / "settings.gradle.kts")]
    paths = git("ls-tree", "-r", "--name-only", "HEAD", "--", *roots).splitlines()
    result = {}
    for path in paths:
        if "/src/" not in path and not path.endswith((".gradle.kts", ".lockfile", ".toml", ".xml", ".properties")):
            continue
        expected = git("rev-parse", "HEAD:" + path)
        actual_path = ROOT.joinpath(*path.split("/"))
        result[path] = {"git_blob": expected, "checkout_blob": git("hash-object", str(actual_path)),
                        "sha256": sha256(actual_path.read_bytes())}
    require(result, "Zero immutable compiled inputs")
    for path in [*MAIN.glob("*.kt"), *TEST.glob("*.kt"), ROOT / ROOM_PATH, OUT / "collect_evidence.py"]:
        relative = path.relative_to(ROOT).as_posix()
        expected = git("rev-parse", "HEAD:" + relative)
        require(git("hash-object", str(path)) == expected, "Uncommitted/stale owned compiled or reader input: " + relative)
        if relative not in result:
            result[relative] = {"git_blob": expected, "checkout_blob": expected, "sha256": sha256(path.read_bytes())}
    return result


def invocation(path):
    if path is None:
        return {"host": platform.system().lower(), "run_id": None, "run_attempt": None, "base_sha": BASE,
                "authority": "local reader; not an authenticated CI or acceptance receipt"}
    record = load_json(path)
    require(record["schema_version"] == 1 and record["host"] == "linux" and record["stage"] == "verify",
            "Expected actual declared Linux verify invocation")
    identity = record["identity"]
    require(identity is not None, "Hosted invocation lacks actual run identity")
    binding = identity["binding"]
    require(binding["repository"] == "cbattlegear/MeshCoreOne-Android" and binding["head_sha"] == git("rev-parse", "HEAD"),
            "Stale/foreign actual invocation")
    require(binding["source_sha"] == SOURCE and binding["manifest_sha256"] == MANIFEST and binding["policy_revision"] == POLICY,
            "Invocation source/policy drift")
    require(type(identity["run_id"]) is int and identity["run_id"] > 0 and
            type(identity["run_attempt"]) is int and identity["run_attempt"] > 0, "Missing run/attempt identity")
    return {"host": record["host"], "run_id": identity["run_id"], "run_attempt": identity["run_attempt"],
            "base_sha": binding["base_sha"], "actual_root_binding": binding,
            "authority": "actual root invocation retained; independent trusted CI still binds its own artifacts"}


def retain(output, invocation_file=None):
    output = output.resolve()
    require(output.is_relative_to(OUT.resolve()) or not output.is_relative_to(ROOT.resolve()), "Output escapes the WP evidence lease")
    require(not output.exists(), "Raw evidence retention directory must be new")
    output.mkdir(parents=True)
    head = git("rev-parse", "HEAD")
    metadata = {"schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-211",
                "head_sha": head, "source_sha": SOURCE, "manifest_sha256": MANIFEST, "policy_revision": POLICY,
                "lease": LEASE, "receipt_base_sha": BASE, "invocation_file": str(invocation_file) if invocation_file else None,
                "raw_junit": [], "missing_directories": []}
    def persist():
        (output / "retention.json").write_text(
            json.dumps(metadata, indent=2, ensure_ascii=True) + "\n", encoding="utf-8",
        )
    persist()
    for module, relative in (
        ("services", Path("android") / "core" / "services" / "build" / "test-results" / "test"),
        ("data", Path("android") / "core" / "data" / "build" / "test-results" / "testDebugUnitTest"),
    ):
        directory = ROOT / relative
        files = sorted(directory.glob("TEST-*.xml"))
        if not files:
            metadata["missing_directories"].append(relative.as_posix())
        target = output / "junit" / module
        target.mkdir(parents=True)
        for path in files:
            require(path.is_file() and not path.is_symlink(), "Linked/non-file raw JUnit input")
            destination = target / path.name
            shutil.copyfile(path, destination)
            raw = destination.read_bytes()
            require(raw == path.read_bytes(), "Raw retention changed JUnit bytes")
            metadata["raw_junit"].append({"path": destination.relative_to(output).as_posix(),
                                          "sha256": sha256(raw), "size_bytes": len(raw)})
    persist()
    if invocation_file is not None:
        shutil.copyfile(invocation_file, output / "actual-invocation.json")
    metadata["execution"] = invocation(invocation_file)
    persist()
    metadata["input_blobs"] = inputs()
    primary = load_json(ROOT / "docs" / "android" / "port-manifest.json")["inventory"]
    metadata["primary_inputs"] = [
        {"path": item["path"], "reference_blob": item["blob_sha"],
         "checkout_blob": git("hash-object", str(ROOT.joinpath(*item["path"].split("/"))))}
        for item in primary if item["primary_owner"] == "WP-211"
    ]
    persist()
    return metadata


def validate_retained(output, invocation_file=None):
    metadata = load_json(output / "retention.json")
    require(metadata["repository"] == "cbattlegear/MeshCoreOne-Android" and metadata["work_package"] == "WP-211" and
            metadata["source_sha"] == SOURCE and metadata["manifest_sha256"] == MANIFEST and
            metadata["policy_revision"] == POLICY and metadata["lease"] == LEASE, "Retained immutable binding drift")
    actual_invocation = invocation_file or (output / "actual-invocation.json" if (output / "actual-invocation.json").is_file() else None)
    require(metadata["execution"] == invocation(actual_invocation), "Retained execution identity drift")
    require(metadata["head_sha"] == git("rev-parse", "HEAD"), "Retained source HEAD is stale")
    require(all(value["git_blob"] == value["checkout_blob"] for value in metadata["input_blobs"].values()),
            "Compiled input checkout differs from immutable HEAD")
    require(metadata["input_blobs"] == inputs(), "Retained input identities are stale")
    require(not metadata["missing_directories"], "Missing actual mandatory JUnit")
    _, families, native, room_methods = source_map()
    services = junit(output / "junit" / "services", "com.meshcoreone.android.core.services.")
    data = junit(output / "junit" / "data", "com.meshcoreone.android.core.data.")
    own_records = [name for classname, name in services if classname.startswith(DEVICE)]
    own_names = set(own_records)
    require(len(own_records) == len(own_names), "Duplicate device display identity across testcase classes")
    expected = set(native)
    for identity, case in families.items():
        if case["runner"] == "services":
            names = [identity + " [" + row + "]" for row in case["rows"]] if case["rows"] else [identity]
            expected.update(names)
            case["executed_names"] = names
        else:
            require((ROOM_CLASS, case["method"]) in data, "Room original did not actually execute: " + identity)
            case["executed_names"] = [case["method"]]
    require(own_names == expected, "Missing/extra actual device cases: " + repr(sorted(expected - own_names)) +
            " / " + repr(sorted(own_names - expected)))
    require({name for classname, name in data if classname == ROOM_CLASS} == room_methods,
            "Incomplete/extra actual Room device/settings suite")
    for report in metadata["raw_junit"]:
        raw = (output / report["path"]).read_bytes()
        require(len(raw) == report["size_bytes"] and sha256(raw) == report["sha256"], "Retained raw JUnit digest drift")
    metadata.update({
        "scope": "Actual complete service/Room assertions and source dispositions; not human, hardware, license or gate acceptance",
        "counts": {"original_families": len(families), "original_expanded": 220,
                   "device_jvm_discovered": len(own_names), "room_discovered": len(room_methods),
                   "module_services_discovered": len(services), "module_data_discovered": len(data),
                   "native_device_regressions": len(native), "failed": 0, "errors": 0, "skipped": 0},
        "original_families": families, "native_regressions": native,
    })
    (output / "verified.json").write_text(json.dumps(metadata, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    return metadata


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check-source-map", action="store_true")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--invocation-file", type=Path)
    parser.add_argument("--retain-only", action="store_true")
    parser.add_argument("--check-retained", type=Path)
    args = parser.parse_args()
    if args.check_source_map:
        _, families, native, room = source_map()
        print(json.dumps({"source_families": len(families), "expanded": 220, "native_declarations": len(native),
                          "room_declarations": len(room), "native_execution": False}))
        return
    if args.check_retained:
        result = validate_retained(args.check_retained, args.invocation_file)
        print(json.dumps(result["counts"], sort_keys=True))
        return
    output = args.output or OUT / "retained" / (git("rev-parse", "HEAD") + "-" + str(__import__("time").time_ns()))
    retain(output, args.invocation_file)
    if args.retain_only:
        print(json.dumps({"raw_retained": str(output), "validated": False}))
        return
    result = validate_retained(output, args.invocation_file)
    print(json.dumps({"evidence": str(output), "counts": result["counts"]}, sort_keys=True))


if __name__ == "__main__":
    main()
