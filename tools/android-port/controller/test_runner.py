import argparse
import json
import sys
import unittest
from pathlib import Path


def run_suite(suite, verbosity=2):
    discovered = suite.countTestCases()
    if discovered == 0:
        print("BLOCKED: zero discovered controller tests", file=sys.stderr)
        return 2
    result = unittest.TextTestRunner(verbosity=verbosity).run(suite)
    print(json.dumps({
        "discovered": discovered, "run": result.testsRun,
        "passed": result.testsRun - len(result.failures) - len(result.errors) - len(result.skipped),
        "failed": len(result.failures), "errors": len(result.errors), "skipped": len(result.skipped),
        "scope": "controller fixtures only; not Android/iOS/hardware parity",
    }))
    return 0 if result.wasSuccessful() and result.testsRun == discovered and not result.skipped else 2


def main():
    parser = argparse.ArgumentParser(description="Stdlib controller test runner; missing/zero/skipped tests fail")
    parser.add_argument("--tests", type=Path, default=Path(__file__).resolve().parents[1] / "tests")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()
    if not args.tests.is_dir():
        print("BLOCKED: missing controller test directory", file=sys.stderr)
        return 2
    try:
        suite = unittest.defaultTestLoader.discover(str(args.tests))
    except (ImportError, OSError) as error:
        print(f"BLOCKED: controller discovery failed: {error}", file=sys.stderr)
        return 2
    return run_suite(suite, verbosity=0 if args.quiet else 2)


if __name__ == "__main__":
    sys.exit(main())
