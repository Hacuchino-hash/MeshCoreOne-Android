# AndroidOnly: WP-304 Preserve complete produced raw XML/input binding independently before success validation.
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import platform
import re
import shutil
import sys
from source_inventory import ROOT, PIN, MANIFEST, PROJECTION_SUITES, inventory, native_inputs, git, require, unique_json

sys.path.insert(0, str(ROOT / "tools" / "android-port"))
from controller.errors import PortError
from controller.gates import policy_revision
from controller.model import load_manifest

POLICY = "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"
SOURCE_TREE = "8918fdc604341e6996a68c88f6bb1c02b9c2f87e"


def invocation_document(path):
    require(path.is_absolute() and path.name == "wp109-invocation.json"
        and path.is_file() and not path.is_symlink()
        and not any(parent.is_symlink() for parent in path.parents)
        and not path.resolve().is_relative_to(ROOT.resolve()), "Unsafe invocation forwarding path")
    require(0 < path.stat().st_size <= 64 * 1024, "Empty/excessive pipeline invocation")
    value = unique_json(path)
    require(isinstance(value, dict) and set(value) == {"schema_version", "stage", "identity", "host"}
        and type(value["schema_version"]) is int and value["schema_version"] == 1
        and value["stage"] == "verify" and value["host"] == "linux",
        "Wrong native pipeline invocation stage/host/schema")
    return value


def pipeline_invocation(path):
    value = invocation_document(path)
    identity = value["identity"]
    require(isinstance(identity, dict) and set(identity) == {"binding", "run_id", "run_attempt"},
        "Missing actual native pipeline invocation identity")
    binding = identity["binding"]
    require(isinstance(binding, dict) and set(binding) == {
        "repository", "work_package", "base_sha", "head_sha", "source_sha", "manifest_sha256", "policy_revision",
    } and binding["repository"] == "cbattlegear/MeshCoreOne-Android"
        and binding["work_package"] == "WP-003" and binding["head_sha"] == git("rev-parse", "HEAD").decode().strip()
        and isinstance(binding["base_sha"], str) and re.fullmatch(r"[0-9a-f]{40}", binding["base_sha"])
        and binding["source_sha"] == PIN
        and binding["manifest_sha256"] == MANIFEST and binding["policy_revision"] == POLICY,
        "Stale/mismatched actual native pipeline invocation")
    require(all(type(identity[key]) is int and identity[key] > 0 for key in ("run_id", "run_attempt")),
        "Missing positive actual native run/attempt")
    return path.parent, value


def local_execution_binding():
    require(platform.system() == "Linux" and platform.machine() == "x86_64",
        "Local UI evidence requires the admitted Linux x64 host")
    head = git("rev-parse", "HEAD").decode().strip()
    tree = git("rev-parse", "HEAD^{tree}").decode().strip()
    require(re.fullmatch(r"[0-9a-f]{40}", head) and re.fullmatch(r"[0-9a-f]{40}", tree),
        "Missing actual local committed head/tree")
    require(not git("status", "--porcelain=v1", "--untracked-files=all").strip(),
        "Local UI evidence requires a clean committed snapshot")
    manifest = load_manifest(ROOT)
    policy = unique_json(ROOT / "docs" / "android" / "automation-policy.json")
    require(manifest.sha256 == MANIFEST and manifest.data["reference"]["commit"] == PIN
        and manifest.data["reference"]["tree_sha"] == SOURCE_TREE
        and policy["repository"] == "cbattlegear/MeshCoreOne-Android"
        and policy_revision(manifest, policy) == POLICY
        and git("rev-parse", PIN + "^{tree}").decode().strip() == SOURCE_TREE,
        "Local UI source/manifest/policy drift")
    inventory()
    return {
        "scope": "local-committed-inputs-no-hosted-run-authority", "host": "linux",
        "binding": {
            "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-304",
            "head_sha": head, "tree_sha": tree, "source_sha": PIN, "source_tree": SOURCE_TREE,
            "manifest_sha256": MANIFEST, "policy_revision": POLICY,
        },
    }


def local_invocation(path):
    value = invocation_document(path)
    require(value["identity"] is None, "Local UI forwarding requires the explicit null executor identity")
    return path.parent, value, local_execution_binding()


