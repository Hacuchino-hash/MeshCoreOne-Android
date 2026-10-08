"""WP-302 reader regressions only: synthetic XML never supplies native acceptance."""
import importlib.util
import base64
import struct
import zlib
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
    @staticmethod
    def png(width=1, height=1, pixels=b"\0\xff\xff\xff\xff"):
        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
        header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
        return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b"")

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

    def test_shared_png_decoder_accepts_valid_pixels_without_granting_native_execution(self):
        self.assertEqual((1, 1), EVIDENCE.native_png_shape(self.png()))

    def test_png_header_alone_crc_truncation_and_wrong_pixels_fail_at_the_decoder(self):
        png = self.png()
        changed_crc = bytearray(png)
        changed_crc[29] ^= 1
        malformed = (
            b"\x89PNG\r\n\x1a\n" + b"\0" * 8 + struct.pack(">II", 1, 1),
            png[:-1], bytes(changed_crc), self.png(2, 2),
            self.png(pixels=b"\x05\xff\xff\xff\xff"), self.png(pixels=b"\0" * 50),
        )
        for data in malformed:
            with self.subTest(bytes=len(data)):
                with self.assertRaisesRegex(EVIDENCE.PortError, "invalid native PNG"):
                    EVIDENCE.native_png_shape(data)

    def test_malformed_png_with_a_matching_hash_fails_before_screen_inventory(self):
        data = self.png()[:-1]
        render = "\nWP302_RENDER|sdk-31-bad|1|1|" + EVIDENCE.digest(data) + "|" + base64.b64encode(data).decode()
        with self.assertRaisesRegex(EVIDENCE.PortError, "invalid native PNG"):
            EVIDENCE.native_reports(self.reports(MARKER + render), BINDING, {IDENTITY})

    def test_valid_pixels_do_not_replace_the_missing_native_screen_matrix(self):
        data = self.png()
        render = "\nWP302_RENDER|sdk-31-width-360|1|1|" + EVIDENCE.digest(data) + "|" + base64.b64encode(data).decode()
        with self.assertRaisesRegex(EVIDENCE.PortError, "missing/unexpected.*screen states"):
            EVIDENCE.native_reports(self.reports(MARKER + render), BINDING, {IDENTITY})

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
