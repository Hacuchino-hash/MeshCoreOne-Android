# AndroidOnly: WP-301 Reader regressions reject missing/zero/failed/skipped/malformed evidence.
import importlib.util
from pathlib import Path
import tempfile
import unittest

path = Path(__file__).with_name("collect_evidence.py")
spec = importlib.util.spec_from_file_location("wp301_evidence_reader", path)
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)


class EvidenceReaderTest(unittest.TestCase):
    def rejected(self, xml):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            (directory / "TEST-native.xml").write_text(xml, encoding="utf8")
            with self.assertRaises((reader.PortError, ValueError)):
                reader.junit_evidence(directory, {"native.Example#example"}, {}, {})

    def test_missing_report_is_blocked(self):
        with tempfile.TemporaryDirectory() as name:
            with self.assertRaises(reader.PortError):
                reader.junit_evidence(Path(name), {"native.Example#example"}, {}, {})

    def test_zero_tests_are_blocked(self):
        self.rejected('<testsuite tests="0" failures="0" errors="0" skipped="0"/>')

    def test_malformed_report_is_blocked(self):
        self.rejected("<testsuite")

    def test_claimed_counts_must_match_actual_case_nodes(self):
        self.rejected('<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase classname="native.Example" name="example"/></testsuite>')

    def test_failed_case_is_not_success(self):
        self.rejected('<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="native.Example" name="example"><failure/></testcase></testsuite>')

    def test_skipped_case_is_not_success(self):
        self.rejected('<testsuite tests="1" failures="0" errors="0" skipped="1"><testcase classname="native.Example" name="example"><skipped/></testcase></testsuite>')

    def test_missing_real_native_render_bytes_is_blocked(self):
        self.rejected('<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="native.Example" name="example"/></testsuite>')

    def test_current_inventory_includes_all_originals_and_source_parameter_families(self):
        originals, mapped, production, methods, inputs, parameters = reader.static_inventory()
        self.assertEqual(58, len(originals))
        self.assertEqual(55, len(mapped))
        self.assertEqual(3, len(reader.EXCLUSIONS))
        self.assertEqual(2424, parameters["IdentityGamutTests::every identity color clears AA against its surfaces in both appearances and contrasts()"])
        self.assertEqual(15466, parameters["ThemeContrastTests::identity colors clear AA against their surfaces for every theme and appearance()"])
        self.assertTrue(len(methods) >= 80)
        self.assertTrue(len(inputs) > 40)
