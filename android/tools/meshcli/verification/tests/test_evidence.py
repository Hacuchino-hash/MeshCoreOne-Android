"""AndroidOnly: WP-109 Actual raw-reader regressions; source floors and malformed evidence fail closed."""

import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

FILE = Path(__file__).resolve().parents[1] / "collect_evidence.py"
spec = importlib.util.spec_from_file_location("wp109_evidence", FILE)
reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reader)
PortError = reader.PortError


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.reports = self.root / "reports"
        self.reports.mkdir()
        self.file = self.reports / "TEST-example.xml"

    def tearDown(self):
        self.temporary.cleanup()

    def report(self, case='<testcase classname="example.Test" name="actual()"/>', counts='tests="1" failures="0" errors="0" skipped="0"'):
        self.file.write_text(f'<testsuite {counts}>{case}<system-out>complete log</system-out></testsuite>', encoding="utf-8")

    def commit_input(self, name, data):
        subprocess.run(["git", "init", "--quiet", str(self.root)], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(self.root), "config", "core.autocrlf", "false"], check=True, capture_output=True)
        source = self.root / name
        source.write_bytes(data)
        subprocess.run(["git", "-C", str(self.root), "add", name], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
                        "commit", "--quiet", "-m", "fixture"], check=True, capture_output=True)
        return subprocess.check_output(["git", "-C", str(self.root), "rev-parse", "HEAD"]).decode().strip()

    def native_floor_fixture(self):
        retained = (reader.EVIDENCE / "baseline-native.json").read_bytes().replace(b"\r\n", b"\n")
        baseline = self.root / "docs" / "android" / "evidence" / "WP-109" / "baseline-native.json"
        baseline.parent.mkdir(parents=True, exist_ok=True)
        baseline.write_bytes(retained)
        return {
            "protocol": json.loads(retained)["cases"] + [
                {"class": reader.NATIVE_PREFIX + "parity.TcpOwnershipParityTest", "name": method + "()", "outcome": "passed"}
                for method in reader.TCP_CASES
            ],
            "meshcli": [{"class": scope, "name": name, "outcome": "passed"}
                        for scope, name in sorted(reader.required_cli_cases())],
        }

    def test_complete_raw_case_and_log_bytes_survive(self):
        self.report()
        cases, raw, counts = reader.junit(self.reports, self.root, 1)
        self.assertEqual([{"class": "example.Test", "name": "actual()", "outcome": "passed"}], cases)
        self.assertEqual(self.file.read_bytes(), raw[self.file.name])
        self.assertIn(b"complete log", raw[self.file.name])
        self.assertEqual(1, counts["passed"])

    def test_missing_zero_failed_skipped_and_inconsistent_reports_reject(self):
        with self.assertRaises(PortError):
            reader.junit(self.reports, self.root, 1)
        for counts, nodes in [
            ('tests="0" failures="0" errors="0" skipped="0"', ""),
            ('tests="2" failures="0" errors="0" skipped="0"', '<testcase classname="x" name="one"/>'),
            ('tests="1" failures="1" errors="0" skipped="0"', '<testcase classname="x" name="one"><failure/></testcase>'),
            ('tests="1" failures="0" errors="0" skipped="1"', '<testcase classname="x" name="one"><skipped/></testcase>'),
        ]:
            with self.subTest(counts=counts):
                self.report(nodes, counts)
                with self.assertRaises(PortError):
                    reader.junit(self.reports, self.root, 1)

    def test_fabricated_pass_counter_cannot_hide_actual_error(self):
        self.report('<testcase classname="x" name="one"><error message="actual"/></testcase>')
        with self.assertRaises(PortError):
            reader.junit(self.reports, self.root, 1)

    def test_duplicate_or_missing_case_identity_rejects(self):
        for nodes, count in [
            ('<testcase name="one"/>', 1),
            ('<testcase classname="x" name="one"/><testcase classname="x" name="one"/>', 2),
        ]:
            self.report(nodes, f'tests="{count}" failures="0" errors="0" skipped="0"')
            with self.assertRaises(PortError):
                reader.junit(self.reports, self.root, 1)

    def test_malformed_and_utf16_entity_reports_reject(self):
        self.file.write_bytes(b"<testsuite")
        with self.assertRaises(PortError):
            reader.junit(self.reports, self.root, 1)
        self.file.write_bytes('<!DOCTYPE testsuite [<!ENTITY x "secret">]><testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="x" name="&x;"/></testsuite>'.encode("utf-16"))
        with self.assertRaises(PortError):
            reader.junit(self.reports, self.root, 1)

    def test_reduced_nonzero_discovery_does_not_satisfy_floor(self):
        self.report()
        with self.assertRaises(PortError):
            reader.junit(self.reports, self.root, 2)

    def test_reports_cannot_escape_bounded_root(self):
        self.report()
        with self.assertRaises(PortError):
            reader.junit(self.reports, self.root / "unrelated", 1)

    def test_explicit_native_aliases_preserve_three_parameter_families(self):
        aliases = reader.aliases()
        self.assertEqual(30, len(aliases))
        self.assertIn(("NewCommandsTests", "setPathHashMode mode 0 (1-byte hashes)"), aliases)
        self.assertIn(("MeshEventErrorCodeTests", "All six firmware sub-codes map to the matching ErrorCode"), aliases)
        self.assertEqual(aliases[("MeshEventErrorCodeTests", "Non-error events have no typed error code")],
                         aliases[("MeshEventErrorCodeTests", "deviceErrorCode is nil for non-deviceError MeshCoreError cases")])

    def test_invocation_requires_exact_version_stage_host_head_and_run(self):
        path = self.root / "invocation.json"
        head = "a" * 40
        record = {
            "schema_version": 1, "stage": "protocol", "host": "linux",
            "identity": {"binding": {
                "repository": "cbattlegear/MeshCoreOne-Android", "work_package": "WP-003",
                "base_sha": "b" * 40, "head_sha": head, "source_sha": reader.SOURCE,
                "manifest_sha256": reader.MANIFEST, "policy_revision": reader.POLICY,
            }, "run_id": 123, "run_attempt": 1},
        }
        path.write_text(json.dumps(record), encoding="utf-8")
        self.assertEqual(record, reader.invocation_record(path, head, "linux"))
        for field, value in [("schema_version", 2), ("schema_version", True), ("stage", "prepare"), ("host", "windows")]:
            wrong = {**record, field: value}
            path.write_text(json.dumps(wrong), encoding="utf-8")
            with self.assertRaises(PortError):
                reader.invocation_record(path, head, "linux")
        path.write_text(json.dumps(record), encoding="utf-8")
        with self.assertRaises(PortError):
            reader.invocation_record(path, "c" * 40, "linux")
        record["identity"]["run_attempt"] = True
        path.write_text(json.dumps(record), encoding="utf-8")
        with self.assertRaises(PortError):
            reader.invocation_record(path, head, "linux")

    def test_local_identity_never_claims_hosted_authority(self):
        record = reader.invocation_record(None, "a" * 40, "linux")
        self.assertNotIn("identity", record)
        self.assertIn("no hosted run authority", record["scope"])

    def test_committed_text_crlf_is_not_drift_but_content_change_is(self):
        head = self.commit_input("input.kt", b"first\nsecond\n")
        source = self.root / "input.kt"
        source.write_bytes(b"first\r\nsecond\r\n")
        inputs, raw = reader.checked_checkout(self.root, head, {"input.kt"})
        self.assertEqual(b"first\nsecond\n", raw["input.kt"])
        self.assertEqual(reader.blob(raw["input.kt"]), inputs["input.kt"])
        source.write_bytes(b"changed\nsecond\n")
        with self.assertRaises(PortError):
            reader.checked_checkout(self.root, head, {"input.kt"})
        with self.assertRaises(PortError):
            reader.checked_checkout(self.root, head, {"uncommitted.kt"})

    def test_extensionless_license_newlines_preserve_the_complete_immutable_blob(self):
        head = self.commit_input("LICENSE", b"Copyright fixture\nExact license terms\n")
        source = self.root / "LICENSE"
        source.write_bytes(b"Copyright fixture\r\nExact license terms\r\n")
        inputs, raw = reader.checked_checkout(self.root, head, {"LICENSE"})
        self.assertEqual(b"Copyright fixture\nExact license terms\n", raw["LICENSE"])
        self.assertEqual(reader.blob(raw["LICENSE"]), inputs["LICENSE"])
        source.write_bytes(b"Copyright fixture\r\nChanged license terms\r\n")
        with self.assertRaises(PortError):
            reader.checked_checkout(self.root, head, {"LICENSE"})

    def test_extensionless_binary_newlines_are_not_normalized(self):
        head = self.commit_input("binary-input", b"\xff\n\x01")
        (self.root / "binary-input").write_bytes(b"\xff\r\n\x01")
        with self.assertRaises(PortError):
            reader.checked_checkout(self.root, head, {"binary-input"})

    def test_frozen_baseline_accepts_checkout_newlines_not_changed_identities(self):
        retained = (reader.EVIDENCE / "baseline-native.json").read_bytes().replace(b"\r\n", b"\n")
        self.assertEqual(reader.BASELINE_SHA256, reader.sha(retained.replace(b"\n", b"\r\n")))
        native = self.native_floor_fixture()
        baseline = self.root / "docs" / "android" / "evidence" / "WP-109" / "baseline-native.json"
        for data in (retained, retained.replace(b"\n", b"\r\n")):
            baseline.write_bytes(data)
            reader.validate_native_floor(self.root, native)
        changed = retained.replace(b"correct layout()", b"changed layout()", 1)
        self.assertNotEqual(retained, changed)
        baseline.write_bytes(changed)
        with self.assertRaises(PortError):
            reader.validate_native_floor(self.root, native)

    def test_every_new_tcp_and_cli_row_is_required_not_only_class_presence_or_total(self):
        native = self.native_floor_fixture()
        reader.validate_native_floor(self.root, native)
        self.assertEqual(87, len(native["meshcli"]))
        incomplete = {**native, "meshcli": [
            case for case in native["meshcli"]
            if not case["name"].startswith("bidiControlCharacters()")
            and "detects a closed" not in case["name"]
        ]}
        self.assertEqual(73, len(incomplete["meshcli"]), "Reproduces actual d472 discovery, not native execution")
        with self.assertRaises(PortError):
            reader.validate_native_floor(self.root, incomplete)
        replaced = {**native, "meshcli": [*native["meshcli"][:-1], {
            "class": reader.CLI_PREFIX + "CliTcpTest", "name": "unrelated replacement()", "outcome": "passed",
        }]}
        with self.assertRaises(PortError):
            reader.validate_native_floor(self.root, replaced)
        without_tcp = {**native, "protocol": native["protocol"][:-1]}
        with self.assertRaises(PortError):
            reader.validate_native_floor(self.root, without_tcp)

    def test_complete_count_schema_rejects_booleans_and_nonintegral_values(self):
        valid = {"discovered": 87, "run": 87, "passed": 87, "failed": 0, "errors": 0, "skipped": 0}
        self.assertEqual(valid, reader.require_counts(valid, minimum=87))
        for key, invalid in (("failed", False), ("errors", 0.0), ("passed", "87"), ("skipped", None)):
            with self.subTest(key=key):
                with self.assertRaises(PortError):
                    reader.require_counts({**valid, key: invalid}, minimum=87)

    def test_required_inputs_include_tools_production_tests_hooks_and_source_scope(self):
        class Manifest:
            def inputs(self, wp):
                return [{"path": "MeshCore/Tests/original.swift"}]
        entries = {
            "android/tools/meshcli/src/main/Main.kt": "a" * 40,
            "android/tools/meshcli/src/test/MainTest.kt": "b" * 40,
            "android/tools/meshcli/verification/tests/test_reader.py": "c" * 40,
            "android/tools/meshcli/build.gradle.kts": "d" * 40,
            "android/build-logic/convention/src/main/BuildConventions.kt": "e" * 40,
            "android/gradle/dependency-locks/immutable.lockfile": "f" * 40,
        }
        required = reader.required_inputs(entries, Manifest())
        self.assertTrue(set(entries) <= required)
        self.assertIn("MeshCore/Tests/original.swift", required)
        self.assertIn("docs/android/evidence/WP-109/baseline-native.json", required)
        self.assertIn("android/core/testing/fixtures/protocol-vectors.tsv", required)
        self.assertTrue(reader.required_runtime_inputs() <= required)
        self.assertIn("LICENSE", required)
        self.assertIn("android/app/src/main/assets/licenses/BouncyCastle-MIT.txt", required)


if __name__ == "__main__":
    unittest.main()
