# AndroidOnly: WP-301 Preserve complete produced XML before fail-closed native validation, including failures.
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import subprocess
import sys
import unittest

import collect_evidence as reader


def require(condition, message):
    if not condition:
        raise reader.PortError("WP-301 raw retention: " + message)


def linked(path):
    return path.is_symlink() or (os.name == "nt" and path.exists()
        and bool(path.lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT))


def safe_path(path):
    require(path.is_absolute(), "an explicit absolute path is required")
    require(not any(linked(parent) for parent in (path, *path.parents)), "linked evidence path")


def copy_reports(source, output, repository):
    safe_path(source)
    safe_path(output)
    safe_path(repository)
    require(not output.resolve().is_relative_to(repository.resolve())
        and not repository.resolve().is_relative_to(output.resolve()), "output overlaps repository inputs")
    files = sorted(source.glob("TEST-*.xml"))
    require(0 < len(files) <= 32, "missing or excessive produced raw XML")
    require(all(not linked(path) and path.is_file() and re.fullmatch(r"TEST-[A-Za-z0-9_.-]+\.xml", path.name)
        and 0 < path.stat().st_size <= 64 * 1024 * 1024 for path in files), "unsafe or empty produced XML")
    require(sum(path.stat().st_size for path in files) <= 128 * 1024 * 1024, "produced XML exceeds bounded size")
    output.mkdir(parents=True, exist_ok=False)
    destination = output / "junit"
    destination.mkdir()
    records = []
    for path in files:
        target = destination / path.name
        shutil.copyfile(path, target)
        raw = path.read_bytes()
        require(raw == target.read_bytes(), "copied XML bytes changed")
        records.append({"name": path.name, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()})
    return destination, records


def retain(source, output):
    destination, reports = copy_reports(source, output, reader.ROOT)
    record = {
        "schema_version": 1, "work_package": "WP-301",
        "scope": "Complete produced raw JUnit; not suite, source parity, device, hardware or gate acceptance.",
        "source_sha": reader.PIN, "host": platform.system().lower(),
        "observed_head_sha": reader.git("rev-parse", "HEAD").decode().strip(),
        "reports": reports, "result": "unvalidated",
    }
    try:
        _, _, _, expected_methods, current_inputs, _ = reader.static_inventory()
        for relative in ("android/core/designsystem/build.gradle.kts", "android/core/designsystem/gradle.lockfile"):
            raw = reader.ROOT.joinpath(*relative.split("/")).read_bytes()
            current_inputs[relative] = {
                "working_blob": subprocess.check_output(
                    ["git", "-C", str(reader.ROOT), "hash-object", "--path", relative, "--stdin"], input=raw,
                ).decode().strip(),
                "sha256": hashlib.sha256(raw).hexdigest(), "size": len(raw),
            }
        record["current_inputs"] = current_inputs
        record["input_changes_vs_observed_head"] = reader.git(
            "diff", "--name-only", "HEAD", "--", "android/core/designsystem",
        ).decode().splitlines()
        record["expected_methods"] = sorted(expected_methods)
        outcomes = record["observed_case_outcomes"] = []
        for report in sorted(destination.glob("TEST-*.xml")):
            for case in reader.read_xml(report).iter("testcase"):
                name = re.split(r"\[", case.get("name", ""), maxsplit=1)[0].removesuffix("()")
                identity = case.get("classname", "") + "#" + name
                statuses = [status for status in ("failure", "error", "skipped") if case.find(status) is not None]
                outcomes.append({"identity": identity, "reported_outcome": statuses[0] if statuses else "passed"})
        observed = {case["identity"] for case in outcomes}
        require(observed == expected_methods and len(outcomes) == len(observed), "missing/extra/duplicate current native methods")
        record["counts"] = reader.suite_counts(destination, 1)
        record["result"] = "raw-preserved-nonzero-passing-counts-not-parity-acceptance"
    except (reader.PortError, ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        record["result"] = "blocked-raw-preserved"
        record["error"] = str(error)
        raise
    finally:
        (output / "native-retention.json").write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf8")
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--junit", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    try:
        if args.self_test:
            path = Path(__file__).with_name("test_native_retention.py")
            require(path.is_file(), "missing native-retention reader tests")
            spec = importlib.util.spec_from_file_location("wp301_native_retention_tests", path)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            suite = unittest.defaultTestLoader.loadTestsFromModule(module)
            require(suite.countTestCases() > 0, "zero native-retention reader tests")
            tested = unittest.TextTestRunner(verbosity=1).run(suite)
            require(tested.wasSuccessful() and not tested.skipped, "failed/skipped native-retention tests")
        if args.junit is not None:
            require(args.output is not None, "explicit private output directory is required")
            retain(args.junit, args.output)
        else:
            require(args.self_test, "raw JUnit directory is required")
        return 0
    except (reader.PortError, ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
