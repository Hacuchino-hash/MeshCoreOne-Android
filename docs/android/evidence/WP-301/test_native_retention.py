# AndroidOnly: WP-301 Raw retention is independent of successful native-suite validation.
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

path = Path(__file__).with_name("retain_native_junit.py")
spec = importlib.util.spec_from_file_location("wp301_native_retention", path)
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)


class NativeRetentionTest(unittest.TestCase):
    def copy(self, xml, verify):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            repository = root / "repository"
            source = repository / "reports"
            source.mkdir(parents=True)
            raw = xml.encode("utf8")
            (source / "TEST-native.xml").write_bytes(raw)
            destination, reports = reader.copy_reports(source, root / "private", repository)
            self.assertEqual(raw, (destination / "TEST-native.xml").read_bytes())
            self.assertEqual(len(raw), reports[0]["bytes"])
            verify(destination)

    def rejected_after_copy(self, xml):
        def verify(destination):
            with self.assertRaises(reader.reader.PortError):
                reader.reader.suite_counts(destination, 1)
        self.copy(xml, verify)

    def test_passing_raw_counts_are_validated_after_exact_copy(self):
        self.copy('<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="native.Example" name="example"/></testsuite>',
            lambda destination: self.assertEqual(1, reader.reader.suite_counts(destination, 1)["passed"]))

    def test_failure_stack_is_copied_before_blocked_validation(self):
        self.rejected_after_copy('<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="native.Example" name="example"><failure message="actual assertion">actual trace</failure></testcase></testsuite>')

    def test_malformed_xml_is_copied_before_blocked_validation(self):
        self.rejected_after_copy("<testsuite")

    def test_zero_suite_is_copied_but_never_passes(self):
        self.rejected_after_copy('<testsuite tests="0" failures="0" errors="0" skipped="0"/>')

    def test_skipped_suite_is_copied_but_never_passes(self):
        self.rejected_after_copy('<testsuite tests="1" failures="0" errors="0" skipped="1"><testcase classname="native.Example" name="example"><skipped/></testcase></testsuite>')

    def test_false_discovery_is_copied_but_never_passes(self):
        self.rejected_after_copy('<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase classname="native.Example" name="example"/></testsuite>')

    def test_missing_output_or_repository_overlap_is_blocked(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            repository = root / "repository"
            repository.mkdir()
            with self.assertRaises(reader.reader.PortError):
                reader.copy_reports(repository, root / "private", repository)
            (repository / "TEST-native.xml").write_text("raw", encoding="utf8")
            with self.assertRaises(reader.reader.PortError):
                reader.copy_reports(repository, repository / "private", repository)

    def test_existing_destination_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            repository = root / "repository"
            repository.mkdir()
            (repository / "TEST-native.xml").write_text("raw", encoding="utf8")
            private = root / "private"
            private.mkdir()
            with self.assertRaises(FileExistsError):
                reader.copy_reports(repository, private, repository)

    def test_relative_and_zero_byte_reports_are_blocked(self):
        with self.assertRaises(reader.reader.PortError):
            reader.safe_path(Path("relative"))
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            repository = root / "repository"
            repository.mkdir()
            (repository / "TEST-native.xml").write_bytes(b"")
            with self.assertRaises(reader.reader.PortError):
                reader.copy_reports(repository, root / "private", repository)

    def test_failed_suite_record_and_complete_xml_survive_validation_error(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            repository = root / "repository"
            source = repository / "reports"
            source.mkdir(parents=True)
            for relative in ("android/core/designsystem/build.gradle.kts", "android/core/designsystem/gradle.lockfile"):
                target = repository.joinpath(*relative.split("/"))
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text("actual input", encoding="utf8")
            raw = b'<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="native.Example" name="example"><failure>actual trace</failure></testcase></testsuite>'
            (source / "TEST-native.xml").write_bytes(raw)
            private = root / "private"
            with patch.object(reader.reader, "ROOT", repository), patch.object(reader.reader, "git", return_value=b"1" * 40), \
                patch.object(reader.reader, "static_inventory", return_value=({}, {}, {}, {"native.Example#example"}, {}, {})), \
                patch.object(reader.subprocess, "check_output", return_value=b"2" * 40):
                with self.assertRaises(reader.reader.PortError):
                    reader.retain(source, private)
            record = reader.json.loads((private / "native-retention.json").read_text(encoding="utf8"))
            self.assertEqual("blocked-raw-preserved", record["result"])
            self.assertEqual("1" * 40, record["observed_head_sha"])
            self.assertEqual(2, len(record["current_inputs"]))
            self.assertEqual([{"identity": "native.Example#example", "reported_outcome": "failure"}],
                record["observed_case_outcomes"])
            self.assertEqual(raw, (private / "junit" / "TEST-native.xml").read_bytes())
