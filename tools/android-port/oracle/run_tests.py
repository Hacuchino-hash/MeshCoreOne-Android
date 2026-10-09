"""AndroidOnly: WP-004 Positive discovery using the existing fail-closed test runner."""

import argparse
import contextlib
import io
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from controller.test_runner import run_suite
from oracle.reference import OracleError, json_bytes, write_or_check

REQUIRED_SUITES = {
    "test_inventory_tools.py", "test_vectors.py", "test_codec.py", "test_ci.py",
    "test_workflow_scope.py",
}


def discover(directory: Path, pattern="test_*.py"):
    if not directory.is_dir():
        raise OracleError("Missing WP-004 tool test directory")
    paths = sorted(directory.glob(pattern))
    if pattern == "test_*.py" and not REQUIRED_SUITES.issubset({path.name for path in paths}):
        raise OracleError("Missing mandatory WP-004 tool suite")
    if not paths:
        raise OracleError("Zero discovered WP-004 tool suites")
    suite = unittest.TestLoader().discover(str(directory), pattern=pattern)
    if suite.countTestCases() == 0:
        raise OracleError("Zero discovered WP-004 tool assertions")
    return suite


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tests", type=Path, default=Path(__file__).resolve().parent)
    parser.add_argument("--pattern", default="test_*.py")
    parser.add_argument("--quiet", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        suite = discover(args.tests, args.pattern)
        captured = io.StringIO()
        with contextlib.redirect_stdout(captured):
            status = run_suite(suite, verbosity=0 if args.quiet else 2)
        result = json.loads(captured.getvalue().strip().splitlines()[-1])
        result["scope"] = "WP-004 Python tool assertions; no original Swift/feature/HIL acceptance"
        if args.output:
            write_or_check(args.output, json_bytes(result), check=False)
        print(json_bytes(result).decode(), end="")
        return status
    except (OracleError, OSError, ImportError, ValueError) as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
