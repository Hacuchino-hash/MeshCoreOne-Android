"""WP-201 local evidence collector; no gate publication, source-catalog change or parity approval."""

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
BASE = "0ae606992bf58c8d3f2bf08b7a26406c97f12f84"
DEFERRED = "RegionScopeSemanticsTests::matchRegions multi-match never stores first-match as regionScope()"
MODULES = {
    "model": ("android/core/model/build/test-results/test", 165),
    "contracts": ("android/core/contracts/build/test-results/test", 4),
    "database": ("android/core/database/build/test-results/testDebugUnitTest", 42),
}
SCOPES = (
    "android/core/model/",
    "android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/",
    "android/core/database/",
    "docs/android/deviations/WP-201.md",
    "docs/android/evidence/WP-201/",
)


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def sha(data):
    return hashlib.sha256(data).hexdigest()


def checkout(path):
    return ROOT.joinpath(*path.split("/"))


def report():
    sys.path.insert(0, str(ROOT / "tools" / "android-port"))
    from controller.gates import policy_revision
    from controller.model import load_manifest
    from portmap import port_map

    manifest = load_manifest(ROOT)
    policy = json.loads(checkout("docs/android/automation-policy.json").read_text(encoding="utf8"))
    assert manifest.sha256 == "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
    assert policy_revision(manifest, policy) == "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"
    owned = [item for item in manifest.data["inventory"] if item["primary_owner"] == "WP-201"]
    assert len(owned) == 80
    mapped = port_map(manifest)
    native = [item for item in mapped if item["implementation"].startswith(SCOPES[:3])]
    input_map = []
    for item in owned:
        raw = git("show", f"{SOURCE}:{item['path']}")
        actual = checkout(item["path"]).read_bytes()
        assert raw.replace(b"\r\n", b"\n") == actual.replace(b"\r\n", b"\n"), item["path"]
        outputs = [entry["implementation"] for entry in native if item["path"] in entry["sources"]]
        assert outputs, "Missing primary input mapping: " + item["path"]
        input_map.append({**item, "source_sha256": sha(raw), "native_files": outputs})

    cases = []
    suites = []
    for module, (relative, required_count) in MODULES.items():
        reports = sorted(checkout(relative).glob("TEST-*.xml"))
        assert reports, "Missing JUnit for " + module
        count = 0
        for file in reports:
            raw = file.read_bytes()
            assert b"<!DOCTYPE" not in raw.upper(), file
            doc = ET.fromstring(raw)
            assert doc.tag == "testsuite"
            nodes = doc.findall("testcase")
            assert nodes and int(doc.attrib["tests"]) == len(nodes)
            for kind in ("failures", "errors", "skipped"):
                assert int(doc.attrib[kind]) == 0, (file, kind)
            for node in nodes:
                assert not any(node.find(kind) is not None for kind in ("failure", "error", "skipped"))
                cases.append({"module": module, "class": node.attrib["classname"], "name": node.attrib["name"], "outcome": "passed"})
            count += len(nodes)
            suites.append({"module": module, "path": file.relative_to(ROOT).as_posix(), "sha256": sha(raw), "cases": len(nodes)})
        assert count == required_count, (module, count, required_count)
    identities = [(case["module"], case["class"], case["name"]) for case in cases]
    assert len(identities) == len(set(identities))

    originals = []
    original_paths = {entry["path"] for entry in owned if entry["kind"] == "test"}
    catalog = json.loads(checkout("docs/android/test-cases.json").read_text(encoding="utf8"))
    by_name = {case["name"]: case for case in cases if case["module"] == "model"}
    db_bindings = {}
    for file in checkout("android/core/database/src/test/kotlin").rglob("*.kt"):
        content = file.read_text(encoding="utf8")
        for identity, method in re.findall(r'@OriginalCase\("([^"]+)"\)\s+fun\s+(\w+)\s*\(', content):
            assert identity not in db_bindings
            hits = [case for case in cases if case["module"] == "database" and case["name"] == method]
            assert len(hits) == 1, (identity, method)
            db_bindings[identity] = hits[0]
    for entry in catalog["entries"]:
        if entry["path"] not in original_paths:
            continue
        for case in entry["cases"]:
            identity = case["id"]
            hit = by_name.get(identity) or db_bindings.get(identity)
            if identity == DEFERRED:
                assert hit is None
                outcome = "deferred-real-WP-103-resolver-consumer"
            else:
                assert hit is not None, "Missing original assertion family: " + identity
                outcome = "source-behavior"
                if identity.startswith("ChannelFloodScopeTests::") and ("Codable" in identity or "Legacy envelope" in identity):
                    outcome = "legacy-field-policy-equivalent-not-envelope-codec"
                elif hit["module"] == "database":
                    outcome = "actual-DAO-or-column-equivalent-not-WP-202-repository"
                elif identity.startswith("DevicePublicKeyDeduplicationTests::createDevice"):
                    outcome = "pure-source-factory-equivalent-not-connect-ceremony"
            originals.append({"source": entry["path"], "blob_sha": entry["blob_sha"], **case,
                              "evidence_kind": outcome, "native_test": hit})
    assert len(originals) == 114
    assert sum(case["native_test"] is None for case in originals) == 1

    schema_path = "android/core/database/schemas/com.meshcoreone.android.core.database.MeshCoreDatabase/1.json"
    schema_raw = checkout(schema_path).read_bytes()
    schema = json.loads(schema_raw)["database"]
    assert schema["version"] == 1 and len(schema["entities"]) == 17
    assert sum(len(entity.get("foreignKeys", [])) for entity in schema["entities"]) == 2
    changes = git("diff", "--name-only", BASE).decode().splitlines()
    untracked = git("ls-files", "--others", "--exclude-standard").decode().splitlines()
    assert all(any(path == scope or path.startswith(scope) for scope in SCOPES) for path in changes + untracked)
    return {
        "schema_version": 1, "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-201",
        "session": "f7b1af4d-9429-49e8-bcb0-6e3cc11cd5de", "branch": "cbattlegear-potential-engine",
        "base_sha": BASE, "source_sha": SOURCE, "manifest_sha256": manifest.sha256,
        "policy_revision": policy_revision(manifest, policy),
        "scope": "local source/field/DAO assertions; not formal review, gate approval, hardware or bidirectional backup restore",
        "input_counts": {"production": 54, "test": 14, "support": 12},
        "inputs": input_map, "source_cases": originals, "junit_suites": suites, "native_cases": cases,
        "discovery": {"model": 165, "contracts": 4, "database": 42, "total": 211, "failed": 0, "errors": 0, "skipped": 0},
        "schema": {"generator": "Room 2.8.5 / KSP 2.3.12", "path": schema_path,
                   "sha256": sha(schema_raw), "canonical_lf_sha256": sha(schema_raw.replace(b"\r\n", b"\n")),
                   "version": 1, "entities": 17, "identity_hash": schema["identityHash"], "explicit_cascade_relationships": 2},
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    result = report()
    if args.write:
        (OUT / "local-evidence.json").write_text(json.dumps(result, indent=2, ensure_ascii=True) + "\n", encoding="utf8")
    print(json.dumps({"result": "passed", "original_declarations": 114, "asserted_or_native_equivalent": 113,
                      "genuine_deferred_consumers": 1, "discovery": result["discovery"], "schema": result["schema"]}, indent=2))


if __name__ == "__main__":
    main()
