"""AndroidOnly: WP-003 Fail-safe full-scaffold selection for required Android CI."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from controller.errors import PortError
from controller.schema import load_json

SHA = re.compile(r"[0-9a-f]{40}")
ZERO = "0" * 40
EXACT = {
    ".github/workflows/android-ci.yml",
    "tools/android-port/controller/ci.py",
    "tools/android-port/controller/ci_environment.py",
    "tools/android-port/controller/ci_evidence.py",
    "tools/android-port/controller/module_junit.py",
    "tools/android-port/controller/scaffold_scope.py",
}
PREFIXES = ("android/", "tools/android-port/tests/", "docs/android/evidence/")
INDEPENDENT_ONLY_PREFIXES = (
    "android/core/protocol/",
    "android/core/data/src/main/kotlin/com/meshcoreone/android/core/data/backup/",
    "android/core/data/src/test/kotlin/com/meshcoreone/android/core/data/backup/",
    "android/core/database/",
    "docs/android/evidence/WP-202/",
    "docs/android/evidence/WP-203/",
)


def endpoints(event_name, event):
    if event_name == "workflow_dispatch":
        return None
    try:
        if event_name == "pull_request":
            base, head = event["pull_request"]["base"]["sha"], event["pull_request"]["head"]["sha"]
        elif event_name == "merge_group":
            base, head = event["merge_group"]["base_sha"], event["merge_group"]["head_sha"]
        elif event_name == "push":
            base, head = event["before"], event["after"]
            if base == ZERO:
                raise PortError("Push has no immutable before commit")
        else:
            raise PortError("Unknown workflow event")
    except (KeyError, TypeError) as error:
        raise PortError("Malformed workflow event endpoints") from error
    if not isinstance(base, str) or not isinstance(head, str) or not SHA.fullmatch(base) or not SHA.fullmatch(head):
        raise PortError("Malformed workflow event endpoint SHA")
    return base, head


def classify(paths):
    paths = sorted(set(paths))
    if not all(isinstance(path, str) and path and "\\" not in path for path in paths):
        raise PortError("Malformed changed path")
    relevant = [path for path in paths if path in EXACT or path.startswith(PREFIXES)]
    if relevant and all(path.startswith(INDEPENDENT_ONLY_PREFIXES) for path in relevant):
        return False
    return bool(relevant)


def evaluate(repo, event_name, event):
    try:
        pair = endpoints(event_name, event)
        if pair is None:
            return {"full": True, "reason": "workflow_dispatch", "fail_safe": False}
        base, head = pair
        completed = subprocess.run(
            ["git", "-C", str(repo), "diff", "--name-only", "--no-renames", base, head, "--"],
            check=True, capture_output=True, text=True,
        )
        paths = [line for line in completed.stdout.splitlines() if line]
        return {"full": classify(paths), "reason": "changed-input-classification",
                "fail_safe": False, "base": base, "head": head, "paths": paths}
    except (OSError, PortError, subprocess.SubprocessError, UnicodeError) as error:
        return {"full": True, "reason": f"fail-safe: {error}", "fail_safe": True}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--event-name", required=True)
    parser.add_argument("--event-path", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        event = load_json(args.event_path)
    except (OSError, PortError, UnicodeError) as error:
        result = {"full": True, "reason": f"fail-safe: {error}", "fail_safe": True}
    else:
        result = evaluate(args.repo, args.event_name, event)
    with args.output.open("a", encoding="utf-8", newline="\n") as stream:
        stream.write(f"full={'true' if result['full'] else 'false'}\n")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
