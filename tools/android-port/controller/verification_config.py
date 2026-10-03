"""AndroidOnly: WP-003 Protected verification-only overlay on the frozen WP-000 generator."""

import argparse
import copy
import json
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from controller.errors import PortError
from controller.schema import digest, load_json

BOOTSTRAP_MANIFEST_SHA256 = "f3fd3a0a51841a8fb43d3f2c4b3953e6ef564d4e96e74f4e3035200face5c90e"
CI = "tools/android-port/controller/ci.py"
AMENDMENTS = {
    "WP-002": {
        "configured": True,
        "commands": [
            ["python", CI, "preflight"],
            ["python", CI, "run", "--stage", "verify"],
            ["python", CI, "run", "--stage", "standalone"],
            ["python", CI, "run", "--stage", "assemble"],
            ["python", CI, "run", "--stage", "lint"],
            ["python", CI, "inspect"],
        ],
        "blocker": "",
    },
    "WP-003": {
        "configured": True,
        "commands": [
            ["python", "tools/android-port/controller/verification_config.py", "--check"],
            ["python", "tools/android-port/controller/workflows.py"],
            ["python", "tools/android-port/controller/test_runner.py"],
            ["python", CI, "python"],
            ["python", CI, "preflight"],
            ["python", CI, "run", "--stage", "verify"],
            ["python", CI, "run", "--stage", "standalone"],
            ["python", CI, "run", "--stage", "assemble"],
            ["python", CI, "run", "--stage", "lint"],
            ["python", CI, "inspect"],
        ],
        "blocker": "",
    },
}


def apply_overlay(data: dict):
    if digest(data) != BOOTSTRAP_MANIFEST_SHA256:
        raise PortError("Frozen bootstrap generator changed outside the protected WP-003 overlay")
    result = copy.deepcopy(data)
    for wp in result["work_packages"]:
        if wp["id"] in AMENDMENTS:
            wp["verification"] = copy.deepcopy(AMENDMENTS[wp["id"]])
    return result


def check_configuration(repo: Path):
    from bootstrap import build_inventory

    generated, exclusions = build_inventory(repo)
    expected = apply_overlay(generated)
    actual = load_json(repo / "docs" / "android" / "port-manifest.json")
    if actual != expected or load_json(repo / "docs" / "android" / "not-ported.json") != exclusions:
        raise PortError("Protected verification overlay or frozen inventory drift")
    return {
        "schema_version": 1, "result": "valid", "bootstrap_manifest_sha256": digest(generated),
        "manifest_sha256": digest(expected), "verification_amendments": sorted(AMENDMENTS),
        "feature_verification_configured": False, "human_or_dependency_acceptance": False,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--check", action="store_true", help="Read-only; never refresh the frozen bootstrap")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(check_configuration(args.repo), indent=2))
        return 0
    except (PortError, OSError) as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
