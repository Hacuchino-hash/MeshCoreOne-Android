"""AndroidOnly: WP-102 Capture actual strict runner/artifact evidence using existing trusted parsers."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[4]
ANDROID = ROOT / "android"
sys.path.insert(0, str(ROOT / "tools" / "android-port"))
sys.path.insert(0, str(ANDROID / "scaffold"))

from controller.ci_evidence import collect_lint, collect_suites, read_xml, suite_counts
from controller.gates import policy_revision
from controller.model import load_manifest
from controller.schema import digest, load_json
from inspect_apk import inspect_apk


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    manifest = load_manifest(ROOT)
    policy = load_json(ROOT / "docs" / "android" / "automation-policy.json")
    protocol_directory = ANDROID / "core" / "protocol" / "build" / "test-results" / "test"
    protocol = suite_counts(protocol_directory, 885)
    crypto = {}
    for path in sorted(protocol_directory.glob("TEST-*.xml")):
        suite = read_xml(path)
        if ".protocol.crypto." in suite.get("name", ""):
            crypto[suite.get("name")] = {"cases": len(suite.findall("testcase")), "sha256": sha256(path)}
    if len(crypto) != 8 or sum(row["cases"] for row in crypto.values()) != 363:
        raise ValueError("Missing or changed mandatory WP-102 crypto discovery")
    helpers = suite_counts(ANDROID / "core" / "testing" / "build" / "test-results" / "testDebugUnitTest", 35)
    apk = inspect_apk()
    artifact = ROOT / apk["artifact"]
    packaged_notices = {}
    with zipfile.ZipFile(artifact) as archive:
        for name in ("GPL-3.0.txt", "MeshCore-MIT.txt", "Apache-2.0.txt", "BouncyCastle-MIT.txt"):
            source = ANDROID / "app" / "src" / "main" / "assets" / "licenses" / name
            data = archive.read("assets/licenses/" + name)
            if data != source.read_bytes():
                raise ValueError("Actual APK notice differs: " + name)
            packaged_notices[name] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    report_paths = [
        ANDROID / "build" / "reports" / "scaffold" / name
        for name in ("module-graph.tsv", "runtime-dependencies.tsv", "test-discovery.tsv")
    ]
    if any(not path.is_file() or path.stat().st_size == 0 for path in report_paths):
        raise ValueError("Missing/zero graph/runtime/discovery evidence")
    lock_paths = [
        path for path in sorted((ANDROID / "gradle" / "dependency-locks").glob("*.lockfile"))
        if "org.bouncycastle:bcprov-jdk18on:1.86=" in path.read_text(encoding="utf-8")
    ]
    if len(lock_paths) != 27:
        raise ValueError("Incomplete BC-only resolution lock coverage")
    source_paths = [
        *sorted((ANDROID / "core" / "protocol" / "src").glob("**/crypto/*.kt")),
        ANDROID / "core" / "protocol" / "build.gradle.kts",
        ANDROID / "gradle" / "libs.versions.toml",
        ANDROID / "gradle" / "verification-metadata.xml",
        ANDROID / "app" / "src" / "main" / "assets" / "licenses" / "BouncyCastle-MIT.txt",
        *lock_paths,
    ]
    result = {
        "schema_version": 1,
        "generator": "docs/android/evidence/WP-102/capture_local_evidence.py",
        "repository": "cbattlegear/MeshCoreOne-Android",
        "work_package": "WP-102",
        "scope": "Actual local Windows execution; source hashes bind tested inputs. Exact published head authority comes from normal hosted checks, not this local record.",
        "git_head_when_captured": subprocess.check_output(["git", "--no-pager", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "source_sha": manifest.data["reference"]["commit"],
        "manifest_sha256": manifest.sha256,
        "semantic_policy_revision": policy_revision(manifest, policy),
        "canonical_policy_json_sha256": digest(policy),
        "protocol": protocol,
        "crypto_suites": crypto,
        "scaffold_suites": collect_suites(ANDROID),
        "testing_helpers": helpers,
        "lint": collect_lint(ANDROID),
        "reports": {str(path.relative_to(ROOT)).replace("\\", "/"): sha256(path) for path in report_paths},
        "apk": apk,
        "packaged_notices": packaged_notices,
        "tested_input_sha256": {str(path.relative_to(ROOT)).replace("\\", "/"): sha256(path) for path in source_paths},
        "physical_device_verified": False,
        "release_signing_or_legal_gate_approved": False,
    }
    output = Path(__file__).with_name("local-verification.json")
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="ascii", newline="\n")
    print(json.dumps({"result": "captured", "protocol": protocol["passed"], "crypto": 363,
                      "scaffold": sum(row["passed"] for row in result["scaffold_suites"].values()),
                      "helpers": helpers["passed"], "lint_targets": len(result["lint"]),
                      "all_four_packaged_notices_verified": True}))


if __name__ == "__main__":
    main()
