"""WP-204 narrowly admitted checksum amendment preserving frozen incumbent and WP-205 SDK entries."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[3]
PATH = "android/gradle/verification-metadata.xml"
BASE = "dc15f1ba445acf3230383ea68d4827c592f3fafa"
HANDOFF = "c840c7c60335f8bcf1c7f990b9ac071a19c97d56"
HANDOFF_BLOB = "fad6f9bc00f9be911bcde5d2d19b4e8828dc13f3"
HANDOFF_SHA256 = "ad8b7cf63f661aa2ded1d23234280acffd901ef45f3e0cf554f8a887323fe85c"
NS = "{https://schema.gradle.org/dependency-verification}"
ALLOWED = {
    ("androidx.datastore", name, "1.2.1") for name in (
        "datastore", "datastore-android", "datastore-core", "datastore-core-android",
        "datastore-core-okio", "datastore-core-okio-jvm", "datastore-preferences", "datastore-preferences-android",
        "datastore-preferences-core", "datastore-preferences-core-android",
        "datastore-preferences-proto", "datastore-preferences-external-protobuf",
    )
} | {
    ("com.squareup.okio", name, "3.9.1") for name in ("okio", "okio-jvm")
} | {
    ("org.jetbrains.kotlinx", name, "1.7.3") for name in (
        "kotlinx-serialization-core", "kotlinx-serialization-core-jvm",
        "kotlinx-serialization-json", "kotlinx-serialization-json-jvm",
    )
}
SDK_ENTRIES = {
    "12.1-robolectric-8229987-i7": {
        "jar": "4bcf8fde62de31d0c6d6b98bd667abc9da526d46848d31284b5238ca6192c3ca",
        "pom": "25de605dc8d7e528acd5f7d00e535e655989a9845b5d4d1317790208d83252b7",
    },
    "13-robolectric-9030017-i7": {
        "jar": "1c3fd182c03d47626686ca3b39c4ec5bdb0898b1056f4eb8f96f32c6209a5c04",
        "pom": "cc3ee7cd344a048a93ca0e288a64933453fd17e0b4af157bc547b06d5878e7e0",
    },
    "17-robolectric-15733970-i7": {
        "jar": "30a15243ba9a6a016361876facc75432f4ec5db0f9e27d4984a4a6b167b432bd",
        "pom": "100b1f5eb235d0cdecce350f153984f12ad2fe0531237d51843e12a409e6d3c8",
    },
}


def require(condition, description):
    if not condition:
        raise ValueError(description)


def git(*arguments):
    return subprocess.check_output(["git", "-C", str(ROOT), *arguments])


def canonical(element):
    return element.tag, tuple(sorted(element.attrib.items())), element.text.strip() if element.text else "", tuple(
        canonical(child) for child in element
    )


def parsed(raw):
    require(len(raw) <= 2 * 1_048_576 and b"<!DOCTYPE" not in raw.upper() and b"<!ENTITY" not in raw.upper(),
            "Oversized/unsafe verification metadata")
    root = ET.fromstring(raw)
    require(root.tag == NS + "verification-metadata", "Unexpected metadata schema")
    components = {}
    for node in root.findall(NS + "components/" + NS + "component"):
        key = tuple(node.attrib[name] for name in ("group", "name", "version"))
        require(key not in components, "Duplicate verification coordinate")
        names = [artifact.attrib["name"] for artifact in node]
        require(len(names) == len(set(names)), "Duplicate verification artifact")
        components[key] = node
    require(components, "Missing incumbent verification components")
    return root, components


def checksums(component):
    return {
        artifact.attrib["name"]: {checksum.attrib["value"] for checksum in artifact.findall(NS + "sha256")}
        for artifact in component.findall(NS + "artifact")
    }


def frozen_inputs():
    base = git("show", f"{BASE}:{PATH}")
    handoff = git("show", f"{HANDOFF}:{PATH}")
    require(git("rev-parse", f"{HANDOFF}:{PATH}").decode().strip() == HANDOFF_BLOB, "SDK handoff blob mismatch")
    require(len(handoff) == 268252 and hashlib.sha256(handoff).hexdigest() == HANDOFF_SHA256, "SDK handoff hash/size mismatch")
    base_root, incumbent = parsed(base)
    handoff_root, transferred = parsed(handoff)
    require(canonical(base_root.find(NS + "configuration")) == canonical(handoff_root.find(NS + "configuration")),
            "Frozen handoff changed verification configuration")
    for key, component in incumbent.items():
        require(key in transferred and canonical(component) == canonical(transferred[key]), "Frozen handoff changed an incumbent pin")
    sdk_keys = {("org.robolectric", "android-all-instrumented", version) for version in SDK_ENTRIES}
    require(set(transferred) - set(incumbent) == sdk_keys, "Frozen handoff includes unapproved components")
    for version, artifacts in SDK_ENTRIES.items():
        expected = {f"android-all-instrumented-{version}.{kind}": {digest} for kind, digest in artifacts.items()}
        require(checksums(transferred[("org.robolectric", "android-all-instrumented", version)]) == expected,
                "Frozen SDK checksum tuple mismatch")
    return base, handoff, transferred, handoff_root


def artifact_block(name, digest):
    require(re.fullmatch(r"[0-9a-f]{64}", digest) is not None, "Malformed actual publisher SHA256")
    return (
        f"         <artifact name={quoteattr(name)}>\n"
        f"            <sha256 value={quoteattr(digest)} origin=\"WP-204 independently verified publisher bytes and metadata\"/>\n"
        "         </artifact>\n"
    )


def render():
    base, handoff, transferred, handoff_root = frozen_inputs()
    publication = json.loads((OUT / "publisher-inputs.json").read_text(encoding="utf8"))
    declaration = json.loads((OUT / "dependency-admission.json").read_text(encoding="utf8"))
    require(publication["counts"] == {
        "components": 18, "publisher_poms": 18, "publisher_modules": 18, "actual_linked_binaries": 10,
    }, "Incomplete actual reviewed publisher graph")
    require({tuple(row["coordinate"].split(":")) for row in publication["components"]} == ALLOWED,
            "Unexpected coordinate requires a concrete publisher request")
    text = handoff.decode("utf8")
    block_pattern = re.compile(r"^      <component\b[\s\S]*?^      </component>\n", re.MULTILINE)
    blocks = {}
    for match in block_pattern.finditer(text):
        node = ET.fromstring(match.group())
        key = tuple(node.attrib[name] for name in ("group", "name", "version"))
        require(key not in blocks, "Duplicate raw component block")
        blocks[key] = match.group()
    require(set(blocks) == set(transferred), "Raw component preservation map differs from parsed input")
    added = []
    preserved = 0
    for row in publication["components"]:
        key = tuple(row["coordinate"].split(":"))
        expected = {}
        for kind in ("module", "pom"):
            record = row[kind]
            require(record["publisher_sha1_matched"], "Unverified publisher metadata")
            expected[record["url"].rsplit("/", 1)[1]] = record["sha256"]
        require(expected[key[1] + "-" + key[2] + ".module"] ==
                declaration["independent_publisher_module_sha256"][row["coordinate"]], "Independent module pin drift")
        for binary in row["verified_binaries"]:
            require(binary["publisher_sha1_matched"] and binary["publisher_module_sha256_matched"], "Unverified runtime binary")
            require(binary["sha256"] == declaration["independent_publisher_binary_sha256"][binary["publisher_filename"]],
                    "Independent binary pin drift")
            expected[binary["gradle_artifact_name"]] = binary["sha256"]
        existing = checksums(transferred[key]) if key in transferred else {}
        block = blocks.get(key) or (
            f"      <component group={quoteattr(key[0])} name={quoteattr(key[1])} version={quoteattr(key[2])}>\n"
            "      </component>\n"
        )
        for name, digest in sorted(expected.items()):
            if name in existing:
                require(existing[name] == {digest}, "Actual publisher differs from existing admitted artifact: " + name)
                preserved += 1
            else:
                block = block.replace("      </component>\n", artifact_block(name, digest) + "      </component>\n", 1)
                added.append({"coordinate": ":".join(key), "artifact": name, "sha256": digest})
        blocks[key] = block
    start = text.index("   <components>\n") + len("   <components>\n")
    end = text.index("   </components>", start)
    output = (text[:start] + "".join(blocks[key] for key in sorted(blocks)) + text[end:]).encode("utf8")
    output_root, output_components = parsed(output)
    require(canonical(output_root.find(NS + "configuration")) == canonical(handoff_root.find(NS + "configuration")),
            "Verification configuration changed")
    for key, original in transferred.items():
        actual = output_components[key]
        actual_artifacts = {node.attrib["name"]: node for node in actual}
        for artifact in original:
            require(canonical(actual_artifacts[artifact.attrib["name"]]) == canonical(artifact),
                    "Incumbent or SDK artifact was mutated")
    bom = publication["existing_serialization_bom"]
    require(bom["publisher_sha1_matched"] and bom["sha256"] == declaration["existing_serialization_bom_pom_sha256"],
            "Observed incumbent serialization BOM was not independently checked")
    require(
        checksums(output_components[("org.jetbrains.kotlinx", "kotlinx-serialization-bom", "1.7.3")])[
            "kotlinx-serialization-bom-1.7.3.pom"
        ] == {bom["sha256"]}, "Existing serialization BOM changed",
    )
    require(set(output_components) - set(transferred) <= ALLOWED, "Unapproved component was added")
    return base, handoff, output, {
        "schema_version": 1, "work_package": "WP-204", "lease": "autonomous-WP-204-dc15f1ba",
        "cli_app_session": "00cbcd6f-1b3b-4e40-8d44-2ce4216eb72e",
        "scope": "coordinator-admitted exact shared checksum amendment; not dependency build, legal/hardware approval or a passing native test",
        "sdk_handoff_sha": HANDOFF, "sdk_handoff_blob": HANDOFF_BLOB, "sdk_handoff_sha256": HANDOFF_SHA256,
        "sdk_handoff_bytes": len(handoff), "transferred_sdk_artifacts_preserved": 6,
        "configuration_preserved": True, "incumbent_components_preserved": True,
        "reviewed_coordinate_count": len(ALLOWED), "already_admitted_artifacts_preserved": preserved,
        "actual_new_sha256_entries": added, "new_sha256_entry_count": len(added),
        "result_path": PATH, "result_bytes": len(output), "result_sha256": hashlib.sha256(output).hexdigest(),
        "independent_publisher_report_sha256": hashlib.sha256((OUT / "publisher-inputs.json").read_bytes()).hexdigest(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    try:
        base, handoff, expected, result = render()
        path = ROOT.joinpath(*PATH.split("/"))
        current = path.read_bytes().replace(b"\r\n", b"\n")
        require(current in (base.replace(b"\r\n", b"\n"), handoff.replace(b"\r\n", b"\n"), expected),
                "Shared XML has unexpected changes; do not overwrite them")
        if args.write:
            path.write_bytes(expected)
            (OUT / "dependency-admission-result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf8")
        require(path.read_bytes().replace(b"\r\n", b"\n") == expected, "Shared XML does not contain the exact admitted amendment")
        print(json.dumps({key: result[key] for key in (
            "scope", "transferred_sdk_artifacts_preserved", "new_sha256_entry_count", "result_sha256", "result_bytes",
        )}, indent=2))
        return 0
    except (OSError, ValueError, KeyError, ET.ParseError, subprocess.SubprocessError) as failure:
        print(f"BLOCKED: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
