import argparse
import json
import re
from pathlib import Path

from controller.errors import PortError
from controller.model import load_manifest
from controller.paths import permits
from controller.paths import git_path
from controller.schema import digest, load_json
from controller.verification_config import content_scope_predecessor, VERIFICATION_MANIFEST_SHA256

HEADER = re.compile(r"^// PortedFrom: (.+)@([0-9a-f]{40})$", re.MULTILINE)
ANDROID_ONLY = re.compile(r"^// AndroidOnly: (WP-\d{3}) (.+)$", re.MULTILINE)
GENERATED = re.compile(r"^// GeneratedFrom: (.+)$", re.MULTILINE)

WP_302_SCOPE_PATHS = (
    "android/app/src/main/kotlin/com/meshcoreone/android/MainActivity.kt",
    "android/app/src/test/kotlin/com/meshcoreone/android/app/navigation/",
)
WP_302_SCOPE_PROOF = "docs/android/evidence/WP-218/navigation-owner-scope-admission.json"
WP_302_SCOPE_APPROVAL = {
    "schema_version": 1,
    "repository": "cbattlegear/MeshCoreOne-Android",
    "work_package": "WP-302",
    "coordinator_session": "bcb17a74-5fa6-47d0-a4be-b6b595e20559",
    "source_sha": "db14559b39d32322b06477c6ae676112f583db50",
    "receiver_base_sha": "67857474ef3d3012ab73c19a4943ec1334c20676",
    "predecessor_manifest_sha256": VERIFICATION_MANIFEST_SHA256,
    "write_paths": list(WP_302_SCOPE_PATHS),
    "authority": "Coordinator-issued necessary WP218 support amendment; exact WP302 launcher and unit-navigation prefix only",
    "acceptance": "Ownership/traceability admission only; no source, parity, human or merge acceptance",
}


def navigation_scope_admitted(manifest, relative):
    if not (relative == WP_302_SCOPE_PATHS[0] or relative.startswith(WP_302_SCOPE_PATHS[1])):
        return False
    predecessor = content_scope_predecessor(manifest)
    proof = load_json(manifest.repo / WP_302_SCOPE_PROOF)
    if predecessor.sha256 != VERIFICATION_MANIFEST_SHA256 or digest(proof) != digest(WP_302_SCOPE_APPROVAL):
        raise PortError("Missing/stale exact WP-302 launcher/unit-navigation scope approval")
    return True

# Coordinator-approved, fixed allowance for exactly the four WP-218 native-adapter
# test files whose write path is outside the WP-218 manifest entry's `write_paths`
# (admitted native-adapter app/content tests, not a `write_paths` edit). This is a
# closed, literal, case-sensitive set for WP-218 only -- never a prefix, glob, or a
# relaxation of `permits()` for any other write-path check or working party. See
# docs/android/evidence/WP-218/run-summary.json
# (native_adapter_write_path_manifest_gap_2026_10_05) for the admitted disclosure.
WP_218_FIXED_TEST_ALLOWANCE = frozenset({
    "android/app/src/test/kotlin/com/meshcoreone/android/app/content/AndroidGeocoderAdapterTest.kt",
    "android/app/src/test/kotlin/com/meshcoreone/android/app/content/BitmapImageDecoderTest.kt",
    "android/app/src/test/kotlin/com/meshcoreone/android/app/content/DataStoreLinkPreviewPreferencesSourceTest.kt",
    "android/app/src/test/kotlin/com/meshcoreone/android/app/content/LocationManagerLocationProducingTest.kt",
})


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
            fixed_allowed = wp_id == "WP-218" and relative in WP_218_FIXED_TEST_ALLOWANCE
            declared = permits(manifest.wp(wp_id)["write_paths"], relative)
            nav_allowed = wp_id == "WP-302" and not declared and navigation_scope_admitted(manifest, relative)
            if not reason.strip() or not (fixed_allowed or declared or nav_allowed):
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
