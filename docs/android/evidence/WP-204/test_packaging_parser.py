"""WP-204 exact SDK37.0.0 dump-shape regression, not synthetic APK or device acceptance."""

import unittest
from inspect_packaging import backup_configuration

# Shape transcribed from actual PR20 run37216036326 APK, dumped with the pinned37.0.0 AAPT2.
MANIFEST = """      E: application (line=18)
        A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=false
        A: http://schemas.android.com/apk/res/android:dataExtractionRules(0x0101063e)=@0x7f0d0001
"""
RESOURCES = """    resource 0x7f0d0001 xml/scaffold_data_extraction_rules
      () (file) res/xml/scaffold_data_extraction_rules.xml type=XML
"""
DOMAINS = (
    "root", "file", "database", "sharedpref", "external",
    "device_root", "device_file", "device_database", "device_sharedpref",
)


def exclusions():
    return "E: data-extraction-rules (line=3)\n" + "".join(
        "    E: " + section + " (line=4)\n" + "".join(
            '        E: exclude (line=5)\n          A: domain="' + domain + '" (Raw: "' + domain + '")\n'
            '          A: path="." (Raw: ".")\n' for domain in DOMAINS
        ) for section in ("cloud-backup", "device-transfer")
    )


class PackagingParserTest(unittest.TestCase):
    def test_actual_sdk_uri_namespace_boolean_and_unqualified_resource_shape(self):
        reference, rules = backup_configuration(MANIFEST, RESOURCES, exclusions())
        self.assertEqual("@0x7f0d0001", reference)
        self.assertEqual({"cloud-backup", "device-transfer"}, set(rules))
        self.assertEqual(9, len(rules["cloud-backup"]))

    def test_enabled_or_missing_backup_is_not_accepted(self):
        for manifest in (MANIFEST.replace("=false", "=true"), MANIFEST.replace("allowBackup", "otherAttribute")):
            with self.assertRaises(ValueError):
                backup_configuration(manifest, RESOURCES, exclusions())

    def test_duplicate_attribute_is_not_a_false_success(self):
        with self.assertRaises(ValueError):
            backup_configuration(MANIFEST + MANIFEST, RESOURCES, exclusions())

    def test_reference_must_match_actual_resource_id(self):
        with self.assertRaises(ValueError):
            backup_configuration(MANIFEST, RESOURCES.replace("0x7f0d0001", "0x7f0d0002"), exclusions())

    def test_missing_or_duplicate_resource_entry_fails(self):
        for resources in ("", RESOURCES + RESOURCES):
            with self.assertRaises(ValueError):
                backup_configuration(MANIFEST, resources, exclusions())

    def test_every_cloud_and_transfer_storage_domain_is_required(self):
        for domain in DOMAINS:
            with self.assertRaises(ValueError):
                backup_configuration(MANIFEST, RESOURCES, exclusions().replace('domain="' + domain + '"', 'domain="unknown"'))

    def test_root_path_and_no_include_are_required(self):
        for rules in (exclusions().replace('path="."', 'path="partial"'), exclusions().replace("E: exclude", "E: include")):
            with self.assertRaises(ValueError):
                backup_configuration(MANIFEST, RESOURCES, rules)

    def test_missing_device_transfer_section_fails(self):
        with self.assertRaises(ValueError):
            backup_configuration(MANIFEST, RESOURCES, exclusions().replace("E: device-transfer", "E: unrecognized-transfer"))


if __name__ == "__main__":
    unittest.main()
