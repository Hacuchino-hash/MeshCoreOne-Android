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
from controller.gates import Binding, policy_revision
from controller.model import Manifest
from controller.schema import load_json

SOURCE = "db14559b39d32322b06477c6ae676112f583db50"
MANIFEST = "ceb84b5e26fcc9ece5c0b3fb6c68b4d2965f9f24114fa81b7434ff73d1ed7904"
POLICY = "45df0b4e40b210780e072e58fd935cb0d5abf2d29341703b4ac486c9596b56e0"
HISTORICAL_MANIFEST = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
HISTORICAL_POLICY = "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"
BASE = "e3369a97bf3a1e19b801c8d69ca8abf171da432b"
RECEIPT_BASE = "7e2835bad2c03dfb5a088063655f9fc4dbafd00f"
LEASE = "autonomous-WP-211-d147865c"
RECOVERY_OWNER = {
    "native_session": "88d414ff-da1d-48da-992f-ad44c80e9cce",
    "app_session": "bbb03d7a-f527-4d3e-8b3b-c1a97c44d631",
    "checkout": "pr-42-cbattlegear-supreme-engine",
    "managed_branch": "pr/42/cbattlegear-supreme-engine",
    "remote_head": "cbattlegear-supreme-engine",
}
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
MIN_NATIVE_CASES = 93
REQUIRED_CLOSE_CASES = {
    "WP-211::closing the context during an in-flight verified write reports NotConnected to the live caller",
    "WP-211::a call after the context closed reports NotConnected without a transport side effect",
}
REQUIRED_DISCOVERY_CASES = {
    "WP-211::discovery excludes canonically equivalent known regions in either normalization form",
    "WP-211::discovery deduplicates canonical regions without rewriting advertised UTF-8 or flood-scope keys",
    "WP-211::discovery sorts canonical Unicode scalar values rather than raw UTF-16",
}
FROZEN_PRODUCERS = {
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/device/RegionalAreas.kt":
        "090ce5f23a4e36f8580094b7d43ecc8e6c40368f",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/device/RadioPresets.kt":
        "7c2e8758af93a8dd379d2232bcad9259e1627fc7",
    "android/core/services/src/main/kotlin/com/meshcoreone/android/core/services/device/RadioOptions.kt":
        "c47b1a3421a0fb37eaf157de557c861936342b6e",
    "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/DeviceSettingsFaults.kt":
        "b2a6b84a3846c016184e06772da4800700e3e8af",
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/device/RegionalAreasTest.kt":
        "676a7a79f725afb34de320c729ecc06af08c138b",
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/device/RadioPresetTest.kt":
        "aadba158183cfefc517e723ba781755c8c9a5ae8",
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/device/DeviceSettingsFaultTest.kt":
        "92f88b189f8ad41a44c15950a0659ea7d6125f15",
    "android/core/services/src/test/kotlin/com/meshcoreone/android/core/services/device/SourceCases.kt":
        "6a0e37b4ad26f0127ac7c04b9fc82971cf18c6cf",
}


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


def frozen_producers():
    freeze = load_json(OUT / "producer-freeze.json")
    require(freeze["schema_version"] == 1 and freeze["repository"] == "cbattlegear/MeshCoreOne-Android" and
            freeze["work_package"] == "WP-211" and freeze["lease"] == LEASE and freeze["source_sha"] == SOURCE and
            freeze["manifest_sha256"] == HISTORICAL_MANIFEST and
            freeze["policy_revision"] == HISTORICAL_POLICY,
            "Coordinator producer-freeze binding drift")
    paths = {entry["path"]: entry["git_blob"] for entry in freeze["files"]}
    require(len(paths) == len(freeze["files"]) and paths == FROZEN_PRODUCERS, "Frozen producer path/blob drift")
    require(re.fullmatch(r"[0-9a-f]{40}", freeze["reviewed_head_sha"]), "Missing immutable reviewed producer head")
    for path, expected in paths.items():
        require(git("rev-parse", freeze["reviewed_head_sha"] + ":" + path) == expected, "Reviewed producer blob drift")
        require(git("rev-parse", "HEAD:" + path) == expected, "Frozen producer edited without repair receipt")
        require(git("hash-object", str(ROOT.joinpath(*path.split("/")))) == expected, "Frozen producer checkout drift")
    return freeze


