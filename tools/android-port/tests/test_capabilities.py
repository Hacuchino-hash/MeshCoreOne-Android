import copy
import tempfile
import unittest
from pathlib import Path

from fixtures import BASE, NOW, policy, settings, test_manifest
from controller.capabilities import CapabilityEngine
from controller.errors import PortError
from controller.ledger import Ledger


class CapabilityReservationTests(unittest.TestCase):
    def setUp(self):
        self.rules = policy()
        self.manifest = test_manifest("WP-101")
        self.engine = CapabilityEngine(self.rules)
        self.temporary = tempfile.TemporaryDirectory(prefix="meshcore-capability-tests-")
        self.addCleanup(self.temporary.cleanup)
        self.ledger = Ledger(Path(self.temporary.name) / "ledger.sqlite", self.rules["repository"])

    def test_wrong_path_and_operation_fail_closed_with_actionable_blocker(self):
        with self.assertRaisesRegex(PortError, r"WP WP-101.*secrets/key.*export"):
            self.engine.admit("WP-101", self.manifest.wp("WP-101")["write_paths"],
                              "secrets/key", "export")

    def test_assigned_path_with_unsupported_operation_fails_closed(self):
        assigned_path = self.manifest.wp("WP-101")["write_paths"][0]
        with self.assertRaisesRegex(PortError, rf"WP WP-101.*{assigned_path}.*unsupported-operation"):
            self.engine.admit("WP-101", self.manifest.wp("WP-101")["write_paths"],
                              assigned_path, "unsupported-operation")

    def test_shared_dependency_scope_is_admitted_without_byte_authorization(self):
        admission = self.engine.admit(
            "WP-101", self.manifest.wp("WP-101")["write_paths"],
            "android/core/libs.versions.toml", "add-resolved-artifact-checksum",
        )
        self.assertEqual(admission.capability_id, "dependency-resolution-metadata")

    def test_scope_evolution_is_same_owner_idempotent_and_stale_cas_is_safe(self):
        wp = self.manifest.wp("WP-101")
        binding = {
            "repository": self.rules["repository"], "work_package": "WP-101",
            "base_sha": BASE, "head_sha": "2" * 40,
            "source_sha": "d" * 40, "manifest_sha256": "e" * 64,
            "policy_revision": "f" * 64,
        }
        record, acquired = self.ledger.claim(
            wp, binding, "local", "attempt", NOW, 1800, 5, 0, 100, 5,
        )
        self.assertTrue(acquired)
        evolved = self.ledger.evolve_scope(
            "WP-101", "attempt", record["revision"],
            {"dependency-resolution-metadata": ["android/core/libs.versions.toml"]},
            ["add-resolved-artifact-checksum"], ["android/core/libs.versions.toml"],
        )
        self.assertEqual(evolved["revision"], 2)
        with self.assertRaisesRegex(PortError, "Stale capability reservation"):
            self.ledger.evolve_scope(
                "WP-101", "attempt", 1, {}, ["modify"], ["android/core/libs.versions.toml"],
            )
        current = self.ledger.get("WP-101")
        self.assertEqual(current["revision"], 2)

    def test_hard_locks_are_separate_from_file_claims(self):
        self.ledger.acquire_hard_lock("gradle-execution", "session-a", {"kind": "runtime"}, NOW)
        with self.assertRaisesRegex(PortError, "Hard-lock collision"):
            self.ledger.acquire_hard_lock("gradle-execution", "session-b", {}, NOW)
        self.ledger.release_hard_lock("gradle-execution", "session-a")

    def test_legacy_migration_preserves_binding_identity_and_state(self):
        wp = copy.deepcopy(self.manifest.wp("WP-101"))
        binding = {
            "repository": self.rules["repository"], "work_package": "WP-101",
            "base_sha": BASE, "head_sha": "2" * 40,
            "source_sha": "d" * 40, "manifest_sha256": "e" * 64,
            "policy_revision": "f" * 64,
        }
        self.ledger.claim(wp, binding, "local", "attempt", NOW, 1800, 5, 0, 100, 5)
        before = self.ledger.get("WP-101")
        with self.ledger.transaction() as connection:
            connection.execute(
                "UPDATE leases SET record_state='legacy-advisory',migration_revision='' "
                "WHERE repository=? AND wp=?", (self.rules["repository"], "WP-101")
            )
        self.assertEqual(self.ledger.migrate_legacy(), 1)
        after = self.ledger.get("WP-101")
        self.assertEqual(after["binding"], before["binding"])
        self.assertEqual(after["identity"], before["identity"])
        self.assertEqual(after["state"], before["state"])
        self.assertEqual(self.ledger.migrate_legacy(), 0)
