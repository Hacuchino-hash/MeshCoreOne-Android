"""AndroidOnly: WP-203 Collector negative assertions; synthetic XML is never native proof."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("wp203_collector", Path(__file__).with_name("collect_evidence.py"))
COLLECTOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COLLECTOR)
IDENTITY = (COLLECTOR.PACKAGE + "BackupFixture", "actualAssertion")


class CollectorTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wp203-collector-")
        self.root = Path(self.temporary.name)
        self.patches = (patch.object(COLLECTOR, "ROOT", self.root), patch.object(COLLECTOR, "REPORTS", self.root))
        for value in self.patches:
            value.start()

    def tearDown(self):
        for value in reversed(self.patches):
            value.stop()
        self.temporary.cleanup()

    def write(self, *, counts=None, body=None, filename="TEST-fixture.xml", encoding="utf8"):
        counts = counts or {"tests": 1, "failures": 0, "errors": 0, "skipped": 0}
        body = body if body is not None else f'<testcase classname="{IDENTITY[0]}" name="{IDENTITY[1]}"/>'
        attributes = " ".join(f'{key}="{value}"' for key, value in counts.items())
        raw = (f"<testsuite {attributes}>{body}</testsuite>").encode(encoding)
        (self.root / filename).write_bytes(raw)
        return raw

    def test_missing_reports_fail(self):
        with self.assertRaisesRegex(ValueError, "Missing actual"):
            COLLECTOR.suites({IDENTITY})

    def test_zero_cases_fail(self):
        self.write(counts={"tests": 0, "failures": 0, "errors": 0, "skipped": 0}, body="")
        with self.assertRaisesRegex(ValueError, "zero-test"):
            COLLECTOR.suites({IDENTITY})

    def test_all_actual_outcomes_and_claimed_counts_are_checked(self):
        for tag in ("failure", "error", "skipped"):
            with self.subTest(tag=tag):
                self.write(body=f'<testcase classname="{IDENTITY[0]}" name="{IDENTITY[1]}"><{tag}/></testcase>')
                with self.assertRaisesRegex(ValueError, "failed/skipped"):
                    COLLECTOR.suites({IDENTITY})
        self.write(counts={"tests": 2, "failures": 0, "errors": 0, "skipped": 0})
        with self.assertRaisesRegex(ValueError, "mismatched"):
            COLLECTOR.suites({IDENTITY})

    def test_duplicate_reports_fail(self):
        self.write(filename="TEST-one.xml")
        self.write(filename="TEST-two.xml")
        with self.assertRaisesRegex(ValueError, "duplicate"):
            COLLECTOR.suites({IDENTITY})

    def test_missing_or_stale_owned_identity_fails(self):
        self.write()
        with self.assertRaisesRegex(ValueError, "Unexpected/stale"):
            COLLECTOR.suites({(IDENTITY[0], "renamedAssertion")})
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            COLLECTOR.suites({IDENTITY, (IDENTITY[0], "requiredNegativeAssertion")})

    def test_xml_declarations_are_rejected_before_parser(self):
        for encoding in ("utf8", "utf-16", "utf-16-le", "utf-16-be", "utf-32", "utf-32-le", "utf-32-be"):
            with self.subTest(encoding=encoding):
                path = self.root / "TEST-fixture.xml"
                path.write_bytes(('<!DOCTYPE testsuite [<!ENTITY data "unsafe">]><testsuite/>').encode(encoding))
                with patch.object(COLLECTOR.ET, "fromstring") as parser:
                    with self.assertRaisesRegex(ValueError, "Unsafe"):
                        COLLECTOR.suites({IDENTITY})
                    parser.assert_not_called()

    def test_oversize_is_rejected_before_read_or_parse(self):
        self.write()
        with patch.object(COLLECTOR, "MAX_XML", 4), patch.object(Path, "open") as opened, patch.object(COLLECTOR.ET, "fromstring") as parser:
            with self.assertRaisesRegex(ValueError, "oversized"):
                COLLECTOR.suites({IDENTITY})
            opened.assert_not_called()
            parser.assert_not_called()

    def test_non_backup_module_failure_cannot_be_ignored(self):
        other = ("com.meshcoreone.android.core.data.repository.OtherFixture", "ordinaryRepository")
        self.write()
        self.write(filename="TEST-repository.xml", body=f'<testcase classname="{other[0]}" name="{other[1]}"><failure/></testcase>')
        with self.assertRaisesRegex(ValueError, "failed/skipped"):
            COLLECTOR.suites({IDENTITY})

    def test_raw_digest_case_and_whole_module_count_are_retained(self):
        raw = self.write()
        cases, reports, total = COLLECTOR.suites({IDENTITY})
        self.assertEqual(1, total)
        self.assertEqual({IDENTITY}, set(cases))
        self.assertEqual("passed", cases[IDENTITY]["outcome"])
        self.assertEqual(COLLECTOR.digest(raw), reports[0]["sha256"])
        self.assertEqual(len(raw), reports[0]["size"])


if __name__ == "__main__":
    unittest.main()
