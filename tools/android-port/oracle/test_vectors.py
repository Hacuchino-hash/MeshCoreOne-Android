"""AndroidOnly: WP-004 Positive and negative independent-vector assertions."""

import copy
import unittest
from unittest.mock import patch

from extract_vectors import (
    EXTRAS, PYTHON_PATH, data_array, generate, tsv_bytes, validate_vectors,
)
from oracle.reference import FrozenReference, OracleError, SOURCE_SHA, sha256
from oracle.swift import Syntax, declarations


class LiteralVectorTests(unittest.TestCase):
    def test_high_bits_decimal_hex_binary_and_octal_are_exact_bytes(self):
        syntax = Syntax("Data([0x80, 255, 0b10101010, 0o377, 0x00])")
        self.assertEqual(data_array(syntax, 0, len(syntax.tokens)), [128, 255, 170, 255, 0])

    def test_comments_and_raw_strings_cannot_add_bytes(self):
        syntax = Syntax("Data([0x01, /* 0xEE, nested /* 0xDD */ */ 0xFF, // 0x80\n0x00])")
        self.assertEqual(data_array(syntax, 0, len(syntax.tokens)), [1, 255, 0])
        with self.assertRaises(OracleError):
            syntax = Syntax('Data([#"0xFF"#])')
            data_array(syntax, 0, len(syntax.tokens))

    def test_invalid_out_of_range_and_computed_byte_inputs_fail(self):
        for expression in ("Data([256])", "Data([-1])", "Data([1.5])", "Data([0xGG])",
                           "Data([true])", "Data([1 + 2])", "Data(repeating: 0, count: 4)",
                           "Data([1]) + Data([2])"):
            with self.subTest(expression=expression), self.assertRaises(OracleError):
                syntax = Syntax(expression)
                data_array(syntax, 0, len(syntax.tokens))

    def test_empty_literal_is_explicit_not_a_silent_invalid_input_default(self):
        syntax = Syntax("Data([])")
        self.assertEqual(data_array(syntax, 0, len(syntax.tokens)), [])
        with self.assertRaises(OracleError):
            syntax = Syntax("Data([,])")
            data_array(syntax, 0, len(syntax.tokens))


class IndependentVectorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.reference = FrozenReference()
        cls.value = generate(cls.reference)
        cls.vectors = {vector["id"]: vector for vector in cls.value["vectors"]}

    def test_every_pinned_python_field_is_copied_once(self):
        source = self.reference.read_many([PYTHON_PATH])[PYTHON_PATH]
        syntax = Syntax(source)
        names = [member.name for member in declarations(syntax)
                 if member.kind == "let" and member.scope == ("PythonReferenceBytes",)]
        self.assertGreater(len(names), 0)
        self.assertEqual({"python." + name for name in names},
                         {identity for identity in self.vectors if identity.startswith("python.")})
        self.assertEqual(len(self.vectors), len(names) + len(EXTRAS))

    def test_known_independent_little_and_big_endian_high_bit_values(self):
        self.assertEqual(self.vectors["python.setTime_1704067200"]["hex"], "0680009265")
        self.assertEqual(self.vectors["swift.uint32-little-endian"]["hex"], "78563412")
        self.assertEqual(self.vectors["swift.int32-minus-one"]["hex"], "ffffffff")
        self.assertEqual(self.vectors["swift.lpp-high-bit-uint32"]["hex"], "016480000000")
        self.assertEqual(self.vectors["swift.lpp-negative-int24"]["hex"], "077afffa24")

    def test_truncated_crypto_and_parser_inputs_retain_source_assertions(self):
        for identity, expected in (
            ("swift.channel-crypto-truncated", "00010203"),
            ("swift.direct-crypto-truncated", "00010203"),
            ("swift.raw-data-truncated", "28ab"),
            ("swift.region-response-truncated", "0102"),
        ):
            vector = self.vectors[identity]
            self.assertEqual(vector["hex"], expected)
            self.assertEqual(vector["role"], "invalid-input-with-source-assertions")
            self.assertTrue(vector["provenance"]["case"]["assertions"])
            self.assertIn("@Test", vector["provenance"]["case"]["declaration"])

    def test_fragmented_frame_input_and_expected_body_are_distinct(self):
        self.assertEqual(self.vectors["swift.wifi-incomplete-header"]["hex"], "3e0500")
        self.assertEqual(self.vectors["swift.wifi-incomplete-body"]["hex"], "0102")
        self.assertEqual(self.vectors["swift.wifi-final-body"]["hex"], "030405")
        self.assertEqual(self.vectors["swift.wifi-expected-body"]["hex"], "0102030405")
        self.assertEqual(self.vectors["swift.wifi-expected-body"]["role"], "expected-bytes")

    def test_full_blob_field_line_and_exact_source_byte_provenance(self):
        for vector in self.value["vectors"]:
            provenance = vector["provenance"]
            source = self.reference.read_many([provenance["path"]])[provenance["path"]]
            self.assertEqual(provenance["source_sha"], SOURCE_SHA)
            self.assertEqual(provenance["blob_sha"], self.reference.blobs[provenance["path"]])
            self.assertEqual(provenance["utf8_bytes"], len(source.encode("utf-8")))
            self.assertEqual(vector["bytes_sha256"], sha256(bytes(vector["bytes"])))
            self.assertGreaterEqual(provenance["line"], 1)

    def test_missing_zero_duplicate_schema_and_stale_source_fail(self):
        for kind in ("empty", "duplicate", "schema", "source", "blob"):
            value = copy.deepcopy(self.value)
            if kind == "empty":
                value["vectors"] = []
            elif kind == "duplicate":
                value["vectors"].append(copy.deepcopy(value["vectors"][0]))
            elif kind == "schema":
                value["vectors"][0]["new"] = 1
            elif kind == "source":
                value["source_sha"] = "f" * 40
            else:
                value["vectors"][0]["provenance"]["blob_sha"] = "f" * 40
            with self.subTest(kind=kind), self.assertRaises(OracleError):
                validate_vectors(value, self.reference)

    def test_mutated_bytes_counts_hex_and_digest_fail(self):
        for field, replacement in (("bytes", [False]), ("byte_count", 0), ("hex", "FF"),
                                   ("bytes_sha256", "f" * 64)):
            value = copy.deepcopy(self.value)
            value["vectors"][0][field] = replacement
            with self.subTest(field=field), self.assertRaises(OracleError):
                validate_vectors(value)

    def test_forged_expected_packets_cannot_disagree_with_copied_literal(self):
        value = copy.deepcopy(self.value)
        item = value["vectors"][0]
        item["bytes"] = [0xFF]
        item["hex"], item["byte_count"], item["bytes_sha256"] = "ff", 1, sha256(b"\xff")
        with self.assertRaisesRegex(OracleError, "copied independent literal"):
            validate_vectors(value)

    def test_generating_expected_vectors_does_not_invoke_candidate_processes(self):
        with patch("subprocess.run", side_effect=AssertionError("unexpected process")):
            validate_vectors(self.value)
            data = tsv_bytes(self.value)
        self.assertIn(b"pinned-reference-snapshot", data)
        self.assertEqual(len(data.decode("utf-8").splitlines()), len(self.value["vectors"]) + 2)

    def test_tsv_serialization_is_deterministic_and_contains_all_vectors(self):
        self.assertEqual(tsv_bytes(self.value), tsv_bytes(generate(self.reference)))
        for vector in self.value["vectors"]:
            self.assertIn((vector["id"] + "\t").encode(), tsv_bytes(self.value))
