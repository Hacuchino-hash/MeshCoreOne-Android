"""AndroidOnly: WP-004 Reuse real CI binding, never publish or invent protected gates."""

from controller.ci import execution_identity
from controller.gates import Binding, policy_revision
from controller.model import load_manifest
from controller.schema import load_json
from oracle.reference import REPO, git


def evidence_identity():
    identity = execution_identity()
    if identity is not None:
        binding = dict(identity["binding"])
        binding["work_package"] = "WP-004"
        Binding.parse(binding)
        return {"binding": binding, "run_id": identity["run_id"], "run_attempt": identity["run_attempt"],
                "scope": "actual candidate CI identity; not an authenticated protected publisher/review verdict"}
    manifest = load_manifest(REPO)
    policy = load_json(REPO / "docs" / "android" / "automation-policy.json")
    head = git(REPO, "rev-parse", "HEAD").decode("ascii").strip()
    base = git(REPO, "merge-base", "HEAD", "origin/main").decode("ascii").strip()
    binding = Binding("cbattlegear/MeshCoreOne-Android", "WP-004", base, head,
                      manifest.data["reference"]["commit"], manifest.sha256, policy_revision(manifest, policy))
    from dataclasses import asdict
    return {"binding": asdict(binding), "run_id": None, "run_attempt": None,
            "scope": "local Git inputs only; no hosted run authority"}
