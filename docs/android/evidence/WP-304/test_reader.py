# AndroidOnly: WP-304 Positive/negative reader regressions use synthetic fixtures only, never native pass evidence.
from __future__ import annotations

import base64
import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zlib

import collect_evidence as reader
import retain_raw
from source_inventory import EvidenceError


def png(width=1, height=1):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(b"\0" + b"\xff" * 4)) + chunk(b"IEND", b"")


class ReaderTests(unittest.TestCase):
    def test_valid_png_has_verified_shape(self):
        self.assertEqual((1, 1), reader.png_shape(png()))

    def test_truncated_png_rejected(self):
        with self.assertRaises(EvidenceError):
            reader.png_shape(png()[:-1])

    def test_changed_crc_rejected(self):
        raw = bytearray(png())
        raw[29] ^= 1
        with self.assertRaises(EvidenceError):
            reader.png_shape(bytes(raw))

    def test_excessive_png_dimensions_rejected(self):
        with self.assertRaises(EvidenceError):
            reader.png_shape(png(5000, 1))

    def test_non_png_rejected(self):
        with self.assertRaises(EvidenceError):
            reader.png_shape(b"not an image")

    def test_complete_crc_structure_with_wrong_pixel_count_is_rejected(self):
        with self.assertRaises(EvidenceError):
            reader.png_shape(png(2, 2))

    def test_empty_junit_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "TEST-empty.xml"
            path.write_bytes(b"")
            with self.assertRaises(EvidenceError):
                reader.read_xml(path)

    def test_junit_entity_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "TEST-unsafe.xml"
            path.write_bytes(b'<!DOCTYPE x [<!ENTITY y SYSTEM "file:///not-used">]><testsuite/>')
            with self.assertRaises(EvidenceError):
                reader.read_xml(path)

    def test_zero_discovery_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-zero.xml").write_text('<testsuite tests="0" failures="0" errors="0" skipped="0"/>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaises(EvidenceError):
                    reader.collect(path)

    def test_missing_reports_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaises(EvidenceError):
                    reader.collect(Path(directory))

    def test_counter_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-one.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase classname="C" name="method"/></testsuite>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaises(EvidenceError):
                    reader.collect(path)

    def test_duplicate_methods_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            case = '<testcase classname="C" name="method"/>'
            (path / "TEST-one.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0">' + case * 2 + '</testsuite>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaises(EvidenceError):
                    reader.collect(path)

    def test_failed_raw_xml_is_preserved_before_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            raw = b'<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="C" name="method"><failure>actual failure stack</failure></testcase></testsuite>'
            (source / "TEST-failure.xml").write_bytes(raw)
            with patch.object(retain_raw, "native_inputs", return_value={"input": {"working_blob": "a" * 40}}), patch.object(retain_raw, "git", return_value=b"b" * 40):
                result = retain_raw.retain(source, root / "raw")
            self.assertEqual(raw, (root / "raw" / "junit" / "TEST-failure.xml").read_bytes())
            self.assertEqual("raw-retained-unvalidated", result["result"])
            self.assertEqual(hashlib.sha256(raw).hexdigest(), result["reports"][0]["sha256"])

    def test_missing_junit_writes_blocked_binding_not_a_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(retain_raw, "native_inputs", return_value={}), patch.object(retain_raw, "git", return_value=b"b" * 40):
                with self.assertRaises(EvidenceError):
                    retain_raw.retain(root / "absent", root / "raw")
            result = json.loads((root / "raw" / "raw-retention.json").read_text())
            self.assertEqual("blocked-no-produced-junit", result["result"])
            self.assertEqual([], result["reports"])

    def test_failed_junit_never_validates_as_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-one.xml").write_text('<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="C" name="method"><failure>stack</failure></testcase></testsuite>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaises(EvidenceError):
                    reader.collect(path)

    def test_skipped_junit_never_validates_as_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-one.xml").write_text('<testsuite tests="1" failures="0" errors="0" skipped="1"><testcase classname="C" name="method"><skipped/></testcase></testsuite>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaises(EvidenceError):
                    reader.collect(path)


if __name__ == "__main__":
    unittest.main()
