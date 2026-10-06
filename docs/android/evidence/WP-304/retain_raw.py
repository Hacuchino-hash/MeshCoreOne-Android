# AndroidOnly: WP-304 Preserve complete produced raw XML/input binding independently before success validation.
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
from source_inventory import ROOT, PIN, MANIFEST, native_inputs, git, require, unique_json

POLICY = "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a"


def pipeline_invocation(path):
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


def retain(junit, output, emit=False, images=None, pipeline_output=None, invocation_path=None):
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
        if invocation_path is not None:
            require(pipeline_output is None, "Ambiguous pipeline output forwarding")
            pipeline_output, record["invocation"] = pipeline_invocation(invocation_path)
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
        destination = pipeline_output / "wp304-native"
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
    args = parser.parse_args()
    try:
        retain(args.junit, args.output, args.emit, args.images, invocation_path=args.invocation)
        return 0
    except (OSError, ValueError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
