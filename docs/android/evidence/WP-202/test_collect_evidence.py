"""AndroidOnly: WP-202 Collector self-tests; synthetic XML is not native persistence evidence."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("wp202_collector", Path(__file__).with_name("collect_evidence.py"))
COLLECTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COLLECTOR)


class CollectorTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wp202-collector-")
        self.directory = Path(self.temporary.name)
        self.report_override = patch.object(COLLECTOR, "REPORTS", self.directory)
        self.root_override = patch.object(COLLECTOR, "ROOT", self.directory)
        self.report_override.start()
        self.root_override.start()

    def tearDown(self):
        self.root_override.stop()
        self.report_override.stop()
        self.temporary.cleanup()

    def write(self, content, name="TEST-collector.xml"):
        (self.directory / name).write_text(content, encoding="utf8")

    def suite(self, *, tests=1, failures=0, errors=0, skipped=0, body=None):
        case = body if body is not None else (
            '<testcase classname="com.meshcoreone.android.core.data.repository.CollectorFixture" name="fixture"/>')
        return f'<testsuite tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}">{case}</testsuite>'

    def test_missing_actual_reports_fail(self):
        with self.assertRaisesRegex(ValueError, "Missing actual native"):
            COLLECTOR.raw_suites()

    def test_zero_test_reports_fail(self):
        self.write(self.suite(tests=0, body=""))
        with self.assertRaisesRegex(ValueError, "zero native suite"):
            COLLECTOR.raw_suites()

    def test_declared_counter_mismatch_fails(self):
        self.write(self.suite(tests=2))
        with self.assertRaisesRegex(ValueError, "inconsistent native suite"):
            COLLECTOR.raw_suites()

    def test_failed_or_skipped_suites_fail(self):
        for key in ("failures", "errors", "skipped"):
            with self.subTest(key=key):
                self.write(self.suite(**{key: 1}))
                with self.assertRaisesRegex(ValueError, "Failed/skipped"):
                    COLLECTOR.raw_suites()

    def test_actual_failure_cannot_hide_behind_zero_failure_counter(self):
        self.write(self.suite(body=(
            '<testcase classname="com.meshcoreone.android.core.data.repository.CollectorFixture" name="fixture">'
            '<failure message="synthetic collector regression"/></testcase>')))
        with self.assertRaisesRegex(ValueError, "Raw case failed/skipped"):
            COLLECTOR.raw_suites()

    def test_duplicate_native_identity_fails(self):
        self.write(self.suite(), "TEST-one.xml")
        self.write(self.suite(), "TEST-two.xml")
        with self.assertRaisesRegex(ValueError, "duplicate/foreign"):
            COLLECTOR.raw_suites()

    def test_foreign_suite_is_not_wp202_assertions(self):
        self.write(self.suite(body='<testcase classname="foreign.LegacyProxy" name="fixture"/>'))
        with self.assertRaisesRegex(ValueError, "duplicate/foreign"):
            COLLECTOR.raw_suites()

    def test_unsafe_xml_fails(self):
        self.write('<!DOCTYPE testsuite [<!ENTITY x "fixture">]>' + self.suite())
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            COLLECTOR.raw_suites()

    def test_utf16_declarations_are_rejected_before_any_xml_parser_call(self):
        self.reject_encoded_declarations(("utf-16", "utf-16-le", "utf-16-be"))

    def test_utf32_declarations_are_rejected_before_any_xml_parser_call(self):
        self.reject_encoded_declarations(("utf-32", "utf-32-le", "utf-32-be"))

    def reject_encoded_declarations(self, encodings):
        for encoding in encodings:
            for declaration in ('<!DOCTYPE testsuite [<!ENTITY x "fixture">]>', '<!ENTITY x "fixture">'):
                with self.subTest(encoding=encoding, declaration=declaration):
                    path = self.directory / "TEST-collector.xml"
                    path.write_bytes((declaration + self.suite()).encode(encoding))
                    with patch.object(COLLECTOR.ET, "fromstring") as parse:
                        with self.assertRaisesRegex(ValueError, "Unsafe"):
                            COLLECTOR.raw_suites()
                        parse.assert_not_called()

    def test_oversized_xml_is_rejected_before_reading_or_parsing(self):
        self.write(self.suite())
        with patch.object(COLLECTOR, "MAX_JUNIT_BYTES", 8), \
                patch.object(Path, "open") as opened, \
                patch.object(COLLECTOR.ET, "fromstring") as parse:
            with self.assertRaisesRegex(ValueError, "oversized"):
                COLLECTOR.raw_suites()
            opened.assert_not_called()
            parse.assert_not_called()

    def test_valid_xml_retains_exact_case_identity_and_raw_digest(self):
        content = self.suite()
        self.write(content)
        cases, suites = COLLECTOR.raw_suites()
        self.assertEqual(
            [("com.meshcoreone.android.core.data.repository.CollectorFixture", "fixture")], list(cases))
        self.assertEqual("passed", next(iter(cases.values()))["outcome"])
        self.assertEqual(1, suites[0]["cases"])
        self.assertEqual(COLLECTOR.digest(content.encode("utf8")), suites[0]["sha256"])

    def test_filtered_xml_cannot_omit_unmapped_native_regressions(self):
        source = self.directory / "Tests.kt"
        source.write_text(
            "class CollectorFixture : RepositoryTest() {\n"
            " @Test fun fixture() {}\n @Test fun requiredBoundary() {}\n}\n", encoding="utf8")
        self.write(self.suite())
        actual, _ = COLLECTOR.raw_suites()
        with patch.object(COLLECTOR, "TESTS", self.directory):
            with self.assertRaisesRegex(ValueError, "Incomplete or stale"):
                COLLECTOR.require_complete_native_cases(actual)

    def test_stale_xml_identity_not_declared_in_source_fails(self):
        (self.directory / "Tests.kt").write_text(
            "class CollectorFixture : RepositoryTest() {\n @Test fun renamed() {}\n}\n", encoding="utf8")
        self.write(self.suite())
        actual, _ = COLLECTOR.raw_suites()
        with patch.object(COLLECTOR, "TESTS", self.directory):
            with self.assertRaisesRegex(ValueError, "unexpected="):
                COLLECTOR.require_complete_native_cases(actual)

    def test_complete_raw_suite_matches_every_declared_test(self):
        (self.directory / "Tests.kt").write_text(
            "class CollectorFixture : RepositoryTest() {\n @Test fun fixture() {}\n}\n", encoding="utf8")
        self.write(self.suite())
        actual, _ = COLLECTOR.raw_suites()
        with patch.object(COLLECTOR, "TESTS", self.directory):
            self.assertEqual(1, COLLECTOR.require_complete_native_cases(actual))


if __name__ == "__main__":
    unittest.main()
