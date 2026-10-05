"""AndroidOnly: WP-207 Positive and fail-closed actual JUnit discovery reader assertions."""

from pathlib import Path
import tempfile
import unittest

from collect_evidence import CASE, read_junit

CLASS = "com.meshcoreone.android.core.runtime.RuntimeTest"
GOOD = f'<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="source::case()" classname="{CLASS}"/></testsuite>'


class RuntimeEvidenceReaderTests(unittest.TestCase):
    def read(self, text):
        with tempfile.TemporaryDirectory() as value:
            directory = Path(value)
            (directory / "TEST-runtime.xml").write_text(text, encoding="utf-8")
            return read_junit(directory, directory)

    def test_positive_real_node_and_hash(self):
        cases, reports = self.read(GOOD)
        self.assertEqual({"source::case()": CLASS}, cases)
        self.assertEqual(1, reports[0]["discovered"])
        self.assertEqual(64, len(reports[0]["sha256"]))

    def test_empty_directory_and_zero_suite_fail(self):
        with tempfile.TemporaryDirectory() as value:
            directory = Path(value)
            with self.assertRaises(ValueError):
                read_junit(directory, directory)
        with self.assertRaises(ValueError):
            self.read('<testsuite tests="0" failures="0" errors="0" skipped="0"/>')

    def test_declared_outcome_failure_skip_error_and_count_mismatch_fail(self):
        for text in (
            GOOD.replace('failures="0"', 'failures="1"'),
            GOOD.replace('errors="0"', 'errors="1"'),
            GOOD.replace('skipped="0"', 'skipped="1"'),
            GOOD.replace('tests="1"', 'tests="2"'),
        ):
            with self.subTest(text=text), self.assertRaises(ValueError):
                self.read(text)

    def test_hidden_failed_error_and_skip_nodes_fail(self):
        for kind in ("failure", "error", "skipped"):
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                self.read(GOOD.replace("/></testsuite>", f"><{kind}/></testcase></testsuite>"))

    def test_missing_and_foreign_case_identities_fail(self):
        for text in (
            GOOD.replace('name="source::case()"', 'name=""'),
            GOOD.replace(CLASS, ""),
            GOOD.replace(CLASS, "foreign.Test"),
        ):
            with self.subTest(text=text), self.assertRaises(ValueError):
                self.read(text)

    def test_duplicate_case_names_across_suites_fail(self):
        with tempfile.TemporaryDirectory() as value:
            directory = Path(value)
            for name in ("a", "b"):
                (directory / f"TEST-{name}.xml").write_text(GOOD, encoding="utf-8")
            with self.assertRaises(ValueError):
                read_junit(directory, directory)

    def test_doctype_and_external_entity_fail(self):
        with self.assertRaises(ValueError):
            self.read('<!DOCTYPE testsuite [<!ENTITY external SYSTEM "file:///private">]>' + GOOD)

    def test_source_parameter_family_signature_is_not_lost(self):
        text = 'original("Suite", "family", "(_ testCase : RetryBudgetCase)") { assertions() }'
        self.assertEqual([("Suite", "family", "(_ testCase : RetryBudgetCase)")], CASE.findall(text))
        self.assertEqual([("Suite", "single", "")], CASE.findall('original("Suite", "single") {}'))


if __name__ == "__main__":
    unittest.main()
