"""AndroidOnly: WP-203 SHA/run/data/XML reader negatives; not real Android or Swift evidence."""

import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from oracle import wp203_ci as CI
from oracle.reference import OracleError, SOURCE_SHA, json_bytes


class BundleTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wp203-bundle-")
        self.root = Path(self.temporary.name)
        self.identity = {"binding": {"head_sha": "a" * 40, "source_sha": SOURCE_SHA}, "run_id": 1, "run_attempt": 1}
        self.junit = {"counts": {"tests": 1, "passed": 1, "failed": 0, "errors": 0, "skipped": 0}, "reports": []}

    def tearDown(self):
        self.temporary.cleanup()

    def make_bundle(self):
        for name in CI.DATA_NAMES["producer"]:
            (self.root / name).write_bytes(b"controlled test data")
        evidence = {"schema_version": 1, "stage": "producer", "identity": self.identity, "source_sha": SOURCE_SHA,
                    "tasks": list(CI.TASKS), "data": [CI.record(self.root, name) for name in CI.DATA_NAMES["producer"]],
                    "junit": self.junit}
        (self.root / "wp203-evidence.json").write_bytes(json_bytes(evidence))
        return evidence

    def test_exact_data_hashes_and_identity_are_required(self):
        self.make_bundle()
        with patch.object(CI, "report_record", return_value=self.junit):
            self.assertEqual("producer", CI.validate_bundle(self.root, "producer", self.identity)["stage"])
            (self.root / "kotlin-export.meshcoreone").write_bytes(b"modified")
            with self.assertRaisesRegex(OracleError, "hashes/sizes"):
                CI.validate_bundle(self.root, "producer", self.identity)

    def test_stale_head_run_or_attempt_fails_before_consumption(self):
        self.make_bundle()
        for field, value in (("run_id", 2), ("run_attempt", 2), ("binding", {"head_sha": "b" * 40, "source_sha": SOURCE_SHA})):
            with self.subTest(field=field):
                expected = dict(self.identity, **{field: value})
                with self.assertRaisesRegex(OracleError, "same head/base/run/attempt"):
                    CI.validate_bundle(self.root, "producer", expected)

    def test_missing_extra_or_malformed_stage_fields_fail(self):
        evidence = self.make_bundle()
        for field in ("tasks", "junit", "data", "identity"):
            with self.subTest(field=field):
                changed = dict(evidence)
                del changed[field]
                (self.root / "wp203-evidence.json").write_bytes(json_bytes(changed))
                with self.assertRaisesRegex(OracleError, "Malformed"):
                    CI.validate_bundle(self.root, "producer", self.identity)

    def test_raw_junit_cannot_disagree_with_summary(self):
        self.make_bundle()
        with patch.object(CI, "report_record", return_value={"counts": {"tests": 0}, "reports": []}):
            with self.assertRaisesRegex(OracleError, "XML disagrees"):
                CI.validate_bundle(self.root, "producer", self.identity)

    def test_unrelated_native_tasks_are_not_success_proof(self):
        evidence = self.make_bundle()
        evidence["tasks"] = [":core:data:compileDebugKotlin"]
        (self.root / "wp203-evidence.json").write_bytes(json_bytes(evidence))
        with self.assertRaisesRegex(OracleError, "mandatory tasks"):
            CI.validate_bundle(self.root, "producer", self.identity)

    def test_oversized_input_is_rejected_before_open(self):
        path = self.root / "controlled.meshcoreone"
        path.write_bytes(b"123456")
        with patch.object(CI, "MAXIMUM_DATA_BYTES", 4), patch.object(Path, "open") as opened:
            with self.assertRaisesRegex(OracleError, "oversized"):
                CI.digest_file(path)
            opened.assert_not_called()

    def test_local_identity_cannot_be_presented_as_hosted_execution(self):
        with patch.object(CI, "execution_identity", return_value=None):
            with self.assertRaisesRegex(OracleError, "actual hosted"):
                CI.identity()


if __name__ == "__main__":
    unittest.main()
