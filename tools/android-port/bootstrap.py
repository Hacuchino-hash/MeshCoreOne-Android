"""Bulk-install reviewed artifacts and freeze the single approved source inventory."""

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath

from controller.errors import PortError
from controller.model import APPROVED_PLAN_SHA256, REFERENCE_SHA, git, plan_rows, profile, tree
from controller.schema import digest, load_json
from inventory_rules import exclusion, kind, ownership, production_owner
from wp_metadata import enrich

SKILLS = ("android-port-wp", "swift-to-kotlin", "compose-from-swiftui")


def write_reviewed(destination: Path, text: str):
    if destination.exists() and destination.read_text(encoding="utf-8") != text:
        raise PortError(f"Refusing to overwrite different existing content: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(text, encoding="utf-8", newline="\n")


def format_generated(name: str, data: dict) -> str:
    if name != "port-manifest.json":
        return json.dumps(data, indent=2, ensure_ascii=True) + "\n"
    # One auditable line per exact source keeps the 1,866-entry inventory reviewable.
    value = {**data, "inventory": "__EXACT_INVENTORY__"}
    rows = ",\n".join("    " + json.dumps(entry, ensure_ascii=True) for entry in data["inventory"])
    return json.dumps(value, indent=2, ensure_ascii=True).replace(
        '"__EXACT_INVENTORY__"', "[\n" + rows + "\n  ]"
    ) + "\n"


def check_generated(repo: Path, manifest: dict, exclusions: dict):
    for name, expected in (("port-manifest.json", manifest), ("not-ported.json", exclusions)):
        actual = load_json(repo / "docs" / "android" / name)
        if actual != expected:
            raise PortError(f"Generated inventory drift: {name}; review attribution changes before explicit refresh")


def install(repo: Path, artifact_root: Path):
    original = (artifact_root / "plan.md").read_text(encoding="utf-8")
    if hashlib.sha256(original.encode()).hexdigest() != APPROVED_PLAN_SHA256:
        raise PortError("Approved artifact plan changed; re-review rather than silently installing")
    rows = plan_rows(original)
    agent_names = {r["owner"] for r in rows.values()} | {"parity-reviewer"}
    agents = artifact_root / "files" / "agents"
    if {p.name.removesuffix(".agent.md") for p in agents.glob("*.agent.md")} != agent_names:
        raise PortError("Staged profile roster differs from the approved plan")
    for name in sorted(agent_names):
        path = agents / f"{name}.agent.md"
        profile(path)
        write_reviewed(repo / ".github" / "agents" / path.name, path.read_text(encoding="utf-8"))
    for name in SKILLS:
        path = artifact_root / "files" / "skills" / name / "SKILL.md"
        content = path.read_text(encoding="utf-8")
        if not content.startswith(f"---\nname: {name}\n"):
            raise PortError(f"Staged skill identity mismatch: {name}")
        write_reviewed(repo / ".github" / "skills" / name / "SKILL.md", content)
    # Only documentation paths change; the approved normative plan is preserved.
    installed_plan = original.replace("files/agents/", "../../.github/agents/").replace(
        "files/skills/", "../../.github/skills/"
    )
    write_reviewed(repo / "docs" / "android" / "PORTING_PLAN.md", installed_plan)


def build_inventory(repo: Path) -> tuple[dict, dict]:
    inputs = tree(repo, REFERENCE_SHA)
    source_index = {}
    for path in inputs:
        if kind(path) != "production" or exclusion(path):
            continue
        owner = production_owner(path)
        stem = PurePosixPath(path).stem.split("+", 1)[0]
        source_index.setdefault(stem, set()).add(owner)
    records, exclusions = [], []
    errors = []
    for path, blob in sorted(inputs.items()):
        removed = exclusion(path)
        if removed:
            reason, adaptation_wp, review_basis, adaptation = removed
            exclusions.append({
                "path": path, "blob_sha": blob, "reason_code": reason,
                "adaptation_wp": adaptation_wp, "review_basis": review_basis,
                "android_adaptation": adaptation,
            })
            owner, consumers = None, [adaptation_wp]
        else:
            try:
                owner, consumers = ownership(path, source_index)
            except (PortError, KeyError) as error:
                errors.append(f"{path}: {error}")
                continue
        records.append({
            "path": path, "blob_sha": blob, "kind": kind(path),
            "primary_owner": owner, "cross_references": consumers,
            "exclusion": removed[0] if removed else None,
        })
    if errors:
        raise PortError("Ownership requires explicit review:\n" + "\n".join(errors))
    directory = repo / "docs" / "android"
    plan = (directory / "PORTING_PLAN.md").read_text(encoding="utf-8")
    rows = plan_rows(plan)
    agents = []
    for path in sorted((repo / ".github" / "agents").glob("*.agent.md")):
        agents.append({
            "name": profile(path)["name"],
            "sha256": hashlib.sha256(path.read_text(encoding="utf-8").encode()).hexdigest(),
        })
    manifest = {
        "schema_version": 1,
        "reference": {
            "repository": "Avi0n/MeshCoreOne",
            "commit": REFERENCE_SHA,
            "tree_sha": git(repo, "rev-parse", f"{REFERENCE_SHA}^{{tree}}").decode().strip(),
            "source_inventory_sha256": digest(inputs),
            "approved_plan_sha256": APPROVED_PLAN_SHA256,
            "installed_plan_sha256": hashlib.sha256(plan.encode()).hexdigest(),
        },
        "agents": agents,
        "work_packages": [enrich(wp_id, row) for wp_id, row in rows.items()],
        "inventory": records,
        "implementations": [],
    }
    not_ported = {"schema_version": 1, "reference_sha": REFERENCE_SHA, "entries": exclusions}
    return manifest, not_ported


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--artifact-root", type=Path)
    parser.add_argument("--write", action="store_true", help="Explicit bulk installation, never dispatch")
    parser.add_argument("--refresh-generated", action="store_true",
                        help="Explicit reviewed refresh of generated JSON only; never source/profile/plan")
    args = parser.parse_args()
    try:
        if args.artifact_root:
            if not args.write:
                raise PortError("Artifact installation requires explicit --write")
            install(args.repo, args.artifact_root)
        manifest, exclusions = build_inventory(args.repo)
        if not args.write:
            check_generated(args.repo, manifest, exclusions)
        if args.write:
            for name, data in (("port-manifest.json", manifest), ("not-ported.json", exclusions)):
                destination = args.repo / "docs" / "android" / name
                content = format_generated(name, data)
                if args.refresh_generated:
                    destination.write_text(content, encoding="utf-8", newline="\n")
                else:
                    write_reviewed(destination, content)
        print(json.dumps({
            "dry_run": not args.write,
            "tracked_reference_files": len(manifest["inventory"]),
            "work_packages": len(manifest["work_packages"]),
            "dependency_edges": sum(len(w["depends_on"]) for w in manifest["work_packages"]),
            "excluded_files": len(exclusions["entries"]),
        }, indent=2))
        return 0
    except (PortError, OSError) as error:
        parser.exit(2, f"BLOCKED: {error}\n")


if __name__ == "__main__":
    main()
