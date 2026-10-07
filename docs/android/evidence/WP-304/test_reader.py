# AndroidOnly: WP-304 Positive/negative reader regressions use synthetic fixtures only, never native pass evidence.
from __future__ import annotations

import base64
import copy
from contextlib import redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
from types import SimpleNamespace
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

def invocation_record():
    return {"schema_version": 1, "stage": "verify", "host": "linux", "identity": {
        "run_id": 12, "run_attempt": 1, "binding": {
            "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-003",
            "base_sha": "a" * 40, "head_sha": "b" * 40,
            "source_sha": retain_raw.PIN, "manifest_sha256": retain_raw.MANIFEST,
            "policy_revision": retain_raw.POLICY,
        }}}


def local_invocation_record():
    return {"schema_version": 1, "stage": "verify", "host": "linux", "identity": None}


def local_git(*arguments):
    values = {
        ("rev-parse", "HEAD"): b"b" * 40,
        ("rev-parse", "HEAD^{tree}"): b"c" * 40,
        ("rev-parse", retain_raw.PIN + "^{tree}"): retain_raw.SOURCE_TREE.encode(),
        ("status", "--porcelain=v1", "--untracked-files=all"): b"",
    }
    return values[arguments]


class ReaderTests(unittest.TestCase):
    def setUp(self):
        binding = patch.object(reader, "source_bindings", return_value={"one": {"method": "C#method", "disposition": "native"}})
        binding.start()
        self.addCleanup(binding.stop)

    def test_only_direct_class_level_junit_methods_are_declared(self):
        text = "package sample\nclass NativeTest {\n@Test fun first() = assertTrue(true)\n@Test fun second() {}\n}"
        self.assertEqual({"sample.NativeTest#first", "sample.NativeTest#second"}, reader.native_test_methods(text))

    def test_nested_test_inside_another_method_or_composable_lambda_is_rejected(self):
        for body in (
            "@Test fun outer() { @Test fun inner() {} }",
            "@Test fun outer() { content { @Test fun inner() {} } }",
        ):
            with self.subTest(body=body), self.assertRaisesRegex(EvidenceError, "direct class-level"):
                reader.native_test_methods("package sample\nclass NativeTest {\n" + body + "\n}")

    def test_comments_raw_strings_characters_and_interpolations_do_not_manufacture_methods(self):
        text = '''package sample
class NativeTest {
    /* outer /* @Test fun fake() {} */ { } */
    val raw = """@Test fun fake() { }"""
    val quoted = "\\\" @Test fun fake() {}"
    val character = '}'
    val interpolated = "${listOf("inner").size}"
    // @Test fun fake() {}
    @Test fun real() {}
}'''
        self.assertEqual({"sample.NativeTest#real"}, reader.native_test_methods(text))

    def test_unbalanced_or_unterminated_native_test_source_fails_instead_of_counting_annotations(self):
        for body in ("@Test fun method() {", 'val text = "unterminated\n@Test fun method() {}',
                "/* unterminated", "val text = \"${run { 1}\""):
            with self.subTest(body=body), self.assertRaises(EvidenceError):
                reader.native_test_methods("package sample\nclass NativeTest {\n" + body)

    def test_orphan_parameterized_and_top_level_native_test_annotations_fail(self):
        for text in (
            "package sample\nclass NativeTest { @Test val value = 1 }",
            "package sample\nclass NativeTest { @Test fun method(value: Int) {} }",
            "package sample\nclass NativeTest {}\n@Test fun outside() {}",
        ):
            with self.subTest(text=text), self.assertRaises(EvidenceError):
                reader.native_test_methods(text)

    def test_pending_source_policy_cannot_be_credited_as_ported_case(self):
        expected = {"source": {"method": "C#method", "disposition": "pending-WP-208"}}
        reader.verify_source_receipt("source", "pending-WP-208", "C#method", "POLICY_CASE", expected, {"C#method"})
        with self.assertRaises(EvidenceError):
            reader.verify_source_receipt("source", "pending-WP-208", "C#method", "CASE", expected, {"C#method"})

    def test_source_receipt_requires_exact_current_method_binding_and_actual_execution(self):
        expected = {"source": {"method": "C#method", "disposition": "native"}}
        reader.verify_source_receipt("source", "native", "C#method", "CASE", expected, {"C#method"})
        for method, observed in (("C#other", {"C#other"}), ("C#method", set())):
            with self.assertRaises(EvidenceError):
                reader.verify_source_receipt("source", "native", method, "CASE", expected, observed)
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
                with self.assertRaisesRegex(EvidenceError, "Missing/ambiguous native suite"):
                    reader.collect(path)

    def test_missing_reports_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaisesRegex(EvidenceError, "No produced native JUnit"):
                    reader.collect(Path(directory))

    def test_counter_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-one.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase classname="C" name="method"/></testsuite>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaisesRegex(EvidenceError, "JUnit counter/outcome mismatch"):
                    reader.collect(path)

    def test_duplicate_methods_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            case = '<testcase classname="C" name="method"/>'
            (path / "TEST-one.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0">' + case * 2 + '</testsuite>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaisesRegex(EvidenceError, "Duplicate/unexpected executed native test"):
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

    def test_actual_produced_png_and_failed_xml_are_copied_to_pipeline_artifact_before_verdict(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, images = root / "source", root / "images"
            source.mkdir(); images.mkdir()
            xml = b'<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="C" name="method"><failure>actual retained failure</failure></testcase></testsuite>'
            image = png()
            (source / "TEST-failure.xml").write_bytes(xml)
            (images / "compact-light.png").write_bytes(image)
            pipeline = root / "pipeline"; pipeline.mkdir()
            invocation = pipeline / "wp109-invocation.json"
            invocation.write_text(json.dumps(invocation_record()))
            console = io.StringIO()
            with patch.object(retain_raw, "native_inputs", return_value={"input": {}}), patch.object(retain_raw, "git", return_value=b"b" * 40), redirect_stdout(console):
                result = retain_raw.retain(source, root / "raw", emit=True, images=images, invocation_path=invocation)
            target = root / "pipeline" / "wp304-native"
            self.assertEqual(xml, (target / "junit" / "TEST-failure.xml").read_bytes())
            self.assertEqual(image, (target / "ui" / "compact-light.png").read_bytes())
            self.assertEqual(hashlib.sha256(image).hexdigest(), result["images"][0]["sha256"])
            self.assertEqual(invocation_record(), result["invocation"])
            self.assertEqual("raw-retained-unvalidated", json.loads((target / "raw-retention.json").read_text())["result"])
            receipts = console.getvalue().splitlines()
            self.assertEqual(3, len(receipts))
            for prefix, raw in (("WP304_RAW_JUNIT|TEST-failure.xml|", xml),
                    ("WP304_RAW_PNG|compact-light.png|", image)):
                receipt = next(line for line in receipts if line.startswith(prefix))
                digest, encoded = receipt[len(prefix):].split("|")
                self.assertEqual(hashlib.sha256(raw).hexdigest(), digest)
                self.assertEqual(raw, base64.b64decode(encoded, validate=True))

    def test_pipeline_retention_rejects_repository_output_or_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"; source.mkdir()
            (source / "TEST-one.xml").write_bytes(b'<testsuite/>')
            for destination in (reader.ROOT, root / "pipeline"):
                if destination != reader.ROOT:
                    (destination / "wp304-native").mkdir(parents=True)
                with patch.object(retain_raw, "native_inputs", return_value={}), patch.object(retain_raw, "git", return_value=b"b" * 40):
                    with self.assertRaises(EvidenceError):
                        retain_raw.retain(source, root / "raw", pipeline_output=destination)

    def test_actual_invocation_forwards_only_exact_linux_verify_identity_root(self):
        record = invocation_record()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "wp109-invocation.json"
            path.write_text(json.dumps(record))
            with patch.object(retain_raw, "git", return_value=b"b" * 40):
                self.assertEqual((path.parent, record), retain_raw.pipeline_invocation(path))
                for key, value in (("host", "windows"), ("stage", "protocol"), ("identity", None),
                        ("schema_version", True)):
                    changed = record | {key: value}; path.write_text(json.dumps(changed))
                    with self.assertRaises(EvidenceError): retain_raw.pipeline_invocation(path)
                for key, value in (("head_sha", "c" * 40), ("repository", "other/repo"),
                        ("work_package", "WP-304"), ("policy_revision", "c" * 64),
                        ("source_sha", "c" * 40), ("manifest_sha256", "c" * 64),
                        ("base_sha", None), ("base_sha", "a" * 39)):
                    changed = copy.deepcopy(record); changed["identity"]["binding"][key] = value
                    path.write_text(json.dumps(changed))
                    with self.assertRaises(EvidenceError): retain_raw.pipeline_invocation(path)
                for key, value in (("run_id", 0), ("run_attempt", True)):
                    changed = copy.deepcopy(record); changed["identity"][key] = value
                    path.write_text(json.dumps(changed))
                    with self.assertRaises(EvidenceError): retain_raw.pipeline_invocation(path)

    def test_explicit_local_invocation_binds_actual_clean_head_tree_and_frozen_inputs(self):
        manifest = SimpleNamespace(sha256=retain_raw.MANIFEST,
            data={"reference": {"commit": retain_raw.PIN, "tree_sha": retain_raw.SOURCE_TREE}})
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "wp109-invocation.json"
            path.write_text(json.dumps(local_invocation_record()))
            with patch.object(retain_raw.platform, "system", return_value="Linux"), \
                    patch.object(retain_raw.platform, "machine", return_value="x86_64"), \
                    patch.object(retain_raw, "git", side_effect=local_git), \
                    patch.object(retain_raw, "load_manifest", return_value=manifest), \
                    patch.object(retain_raw, "policy_revision", return_value=retain_raw.POLICY), \
                    patch.object(retain_raw, "inventory") as inventory:
                root, invocation, local = retain_raw.local_invocation(path)
            self.assertEqual(path.parent, root)
            self.assertEqual(local_invocation_record(), invocation)
            self.assertEqual("local-committed-inputs-no-hosted-run-authority", local["scope"])
            self.assertEqual("b" * 40, local["binding"]["head_sha"])
            self.assertEqual("c" * 40, local["binding"]["tree_sha"])
            self.assertEqual(retain_raw.PIN, local["binding"]["source_sha"])
            self.assertEqual(retain_raw.SOURCE_TREE, local["binding"]["source_tree"])
            self.assertEqual(retain_raw.MANIFEST, local["binding"]["manifest_sha256"])
            self.assertEqual(retain_raw.POLICY, local["binding"]["policy_revision"])
            self.assertNotIn("run_id", local)
            self.assertNotIn("run_attempt", local)
            inventory.assert_called_once_with()

    def test_local_binding_rejects_wrong_host_dirty_snapshot_or_bad_actual_head_tree(self):
        with patch.object(retain_raw.platform, "system", return_value="Windows"):
            with self.assertRaisesRegex(EvidenceError, "Linux x64"):
                retain_raw.local_execution_binding()
        with patch.object(retain_raw.platform, "system", return_value="Linux"), \
                patch.object(retain_raw.platform, "machine", return_value="arm64"):
            with self.assertRaisesRegex(EvidenceError, "Linux x64"):
                retain_raw.local_execution_binding()
        for arguments, invalid, message in (
            (("rev-parse", "HEAD"), b"b" * 39, "head/tree"),
            (("rev-parse", "HEAD^{tree}"), b"not-a-tree", "head/tree"),
            (("status", "--porcelain=v1", "--untracked-files=all"), b" M owned.py", "clean committed"),
        ):
            def changed_git(*actual):
                return invalid if actual == arguments else local_git(*actual)
            with self.subTest(arguments=arguments), \
                    patch.object(retain_raw.platform, "system", return_value="Linux"), \
                    patch.object(retain_raw.platform, "machine", return_value="x86_64"), \
                    patch.object(retain_raw, "git", side_effect=changed_git):
                with self.assertRaisesRegex(EvidenceError, message):
                    retain_raw.local_execution_binding()

    def test_local_binding_rejects_source_manifest_policy_or_original_tree_drift(self):
        reference = {"commit": retain_raw.PIN, "tree_sha": retain_raw.SOURCE_TREE}
        variants = (
            (SimpleNamespace(sha256="a" * 64, data={"reference": reference}), retain_raw.POLICY, local_git),
            (SimpleNamespace(sha256=retain_raw.MANIFEST, data={"reference": reference | {"commit": "a" * 40}}),
                retain_raw.POLICY, local_git),
            (SimpleNamespace(sha256=retain_raw.MANIFEST, data={"reference": reference | {"tree_sha": "a" * 40}}),
                retain_raw.POLICY, local_git),
            (SimpleNamespace(sha256=retain_raw.MANIFEST, data={"reference": reference}), "a" * 64, local_git),
            (SimpleNamespace(sha256=retain_raw.MANIFEST, data={"reference": reference}), retain_raw.POLICY,
                lambda *args: b"a" * 40 if args == ("rev-parse", retain_raw.PIN + "^{tree}") else local_git(*args)),
        )
        for manifest, policy, git in variants:
            with self.subTest(manifest=manifest, policy=policy), \
                    patch.object(retain_raw.platform, "system", return_value="Linux"), \
                    patch.object(retain_raw.platform, "machine", return_value="x86_64"), \
                    patch.object(retain_raw, "git", side_effect=git), \
                    patch.object(retain_raw, "load_manifest", return_value=manifest), \
                    patch.object(retain_raw, "policy_revision", return_value=policy), \
                    patch.object(retain_raw, "inventory") as inventory:
                with self.assertRaisesRegex(EvidenceError, "source/manifest/policy drift"):
                    retain_raw.local_execution_binding()
                inventory.assert_not_called()

    def test_local_forwarding_cannot_consume_a_hosted_or_malformed_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "wp109-invocation.json"
            for identity in (invocation_record()["identity"], {}, [], False, 0):
                path.write_text(json.dumps(local_invocation_record() | {"identity": identity}))
                with self.subTest(identity=identity), self.assertRaisesRegex(EvidenceError, "explicit null"):
                    retain_raw.local_invocation(path)
            for change in ({"host": "windows"}, {"stage": "protocol"}, {"schema_version": True}, {"local": True}):
                path.write_text(json.dumps(local_invocation_record() | change))
                with self.subTest(change=change), self.assertRaisesRegex(EvidenceError, "stage/host/schema"):
                    retain_raw.local_invocation(path)
            path.write_text(json.dumps(local_invocation_record()))
            with self.assertRaisesRegex(EvidenceError, "Missing actual native pipeline invocation identity"):
                retain_raw.pipeline_invocation(path)

    def test_complete_actual_producer_methods_and_raw_hash_are_required(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "TEST-producer.xml"
            raw = ('<testsuite name="Producer" tests="2" failures="0" errors="0" skipped="0">'
                '<testcase classname="Producer" name="first()"/>'
                '<testcase classname="Producer" name="second"/>'
                '</testsuite>').encode()
            path.write_bytes(raw)
            result = reader.verify_projection_suite(path, "Producer", {"first", "second"})
            self.assertEqual(2, result["passed"])
            self.assertEqual(["first", "second"], result["methods"])
            self.assertEqual(hashlib.sha256(raw).hexdigest(), result["sha256"])
            self.assertEqual(len(raw), result["bytes"])

    def test_granted_and_landed_producer_registry_has_all_four_independent_suites(self):
        self.assertEqual({"runtime", "ble", "contacts", "remote"}, set(retain_raw.PROJECTION_SUITES))
        for suite in retain_raw.PROJECTION_SUITES.values():
            self.assertEqual({"directory", "classname", "module", "declaration_kind"} |
                ({"methods"} if suite["module"] != "services" else {"case_count"}), set(suite))
            self.assertIn(suite["module"], {"runtime", "ble", "services"})
            self.assertEqual(4 if suite["module"] != "services" else suite["case_count"],
                len(reader.projection_declaration_methods(suite)))

    def test_producer_missing_failed_error_skipped_zero_or_counter_mismatch_blocks(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "TEST-producer.xml"
            with self.assertRaisesRegex(EvidenceError, "Missing/linked"):
                reader.verify_projection_suite(path, "Producer", {"method"})
            variants = [
                '<testsuite name="Producer" tests="0" failures="0" errors="0" skipped="0"/>',
                '<testsuite name="Producer" tests="2" failures="0" errors="0" skipped="0">'
                    '<testcase classname="Producer" name="method"/></testsuite>',
            ]
            for status, plural in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
                counters = {"failures": 0, "errors": 0, "skipped": 0} | {plural: 1}
                variants.append('<testsuite name="Producer" tests="1" ' +
                    " ".join(f'{key}="{value}"' for key, value in counters.items()) + '>'
                    f'<testcase classname="Producer" name="method"><{status}/></testcase></testsuite>')
            for raw in variants:
                path.write_text(raw)
                with self.subTest(raw=raw), self.assertRaises(EvidenceError):
                    reader.verify_projection_suite(path, "Producer", {"method"})

    def test_producer_duplicate_unknown_or_missing_method_cannot_be_credited(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "TEST-producer.xml"
            for names, methods in ((["method", "method"], {"method"}),
                    (["other"], {"method"}), (["method"], {"method", "missing"})):
                raw = f'<testsuite name="Producer" tests="{len(names)}" failures="0" errors="0" skipped="0">' + \
                    "".join(f'<testcase classname="Producer" name="{name}"/>' for name in names) + '</testsuite>'
                path.write_text(raw)
                with self.subTest(names=names), self.assertRaises(EvidenceError):
                    reader.verify_projection_suite(path, "Producer", methods)

    def test_producer_raw_reports_export_before_assertion_or_identity_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"; source.mkdir()
            ui = b'<testsuite tests="1"><testcase name="actual"/></testsuite>'
            (source / "TEST-ui.xml").write_bytes(ui)
            sources = {}
            raw = b'<testsuite tests="1" failures="1"><testcase name="failed"><failure>original producer stack</failure></testcase></testsuite>'
            for owner, suite in retain_raw.PROJECTION_SUITES.items():
                directory = root / owner
                directory.mkdir()
                (directory / ("TEST-" + suite["classname"] + ".xml")).write_bytes(raw)
                sources[owner] = directory
            with patch.object(retain_raw, "native_inputs", return_value={"real-producer": {}}), \
                    patch.object(retain_raw, "git", return_value=b"b" * 40):
                result = retain_raw.retain(source, root / "raw", pipeline_output=root / "pipeline",
                    producer_junit=sources)
            self.assertEqual(set(retain_raw.PROJECTION_SUITES), set(result["producer_reports"]))
            for owner, receipt in result["producer_reports"].items():
                path = root / "pipeline" / "wp304-native" / "producers" / owner / receipt["name"]
                self.assertEqual(raw, path.read_bytes())
                self.assertEqual(hashlib.sha256(raw).hexdigest(), receipt["sha256"])
            self.assertEqual("raw-retained-unvalidated", result["result"])

    def test_local_raw_xml_png_and_binding_export_only_to_the_local_evidence_subtree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, images, pipeline = root / "source", root / "images", root / "pipeline"
            source.mkdir(); images.mkdir(); pipeline.mkdir()
            xml = b'<testsuite tests="1" failures="1"><testcase name="failed"><failure>actual stack</failure></testcase></testsuite>'
            image = png()
            (source / "TEST-failure.xml").write_bytes(xml)
            (images / "dialog.png").write_bytes(image)
            invocation = pipeline / "wp109-invocation.json"
            invocation.write_text(json.dumps(local_invocation_record()))
            local = {"scope": "local-committed-inputs-no-hosted-run-authority",
                "binding": {"head_sha": "b" * 40, "tree_sha": "c" * 40}}
            with patch.object(retain_raw, "native_inputs", return_value={"actual-input": {}}), \
                    patch.object(retain_raw, "git", return_value=b"b" * 40), \
                    patch.object(retain_raw, "local_execution_binding", return_value=local):
                result = retain_raw.retain(source, root / "raw", images=images, invocation_path=invocation)
                with self.assertRaisesRegex(EvidenceError, "must not be overwritten"):
                    retain_raw.retain(source, root / "raw", images=images, invocation_path=invocation)
            target = pipeline / "wp304-local"
            self.assertEqual(xml, (target / "junit" / "TEST-failure.xml").read_bytes())
            self.assertEqual(image, (target / "ui" / "dialog.png").read_bytes())
            self.assertEqual("local", result["execution_scope"])
            self.assertEqual(local, result["local_execution"])
            self.assertIsNone(result["invocation"]["identity"])
            self.assertEqual("raw-retained-unvalidated", result["result"])
            self.assertFalse((pipeline / "wp304-native").exists())
            self.assertEqual(result, json.loads((target / "raw-retention.json").read_text()))

    def test_invalid_local_binding_preserves_raw_bytes_without_export_or_hosted_fallback(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"; source.mkdir()
            raw = b'<testsuite tests="1"><testcase name="actual"/></testsuite>'
            (source / "TEST-one.xml").write_bytes(raw)
            invocation = root / "wp109-invocation.json"
            invocation.write_text(json.dumps(local_invocation_record()))
            with patch.object(retain_raw, "native_inputs", return_value={}), \
                    patch.object(retain_raw, "git", return_value=b"b" * 40), \
                    patch.object(retain_raw, "local_execution_binding",
                        side_effect=EvidenceError("Local UI evidence requires a clean committed snapshot")):
                with self.assertRaisesRegex(EvidenceError, "clean committed"):
                    retain_raw.retain(source, root / "raw", invocation_path=invocation)
            self.assertEqual(raw, (root / "raw" / "junit" / "TEST-one.xml").read_bytes())
            result = json.loads((root / "raw" / "raw-retention.json").read_text())
            self.assertIsNone(result["local_execution"])
            self.assertIsNone(result["invocation"])
            self.assertFalse((root / "wp304-local").exists())
            self.assertFalse((root / "wp304-native").exists())

    def test_pipeline_invocation_rejects_wrong_path_unbounded_or_malformed_json(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wrong = root / "other.json"
            wrong.write_text("{}")
            with self.assertRaisesRegex(EvidenceError, "Unsafe invocation"):
                retain_raw.pipeline_invocation(wrong)
            path = root / "wp109-invocation.json"
            for raw in ("", " " * (64 * 1024 + 1)):
                path.write_text(raw)
                with self.assertRaisesRegex(EvidenceError, "Empty/excessive"):
                    retain_raw.pipeline_invocation(path)
            for raw in ("null", "[]", "{}", '{"schema_version":1,"schema_version":1}'):
                path.write_text(raw)
                with self.assertRaises(EvidenceError):
                    retain_raw.pipeline_invocation(path)

    def test_invalid_invocation_retains_raw_bytes_but_does_not_export_or_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, images = root / "source", root / "images"
            source.mkdir(); images.mkdir()
            xml = b'<testsuite tests="1" failures="1"><testcase name="failed"><failure>actual stack</failure></testcase></testsuite>'
            image = png()
            (source / "TEST-failure.xml").write_bytes(xml)
            (images / "dialog.png").write_bytes(image)
            invocation = root / "wp109-invocation.json"
            invocation.write_text('{"schema_version":0}')
            console = io.StringIO()
            with patch.object(retain_raw, "native_inputs", return_value={}), patch.object(retain_raw, "git", return_value=b"b" * 40), redirect_stdout(console):
                with self.assertRaisesRegex(EvidenceError, "Wrong native pipeline"):
                    retain_raw.retain(source, root / "raw", emit=True, images=images, invocation_path=invocation)
            self.assertEqual(xml, (root / "raw" / "junit" / "TEST-failure.xml").read_bytes())
            self.assertEqual(image, (root / "raw" / "ui" / "dialog.png").read_bytes())
            self.assertIsNone(json.loads((root / "raw" / "raw-retention.json").read_text())["invocation"])
            self.assertFalse((root / "wp304-native").exists())
            self.assertEqual(3, len(console.getvalue().splitlines()))

    def test_missing_junit_writes_blocked_binding_not_a_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(retain_raw, "native_inputs", return_value={}), patch.object(retain_raw, "git", return_value=b"b" * 40):
                with self.assertRaisesRegex(EvidenceError, "No produced JUnit"):
                    retain_raw.retain(root / "absent", root / "raw")
            result = json.loads((root / "raw" / "raw-retention.json").read_text())
            self.assertEqual("blocked-no-produced-junit", result["result"])
            self.assertEqual([], result["reports"])

    def test_failed_junit_never_validates_as_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-one.xml").write_text('<testsuite tests="1" failures="1" errors="0" skipped="0"><testcase classname="C" name="method"><failure>stack</failure></testcase></testsuite>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaisesRegex(EvidenceError, "Failed/error/skipped/zero native tests"):
                    reader.collect(path)

    def test_skipped_junit_never_validates_as_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-one.xml").write_text('<testsuite tests="1" failures="0" errors="0" skipped="1"><testcase classname="C" name="method"><skipped/></testcase></testsuite>')
            with patch.object(reader, "declarations", return_value=({"one": {"scenarios": 1}}, {"C#method"})):
                with self.assertRaisesRegex(EvidenceError, "Failed/error/skipped/zero native tests"):
                    reader.collect(path)


if __name__ == "__main__":
    unittest.main()
