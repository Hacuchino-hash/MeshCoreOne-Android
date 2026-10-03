import argparse
import json
import re
from pathlib import Path

from controller.errors import PortError
from controller.model import load_manifest
from controller.paths import permits
from controller.paths import git_path

HEADER = re.compile(r"^// PortedFrom: (.+)@([0-9a-f]{40})$", re.MULTILINE)
ANDROID_ONLY = re.compile(r"^// AndroidOnly: (WP-\d{3}) (.+)$", re.MULTILINE)
GENERATED = re.compile(r"^// GeneratedFrom: (.+)$", re.MULTILINE)


def port_map(manifest):
    directory = manifest.repo / "android"
    if not directory.exists():
        return []
    known = {e["path"] for e in manifest.data["inventory"]}
    results = []
    for path in sorted(directory.rglob("*.kt")):
        relative = path.relative_to(manifest.repo).as_posix()
        if any(part in ("build", ".gradle") for part in path.relative_to(directory).parts):
            continue
        content = path.read_text(encoding="utf-8")
        sources = HEADER.findall(content)
        android_only = ANDROID_ONLY.findall(content)
        generated = GENERATED.findall(content)
        if not sources and not android_only and not generated:
            raise PortError(f"Missing traceability header: {relative}")
        if sources and (android_only or generated):
            raise PortError(f"Conflicting traceability dispositions: {relative}")
        if len(set(sources)) != len(sources):
            raise PortError(f"Duplicate source header: {relative}")
        for source, sha in sources:
            if source not in known or sha != manifest.data["reference"]["commit"]:
                raise PortError(f"Unknown/stale source provenance: {relative}")
        for wp_id, reason in android_only:
            if not reason.strip() or not permits(manifest.wp(wp_id)["write_paths"], relative):
                raise PortError(f"Invalid Android-only owner/write path: {relative}")
        for declaration in generated:
            generator, separator, declared_inputs = declaration.partition("; inputs: ")
            if not separator or not declared_inputs.strip():
                raise PortError(f"Generated output must name generator and pinned inputs: {relative}")
            git_path(generator)
            if not generator.startswith("tools/android-port/") or not (manifest.repo / Path(generator)).is_file():
                raise PortError(f"Unknown generated-output generator: {relative}")
            for value in declared_inputs.split(", "):
                source, separator, sha = value.rpartition("@")
                if not separator or source not in known or sha != manifest.data["reference"]["commit"]:
                    raise PortError(f"Unknown/stale generated input: {relative}")
        results.append({
            "implementation": relative,
            "sources": [source for source, _ in sources],
            "android_only": [wp_id for wp_id, _ in android_only],
            "generated_inputs": generated,
            "feature_acceptance": "not established by headers",
        })
    return results


def main():
    parser = argparse.ArgumentParser(description="Derive many-to-many provenance without editing shared progress")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    try:
        print(json.dumps(port_map(load_manifest(args.repo)), indent=2))
    except (PortError, OSError) as error:
        parser.exit(2, f"BLOCKED: {error}\n")


if __name__ == "__main__":
    main()