def source_map():
    frozen_producers()
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
    require(len(native) >= MIN_NATIVE_CASES, "Native failure/cancellation/capability assertions must not be lowered")
    require(REQUIRED_CLOSE_CASES <= native.keys(), "Both reviewed close-lifecycle regressions are mandatory")
    require(REQUIRED_DISCOVERY_CASES <= native.keys(), "All canonical region discovery regressions are mandatory")
    room_methods = set(ROOM_METHOD.findall(room_text))
    require(len(room_methods) == len(ROOM_METHOD.findall(room_text)) and len(room_methods) >= 12,
            "Missing/duplicate original and native Room consumers")
    for identity, metadata in declarations.items():
        families[identity].update(metadata)
    return primary, families, native, room_methods


def partition_counts(families, native, room_methods):
    jvm_originals = sum(len(case["rows"]) if case["rows"] else 1
                        for case in families.values() if case["runner"] == "services")
    room_originals = {case["method"] for case in families.values() if case["runner"] == "room"}
    require(jvm_originals == 213 and len(room_originals) == 7,
            "Frozen original JVM/Room partition changed")
    require(room_originals <= room_methods, "Original Room methods missing from the actual owning suite")
    return {
        "original_jvm_expanded": jvm_originals,
        "original_room_expanded": len(room_originals),
        "native_device_regressions": len(native),
        "native_room_regressions": len(room_methods - room_originals),
        "declared_device_jvm": jvm_originals + len(native),
        "declared_room": len(room_methods),
        "declared_owned_total": jvm_originals + len(native) + len(room_methods),
    }


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
    readers = sorted(OUT.glob("*.py")) + [OUT / "producer-freeze.json"]
    for path in [*MAIN.glob("*.kt"), *TEST.glob("*.kt"), ROOT / ROOM_PATH, *readers]:
        relative = path.relative_to(ROOT).as_posix()
        expected = git("rev-parse", "HEAD:" + relative)
        if relative not in result:
            result[relative] = {
                "git_blob": expected, "checkout_blob": git("hash-object", str(path)),
                "sha256": sha256(path.read_bytes()),
            }
    return result


def invocation(path):
    if path is None:
        return {"host": platform.system().lower(), "run_id": None, "run_attempt": None, "base_sha": BASE,
                "authority": "local reader; not an authenticated CI or acceptance receipt"}
    record = load_json(path)
    require(isinstance(record, dict) and set(record) == {"schema_version", "host", "stage", "identity"},
            "Malformed declared invocation fields")
    require(type(record["schema_version"]) is int and record["schema_version"] == 1 and
            record["host"] == "linux" and record["stage"] == "verify",
            "Expected actual declared Linux verify invocation")
    identity = record["identity"]
    if identity is None:
        return {**invocation(None), "host": record["host"], "head_sha": git("rev-parse", "HEAD"),
                "actual_invocation": record}
    require(isinstance(identity, dict) and set(identity) == {"binding", "run_id", "run_attempt"},
            "Malformed hosted invocation identity")
    binding = identity["binding"]
    require(isinstance(binding, dict), "Malformed hosted invocation binding")
    typed_binding = Binding.parse(binding)
    head = git("rev-parse", "HEAD")
    require(typed_binding.repository == "cbattlegear/MeshCoreOne-Android" and
            typed_binding.work_package == "WP-003" and typed_binding.head_sha == head,
            "Stale/foreign actual invocation")
    require(git("cat-file", "-t", typed_binding.base_sha) == "commit", "Invocation base is not an existing commit")
    require(git("merge-base", BASE, typed_binding.base_sha) == BASE,
            "Invocation base is outside the inherited integration baseline")
    require(git("merge-base", BASE, head) == BASE,
            "Actual HEAD is outside the inherited integration baseline")
    require(binding["source_sha"] == SOURCE and binding["manifest_sha256"] == MANIFEST and binding["policy_revision"] == POLICY,
            "Invocation source/policy drift")
    require(type(identity["run_id"]) is int and identity["run_id"] > 0 and
            type(identity["run_attempt"]) is int and identity["run_attempt"] > 0, "Missing run/attempt identity")
    return {"host": record["host"], "run_id": identity["run_id"], "run_attempt": identity["run_attempt"],
            "base_sha": binding["base_sha"], "actual_root_binding": binding,
            "actual_invocation": record,
            "authority": "actual root invocation retained; independent trusted CI still binds its own artifacts"}


