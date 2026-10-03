"""Android-only WP-002 composite/standalone and native-classifier verification regressions."""

import copy
from pathlib import Path
import tomllib
import unittest
import xml.etree.ElementTree as ET


ANDROID = Path(__file__).resolve().parents[1]
NS = {"v": "https://schema.gradle.org/dependency-verification"}
COORDINATE = {"group": "org.junit", "name": "junit-bom", "version": "5.10.1"}
# Independently fetched Maven Central artifacts and their publisher .sha256 files.
EXPECTED_ARTIFACTS = {
    "junit-bom-5.10.1.pom": "21c4b0286f4b20069577ff4b20978a85c100ac8a46b6f1c8672fbaab337bc3f2",
    "junit-bom-5.10.1.module": "21b0afcfffe2ecb3770f5eb00ae7a19feaee94e771fa3918173850dae78067b7",
}
AAPT2_COORDINATE = {"group": "com.android.tools.build", "name": "aapt2", "version": "9.4.1-15978811"}
EXPECTED_AAPT2_ARTIFACTS = {
    "aapt2-9.4.1-15978811-linux.jar": "f5bebd466ecf14d341fd465f2756a16d86052f29eb4532003d5ff7bcffd08de5",
    "aapt2-9.4.1-15978811-windows.jar": "5fe3c8ee5c6b3f47efd1fced1d6418084037f54f13290816c5db717e6b5c92aa",
    "aapt2-9.4.1-15978811.pom": "8a84707f3dcf60c5ab7fd4638f60dafdf9d0345217e3b35141628f00684e55d0",
}


def checked_publication_artifacts(metadata, coordinate, expected_artifacts, label):
    if metadata.findtext("v:configuration/v:verify-metadata", namespaces=NS) != "true":
        raise ValueError("Artifact metadata verification must stay enabled")
    components = [
        component for component in metadata.findall("v:components/v:component", NS)
        if component.attrib == coordinate
    ]
    if len(components) != 1:
        raise ValueError(f"Pinned {label} component must be present exactly once")
    artifacts = {}
    for artifact in components[0].findall("v:artifact", NS):
        name = artifact.attrib.get("name")
        if name in artifacts:
            raise ValueError(f"Duplicate {label} artifact verification entry")
        artifacts[name] = {checksum.attrib.get("value") for checksum in artifact.findall("v:sha256", NS)}
    for name, expected in expected_artifacts.items():
        if artifacts.get(name) != {expected}:
            raise ValueError(f"Missing or changed publisher-checked {label} checksum: {name}")
    return artifacts


def checked_bom_artifacts(metadata):
    return checked_publication_artifacts(metadata, COORDINATE, EXPECTED_ARTIFACTS, "BOM")


def checked_aapt2_artifacts(metadata):
    return checked_publication_artifacts(metadata, AAPT2_COORDINATE, EXPECTED_AAPT2_ARTIFACTS, "AAPT2")


def checked_included_coverage(root, included):
    def artifact_pins(metadata):
        pins = {}
        for component in metadata.findall("v:components/v:component", NS):
            coordinate = tuple(component.attrib[name] for name in ("group", "name", "version"))
            for artifact in component.findall("v:artifact", NS):
                key = (*coordinate, artifact.attrib["name"])
                if key in pins:
                    raise ValueError(f"Duplicate verification artifact: {key}")
                pins[key] = {checksum.attrib["value"] for checksum in artifact.findall("v:sha256", NS)}
        return pins

    root_pins = artifact_pins(root)
    for key, expected in artifact_pins(included).items():
        if not expected or not expected.issubset(root_pins.get(key, set())):
            raise ValueError(f"Composite root lacks standalone publication checksums: {key}")