def retain(junit, output, emit=False, images=None, pipeline_output=None, invocation_path=None, producer_junit=None):
    require(junit.is_absolute() and output.is_absolute(), "Explicit absolute raw input/output paths required")
    require(not junit.is_symlink() and not output.is_symlink(), "Linked raw evidence path")
    reports = sorted(junit.glob("TEST-*.xml"))
    output.mkdir(parents=True, exist_ok=True)
    record = {
        "schema_version": 1, "work_package": "WP-304", "source_sha": PIN,
        "observed_head_sha": git("rev-parse", "HEAD").decode().strip(),
        "scope": "Unvalidated complete produced XML; never a passing suite or parity claim.",
        "result": "raw-retained-unvalidated" if reports else "blocked-no-produced-junit",
        "inputs": native_inputs(), "reports": [], "images": [], "invocation": None,
        "execution_scope": "unforwarded", "local_execution": None,
        "producer_reports": {},
    }
    directory = output / "junit"
    directory.mkdir(exist_ok=True)
    require(len(reports) <= 32, "Excessive native raw report set")
    for path in reports:
        raw = path.read_bytes()
        require(not path.is_symlink() and 0 < len(raw) <= 64 * 1024 * 1024, "Unsafe/empty/excessive raw report")
        (directory / path.name).write_bytes(raw)
        sha = hashlib.sha256(raw).hexdigest()
        record["reports"].append({"name": path.name, "bytes": len(raw), "sha256": sha})
        if emit:
            print("WP304_RAW_JUNIT|" + path.name + "|" + sha + "|" + base64.b64encode(raw).decode())
    if images is not None:
        require(images.is_absolute() and not images.is_symlink(), "Explicit unlinked native image directory required")
        image_output = output / "ui"
        image_output.mkdir(exist_ok=True)
        for path in sorted(images.glob("*.png")):
            require(not path.is_symlink() and 0 < path.stat().st_size <= 64 * 1024 * 1024,
                "Unsafe/empty/excessive produced native image")
            raw = path.read_bytes()
            (image_output / path.name).write_bytes(raw)
            sha = hashlib.sha256(raw).hexdigest()
            record["images"].append({"name": path.name, "bytes": len(raw), "sha256": sha})
            if emit:
                print("WP304_RAW_PNG|" + path.name + "|" + sha + "|" + base64.b64encode(raw).decode())
    try:
        if producer_junit is not None:
            require(set(producer_junit) == set(PROJECTION_SUITES), "Missing/unknown producer projection directories")
            for owner, source in producer_junit.items():
                require(source.is_absolute() and not source.is_symlink()
                    and not any(parent.is_symlink() for parent in source.parents), "Unsafe producer projection directory")
                name = "TEST-" + PROJECTION_SUITES[owner]["classname"] + ".xml"
                path = source / name
                require(path.is_file() and not path.is_symlink(), "Missing actual producer projection XML")
                raw = path.read_bytes()
                require(0 < len(raw) <= 64 * 1024 * 1024, "Empty/excessive producer projection XML")
                target = output / "producers" / owner
                target.mkdir(parents=True, exist_ok=True)
                (target / name).write_bytes(raw)
                sha = hashlib.sha256(raw).hexdigest()
                record["producer_reports"][owner] = {"name": name, "bytes": len(raw), "sha256": sha}
                if emit:
                    print("WP304_RAW_PRODUCER|" + owner + "|" + name + "|" + sha + "|"
                        + base64.b64encode(raw).decode())
        if invocation_path is not None:
            require(pipeline_output is None, "Ambiguous pipeline output forwarding")
            forwarded = invocation_document(invocation_path)
            if forwarded["identity"] is None:
                pipeline_output, record["invocation"], record["local_execution"] = local_invocation(invocation_path)
                record["execution_scope"] = "local"
                record["scope"] = "Unvalidated local XML/PNG/committed inputs; no hosted run, passing cycle or parity authority."
            else:
                pipeline_output, record["invocation"] = pipeline_invocation(invocation_path)
                record["execution_scope"] = "hosted"
    finally:
        raw_record = (json.dumps(record, indent=2, sort_keys=True) + "\n").encode()
        (output / "raw-retention.json").write_bytes(raw_record)
        if emit:
            print("WP304_RAW_BINDING|" + hashlib.sha256(raw_record).hexdigest() + "|" + base64.b64encode(raw_record).decode())
    if pipeline_output is not None:
        require(pipeline_output.is_absolute() and not pipeline_output.is_symlink()
            and not any(parent.is_symlink() for parent in pipeline_output.parents)
            and not pipeline_output.resolve().is_relative_to(ROOT.resolve())
            and not ROOT.resolve().is_relative_to(pipeline_output.resolve()), "Unsafe pipeline evidence root")
        destination = pipeline_output / ("wp304-local" if record["execution_scope"] == "local" else "wp304-native")
        require(not destination.exists(), "Existing pipeline WP-304 evidence must not be overwritten")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(output, destination)
    require(reports, "No produced JUnit; dependency/test failure remains blocked and diagnosed")
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--junit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--emit", action="store_true")
    parser.add_argument("--images", type=Path)
    parser.add_argument("--invocation", type=Path)
    parser.add_argument("--runtime-junit", type=Path)
    parser.add_argument("--ble-junit", type=Path)
    args = parser.parse_args()
    try:
        require((args.runtime_junit is None) == (args.ble_junit is None),
            "Both actual producer projection directories are required")
        producers = None if args.runtime_junit is None else {"runtime": args.runtime_junit, "ble": args.ble_junit}
        result = retain(args.junit, args.output, args.emit, args.images, invocation_path=args.invocation,
            producer_junit=producers)
        if result["local_execution"] is not None:
            binding = result["local_execution"]["binding"]
            print("WP304_LOCAL_ONLY|" + binding["head_sha"] + "|" + binding["tree_sha"]
                + "|no-hosted-run-authority")
        return 0
    except (OSError, ValueError, PortError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
