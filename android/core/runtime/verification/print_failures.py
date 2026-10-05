"""AndroidOnly: WP-207 Bounded actual failure diagnostics, not a verification verdict."""

from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=Path)
args = parser.parse_args()
if args.output is not None and (not args.output.is_absolute() or args.output.resolve().is_relative_to(ROOT)):
    raise ValueError("Failure evidence requires a separate absolute job output")
directories = [
    ROOT / "android/core/runtime/build/test-results/test",
    ROOT / "android/core/data/build/test-results/testDebugUnitTest",
    ROOT / "android/core/datastore/build/test-results/testDebugUnitTest",
]
reported = 0
reports = []
for directory in directories:
    for path in sorted(directory.glob("TEST-*.xml")):
        if path.is_symlink() or path.stat().st_size > 16 * 1_048_576:
            raise ValueError("Unsafe failure diagnostic input")
        raw = path.read_bytes()
        declarations = raw.replace(b"\x00", b"").upper()
        if b"<!DOCTYPE" in declarations or b"<!ENTITY" in declarations:
            raise ValueError("Unsafe failure diagnostic XML")
        suite = ET.fromstring(raw)
        if args.output is not None:
            destination = args.output / directory.parents[2].name / path.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            if destination.exists() and destination.read_bytes() != raw:
                raise ValueError("Failure evidence destination would overwrite different bytes")
            shutil.copyfile(path, destination)
        reports.append({
            "source": path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(raw).hexdigest(),
            "size_bytes": len(raw), "discovered": len(suite.findall("testcase")),
            "failures": int(suite.get("failures", "0")), "errors": int(suite.get("errors", "0")),
            "skipped": int(suite.get("skipped", "0")),
        })
        for case in suite.findall("testcase"):
            for kind in ("failure", "error", "skipped"):
                outcome = case.find(kind)
                if outcome is None:
                    continue
                if reported < 24:
                    print(f"ACTUAL {kind.upper()}: {case.get('classname')}::{case.get('name')}", flush=True)
                    print((outcome.get("message", "") + "\n" + (outcome.text or ""))[:3500], flush=True)
                reported += 1
                if reported == 24:
                    print("Additional raw failures remain in the module's full JUnit reports.", flush=True)
if args.output is not None:
    sys.path.insert(0, str(ROOT / "tools" / "android-port"))
    from controller.module_junit import module_sources

    invocation = args.output.parent / "wp109-invocation.json"
    binding = json.loads(invocation.read_text(encoding="utf-8")) if invocation.exists() else None
    result = {
        "schema_version": 1, "work_package": "WP-207",
        "scope": "Full raw discovered outcomes retained before fail-closed verification; never PASS or source acceptance",
        "head_sha": subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "HEAD"], text=True).strip(),
        "actual_executor_invocation": binding,
        "committed_module_inputs": {key: value for key, value in module_sources(ROOT).items()
                                    if key in {"core/runtime", "core/data", "core/datastore"}},
        "reports": reports, "failed_nodes": reported,
    }
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "raw-outcomes.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(f"Actual failed/skipped JUnit nodes printed: {reported}; this is diagnostic output, not PASS.", flush=True)