def retain(output, invocation_file=None):
    output = output.resolve()
    require(output.is_relative_to(OUT.resolve()) or not output.is_relative_to(ROOT.resolve()), "Output escapes the WP evidence lease")
    require(not output.exists(), "Raw evidence retention directory must be new")
    output.mkdir(parents=True)
    head = git("rev-parse", "HEAD")
    metadata = {"schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-211",
                "head_sha": head, "source_sha": SOURCE, "manifest_sha256": MANIFEST, "policy_revision": POLICY,
                "lease": LEASE, "receipt_base_sha": RECEIPT_BASE, "integration_base_sha": BASE,
                "recovery_owner_receipt": RECOVERY_OWNER,
                "invocation_file": str(invocation_file) if invocation_file else None,
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
    metadata["input_blobs"] = inputs()
    metadata["producer_freeze"] = load_json(OUT / "producer-freeze.json")
    primary = load_json(ROOT / "docs" / "android" / "port-manifest.json")["inventory"]
    metadata["primary_inputs"] = [
        {"path": item["path"], "reference_blob": item["blob_sha"],
         "checkout_blob": git("hash-object", str(ROOT.joinpath(*item["path"].split("/"))))}
        for item in primary if item["primary_owner"] == "WP-211"
    ]
    persist()
    metadata["execution"] = invocation(invocation_file)
    persist()
    return metadata


def validate_retained(output, invocation_file=None):
    metadata = load_json(output / "retention.json")
    require(metadata["repository"] == "cbattlegear/MeshCoreOne-Android" and metadata["work_package"] == "WP-211" and
            metadata["source_sha"] == SOURCE and metadata["manifest_sha256"] == MANIFEST and
            metadata["policy_revision"] == POLICY and metadata["lease"] == LEASE, "Retained immutable binding drift")
    require(metadata["receipt_base_sha"] == RECEIPT_BASE and metadata["integration_base_sha"] == BASE,
            "Retained receipt/integration base drift")
    require(metadata["recovery_owner_receipt"] == RECOVERY_OWNER, "Retained recovery owner receipt drift")
    retained_invocation = output / "actual-invocation.json"
    if metadata["invocation_file"] is not None:
        require(retained_invocation.is_file() and not retained_invocation.is_symlink(),
                "Missing/linked retained actual invocation")
        actual_invocation = retained_invocation
    else:
        require(not retained_invocation.exists(), "Unexpected retained actual invocation")
        actual_invocation = None
    require(metadata["execution"] == invocation(actual_invocation), "Retained execution identity drift")
    if invocation_file is not None:
        require(actual_invocation is not None and load_json(invocation_file) == load_json(actual_invocation),
                "Provided invocation differs from the retained actual invocation")
    require(metadata["head_sha"] == git("rev-parse", "HEAD"), "Retained source HEAD is stale")
    require(all(value["git_blob"] == value["checkout_blob"] for value in metadata["input_blobs"].values()),
            "Compiled input checkout differs from immutable HEAD")
    require(metadata["input_blobs"] == inputs(), "Retained input identities are stale")
    require(metadata["producer_freeze"] == frozen_producers(), "Retained producer freeze is stale")
    require(not metadata["missing_directories"], "Missing actual mandatory JUnit")
    _, families, native, room_methods = source_map()
    partitions = partition_counts(families, native, room_methods)
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
                   "original_jvm_expanded": partitions["original_jvm_expanded"],
                   "original_room_expanded": partitions["original_room_expanded"],
                   "device_jvm_discovered": len(own_names), "room_discovered": len(room_methods),
                   "owned_discovered": len(own_names) + len(room_methods),
                   "module_services_discovered": len(services), "module_data_discovered": len(data),
                   "native_device_regressions": len(native),
                   "native_room_regressions": partitions["native_room_regressions"],
                   "failed": 0, "errors": 0, "skipped": 0},
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
                          "room_declarations": len(room), **partition_counts(families, native, room),
                          "native_execution": False}))
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
