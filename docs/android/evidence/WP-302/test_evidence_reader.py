"""WP-302 reader regressions only: synthetic XML never supplies native acceptance."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("wp302_evidence", Path(__file__).with_name("collect_evidence.py"))
EVIDENCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EVIDENCE)
CLASS = "com.meshcoreone.android.app.navigation.NavigationStateTest"
IDENTITY = (CLASS, "realCase", "jvm")
BINDING = {"nonce": "nonce", "head": "head", "tree": "tree", "inputs_sha256": "digest"}
MARKER = "WP302_EXECUTION|nonce|head|tree|digest|" + CLASS + "|realCase|jvm"


class ReaderTest(unittest.TestCase):
    def reports(self, output="", nodes=None, tests=1, skipped=0):
        nodes = nodes if nodes is not None else '<testcase classname="' + CLASS + '" name="realCase"/>'
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        (root / "TEST-navigation.xml").write_text(
            f'<testsuite name="navigation" tests="{tests}" failures="0" errors="0" skipped="{skipped}">'
            + nodes + "<system-out>" + output + "</system-out></testsuite>", encoding="utf8",
        )
        return root

    def rejects(self, directory):
        with self.assertRaises(EVIDENCE.PortError):
            EVIDENCE.native_reports(directory, BINDING, {IDENTITY})

    def test_actual_static_inventory_maps_all_fifty_but_is_not_execution(self):
        _, source, originals, mapped, expected = EVIDENCE.inventory()
        self.assertEqual(10, len(source)); self.assertEqual(50, len(originals)); self.assertEqual(50, len(mapped))
        self.assertEqual(140, len(expected))

    def test_missing_directory_fails(self):
        self.rejects(Path("nonexistent-wp302-test-reports"))

    def test_zero_tests_fails(self):
        self.rejects(self.reports(nodes="", tests=0))

    def test_malformed_xml_fails(self):
        directory = self.reports()
        (directory / "TEST-navigation.xml").write_text("<testsuite", encoding="utf8")
        self.rejects(directory)

    def test_declared_count_disagrees_with_actual_nodes(self):
        self.rejects(self.reports(tests=2))

    def test_skipped_mandatory_node_fails(self):
        self.rejects(self.reports(nodes=f'<testcase classname="{CLASS}" name="realCase"><skipped/></testcase>', skipped=1))

    def test_missing_execution_binding_fails(self):
        self.rejects(self.reports())

    def test_stale_nonce_fails(self):
        self.rejects(self.reports(MARKER.replace("|nonce|", "|stale|")))

    def test_duplicate_execution_fails(self):
        self.rejects(self.reports(MARKER + "\n" + MARKER))

    def test_missing_native_screens_fails_even_with_valid_bound_node(self):
        self.rejects(self.reports(MARKER))

    def test_wrong_or_unknown_sdk_fails(self):
        self.rejects(self.reports(MARKER.removesuffix("jvm") + "30"))

    def test_malformed_render_bytes_fail(self):
        self.rejects(self.reports(MARKER + "\nWP302_RENDER|sdk-31-bad|10|10|bad|AAAA"))

    def test_missing_binding_fields_are_not_success_shaped(self):
        with self.assertRaises(KeyError):
            EVIDENCE.verify_binding({})

    def test_changed_compiled_input_binding_fails(self):
        value = dict(BINDING, schema_version=1, wp="WP-302", source_sha=EVIDENCE.PIN, inputs={})
        with patch.object(EVIDENCE, "git", side_effect=[b"head\n", b"tree\n"]), \
                patch.object(EVIDENCE, "current_inputs", return_value={"changed.kt": {"size": 1, "sha256": "changed"}}):
            with self.assertRaises(EVIDENCE.PortError):
                EVIDENCE.verify_binding(value)

    def test_method_name_normalization_preserves_original_family_identity(self):
        for name in ("case", "case()", "case[31]", "case()[31]", "case[31]()"):
            self.assertEqual("case", EVIDENCE.method_name(name))


if __name__ == "__main__":
    unittest.main()
