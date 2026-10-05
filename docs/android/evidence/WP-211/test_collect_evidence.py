"""AndroidOnly: WP-211 Reader regressions; synthetic XML never counts as native service evidence."""

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import collect_evidence as reader


class JUnitReaderTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wp211-reader-")
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def report(self, body, counters='tests="1" failures="0" errors="0" skipped="0"'):
        raw = f'<testsuite {counters}>{body}</testsuite>'.encode()
        (self.root / "TEST-fixture.xml").write_bytes(raw)
        return raw

    def case(self, name="case", children=""):
        return f'<testcase classname="com.meshcoreone.android.core.services.device.Fixture" name="{name}">{children}</testcase>'

    def test_positive_complete_xml_is_counted_by_actual_nodes(self):
        self.report(self.case())
        self.assertEqual(1, len(reader.junit(self.root, "com.meshcoreone.android.core.services.")))

    def test_missing_directory_fails(self):
        with self.assertRaisesRegex(ValueError, "Missing complete"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_zero_suite_fails(self):
        self.report("", 'tests="0" failures="0" errors="0" skipped="0"')
        with self.assertRaisesRegex(ValueError, "Malformed/zero"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_counter_mismatch_fails(self):
        self.report(self.case(), 'tests="2" failures="0" errors="0" skipped="0"')
        with self.assertRaisesRegex(ValueError, "counter/testcase"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_failed_error_and_skipped_counters_fail(self):
        for field in ("failures", "errors", "skipped"):
            with self.subTest(field=field):
                values = {"tests": 1, "failures": 0, "errors": 0, "skipped": 0, field: 1}
                counters = " ".join(f'{key}="{value}"' for key, value in values.items())
                self.report(self.case(), counters)
                with self.assertRaisesRegex(ValueError, "Failed/error/skipped"):
                    reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_hidden_failure_error_and_skip_nodes_fail(self):
        for element in ("failure", "error", "skipped"):
            with self.subTest(element=element):
                self.report(self.case(children=f"<{element}/>"))
                with self.assertRaisesRegex(ValueError, "Hidden unsuccessful"):
                    reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_duplicate_names_in_the_same_class_fail(self):
        self.report(self.case() + self.case(), 'tests="2" failures="0" errors="0" skipped="0"')
        with self.assertRaisesRegex(ValueError, "duplicate"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_foreign_class_and_missing_case_name_fail(self):
        for body in (
            '<testcase classname="another.module.Test" name="case"/>',
            '<testcase classname="com.meshcoreone.android.core.services.device.Fixture"/>',
        ):
            with self.subTest(body=body):
                self.report(body)
                with self.assertRaisesRegex(ValueError, "Foreign/duplicate/missing"):
                    reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_xml_entities_and_doctype_fail_before_parsing(self):
        raw = b'<!DOCTYPE testsuite [<!ENTITY x "injected">]><testsuite/>'
        (self.root / "TEST-fixture.xml").write_bytes(raw)
        with self.assertRaisesRegex(ValueError, "Unsafe JUnit"):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_malformed_xml_is_not_a_success(self):
        (self.root / "TEST-fixture.xml").write_bytes(b"<testsuite")
        with self.assertRaises(reader.ET.ParseError):
            reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_oversized_reports_fail_without_lowering_the_bound(self):
        self.report(self.case())
        with patch.object(reader, "MAX_XML_BYTES", 1):
            with self.assertRaisesRegex(ValueError, "Unsafe/oversized"):
                reader.junit(self.root, "com.meshcoreone.android.core.services.")

    def test_failed_raw_bytes_and_basic_binding_survive_input_retention_failure(self):
        source = self.root / "repo"
        reports = source / "android" / "core" / "services" / "build" / "test-results" / "test"
        reports.mkdir(parents=True)
        raw = b'<testsuite tests="0" failures="1" errors="0" skipped="0"></testsuite>'
        (reports / "TEST-failed.xml").write_bytes(raw)
        output = self.root / "retained"
        with patch.object(reader, "ROOT", source), patch.object(reader, "OUT", source / "owned"), \
                patch.object(reader, "git", return_value="a" * 40), \
                patch.object(reader, "inputs", side_effect=ValueError("controlled missing compiled input")):
            with self.assertRaisesRegex(ValueError, "controlled missing"):
                reader.retain(output)
        self.assertEqual(raw, (output / "junit" / "services" / "TEST-failed.xml").read_bytes())
        metadata = json.loads((output / "retention.json").read_text())
        self.assertEqual("a" * 40, metadata["head_sha"])
        self.assertEqual(reader.LEASE, metadata["lease"])
        self.assertEqual(1, len(metadata["raw_junit"]))
        self.assertTrue(metadata["missing_directories"])


class IdentityReaderTests(unittest.TestCase):
    def test_dynamic_or_unreadable_source_identity_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Dynamic original"):
            reader.string('"${untrusted}"')
        with self.assertRaises(json.JSONDecodeError):
            reader.string('"unterminated')

    def test_actual_invocation_requires_linux_verify_and_current_immutable_bindings(self):
        with tempfile.TemporaryDirectory(prefix="wp211-invocation-") as directory:
            path = Path(directory) / "invocation.json"
            binding = {"repository": "cbattlegear/MeshCoreOne-Android", "head_sha": "a" * 40,
                       "base_sha": "b" * 40, "source_sha": reader.SOURCE,
                       "manifest_sha256": reader.MANIFEST, "policy_revision": reader.POLICY}
            value = {"schema_version": 1, "host": "linux", "stage": "verify",
                     "identity": {"binding": binding, "run_id": 1, "run_attempt": 1}}
            path.write_text(json.dumps(value))
            with patch.object(reader, "git", return_value="a" * 40):
                self.assertEqual(1, reader.invocation(path)["run_id"])
                for field, replacement in (("host", "windows"), ("stage", "assemble")):
                    bad = {**value, field: replacement}
                    path.write_text(json.dumps(bad))
                    with self.assertRaises(ValueError):
                        reader.invocation(path)
                value["identity"]["binding"]["head_sha"] = "c" * 40
                path.write_text(json.dumps(value))
                with self.assertRaisesRegex(ValueError, "Stale/foreign"):
                    reader.invocation(path)


if __name__ == "__main__":
    unittest.main()
