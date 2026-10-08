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

BOOTSTRAP_POLICY_AMENDMENT = {
    "schema_version": 1,
    "amendment_id": "WP-000-capability-reservations-v1",
    "policy_commit_sha": "4658275ca7f753527aa14fd5eacbad3ae606536e",
    "previous_approved_plan_sha256": "0afc364cd63a99f438bffe7df8542d49503bfd113c363ed89e60d284b6a3c837",
    "approved_plan_sha256": "badb1f34f22295d0f7ac4316eba0cc5f6189c9012ac7c96981fb352e9133944c",
    "previous_generated_manifest_sha256": "f3fd3a0a51841a8fb43d3f2c4b3953e6ef564d4e96e74f4e3035200face5c90e",
    "generated_manifest_sha256": "b30b20c2d2225c98e13c52020a251d7bad87febd27e9dc1929c142c19c10d8b6",
    "previous_final_manifest_sha256": "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746",
    "final_manifest_sha256": "ceb84b5e26fcc9ece5c0b3fb6c68b4d2965f9f24114fa81b7434ff73d1ed7904",
    "reference_sha": "db14559b39d32322b06477c6ae676112f583db50",
    "work_packages": 65,
    "dependency_edges": 185,
    "agents": 17,
    "human_gates": 8,
}
BOOTSTRAP_MANIFEST_SHA256 = BOOTSTRAP_POLICY_AMENDMENT["generated_manifest_sha256"]
POLICY_AMENDMENT_EVIDENCE = Path("docs/android/evidence/WP-000/bootstrap-verifier.json")
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
    if digest(result) != BOOTSTRAP_POLICY_AMENDMENT["final_manifest_sha256"]:
        raise PortError("Frozen bootstrap final manifest changed outside the protected WP-003 overlay")
    return result


def check_configuration(repo: Path):
    from bootstrap import build_inventory

    generated, exclusions = build_inventory(repo)
    expected = apply_overlay(generated)
    actual = load_json(repo / "docs" / "android" / "port-manifest.json")
    amendment = load_json(repo / POLICY_AMENDMENT_EVIDENCE)
    if amendment != BOOTSTRAP_POLICY_AMENDMENT:
        raise PortError("Protected bootstrap policy amendment evidence drift")
    if actual != expected or load_json(repo / "docs" / "android" / "not-ported.json") != exclusions:
        raise PortError("Protected verification overlay or frozen inventory drift")
    return {
        "schema_version": 1, "result": "valid", "bootstrap_manifest_sha256": digest(generated),
        "manifest_sha256": digest(expected), "verification_amendments": sorted(AMENDMENTS),
        "policy_amendment": amendment["amendment_id"],
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