class VerificationMetadataTests(unittest.TestCase):
    def metadata(self, included=False):
        base = ANDROID / "build-logic" if included else ANDROID
        return ET.parse(base / "gradle" / "verification-metadata.xml").getroot()

    def test_composite_root_checks_both_publication_formats(self):
        with (ANDROID / "gradle" / "libs.versions.toml").open("rb") as stream:
            catalog = tomllib.load(stream)
        self.assertEqual(COORDINATE["version"], catalog["versions"]["junit5"])
        self.assertEqual(
            {"module": "org.junit:junit-bom", "version": {"ref": "junit5"}},
            catalog["libraries"]["junit5-bom"],
        )
        self.assertEqual(set(EXPECTED_ARTIFACTS), set(checked_bom_artifacts(self.metadata())))

    def test_standalone_included_build_has_identical_pins(self):
        self.assertEqual(
            checked_bom_artifacts(self.metadata()),
            checked_bom_artifacts(self.metadata(included=True)),
        )

    def test_composite_covers_every_standalone_publication_variant(self):
        checked_included_coverage(self.metadata(), self.metadata(included=True))

    def test_missing_classpath_bom_is_rejected_as_a_coverage_gap(self):
        metadata = self.metadata()
        components = metadata.find("v:components", NS)
        components.remove(next(
            item for item in components
            if item.attrib == {"group": "org.jetbrains.kotlinx", "name": "kotlinx-coroutines-bom", "version": "1.8.0"}
        ))
        with self.assertRaisesRegex(ValueError, "kotlinx-coroutines-bom"):
            checked_included_coverage(metadata, self.metadata(included=True))

    def test_missing_pom_is_rejected_even_when_module_is_pinned(self):
        metadata = self.metadata()
        component = next(
            item for item in metadata.findall("v:components/v:component", NS)
            if item.attrib == COORDINATE
        )
        component.remove(next(item for item in component if item.attrib.get("name", "").endswith(".pom")))
        with self.assertRaisesRegex(ValueError, "junit-bom-5.10.1.pom"):
            checked_bom_artifacts(metadata)

    def test_changed_checksum_cannot_be_accepted_as_an_alternative(self):
        metadata = self.metadata()
        checksum = metadata.find(
            "v:components/v:component[@group='org.junit'][@name='junit-bom'][@version='5.10.1']"
            "/v:artifact[@name='junit-bom-5.10.1.pom']/v:sha256",
            NS,
        )
        checksum.set("value", "0" * 64)
        with self.assertRaisesRegex(ValueError, "changed publisher-checked"):
            checked_bom_artifacts(metadata)

    def test_duplicate_component_does_not_hide_missing_evidence(self):
        metadata = self.metadata()
        components = metadata.find("v:components", NS)
        component = next(item for item in components if item.attrib == COORDINATE)
        components.append(copy.deepcopy(component))
        with self.assertRaisesRegex(ValueError, "exactly once"):
            checked_bom_artifacts(metadata)

    def test_disabled_metadata_verification_is_rejected(self):
        metadata = self.metadata()
        metadata.find("v:configuration/v:verify-metadata", NS).text = "false"
        with self.assertRaisesRegex(ValueError, "stay enabled"):
            checked_bom_artifacts(metadata)

    def test_linux_aapt2_pin_preserves_windows_and_pom_checksums(self):
        self.assertEqual(
            {name: {checksum} for name, checksum in EXPECTED_AAPT2_ARTIFACTS.items()},
            checked_aapt2_artifacts(self.metadata()),
        )

    def test_windows_aapt2_pin_does_not_substitute_for_missing_linux_artifact(self):
        metadata = self.metadata()
        component = next(
            item for item in metadata.findall("v:components/v:component", NS)
            if item.attrib == AAPT2_COORDINATE
        )
        component.remove(next(item for item in component if item.attrib.get("name", "").endswith("-linux.jar")))
        with self.assertRaisesRegex(ValueError, "aapt2-9.4.1-15978811-linux.jar"):
            checked_aapt2_artifacts(metadata)

    def test_linux_aapt2_checksum_cannot_be_replaced_with_windows_bytes(self):
        metadata = self.metadata()
        checksum = metadata.find(
            "v:components/v:component[@group='com.android.tools.build'][@name='aapt2'][@version='9.4.1-15978811']"
            "/v:artifact[@name='aapt2-9.4.1-15978811-linux.jar']/v:sha256",
            NS,
        )
        checksum.set("value", EXPECTED_AAPT2_ARTIFACTS["aapt2-9.4.1-15978811-windows.jar"])
        with self.assertRaisesRegex(ValueError, "changed publisher-checked AAPT2"):
            checked_aapt2_artifacts(metadata)


if __name__ == "__main__":
    unittest.main()
