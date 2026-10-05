# AndroidOnly: WP-304 Preserve complete produced raw XML/input binding independently before success validation.
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import sys
from source_inventory import ROOT, PIN, native_inputs, git, require


def retain(junit, output, emit=False):
    require(junit.is_absolute() and output.is_absolute(), "Explicit absolute raw input/output paths required")
    require(not junit.is_symlink() and not output.is_symlink(), "Linked raw evidence path")
    reports = sorted(junit.glob("TEST-*.xml"))
    output.mkdir(parents=True, exist_ok=True)
    record = {
        "schema_version": 1, "work_package": "WP-304", "source_sha": PIN,
        "observed_head_sha": git("rev-parse", "HEAD").decode().strip(),
        "scope": "Unvalidated complete produced XML; never a passing suite or parity claim.",
        "result": "raw-retained-unvalidated" if reports else "blocked-no-produced-junit",
        "inputs": native_inputs(), "reports": [],
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
    raw_record = (json.dumps(record, indent=2, sort_keys=True) + "\n").encode()
    (output / "raw-retention.json").write_bytes(raw_record)
    if emit:
        print("WP304_RAW_BINDING|" + hashlib.sha256(raw_record).hexdigest() + "|" + base64.b64encode(raw_record).decode())
    require(reports, "No produced JUnit; dependency/test failure remains blocked and diagnosed")
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--junit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--emit", action="store_true")
    args = parser.parse_args()
    try:
        retain(args.junit, args.output, args.emit)
        return 0
    except (OSError, ValueError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
