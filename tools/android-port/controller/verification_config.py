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
VERIFICATION_MANIFEST_SHA256 = "78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746"
CONTENT_SCOPE_PATHS = (
    "android/app/src/main/kotlin/com/meshcoreone/android/app/content/",
    "android/app/src/test/kotlin/com/meshcoreone/android/app/content/",
)
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


def apply_content_scope(data: dict):
    if not isinstance(data, dict) or digest(data) != VERIFICATION_MANIFEST_SHA256:
        raise PortError("Content scope requires the complete frozen verification manifest")
    result = copy.deepcopy(data)
    wp = next(item for item in result["work_packages"] if item["id"] == "WP-218")
    wp["write_paths"][2:2] = CONTENT_SCOPE_PATHS
    return result


def project_content_scope(data: dict):
    if not isinstance(data, dict):
        raise PortError("Malformed content-scope manifest lineage")
    result = copy.deepcopy(data)
    if digest(result) == VERIFICATION_MANIFEST_SHA256:
        return result
    packages = result.get("work_packages")
    if not isinstance(packages, list) or not all(isinstance(wp, dict) for wp in packages):
        raise PortError("Malformed content-scope work packages")
    owned = [wp for wp in packages if wp.get("id") == "WP-218"]
    if len(owned) != 1:
        raise PortError("Content scope must preserve exactly one original WP-218")
    paths = owned[0].get("write_paths")
    if not isinstance(paths, list) or paths[2:4] != list(CONTENT_SCOPE_PATHS):
        raise PortError("Content scope must contain only the exact ordered two-prefix addition")
    del paths[2:4]
    if digest(result) != VERIFICATION_MANIFEST_SHA256:
        raise PortError("Content scope changed the frozen manifest outside its exact two-prefix addition")
    return result


def content_scope_predecessor(manifest):
    from controller.model import Manifest

    return Manifest(project_content_scope(manifest.data), manifest.exclusions, manifest.repo)


def content_scope_revisions(manifest, policy):
    from controller.gates import policy_revision

    predecessor = content_scope_predecessor(manifest)
    if policy_revision(predecessor, policy) != "56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a":
        raise PortError("Frozen predecessor policy changed outside the approved content scope")
    return {"manifest_sha256": manifest.sha256, "policy_revision": policy_revision(manifest, policy)}


def inventory_details_predecessor(details, manifest):
    predecessor = content_scope_predecessor(manifest)
    if (not isinstance(details, dict) or details.get("source_sha") != predecessor.data["reference"]["commit"]
            or details.get("manifest_sha256") != manifest.sha256):
        raise PortError("Original inventory details do not bind the actual canonical catalog")
    result = copy.deepcopy(details)
    result["manifest_sha256"] = predecessor.sha256
    return result


def check_configuration(repo: Path):
    from bootstrap import build_inventory

    generated, exclusions = build_inventory(repo)
    expected = apply_overlay(generated)
    actual = load_json(repo / "docs" / "android" / "port-manifest.json")
    if project_content_scope(actual) != expected or load_json(repo / "docs" / "android" / "not-ported.json") != exclusions:
        raise PortError("Protected verification overlay or frozen inventory drift")
    return {
        "schema_version": 1, "result": "valid", "bootstrap_manifest_sha256": digest(generated),
        "manifest_sha256": digest(actual), "verification_amendments": sorted(AMENDMENTS),
        "content_scope_amended": actual != expected,
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
