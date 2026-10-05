"""AndroidOnly: WP-207 Bounded actual failure diagnostics, not a verification verdict."""

from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
directories = [
    ROOT / "android/core/runtime/build/test-results/test",
    ROOT / "android/core/data/build/test-results/testDebugUnitTest",
    ROOT / "android/core/datastore/build/test-results/testDebugUnitTest",
]
reported = 0
for directory in directories:
    for path in sorted(directory.glob("TEST-*.xml")):
        if path.is_symlink() or path.stat().st_size > 16 * 1_048_576:
            raise ValueError("Unsafe failure diagnostic input")
        raw = path.read_bytes()
        declarations = raw.replace(b"\x00", b"").upper()
        if b"<!DOCTYPE" in declarations or b"<!ENTITY" in declarations:
            raise ValueError("Unsafe failure diagnostic XML")
        suite = ET.fromstring(raw)
        for case in suite.findall("testcase"):
            for kind in ("failure", "error", "skipped"):
                outcome = case.find(kind)
                if outcome is None:
                    continue
                print(f"ACTUAL {kind.upper()}: {case.get('classname')}::{case.get('name')}", flush=True)
                print((outcome.get("message", "") + "\n" + (outcome.text or ""))[:3500], flush=True)
                reported += 1
                if reported >= 24:
                    print("Additional raw failures remain in the module's full JUnit reports.", flush=True)
                    raise SystemExit(0)
print(f"Actual failed/skipped JUnit nodes printed: {reported}; this is diagnostic output, not PASS.", flush=True)
