import argparse
import json
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from controller.errors import PortError
from controller.gates import integrity
from controller.model import git, load_manifest
from controller.schema import decode_json, load_json


def main(argv=None):
    parser = argparse.ArgumentParser(description="Validate pinned schema, source hashes, ownership and DAG")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[3])
    parser.add_argument("--gate-base", help="Immutable trusted base SHA; inspect candidate objects, never execute them")
    parser.add_argument("--gate-candidate")
    args = parser.parse_args(argv)
    try:
        manifest = load_manifest(args.repo)
        result = {"result": "valid", **manifest.counts(), "manifest_sha256": manifest.sha256}
        if bool(args.gate_base) != bool(args.gate_candidate):
            raise PortError("Gate integrity requires both trusted base and candidate SHAs")
        if args.gate_base:
            from controller.model import SHA

            if any(SHA.fullmatch(sha) is None for sha in (args.gate_base, args.gate_candidate)):
                raise PortError("Gate candidate/base must be immutable full SHAs")
            base = decode_json(git(args.repo, "show", f"{args.gate_base}:docs/android/port-manifest.json").decode())
            candidate = decode_json(git(args.repo, "show", f"{args.gate_candidate}:docs/android/port-manifest.json").decode())
            policy = decode_json(git(args.repo, "show", f"{args.gate_base}:docs/android/automation-policy.json").decode())
            paths = git(args.repo, "diff", "--name-only", "--no-renames", args.gate_base, args.gate_candidate).decode().splitlines()
            result["gate_audit"] = integrity(base, candidate, policy, paths)
            result["authoritative"] = False
            if result["gate_audit"]["human_gate_required"]:
                print(json.dumps(result, indent=2))
                raise PortError("Protected-path candidate requires real bound maintainer approval; offline audit cannot approve")
        print(json.dumps(result, indent=2))
        return 0
    except (PortError, OSError, UnicodeError) as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
